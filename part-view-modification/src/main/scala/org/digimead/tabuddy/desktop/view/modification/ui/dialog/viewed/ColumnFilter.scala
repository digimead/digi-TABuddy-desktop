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

package org.digimead.tabuddy.desktop.view.modification.ui.dialog.viewed

import java.util.UUID
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.support.WritableList
import org.digimead.tabuddy.desktop.logic.payload.view.api.XFilter
import org.digimead.tabuddy.desktop.view.modification.Default
import org.eclipse.jface.viewers.{ CellLabelProvider, ViewerCell }
import org.eclipse.swt.graphics.Point
import org.eclipse.swt.widgets.TableItem

object ColumnFilter extends XLoggable {
  class TLabelProvider(val actualFilters: WritableList[UUID]) extends CellLabelProvider {
    /** Update the label for cell. */
    override def update(cell: ViewerCell) = cell.getElement() match {
      case item: XFilter ⇒
        cell.setText(item.name)
        // update checkbox
        cell.getItem() match {
          case tableItem: TableItem if tableItem.getChecked() != actualFilters.contains(item.id) ⇒
            tableItem.setChecked(!tableItem.getChecked())
          case _ ⇒
        }
      case unknown ⇒
        log.fatal("Unknown item " + unknown.getClass())
    }
    /** Get the text displayed in the tool tip for object. */
    override def getToolTipText(element: Object): String = element match {
      case item: XFilter ⇒
        if (item.description.nonEmpty)
          item.description
        else
          null
      case unknown ⇒
        log.fatal("Unknown item " + unknown.getClass())
        null
    }
    /**
     * Return the amount of pixels in x and y direction that the tool tip to
     * pop up from the mouse pointer.
     */
    override def getToolTipShift(obj: Object): Point = Default.toolTipShift
    /** The time in milliseconds until the tool tip is displayed. */
    override def getToolTipDisplayDelayTime(obj: Object): Int = Default.toolTipDisplayDelayTime
    /** The time in milliseconds the tool tip is shown for. */
    override def getToolTipTimeDisplayed(obj: Object): Int = Default.toolTipTimeDisplayed
  }
}
