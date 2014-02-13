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

import akka.actor.{ Actor, ActorRef, PoisonPill, Props, Terminated, actorRef2Scala }
import akka.pattern.{ AskTimeoutException, ask }
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
import org.digimead.tabuddy.desktop.core.ui.block.api.Configuration.CPlaceHolder
import org.digimead.tabuddy.desktop.core.ui.block.builder.ViewContentBuilder
import org.digimead.tabuddy.desktop.core.ui.definition.widget.{ AppWindow, SComposite, SCompositeTab, VComposite, VEmpty, WComposite }
import org.eclipse.swt.custom.ScrolledComposite
import org.eclipse.swt.widgets.Widget
import scala.collection.{ immutable, mutable }
import scala.concurrent.{ Await, Future, TimeoutException }
import scala.language.implicitConversions

/**
 * Stack supervisor responsible for:
 * - restore all view
 * - track views
 * - provide view configuration
 * - save all views configuration
 */
class StackSupervisor(val windowId: UUID, val parentContext: Context.Rich) extends Actor with Loggable {
  /** Akka execution context. */
  implicit lazy val ec = App.system.dispatcher
  /** Akka communication timeout. */
  implicit val timeout = akka.util.Timeout(UI.communicationTimeout)
  /** Stack configuration. */
  val initialConfiguration = StackConfiguration.load(windowId) getOrElse StackConfiguration.default()
  /** Flag indicating whether the configurations save process restart is required. */
  val configurationsSaveRestart = new AtomicBoolean()
  /** Last active view id for this window. */
  val lastActiveViewIdForCurrentWindow = new AtomicReference[Option[UUID]](None)
  /** List of all window layers. */
  val pointers = mutable.HashMap[UUID, StackSupervisor.StackPointer]()
  /** Runnable with callback that invoked after window is ready. */
  var restoreCallback = Option.empty[Runnable]
  /** Flag indicating whether the StackSupervisor is alive. */
  var terminated = false
  /** Top level stack hierarchy container. It is ScrolledComposite of content of AppWindow. */
  var wComposite: Option[WComposite] = None
  /** Window actor. */
  lazy val window = context.parent
  log.debug(s"Start actor ${self.path} ${windowId}")

