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

import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.gui.widget.AppWindow
import org.eclipse.jface.action.ToolBarContributionItem
import org.eclipse.jface.action.ToolBarManager

object WindowToolbar extends Loggable {
  /** Common toolbar descriptor. */
  val common = Descriptor(getClass.getName() + "#common")

  /** ToolBar descriptor. */
  case class Descriptor(id: String)
  /** Return toolbar with the specific id from the window CoolBarManager. */
  def apply(window: AppWindow, toolBarDescriptor: Descriptor): ToolBarContributionItem = {
    val cbm = window.getCoolBarManager()
    Option(cbm.find(toolBarDescriptor.id)) match {
      case Some(toolbar: ToolBarContributionItem) =>
        toolbar
      case Some(unknown) =>
        throw new IllegalArgumentException(s"${toolBarDescriptor} id points to unexpected toolbar contribution item.")
      case None =>
        val toolBarContributionItem = new ToolBarContributionItem(new ToolBarManager(), toolBarDescriptor.id)
        cbm.add(toolBarContributionItem)
        toolBarContributionItem
    }
  }
}
