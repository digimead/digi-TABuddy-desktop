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

import akka.actor.{ Actor, ActorRef, PoisonPill, Props, ScalaActorRef, actorRef2Scala }
import akka.pattern.ask
import com.google.common.collect.MapMaker
import java.lang.ref.WeakReference
import java.util.UUID
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.{ AtomicBoolean, AtomicReference }
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.XDependencyInjection
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.{ Core, EventLoop }
import org.digimead.tabuddy.desktop.core.definition.Context
import org.digimead.tabuddy.desktop.core.support.{ App, WritableValue }
import org.digimead.tabuddy.desktop.core.ui.UI
import org.digimead.tabuddy.desktop.core.ui.definition.widget.{ AppWindow, WComposite }
import org.eclipse.core.databinding.observable.Observables
import org.eclipse.core.databinding.observable.value.{ IValueChangeListener, ValueChangeEvent }
import org.eclipse.core.internal.databinding.observable.DelayedObservableValue
import org.eclipse.jface.window.{ Window ⇒ JWindow }
import org.eclipse.swt.SWT
import org.eclipse.swt.widgets.{ Event, Listener, Shell, Widget }
import scala.collection.{ immutable, mutable }
import scala.collection.JavaConverters.mapAsScalaMapConverter
import scala.concurrent.{ Await, Future }
import scala.language.implicitConversions

/**
 * Window supervisor responsible for:
 * - start application
 * - restore all windows
 * - track windows
 * - provide window configuration
 * - save all windows configuration
 * - shut down application
 */
class WindowSupervisor extends Actor with XLoggable {
  /** Akka execution context. */
  implicit lazy val ec = App.system.dispatcher
  /** Akka communication timeout. */
  implicit val timeout = akka.util.Timeout(UI.communicationTimeout)
  /** All known window configurations. */
  val configurations = new WindowSupervisor.ConfigurationMap()
  /** Reference to configurations save process future. */
  val configurationsSave = new AtomicReference[Option[Future[_]]](None)
  /** Flag indicating whether the configurations save process restart is required. */
  val configurationsSaveRestart = new AtomicBoolean()
  /** List of all application windows. */
  val pointers = new WindowSupervisor.PointerMap()
  /** Event with the active widget. */
  protected var activeFocusEvent: Option[(UUID, Widget)] = None
  /** Last App.Message.Start/Stop event from FocusListener. */
  protected var lastFocusEvent: Either[(UUID, Widget), (UUID, Widget)] = null

  log.debug("Start actor " + self.path)

