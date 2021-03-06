/**
 * This file is part of the TA Buddy project.
 * Copyright (c) 2013-2015 Alexey Aksenov ezh@ezh.msk.ru
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

import akka.actor.{ Actor, ActorRef, PoisonPill, Props, actorRef2Scala }
import akka.pattern.ask
import java.util.UUID
import java.util.concurrent.TimeoutException
import java.util.concurrent.locks.ReentrantReadWriteLock
import org.digimead.digi.lib.api.XDependencyInjection
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.{ Core, Messages }
import org.digimead.tabuddy.desktop.core.definition.Context
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.ui.UI
import org.digimead.tabuddy.desktop.core.ui.definition.widget.AppWindow
import org.digimead.tabuddy.desktop.core.ui.support.Generic
import org.eclipse.e4.core.contexts.ContextInjectionFactory
import org.eclipse.swt.widgets.Widget
import scala.collection.{ immutable, mutable }
import scala.concurrent.{ Await, Future }

/**
 * Actor that represents application window, responsible for:
 * - create window
 * - open window
 * - start window
 * - close/destroy window
 */
class Window(val windowId: UUID, val windowContext: Context.Rich) extends Actor with AppWindow.Controller with XLoggable {
  /** Akka execution context. */
  implicit lazy val ec = App.system.dispatcher
  /** Akka communication timeout. */
  implicit val timeout = akka.util.Timeout(UI.communicationTimeout)
  /** Window views supervisor. */
  lazy val stackSupervisor = context.actorOf(StackSupervisor.props.copy(args = immutable.Seq(windowId, windowContext)), StackSupervisor.name(windowId))
  /** Flag indicating whether the Window is alive. */
  var stackSupervisorTerminated = false
  /** Flag indicating whether the Window is alive. */
  var terminated = false
  /** Window JFace instance. */
  var window: Option[AppWindow] = None
  /** Window supervisor actor. */
  lazy val windowSupervisor = context.parent
  log.debug(s"Start actor ${self.path} ${windowId}")