  /** Is called asynchronously after 'actor.stop()' is invoked. */
  override def postStop() = log.debug(this + " is stopped.")
  /** Is called when an Actor is started. */
  override def preStart() = log.debug(this + " is started.")
  def receive = {
    case message @ App.Message.Create(viewConfiguration: Configuration.CView, None, None) ⇒ App.traceMessage(message) {
      if (terminated) {
        App.Message.Error(s"${this} is terminated.", self)
      } else {
        create(viewConfiguration) match {
          case Some(windowComposite) ⇒
            App.Message.Create(windowComposite, self)
          case None ⇒
            App.Message.Error(s"Unable to create ${viewConfiguration}.", self)
        }
      }
    } foreach { sender ! _ }

    // Notification.
    case message @ App.Message.Create(stackLayer: SComposite, Some(origin), None) ⇒ App.traceMessage(message) {
      assert(context.children.exists(_ == sender))
      if (terminated) {
        App.Message.Error(s"${this} is terminated.", self)
      } else {
        if (App.execNGet { wComposite.map(UI.widgetHierarchy(stackLayer).contains).getOrElse(false) && !stackLayer.isDisposed() })
          onCreated(stackLayer, origin)
      }
    }

    // Notification.
    case message @ App.Message.Destroy(stackLayer: SComposite, Some(origin), None) ⇒ App.traceMessage(message) {
      assert(sender != window)
      if (pointers.isDefinedAt(stackLayer.id))
        onDestroyed(stackLayer, origin)
      if (terminated && pointers.isEmpty)
        window ! App.Message.Destroy(self, self)
    }

    // Notification.
    case message @ App.Message.Destroy(tab: SCompositeTab, Some(origin), Some("toTab")) ⇒ App.traceMessage(message) {
      transform.TransformTabToView(this, tab) match {
        case Right(vComposite) ⇒
          log.debug(s"Transform ${tab} to ${vComposite}.")
        case Left(Some(vComposite)) ⇒
          log.debug(s"Unable to transform ${tab} to ${vComposite}.")
          // view was disposed while message delivery
          if (pointers.isDefinedAt(vComposite.id))
            onDestroyed(vComposite, origin)
        case Left(None) ⇒
          // view was disposed before message delivery
          log.debug(s"Unable to transform ${tab} to VComposite: there are no children.")
      }
    }

    case message @ App.Message.Destroy(windowToDestroy: AppWindow, Some(this.window), None) ⇒ App.traceMessage(message) {
      log.debug(s"Initiate destroy sequence for ${windowToDestroy}.")
      terminated = true
      restoreCallback = None
      val children = context.children
      // Get only pointers that have valid nextLevelActor.
      pointers.values.filter(p ⇒ children.exists(_.path == p.nextLevelActor.path)).flatMap(p ⇒ Option(p.block.get().ref)) match {
        case Nil ⇒
          if (pointers.nonEmpty)
            log.debug(s"Drop unreachable pointers: ${pointers.values.flatMap(p ⇒ Option(p.block.get)).mkString(", ")}.")
          pointers.clear()
          window ! App.Message.Destroy(self, self)
        case children ⇒
          try Await.result(Future.sequence(children.map(_ ? App.Message.Destroy())), timeout.duration)
          catch {
            case e: AskTimeoutException if e.getMessage().endsWith("had already been terminated.") ⇒
              log.debug("AskTimeoutException: " + e.getMessage())
            case e: TimeoutException ⇒
              log.error(s"Unable to stop ${children}.", e)
          }
          // There are maybe children that is terminated manually (TransformTabToView)
          // The usual way for child termination is onDestroy handler.
          // But any another child may follow a different behavior.
          log.debug(s"Delay ${windowToDestroy} destroy sequence. Pending: (${children.map(_.path.name).mkString(", ")}).")
          children.foreach(context.watch)
      }
    }

    case message @ App.Message.Get(Actor) ⇒ App.traceMessage(message) {
      val tree = context.children.map(child ⇒ child -> child ? App.Message.Get(Actor))
      Map(tree.map { case (child, map) ⇒ child -> Await.result(map, timeout.duration) }.toSeq: _*)
    } foreach { sender ! _ }

    case message @ App.Message.Get(Configuration) ⇒ sender ! buildConfiguration()

    case message @ App.Message.Get(Option) ⇒ sender ! lastActiveViewIdForCurrentWindow.get

    case message @ App.Message.Get(Seq) ⇒ sender ! pointers.toSeq

    case message @ App.Message.Restore(content: WComposite, Some(this.window), None) ⇒ App.traceMessage(message) {
      if (terminated) {
        sender ! App.Message.Error(s"${this} is terminated.", self)
      } else {
        // reply to sender generated within future
        restore(content, sender)
      }
    }

    case message @ App.Message.Start(widget: Widget, Some(this.window), None) ⇒ Option {
      if (terminated) {
        App.Message.Error(s"${this} is terminated.", self)
      } else {
        onStart(widget)
        App.Message.Start(widget, self)
      }
    } foreach { sender ! _ }

    case message @ App.Message.Stop(widget: Widget, Some(this.window), None) ⇒ Option {
      if (terminated) {
        App.Message.Error(s"${this} is terminated.", self)
      } else {
        onStop(widget)
        App.Message.Stop(widget, self)
      }
    } foreach { sender ! _ }

    case App.Message.Error(Some(message), _) if message.endsWith("is terminated.") ⇒
      log.debug(message)

    case Terminated(actor) ⇒
      restoreCallback match {
        case Some(callback) ⇒
          if (context.children.isEmpty) {
            // Hooray! Everything is stopped. Restore.
            restoreCallback = None
            callback.run()
          }
        case None ⇒
          pointers.find { case (id, pointer) ⇒ Option(pointer.block.get()).map(_.ref.path) == Some(actor.path) }.foreach {
            case (id, pointer) ⇒
              Option(pointer.block.get()) match {
                case Some(stack) ⇒ onDestroyed(stack, self)
                case None ⇒ pointers -= id
              }
              if (terminated && pointers.isEmpty)
                window ! App.Message.Destroy(self, self)
          }
      }
  }

