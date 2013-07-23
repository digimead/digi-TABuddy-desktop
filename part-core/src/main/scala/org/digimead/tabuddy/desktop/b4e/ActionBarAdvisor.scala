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

import scala.collection.mutable.Publisher

import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.App.app2implementation
import org.eclipse.core.commands.ExecutionException
import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.Status
import org.eclipse.jface.action.ICoolBarManager
import org.eclipse.jface.action.IMenuManager
import org.eclipse.jface.action.IStatusLineManager
import org.eclipse.ui.IMemento
import org.eclipse.ui.IWorkbenchWindow
import org.eclipse.ui.application.{ ActionBarAdvisor => EActionBarAdvisor }
import org.eclipse.ui.application.IActionBarConfigurer

/**
 * Public base class for configuring the action bars of a workbench window.
 */
class ActionBarAdvisor(configurer: IActionBarConfigurer) extends EActionBarAdvisor(configurer) with Loggable {
  /** Instantiates the actions used in the fill methods. */
  @log
  protected override def makeActions(window: IWorkbenchWindow) {
    super.makeActions(window)
    try {
      ActionBarAdvisor.Message.publish(ActionBarAdvisor.Message.MakeActions(getActionBarConfigurer(), window))
    } catch {
      case e: ExecutionException =>
        log.error(e.getMessage(), e)
    }
  }
  /**  Fills the menu bar with the main menus for the window. */
  @log
  protected override def fillMenuBar(menuBar: IMenuManager) = {
    super.fillMenuBar(menuBar)
    try {
      ActionBarAdvisor.Message.publish(ActionBarAdvisor.Message.FillMenuBar(getActionBarConfigurer(), menuBar))
    } catch {
      case e: ExecutionException =>
        log.error(e.getMessage(), e)
    }
  }
  /** Fills the cool bar with the main toolbars for the window. */
  @log
  protected override def fillCoolBar(coolBar: ICoolBarManager) = {
    super.fillCoolBar(coolBar)
    try {
      ActionBarAdvisor.Message.publish(ActionBarAdvisor.Message.FillCoolBar(getActionBarConfigurer(), coolBar))
    } catch {
      case e: ExecutionException =>
        log.error(e.getMessage(), e)
    }
  }
  /** Fills the status line with the main status line contributions for the window. */
  @log
  protected override def fillStatusLine(statusLine: IStatusLineManager) = {
    super.fillStatusLine(statusLine)
    try {
      ActionBarAdvisor.Message.publish(ActionBarAdvisor.Message.FillStatusLine(getActionBarConfigurer(), statusLine))
    } catch {
      case e: ExecutionException =>
        log.error(e.getMessage(), e)
    }
  }
  /** Saves arbitrary application-specific state information for this action bar advisor. */
  @log
  override def saveState(memento: IMemento): IStatus = {
    val status = super.saveState(memento)
    if (status == Status.OK_STATUS) try {
      ActionBarAdvisor.Message.publish(ActionBarAdvisor.Message.SaveState(getActionBarConfigurer(), memento))
      Status.OK_STATUS
    } catch {
      case e: ExecutionException =>
        log.debug("Prevent saveState operation: " + e.getMessage, e)
        new Status(IStatus.CANCEL, App.bundle(getClass).getSymbolicName, e.getMessage, e)
    }
    else
      status
  }
  /** Restores arbitrary application-specific state information for this action bar advisor. */
  @log
  override def restoreState(memento: IMemento): IStatus = {
    val status = super.restoreState(memento)
    if (status == Status.OK_STATUS) try {
      ActionBarAdvisor.Message.publish(ActionBarAdvisor.Message.RestoreState(getActionBarConfigurer(), memento))
      Status.OK_STATUS
    } catch {
      case e: ExecutionException =>
        log.debug("Prevent restoreState operation: " + e.getMessage, e)
        new Status(IStatus.CANCEL, App.bundle(getClass).getSymbolicName, e.getMessage, e)
    }
    else
      status
  }
}

object ActionBarAdvisor extends Loggable {
  sealed trait Message extends App.Message {
    /**
     * Action bar configurer object that is in 1-1 correspondence with the workbench
     * window it configure. Clients may use <code>getWindowConfigurer and get/setData</code> to
     * associate arbitrary state with the action bar configurer object.
     */
    val configurer: IActionBarConfigurer
  }
  object Message extends Publisher[Message] {
    private val publishLock = new Object
    /** Instantiates the actions used in the fill methods. */
    case class MakeActions(val configurer: IActionBarConfigurer, window: IWorkbenchWindow) extends Message
    /** Fills the menu bar with the main menus for the window. */
    case class FillMenuBar(val configurer: IActionBarConfigurer, menuBar: IMenuManager) extends Message
    /** Fills the cool bar with the main toolbars for the window */
    case class FillCoolBar(val configurer: IActionBarConfigurer, coolBar: ICoolBarManager) extends Message
    /** Fills the status line with the main status line contributions for the window. */
    case class FillStatusLine(val configurer: IActionBarConfigurer, statusLine: IStatusLineManager) extends Message
    // Throw ExecutionException to prohibit
    /** Saves arbitrary application-specific state information for this action bar advisor. */
    case class SaveState(val configurer: IActionBarConfigurer, val memento: IMemento) extends Message
    // Throw ExecutionException to prohibit
    /** Restores arbitrary application-specific state information for this action bar advisor. */
    case class RestoreState(val configurer: IActionBarConfigurer, val memento: IMemento) extends Message

    /** Publish events to all registered subscribers */
    override protected[ActionBarAdvisor] def publish(message: Message) = publishLock.synchronized {
      try {
        super.publish(message)
      } catch {
        case e: ExecutionException =>
          // rethrow
          throw e
        case e: Throwable =>
          // catch all other subscriber exceptions
          log.error(e.getMessage(), e)
      }
    }
  }
}