  /** Is called asynchronously after 'actor.stop()' is invoked. */
  override def postStop() = log.debug(this + " is stopped.")
  /**
   * Is called on a crashed Actor right BEFORE it is restarted to allow clean
   * up of resources before Actor is terminated.
   */
  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    log.debug(this + " is restarted.")
    super.preRestart(reason, message)
  }
  /** Is called when an Actor is started. */
  override def preStart() = log.debug(this + " is started.")

  def receive = {
    case message @ App.Message.Close(_, _, None) ⇒ App.traceMessage(message) {
      close(sender) match {
        case Some(appWindow) ⇒
          App.Message.Close(appWindow, None)
        case None ⇒
          App.Message.Error(s"Unable to close ${window}.", None)
      }
    } foreach { sender ! _ }

    case message @ App.Message.Create(Window.<>(windowId, configuration), Some(this.windowSupervisor), None) ⇒ App.traceMessage(message) {
      assert(windowId == this.windowId)
      if (terminated) {
        App.Message.Error(s"${this} is terminated.", self)
      } else {
        create(configuration, sender) match {
          case Some(appWindow) ⇒
            App.Message.Create(appWindow, self)
          case None ⇒
            App.Message.Error(s"Unable to create window with ${configuration}.", self)
        }
      }
    } foreach { sender ! _ }

    // StackSupervisor is terminated.
    case message @ App.Message.Destroy(this.stackSupervisor, Some(this.stackSupervisor), None) ⇒ App.traceMessage(message) {
      stackSupervisorTerminated = true
      if (terminated)
        window match {
          case Some(window) ⇒
            windowSupervisor ! App.Message.Destroy(window, self)
          case None ⇒
            // There is no window. Strange. Is this actor destroyed immediately? Stop it silently.
            self ! PoisonPill
        }
    }

    // Destroy this window.
    case message @ App.Message.Destroy(_, _, None) ⇒ App.traceMessage(message) {
      if (terminated) {
        App.Message.Error(s"${this} is terminated.", self)
      } else {
        destroy(sender) match {
          case Some(appWindow) ⇒
            App.Message.Destroy(appWindow, None)
          case None ⇒
            App.Message.Error(s"Unable to destroy ${window}.", None)
        }
      }
      if (stackSupervisorTerminated)
        window match {
          case Some(window) ⇒
            windowSupervisor ! App.Message.Destroy(window, self)
          case None ⇒
            self ! PoisonPill
        }
    } foreach { sender ! _ }

    case message @ App.Message.Get(AppWindow) ⇒ App.traceMessage(message) {
      window
    } foreach { sender ! _ }

    case message @ App.Message.Get(Actor) ⇒ App.traceMessage(message) {
      val tree = context.children.map(child ⇒ child -> child ? App.Message.Get(Actor))
      Map(self -> Map(tree.map { case (child, map) ⇒ child -> Await.result(map, timeout.duration) }.toSeq: _*))
    } foreach { sender ! _ }

    // Asynchronous routine that creates initial window and passes it to AppWindow.showContent.
    case message @ App.Message.Open(_, Some(this.windowSupervisor), None) ⇒ App.traceMessage(message) {
      open(sender) match {
        case Some(appWindow) ⇒
          App.Message.Open(appWindow, self, "Opening in progress.")
        case None ⇒
          App.Message.Error(s"Unable to open ${window}.", None)
      }
    } foreach { sender ! _ }

    // Reply from AppWindow.showContent on success.
    case message @ App.Message.Open(window: AppWindow, Some(this.self), None) ⇒ App.traceMessage(message) {
      log.debug(s"${window} content created and shown.")
      windowSupervisor ! message
    }

    case message @ App.Message.Restore(_, _, None) ⇒ App.traceMessage(message) {
      val reply = for {
        window ← window
        content ← window.getContent()
      } yield Await.result(stackSupervisor ? App.Message.Restore(content, self), timeout.duration)
      reply getOrElse App.Message.Error(s"Unable to restore content for ${this}.", self)
    } foreach { sender ! _ }

    case message @ App.Message.Start(widget: Widget, _, None) ⇒ Option {
      if (terminated) {
        App.Message.Error(s"${this} is terminated.", self)
      } else {
        try onStart(widget) catch { case e: Throwable ⇒ log.error(e.getMessage(), e) }
        App.Message.Start(widget, self)
      }
    } foreach { sender ! _ }

    case message @ App.Message.Stop(widget: Widget, _, None) ⇒ Option {
      if (terminated) {
        App.Message.Error(s"${this} is terminated.", self)
      } else {
        try onStop(widget) catch { case e: Throwable ⇒ log.error(e.getMessage(), e) }
        App.Message.Stop(widget, self)
      }
    } foreach { sender ! _ }
  }

  /** Close window. */
  protected def close(sender: ActorRef): Option[AppWindow] = {
    log.debug(s"Close window ${windowId}.")
    this.window.flatMap { window ⇒
      val closed = App.execNGet {
        if (window.getShell() != null && !window.getShell().isDisposed()) window.close() else true
      }
      if (closed) {
        stackSupervisor ! App.Message.Destroy(window, self)
        terminated = true
        Some(window)
      } else {
        None
      }
    }
  }
  /** Create window. */
  protected def create(configuration: WindowConfiguration, supervisor: ActorRef): Option[AppWindow] = {
    if (this.window.nonEmpty)
      throw new IllegalStateException("Unable to create window. It is already created.")
    App.assertEventThread(false)
    log.debug(s"Create window ${windowId}.")
    this.window = App.execNGet {
      windowContext.set("StackSupervisorActorRef", stackSupervisor)
      windowContext.set(UI.Id.windowTitle, Messages.TABuddyDesktop)
      windowContext.set(classOf[ActorRef], self)
      windowContext.set(classOf[UUID], windowId)
      windowContext.set(classOf[WindowConfiguration], configuration)
      val window = ContextInjectionFactory.make(classOf[AppWindow], windowContext)
      // Add new window to the common map.
      Window.windowMapRWL.writeLock().lock()
      try {
        Window.instanceCounter += 1
        Window.windowMap(window.id) = (Window.instanceCounter, window)
        val windows = Window.windowMap.toSeq
        Future { Window.titleSynchronizer.notify(windows.sortBy(_._2._1).map(_._2._2)) }. // sort by instanceCounter
          onFailure { case e: Throwable ⇒ log.error(e.getMessage(), e) }
      } finally Window.windowMapRWL.writeLock().unlock()
      Option(window)
    }(App.LongRunnable) // sometimes it may be longer than 1000ms
    this.window
  }
  /** Destroy created window. */
  protected def destroy(sender: ActorRef): Option[AppWindow] = this.window.flatMap { window ⇒
    log.debug(s"Destroy window ${windowId}.")
    window.saveOnClose = false
    close(sender)
  }
  /** User start interaction with window. Focus is gained. */
  //@log
  protected def onStart(widget: Widget) = window match {
    case Some(window) ⇒
      windowContext.activateBranch()
      try Await.result(stackSupervisor ? App.Message.Start(widget, self), UI.focusTimeout)
      catch {
        case e: TimeoutException ⇒ log.debug(s"${stackSupervisor} is unreachable for App.Message.Start.")
      }
    case None ⇒
      // Is window deleted while event was delivered?
      log.debug(s"Unable to start unexists window for ${this}.")
  }
  /** Focus is lost. */
  //@log
  protected def onStop(widget: Widget) = window match {
    case Some(window) ⇒
      try Await.result(stackSupervisor ? App.Message.Stop(widget, self), UI.focusTimeout)
      catch {
        case e: TimeoutException ⇒ log.debug(s"${stackSupervisor} is unreachable for App.Message.Stop.")
      }
    case None ⇒
      // Is window deleted while event was delivered?
      log.debug(s"Unable to stop unexists window for ${this}.")
  }
  /** Open created window. */
  protected def open(sender: ActorRef): Option[AppWindow] = {
    log.debug(s"Open window ${windowId}.")
    this.window.flatMap { window ⇒
      val opened = App.execNGet {
        if (window.getShell() == null || (window.getShell() != null && !window.getShell().isDisposed()))
          window.open() == org.eclipse.jface.window.Window.OK
        else
          false
      }(App.LongRunnable)
      if (opened)
        Some(window)
      else
        None
    }
  }

  override lazy val toString = "Window[actor/%08X]".format(windowId.hashCode())
}

