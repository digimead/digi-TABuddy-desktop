/**
 * This file is part of the TABuddy project.
 * Copyright (c) 2012-2013 Alexey Aksenov ezh@ezh.msk.ru
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

package org.digimead.tabuddy.desktop.ui.view.tree

import org.digimead.digi.lib.log.Loggable
import org.eclipse.swt.widgets.Composite
import org.eclipse.jface.viewers.IStructuredContentProvider
import org.eclipse.jface.viewers.Viewer
import org.eclipse.jface.viewers.LabelProvider
import org.eclipse.jface.viewers.ITableLabelProvider
import org.eclipse.swt.graphics.Image
import org.digimead.tabuddy.desktop.ui.view.View
import org.digimead.tabuddy.desktop.ui.toolbar.TreePrimary
import org.eclipse.jface.viewers.ITreeContentProvider

class Tree private (argParent: Composite, argStyle: Int) extends org.digimead.tabuddy.desktop.res.view.Tree(argParent, argStyle) with View {
  lazy val title = "Tree"
  lazy val description = "Tree View"

  // initialize toolbars
  getCoolBarManager.add(TreePrimary)
  getCoolBarManager.update(true)
  //initialize table
  getTreeViewer.setContentProvider(new Tree.TContentProvider())
  getTreeViewer.setLabelProvider(new Tree.TLabelProvider())
  /*  getTableViewer.getTable.setHeaderVisible(true)
  getTableViewer.getTable.setLinesVisible(true)
  getTableViewer.getTable.setLayoutData(new RowData(200, 200))
  getTableViewer.getTable.setItemCount(8876)
  getTableViewer.getTable.addListener(SWT.SetData, new Listener() {
    def handleEvent(event: Event) = event.item match {
      case item: TableItem =>
        val index = getTableViewer.getTable.indexOf(item)
        item.setText("Item " + index)
      case item =>
        log.fatal("unknown item type " + item)
    }
  })*/
  /*
  lazy val view = new BorderPane(FXMLLoader.load[javafx.scene.layout.BorderPane](getClass.getClassLoader().getResource("viewTree.fxml")))
  lazy val list = new TableView[Int](Tree.controller.list)
  lazy val listColWBS = new TableColumn[Int, String](Tree.controller.listColWBS)
  lazy val listColTask = new TableColumn[Int, String](Tree.controller.listColTask)
  lazy val listColEfforts = new TableColumn[Int, String](Tree.controller.listColEfforts)
  lazy val status = new Label(Tree.controller.status)
  lazy val `new` = new Button(Tree.controller.`new`)
  lazy val delete = new Button(Tree.controller.delete)
  lazy val link = new Button(Tree.controller.link)
  lazy val left = new Button(Tree.controller.left)
  lazy val right = new Button(Tree.controller.right)
  lazy val up = new Button(Tree.controller.up)
  lazy val down = new Button(Tree.controller.down)
  @volatile var toolbarEnabled = LocalStorage.isModelConsistent
  @volatile var statusText = if (toolbarEnabled) "model: " + Model.eId.name else ""
  /*
   * Initialize GUI
   */
  // force controller initialization
  view.requestLayout()
  listColTask.prefWidth <== list.width - listColWBS.width - listColEfforts.width
  /*
   * Add listeners
   */
  /*
   * Process an application job events
   */
  val subscriber = new Job.Event.Sub {
    def notify(pub: Job.Event.Pub, event: Job.Event) = event match {
      case Job.Event.Succeeded(JobModelAcquire(model)) =>
        statusText = "model: " + Model.eId.name
        toolbarEnabled = LocalStorage.isModelConsistent
        updateElements
      case _ =>
    }
  }
  Job.Event.subscribe(subscriber)

  def updateElements() = Platform.runLater(new Runnable() {
    def run() {
      status.text = statusText
      `new`.disable = !toolbarEnabled
      delete.disable = !toolbarEnabled
      link.disable = !toolbarEnabled
      left.disable = !toolbarEnabled
      right.disable = !toolbarEnabled
      up.disable = !toolbarEnabled
      down.disable = !toolbarEnabled
    }
  })*/
}

object Tree extends Loggable {
  @volatile private var tree: Tree = null

  def apply(parent: Composite, style: Int): Tree = {
    assert(tree == null, "Tree already initialized")
    tree = new Tree(parent, style)
    tree
  }
  class TContentProvider extends ITreeContentProvider {
    def inputChanged(v: Viewer, oldInput: Object, newInput: Object) {
    }
    def dispose() {
    }
    def getElements(parent: Object): Array[AnyRef] = {
      Array()
    }
    def getChildren(parentElement: Object): Array[AnyRef] = {
      //    if (parentElement instanceof Category) {
      //      Category category = (Category) parentElement;
      //      return category.getTodos().toArray();
      //    }
      return null
    }
    def getParent(element: AnyRef): AnyRef = {
      return null
    }
    def hasChildren(element: AnyRef): Boolean = false
  }
  class TLabelProvider extends LabelProvider with ITableLabelProvider {
    def getColumnText(obj: Object, index: Int): String = {
      return "BBBB"
    }
    def getColumnImage(obj: Object, index: Int): Image = getImage(obj)
  }
}
