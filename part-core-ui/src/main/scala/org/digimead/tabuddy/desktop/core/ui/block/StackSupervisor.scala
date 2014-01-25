/**
 * This file is part of the TA Buddy project.
 * Copyright (c) 2013-2014 Alexey Aksenov ezh@ezh.msk.ru
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Global License version 3
 * as published by the Free Software Foundation with the addition of the
 * following permission added to Section 15 as permitted in Section 7(a):
 * FOR ANY PART OF THE COVERED WORK IN WHICH THE COPYRIGHT IS OWNED
 * BY Limited Liability Company «MEZHGALAKTICHESKIJ TORGOVYJ ALIANS»,
 * Limited Liability Company «MEZHGALAKTICHESKIJ TORGOVYJ ALIANS» DISCLAIMS
 * THE WARRANTY OF NON INFRINGEMENT OF THIRD PARTY RIGHTS.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Global License for more details.
 * You should have received a copy of the GNU Affero General Global License
 * along with this program; if not, see http://www.gnu.org/licenses or write to
 * the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA, 02110-1301 USA, or download the license from the following URL:
 * http://www.gnu.org/licenses/agpl.html
 *
 * The interactive user interfaces in modified source and object code versions
 * of this program must display Appropriate Legal Notices, as required under
 * Section 5 of the GNU Affero General Global License.
 *
 * In accordance with Section 7(b) of the GNU Affero General Global License,
 * you must retain the producer line in every report, form or document
 * that is created or manipulated using TA Buddy.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license. Buying such a license is mandatory as soon as you
 * develop commercial activities involving the TA Buddy software without
 * disclosing the source code of your own applications.
 * These activities include: offering paid services to customers,
 * serving files in a web or/and network application,
 * shipping TA Buddy with a closed source product.
 *
 * For more information, please contact Digimead Team at this
 * address: ezh@ezh.msk.ru
 */

package org.digimead.tabuddy.desktop.core.ui.block

import akka.actor.{ Actor, ActorRef, Props, actorRef2Scala }
import akka.pattern.ask
import java.lang.ref.WeakReference
import java.util.UUID
import java.util.concurrent.atomic.{ AtomicBoolean, AtomicReference }
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.Core
import org.digimead.tabuddy.desktop.core.definition.Context
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.support.Timeout
import org.digimead.tabuddy.desktop.core.ui.UI
import org.digimead.tabuddy.desktop.core.ui.block.builder.ViewContentBuilder
import org.digimead.tabuddy.desktop.core.ui.definition.widget.{ SComposite, VComposite, WComposite }
import org.eclipse.swt.custom.ScrolledComposite
import org.eclipse.swt.widgets.Widget
import scala.collection.{ immutable, mutable }
import scala.concurrent.Await
import scala.language.implicitConversions

/**
 * Stack supervisor responsible for:
 * - restore all view
 * - track views
 * - provide view configuration
 * - save all views configuration
 */
class StackSupervisor(val windowId: UUID, val parentContext: Context.Rich) extends Actor with Loggable {
  /** Stack configuration. */
  val configuration = StackConfiguration.load(windowId) getOrElse StackConfiguration.default()
  /** configurationMap*/
  val configurationMap = configurationToMap(configuration)
  /** Flag indicating whether the configurations save process restart is required. */
  val configurationsSaveRestart = new AtomicBoolean()
  /** Top level stack hierarchy container. It is ScrolledComposite of content of AppWindow. */
  @volatile var container: Option[ScrolledComposite] = None
  /** Last active view id for this window. */
  val lastActiveViewIdForCurrentWindow = new AtomicReference[Option[UUID]](None)
  /** List of all window stacks. */
  val pointers = new StackSupervisor.PointerMap(context.parent)
  log.debug("Start actor " + self.path)

