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

package org.digimead.tabuddy.desktop.logic.ui.wizard

import java.io.File
import java.security.PublicKey
import java.util.{ ArrayList, UUID }
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.{ Core, Messages ⇒ CMessages }
import org.digimead.tabuddy.desktop.core.support.{ App, WritableValue }
import org.digimead.tabuddy.desktop.core.ui.UI
import org.digimead.tabuddy.desktop.core.ui.support.{ StatefulMessageManager, SymbolValidator, Validator }
import org.digimead.tabuddy.desktop.logic.{ Logic, Messages }
import org.digimead.tabuddy.desktop.logic.payload.Payload
import org.digimead.tabuddy.desktop.logic.payload.marker.serialization.encryption.Encryption
import org.digimead.tabuddy.desktop.logic.payload.marker.serialization.signature.{ Validator ⇒ SValidator }
import org.digimead.tabuddy.desktop.logic.payload.marker.serialization.signature.api.XValidator
import org.digimead.tabuddy.desktop.logic.ui.support.digest.DigestAdapter
import org.digimead.tabuddy.desktop.logic.ui.support.encryption.EncryptionAdapter
import org.digimead.tabuddy.desktop.logic.ui.support.signature.SignatureAdapter
import org.digimead.tabuddy.model.serialization.{ Serialization, digest, signature }
import org.eclipse.core.databinding.DataBindingContext
import org.eclipse.e4.core.contexts.ContextInjectionFactory
import org.eclipse.jface.databinding.swt.WidgetProperties
import org.eclipse.jface.databinding.viewers.ViewersObservables
import org.eclipse.jface.dialogs.{ IMessageProvider, TitleAreaDialog }
import org.eclipse.jface.viewers.{ ComboViewer, LabelProvider, StructuredSelection }
import org.eclipse.jface.window.Window
import org.eclipse.jface.wizard.{ Wizard, WizardPage }
import org.eclipse.swt.SWT
import org.eclipse.swt.events.{ DisposeEvent, DisposeListener, SelectionAdapter, SelectionEvent }
import org.eclipse.swt.widgets.{ Composite ⇒ JComposite, DirectoryDialog, Event, Listener, Shell, Text }
import org.eclipse.ui.internal.forms.MessageManager
import scala.language.reflectiveCalls

/**
 * NewGraphWizard first page.
 */
class NewGraphWizardPageOne extends WizardPage(Messages.NewGraphWizardPageOne_title_text) with XLoggable {
  /** Page view. */
  protected var view: Option[NewGraphWizardPageOne.Composite] = None
  /** Message manager messages field. */
  protected val messageManagerMessagesField = {
    val field = classOf[MessageManager].getDeclaredField("messages")
    if (!field.isAccessible())
      field.setAccessible(true)
    field
  }

  ContextInjectionFactory.inject(this, Core.context)
  setTitle(Messages.NewGraphWizardPageOne_title_text)
  setDescription(Messages.NewGraphWizardPageOne_description_text)

