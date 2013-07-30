/**
 * This file is part of the TABuddy project.
 * Copyright (c) 2013 Alexey Aksenov ezh@ezh.msk.ru
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
 * that is created or manipulated using TABuddy.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license. Buying such a license is mandatory as soon as you
 * develop commercial activities involving the TABuddy software without
 * disclosing the source code of your own applications.
 * These activities include: offering paid services to customers,
 * serving files in a web or/and network application,
 * shipping TABuddy with a closed source product.
 *
 * For more information, please contact Digimead Team at this
 * address: ezh@ezh.msk.ru
 */

package org.digimead.tabuddy.desktop.gui

import java.lang.ref.WeakReference
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

import scala.collection.immutable
import scala.collection.mutable
import scala.concurrent.Future

import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.Core
import org.digimead.tabuddy.desktop.gui.StackConfiguration.configuration2implementation
import org.digimead.tabuddy.desktop.gui.widget.SComposite
import org.digimead.tabuddy.desktop.gui.widget.VComposite
import org.digimead.tabuddy.desktop.gui.widget.WComposite
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.App.app2implementation
import org.digimead.tabuddy.desktop.support.Timeout
import org.eclipse.e4.core.internal.contexts.EclipseContext
import org.eclipse.swt.custom.ScrolledComposite
import org.eclipse.swt.widgets.Shell
import org.eclipse.swt.widgets.Widget

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.pattern.ask

import language.implicitConversions

/**
 * Stack supervisor responsible for:
 * - restore all view
 * - track views
 * - provide view configuration
 * - save all views configuration
 */
class StackSupervisor(val parentContext: EclipseContext) extends Actor with Loggable {
  /** Stack configuration. */
  val configuration = new StackSupervisor.AtomicConfiguration(StackConfiguration.default)
  /** Reference to configurations save process future. */
  val configurationsSave = new AtomicReference[Option[Future[_]]](None)
  /** Flag indicating whether the configurations save process restart is required. */
  val configurationsSaveRestart = new AtomicBoolean()
  /** Top level stack hierarchy container. It is ScrolledComposite of content of WComposite. */
  @volatile var container: Option[ScrolledComposite] = None
  /** Last active view id. */
  val lastActiveView = new AtomicReference[Option[UUID]](None)
  /** List of all window stacks. */
  val pointers = new StackSupervisor.PointerMap()
  /** Window/StackSupervisor id. */
  lazy val supervisorId = UUID.fromString(self.path.parent.name.split("@").last)
  log.debug("Start actor " + self.path)

  /** Is called asynchronously after 'actor.stop()' is invoked. */
  override def postStop() {
    App.system.eventStream.unsubscribe(self, classOf[App.Message.Created[_]])
    App.system.eventStream.unsubscribe(self, classOf[App.Message.Destroyed[_]])
    log.debug(self.path.name + " actor is stopped.")
  }
  /** Is called when an Actor is started. */
  override def preStart() {
    App.system.eventStream.subscribe(self, classOf[App.Message.Created[_]])
    App.system.eventStream.subscribe(self, classOf[App.Message.Destroyed[_]])
    log.debug(self.path.name + " actor is started.")
  }
  def receive = {
    case message @ App.Message.Attach(props, name) => App.traceMessage(message) {
      sender ! context.actorOf(props, name)
    }
    case message @ App.Message.Create(viewFactory: ViewLayer.Factory, supervisor) => App.traceMessage(message) {
      create(viewFactory)
    }
    case message @ App.Message.Created(stack: SComposite, sender) => App.traceMessage(message) {
      onCreated(stack, sender)
    }
    case message @ App.Message.Destroyed(stack: SComposite, sender) => App.traceMessage(message) {
      onDestroyed(stack, sender)
    }
    case message @ App.Message.Restore(content: ScrolledComposite, sender) => App.traceMessage(message) {
      restore(content)
    }
    case message @ App.Message.Save => App.traceMessage(message) {
      save()
    }
    case message @ App.Message.Start(widget: Widget, supervisor) => App.traceMessage(message) {
      onStart(widget)
    }

    case message @ App.Message.Created(_, sender) =>
    case message @ App.Message.Destroyed(_, sender) =>
    case message @ App.Message.Started(_, sender) =>
    case message @ App.Message.Stopped(_, sender) =>
  }