  /** Is called asynchronously after 'actor.stop()' is invoked. */
  override def postStop() {
    App.system.eventStream.unsubscribe(self, classOf[App.Message.Create[_]])
    App.system.eventStream.unsubscribe(self, classOf[App.Message.Destroy[_]])
    log.debug(self.path.name + " actor is stopped.")
  }
  /** Is called when an Actor is started. */
  override def preStart() {
    App.system.eventStream.subscribe(self, classOf[App.Message.Create[_]])
    App.system.eventStream.subscribe(self, classOf[App.Message.Destroy[_]])
    log.debug(self.path.name + " actor is started.")
  }
  def receive = {
    case message @ App.Message.Create(Left(viewFactory: View.Factory), None) ⇒ App.traceMessage(message) {
      create(viewFactory) match {
        case Some(windowComposite) ⇒
          App.Message.Create(Right(windowComposite))
        case None ⇒
          App.Message.Error(s"Unable to create ${viewFactory}.")
      }
    } foreach { sender ! _ }

    case message @ App.Message.Create(Right(stackLayer: SComposite), Some(publisher)) ⇒ App.traceMessage(message) {
      onCreated(stackLayer, publisher)
    }

    case message @ App.Message.Destroy(Right(stackLayer: SComposite), Some(publisher)) ⇒ App.traceMessage(message) {
      onDestroyed(stackLayer)
    }

    case message @ App.Message.Get(Configuration) ⇒ sender ! App.execNGet { container.map(w ⇒ StackConfiguration.build(w.getShell)) getOrElse configuration }

    case message @ App.Message.Get(Option) ⇒ sender ! lastActiveViewIdForCurrentWindow.get

    case message @ App.Message.Get(Seq) ⇒ sender ! pointers.toSeq

    case message @ App.Message.Restore(Left(content: WComposite), None) ⇒ App.traceMessage(message) {
      restore(content) match {
        case Some(windowComposite) ⇒
          App.Message.Create(Right(windowComposite))
        case None ⇒
          App.Message.Error(s"Unable to restore content for ${content}.")
      }
    } foreach { sender ! _ }

    case message @ App.Message.Start(Left(widget: Widget), None) ⇒ App.traceMessage(message) {
      onStart(widget)
      App.Message.Start(Right(widget))
    } foreach { sender ! _ }

    case message @ App.Message.Stop(Left(widget: Widget), None) ⇒ App.traceMessage(message) {
      onStop(widget)
      App.Message.Stop(Right(widget))
    } foreach { sender ! _ }

    /*
     * Skip
     */
    case message @ App.Message.Create(_, _) ⇒
    case message @ App.Message.Destroy(_, _) ⇒
    case message @ App.Message.Start(_, _) ⇒
    case message @ App.Message.Stop(_, _) ⇒
  }

