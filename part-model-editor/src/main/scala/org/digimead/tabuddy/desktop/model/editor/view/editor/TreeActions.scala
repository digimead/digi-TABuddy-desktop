/**
 * This file is part of the TA Buddy project.
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

package org.digimead.tabuddy.desktop.model.editor.view.editor
//
//import scala.concurrent.future
//
//import org.digimead.tabuddy.desktop.Messages
//import org.digimead.tabuddy.desktop.support.App
//import org.digimead.tabuddy.desktop.support.App.app2implementation
//import org.digimead.tabuddy.desktop.support.TreeProxy
//import org.digimead.tabuddy.model.element.Element
//import org.eclipse.jface.action.Action
//import org.eclipse.jface.action.IAction
//import org.eclipse.jface.viewers.StructuredSelection
//
///**
// * Tree actions.
// */
//trait TreeActions {
//  this: Tree =>
//
//  class ActionAsRoot(val element: Element.Generic) extends Action(Messages.markAsRoot_text) {
//    def apply() = View.withRedrawDelayed(view) {
//      treeViewer.setInput(TreeProxy.Item(element))
//      view.ActionAutoResize(false)
//    }
//    override def run() = apply()
//  }
//  object ActionAutoResize extends Action(Messages.autoresize_key, IAction.AS_CHECK_BOX) {
//    setChecked(true)
//
//    def apply(immediately: Boolean = false) = if (immediately)
//      autoresize(true)
//    else {
//      implicit val ec = App.system.dispatcher
//      future { autoresize(false) } onFailure {
//        case e: Exception => log.error(e.getMessage(), e)
//        case e => log.error(e.toString())
//      }
//    }
//    override def run = if (isChecked()) apply()
//  }
//  class ActionCollapse(val element: Element.Generic) extends Action(Messages.collapseRecursively_text) {
//    def apply() = View.withRedrawDelayed(view) {
//      Tree.collapse(element, true, view)
//      view.ActionAutoResize(false)
//    }
//    override def run() = apply()
//  }
//  class ActionExpand(val element: Element.Generic) extends Action(Messages.expandRecursively_text) {
//    def apply() = View.withRedrawDelayed(view) {
//      Tree.expand(element, true, view)
//      view.ActionAutoResize(true)
//    }
//    override def run() = apply()
//  }
//  object ActionHideTree extends Action(Messages.hide_text) {
//    def apply() = {
//      view.ActionHideTree.setChecked(true)
//      view.ActionHideTree()
//    }
//    override def run() = apply()
//  }
//  class ActionSelectInTable(val element: Element.Generic) extends Action(Messages.select_text) {
//    def apply() = view.table.tableViewer.setSelection(new StructuredSelection(TreeProxy.Item(element)), true)
//    override def run() = apply()
//  }
//}