  /** Create form content. */
  def createControl(parent: JComposite) {
    val view = new NewGraphWizardPageOne.Composite(updateOK, parent, SWT.NONE)
    this.view = Some(view)
    setControl(view)
    setPageComplete(false)
    view.idField.addChangeListener { (id, event) ⇒
      val newId = id.trim
      if (newId.isEmpty()) {
        setDescription(Messages.NewGraphWizardPageOne_description_text)
        getWizard.asInstanceOf[Wizard].setWindowTitle(Messages.NewGraphWizard_shellTitleEmpty_text)
      } else {
        setDescription(Messages.NewGraphWizardPageOne_description_with_name_text.format(newId))
        getWizard.asInstanceOf[Wizard].setWindowTitle(Messages.NewGraphWizard_shellTitle_text.format(newId))
      }
    }
  }
  /** Get graph parameters. */
  def getGraphParameters() = for {
    view ← view
    id ← Option(view.idField.value)
    locationField ← Option(view.locationField.value)
    containerEncryption ← Option(view.containerEncryptionField.value).map { adapter ⇒ (adapter.identifier, view.containerEncryptionParameters) }
    contentEncryption ← Option(view.contentEncryptionField.value).map { adapter ⇒ (adapter.identifier, view.contentEncryptionParameters) }
    digestAcquireField = Option(view.digestAcquireField.value) match {
      case Some(NewGraphWizardPageOne.Acquire.Digest.Optional) ⇒ Some(true)
      case Some(NewGraphWizardPageOne.Acquire.Digest.Required) ⇒ Some(false)
      case _ ⇒ None
    }
    digestFreezeField ← Option(view.digestFreezeField.value).map { adapter ⇒ (adapter.identifier, view.digestFreezeParameters) }
    serializationField ← Option(view.serializationField.value)
    signatureAcquireField ← Option(view.signatureAcquireField.value)
    signatureFreezeField ← Option(view.signatureFreezeField.value).map { adapter ⇒ (adapter.identifier, view.signatureFreezeParameters) }
  } yield (id, locationField, containerEncryption, contentEncryption, digestAcquireField, digestFreezeField,
    serializationField, signatureAcquireField, signatureFreezeField)

  /** Update OK state. */
  protected def updateOK(mmng: StatefulMessageManager) = {
    val messages = mmng.getState().filter {
      case (_, IMessageProvider.ERROR) ⇒ true
      case (_, IMessageProvider.WARNING) ⇒ true
      case (_, IMessageProvider.INFORMATION) ⇒ false
      case (_, IMessageProvider.NONE) ⇒ false
    }
    if (messages.isEmpty) {
      setErrorMessage(null)
      setPageComplete(true)
    } else {
      setPageComplete(false)
    }
  }
  /** Get message manager state. */
  protected def getMessageManagerState(mmng: MessageManager) =
    messageManagerMessagesField.get(mmng).asInstanceOf[ArrayList[AnyRef]]
}

object NewGraphWizardPageOne extends XLoggable {
  /**
   * Acquire combo elements.
   */
  object Acquire {
    object Digest {
      object SelectionRequired
      object Disabled
      object Optional
      object Required
    }
    object Signature {
      object SelectionRequired extends XValidator {
        /** Unique ID. */
        val id: UUID = UUID.randomUUID()
        /** Validator name. */
        val name: Symbol = 'stub
        /** Validator description. */
        val description: String = ""
        /** Get validator rule. */
        def rule: XValidator.Rule = null
        /** Validation routine. */
        def validator: Option[PublicKey] ⇒ Boolean = null
      }
      object Disabled extends XValidator {
        /** Unique ID. */
        val id: UUID = UUID.randomUUID()
        /** Validator name. */
        val name: Symbol = 'stub
        /** Validator description. */
        val description: String = ""
        /** Get validator rule. */
        def rule: XValidator.Rule = null
        /** Validation routine. */
        def validator: Option[PublicKey] ⇒ Boolean = null
      }
    }
  }
  /**
   * Message manager identificators.
   */
  object FormMessages {
    object containerEncryptionFieldError
    object contentEncryptionFieldError
    object digestAcquireFieldError
    object digestFreezeFieldError
    object idFieldError
    object locationFieldError
    object signatureAcquireFieldError
    object signatureFreezeFieldError
  }
  /**
   * NewGraphWizardPageOne content
   */
  class Composite(stateCallback: StatefulMessageManager ⇒ _, parent: JComposite, style: Int) extends NewGraphWizardPageOneView(parent, style) {
    /** Any adapter type: DigestAdapter, EncryptionAdapter or SignatureAdapter */
    type AnyAdapter[A] = { def parameters: Boolean; def dialog(parent: Shell, default: Option[A], tag: String): Option[AnyDialog[A]] }
    /** Any dialog type: DigestAdapter.Dialog, EncryptionAdapter.Dialog or SignatureAdapter.Dialog */
    type AnyDialog[A] = TitleAreaDialog { def get(): Option[Either[String, A]] }

