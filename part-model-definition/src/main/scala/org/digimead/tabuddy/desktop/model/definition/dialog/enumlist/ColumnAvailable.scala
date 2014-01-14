/**
 * This file is part of the TA Buddy project.
 * Copyright (c) 2012-2014 Alexey Aksenov ezh@ezh.msk.ru
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

package org.digimead.tabuddy.desktop.model.definition.dialog.enumlist

import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.Messages
import org.digimead.tabuddy.desktop.logic.payload.Enumeration
import org.digimead.tabuddy.desktop.model.definition.Default
import org.eclipse.jface.viewers.{ CellEditor, CellLabelProvider, CheckboxCellEditor, EditingSupport, TableViewer, ViewerCell }
import org.eclipse.swt.SWT
import org.eclipse.swt.graphics.Point
import org.eclipse.swt.widgets.TableItem

object ColumnAvailable extends Loggable {
  class TLabelProvider extends CellLabelProvider {
    /** Update the label for cell. */
    override def update(cell: ViewerCell) = cell.getElement() match {
      case item: Enumeration[_] ⇒
        // update label
        if (item.availability)
          cell.setText(Messages.available_text)
        else
          cell.setText(Messages.hidden_text)
        // update checkbox
        cell.getItem() match {
          case tableItem: TableItem if tableItem.getChecked() != item.availability ⇒
            tableItem.setChecked(item.availability)
          case _ ⇒
        }
      case unknown ⇒
        log.fatal("Unknown item " + unknown.getClass())
    }
    /** Get the text displayed in the tool tip for object. */
    override def getToolTipText(element: Object): String = element match {
      case item: Enumeration[_] ⇒
        Messages.enumerationTooltip_text.format(item.ptype.id.name, item.ptype.typeClass.getName, item.constants.size)
      case unknown ⇒
        log.fatal("Unknown item " + unknown.getClass())
        null
    }
    override def getToolTipShift(obj: Object): Point = Default.toolTipShift
    override def getToolTipDisplayDelayTime(obj: Object): Int = Default.toolTipDisplayDelayTime
    override def getToolTipTimeDisplayed(obj: Object): Int = Default.toolTipTimeDisplayed
  }
  class TEditingSupport(viewer: TableViewer, container: EnumerationList) extends EditingSupport(viewer) {
    override protected def getCellEditor(element: AnyRef): CellEditor = new CheckboxCellEditor(null, SWT.CHECK | SWT.READ_ONLY)
    override protected def canEdit(element: AnyRef): Boolean = true
    override protected def getValue(element: AnyRef): AnyRef = element match {
      case item: Enumeration[_] ⇒
        Boolean.box(item.availability)
      case unknown ⇒
        log.fatal("Unknown item " + unknown.getClass())
        ""
    }
    override protected def setValue(element: AnyRef, value: AnyRef): Unit = element match {
      case before: Enumeration[_] ⇒
        val availability = value.asInstanceOf[Boolean]
        if (before.availability != availability)
          container.updateActualEnumeration(before, before.copy(availability = availability))
      case unknown ⇒
        log.fatal("Unknown item " + unknown.getClass())
    }
  }
}
