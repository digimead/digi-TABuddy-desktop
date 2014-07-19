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

package org.digimead.tabuddy.desktop.model.definition.ui.dialog.eltemed

import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.support.WritableList
import org.digimead.tabuddy.desktop.logic.payload.{ Enumeration, PropertyType }
import org.digimead.tabuddy.desktop.model.definition.Default
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.graph.Graph
import org.eclipse.jface.databinding.viewers.ObservableListContentProvider
import org.eclipse.jface.viewers.{ CellEditor, CellLabelProvider, ComboBoxViewerCellEditor, EditingSupport, LabelProvider, TableViewer, ViewerCell }
import org.eclipse.swt.SWT
import org.eclipse.swt.graphics.Point
import org.eclipse.swt.widgets.Composite

object ColumnType extends XLoggable {
  class TLabelProvider(graph: Graph[_ <: Model.Like]) extends CellLabelProvider {
    override def update(cell: ViewerCell) = cell.getElement() match {
      case item: ElementTemplateEditor.Item ⇒
        item.enumeration match {
          case Some(enumeration) ⇒
            cell.setText(enumeration.name)
          case None ⇒
            cell.setText(item.ptype.name(graph))
        }
        item.typeError.foreach(err ⇒ cell.setImage(err._2))
      case unknown ⇒
        log.fatal("Unknown item " + unknown.getClass())
    }
    override def getToolTipText(element: AnyRef): String = element match {
      case item: ElementTemplateEditor.Item ⇒
        item.typeError match {
          case Some(error) ⇒ error._1
          case None ⇒ item.ptype.typeSymbol.name
        }
      case unknown ⇒
        log.fatal("Unknown item " + unknown.getClass())
        null
    }
    override def getToolTipShift(obj: Object): Point = Default.toolTipShift
    override def getToolTipDisplayDelayTime(obj: Object): Int = Default.toolTipDisplayDelayTime
    override def getToolTipTimeDisplayed(obj: Object): Int = Default.toolTipTimeDisplayed
  }
  class TEditingSupport(graph: Graph[_ <: Model.Like], viewer: TableViewer, container: ElementTemplateEditor) extends EditingSupport(viewer) {
    override protected def getCellEditor(element: AnyRef): CellEditor = {
      val types = container.types.sortBy(_.name(graph))
      val enumerations = container.enumerations.sortBy(_.name)
      val cellEditor = new ComboBoxViewerCellEditor(viewer.getControl().asInstanceOf[Composite], SWT.READ_ONLY)
      cellEditor.setLabelProvider(new LabelProvider {
        override def getText(element: AnyRef): String = element match {
          case ptype: PropertyType[_] ⇒ ptype.name(graph)
          case enumeration: Enumeration[_] ⇒ enumeration.name
          case unknown ⇒ log.fatal("Unknown item " + unknown.getClass()); ""
        }
      })
      cellEditor.setContentProvider(new ObservableListContentProvider())
      cellEditor.setInput(WritableList((types ++ enumerations).toList).underlying)
      cellEditor
    }
    override protected def canEdit(element: AnyRef): Boolean = true
    override protected def getValue(element: AnyRef): AnyRef = element match {
      case item: ElementTemplateEditor.Item ⇒
        item.enumeration match {
          case Some(enumeration) ⇒ enumeration
          case None ⇒ item.ptype
        }
      case unknown ⇒ log.fatal("Unknown item " + unknown.getClass()); ""
    }
    override protected def setValue(element: AnyRef, value: AnyRef): Unit = element match {
      case before: ElementTemplateEditor.Item ⇒
        value match {
          case ptype: PropertyType[_] ⇒
            if (before.ptype != ptype || before.enumeration.nonEmpty) {
              val after = before.copy(enumeration = None,
                ptype = ptype.asInstanceOf[PropertyType[AnyRef with java.io.Serializable]])
              container.updateActualProperty(before, container.validateItem(after))
            }
          case enumeration: Enumeration[_] ⇒
            if (before.enumeration != enumeration) {
              val after = before.copy(enumeration = Some(enumeration.asInstanceOf[Enumeration[AnyRef with java.io.Serializable]]),
                ptype = enumeration.ptype.asInstanceOf[PropertyType[AnyRef with java.io.Serializable]])
              container.updateActualProperty(before, container.validateItem(after))
            }
          case unknown ⇒ log.fatal("Unknown item " + unknown.getClass())
        }
      case unknown ⇒
        log.fatal("Unknown item " + unknown.getClass())
    }
  }
}
