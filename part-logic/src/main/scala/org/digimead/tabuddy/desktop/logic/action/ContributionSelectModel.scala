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

package org.digimead.tabuddy.desktop.logic.action

import scala.collection.mutable

import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.Messages
import org.digimead.tabuddy.desktop.Resources
import org.digimead.tabuddy.desktop.Resources.resources2implementation
import org.digimead.tabuddy.desktop.logic.Data
import org.digimead.tabuddy.desktop.logic.operation.OperationModelNew
import org.digimead.tabuddy.desktop.logic.operation.OperationModelOpen
import org.digimead.tabuddy.desktop.logic.payload.Payload
import org.digimead.tabuddy.desktop.logic.payload.Payload.payload2implementation
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.App.app2implementation
import org.digimead.tabuddy.desktop.support.SymbolValidator
import org.digimead.tabuddy.desktop.support.Validator
import org.digimead.tabuddy.desktop.support.WritableValue
import org.digimead.tabuddy.desktop.support.WritableValue.wrapper2underlying
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.Model.model2implementation
import org.eclipse.core.databinding.observable.value.IValueChangeListener
import org.eclipse.core.databinding.observable.value.ValueChangeEvent
import org.eclipse.core.runtime.jobs.Job
import org.eclipse.jface.action.ControlContribution
import org.eclipse.jface.databinding.viewers.ObservableListContentProvider
import org.eclipse.jface.layout.RowLayoutFactory
import org.eclipse.jface.viewers.ComboViewer
import org.eclipse.jface.viewers.ISelectionChangedListener
import org.eclipse.jface.viewers.IStructuredSelection
import org.eclipse.jface.viewers.LabelProvider
import org.eclipse.jface.viewers.SelectionChangedEvent
import org.eclipse.jface.viewers.StructuredSelection
import org.eclipse.swt.SWT
import org.eclipse.swt.events.KeyAdapter
import org.eclipse.swt.events.KeyEvent
import org.eclipse.swt.events.ModifyEvent
import org.eclipse.swt.events.ModifyListener
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.layout.RowData
import org.eclipse.swt.widgets.Combo
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Control
import org.eclipse.swt.widgets.Label
import org.eclipse.swt.widgets.Widget

class ContributionSelectModel extends ControlContribution(ContributionSelectModel.id) with Loggable {
  val id = getClass.getName
  @volatile protected var combo: Option[ComboViewer] = None
  @volatile protected var label: Option[Label] = None
  /** Id text value. */
  protected val idValue = WritableValue("")

  ContributionSelectModel.instance += (ContributionSelectModel.this) -> {}

  def comboMinimumWidth = 80
  def comboMaximumWidth = (window.getShell().getBounds().width / 4).toInt

