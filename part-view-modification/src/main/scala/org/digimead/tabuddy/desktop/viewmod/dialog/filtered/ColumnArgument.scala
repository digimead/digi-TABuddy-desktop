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

package org.digimead.tabuddy.desktop.viewmod.dialog.filtered

import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.logic.filter.AvailableFilters
import org.digimead.tabuddy.desktop.logic.payload.view.api
import org.digimead.tabuddy.desktop.viewmod.Default
import org.eclipse.jface.viewers.CellEditor
import org.eclipse.jface.viewers.CellLabelProvider
import org.eclipse.jface.viewers.EditingSupport
import org.eclipse.jface.viewers.TableViewer
import org.eclipse.jface.viewers.TextCellEditor
import org.eclipse.jface.viewers.ViewerCell
import org.eclipse.swt.graphics.Point

object ColumnArgument extends Loggable {
  class TLabelProvider extends CellLabelProvider {
    /** Update the label for cell. */
    override def update(cell: ViewerCell) = cell.getElement() match {
      case rule: api.Filter.Rule =>
        val text = AvailableFilters.map.get(rule.filter) match {
          case Some(filter) =>
            filter.stringToArgument(rule.argument).map(arg => {
              filter.generic.argumentToText(arg)
            }).getOrElse(rule.argument)
          case None =>
            rule.argument
        }
        cell.setText(text)
      case unknown =>
        log.fatal("Unknown item " + unknown.getClass())
    }
    /** Get the text displayed in the tool tip for object. */
    override def getToolTipText(element: Object): String = element match {
      case rule: api.Filter.Rule =>
        AvailableFilters.map.get(rule.filter).map(c => "filter: " + c.description).getOrElse(null)
      case unknown =>
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
  class TEditingSupport(viewer: TableViewer, container: FilterEditor) extends EditingSupport(viewer) {
    override protected def getCellEditor(element: AnyRef): CellEditor = element match {
      case item: api.Filter.Rule =>
        val cellEditor = None // TODO get the custom editor
        cellEditor getOrElse new TextCellEditor(viewer.getTable())
      case unknown =>
        log.fatal("Unknown item " + unknown.getClass())
        new TextCellEditor(viewer.getTable())
    }
    override protected def canEdit(element: AnyRef): Boolean = element match {
      case rule: api.Filter.Rule =>
        AvailableFilters.map.get(rule.filter) match {
          case Some(filter) =>
            filter.isArgumentSupported
          case None =>
            log.warn("filter with id %d is not found".format(rule.filter))
            false
        }
      case unknown =>
        log.fatal("Unknown item " + unknown.getClass())
        false
    }
    override protected def getValue(element: AnyRef): AnyRef = element match {
      case rule: api.Filter.Rule =>
        rule.argument // pass argument as a string to the cell editor
      case unknown =>
        log.fatal("Unknown item " + unknown.getClass())
        ""
    }
    override protected def setValue(element: AnyRef, value: AnyRef): Unit = element match {
      case before: api.Filter.Rule =>
        val argument = value.asInstanceOf[String].trim
        AvailableFilters.map.get(before.filter) match {
          case Some(filter) =>
            // check argument
            filter.stringToArgument(argument) match {
              case Some(arg) =>
                if (before.argument != argument)
                  container.updateActualRule(before, before.copy(argument = argument))
              case None =>
                if (before.argument != "") {
                  log.warn("reset argument for filter " + filter)
                  container.updateActualRule(before, before.copy(argument = ""))
                }
            }
          case None =>
            log.warn("filter with id %d is not found".format(before.filter))
        }
      case unknown =>
        log.fatal("Unknown item " + unknown.getClass())
    }
  }
}