  /** Build view configuration. */
  def buildConfiguration() = App.execNGet {
    wComposite.map {
      case w if w.isDisposed() ⇒ initialConfiguration
      case w ⇒ StackConfiguration.build(w.getShell)
    } getOrElse initialConfiguration
  }
  /** Create new stack element from configuration. */
  protected def createStackContent(stackId: UUID, parentWidget: ScrolledComposite, configuration: Configuration): Option[SComposite] = {
    if (pointers.contains(stackId))
      throw new IllegalArgumentException(s"Stack with id ${stackId} is already exists.")
    if (!configuration.asMap.contains(stackId))
      throw new IllegalArgumentException(s"Stack with id ${stackId} is unknown.")
    log.debug("Create a top level stack element %s with id %08X=%s.\n%s".
      format(configuration.asMap(stackId)._2, stackId.hashCode(), stackId, configuration.dump().mkString(",")))
    configuration.asMap(stackId) match {
      case (parent, stackConfiguration: Configuration.Stack) ⇒
        log.debug(s"Attach ${stackConfiguration} as top level element.")
        val stackLayerRef = context.actorOf(StackLayer.props.copy(args = immutable.Seq(stackConfiguration.id, parentContext)), StackLayer.name(stackConfiguration.id))
        pointers += stackId -> StackSupervisor.StackPointer(stackLayerRef)(new WeakReference(null))
        val stackWidgetFuture = stackLayerRef ? App.Message.Create(StackLayer.<>(stackConfiguration, parentWidget), self)
        Await.result(stackWidgetFuture, timeout.duration) match {
          case App.Message.Create(stackWidget: SComposite, Some(stackLayerRef), _) ⇒
            log.debug(s"Stack layer ${stackConfiguration} content created.")
            Some(stackWidget)
          case App.Message.Error(error, None) ⇒
            log.fatal(s"Unable to create content for stack layer ${stackConfiguration}: ${error.getOrElse("unknown")}.")
            None
          case _ ⇒
            log.fatal(s"Unable to create content for stack layer ${stackConfiguration}.")
            None
        }
      case (parent, viewConfiguration: Configuration.CView) ⇒
        // There is only a view that is directly attached to the window.
        log.debug(s"Attach ${viewConfiguration} as top level element.")
        val view = ViewContentBuilder.container(viewConfiguration, parentWidget, parentContext, context, None)
        view.foreach { vComposite ⇒ pointers += vComposite.id -> StackSupervisor.StackPointer(vComposite.ref)(new WeakReference(null)) }
        view
      case (parent, viewConfiguration: Configuration.CEmpty) ⇒
        // Skip an empty configuration.
        Some(App.execNGet { new VEmpty(stackId, parentWidget) })
      case (parent, unexpected: CPlaceHolder) ⇒
        log.fatal("Unexpected configuration: " + unexpected)
        None
    }
  }
  /** Create new view within stack hierarchy. */
  protected def create(viewConfiguration: Configuration.CView): Option[VComposite] = {
    if (wComposite.isEmpty)
      throw new IllegalStateException(s"Unable to create view from ${viewConfiguration}. Stack container isn't created.")
    App.assertEventThread(false)
    val neighborViewId = lastActiveViewIdForCurrentWindow.get() orElse {
      pointers.find {
        case (id, pointer) ⇒ Option(pointer.block.get()).
          map(_.isInstanceOf[VComposite]).getOrElse(false)
      }.map { case (id, pointer) ⇒ id }
    }
    log.debug("Create new %s within StackSupervisor[%08X]; neighbor %s.".
      format(viewConfiguration, windowId.hashCode(), neighborViewId.map(id ⇒ "CView[%08X]".format(id.hashCode()))))
    neighborViewId match {
      case Some(id) ⇒
        // Attach view from viewFactory to neighbor.
        Option(pointers(id).block.get()) match {
          case Some(view: VComposite) ⇒
            transform.TransformViewToTab(this, view).flatMap { tab ⇒
              Await.result(tab.ref ? App.Message.Create(StackLayer.<+>(viewConfiguration), self), timeout.duration) match {
                case App.Message.Create(vComposite: VComposite, Some(tab.ref), _) ⇒
                  Some(vComposite)
                case App.Message.Error(message, _) ⇒
                  log.fatal(s"Unable to attach ${viewConfiguration} to ${tab}: ${message.getOrElse("UNKNOWN")}.")
                  None
              }
            }
          case _ ⇒
            log.fatal(s"Lost weak reference to ${id} view.")
            None
        }
      case None ⇒
        val vComposite = for {
          wComposite ← wComposite
          appWindow ← wComposite.getAppWindow()
        } yield transform.TransformReplace(this, appWindow, viewConfiguration)
        vComposite.flatten
    }
  }
  /** Register created stack element. */
  @log
  protected def onCreated(stack: SComposite, origin: ActorRef) {
    log.debug(s"Add created ${stack} to ${this}.")
    pointers += stack.id -> StackSupervisor.StackPointer(sender)(new WeakReference(stack))
    stack match {
      case vComposite: VComposite ⇒
        if (Core.context.getActiveLeaf().get(classOf[VComposite]) == null)
          onStart(vComposite)
      case _ ⇒
    }
    App.publish(App.Message.Create(stack, origin))
  }
  /** Unregister destroyed stack element. */
  @log
  protected def onDestroyed(stack: SComposite, origin: ActorRef) {
    log.debug(s"Remove destroyed ${stack} from ${this}, keep ${pointers.size - 1} element(s).")
    // 1. commit delete
    // stop any child
    stack.ref ! PoisonPill
    pointers -= stack.id
    // remove dependent (unreachable) elements
    val dependentPointers = pointers.filter { case (id, pointer) ⇒ pointer.nextLevelActor == stack.ref && id != stack.id }.map(_._1).toSeq
    if (dependentPointers.nonEmpty) {
      val strPointers = dependentPointers.map(id ⇒ "%08X".format(id.hashCode())).mkString(",")
      log.debug(s"Remove sub element(s) ${strPointers}, keep ${pointers.size - dependentPointers.size} element(s).")
      pointers --= dependentPointers
    }
    if (lastActiveViewIdForCurrentWindow.get() == Option(stack.id))
      lastActiveViewIdForCurrentWindow.set(None)
    App.publish(App.Message.Destroy(stack, origin))
    // 2. run restoreCallback or close window
    restoreCallback match {
      case Some(callback) ⇒
        if (pointers.isEmpty)
          context.children match {
            case Nil ⇒
              restoreCallback = None
              callback.run()
            case seq ⇒
              seq.foreach(context.watch) // still waiting for children
          }
      case None ⇒
        if (UI.closeWindowWithLastView &&
          !pointers.exists { case (uuid, pointer) ⇒ pointer.block.get().isInstanceOf[VComposite] }) {
          log.info("There are no views. Close window.")
          Await.result(window ? App.Message.Close(), timeout.duration)
        }
    }
  }
  /** User start interaction with window/stack supervisor. Focus is gained. */
  //@log
  protected def onStart(widget: Widget) {
    val actualHierarchy = App.execNGet { UI.widgetHierarchy(widget) }
    if (actualHierarchy.isEmpty && widget.isInstanceOf[SComposite]) {
      log.debug(s"Skip onStart event for unbinded ${widget}.")
      return
    }
    val toStart = if (actualHierarchy.headOption.map(_.isInstanceOf[VComposite]).getOrElse(false)) {
      (Some(widget), actualHierarchy)
    } else {
      lastActiveViewIdForCurrentWindow.get() match {
        case Some(viewId) ⇒
          pointers.get(viewId).flatMap(pointer ⇒ Option(pointer.block.get())) match {
            case Some(view) ⇒
              App.execNGet { UI.widgetHierarchy(view) } match {
                case Nil ⇒
                  log.debug(s"Last view ${view} is unbound. Skip onStart event.")
                  (None, Seq[SComposite]())
                case hierarchy ⇒
                  (Some(view), hierarchy)
              }
            case None ⇒
              (None, Seq[SComposite]())
          }
        case None ⇒
          (None, Seq[SComposite]())
      }
    }
    toStart match {
      case (Some(widget), hierarchy @ Seq(view: VComposite, windowContent: WComposite)) ⇒
        // View is directly attached to shell.
        onStart(view.id, widget, hierarchy.reverse.tail)
      case (Some(widget), hierarchy @ Seq(view: VComposite, stack: SComposite, _*)) ⇒
        // View is attached to stack.
        onStart(view.id, widget, hierarchy.reverse.tail)
      case (Some(widget), unexpected) ⇒
        log.fatal(s"Unexpected hierarchy ${unexpected} for widget ${widget}.")
      case (None, Nil) ⇒
    }
  }
  /** Set active view. */
  protected def onStart(id: UUID, widget: Widget, hierarchyFromWindowToWidget: Seq[SComposite]): Unit = try {
    if (lastActiveViewIdForCurrentWindow.get() != Some(id))
      lastActiveViewIdForCurrentWindow.set(Option(id))
    val pointer = pointers(id)
    val disposed = App.execNGet {
      val sComposite = pointer.block.get()
      sComposite == null || sComposite.isDisposed()
    }
    if (disposed)
      return
    try Await.ready(pointers(id).nextLevelActor ? App.Message.Start((widget, hierarchyFromWindowToWidget), self), UI.focusTimeout)
    catch {
      case e: TimeoutException ⇒ log.debug(s"${pointers(id).nextLevelActor} is unreachable for App.Message.Start.")
    }
  } catch {
    case e: Throwable ⇒
      log.error(e.getMessage, e)
  }
  /** Focus is lost. */
  protected def onStop(widget: Widget) {
    lastActiveViewIdForCurrentWindow.get.foreach { id ⇒
      val pointer = pointers(id)
      val (hierarchy, disposed) = App.execNGet {
        val sComposite = pointer.block.get()
        (UI.widgetHierarchy(widget), sComposite == null || sComposite.isDisposed())
      }
      if (disposed)
        return
      // Replace event widget with view composite if the original one is unknown (not a child of a view).
      val (widgetToStop, hierarchyToStop) = hierarchy.headOption match {
        case Some(view: VComposite) ⇒
          (widget, hierarchy)
        case _ ⇒
          val view = pointer.block.get()
          (view, App.execNGet { UI.widgetHierarchy(view) }) // if view is null then hierarchyToStop is empty
      }
      if (hierarchyToStop.nonEmpty) {
        // hierarchyToStop is a hierarchy from the widget to the window without the window WComposite.
        try Await.ready(pointer.nextLevelActor ? App.Message.Stop((widgetToStop, hierarchyToStop.reverse.tail), self), UI.focusTimeout)
        catch {
          case e: TimeoutException ⇒ log.debug(s"${pointer.nextLevelActor} is unreachable for App.Message.Stop.")
        }
      }
    }
  }
  /** Restore stack configuration. */
  protected def restore(parent: WComposite, sender: ActorRef) {
    if (restoreCallback.isEmpty) {
      log.debug(s"Restore stack for ${parent}.")
      if (pointers.nonEmpty && buildConfiguration() == initialConfiguration) {
        sender ! App.Message.Restore(Option(pointers(initialConfiguration.stack.id).block.get()), self)
      } else {
        val futures = context.children.map(_ ? App.Message.Destroy())
        val result = Await.result(Future.sequence(futures), Timeout.short)
        val errors = result.filter(_ match {
          case App.Message.Error(message, _) ⇒ true
          case _ ⇒ false
        })
        if (errors.isEmpty) {
          val runnable = new Runnable {
            def run {
              wComposite = Some(parent)
              val result = createStackContent(initialConfiguration.stack.id, parent, initialConfiguration)
              // TODO activate last
              sender ! App.Message.Restore(result, self)
            }
          }
          if (pointers.isEmpty)
            runnable.run() // There are no children, only top element
          else
            restoreCallback = Some(runnable) // There are children. Waiting for termination.
        } else {
          errors.foreach(err ⇒ log.error(s"Unable to : ${err.asInstanceOf[App.Message.Error].message}"))
          sender ! App.Message.Error(s"Unable to restore content for ${parent}.", self)
        }
      }
    } else {
      val message = s"Restoration process for ${parent} is already in progress."
      log.debug(message)
      sender ! App.Message.Error(message, self)
    }
  }

  override lazy val toString = "StackSupervisor[actor/%08X]".format(windowId.hashCode())
}

object StackSupervisor extends Loggable {
  /** Singleton identificator. */
  val id = getClass.getSimpleName().dropRight(1)
  // Initialize descendant actor singletons
  StackLayer

  /** Get stack supervisor name. */
  def name(id: UUID) = StackSupervisor.id + "_%08X".format(id.hashCode())
  /** StackSupervisor actor reference configuration object. */
  def props = DI.props

  override def toString = "StackSupervisor[Singleton]"

  /**
   * Wrapper that contains UI block and ActorRef to the next level after stack supervisor.
   *
   * @param nextLevelActor direct stack supervisor child
   * @param block UI block
   */
  case class StackPointer(val nextLevelActor: ActorRef)(val block: WeakReference[SComposite])
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
