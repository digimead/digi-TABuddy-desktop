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
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.{ Core, Messages ⇒ CMessages }
import org.digimead.tabuddy.desktop.core.support.{ App, WritableValue }
import org.digimead.tabuddy.desktop.core.ui.UI
import org.digimead.tabuddy.desktop.core.ui.support.{ SymbolValidator, Validator }
import org.digimead.tabuddy.desktop.logic.Logic
import org.digimead.tabuddy.desktop.logic.payload.Payload
import org.digimead.tabuddy.desktop.logic.payload.marker.serialization.encryption.Encryption
import org.digimead.tabuddy.desktop.logic.ui.support.digest.DigestAdapter
import org.digimead.tabuddy.desktop.logic.ui.support.encryption.EncryptionAdapter
import org.digimead.tabuddy.desktop.logic.ui.support.signature.SignatureAdapter
import org.digimead.tabuddy.desktop.logic.Messages
import org.digimead.tabuddy.model.serialization.{ Serialization, digest, signature }
import org.eclipse.e4.core.contexts.ContextInjectionFactory
import org.eclipse.jface.databinding.swt.WidgetProperties
import org.eclipse.jface.databinding.viewers.ViewersObservables
import org.eclipse.jface.dialogs.TitleAreaDialog
import org.eclipse.jface.viewers.{ ComboViewer, LabelProvider, StructuredSelection }
import org.eclipse.jface.window.Window
import org.eclipse.jface.wizard.{ Wizard, WizardPage }
import org.eclipse.swt.SWT
import org.eclipse.swt.events.{ DisposeEvent, DisposeListener, SelectionAdapter, SelectionEvent }
import org.eclipse.swt.widgets.{ Composite ⇒ JComposite, DirectoryDialog, Event, Listener, Shell, Text }
import scala.language.reflectiveCalls
import org.eclipse.jface.dialogs.IMessageProvider
import org.eclipse.core.databinding.DataBindingContext

class NewGraphWizardPageOne extends WizardPage(Messages.NewGraphWizardPageOne_title_text) with XLoggable {
  /** Page view. */
  protected var view: Option[NewGraphWizardPageOneView] = None

  ContextInjectionFactory.inject(this, Core.context)
  setTitle(Messages.NewGraphWizardPageOne_title_text)
  setDescription(Messages.NewGraphWizardPageOne_description_text)

  /** Create form content. */
  def createControl(parent: JComposite) {
    val view = new NewGraphWizardPageOne.Composite(updateOK, parent, SWT.NONE)
    this.view = Some(view)

    // common part
    setControl(view)
    setPageComplete(false)
    //    val mmng = view.getForm().getMessageManager()
    //            mmng.addMessage("textLength",
    //          "Text is longer than 0 characters", null,
    //          IMessageProvider.INFORMATION, signatureViewer.getCCombo())

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
  /** Get graph location. */
  def getGraphLocation(): File = {
    //    val location = new File(locationField.value.trim)
    //    val id = idField.value.trim
    //    new File(location, id)
    null
  }

  /** Update OK state. */
  protected def updateOK() = {
    //    val newId = idField.value.trim
    //    val newLocation = Option(locationField.value).map(_.trim()).getOrElse("")
    //    val ok = {
    //      ok
    //    } && {
    //    } && {
    //      // validate model container
    //      val location = new File(newLocation)
    //      var ok = location.exists() && location.canWrite()
    //      if (ok) {
    //        // validate model directory
    //        val modelDirectory = new File(location, newId)
    //        if (modelDirectory.exists()) {
    //          setErrorMessage(CMessages.warningMessage_text.format(Messages.locationIsAlreadyExists_text.format(modelDirectory)))
    //          ok = false
    //        } else {
    //          // validate model descriptor
    //          //          val modelDescriptor = new File(location, newId + "." + Payload.extensionModel)
    //          //          if (modelDescriptor.exists()) {
    //          //            setErrorMessage(Messages.locationIsAlreadyExists_text.format(modelDescriptor))
    //          //            ok = false
    //          //          }
    //        }
    //      } else
    //        setErrorMessage(CMessages.warningMessage_text.format(Messages.locationIsIncorrect_text))
    //      ok
    //    }
    //    if (ok) setErrorMessage(null)
    //    setPageComplete(ok)
  }
}

object NewGraphWizardPageOne extends XLoggable {
  /**
   * Acquire combo elements
   */
  object Acquire {
    object Digest {
      object SelectionRequired
      object Disabled
      object Optional
      object Required
    }
    object Signature {
      object SelectionRequired
    }
  }
  /**
   * Message manager identificators
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
  class Composite(stateCallback: () ⇒ _, parent: JComposite, style: Int) extends NewGraphWizardPageOneView(parent, style) {
    /** Any adapter type: DigestAdapter, EncryptionAdapter or SignatureAdapter */
    type AnyAdapter[A] = { def parameters: Boolean; def dialog(parent: Shell, default: Option[A], tag: String): Option[AnyDialog[A]] }
    /** Any dialog type: DigestAdapter.Dialog, EncryptionAdapter.Dialog or SignatureAdapter.Dialog */
    type AnyDialog[A] = TitleAreaDialog { def get(): Option[Either[String, A]] }