  /** Create new stack element from configuration */
  protected def create(stackId: UUID, parentWidget: ScrolledComposite) {
    log.debug(s"Create a top level stack element with id ${stackId}.")
    if (pointers.contains(stackId))
      throw new IllegalArgumentException(s"Stack with id ${stackId} is already exists.")
    if (!configuration.element.contains(stackId))
      throw new IllegalArgumentException(s"Stack with id ${stackId} is unknown.")
    configuration.element(stackId) match {
      case stackConfiguration: org.digimead.tabuddy.desktop.gui.Configuration.Stack =>
        log.debug(s"Attach ${stackConfiguration} as top level element.")
        val stack = context.actorOf(StackLayer.props, StackLayer.id + "@" + stackId.toString())
        pointers += stackId -> StackSupervisor.StackPointer(stack)(new WeakReference(null))
        stack ! App.Message.Create(StackLayer.CreateArgument(stackConfiguration, parentWidget, parentContext), self)
      case viewConfiguration: org.digimead.tabuddy.desktop.gui.Configuration.View =>
        // There is only a view that is directly attached to the window.
        log.debug(s"Attach ${viewConfiguration} as top level element.")
        val view = context.actorOf(ViewLayer.props.copy(args = immutable.Seq[Any](parentContext)), ViewLayer.id + "@" + stackId.toString())
        pointers += stackId -> StackSupervisor.StackPointer(view)(new WeakReference(null))
        view ! App.Message.Create(ViewLayer.CreateArgument(viewConfiguration, parentWidget, parentContext), self)
    }
  }
  /** Create new view within stack hierarchy. */
  @log
  protected def create(viewFactory: ViewLayer.Factory) {
    if (container.isEmpty)
      throw new IllegalStateException(s"Unable to create view from ${viewFactory}. Stack container isn't created.")
    log.debug(s"Create new view from ${viewFactory}.")
    val neighborViewId = lastActiveView.get() orElse {
      pointers.find {
        case (id, pointer) => Option(pointer.stack.get()).
          map(_.isInstanceOf[VComposite]).getOrElse(false)
      }.map { case (id, pointer) => id }
    }
    neighborViewId match {
      case Some(id) =>
        // Attach view from viewFactory to neighborViewId.
        Option(pointers(id).stack.get) match {
          case Some(view: VComposite) =>
            // Waiting for result
            App.execNGet {
              transform.TransformViewToTab(this, view).foreach { tab =>
                transform.TransformAttachView(this, tab, viewFactory)
              }
            }
          case _ =>
            log.fatal(s"Lost view ${id} composite weak reference.")
        }
      case None =>
        // Attach view from viewFactory directly to window.
        implicit val ec = App.system.dispatcher
        implicit val timeout = akka.util.Timeout(Timeout.short)
        context.parent ? Window.Message.Get onSuccess {
          case wcomposite: WComposite =>
            // Waiting for result
            App.execNGet { transform.TransformReplace(this, wcomposite, viewFactory) }
        }
    }
  }
  /** Register created stack element. */
  protected def onCreated(stack: SComposite, sender: ActorRef) {
    pointers += stack.id -> StackSupervisor.StackPointer(sender)(new WeakReference(stack))
    configuration.element.get(stack.id) match {
      case Some(view: Configuration.View) =>
        Option(pointers(view.id).stack.get()) match {
          case Some(viewComposite) =>
            setActiveView(view.id, viewComposite)
          case None =>
            log.fatal(s"Lost view ${view.id} composite weak reference.")
        }
      case _ => //skip
    }
  }
  /** Unregister destroyed stack element. */
  protected def onDestroyed(stack: SComposite, sender: ActorRef) {
    pointers -= stack.id
  }
  /** User start interaction with window/stack supervisor. Focus is gained. */
  protected def onStart(widget: Widget) {
    val seq = App.execNGet { App.widgetHierarchy(widget) }
    if (seq.headOption.map(_.isInstanceOf[VComposite]).getOrElse(false)) {
      // 1st element of hierarchy sequence is VComposite
      log.debug(s"Focus obtained by view widget ${widget}, activate view ${seq.head}.")
      log.debug("New active view hierarchy: " + seq)
      seq match {
        case Seq(view: VComposite, shell: Shell) =>
          log.debug("View is directly attached to shell.")
          setActiveView(view.id, widget)
        case Seq(view: VComposite, stack: SComposite, _*) =>
          log.debug("View is attached to stack.")
          setActiveView(view.id, widget)
        case unexpected =>
          log.fatal("Unexpected GUI hierarchy.")
      }
    } else {
      log.debug(s"Focus obtained by non view widget ${widget}, reactivate last view.")
      lastActiveView.get().foreach { view =>
        val lastActiveViewGUIHierarchy = pointers.get(view).flatMap(pointer => Option(pointer.stack.get()).map(view =>
          App.execNGet { App.widgetHierarchy(view) })).getOrElse(Seq[Widget]())
        log.debug("Last active view hierarchy: " + lastActiveViewGUIHierarchy)
        lastActiveViewGUIHierarchy match {
          case Seq(view: VComposite, shell: Shell) =>
            log.debug("View is directly attached to shell.")
            setActiveView(view.id, view)
          case Seq(view: VComposite, stack: SComposite, _*) =>
            log.debug("View is attached to stack.")
            setActiveView(view.id, view)
          case Nil =>
            log.warn("Last active view GUI hierarchy was gone.")
          case unexpected =>
            log.fatal("Unexpected GUI hierarchy.")
        }
      }
    }
  }
  protected def restore(parent: ScrolledComposite) {
    // TODO destroy all current stacks
    StackConfiguration.load(supervisorId).foreach(configuration.set)
    container = Some(parent)
    create(configuration.get.stack.id, parent)
    // TODO activate last
  }
  /** Save windows configuration. */
  protected def save() {
    /*implicit val ec = App.system.dispatcher
    if (!configurationsSave.compareAndSet(None, Some(future {
      WindowConfiguration.save(immutable.HashMap(configurations.toSeq: _*))
      configurationsSave.set(None)
      if (configurationsSaveRestart.compareAndSet(true, false))
        save()
    }))) configurationsSaveRestart.set(true)*/
  }
  /** Set active view. */
  protected def setActiveView(id: UUID, widget: Widget) {
    log.debug(s"Set active view ${id} - ${configuration.element(id)}.")
    lastActiveView.set(Option(id))
    pointers(id).actor ! App.Message.Start(widget, self)
  }

}

