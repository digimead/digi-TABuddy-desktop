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

package org.digimead.tabuddy.desktop.model.definition.dialog.eltemed

import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.Messages
import org.digimead.tabuddy.desktop.model.definition.Default
import org.digimead.tabuddy.desktop.ui.support.{ SymbolValidator, Validator }
import org.eclipse.core.databinding.observable.{ ChangeEvent, IChangeListener }
import org.eclipse.jface.databinding.swt.WidgetProperties
import org.eclipse.jface.viewers.{ CellEditor, CellLabelProvider, EditingSupport, TableViewer, TextCellEditor, ViewerCell }
import org.eclipse.swt.SWT
import org.eclipse.swt.events.VerifyEvent
import org.eclipse.swt.graphics.Point
import org.eclipse.swt.widgets.{ Composite, Control, Text }

object ColumnId extends Loggable {
  class TLabelProvider extends CellLabelProvider {
    override def update(cell: ViewerCell) = cell.getElement() match {
      case item: ElementTemplateEditor.Item ⇒
        cell.setText(item.id)
        item.idError.foreach(err ⇒ cell.setImage(err._2))
      case unknown ⇒
        log.fatal("Unknown item " + unknown.getClass())
    }
    override def getToolTipText(element: AnyRef): String = element match {
      case item: ElementTemplateEditor.Item ⇒
        item.idError match {
          case Some(error) ⇒ error._1
          case None ⇒ null
        }
      case unknown ⇒
        log.fatal("Unknown item " + unknown.getClass())
        null
    }
    override def getToolTipShift(obj: Object): Point = Default.toolTipShift
    override def getToolTipDisplayDelayTime(obj: Object): Int = Default.toolTipDisplayDelayTime
    override def getToolTipTimeDisplayed(obj: Object): Int = Default.toolTipTimeDisplayed
  }
  class TEditingSupport(viewer: TableViewer, container: ElementTemplateEditor) extends EditingSupport(viewer) {
    override protected def getCellEditor(element: AnyRef): CellEditor =
      new IdTextCellEditor(viewer.getTable(), element.asInstanceOf[ElementTemplateEditor.Item], container)
    override protected def canEdit(element: AnyRef): Boolean = true
    override protected def getValue(element: AnyRef): AnyRef = element match {
      case item: ElementTemplateEditor.Item ⇒
        item.id
      case unknown ⇒
        log.fatal("Unknown item " + unknown.getClass())
        ""
    }
    override protected def setValue(element: AnyRef, value: AnyRef): Unit = element match {
      case before: ElementTemplateEditor.Item ⇒
        val id = value.asInstanceOf[String].trim
        if (id.nonEmpty && before.id != id && !container.actualProperties.exists(_.id == id)) {
          val after = before.copy(id = id)
          container.updateActualProperty(before, container.validateItem(after))
        }
      case unknown ⇒
        log.fatal("Unknown item " + unknown.getClass())
    }
  }
  class IdTextCellEditor(parent: Composite, item: ElementTemplateEditor.Item, container: ElementTemplateEditor) extends TextCellEditor(parent) {
    /** Creates the control for this cell editor under the given parent control. */
    override def createControl(parent: Composite): Control = {
      val text = super.createControl(parent).asInstanceOf[Text]
      val validator = SymbolValidator(text, true)(validate)
      WidgetProperties.text(SWT.Modify).observe(text).addChangeListener(new IChangeListener() {
        override def handleChange(event: ChangeEvent) = {
          val newId = text.getText().trim
          if (newId.isEmpty())
            validator.withDecoration(validator.showDecorationRequired(_))
          else if (container.actualProperties.exists(_.id == newId) && newId != item.id)
            validator.withDecoration(validator.showDecorationError(_, Messages.identificatorIsAlreadyInUse_text.format(newId)))
          else
            validator.withDecoration(_.hide)
        }
      })
      text
    }
    /** Validates an input */
    def validate(validator: Validator, event: VerifyEvent) = if (!event.doit)
      validator.withDecoration(validator.showDecorationError(_))
    else
      validator.withDecoration(_.hide)
  }
}