    /** Binding context. */
    protected lazy val bindingContext = new DataBindingContext(App.realm)
    /** Graph container encryption adapter. */
    val containerEncryptionField = WritableValue[EncryptionAdapter](EncryptionAdapter.Empty)
    /** Graph container encryption parameters. */
    var containerEncryptionParameters = Option.empty[Encryption.Parameters]
    /** Graph content encryption adapter. */
    val contentEncryptionField = WritableValue[EncryptionAdapter](EncryptionAdapter.Empty)
    /** Graph content encryption parameters. */
    var contentEncryptionParameters = Option.empty[Encryption.Parameters]
    /** Digest validation.*/
    val digestAcquireField = WritableValue[AnyRef](Acquire.Digest.SelectionRequired)
    /** Digest calculator. */
    val digestFreezeField = WritableValue[DigestAdapter](DigestAdapter.Empty)
    /** Digest calculator parameters. */
    var digestFreezeParameters = Option.empty[digest.Mechanism.Parameters]
    /** Graph identifier. */
    val idField = WritableValue("")
    /** Graph container. */
    val locationField = WritableValue(Logic.graphContainer.getAbsolutePath())
    /** Serialization adapter. */
    val serializationField = WritableValue[Serialization.Identifier](Payload.defaultSerialization)
    /** Signature validation.*/
    val signatureAcquireField = WritableValue[XValidator](Acquire.Signature.SelectionRequired)
    /** Signature generator. */
    val signatureFreezeField = WritableValue[SignatureAdapter](SignatureAdapter.Empty)
    /** Signature generator parameters. */
    var signatureFreezeParameters = Option.empty[signature.Mechanism.Parameters]
    /** Stateful MessageManager */
    protected lazy val mmng = StatefulMessageManager(getForm().getMessageManager())

    initializeUI()
    initializeBindings()
    initializeDefaults()
    updateState()
    this.addDisposeListener(new DisposeListener { def widgetDisposed(e: DisposeEvent) = onDispose })

