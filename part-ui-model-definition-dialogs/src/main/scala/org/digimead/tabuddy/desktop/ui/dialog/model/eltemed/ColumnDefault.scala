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

package org.digimead.tabuddy.desktop.ui.dialog.model.eltemed

import org.digimead.digi.lib.log.Loggable
import org.digimead.digi.lib.log.logger.RichLogger.rich2slf4j
import org.digimead.tabuddy.desktop.payload.Enumeration
import org.digimead.tabuddy.desktop.payload.PropertyType
import org.digimead.tabuddy.desktop.support.WritableList
import org.eclipse.jface.databinding.viewers.ObservableListContentProvider
import org.eclipse.jface.viewers.CellEditor
import org.eclipse.jface.viewers.CellLabelProvider
import org.eclipse.jface.viewers.ComboBoxViewerCellEditor
import org.eclipse.jface.viewers.EditingSupport
import org.eclipse.jface.viewers.LabelProvider
import org.eclipse.jface.viewers.TableViewer
import org.eclipse.jface.viewers.ViewerCell
import org.eclipse.swt.SWT
import org.eclipse.swt.graphics.Color
import org.eclipse.swt.graphics.Font
import org.eclipse.swt.graphics.Image
import org.eclipse.swt.graphics.Point
import org.eclipse.swt.widgets.Composite

object ColumnDefault extends Loggable {
  class TLabelProvider extends CellLabelProvider {
    override def update(cell: ViewerCell) = cell.getElement() match {
      case item: ElementTemplateEditor.Item =>
        item.ptype.adapter.cellLabelProvider.update(cell, item.default)
        item.defaultError.foreach(err => cell.setImage(err._2))
      case unknown =>
        log.fatal("Unknown item " + unknown.getClass())
    }
    override def getToolTipBackgroundColor(element: AnyRef): Color =
      withElement(element)((adapter, element, error) => adapter.getToolTipBackgroundColor(element)).getOrElse(super.getToolTipBackgroundColor(element))
    override def getToolTipDisplayDelayTime(element: AnyRef): Int =
      withElement(element)((adapter, element, error) => adapter.getToolTipDisplayDelayTime(element)).getOrElse(super.getToolTipDisplayDelayTime(element))
    override def getToolTipFont(element: AnyRef): Font =
      withElement(element)((adapter, element, error) => adapter.getToolTipFont(element)).getOrElse(super.getToolTipFont(element))
    override def getToolTipForegroundColor(element: AnyRef): Color =
      withElement(element)((adapter, element, error) => adapter.getToolTipForegroundColor(element)).getOrElse(super.getToolTipForegroundColor(element))
    override def getToolTipImage(element: AnyRef): Image =
      withElement(element)((adapter, element, error) => adapter.getToolTipImage(element)).getOrElse(super.getToolTipImage(element))
    override def getToolTipShift(element: AnyRef): Point =
      withElement(element)((adapter, element, error) => adapter.getToolTipShift(element)).getOrElse(super.getToolTipShift(element))
    override def getToolTipText(element: AnyRef): String =
      withElement(element)((adapter, element, error) => error match {
        case Some(error) => error._1
        case None => adapter.getToolTipText(element)
      }).getOrElse(super.getToolTipText(element))
    override def getToolTipTimeDisplayed(element: AnyRef): Int =
      withElement(element)((adapter, element, error) => adapter.getToolTipTimeDisplayed(element)).getOrElse(super.getToolTipTimeDisplayed(element))
    override def getToolTipStyle(element: AnyRef): Int =
      withElement(element)((adapter, element, error) => adapter.getToolTipStyle(element)).getOrElse(super.getToolTipStyle(element))

    /** Call the specific CellLabelProviderAdapter Fn with element argument */
    protected def withElement[T](element: AnyRef)(f: (PropertyType.CellLabelProviderAdapter[_], AnyRef, Option[(String, Image)]) => T): Option[T] = element match {
      case item: ElementTemplateEditor.Item =>
        item.default match {
          case Some(value) if value.getClass() == item.ptype.typeClass => Some(f(item.ptype.adapter.cellLabelProvider, value, item.defaultError))
          case _ => Some(f(item.ptype.adapter.cellLabelProvider, null, item.defaultError))
        }
      case unknown =>
        log.fatal("Unknown item " + unknown.getClass())
        None
    }
  }
  class TEditingSupport(viewer: TableViewer, container: ElementTemplateEditor) extends EditingSupport(viewer) {
    override protected def getCellEditor(element: AnyRef): CellEditor = element match {
      case item: ElementTemplateEditor.Item =>
        item.enumeration match {
          case Some(enumeration) =>
            val cellEditor = new ComboBoxViewerCellEditor(viewer.getControl().asInstanceOf[Composite], SWT.READ_ONLY)
            cellEditor.setLabelProvider(item.ptype.adapter.createEnumerationLabelProvider)
            cellEditor.setContentProvider(new ObservableListContentProvider())
            cellEditor.setInput(WritableList(enumeration.constants.toList.sortBy(_.view)).underlying)
            cellEditor
          case None =>
            item.ptype.adapter.createCellEditor(viewer.getControl().asInstanceOf[Composite])
        }
      case unknown =>
        log.fatal("Unknown item " + unknown.getClass())
        null
    }
    override protected def canEdit(element: AnyRef): Boolean = element.isInstanceOf[ElementTemplateEditor.Item]
    override protected def getValue(element: AnyRef): AnyRef = element match {
      case item: ElementTemplateEditor.Item =>
        item.enumeration match {
          case Some(enumeration) =>
            enumeration.constants.find(c => Some(c.value) == item.default) getOrElse (enumeration.constants.toList.sortBy(_.view).head)
          case None =>
            item.default match {
              case Some(default) =>
                default
              case None =>
                null
            }
        }
      case unknown =>
        log.fatal("Unknown item " + unknown.getClass())
        null
    }
    override protected def setValue(element: AnyRef, value: AnyRef): Unit = element match {
      case before: ElementTemplateEditor.Item =>
        val default = value match {
          case constant: Enumeration.Constant[_] =>
            Some(constant.value)
          case other: String =>
            val defaultAsString = value.asInstanceOf[String].trim
            try {
              if (defaultAsString.nonEmpty)
                Some(before.ptype.valueFromString(defaultAsString))
              else
                None
            } catch {
              case e: Throwable =>
                log.info("Unable to convert string '%s' to value: %s".format(defaultAsString, e.getMessage()))
                None
            }
        }
        if (before.default != default) {
          val after = before.copy(default = default)
          container.updateActualProperty(before, container.validateItem(after))
        }
      case unknown =>
        log.fatal("Unknown item " + unknown.getClass())
    }
  }
}
