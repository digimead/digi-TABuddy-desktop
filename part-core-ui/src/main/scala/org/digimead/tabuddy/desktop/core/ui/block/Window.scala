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
import java.util.UUID
import java.util.concurrent.locks.ReentrantReadWriteLock
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.Core
import org.digimead.tabuddy.desktop.core.definition.Context
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.support.Timeout
import org.digimead.tabuddy.desktop.core.ui.definition.widget.AppWindow
import org.eclipse.swt.widgets.Widget
import scala.collection.{ immutable, mutable }
import scala.concurrent.Await

/**
 * Actor that represents application window, responsible for:
 * - create window
 * - open window
 * - start window
 * - close/destroy window
 */
class Window(val windowId: UUID, val windowContext: Context.Rich) extends Actor with AppWindow.Controller with Loggable {
  /** Window JFace instance. */
  var window: Option[AppWindow] = None
  /** Window views supervisor. */
  lazy val stackSupervisor = context.actorOf(StackSupervisor.props.copy(args = immutable.Seq(windowId, windowContext)), StackSupervisor.id)
  log.debug("Start actor " + self.path)

  def receive = {
    case message @ App.Message.Close(_, None) ⇒ App.traceMessage(message) {
      close(sender) match {
        case Some(appWindow) ⇒
          App.Message.Close(Right(appWindow))
        case None ⇒
          App.Message.Error(s"Unable to close ${window}.")
      }
    } foreach { sender ! _ }

    case message @ App.Message.Create(Left(Window.<>(windowId, configuration)), None) ⇒ App.traceMessage(message) {
      assert(windowId == this.windowId)
      create(configuration, sender) match {
        case Some(appWindow) ⇒
          App.publish(App.Message.Create(Right(appWindow), self))
          App.Message.Create(Right(appWindow))
        case None ⇒
          App.Message.Error("Unable to create ${viewConfiguration}.")
      }
    } foreach { sender ! _ }

    case message @ App.Message.Destroy(_, None) ⇒ App.traceMessage(message) {
      destroy(sender) match {
        case Some(appWindow) ⇒
          // App.publish(App.Message.Destroy(Right(appWindow), self)) via AppWindow dispose listener
          App.Message.Destroy(Right(appWindow))
        case None ⇒
          App.Message.Error(s"Unable to destroy ${window}.")
      }
    } foreach { sender ! _ }

    case message @ App.Message.Get ⇒ App.traceMessage(message) {
      window
    } foreach { sender ! _ }

    case message @ App.Message.Open(_, None) ⇒ App.traceMessage(message) {
      open(sender) match {
        case Some(appWindow) ⇒
          App.Message.Open(Right(appWindow))
        case None ⇒
          App.Message.Error(s"Unable to open ${window}.")
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
  }
  override def postStop() = log.debug(self.path.name + " actor is stopped.")

  /** Close window. */
  protected def close(sender: ActorRef): Option[AppWindow] = {
    log.debug(s"Close window ${windowId}.")
    this.window.flatMap { window ⇒
      val closed = App.execNGet {
        if (window.getShell() != null && !window.getShell().isDisposed()) window.close() else false
      }
      if (closed) {
        this.window = None
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
      val window = new AppWindow(windowId, self, stackSupervisor, windowContext, null)
      window.configuration = Some(configuration)
      // Add new window to the common map.
      Window.windowMapRWL.writeLock().lock()
      try Window.windowMap += window -> self
      finally Window.windowMapRWL.writeLock().unlock()
      Option(window)
    }
    this.window
  }
  /** Destroy created window. */
  protected def destroy(sender: ActorRef): Option[AppWindow] = this.window.flatMap { window ⇒
    log.debug(s"Destroy window ${windowId}.")
    window.saveOnClose = false
    close(sender)
  }
  /** User start interaction with window. Focus is gained. */
  @log
  protected def onStart(widget: Widget) = window match {
    case Some(window) ⇒
      windowContext.activateBranch()
      Await.result(ask(stackSupervisor, App.Message.Start(Left(widget)))(Timeout.short), Timeout.short)
    case None ⇒
      // Is window deleted while event was delivered?
      log.debug(s"Unable to start unexists window for ${this}.")
  }
  /** Focus is lost. */
  @log
  protected def onStop(widget: Widget) = window match {
    case Some(window) ⇒
      Await.result(ask(stackSupervisor, App.Message.Stop(Left(widget)))(Timeout.short), Timeout.short)
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
}

object Window extends Loggable {
  /** Singleton identificator. */
  val id = getClass.getSimpleName().dropRight(1)
  /** All application windows. */
  protected val windowMap = mutable.WeakHashMap[AppWindow, ActorRef]()
  /** Window map lock. */
  protected val windowMapRWL = new ReentrantReadWriteLock
  // Initialize descendant actor singletons
  StackSupervisor

  /** Window actor reference configuration object. */
  def props = DI.props

  /** Wrapper for App.Message,Create argument. */
  case class <>(val windowId: UUID, val configuration: WindowConfiguration) {
    override def toString() = "<>([%08X]=%s, %s)".format(windowId.hashCode(), windowId.toString(), configuration)
  }
  /**
   * Window map consumer.
   */
  trait WindowMapConsumer {
    /** Get map with all application windows. */
    def windowMap: immutable.Map[AppWindow, ActorRef] = {
      Window.windowMapRWL.readLock().lock()
      try Window.windowMap.toMap
      finally Window.windowMapRWL.readLock().unlock()
    }
  }
  /**
   * Window map consumer.
   */
  trait WindowMapDisposer {
    this: AppWindow ⇒
    /** Remove this AppWindow from the common map. */
    def windowRemoveFromCommonMap() {
      // Remove window from the common map.
      Window.windowMapRWL.writeLock().lock()
      try Window.windowMap -= this
      finally Window.windowMapRWL.writeLock().unlock()
    }
  }
  /**
   * Dependency injection routines.
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** Window actor reference configuration object. */
    lazy val props = injectOptional[Props]("Core.UI.Window") getOrElse Props(classOf[Window],
      // window id
      UUID.fromString("00000000-0000-0000-0000-000000000000"),
      // parent context
      Core.context)
  }
}
