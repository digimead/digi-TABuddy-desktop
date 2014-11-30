/**
 * This file is part of the TA Buddy project.
 * Copyright (c) 2014 Alexey Aksenov ezh@ezh.msk.ru
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

package org.digimead.tabuddy.desktop.core.ui

import org.digimead.digi.lib.api.XDependencyInjection
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.ui.definition.widget.AppWindow
import org.eclipse.jface.action.{ IAction, IContributionItem, IMenuManager, MenuManager }
import org.eclipse.jface.resource.ImageDescriptor
import scala.collection.immutable.ListMap
import scala.language.implicitConversions

/**
 * Smart menu manager.
 */
class SmartMenuManager extends XLoggable {
  /** Add action to toolbar only if not exists. */
  def add(menu: IMenuManager, action: IAction): Boolean = {
    if (action.getId == null)
      throw new IllegalArgumentException(s"Unable to add action ${action.getText} without id.")
    Option(menu.find(action.getId())) match {
      case Some(action) ⇒
        log.debug(s"""Action "${action.getId}" is already exists.""")
        false
      case None ⇒
        log.debug(s"""Add action "${action.getId}" to menu "${menu.getId}".""")
        menu.add(action)
        true
    }
  }
  /** Add contribution to menu only if not exists. */
  def add(menu: IMenuManager, item: IContributionItem): Boolean = {
    if (item.getId == null)
      throw new IllegalArgumentException(s"Unable to add item ${item.getClass} without id.")
    Option(menu.find(item.getId())) match {
      case Some(item) ⇒
        log.debug(s"""Contribution item "${item.getId}" is already exists.""")
        false
      case None ⇒
        log.debug(s"""Add contribution item  "${item.getId}" to menu "${menu.getId}".""")
        menu.add(item)
        true
    }
  }
  /** Get menu with the specific id from the window. */
  def apply(parent: AppWindow, menuDescriptor: SmartMenuManager.Descriptor): IMenuManager =
    apply(parent.getMenuBarManager(), menuDescriptor)
  /** Get menu with the specific id from the window. */
  def apply(manager: IMenuManager, menuDescriptor: SmartMenuManager.Descriptor): IMenuManager = {
    Option(manager.findMenuUsingPath(menuDescriptor.id)) match {
      case Some(menu) ⇒ menu
      case None ⇒
        val menu = new MenuManager(menuDescriptor.text, menuDescriptor.image.getOrElse(null), menuDescriptor.id)
        log.debug(s"""Add menu "${menu.getId}" to menu "${manager.getId}".""")
        manager.add(menu)
        menu
    }
  }
  /** Collect content of MenuManager. */
  def collectForMenuManager(mm: MenuManager): ListMap[IContributionItem, Option[_]] = {
    val items = mm.getItems.map {
      case mm: MenuManager ⇒
        mm -> Some(collectForMenuManager(mm))
      case item ⇒
        item -> None
    }
    ListMap(items: _*)
  }

  override def toString = "core.ui.block.SmartMenuManager"
}

object SmartMenuManager {
  implicit def manager2implementation(m: SmartMenuManager.type): SmartMenuManager = m.inner

  /** Get SmartMenuManager implementation. */
  def inner = DI.implementation

  override def toString = "core.ui.block.SmartMenuManager[Singleton]"

  /** Menu descriptor. */
  case class Descriptor(text: String, image: Option[ImageDescriptor], id: String)

  /**
   * Dependency injection routines
   */
  private object DI extends XDependencyInjection.PersistentInjectable {
    /** SmartMenuManager implementation. */
    lazy val implementation = injectOptional[SmartMenuManager] getOrElse new SmartMenuManager
  }
}
