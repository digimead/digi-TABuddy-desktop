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

package org.digimead.tabuddy.desktop

import java.util.concurrent.Exchanger
import java.util.concurrent.atomic.AtomicReference

import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.MainService.main2implementation
import org.digimead.tabuddy.desktop.gui.GUI.gui2implementation
import org.eclipse.core.databinding.DataBindingContext
import org.eclipse.core.databinding.observable.Realm
import org.eclipse.jface.databinding.swt.SWTObservables
import org.eclipse.swt.widgets.Display
import org.eclipse.ui.PlatformUI

/**
 * Main UI event thread implementation.
 */
class UIThread extends Thread("GUI Thread") with UIThread.Initializer with Loggable {
  /** The global application data binding context. */
  lazy val bindingContext = {
    assert(initialized, "UIThread is not initialized.")
    new DataBindingContext(realm)
  }
  /** The default display. */
  lazy val display = {
    assert(initialized, "UIThread is not initialized.")
    PlatformUI.createDisplay()
  }
  /** The realm representing the UI thread for the given display */
  lazy val realm = {
    assert(initialized, "UIThread is not initialized.")
    SWTObservables.getRealm(display)
  }
  /** Flag indicating whether this thread is initialized. */
  @volatile protected var initialized = false

  @log
  override def run {
    Display.getDefault() // get or create
    if (Display.getCurrent() != null) {
      initialized = true
      assert(bindingContext != null)
      assert(display != null)
      assert(realm != null)
      log.debug("Mark UI thread with ID " + getId())
      uiThreadStartSync()
      gui.GUI.waitWhile { _ == null } match {
        case None =>
          Realm.runWithDefault(realm, new Runnable() { def run() = gui.GUI.run() })
        case Some(exitCode) =>
          log.info(s"Skip GUI main loop: ${exitCode}")
      }
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
        case e: Throwable =>
          log.error(e.getMessage, e)
      }
    }
  }
}

object UIThread {
  /** The UI thread. */
  lazy val thread = new UIThread
  /** Startup synchronization. */
  protected lazy val startSync = new Exchanger[Null]

  /**
   * I really don't want to provide access to MainService from entire system
   */
  trait Consumer {
    /** The global application data binding context */
    def bindingContext = UIThread.thread.bindingContext
    /** The default display, available from ui.Window */
    def display = UIThread.thread.display
    /** The realm representing the UI thread for the given display */
    def realm = UIThread.thread.realm
    /** The application-wide actor system */
    def system = MainService.system
    /** The UI thread */
    def thread = UIThread.thread
  }
  /** Startup synchronization trait for bundle activator. */
  trait Initializer {
    def uiThreadStartSync() = UIThread.startSync.exchange(null)
  }
  /** UI Thread exit codes. */
  sealed trait Code
  object Code {
    case object Ok extends Code
    case object Error extends Code
    case object Restart extends Code
  }
}