    /** Update form state. */
    def updateState() {
      val newId = idField.value.trim
      val newLocation = Option(locationField.value).map(_.trim()).getOrElse("")

      if (newId.isEmpty())
        mmng.addMessage(FormMessages.idFieldError, CMessages.identificatorIsNotDefined_text, null, IMessageProvider.WARNING, getTxtIdentificator())
      else
        mmng.removeMessage(FormMessages.idFieldError, getTxtIdentificator())

      if (newLocation.isEmpty())
        mmng.addMessage(FormMessages.locationFieldError, CMessages.warningMessage_text.format(Messages.locationIsEmpty_text.capitalize), null, IMessageProvider.WARNING, getTxtLocation())
      else {
        // validate graph container
        val location = new File(newLocation)
        var ok = location.exists() && location.canWrite()
        if (ok) {
          if (newId.isEmpty())
            mmng.removeMessage(FormMessages.locationFieldError, getTxtLocation())
          else {
            // validate graph directory
            val modelDirectory = new File(location, newId)
            if (modelDirectory.exists()) {
              mmng.addMessage(FormMessages.locationFieldError, CMessages.warningMessage_text.
                format(Messages.locationIsAlreadyExists_text.format(modelDirectory).capitalize), null, IMessageProvider.ERROR, getTxtLocation())
            } else {
              // validate graph descriptor
              val graphDescriptor = new File(location, newId + "." + Payload.extensionGraph)
              if (graphDescriptor.exists())
                mmng.addMessage(FormMessages.locationFieldError, CMessages.warningMessage_text.
                  format(Messages.locationIsAlreadyExists_text.format(graphDescriptor).capitalize), null, IMessageProvider.ERROR, getTxtLocation())
              else
                mmng.removeMessage(FormMessages.locationFieldError, getTxtLocation())
            }
          }
        } else
          mmng.addMessage(FormMessages.locationFieldError, CMessages.warningMessage_text.format(Messages.locationIsIncorrect_text.capitalize), null, IMessageProvider.ERROR, getTxtLocation())
      }

      if (containerEncryptionField.value == EncryptionAdapterSelectionRequired)
        mmng.addMessage(FormMessages.containerEncryptionFieldError, Messages.NewGraphWizardPageOne_containerEncryptionIsNotDefined_text, null, IMessageProvider.WARNING, getComboViewerContainerEncryption.getCCombo())
      else
        mmng.removeMessage(FormMessages.containerEncryptionFieldError, getComboViewerContainerEncryption.getCCombo())

      if (contentEncryptionField.value == EncryptionAdapterSelectionRequired)
        mmng.addMessage(FormMessages.contentEncryptionFieldError, Messages.NewGraphWizardPageOne_contentEncryptionIsNotDefined_text, null, IMessageProvider.WARNING, getComboViewerContentEncryption.getCCombo())
      else
        mmng.removeMessage(FormMessages.contentEncryptionFieldError, getComboViewerContentEncryption.getCCombo())

      if (digestFreezeField.value == DigestAdapterSelectionRequired)
        mmng.addMessage(FormMessages.digestFreezeFieldError, Messages.NewGraphWizardPageOne_digestFreezeIsNotDefined_text, null, IMessageProvider.WARNING, getComboViewerDigestFreeze().getCCombo())
      else
        mmng.removeMessage(FormMessages.digestFreezeFieldError, getComboViewerDigestFreeze().getCCombo())

      if (signatureFreezeField.value == SignatureAdapterSelectionRequired)
        mmng.addMessage(FormMessages.signatureFreezeFieldError, Messages.NewGraphWizardPageOne_signatureFreezeIsNotDefined_text, null, IMessageProvider.WARNING, getComboViewerSignatureFreeze().getCCombo())
      else
        mmng.removeMessage(FormMessages.signatureFreezeFieldError, getComboViewerSignatureFreeze().getCCombo())

      if (digestAcquireField.value == Acquire.Digest.SelectionRequired)
        mmng.addMessage(FormMessages.digestAcquireFieldError, Messages.NewGraphWizardPageOne_digestValidationIsNotSelected_text, null, IMessageProvider.WARNING, getComboViewerDigestAcquire().getCCombo())
      else
        mmng.removeMessage(FormMessages.digestAcquireFieldError, getComboViewerDigestAcquire().getCCombo())

      if (signatureAcquireField.value == Acquire.Signature.SelectionRequired)
        mmng.addMessage(FormMessages.signatureAcquireFieldError, Messages.NewGraphWizardPageOne_signatureValidationIsNotSelected_text, null, IMessageProvider.WARNING, getComboViewerSignatureAcquire().getCCombo())
      else
        mmng.removeMessage(FormMessages.signatureAcquireFieldError, getComboViewerSignatureAcquire().getCCombo())

      stateCallback(mmng)
    }

