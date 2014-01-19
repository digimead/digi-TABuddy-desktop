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

package org.digimead.tabuddy.desktop.model.editor.ui.view.editor

import scala.concurrent.future
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.ui.support.TreeProxy
import org.digimead.tabuddy.model.element.Element
import org.eclipse.jface.action.Action
import org.eclipse.jface.action.IAction
import org.eclipse.jface.util.ConfigureColumns
import org.eclipse.jface.viewers.StructuredSelection
import org.eclipse.jface.window.SameShellProvider
import org.digimead.tabuddy.desktop.model.editor.Messages

/**
 * Table actions
 */
trait TableActions {
  this: Table =>

  object ActionConfigureColumns extends Action("Configure Columns...") {
    def apply() = {} //ConfigureColumns.forTable(tableViewer.getTable(), new SameShellProvider(view.getShell()))
    override def run() = apply()
  }
  object ActionAutoResize extends Action(Messages.autoresize_key, IAction.AS_CHECK_BOX) {
    setChecked(true)
    def apply(immediately: Boolean = false) = if (immediately)
      autoresize(true)
    else {
      implicit val ec = App.system.dispatcher
      future { autoresize(false) } onFailure {
        case e: Exception => log.error(e.getMessage(), e)
        case e => log.error(e.toString())
      }
    }
    override def run = if (isChecked()) apply()
  }
  object ActionResetSorting extends Action(Messages.resetSorting_text) {
    // column -1 is user defined sorting
    def apply(immediately: Boolean = false) = {
      val comparator = tableViewer.getComparator().asInstanceOf[Table.TableComparator]
      comparator.column = -1
      tableViewer.refresh()
    }
    override def run = apply()
  }
  class ActionSelectInTree(val element: Element) extends Action(Messages.select_text) {
    def apply() = view.tree.treeViewer.setSelection(new StructuredSelection(TreeProxy.Item(element)), true)
    override def run() = apply()
  }
  object ActionShowTree extends Action(Messages.tree_text) {
    def apply() = {
//      view.ActionHideTree.setChecked(false)
//      view.ActionHideTree()
    }
    override def run() = apply()
  }
}