  /** Extract map from configuration. */
  protected def configurationToMap(configuration: Configuration): immutable.HashMap[UUID, (Option[UUID], Configuration.CPlaceHolder)] = {
    var entry = Seq[(UUID, (Option[UUID], Configuration.CPlaceHolder))]()
    def visit(stack: Configuration.CPlaceHolder, parent: Option[UUID]) {
      entry = entry :+ stack.id -> (parent, stack)
      stack match {
        case tab: Configuration.Stack.CTab ⇒
          tab.children.foreach(visit(_, Some(tab.id)))
        case hsash: Configuration.Stack.CHSash ⇒
          visit(hsash.left, Some(hsash.id))
          visit(hsash.right, Some(hsash.id))
        case vsash: Configuration.Stack.CVSash ⇒
          visit(vsash.top, Some(vsash.id))
          visit(vsash.bottom, Some(vsash.id))
        case view: Configuration.CView ⇒
        case empty: Configuration.CEmpty ⇒
      }
    }
    visit(configuration.stack, None)
    immutable.HashMap[UUID, (Option[UUID], Configuration.CPlaceHolder)](entry: _*)
  }
  /** Create new stack element from configuration. */
  protected def create(stackId: UUID, parentWidget: ScrolledComposite): Option[SComposite] = {
    log.debug("Create a top level stack element with id %08X=%s.".format(stackId.hashCode(), stackId))
    if (pointers.contains(stackId))
      throw new IllegalArgumentException(s"Stack with id ${stackId} is already exists.")
    if (!configurationMap.contains(stackId))
      throw new IllegalArgumentException(s"Stack with id ${stackId} is unknown.")
    val composite = configurationMap(stackId) match {
      case (parent, stackConfiguration: Configuration.Stack) ⇒
        log.debug(s"Attach ${stackConfiguration} as top level element.")
        val stack = context.actorOf(StackLayer.props.copy(args = immutable.Seq(stackConfiguration.id)), StackLayer.id + "_%08X".format(stackConfiguration.id.hashCode()))
        pointers += stackId -> StackSupervisor.StackPointer(stack)(new WeakReference(null))
        // Block supervisor until stack is created.
        Await.result(ask(stack, App.Message.Create(Left(StackLayer.<>(stackConfiguration,
          parentWidget, parentContext, context))))(Timeout.short), Timeout.short) match {
          case App.Message.Create(Right(stackWidget: SComposite), None) ⇒
            log.debug(s"Stack layer ${stackConfiguration} content created.")
            Some(stackWidget)
          case App.Message.Error(error, None) ⇒
            log.fatal(s"Unable to create content for stack layer ${stackConfiguration}: ${error}.")
            None
          case _ ⇒
            log.fatal(s"Unable to create content for stack layer ${stackConfiguration}.")
            None
        }
      case (parent, viewConfiguration: Configuration.CView) ⇒
        // There is only a view that is directly attached to the window.
        log.debug(s"Attach ${viewConfiguration} as top level element.")
        ViewContentBuilder(viewConfiguration, parentWidget, parentContext, context) match {
          case result @ Some(viewWidget) ⇒
            pointers += stackId -> StackSupervisor.StackPointer(viewWidget.ref)(new WeakReference(viewWidget))
            result
          case None ⇒
            None
        }
      case (parent, viewConfiguration: Configuration.CEmpty) ⇒
        // Skip an empty configuration.
        None
    }
    Option(Core.context.getActiveLeaf().get(classOf[VComposite])).getOrElse {
      composite.foreach(setActiveView(stackId, _))
    }
    composite
  }
  /** Create new view within stack hierarchy. */
  protected def create(viewFactory: View.Factory): Option[VComposite] = {
    if (container.isEmpty)
      throw new IllegalStateException(s"Unable to create view from ${viewFactory}. Stack container isn't created.")
    App.assertEventThread(false)
    log.debug("Create new view from %s within StackSupervisor[%08X].".format(viewFactory, windowId.hashCode()))
    val neighborViewId = lastActiveViewIdForCurrentWindow.get() orElse {
      pointers.find {
        case (id, pointer) ⇒ Option(pointer.stack.get()).
          map(_.isInstanceOf[VComposite]).getOrElse(false)
      }.map { case (id, pointer) ⇒ id }
    }
    neighborViewId match {
      case Some(id) ⇒
        // Attach view from viewFactory to neighborViewId.
        Option(pointers(id).stack.get) match {
          case Some(view: VComposite) ⇒
            transform.TransformViewToTab(this, view).foreach { tab ⇒
              transform.TransformAttachView(this, tab, viewFactory)
            }
            Some(view)
          case _ ⇒
            log.fatal(s"Lost view ${id} composite weak reference.")
            None
        }
      case None ⇒
        // Attach view from viewFactory directly to window.
        //implicit val ec = App.system.dispatcher
        //implicit val timeout = akka.util.Timeout(Timeout.short)
        //context.parent ? Window.Message.Get onSuccess {
        //case wcomposite: WComposite =>
        // Waiting for result
        //App.execNGet { transform.TransformReplace(this, wcomposite, viewFactory) }
        //}
        App.execNGet { transform.TransformReplace(this, null, viewFactory) }
        None
    }
  }
  /** Register created stack element. */
  protected def onCreated(stackLayer: SComposite, sender: ActorRef) {
    pointers += stackLayer.id -> StackSupervisor.StackPointer(sender)(new WeakReference(stackLayer))
  }
  /** Unregister destroyed stack element. */
  protected def onDestroyed(stack: SComposite) {
    val stackOpt = Option(stack.id)
    pointers -= stack.id
    if (lastActiveViewIdForCurrentWindow.get() == stackOpt)
      lastActiveViewIdForCurrentWindow.set(None)
  }
  /** User start interaction with window/stack supervisor. Focus is gained. */
  protected def onStart(widget: Widget) {
    val seq = App.execNGet { UI.widgetHierarchy(widget) }
    if (seq.headOption.map(_.isInstanceOf[VComposite]).getOrElse(false)) {
      // 1st element of hierarchy sequence is VComposite
      log.debug(s"Focus obtained by view widget ${widget}, activate view ${seq.head}.")
      log.debug("New active view hierarchy: " + seq)
      seq match {
        case Seq(view: VComposite, windowContent: WComposite) ⇒
          // View is directly attached to shell.
          setActiveView(view.id, widget)
        case Seq(view: VComposite, stack: SComposite, _*) ⇒
          // View is attached to stack.
          setActiveView(view.id, widget)
        case unexpected ⇒
          log.fatal("Unexpected GUI hierarchy.")
      }
    } else {
      log.debug(s"Focus obtained by non view widget ${widget}, reactivate last view.")
      lastActiveViewIdForCurrentWindow.get().foreach { view ⇒
        val lastActiveViewIdForCurrentWindowGUIHierarchy = pointers.get(view).flatMap(pointer ⇒ Option(pointer.stack.get()).map(view ⇒
          App.execNGet { UI.widgetHierarchy(view) })).getOrElse(Seq[Widget]())
        log.debug("Last active view hierarchy: " + lastActiveViewIdForCurrentWindowGUIHierarchy)
        lastActiveViewIdForCurrentWindowGUIHierarchy match {
          case Seq(view: VComposite, windowContent: WComposite) ⇒
            // View is directly attached to shell.
            setActiveView(view.id, view)
          case Seq(view: VComposite, stack: SComposite, _*) ⇒
            // View is attached to stack.
            setActiveView(view.id, view)
          case Nil ⇒
            log.warn("Last active view GUI hierarchy was gone.")
          case unexpected ⇒
            log.fatal("Unexpected GUI hierarchy.")
        }
      }
    }
  }
  /** Focus is lost. */
  protected def onStop(widget: Widget) {
    lastActiveViewIdForCurrentWindow.get.foreach { viewId ⇒
      val viewPointer = pointers(viewId)
      val seq = App.execNGet { UI.widgetHierarchy(widget) }
      // Replace event widget with view composite if the original one is unknown (not a child of a view).
      val actualWidget = if (seq.headOption.map(_.isInstanceOf[VComposite]).getOrElse(false)) Some(widget) else Option(viewPointer.stack.get())
      actualWidget.foreach(actualWidget ⇒ Await.ready(ask(viewPointer.actor, App.Message.Stop(Left(actualWidget)))(Timeout.short), Timeout.short))
    }
  }
  /** Restore stack configuration. */
  protected def restore(parent: WComposite): Option[SComposite] = {
    log.debug("Restore stack for ${parent}.")
    context.children.foreach(context.stop)
    container = Some(parent)
    create(configuration.stack.id, parent)
    // TODO activate last
  }
  /** Set active view. */
  protected def setActiveView(id: UUID, widget: Widget): Unit = try {
    if (lastActiveViewIdForCurrentWindow.get() != Some(id)) {
      log.debug(s"Set active view ${configurationMap(id)._2} with full id ${id}.")
      lastActiveViewIdForCurrentWindow.set(Option(id))
    }
    Await.ready(ask(pointers(id).actor, App.Message.Start(Left(widget)))(Timeout.short), Timeout.short)
  } catch {
    case e: Throwable ⇒
      log.error(e.getMessage, e)
  }
}