    /** Initialize UI part. */
    protected def initializeUI() {
      App.assertEventThread()
      // graph name
      getTxtIdentificator.setMessage(Messages.NewGraphWizardPageOne_lblIdentificator_hint_text)
      SymbolValidator(getTxtIdentificator, true) { (validator, event) ⇒
        if (!event.doit)
          validator.withDecoration(validator.showDecorationError(_))
        else
          validator.withDecoration(_.hide)
      }

      // graph location
      getTxtLocation().setMessage(Messages.NewGraphWizardPageOne_lblLocation_hint_text)
      getBtnLocation.addSelectionListener(new SelectionAdapter() {
        override def widgetSelected(event: SelectionEvent) = {
          val widget = event.widget
          UI.findShell(widget) match {
            case Some(shell) ⇒
              Option(selectGraphLocation(shell)).foreach(locationField.setValue)
            case None ⇒
              log.fatal("Unable to find shell for widget " + widget)
          }
        }
      })

      // serialization
      val serializationViewer = getComboViewerSerialization()
      serializationViewer.setLabelProvider(new LabelProvider() {
        override def getText(element: AnyRef): String = element match {
          case identifier: Serialization.Identifier ⇒ s"${identifier.extension.name.toUpperCase} - ${identifier.description}"
          case unknown ⇒ super.getText(unknown)
        }
      })
      Payload.availableSerialization.toSeq.sortBy(_.extension.name.toUpperCase).foreach(serializationViewer.add)

      // container encryption
      val containerEncryptionViewer = getComboViewerContainerEncryption()
      containerEncryptionViewer.setLabelProvider(new LabelProvider() {
        override def getText(element: AnyRef): String = element match {
          case EncryptionAdapterSelectionRequired ⇒ Messages.NewGraphWizardPageOne_selectContainerEncryption_text.capitalize
          case adapter: EncryptionAdapter ⇒ s"${adapter.identifier.name.capitalize} - ${adapter.identifier.description}"
          case unknown ⇒ super.getText(unknown)
        }
      })
      (EncryptionAdapterSelectionRequired +: EncryptionAdapter.Empty +: EncryptionAdapter.validIdentifiers.toSeq.sortBy(_.name.toUpperCase).
        map(EncryptionAdapter.perIdentifier)).foreach(containerEncryptionViewer.add)
      getTextContainerEncryption().setMessage(Messages.noParameters)
      setup(containerEncryptionField, getTextContainerEncryption(), containerEncryptionViewer, Messages.containerEncryption_text,
        (result: Option[Encryption.Parameters]) ⇒ containerEncryptionParameters = result, () ⇒ containerEncryptionParameters)

      // content encryption
      val contentEncryptionViewer = getComboViewerContentEncryption()
      contentEncryptionViewer.setLabelProvider(new LabelProvider() {
        override def getText(element: AnyRef): String = element match {
          case EncryptionAdapterSelectionRequired ⇒ Messages.NewGraphWizardPageOne_selectContentEncryption_text.capitalize
          case adapter: EncryptionAdapter ⇒ s"${adapter.identifier.name.capitalize} - ${adapter.identifier.description}"
          case unknown ⇒ super.getText(unknown)
        }
      })
      (EncryptionAdapterSelectionRequired +: EncryptionAdapter.Empty +: EncryptionAdapter.validIdentifiers.toSeq.sortBy(_.name.toUpperCase).
        map(EncryptionAdapter.perIdentifier)).foreach(contentEncryptionViewer.add)
      setup(contentEncryptionField, getTextContentEncryption(), contentEncryptionViewer, Messages.contentEncryption_text,
        (result: Option[Encryption.Parameters]) ⇒ contentEncryptionParameters = result, () ⇒ contentEncryptionParameters)

      // digest freeze
      val digestFreezeViewer = getComboViewerDigestFreeze()
      digestFreezeViewer.setLabelProvider(new LabelProvider() {
        override def getText(element: AnyRef): String = element match {
          case DigestAdapterSelectionRequired ⇒ Messages.NewGraphWizardPageOne_selectDigestMechanism_text.capitalize
          case adapter: DigestAdapter ⇒ s"${adapter.identifier.name.capitalize} - ${adapter.identifier.description}"
          case unknown ⇒ super.getText(unknown)
        }
      })
      (DigestAdapterSelectionRequired +: DigestAdapter.Empty +: DigestAdapter.validIdentifiers.toSeq.sortBy(_.name.toUpperCase).
        map(DigestAdapter.perIdentifier)).foreach(digestFreezeViewer.add)
      setup(digestFreezeField, getTextDigestFreeze(), digestFreezeViewer, Messages.digest_text,
        (result: Option[digest.Mechanism.Parameters]) ⇒ digestFreezeParameters = result, () ⇒ digestFreezeParameters)

      // signature freeze
      val signatureFreezeViewer = getComboViewerSignatureFreeze()
      signatureFreezeViewer.setLabelProvider(new LabelProvider() {
        override def getText(element: AnyRef): String = element match {
          case SignatureAdapterSelectionRequired ⇒ Messages.NewGraphWizardPageOne_selectSignatureMechanism_text.capitalize
          case adapter: SignatureAdapter ⇒ s"${adapter.identifier.name.capitalize} - ${adapter.identifier.description}"
          case unknown ⇒ super.getText(unknown)
        }
      })
      (SignatureAdapterSelectionRequired +: SignatureAdapter.Empty +: SignatureAdapter.validIdentifiers.toSeq.sortBy(_.name.toUpperCase).
        map(SignatureAdapter.perIdentifier)).foreach(signatureFreezeViewer.add)
      setup(signatureFreezeField, getTextSignatureFreeze(), signatureFreezeViewer, Messages.signature_text,
        (result: Option[signature.Mechanism.Parameters]) ⇒ signatureFreezeParameters = result, () ⇒ signatureFreezeParameters)

      // digest acquire
      val digestAcquireViewer = getComboViewerDigestAcquire()
      digestAcquireViewer.setLabelProvider(new LabelProvider() {
        override def getText(element: AnyRef): String = element match {
          case Acquire.Digest.SelectionRequired ⇒ Messages.NewGraphWizardPageOne_selectDigestValidation_text.capitalize
          case Acquire.Digest.Disabled ⇒ Messages.NewGraphWizardPageOne_digestValidationIsDisabled_text.capitalize
          case Acquire.Digest.Optional ⇒ Messages.NewGraphWizardPageOne_digestValidationOptional_text.capitalize
          case Acquire.Digest.Required ⇒ Messages.NewGraphWizardPageOne_digestValidationRequired_text.capitalize
          case unknown ⇒ super.getText(unknown)
        }
      })
      digestAcquireViewer.add(Acquire.Digest.SelectionRequired)
      digestAcquireViewer.add(Acquire.Digest.Disabled)
      digestAcquireViewer.add(Acquire.Digest.Optional)
      digestAcquireViewer.add(Acquire.Digest.Required)

      // signature acquire
      val signatureAcquireViewer = getComboViewerSignatureAcquire()
      signatureAcquireViewer.setLabelProvider(new LabelProvider() {
        override def getText(element: AnyRef): String = element match {
          case Acquire.Signature.SelectionRequired ⇒ Messages.NewGraphWizardPageOne_selectSignatureValidation_text.capitalize
          case Acquire.Signature.Disabled ⇒ Messages.NewGraphWizardPageOne_signatureValidationIsDisabled_text.capitalize
          case validator: XValidator ⇒ s"${validator.name.name.capitalize} - ${validator.description}"
          case unknown ⇒ super.getText(unknown)
        }
      })
      (Acquire.Signature.SelectionRequired +: Acquire.Signature.Disabled +: SValidator.validators().toSeq.sortBy(_.name.name.capitalize)).foreach(signatureAcquireViewer.add)
    }
    /** Initialize binding part. */
    protected def initializeBindings() {
      // Bind elements to fields
      bindingContext.bindValue(WidgetProperties.text(SWT.Modify).observeDelayed(50, getTxtIdentificator()), idField)
      bindingContext.bindValue(WidgetProperties.text(SWT.Modify).observeDelayed(50, getTxtLocation()), locationField)
      bindingContext.bindValue(ViewersObservables.observeDelayedValue(50,
        ViewersObservables.observeSingleSelection(getComboViewerSerialization())), serializationField)
      bindingContext.bindValue(ViewersObservables.observeDelayedValue(50,
        ViewersObservables.observeSingleSelection(getComboViewerContainerEncryption())), containerEncryptionField)
      bindingContext.bindValue(ViewersObservables.observeDelayedValue(50,
        ViewersObservables.observeSingleSelection(getComboViewerContentEncryption())), contentEncryptionField)
      bindingContext.bindValue(ViewersObservables.observeDelayedValue(50,
        ViewersObservables.observeSingleSelection(getComboViewerDigestAcquire())), digestAcquireField)
      bindingContext.bindValue(ViewersObservables.observeDelayedValue(50,
        ViewersObservables.observeSingleSelection(getComboViewerDigestFreeze())), digestFreezeField)
      bindingContext.bindValue(ViewersObservables.observeDelayedValue(50,
        ViewersObservables.observeSingleSelection(getComboViewerSignatureAcquire())), signatureAcquireField)
      bindingContext.bindValue(ViewersObservables.observeDelayedValue(50,
        ViewersObservables.observeSingleSelection(getComboViewerSignatureFreeze())), signatureFreezeField)

      // Bind fields to logic
      idField.addChangeListener { (id, event) ⇒
        val newId = id.trim
        if (newId.isEmpty())
          getForm().setText(Messages.NewGraphWizardPageOne_formTitle_text)
        else
          getForm().setText(id)
        updateState
      }
      locationField.addChangeListener { (_, event) ⇒ updateState }
      serializationField.addChangeListener { (_, event) ⇒ updateState }
      containerEncryptionField.addChangeListener { (_, event) ⇒ updateState }
      contentEncryptionField.addChangeListener { (_, event) ⇒ updateState }
      digestAcquireField.addChangeListener { (_, event) ⇒ updateState }
      digestFreezeField.addChangeListener { (_, event) ⇒ updateState }
      signatureAcquireField.addChangeListener { (_, event) ⇒ updateState }
      signatureFreezeField.addChangeListener { (_, event) ⇒ updateState }
    }
    /** Initialize default values. */
    protected def initializeDefaults() {
      getComboViewerSerialization().setSelection(new StructuredSelection(Payload.defaultSerialization))
      getComboViewerContainerEncryption().setSelection(new StructuredSelection(EncryptionAdapterSelectionRequired))
      getComboViewerContentEncryption().setSelection(new StructuredSelection(EncryptionAdapterSelectionRequired))
      getComboViewerDigestFreeze().setSelection(new StructuredSelection(DigestAdapterSelectionRequired))
      getComboViewerSignatureFreeze().setSelection(new StructuredSelection(SignatureAdapterSelectionRequired))
      getComboViewerDigestAcquire().setSelection(new StructuredSelection(Acquire.Digest.SelectionRequired))
      getComboViewerSignatureAcquire().setSelection(new StructuredSelection(Acquire.Signature.SelectionRequired))
    }
    /** On dispose callback. */
    protected def onDispose {
      //updateResult
      bindingContext.dispose()

      containerEncryptionField.dispose()
      contentEncryptionField.dispose()
      digestFreezeField.dispose()
      digestAcquireField.dispose()
      idField.dispose()
      locationField.dispose()
      serializationField.dispose()
      signatureFreezeField.dispose()
      signatureAcquireField.dispose()
    }
    /** Get new model location path. */
    protected def selectGraphLocation(shell: Shell) = {
      val dialog = new DirectoryDialog(shell, SWT.OPEN)
      Option(locationField.value).foreach { value ⇒
        val path = value.trim
        if (path.nonEmpty)
          dialog.setFilterPath(path)
      }
      dialog.setMessage(Messages.NewGraphWizard_selectGraphLocation_text)
      dialog.setText(Messages.NewGraphWizard_selectGraphLocation_text)
      dialog.open()
    }
    /** Setup composite of SWT controls that describes logical element. */
    protected def setup[A <: AnyAdapter[B], B](adapterField: WritableValue[A], text: Text, comboViewer: ComboViewer,
      tag: String, fnSetter: Option[B] ⇒ _, fnGetter: () ⇒ Option[B]): Validator[Option[String]] = {
      text.setMessage(Messages.noParameters)
      val validator = Validator(comboViewer.getCCombo(), false) { (validator: Validator[Option[String]], error: Option[String]) ⇒
        error match {
          case Some(message) ⇒ validator.withDecoration(decoration ⇒ validator.showDecorationError(decoration, message))
          case None ⇒ validator.withDecoration(_.hide())
        }
      }
      // Add adapter listener for combo box
      val adapterListener = adapterField.addChangeListener { (adapter, event) ⇒
        mmng.removeMessages(comboViewer.getCCombo())
        mmng.removeMessages(text)
        if (adapter.parameters) {
          text.setEnabled(true)
          text.setMessage(Messages.selectToSetParameters)
          mmng.addMessage(FormMessages.containerEncryptionFieldError,
            Messages.parametersRequired.capitalize, null, IMessageProvider.WARNING, text)
        } else {
          text.setEnabled(false)
          text.setMessage(Messages.noParameters)
        }
        fnSetter(None)
        text.setText("")
        text.setToolTipText(null)
      }
      comboViewer.getCCombo().addDisposeListener(new DisposeListener {
        def widgetDisposed(e: DisposeEvent) = adapterField.removeChangeListener(adapterListener)
      })
      // Add MouseUp/KeyUp listener to text box
      val textListener = new Listener() {
        override def handleEvent(event: Event) = if (adapterField.value.parameters) {
          val dialog = adapterField.value.dialog(text.getShell(), fnGetter(), tag).get
          if (dialog.open() == Window.OK) {
            dialog.get() match {
              case result @ Some(Left(error)) ⇒
                fnSetter(None)
                text.setText("")
                text.setToolTipText(null)
                mmng.addMessage(FormMessages.containerEncryptionFieldError, error, null, IMessageProvider.ERROR, text)
              case result @ Some(Right(value)) ⇒
                fnSetter(Some(value))
                text.setText(value.toString())
                text.setToolTipText(value.toString())
                mmng.removeMessages(text)
              case None ⇒
                log.fatal("Expect value with parameters")
                fnSetter(None)
                text.setText("")
                text.setToolTipText(null)
            }
            updateState
          }
          text.traverse(SWT.TRAVERSE_ESCAPE)
        }
      }
      text.addListener(SWT.MouseUp, textListener)
      text.addListener(SWT.KeyUp, textListener)
      validator
    }
  }
  /**
   * Stub digest adapter.
   */
  object DigestAdapterSelectionRequired extends DigestAdapter {
    /** Identifier of the digest mechanism. */
    val identifier: digest.Mechanism.Identifier = SelectionRequired