object Window extends XLoggable {
  /** Akka execution context. */
  implicit lazy val ec = App.system.dispatcher
  /** Singleton identificator. */
  val id = getClass.getSimpleName().dropRight(1)
  /** Window instance counter. */
  // There are only 2^64 - 1 windows. Please restart this software before the limit will be reached. ;-)
  protected var instanceCounter = Long.MinValue
  /** All application windows. */
  protected val windowMap = mutable.HashMap[UUID, (Long, AppWindow)]()
  /** Window map lock. */
  protected val windowMapRWL = new ReentrantReadWriteLock
  // Initialize descendant actor singletons
  StackSupervisor

  /** Get stack supervisor actor. */
  def getStackSupervisor(windowActor: ActorRef): ActorRef =
    App.getActorRef(windowActor.path / windowActor.path.name.replaceAll(Window.id, StackSupervisor.id)) getOrElse {
      throw new IllegalStateException(s"Unable to get StackSupervisor for ${windowActor}.")
    }
  /** Get window name. */
  def name(id: UUID) = Window.id + "_%08X".format(id.hashCode())
  /** Window actor reference configuration object. */
  def props = DI.props
  /** Application windows title synchronizer. */
  def titleSynchronizer = DI.titleSynchronizer

  override def toString = "Window[Singleton]"

  /**
   * Wrapper for App.Message.Create argument.
   */
  case class <>(val windowId: UUID, val configuration: WindowConfiguration) {
    override lazy val toString = "<>([%08X]=%s, %s)".format(windowId.hashCode(), windowId.toString(), configuration)
  }
  /**
   * Window's context title synchronizer.
   */
  class TitleSynchronizer extends Generic.TitleSynchronizer[AppWindow] {
    /** Update window's contexts. */
    protected def synchronize(windows: Seq[AppWindow]) {
      var n = 0
      windows.foreach { window ⇒
        n += 1
        if (n > 1)
          window.windowContext.set(UI.Id.windowTitle, s"${Messages.TABuddyDesktop}(${n})")
        else
          window.windowContext.set(UI.Id.windowTitle, Messages.TABuddyDesktop)
      }
    }
  }
  /**
   * Window map consumer.
   */
  trait WindowMapConsumer {
    /** Get map with all application windows. */
    def windowMap: immutable.Map[UUID, AppWindow] = {
      Window.windowMapRWL.readLock().lock()
      try Map(Window.windowMap.map { case (id, (n, appWindow)) ⇒ (id, appWindow) }.toSeq: _*)
      finally Window.windowMapRWL.readLock().unlock()
    }
    /** Get map with all application windows with instance counter. */
    def windowMapExt: immutable.Map[UUID, (Long, AppWindow)] = {
      Window.windowMapRWL.readLock().lock()
      try Window.windowMap.toMap
      finally Window.windowMapRWL.readLock().unlock()
    }
  }
  /**
   * Window map disposer.
   */
  trait WindowMapDisposer {
    this: AppWindow ⇒
    /** Remove this AppWindow from the common map. */
    def windowRemoveFromCommonMap() {
      // Remove window from the common map.
      Window.windowMapRWL.writeLock().lock()
      try {
        Window.windowMap -= this.id
        val windows = Window.windowMap.toSeq
        Future {
          val sorted = windows.sortBy(_._2._1).map(_._2._2) // sort by instanceCounter
          if (!App.display.isDisposed())
            App.exec {
              sorted.filter(w ⇒ w.getShell() != null && !w.getShell().isDisposed()).lastOption.foreach { lastWindow ⇒
                // Activate last window.
                lastWindow.windowContext.activate()
                lastWindow.getShell().setActive()
                lastWindow.getShell().forceActive()
                lastWindow.getShell().forceFocus()
              }
            }
          Window.titleSynchronizer.notify(sorted)
        }.onFailure { case e: Throwable ⇒ log.error(e.getMessage(), e) }
      } finally Window.windowMapRWL.writeLock().unlock()
    }
  }
  /**
   * Dependency injection routines.
   */
  private object DI extends XDependencyInjection.PersistentInjectable {
    /** Window actor reference configuration object. */
    lazy val props = injectOptional[Props]("Core.UI.Window") getOrElse Props(classOf[Window],
      // window id
      UUID.fromString("00000000-0000-0000-0000-000000000000"),
      // parent context
      Core.context)
    /** Application windows title synchronizer. */
    lazy val titleSynchronizer = injectOptional[TitleSynchronizer] getOrElse new TitleSynchronizer
  }
}