  /** Create contribution control. */
  override protected def createControl(parent: Composite): Control = {
    val parentShell = App.findShell(parent)

    val container = new Composite(parent, SWT.NONE)
    val layout = RowLayoutFactory.fillDefaults().wrap(false).spacing(0).create()
    layout.marginLeft = 3
    layout.center = true
    container.setLayout(layout)
    val label = createLabel(container)
    ContributionSelectModel.this.label = Option(label)
    val comboViewer = createCombo(container)
    ContributionSelectModel.this.combo = Option(comboViewer)

    //
    // initialize combo
    //
    val context = App.getWindowContext(parent.getShell())
    // propagate idValue -> context Data.Id.modelIdUserInput
    idValue.addChangeListener { (id, event) =>
      if (id == Messages.default_text || id == Payload.defaultModel.eId.name)
        context.set(Data.Id.modelIdUserInput, "")
      else
        context.set(Data.Id.modelIdUserInput, id)
    }
    // acquire Data.modelName -> combo
    Data.modelName.addChangeListener { (name, event) =>
      if (name == Payload.defaultModel.eId.name) {
        comboViewer.getCombo.setEnabled(true)
      } else {
        comboViewer.getCombo.setEnabled(false)
        comboViewer.setSelection(new StructuredSelection(name), true)
      }
    }
    // acquire Data.availableModels -> combo
    comboViewer.setInput(Data.availableModels.underlying)
    Data.availableModels.addChangeListener { (event) => App.exec { resizeCombo() } }
    idValue.value = Messages.default_text
    resizeCombo()
    container
  }
  protected def createLabel(parent: Composite): Label = {
    val container = new Composite(parent, SWT.NONE)
    container.setLayout(new GridLayout(1, false))
    val label = new Label(container, SWT.NONE)
    label.setAlignment(SWT.CENTER);
    label.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1))
    label.setText(Messages.localModel_text + ":")
    label.setToolTipText(Messages.localModel_tooltip_text)
    label.setFont(Resources.fontSmall)
    label
  }
  /** Create combo box for toolbar. */
  protected def createCombo(parent: Composite): ComboViewer = {
    val viewer = new ComboViewer(parent, SWT.NONE)
    val validator = SymbolValidator(viewer.getCombo, true) {
      (validator, event) => validateComboText(validator, event.getSource.asInstanceOf[Combo].getText, event.doit)
    }
    viewer.getCombo.setToolTipText(Messages.localModel_tooltip_text)
    viewer.setContentProvider(new ObservableListContentProvider())
    viewer.setLabelProvider(new ContributionSelectModel.ComboLabelProvider())
    // there is no built in support for combo text field property binding
    // bind combo -> modelComboText
    viewer.getCombo.addModifyListener(new ModifyListener() {
      def modifyText(e: ModifyEvent) = {
        validator.withDecoration(_.hide())
        val newValue = e.getSource().asInstanceOf[Combo].getText()
        if (idValue.getValue() != newValue)
          idValue.setValue(newValue)
      }
    })
    // there is no built in support for combo text field property binding
    // bind idValue -> combo
    idValue.addValueChangeListener(new IValueChangeListener() {
      def handleValueChange(event: ValueChangeEvent) {
        val newValue = event.diff.getNewValue().asInstanceOf[String]
        combo.foreach { viewer =>
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
    viewer.getCombo.addKeyListener(new KeyAdapter() { override def keyReleased(e: KeyEvent) = if (e.keyCode == SWT.CR) onEnter(e.widget) })
    viewer.getCombo.setEnabled(Data.modelName.value == Payload.defaultModel.eId.name)
    viewer.getCombo.setLayoutData(new RowData(comboMinimumWidth, SWT.DEFAULT))
    viewer
  }
  /** Get combo text. */
  def getComboText() = if (Model.eId == Payload.defaultModel.eId) Messages.default_text else Model.eId.name
  /** On Enter key event. */
  protected def onEnter(widget: Widget) = if (idValue.value.nonEmpty) {
    val id = idValue.value
    if (id.nonEmpty)
      Payload.listModels.find(marker => marker.isValid && marker.id.name == id) match {
        case Some(marker) =>
          OperationModelOpen(Some(Model.eId), Symbol(id), false) foreach { operation =>
            operation.getExecuteJob() match {
              case Some(job) =>
                job.setPriority(Job.SHORT)
                job.schedule()
              case None =>
                log.fatal(s"Unable to create job for ${operation}.")
            }
          }
        case None =>
          OperationModelNew(Some(id), None, true) foreach { operation =>
            operation.getExecuteJob() match {
              case Some(job) =>
                job.setPriority(Job.SHORT)
                job.schedule()
              case None =>
                log.fatal(s"Unable to create job for ${operation}.")
            }
          }
      }
  }
  /** Resize combo viewer */
  protected def resizeCombo() = for {
    combo <- combo
    control = combo.getCombo()
  } {
    val prefferedWidth = control.computeSize(SWT.DEFAULT, SWT.DEFAULT, true).x
    val width = math.min(math.max(comboMinimumWidth, prefferedWidth), comboMaximumWidth)
    control.setLayoutData(new RowData(width, SWT.DEFAULT))
    control.getParent().layout()
  }
  /** Validates a text in the the combo viewer */
  protected def validateComboText(validator: Validator, text: String, valid: Boolean) = if (!valid)
    validator.withDecoration { validator.showDecorationError(_) }
  else
    validator.withDecoration { _.hide() }
  protected def window = combo.get.getControl().getShell()
}

object ContributionSelectModel {
  /** All SelectModel instances. */
  private val instance = new mutable.WeakHashMap[ContributionSelectModel, Unit] with mutable.SynchronizedMap[ContributionSelectModel, Unit]
  /** Singleton identificator. */
  val id = getClass.getName().dropRight(1)

  class ComboLabelProvider extends LabelProvider {
    override def getText(element: AnyRef): String = element match {
      case value: String if value == Payload.defaultModel.eId.name => Messages.default_text
      case value => super.getText(element)
    }
  }
}
