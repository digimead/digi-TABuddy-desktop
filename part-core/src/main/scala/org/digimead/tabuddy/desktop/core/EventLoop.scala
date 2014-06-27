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

package org.digimead.tabuddy.desktop.core

import java.util.concurrent.Exchanger
import java.util.concurrent.atomic.AtomicReference
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.XDependencyInjection
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.console.Console
import org.digimead.tabuddy.desktop.core.support.App
import org.eclipse.core.databinding.DataBindingContext
import org.eclipse.core.databinding.observable.Realm
import org.eclipse.jface.databinding.swt.SWTObservables
import org.eclipse.swt.widgets.Display
import org.eclipse.ui.PlatformUI
import scala.concurrent.Future

/**
 * Application event loop that is based on SWT.
 * Thread is required for data binding and UI operation.
 * It is suitable for headless mode.
 */
class EventLoop extends Thread("Application event loop") with EventLoop.Initializer with XLoggable {
  /** Akka execution context. */
  implicit lazy val ec = App.system.dispatcher
  /** The global application data binding context. */
  protected lazy val bindingContext = {
    assert(initialized, "EventLoop is not initialized.")
    new DataBindingContext(realm)
  }
  /** The default display. */
  protected lazy val display = {
    assert(initialized, "EventLoop is not initialized.")
    PlatformUI.createDisplay()
  }
  /** Main loop exit code. */
  protected val exitCodeValue = new AtomicReference[Option[EventLoop.Code]](null)
  /** Flag indicating whether this thread is initialized. */
  @volatile protected var initialized = false
  /** The realm representing the application thread for the given display. */
  protected lazy val realm = {
    assert(initialized, "EventLoop is not initialized.")
    SWTObservables.getRealm(display)
  }

  /** Current exit code. */
  def exitCode = exitCodeValue.synchronized { Option(exitCodeValue.get()) getOrElse None }
  /** Check if event loop is initialized. */
  def isInitialized = exitCodeValue.synchronized { initialized }
  /** Wrapper for thread with event loop. */
  @log
  override def run {
    Display.getDefault() // get or create
    if (Display.getCurrent() != null) {
      initialized = true
      assert(bindingContext != null)
      assert(display != null)
      assert(realm != null)
      log.debug(s"Mark thread with ID ${getId()} as event loop.")
      eventLoopThreadSync()
      waitWhile { _ == null } match {
        case None ⇒
          Realm.runWithDefault(realm, new Runnable() { def run() = loop() })
        case Some(exitCodeValue) ⇒
          log.info(s"Skip main loop. Exit code detected: ${exitCodeValue}")
      }
      eventLoopThreadSync()
    } else {
      log.fatal("Unable to get current SWT display.")
      try {
        val displayClass = Display.getDefault().getClass()
        val threadField = displayClass.getDeclaredField("thread")
        if (!threadField.isAccessible())
          threadField.setAccessible(true)
        val thread = threadField.get(Display.getDefault())
        log.fatal("Default display is binded to unexpected thread " + thread)
      } catch {
        case e: Throwable ⇒
          log.error(e.getMessage, e)
      }
    }
  }
  /** Start event loop. */
  def startEventLoop() = exitCodeValue.synchronized {
    log.debugWhere("Start main loop.")
    // change exitCodeValue from null -> None
    exitCodeValue.set(None)
    exitCodeValue.notifyAll()
  }
  /** Stop event loop with the specific exit code. */
  def stopEventLoop(code: EventLoop.Code) = exitCodeValue.synchronized {
    log.debugWhere(s"Stop event loop with code '${code}'.")
    if (exitCodeValue.compareAndSet(None, Some(code))) {
      exitCodeValue.synchronized { exitCodeValue.notifyAll() }
      App.display.wake()
      log.debugWhere("Exit code updated.")
    } else if (exitCodeValue.compareAndSet(null, Some(code))) {
      exitCodeValue.synchronized { exitCodeValue.notifyAll() }
      log.debugWhere("Exit code updated.")
    } else
      log.error(s"Unable to set new exit code ${code}. There is already ${exitCodeValue.get}.")
  }
  /** Wait for the specific exit code. Terminate on false. */
  def waitWhile(f: Option[EventLoop.Code] ⇒ Boolean): Option[EventLoop.Code] = exitCodeValue.synchronized {
    log.debug("Waiting for the event loop thread.")
    while ({
      val value = exitCodeValue.get
      if (!f(value)) {
        log.debug(s"Waiting for completion of the event loop thread. Current exit code is ${value}.")
        return value
      }
      true
    }) exitCodeValue.wait()
    exitCodeValue.get // unreachable code for compiler
  }

  /** Application main loop that is invoked from EventLoop. */
  @log
  protected def loop() {
    log.debug("Event loop is running.")
    App.assertEventThread()
    val display = App.display
    var ts = 0L
    var result = false
    var duration = 0L
    // Start event in separated thread since watcher is synchronous
    // and watcher hook may depends on event loop
    Future { App.watch(EventLoop) on {} } onFailure { case e: Throwable ⇒ log.error(e.getMessage(), e) }
    while (exitCodeValue.get.isEmpty) try {
      // ts = System.currentTimeMillis()
      result = display.readAndDispatch()
      // duration = System.currentTimeMillis() - ts
      // if (duration > 500)
      // log.error(s"Too heavy UI operation: ${duration}ms.")
      if (!result)
        display.sleep()
    } catch {
      case e: Throwable ⇒
        log.error(e.getMessage, e)
    }
    Console ! Console.Message.Notice("Shutdown application.")
    Future { App.watch(EventLoop) off {} } onFailure { case e: Throwable ⇒ log.error(e.getMessage(), e) }
    log.debug("Event loop is finishing. Process pending events.")
    // Process events until the display is disposed.
    while (!display.isDisposed()) try {
      if (!display.readAndDispatch())
        display.sleep()
    } catch {
      case e: Throwable ⇒
        log.error(e.getMessage, e)
    }
    log.debug("Event loop is finished.")
    exitCodeValue.get.getOrElse {
      log.fatal("Unexpected termination without exit code.")
      exitCodeValue.set(Some(EventLoop.Code.Error))
    }
    exitCodeValue.synchronized { exitCodeValue.notifyAll() }
  }
}

object EventLoop {
  /** The application event loop thread. */
  lazy val thread = DI.implementation.newInstance()
  /** Startup synchronization. */
  protected lazy val startSync = new Exchanger[Null]

  /**
   * I really don't want to provide access to AppService/EventLoop from entire system
   */
  trait Consumer {
    /** The global application data binding context. */
    def bindingContext = EventLoop.thread.bindingContext
    /** The default display. */
    def display = EventLoop.thread.display
    /** The realm representing the event thread for the given display. */
    def realm = EventLoop.thread.realm
    /** The application-wide actor system. */
    def system = AppService.system
    /** The event thread. */
    def thread = EventLoop.thread
  }
  /** Startup synchronization trait for bundle activator. */
  trait Initializer {
    def eventLoopThreadSync() = EventLoop.startSync.exchange(null)
  }
  /** Event loop exit codes. */
  sealed trait Code
  object Code {
    case object Ok extends Code
    case object Error extends Code
    case object Restart extends Code
  }
  /**
   * Dependency injection routines
   */
  private object DI extends XDependencyInjection.PersistentInjectable {
    /** EventLoop implementation */
    lazy val implementation = injectOptional[Class[EventLoop]]("EventLoop") getOrElse classOf[EventLoop]
  }
}