    /** Get composite for the digest configuration. */
    def composite(parent: JComposite, default: Option[digest.Mechanism.Parameters]) = None
    /** Get dialog for the digest configuration. */
    def dialog(parent: Shell, default: Option[digest.Mechanism.Parameters], tag: String = Messages.digest_text) = None
    /** Flag indicating whether the parameters are supported. */
    def parameters: Boolean = false

    object SelectionRequired extends digest.Mechanism.Identifier {
      /** Digest name. */
      val name = ""
      /** Digest description. */
      val description: String = ""
    }
  }
  /**
   * Stub encryption adapter.
   */
  object EncryptionAdapterSelectionRequired extends EncryptionAdapter {
    /** Identifier of the encryption mechanism. */
    val identifier: Encryption.Identifier = SelectionRequired

    /** Get composite for the encryption configuration. */
    def composite(parent: JComposite, default: Option[Encryption.Parameters]) = None
    /** Get dialog for the encryption configuration. */
    def dialog(parent: Shell, default: Option[Encryption.Parameters], tag: String = Messages.encryption_text) = None
    /** Flag indicating whether the parameters are supported. */
    def parameters: Boolean = false

    object SelectionRequired extends Encryption.Identifier {
      /** Encryption name. */
      val name = ""
      /** Encryption description. */
      val description: String = ""
    }
  }
  /**
   * Stub signature adapter.
   */
  object SignatureAdapterSelectionRequired extends SignatureAdapter {
    /** Identifier of the signature mechanism. */
    val identifier: signature.Mechanism.Identifier = SelectionRequired

    /** Get composite for the signature configuration. */
    def composite(parent: JComposite, default: Option[signature.Mechanism.Parameters]) = None
    /** Get dialog for the signature configuration. */
    def dialog(parent: Shell, default: Option[signature.Mechanism.Parameters], tag: String = Messages.signature_text) = None
    /** Flag indicating whether the parameters are supported. */
    def parameters: Boolean = false

    object SelectionRequired extends signature.Mechanism.Identifier {
      /** Signature name. */
      val name = ""
      /** Signature description. */
      val description: String = ""
    }
  }
}
