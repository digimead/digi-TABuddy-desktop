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

package org.digimead.tabuddy.desktop.ui.toolbar

import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.log.Loggable
import org.digimead.digi.lib.log.logger.RichLogger.rich2slf4j
import org.digimead.tabuddy.desktop.Data
import org.digimead.tabuddy.desktop.Main
import org.digimead.tabuddy.desktop.Resources
import org.digimead.tabuddy.desktop.payload.Payload
import org.digimead.tabuddy.desktop.res.Messages
import org.digimead.tabuddy.desktop.support.SymbolValidator
import org.digimead.tabuddy.desktop.support.Validator
import org.digimead.tabuddy.desktop.support.WritableValue
import org.digimead.tabuddy.desktop.support.WritableValue.wrapper2underlying
import org.digimead.tabuddy.desktop.ui.action.ActionLocalStorageDelete
import org.digimead.tabuddy.desktop.ui.action.ActionLocalStorageFreeze
import org.digimead.tabuddy.desktop.ui.action.ActionLocalStorageLock
import org.eclipse.core.databinding.beans.BeanProperties
import org.eclipse.core.databinding.observable.value.IValueChangeListener
import org.eclipse.core.databinding.observable.value.ValueChangeEvent
import org.eclipse.jface.action.ControlContribution
import org.eclipse.jface.action.ToolBarManager
import org.eclipse.jface.databinding.viewers.ObservableListContentProvider
import org.eclipse.jface.viewers.ComboViewer
import org.eclipse.jface.viewers.ISelectionChangedListener
import org.eclipse.jface.viewers.IStructuredSelection
import org.eclipse.jface.viewers.SelectionChangedEvent
import org.eclipse.swt.SWT
import org.eclipse.swt.events.ModifyEvent
import org.eclipse.swt.events.ModifyListener
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.widgets.Combo
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Control
import org.eclipse.swt.widgets.Label

object MainLocalStorage extends ToolBarManager with Loggable {
  @volatile protected var modelLabel: Option[Label] = None
  @volatile protected var modelCombo: Option[ComboViewer] = None
  private val modelComboText = WritableValue("")
  Main.bindingContext.bindValue(Data.fieldModelName, modelComboText)
  BeanProperties.value(null, "")
  log.debug("alive")

  // initialize
  // add label
  add(new ControlContribution(null) {
    protected def createControl(parent: Composite): Control = {
      val container = new Composite(parent, SWT.NONE)
      container.setLayout(new GridLayout(1, false))
      val label = new Label(container, SWT.NONE)
      label.setAlignment(SWT.CENTER);
      label.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1))
      label.setText(Messages.localModel_text)
      label.setToolTipText(Messages.localModel_tooltip_text)
      label.setFont(Resources.fontSmall)
      modelLabel = Some(label)
      container
    }
  })
  // add combo
  add(new ControlContribution(null) {
    protected def createControl(parent: Composite): Control = {
      val viewer = new ComboViewer(parent, SWT.NONE)
      val validator = SymbolValidator(viewer.getCombo, true) {
        (validator, event) => validateComboText(validator, event.getSource.asInstanceOf[Combo].getText, event.doit)
      }
      viewer.getCombo.setToolTipText(Messages.localModel_tooltip_text)
      viewer.setContentProvider(new ObservableListContentProvider())
      viewer.setInput(Data.availableModels.underlying)
      // there is no built in support for combo text field property binding
      // bind combo -> modelComboText
      viewer.getCombo.addModifyListener(new ModifyListener() {
        def modifyText(e: ModifyEvent) = {
          validator.withDecoration(_.hide())
          val newValue = e.getSource().asInstanceOf[Combo].getText()
          if (modelComboText.getValue() != newValue)
            modelComboText.setValue(newValue)
        }
      })
      // there is no built in support for combo text field property binding
      // bind modelComboText -> combo
      modelComboText.addValueChangeListener(new IValueChangeListener() {
        def handleValueChange(event: ValueChangeEvent) {
          val newValue = event.diff.getNewValue().asInstanceOf[String]
          modelCombo.foreach { viewer =>
            val combo = viewer.getCombo
            if (combo.getText() != newValue)
              combo.setText(newValue)
          }
        }
      })
      viewer.addSelectionChangedListener(new ISelectionChangedListener() {
        override def selectionChanged(event: SelectionChangedEvent) = event.getSelection() match {
          case selection: IStructuredSelection if !selection.isEmpty() =>
            validateComboText(validator, event.getSource.asInstanceOf[ComboViewer].getCombo().getText, true)
          case selection =>
        }
      })
      Data.modelName.addChangeListener { (name, event) => viewer.getCombo.setEnabled(name == Payload.defaultModelIdentifier.name) }
      viewer.getCombo.setEnabled(Data.modelName.value == Payload.defaultModelIdentifier.name)
      modelCombo = Some(viewer)
      validateComboText(validator, viewer.getCombo.getText, true)
      viewer.getCombo
    }
  })
  // add actions
  add(ActionLocalStorageLock)
  add(ActionLocalStorageFreeze)
  add(ActionLocalStorageDelete)

  @log
  override def dispose() {
    getItems().foreach(_.dispose())
    super.dispose()
  }
  /** Validates a text in the the combo viewer */
  def validateComboText(validator: Validator, text: String, valid: Boolean) = if (!valid)
    validator.withDecoration { validator.showDecorationError(_) }
  else if (text.isEmpty())
    validator.withDecoration { validator.showDecorationRequired(_) }
  else
    validator.withDecoration { _.hide() }
}
