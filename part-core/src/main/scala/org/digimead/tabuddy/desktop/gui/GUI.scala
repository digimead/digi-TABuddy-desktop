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

import java.util.concurrent.atomic.AtomicReference

import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.Resources
import org.digimead.tabuddy.desktop.Resources.resources2implementation
import org.digimead.tabuddy.desktop.UIThread
import org.digimead.tabuddy.desktop.gui.WindowSupervisor.windowSupervisor2actorSRef
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.App.app2implementation

import language.implicitConversions

/**
 * Run main loop, save and restore windows.
 */
class GUI extends Loggable {
  /** Main loop exit code. */
  protected val exitCode = new AtomicReference[Option[UIThread.Code]](null)

  /** GUI main loop that is invoked from UIThread. */
  @log
  def run() {
    log.debug("Main loop is running.")
    App.assertUIThread()
    val display = App.display
    Resources.start(App.bundle(getClass).getBundleContext())
    App.publish(App.Message.Start(Right(GUI)))
    WindowSupervisor ! App.Message.Restore
    var ts = 0L
    var result = false
    var duration = 0L
    while (exitCode.get.isEmpty) try {
      ts = System.currentTimeMillis()
      result = display.readAndDispatch()
      duration = System.currentTimeMillis() - ts
      if (duration > 500)
        log.error(s"Too heavy UI operation: ${duration}ms.")
      if (!result)
        display.sleep()
    } catch {
      case e: Throwable =>
        log.error(e.getMessage, e)
    }
    App.publish(App.Message.Stop(Right(GUI)))
    log.debug("Main loop is finishing. Process pending UI messages.")
    // Process UI messages until the display is disposed.
    while (!display.isDisposed()) try {
      if (!display.readAndDispatch())
        display.sleep()
    } catch {
      case e: Throwable =>
        log.error(e.getMessage, e)
    }
    Resources.validateOnShutdown()
    Resources.stop(App.bundle(getClass).getBundleContext())
    log.debug("Main loop is finished.")
    exitCode.get.getOrElse {
      log.fatal("Unexpected termination without exit code.")
      exitCode.set(Some(UIThread.Code.Error))
    }
    exitCode.synchronized { exitCode.notifyAll() }
  }
  /** Start main loop. */
  def start() = exitCode.synchronized {
    log.debugWhere("Start main loop.")
    // change exitCode from null -> None
    exitCode.set(None)
    exitCode.notifyAll()
  }
  /** Stop main loop with the specific exit code. */
  def stop(code: UIThread.Code) = exitCode.synchronized {
    log.debugWhere("Stop main loop with code " + code)
    if (exitCode.compareAndSet(None, Some(code))) {
      exitCode.synchronized { exitCode.notifyAll() }
      App.display.wake()
      log.debugWhere("Exit code updated.")
    } else
      log.error(s"Unable to set new exit code ${code}. There is already ${exitCode.get}.")
  }
  /** Wait for the specific exit code. */
  def waitWhile(f: Option[UIThread.Code] => Boolean): Option[UIThread.Code] = exitCode.synchronized {
    log.debug("Waiting for GUI.")
    while ({
      val value = exitCode.get
      if (!f(value)) {
        log.debug(s"Waiting for GUI is completed. Actual code is ${value}.")
        return value
      }
      true
    }) exitCode.wait()
    exitCode.get // unreachable code for compiler
  }
}

object GUI {
  implicit def gui2implementation(g: GUI.type): GUI = g.inner
  /** SWT Data ID key */
  val swtId = getClass.getName() + "#ID"
  /** Context key with the current shell. */
  final val shellContextKey = "shell"
  /** Context key with current view. */
  final val viewContextKey = "view"
  /** Context key with current window. */
  final val windowContextKey = "window"

  def inner(): GUI = DI.implementation
  override def toString = "Core.GUI[Singleton]"

  /**
   * Dependency injection routines
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** GUI implementation */
    lazy val implementation = injectOptional[GUI] getOrElse new GUI
  }
}
