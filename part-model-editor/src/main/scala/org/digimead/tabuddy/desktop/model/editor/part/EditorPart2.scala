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

package org.digimead.tabuddy.desktop.model.editor.part

import org.eclipse.e4.ui.di.Focus
import org.eclipse.jface.viewers.ArrayContentProvider
import org.eclipse.jface.viewers.ColumnLabelProvider
import org.eclipse.jface.viewers.TableViewer
import org.eclipse.jface.viewers.TableViewerColumn
import org.eclipse.swt.SWT
import org.eclipse.swt.events.SelectionAdapter
import org.eclipse.swt.events.SelectionEvent
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.widgets.Button
import org.eclipse.swt.widgets.Composite
import javax.annotation.PostConstruct
import org.eclipse.ui.part.ViewPart

class EditorPart2 extends ViewPart {
  private var viewer: TableViewer = null

  def createPartControl(parent: Composite) {
    viewer = new TableViewer(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);

    // Provide the input to the ContentProvider
    //viewer.setInput(Array[String]("OneZZZAAA", "TwoZZZ", "ThreeABC"))
  }

  @PostConstruct
  def createUserInterface(parent: Composite) {
    parent.setLayout(new GridLayout(1, false))

    val button = new Button(parent, SWT.NONE)
    button.addSelectionListener(new SelectionAdapter() {
      override def widgetSelected(e: SelectionEvent) {
        //viewer.setInput(model.getTodos());
      }
    })
    button.setText("Update2")

    viewer = new TableViewer(parent, SWT.MULTI);
    val table = viewer.getTable();
    table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1))
    viewer.setContentProvider(ArrayContentProvider.getInstance())
    viewer.getTable().setHeaderVisible(true)
    viewer.getTable().setLinesVisible(true)

    // Summary
    var column = new TableViewerColumn(viewer, SWT.NONE)
    column.setLabelProvider(new ColumnLabelProvider() {
      override def getText(element: AnyRef): String = {
        //Todo todo = (Todo) element
        //return todo.getSummary();
        "ABC"
      }
    })
    column.getColumn().setWidth(100)
    column.getColumn().setText("Summary")

    // Description
    column = new TableViewerColumn(viewer, SWT.NONE)
    column.setLabelProvider(new ColumnLabelProvider() {
      override def getText(element: AnyRef): String = {
        //Todo todo = (Todo) element;
        //return todo.getDescription()
        "XYZ"
      }
    });
    column.getColumn().setWidth(100);
    column.getColumn().setText("Description");

    // I want content
    //viewer.setInput(model.getTodos());
  }

  @Focus
  def setFocus() {
    viewer.getControl().setFocus();
  }
}
