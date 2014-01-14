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

package org.digimead.tabuddy.desktop.example

import org.eclipse.jface.viewers.IStructuredContentProvider
import org.eclipse.jface.viewers.ITableLabelProvider
import org.eclipse.jface.viewers.LabelProvider
import org.eclipse.jface.viewers.TableViewer
import org.eclipse.jface.viewers.Viewer
import org.eclipse.swt.SWT
import org.eclipse.swt.graphics.Image
import org.eclipse.swt.widgets.Composite
import org.eclipse.ui.ISharedImages
import org.eclipse.ui.PlatformUI
import org.eclipse.ui.part.ViewPart

class DefaultView extends ViewPart {
  System.err.println("Initiate " + this)

  private var viewer: TableViewer = null;

  /**
   * The content provider class is responsible for providing objects to the
   * view. It can wrap existing objects in adapters or simply return objects
   * as-is. These objects may be sensitive to the current input of the view,
   * or ignore it and always show the same content (like Task List, for
   * example).
   */
  class ViewContentProvider extends IStructuredContentProvider {
    def inputChanged(v: Viewer, oldInput: Object, newInput: Object) {
    }

    def dispose() {
    }

    def getElements(parent: Object): Array[AnyRef] = {
      if (parent.isInstanceOf[Array[_]]) {
        return parent.asInstanceOf[Array[AnyRef]]
      }
      return Array()
    }
  }

  class ViewLabelProvider extends LabelProvider with ITableLabelProvider {
    def getColumnText(obj: Object, index: Int): String = {
      return getText(obj)
    }

    def getColumnImage(obj: Object, index: Int): Image = {
      return getImage(obj)
    }

    override def getImage(obj: Object): Image = {
      return PlatformUI.getWorkbench().getSharedImages().getImage(
        ISharedImages.IMG_OBJ_ELEMENT);
    }
  }

  /**
   * This is a callback that will allow us to create the viewer and initialize
   * it.
   */
  def createPartControl(parent: Composite) {
    viewer = new TableViewer(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);
    viewer.setContentProvider(new ViewContentProvider());
    viewer.setLabelProvider(new ViewLabelProvider());
    // Provide the input to the ContentProvider
    viewer.setInput(Array[String]("One1", "Two1", "Three1"))
  }

  /**
   * Passing the focus request to the viewer's control.
   */
  def setFocus() {
    viewer.getControl().setFocus()
  }
}