  if (App.watch(UI, this).hooks.isEmpty)
    App.watch(UI, this).always().
      makeAfterStart('core_ui_WindowSupervisor__onUIStarted) { onUIStarted() }.
      makeBeforeStop('core_ui_WindowSupervisor__onUIStopped) { onUIStopped() }.sync()

  /** Is called asynchronously after 'actor.stop()' is invoked. */
  override def postStop() {
    val saveFuture = configurationsSave.get
    saveFuture.map(future ⇒ Await.result(future, timeout.duration))
    if (configurationsSaveRestart.get) {
      for (i ← 0 to 10 if saveFuture == configurationsSave.get)
        Thread.sleep(100) // User have limited patience - 1 second is enough
      if (saveFuture != configurationsSave.get)
        configurationsSave.get.map(future ⇒ Await.result(future, timeout.duration))
    }
    App.watch(this) off ()
    log.debug(this + " is stopped.")
  }
  /** Is called when an Actor is started. */
  override def preStart() {
    App.watch(this) on ()
    log.debug(this + " is started.")
  }
  def receive = {
    case message @ App.Message.Create(window: AppWindow, Some(origin), None) ⇒ App.traceMessage(message) {
      if (!pointers.isDefinedAt(window.id))
        onCreated(window, origin)
    }

    case message @ App.Message.Destroy(window: AppWindow, Some(origin), None) ⇒ App.traceMessage(message) {
      if (pointers.isDefinedAt(window.id))
        onDestroyed(window, origin)
    }

    case message @ App.Message.Get(windowId: UUID) ⇒
      try getConfiguration(sender, Some(windowId)) catch { case e: Throwable ⇒ log.error(e.getMessage(), e) }

    case message @ App.Message.Get(None) ⇒
      try getConfiguration(sender, None) catch { case e: Throwable ⇒ log.error(e.getMessage(), e) }

    case message @ App.Message.Get(WindowSupervisor.ConfigurationMap) ⇒
      try sender ! configurations.toMap catch { case e: Throwable ⇒ log.error(e.getMessage(), e) }

    case message @ App.Message.Get(WindowSupervisor.PointerMap) ⇒
      try sender ! pointers.toMap catch { case e: Throwable ⇒ log.error(e.getMessage(), e) }

    // open specific window
    case message @ App.Message.Open(id: UUID, _, None) ⇒ App.traceMessage(message) {
      open(Some(id)) match {
        case Some(uuid) ⇒
          App.Message.Open(uuid, None)
        case None ⇒
          App.Message.Error(s"Unable to open window ${id}.", None)
      }
    } foreach { sender ! _ }

    // open some new window
    case message @ App.Message.Open(None, _, None) ⇒ App.traceMessage(message) {
      open(None) match {
        case Some(uuid) ⇒
          App.Message.Open(uuid, None)
        case None ⇒
          App.Message.Error("Unable to open new window.", None)
      }
    } foreach { sender ! _ }

    // open specific exists or new window
    case message @ App.Message.Open(Some(id: UUID), _, None) ⇒ App.traceMessage(message) {
      open(Some(id)) match {
        case Some(uuid) ⇒
          App.Message.Open(uuid, None)
        case None ⇒
          App.Message.Error(s"Unable to open ${id} window.", None)
      }
    } foreach { sender ! _ }

    // opening in progress
    case message @ App.Message.Open(appWindow: AppWindow, Some(windowSupervisor), Some("Opening in progress.")) ⇒ App.traceMessage(message) {
      log.debug(s"${appWindow} is opening.")
    }

    // window is opened
    case message @ App.Message.Open(appWindow: AppWindow, Some(windowSupervisor), None) ⇒ App.traceMessage(message) {
      App.publish(message)
    }

    case message @ App.Message.Restore(_, _, None) ⇒ App.traceMessage(message) {
      restore()
    }

    case message @ App.Message.Save(_, _, None) ⇒ App.traceMessage(message) {
      save()
    }

    case message @ App.Message.Set(windowId: UUID, configuration: WindowConfiguration) ⇒
      try setConfiguration(sender, windowId, configuration) catch { case e: Throwable ⇒ log.error(e.getMessage(), e) }

    case message @ App.Message.Start((id: UUID, widget: Widget), _, None) ⇒ App.traceMessage(message) {
      // Stop previous active widget if any
      activeFocusEvent match {
        case Some((activeId, activeWidget)) if activeId != id || activeWidget != widget ⇒
          stop(activeId, activeWidget)
        case _ ⇒
      }
      // Start new
      start(id, widget)
    }

    case message @ App.Message.Stop((id: UUID, widget: Widget), _, None) ⇒ App.traceMessage(message) {
      stop(id, widget)
    }
  }

  /** Create new window actor and actor contents. */
  protected def create(windowId: UUID) {
    log.debug(s"Create window ${windowId}.")
    if (pointers.contains(windowId))
      throw new IllegalArgumentException(s"Window with id ${windowId} is already exists.")
    App.assertEventThread(false)
    val windowName = Window.name(windowId)
    val windowContext = Core.context.createChild(windowName): Context.Rich
    val window = context.actorOf(Window.props.copy(args = immutable.Seq(windowId, windowContext)), windowName)
    pointers += windowId -> WindowSupervisor.WindowPointer(window)(new WeakReference(null))
    Await.result(window ? App.Message.Create(Window.<>(windowId, configurations.get(windowId).
      getOrElse(WindowConfiguration.default)), self), timeout.duration) match {
      case message @ App.Message.Create(window: AppWindow, Some(windowActor), _) ⇒
        pointers += window.id -> WindowSupervisor.WindowPointer(windowActor)(new WeakReference(window))
        if (lastActiveWindow.isEmpty) {
          // if there is no last active window
          // this is the 1st...
          if (UI.getActiveShell().isEmpty)
            Core.context.set(UI.Id.shellList, Seq(window.getShell()))
          window.windowContext.activateBranch()
        }
        App.publish(message)
        windowActor ! App.Message.Open(None, self)
      case App.Message.Error(message, _) ⇒
        log.error(s"Unable to create window ${windowId}: ${message}.")
    }
  }
  /** Send exists or default window configuration to sender. */
  @log
  protected def getConfiguration(sender: ActorRef, windowId: Option[UUID]) = windowId match {
    case Some(id: UUID) ⇒
      sender ! (configurations.get(id) getOrElse WindowConfiguration.default)
    case None ⇒
      sender ! WindowConfiguration.default
  }
  /** Get last active window for this application. */
  protected def lastActiveWindow: Option[WComposite] = UI.getActiveWindow().flatMap(_.getContent())
  /** Update/create window pointer with AppWindow value. */
  protected def onCreated(window: AppWindow, sender: ActorRef) {
    pointers.get(window.id).flatMap(ptr ⇒ Option(ptr.appWindowRef.get())) match {
      case Some(appWindow) ⇒
        log.fatal(s"Window ${appWindow} is already created.")
      case None ⇒
        pointers += window.id -> WindowSupervisor.WindowPointer(sender)(new WeakReference(window))
        if (lastActiveWindow.isEmpty) {
          // if there is no last active window
          // this is the 1st...
          if (UI.getActiveShell().isEmpty)
            Core.context.set(UI.Id.shellList, Seq(window.getShell()))
          window.windowContext.activateBranch()
        }
        Await.result(sender ? App.Message.Open(), timeout.duration)
    }
  }
  /** Remove window pointer with AppWindow value. */
  protected def onDestroyed(window: AppWindow, origin: ActorRef) {
    pointers -= window.id
    App.publish(App.Message.Destroy(window, origin))
    window.ref ! PoisonPill
  }
  /** Start global focus listener when GUI is available. */
  @log
  protected def onUIStarted() = App.exec {
    App.display.addFilter(SWT.FocusIn, FocusListener)
    App.display.addFilter(SWT.FocusOut, FocusListener)
    App.display.addFilter(SWT.Activate, FocusListener)
    App.display.addFilter(SWT.Deactivate, FocusListener)
  }
  /** Stop global focus listener when GUI is not available. */
  @log
  protected def onUIStopped() = App.exec {
    App.display.removeFilter(SWT.FocusIn, FocusListener)
    App.display.removeFilter(SWT.FocusOut, FocusListener)
    App.display.removeFilter(SWT.Activate, FocusListener)
    App.display.removeFilter(SWT.Deactivate, FocusListener)
  }
  /** Create the new window or open the exists one. */
  protected def open(windowId: Option[UUID]): Option[UUID] = {
    log.debug(s"Open window ${windowId}.")
    windowId.flatMap(pointers.get) match {
      case Some(pointer) ⇒
        log.debug(s"Window ${windowId} is already exists. Make active exists one.")
        Option(pointer.appWindowRef.get).map { appWindow ⇒
          App.exec { appWindow.getShell().forceActive() }
          appWindow.id
        }
      case None ⇒
        val saved = configurations.keySet -- pointers.keySet
        val id = windowId orElse saved.headOption getOrElse UUID.randomUUID()
        if (configurations.isDefinedAt(id))
          log.debug(s"Configuration found. Restore window ${id}.")
        else
          log.debug(s"Configuration not found. Create new window ${id}.")
        create(id)
        Some(id)
    }
  }
  /** Restore windows from configuration. */
  protected def restore() {
    log.debug(s"Restore windows configuration.")
    // destroy all current windows
    configurations.clear
    WindowConfiguration.load.foreach(kv ⇒ configurations += kv)
    val configurationsSeq = configurations.toSeq
    configurationsSeq.filter(_._2.active) match {
      case Nil ⇒
        // there are no visible windows
        if (configurations.isEmpty) {
          // there are no windows
          val id = UUID.randomUUID()
          log.debug("Create new window " + id)
          create(id)
        } else {
          // there was last visible window
          val lastVisibleId = configurationsSeq.sortBy(-_._2.timestamp).head._1
          log.debug("Restore last active window " + lastVisibleId)
          create(lastVisibleId)
        }
      case visibleWindows ⇒
        // there were visible windows
        visibleWindows.foreach {
          case (id, value) ⇒
            log.debug("Restore active window " + id)
            create(id)
        }
    }
  }
  /** Save windows configuration. */
  protected def save() {
    if (!configurationsSave.compareAndSet(None, Some({
      val future = Future {
        WindowConfiguration.save(immutable.HashMap(configurations.toSeq: _*))
        configurationsSave.set(None)
        if (configurationsSaveRestart.compareAndSet(true, false))
          save()
      }
      future onFailure { case e: Throwable ⇒ log.error(e.getMessage(), e) }
      future
    }))) configurationsSaveRestart.set(true)
  }
  /** Start window. */
  @log
  def start(id: UUID, widget: Widget) {
    if (Left(id, widget) != lastFocusEvent)
      pointers.get(id).foreach { pointer ⇒
        try Await.ready(pointer.windowActor ? App.Message.Start(widget, None), UI.focusTimeout)
        catch {
          case e: TimeoutException ⇒ log.debug(s"${pointer.windowActor} is unreachable for App.Message.Start.")
        }
        lastFocusEvent = Left(id, widget)
        activeFocusEvent = Some((id, widget))
      }
  }
  /** Stop window. */
  @log
  def stop(id: UUID, widget: Widget) {
    if (Right(id, widget) != lastFocusEvent)
      pointers.get(id).foreach { pointer ⇒
        try Await.ready(pointer.windowActor ? App.Message.Stop(widget, None), UI.focusTimeout)
        catch {
          case e: TimeoutException ⇒ log.debug(s"${pointer.windowActor} is unreachable for App.Message.Stop.")
        }
        lastFocusEvent = Right(id, widget)
        activeFocusEvent = None
      }
  }
  /** Update configuration for window. */
  @log
  protected def setConfiguration(sender: ActorRef, windowId: UUID, configuration: WindowConfiguration) = {
    configurations(windowId) = configuration
  }

  /**
   * Global focus listener.
   * Origin of all App.Message.Start events.
   */
  object FocusListener extends Listener() {
    /** Last focus event value. */
    val focusEvent = WritableValue[FocusEvent]
    /** Focus event delay, ms. */
    val antiSpamDelay = 100
    /** Intercepted shells: shell -> timestamp. */
    val shellMap = new MapMaker().weakKeys().makeMap[Shell, Long]().asScala

    Observables.observeDelayedValue(antiSpamDelay, focusEvent).addValueChangeListener(new IValueChangeListener {
      def handleValueChange(event: ValueChangeEvent) =
        event.getObservableValue().asInstanceOf[DelayedObservableValue].getValue().asInstanceOf[FocusEvent].fire()
    })
    def handleEvent(event: Event) {
      if (event.widget != null && event.widget.isInstanceOf[Shell]) {
        val shell = event.widget.asInstanceOf[Shell]
        shellMap += shell -> System.currentTimeMillis()
        //head is last active shell
        val (active, disposed) = shellMap.toSeq.sortBy(-_._2).map(_._1).partition(!_.isDisposed)
        shellMap --= disposed
        Core.context.set(UI.Id.shellList, active)
      }
      UI.findShell(event.widget).foreach { shell ⇒
        Option(shell.getData(UI.swtId).asInstanceOf[UUID]).foreach { id ⇒
          event.`type` match {
            case SWT.FocusIn ⇒
              log.trace("Receive StrictStartFocusEvent for AppWindow[%08X] and %s.".format(id.hashCode(), event.widget))
              focusEvent.value = StrictStartFocusEvent(id, event.widget)
            case SWT.FocusOut ⇒
              log.trace("Receive StrictStopFocusEvent for AppWindow[%08X] and %s.".format(id.hashCode(), event.widget))
              focusEvent.value = StrictStopFocusEvent(id, event.widget)
            case SWT.Activate ⇒
              log.trace("Receive FuzzyStartFocusEvent for AppWindow[%08X] and %s.".format(id.hashCode(), event.widget))
              focusEvent.value match {
                case StrictStartFocusEvent(sid, swidget) if sid == id ⇒ // Skip. Fuzzy couldn't override strict.
                case _ ⇒ focusEvent.value = FuzzyStartFocusEvent(id, event.widget)
              }
            case SWT.Deactivate ⇒
              log.trace("Receive StrictStopFocusEvent for AppWindow[%08X] and %s.".format(id.hashCode(), event.widget))
              focusEvent.value match {
                case StrictStopFocusEvent(sid, swidget) if sid == id ⇒ // Skip. Fuzzy couldn't override strict.
                case _ ⇒ focusEvent.value = FuzzyStopFocusEvent(id, event.widget)
              }
          }
        }
      }
    }
    /** Base trait for focus event. */
    trait FocusEvent {
      def fire()
    }
    /** Start event by SWT.FocusIn. */
    case class StrictStartFocusEvent(val id: UUID, val widget: Widget) extends FocusEvent {
      def fire() {
        log.debug("Focus gained by window %08X for widget %s.".format(id.hashCode(), widget))
        self ! App.Message.Start((id, widget), None)
      }
    }
    /** Start event without explicit focus widget. */
    case class FuzzyStartFocusEvent(val id: UUID, val widget: Widget) extends FocusEvent {
      def fire() {
        log.debug("Window %08X activated.".format(id.hashCode()))
        self ! App.Message.Start((id, widget), None)
      }
    }
    /** Stop event by SWT.FocusOut. */
    case class StrictStopFocusEvent(val id: UUID, val widget: Widget) extends FocusEvent {
      def fire() {
        log.debug("Focus lost by window %08X.".format(id.hashCode()))
        self ! App.Message.Stop((id, widget), None)
      }
    }
    /** Stop event without explicit focus widget. */
    case class FuzzyStopFocusEvent(val id: UUID, val widget: Widget) extends FocusEvent {
      def fire() {
        log.debug("Window %08X deactivated.".format(id.hashCode()))
        self ! App.Message.Stop((id, widget), None)
      }
    }
  }

  override lazy val toString = "WindowSupervisor(actor)"
}

object WindowSupervisor extends XLoggable {
  implicit def windowSupervisor2actorRef(w: WindowSupervisor.type): ActorRef = w.actor
  implicit def windowSupervisor2actorSRef(w: WindowSupervisor.type): ScalaActorRef = w.actor
  /** WindowSupervisor actor reference. */
  lazy val actor = App.getActorRef(App.system.actorSelection(actorPath)) getOrElse {
    throw new IllegalStateException("Unable to locate actor with path " + actorPath)
  }
  /** WindowSupervisor actor path. */
  lazy val actorPath = UI.path / id
  /** Get window id. */
  def getId(window: AppWindow) = Option(window.id)
  /** Get window id. */
  def getId(window: JWindow) = Option(window.getShell().getData(UI.swtId).asInstanceOf[UUID])
  /** Singleton identificator. */
  val id = getClass.getSimpleName().dropRight(1)
  // Initialize descendant actor singletons
  Window

  /** WindowSupervisor actor reference configuration object. */
  def props = DI.props

  override def toString = "WindowSupervisor[Singleton]"

  /**
   * Configurations map.
   */
  class ConfigurationMap extends mutable.HashMap[UUID, WindowConfiguration]
  object ConfigurationMap
  /**
   * Window pointers map.
   *
   * Shut down application on empty if UI.stopEventLoopWithLastWindow is true.
   */
  class PointerMap extends mutable.HashMap[UUID, WindowPointer] {
    override def -=(key: UUID): this.type = {
      get(key) match {
        case None ⇒
        case Some(old) ⇒
          super.-=(key)
          if (isEmpty && UI.stopEventLoopWithLastWindow) {
            log.info("There are no windows. Shutdown main loop.")
            WindowSupervisor ! App.Message.Save()
            EventLoop.runnable.stopEventLoop(EventLoop.Code.Ok)
          }
      }
      this
    }
    override def clear(): Unit = {
      super.clear()
      if (UI.stopEventLoopWithLastWindow) {
        log.info("There are no windows. Shutdown main loop.")
        WindowSupervisor ! App.Message.Save()
        EventLoop.runnable.stopEventLoop(EventLoop.Code.Ok)
      }
    }
  }
  object PointerMap
  /** Wrapper that contains window and ActorRef. */
  case class WindowPointer(val windowActor: ActorRef)(val appWindowRef: WeakReference[AppWindow])
  /**
   * Dependency injection routines.
   */
  private object DI extends XDependencyInjection.PersistentInjectable {
    /** WindowSupervisor actor reference configuration object. */
    lazy val props = injectOptional[Props]("Core.UI.WindowSupervisor") getOrElse Props[WindowSupervisor]
  }
}
