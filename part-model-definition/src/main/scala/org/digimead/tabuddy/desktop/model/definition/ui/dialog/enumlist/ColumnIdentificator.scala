/**
 * This file is part of the TA Buddy project.
 * Copyright (c) 2012-2015 Alexey Aksenov ezh@ezh.msk.ru
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

package org.digimead.tabuddy.desktop.model.definition.ui.dialog.enumlist

import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.{ Messages ⇒ CoreMessages }
import org.digimead.tabuddy.desktop.logic.{ Messages ⇒ LogicMessages }
import org.digimead.tabuddy.desktop.logic.payload.Enumeration
import org.digimead.tabuddy.desktop.model.definition.Default
import org.digimead.tabuddy.desktop.core.ui.support.{ SymbolValidator, Validator }
import org.eclipse.core.databinding.observable.{ ChangeEvent, IChangeListener }
import org.eclipse.jface.databinding.swt.WidgetProperties
import org.eclipse.jface.viewers.{ CellEditor, CellLabelProvider, EditingSupport, TableViewer, TextCellEditor, ViewerCell }
import org.eclipse.swt.SWT
import org.eclipse.swt.events.{ FocusAdapter, FocusEvent, VerifyEvent }
import org.eclipse.swt.graphics.Point
import org.eclipse.swt.widgets.{ Composite, Control, Text }

object ColumnIdentificator extends XLoggable {
  class TLabelProvider extends CellLabelProvider {
    /** Update the label for cell. */
    override def update(cell: ViewerCell) = cell.getElement() match {
      case item: Enumeration[_] ⇒
        cell.setText(item.id.name)
      case unknown ⇒
        log.fatal("Unknown item " + unknown.getClass())
    }
    /** Get the text displayed in the tool tip for object. */
    override def getToolTipText(element: Object): String = element match {
      case item: Enumeration[_] ⇒
        LogicMessages.enumerationTooltip_text.format(item.ptype.id.name, item.ptype.typeClass.getName, item.constants.size)
      case unknown ⇒
        log.fatal("Unknown item " + unknown.getClass())
        null
    }
    override def getToolTipShift(obj: Object): Point = Default.toolTipShift
    override def getToolTipDisplayDelayTime(obj: Object): Int = Default.toolTipDisplayDelayTime
    override def getToolTipTimeDisplayed(obj: Object): Int = Default.toolTipTimeDisplayed
  }
  class TEditingSupport(viewer: TableViewer, container: EnumerationList) extends EditingSupport(viewer) {
    override protected def getCellEditor(element: AnyRef): CellEditor = new IdentifierTextCellEditor(viewer.getTable(), container, element.asInstanceOf[Enumeration[_]])
    override protected def canEdit(element: AnyRef): Boolean = true
    override protected def getValue(element: AnyRef): AnyRef = element match {
      case item: Enumeration[_] ⇒
        item.id.name
      case unknown ⇒
        log.fatal("Unknown item " + unknown.getClass())
        ""
    }
    override protected def setValue(element: AnyRef, value: AnyRef): Unit = element match {
      case before: Enumeration[_] ⇒
        val id = value.asInstanceOf[String].trim
        if (id.nonEmpty && id != before.id.name && !container.actual.exists(_.id.name == id))
          container.updateActualEnumeration(before, before.copy(id = Symbol(id)))
      case unknown ⇒
        log.fatal("Unknown item " + unknown.getClass())
    }
  }
  class IdentifierTextCellEditor(parent: Composite, container: EnumerationList, enumeration: Enumeration[_]) extends TextCellEditor(parent) {
    /** Creates the control for this cell editor under the given parent control. */
    override def createControl(parent: Composite): Control = {
      val text = super.createControl(parent).asInstanceOf[Text]
      val validator = SymbolValidator(text, true)(validate)
      validator.decoration.get.foreach(_.setShowOnlyOnFocus(true))
      WidgetProperties.text(SWT.Modify).observe(text).addChangeListener(new IChangeListener() {
        override def handleChange(event: ChangeEvent) = {
          val newID = text.getText().trim
          if (newID.isEmpty())
            validator.withDecoration(validator.showDecorationRequired(_))
          else if (container.actual.exists(_.id.name == newID) && newID != enumeration.id.name)
            validator.withDecoration(validator.showDecorationError(_, CoreMessages.identificatorIsAlreadyInUse_text.format(newID)))
          else
            validator.withDecoration(_.hide)
        }
      })
      text.addFocusListener(new FocusAdapter() {
        override def focusLost(e: FocusEvent) = validator.withDecoration(_.hide())
      })
      text
    }
    /** Validates an input */
    def validate(validator: Validator[VerifyEvent], event: VerifyEvent) = if (!event.doit)
      validator.withDecoration(validator.showDecorationError(_))
    else
      validator.withDecoration(_.hide)
  }
}