object StackSupervisor {
  implicit def atomicConfiguration2AtomicReference(c: AtomicConfiguration): AtomicReference[Configuration] = c.container
  /** Singleton identificator. */
  val id = getClass.getSimpleName().dropRight(1)
  // Initialize descendant actor singletons
  StackLayer

  /** StackSupervisor actor reference configuration object. */
  def props = DI.props

  class AtomicConfiguration(initial: Configuration) {
    val container = new AtomicReference[Configuration](initial)
    /** Stack configuration map. SComposite UUID -> configuration element. */
    @volatile protected var configurationMap: immutable.HashMap[UUID, Configuration.PlaceHolder] = toMap(initial)
    /** Set new configuration. */
    def set(arg: Configuration) = {
      configurationMap = toMap(arg)
      container.set(arg)
    }
    /** Get configuration map. */
    def element = configurationMap

    /** Extract map from configuration. */
    protected def toMap(configuration: Configuration): immutable.HashMap[UUID, Configuration.PlaceHolder] = {
      var entry = Seq[(UUID, org.digimead.tabuddy.desktop.gui.Configuration.PlaceHolder)]()
      def visit(stack: org.digimead.tabuddy.desktop.gui.Configuration.PlaceHolder) {
        entry = entry :+ stack.id -> stack
        stack match {
          case tab: org.digimead.tabuddy.desktop.gui.Configuration.Stack.Tab =>
            tab.children.foreach(visit)
          case hsash: org.digimead.tabuddy.desktop.gui.Configuration.Stack.HSash =>
            visit(hsash.left)
            visit(hsash.right)
          case vsash: org.digimead.tabuddy.desktop.gui.Configuration.Stack.VSash =>
            visit(vsash.top)
            visit(vsash.bottom)
          case view: org.digimead.tabuddy.desktop.gui.Configuration.View =>
        }
      }
      visit(configuration.stack)
      immutable.HashMap[UUID, org.digimead.tabuddy.desktop.gui.Configuration.PlaceHolder](entry: _*)
    }
  }
  /**
   * Stack pointers map
   * Shut down application on empty.
   */
  class PointerMap extends mutable.HashMap[UUID, StackPointer] with mutable.SynchronizedMap[UUID, StackPointer]
  /** Wrapper that contains stack layer widgets and ActorRef. */
  case class StackPointer(val actor: ActorRef)(val stack: WeakReference[SComposite])
  /**
   * Dependency injection routines.
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** WindowSupervisor actor reference configuration object. */
    lazy val props = injectOptional[Props]("Core.GUI.StackSupervisor") getOrElse Props(classOf[StackSupervisor], Core.context)
  }
}
