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

package org.digimead.tabuddy.desktop.b4e

import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.support.App
import org.eclipse.core.runtime.IStatus
import org.eclipse.ui.IMemento
import org.eclipse.ui.application.IActionBarConfigurer
import org.eclipse.ui.application.IWorkbenchWindowConfigurer
import org.eclipse.ui.application.WorkbenchWindowAdvisor

/**
 * The workbench window advisor object is created in response to a workbench
 * window being created (one per window), and is used to configure the window.
 */
class WindowAdvisor(configurer: IWorkbenchWindowConfigurer)
  extends WorkbenchWindowAdvisor(configurer) with Loggable {
  /**
   * Creates a new action bar advisor to configure the action bars of the window
   * via the given action bar configurer.
   */
  override def createActionBarAdvisor(configurer: IActionBarConfigurer): ActionBarAdvisor =
    return new ActionBarAdvisor(configurer)
  /**
   * Performs arbitrary actions after the window has been created (possibly after being restored),
   * but has not yet been opened.
   */
  @log
  override def postWindowCreate() = {
    App.publish(WindowAdvisor.Message.PostWindowCreate(getWindowConfigurer()))
    super.postWindowCreate()
  }
  /** Performs arbitrary actions after the window has been restored, but before it is opened. */
  @log
  override def postWindowRestore() = {
    App.publish(WindowAdvisor.Message.PostWindowRestore(getWindowConfigurer()))
    super.postWindowRestore()
  }
  /** Performs arbitrary actions before the window is opened. */
  @log
  override def preWindowOpen() = {
    App.publish(WindowAdvisor.Message.PreWindowOpen(getWindowConfigurer()))
    super.preWindowOpen()
  }
  /** Performs arbitrary actions after the window has been opened (possibly after being restored). */
  @log
  override def postWindowOpen() = {
    App.publish(WindowAdvisor.Message.PostWindowOpen(getWindowConfigurer()))
    super.postWindowOpen()
  }
  /** Performs arbitrary actions as the window's shell is being closed directly, and possibly veto the close. */
  @log
  override def preWindowShellClose(): Boolean = {
    App.publish(WindowAdvisor.Message.PreWindowShellClose(getWindowConfigurer()))
    super.preWindowShellClose()
  }
  /** Performs arbitrary actions after the window is closed. */
  @log
  override def postWindowClose() = {
    App.publish(WindowAdvisor.Message.PostWindowClose(getWindowConfigurer()))
    super.postWindowClose()
  }
  /** Saves arbitrary application specific state information. */
  @log
  override def saveState(memento: IMemento): IStatus = {
    App.publish(WindowAdvisor.Message.SaveState(getWindowConfigurer(), memento))
    super.saveState(memento)
  }
  /** Restores arbitrary application specific state information. */
  @log
  override def restoreState(memento: IMemento): IStatus = {
    App.publish(WindowAdvisor.Message.RestoreState(getWindowConfigurer(), memento))
    super.restoreState(memento)
  }
}

object WindowAdvisor {
  sealed trait Message extends App.Message {
    /**
     * Window configurer object that is in 1-1 correspondence with the workbench
     * window it configure. Clients may use <code>get/setData</code> to
     * associate arbitrary state with the window configurer object.
     */
    val configurer: IWorkbenchWindowConfigurer
  }
  object Message {
    /** Performs arbitrary actions before the window is opened. */
    case class PreWindowOpen(val configurer: IWorkbenchWindowConfigurer) extends Message
    /**
     * Performs arbitrary actions after the window has been created (possibly after being restored),
     * but has not yet been opened.
     */
    case class PostWindowCreate(val configurer: IWorkbenchWindowConfigurer) extends Message
    /** Performs arbitrary actions after the window has been restored, but before it is opened. */
    case class PostWindowRestore(val configurer: IWorkbenchWindowConfigurer) extends Message
    /** Performs arbitrary actions after the window has been opened (possibly after being restored). */
    case class PostWindowOpen(val configurer: IWorkbenchWindowConfigurer) extends Message
    // Throw WorkbenchException to prohibit
    /** Performs arbitrary actions as the window's shell is being closed directly, and possibly veto the close. */
    case class PreWindowShellClose(val configurer: IWorkbenchWindowConfigurer) extends Message
    /** Performs arbitrary actions after the window is closed. */
    case class PostWindowClose(val configurer: IWorkbenchWindowConfigurer) extends Message
    // Throw ExecutionException to prohibit
    /** Saves arbitrary application specific state information. */
    case class SaveState(val configurer: IWorkbenchWindowConfigurer, val memento: IMemento) extends Message
    // Throw ExecutionException to prohibit
    /** Restores arbitrary application specific state information. */
    case class RestoreState(val configurer: IWorkbenchWindowConfigurer, val memento: IMemento) extends Message
  }
}
