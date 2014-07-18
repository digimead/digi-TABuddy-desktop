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

import org.digimead.digi.lib.api.XDependencyInjection
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.ui.definition.widget.AppWindow
import org.digimead.tabuddy.desktop.core.ui.definition.{ ToolBarContributionItem, ToolBarManager }
import org.eclipse.jface.action.ICoolBarManager
import org.eclipse.jface.action.{ IAction, IContributionItem, IToolBarManager }
import scala.language.implicitConversions

/**
 * Smart toolbar manager.
 */
class SmartToolbarManager {
  /** Add toolbar to coolbar only if not exists. */
  def add(coolbar: ICoolBarManager, toolbar: ToolBarContributionItem): Boolean =
    Option(coolbar.find(toolbar.getId())) match {
      case Some(action) ⇒
        false
      case None ⇒
        coolbar.add(toolbar)
        true
    }
  /** Add action to toolbar only if not exists. */
  def add(toolbar: ToolBarContributionItem, action: IAction): Boolean =
    add(toolbar.getToolBarManager(), action)
  /** Add action to toolbar only if not exists. */
  def add(toolbar: IToolBarManager, action: IAction): Boolean =
    Option(toolbar.find(action.getId())) match {
      case Some(action) ⇒
        false
      case None ⇒
        toolbar.add(action)
        true
    }
  /** Add contribution to toolbar only if not exists. */
  def add(toolbar: ToolBarContributionItem, item: IContributionItem): Boolean =
    add(toolbar.getToolBarManager(), item)
  /** Add contribution to toolbar only if not exists. */
  def add(toolbar: IToolBarManager, item: IContributionItem): Boolean =
    Option(toolbar.find(item.getId())) match {
      case Some(item) ⇒
        false
      case None ⇒
        toolbar.add(item)
        true
    }
  /** Returns toolbar with the specific id from the window CoolBarManager. */
  def apply(window: AppWindow, toolBarDescriptor: SmartToolbarManager.Descriptor): ToolBarContributionItem = {
    App.assertEventThread()
    val cbm = window.getCoolBarManager()
    Option(cbm.find(toolBarDescriptor.id)) match {
      case Some(toolbar: ToolBarContributionItem) ⇒
        toolbar
      case Some(unknown) ⇒
        throw new IllegalArgumentException(s"${toolBarDescriptor} id points to unexpected toolbar contribution item.")
      case None ⇒
        val manager = toolBarDescriptor.factory()
        val toolBarContributionItem = new ToolBarContributionItem(manager, toolBarDescriptor.id)
        manager match {
          case manager: ToolBarManager ⇒ manager.setToolBarManagerContribution(toolBarContributionItem)
          case other ⇒
        }
        cbm.add(toolBarContributionItem)
        toolBarContributionItem
    }
  }

  override def toString = "core.ui.block.SmartToolbarManager"
}

object SmartToolbarManager extends XLoggable {
  implicit def manager2implementation(m: SmartToolbarManager.type): SmartToolbarManager = m.inner

  /** Get SmartToolbarManager implementation. */
  def inner = DI.implementation

  override def toString = "core.ui.block.SmartToolbarManager[Singleton]"

  /** ToolBar descriptor. */
  case class Descriptor(val id: String, val factory: () ⇒ ToolBarManager = () ⇒ new ToolBarManager())

  /**
   * Dependency injection routines
   */
  private object DI extends XDependencyInjection.PersistentInjectable {
    /** SmartToolbarManager implementation. */
    lazy val implementation = injectOptional[SmartToolbarManager] getOrElse new SmartToolbarManager
  }
}