    /** Binding context. */
    protected lazy val bindingContext = new DataBindingContext(App.realm)
    /** Graph container encryption adapter. */
    val containerEncryptionField = WritableValue[EncryptionAdapter](EncryptionAdapter.Empty)
    /** Graph container encryption parameters. */
    protected var containerEncryptionParameters = Option.empty[Encryption.Parameters]
    /** Graph content encryption adapter. */
    val contentEncryptionField = WritableValue[EncryptionAdapter](EncryptionAdapter.Empty)
    /** Graph content encryption parameters. */
    protected var contentEncryptionParameters = Option.empty[Encryption.Parameters]
    /** Digest validation.*/
    val digestAcquireField = WritableValue[AnyRef](Acquire.Digest.SelectionRequired)
    /** Digest calculator. */
    val digestFreezeField = WritableValue[DigestAdapter](DigestAdapter.Empty)
    /** Digest calculator parameters. */
    protected var digestFreezeParameters = Option.empty[digest.Mechanism.Parameters]
    /** Graph identifier. */
    val idField = WritableValue("")
    /** Graph container. */
    val locationField = WritableValue(Logic.graphContainer.getAbsolutePath())
    /** Graph serialization adapter. */
    val serializationField = WritableValue[Serialization.Identifier](Payload.defaultSerialization)
    /** Graph signature adapter. */
    val signatureFreezeField = WritableValue[SignatureAdapter](SignatureAdapter.Empty)
    /** Graph signature parameters. */
    protected var signatureFreezeParameters = Option.empty[signature.Mechanism.Parameters]

    initializeUI()
    initializeBindings()
    initializeDefaults()
    updateState()
    this.addDisposeListener(new DisposeListener { def widgetDisposed(e: DisposeEvent) = onDispose })

    /** Update form state. */
    def updateState() {
      val mmng = getForm().getMessageManager()
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
        mmng.addMessage(FormMessages.digestFreezeFieldError, Messages.NewGraphWizardPageOne_digestValidationIsNotSelected_text, null, IMessageProvider.WARNING, getComboViewerDigestAcquire().getCCombo())
      else
        mmng.removeMessage(FormMessages.digestFreezeFieldError, getComboViewerDigestAcquire().getCCombo())

      stateCallback()
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
    }
    /** On dispose callback. */
    protected def onDispose {
      //updateResult
      bindingContext.dispose()

      containerEncryptionField.dispose()
      contentEncryptionField.dispose()
      digestFreezeField.dispose()
      idField.dispose()
      locationField.dispose()
      serializationField.dispose()
      signatureFreezeField.dispose()
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
        getForm().getMessageManager().removeMessages(comboViewer.getCCombo())
        getForm().getMessageManager().removeMessages(text)
        if (adapter.parameters) {
          text.setEnabled(true)
          text.setMessage(Messages.selectToSetParameters)
          getForm().getMessageManager().addMessage(FormMessages.containerEncryptionFieldError,
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
                getForm().getMessageManager().addMessage(FormMessages.containerEncryptionFieldError,
                  error, null, IMessageProvider.ERROR, text)
              case result @ Some(Right(value)) ⇒
                fnSetter(Some(value))
                text.setText(value.toString())
                text.setToolTipText(value.toString())
                getForm().getMessageManager().removeMessages(text)
              case None ⇒
                log.fatal("Expect value with parameters")
                fnSetter(None)
                text.setText("")
                text.setToolTipText(null)
            }
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