object StackSupervisor extends Loggable {
  /** Singleton identificator. */
  val id = getClass.getSimpleName().dropRight(1)
  // Initialize descendant actor singletons
  StackLayer

  /** StackSupervisor actor reference configuration object. */
  def props = DI.props

  /**
   * Stack pointers map
   * Shut down application on empty.
   */
  class PointerMap(val parent: ActorRef) extends mutable.HashMap[UUID, StackPointer] {
    override def -=(key: UUID): this.type = {
      get(key) match {
        case None ⇒
        case Some(old) ⇒
          super.-=(key)
          if (UI.closeWindowWithLastView &&
            (old.stack.get().isInstanceOf[VComposite] || old.stack.get() == null) &&
            this.find { case (uuid, pointer) ⇒ pointer.stack.get().isInstanceOf[VComposite] }.isEmpty) {
            log.info("There are no views. Close window.")
            parent ! App.Message.Close
          }
      }
      this
    }
    override def clear(): Unit = {
      super.clear()
      if (UI.closeWindowWithLastView) {
        log.info("There are no views. Close window.")
        parent ! App.Message.Close
      }
    }
  }
  /** Wrapper that contains stack layer widgets and ActorRef. */
  case class StackPointer(val actor: ActorRef)(val stack: WeakReference[SComposite])
  /**
   * Dependency injection routines.
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** WindowSupervisor actor reference configuration object. */
    lazy val props = injectOptional[Props]("Core.UI.StackSupervisor") getOrElse Props(classOf[StackSupervisor],
      // window id = stack supervisor id
      UUID.fromString("00000000-0000-0000-0000-000000000000"),
      // parent context
      Core.context)
  }
}
