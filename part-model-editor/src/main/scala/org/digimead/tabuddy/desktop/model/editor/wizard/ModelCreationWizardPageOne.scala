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

package org.digimead.tabuddy.desktop.model.editor.wizard

import java.io.File

import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.Core
import org.digimead.tabuddy.desktop.model.editor.Messages
import org.digimead.tabuddy.desktop.logic.Data
import org.digimead.tabuddy.desktop.logic.payload.Payload
import org.digimead.tabuddy.desktop.logic.payload.Payload.payload2implementation
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.App.app2implementation
import org.digimead.tabuddy.desktop.definition.Context.rich2appContext
import org.digimead.tabuddy.desktop.support.SymbolValidator
import org.digimead.tabuddy.desktop.support.WritableValue
import org.digimead.tabuddy.desktop.support.WritableValue.wrapper2underlying
import org.eclipse.e4.core.contexts.Active
import org.eclipse.e4.core.contexts.ContextInjectionFactory
import org.eclipse.e4.core.di.annotations.Optional
import org.eclipse.jface.databinding.swt.WidgetProperties
import org.eclipse.jface.wizard.Wizard
import org.eclipse.jface.wizard.WizardPage
import org.eclipse.swt.SWT
import org.eclipse.swt.events.DisposeEvent
import org.eclipse.swt.events.DisposeListener
import org.eclipse.swt.events.SelectionAdapter
import org.eclipse.swt.events.SelectionEvent
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.DirectoryDialog
import org.eclipse.swt.widgets.Shell

import javax.inject.Inject
import javax.inject.Named

class ModelCreationWizardPageOne extends WizardPage(Messages.ModelCreationWizardPageOne_title_text) with Loggable {
  /** Page view. */
  protected var view: Option[ModelCreationWizardPageOneView] = None
  /** Model identifier. */
  protected val idField = WritableValue("")
  /** Model container. */
  protected val locationField = WritableValue("")
  /** Current Data.Id.modelIdUserInput. */
  @Inject @Optional @Active @Named(Data.Id.modelIdUserInput)
  val modelIdUserInput: String = null
  ContextInjectionFactory.inject(this, Core.context)
  Option(modelIdUserInput).foreach(idField.value = _)

  setTitle(Messages.ModelCreationWizardPageOne_title_text)
  setDescription(Messages.ModelCreationWizardPageOne_description_text)

  /** Create form content. */
  def createControl(parent: Composite) {
    val view = new ModelCreationWizardPageOneView(parent, SWT.NONE)
    this.view = Some(view)
    // model name
    view.getTxtModelIdentificator.setMessage(Messages.ModelCreationWizardPageOne_lblModelIdentificator_hint_text)
    val modelNameBinding = App.bindingContext.bindValue(WidgetProperties.text(SWT.Modify).observeDelayed(50, view.getTxtModelIdentificator), idField)
    view.getTxtModelIdentificator.addDisposeListener(new DisposeListener {
      def widgetDisposed(e: DisposeEvent) = App.bindingContext.removeBinding(modelNameBinding)
    })
    val idFieldValidator = SymbolValidator(view.getTxtModelIdentificator, true) { (validator, event) =>
      if (!event.doit)
        validator.withDecoration(validator.showDecorationError(_))
      else
        validator.withDecoration(_.hide)
    }
    // model location
    view.getTxtModelLocation().setMessage(Messages.ModelCreationWizardPageOne_lblModelLocation_hint_text)
    val modelLocationBinding = App.bindingContext.bindValue(WidgetProperties.text(SWT.Modify).observeDelayed(50, view.getTxtModelLocation()), locationField)
    view.getTxtModelLocation.addDisposeListener(new DisposeListener {
      def widgetDisposed(e: DisposeEvent) = App.bindingContext.removeBinding(modelLocationBinding)
    })
    view.getBtnModelLocation.addSelectionListener(new SelectionAdapter() {
      override def widgetSelected(event: SelectionEvent) = {
        val widget = event.widget
        App.findShell(widget) match {
          case Some(shell) =>
            locationField.setValue(selectModelLocation(shell))
          case None =>
            log.fatal("Unable to find shell for widget " + widget)
        }
      }
    })
    setControl(view)
    setPageComplete(false)
    // setup data listeners
    idField.addChangeListener { (id, event) =>
      val newId = id.trim
      if (newId.isEmpty()) {
        idFieldValidator.withDecoration(idFieldValidator.showDecorationRequired(_))
        getWizard.asInstanceOf[Wizard].setWindowTitle(Messages.ModelCreationWizardPageOne_shellTitleEmpty_text)
      } else {
        idFieldValidator.withDecoration(_.hide)
        getWizard.asInstanceOf[Wizard].setWindowTitle(Messages.ModelCreationWizardPageOne_shellTitle_text.format(newId))
      }
      updateOK
    }
    locationField.addChangeListener { (location, event) => updateOK }
  }
  /** Get model location. */
  def getModelLocation(): File = {
    val location = new File(locationField.value.trim)
    val id = idField.value.trim
    new File(location, id)
  }
  /** Get new model location path. */
  def selectModelLocation(shell: Shell) = {
    val dialog = new DirectoryDialog(shell, SWT.OPEN)
    val result = dialog.open()
    result
  }

  /** Update OK state. */
  protected def updateOK() = {
    val newId = idField.value.trim
    val newLocation = locationField.value.trim()
    val ok = {
      val ok = newId.nonEmpty
      if (!ok)
        setErrorMessage(Messages.ModelCreationWizardPageOne_identifierIsEmpty_text)
      ok
    } && {
      val ok = newLocation.nonEmpty
      if (!ok)
        setErrorMessage(Messages.ModelCreationWizardPageOne_locationIsEmpty_text)
      ok
    } && {
      // validate model container
      val location = new File(newLocation)
      var ok = location.exists() && location.canWrite()
      if (ok) {
        // validate model directory
        val modelDirectory = new File(location, newId)
        if (modelDirectory.exists()) {
          setErrorMessage(Messages.ModelCreationWizardPageOne_locationIsAlreadyExists_text.format(modelDirectory))
          ok = false
        } else {
          // validate model descriptor
          val modelDescriptor = new File(location, newId + "." + Payload.extensionModel)
          if (modelDescriptor.exists()) {
            setErrorMessage(Messages.ModelCreationWizardPageOne_locationIsAlreadyExists_text.format(modelDescriptor))
            ok = false
          }
        }
      } else
        setErrorMessage(Messages.ModelCreationWizardPageOne_locationIsIncorrect_text)
      ok
    }
    if (ok) setErrorMessage(null)
    setPageComplete(ok)
  }
}
