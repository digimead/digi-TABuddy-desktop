/**
 * This file is part of the TA Buddy project.
 * Copyright (c) 2014 Alexey Aksenov ezh@ezh.msk.ru
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

package org.digimead.tabuddy.desktop.logic.ui.support.encryption

import org.digimead.tabuddy.desktop.core.support.{ App, WritableValue }
import org.digimead.tabuddy.desktop.logic.{ Default, Messages }
import org.digimead.tabuddy.desktop.logic.payload.marker.serialization.encryption.{ Encryption, XOR }
import org.eclipse.core.databinding.DataBindingContext
import org.eclipse.jface.databinding.swt.WidgetProperties
import org.eclipse.jface.databinding.viewers.ViewersObservables
import org.eclipse.jface.dialogs.{ IDialogConstants, TitleAreaDialog }
import org.eclipse.jface.viewers.{ LabelProvider, StructuredSelection }
import org.eclipse.swt.SWT
import org.eclipse.swt.events.{ DisposeEvent, DisposeListener, SelectionAdapter, SelectionEvent }
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.widgets.{ Button, Composite, Control, Shell }

/**
 * Adapter between logic.payload.marker.serialization.encryption.XOR and UI
 */
class XORAdapter extends EncryptionAdapter {
  /** Identifier of the encryption mechanism. */
  val identifier: Encryption.Identifier = XOR.Identifier

  /** Get composite for the encryption configuration. */
  def composite(parent: Composite, default: Option[Encryption.Parameters]) =
    default match {
      case Some(p: XOR.Parameters) ⇒
        Option(new XORAdapter.XORComposite(parent, SWT.NONE, Option(p)))
      case Some(other) ⇒
        throw new IllegalArgumentException(s"Expect XOR.Parameters, but ${other} found")
      case None ⇒
        Option(new XORAdapter.XORComposite(parent, SWT.NONE, None))
    }
  /** Get dialog for the encryption configuration. */
  def dialog(parent: Shell, default: Option[Encryption.Parameters], tag: String = Messages.encryption_text) =
    default match {
      case Some(p: XOR.Parameters) ⇒
        Option(new XORAdapter.XORDialog(parent, Option(p), tag))
      case Some(other) ⇒
        throw new IllegalArgumentException(s"Expect XOR.Parameters, but ${other} found")
      case None ⇒
        Option(new XORAdapter.XORDialog(parent, None, tag))
    }
  /** Flag indicating whether the parameters are supported. */
  def parameters: Boolean = true
}

object XORAdapter {
  /**
   * XOR adapter composite
   */
  class XORComposite(parent: Composite, style: Int, defaultParameters: Option[XOR.Parameters])
    extends XORAdapterSkel(parent, style) with EncryptionAdapter.Composite {
    /** Binding context. */
    lazy val bindingContext = new DataBindingContext(App.realm)
    /** Secret field. */
    lazy val secretField = WritableValue[String]
    /** Composite result */
    @volatile protected var result = Option.empty[Either[String, Encryption.Parameters]]

    initializeUI()
    initializeBindings()
    initializeDefaults()
    this.addDisposeListener(new DisposeListener {
      def widgetDisposed(e: DisposeEvent) = onDispose
    })

    /** Get an error or encryption parameters. */
    def get(): Option[Either[String, Encryption.Parameters]] = result

    /** On dispose callback. */
    protected def onDispose {
      updateResult
      bindingContext.dispose()
      secretField.dispose()
    }
    /** Initialize UI part. */
    protected def initializeUI() {
      App.assertEventThread()
      // secret
      val textSecret = getTxtSecret()
      textSecret.setEchoChar(Default.passwordChar)
      textSecret.setMessage(Messages.Adapter_txtSecret_hint_text)
      textSecret.setToolTipText(Messages.Adapter_txtSecret_tip_text)
      val secretFieldBinding = bindingContext.bindValue(WidgetProperties.text(SWT.Modify).observeDelayed(50, textSecret), secretField)
      textSecret.addDisposeListener(new DisposeListener {
        def widgetDisposed(e: DisposeEvent) = bindingContext.removeBinding(secretFieldBinding)
      })
      val btnSecretShow = getBtnSecretShow()
      btnSecretShow.setToolTipText(Messages.showHiddenText_text.capitalize)
      btnSecretShow.addSelectionListener(new SelectionAdapter {
        override def widgetSelected(e: SelectionEvent) =
          if (btnSecretShow.getSelection()) textSecret.setEchoChar(0.toChar) else textSecret.setEchoChar(Default.passwordChar)
      })
    }
    /** Initialize binding part. */
    protected def initializeBindings() {
      secretField.addChangeListener { case (secret, event) ⇒ updateResult }
    }
    /** Initialize default values. */
    protected def initializeDefaults() = defaultParameters match {
      case Some(parameters) ⇒
        parameters.key.foreach(getTxtSecret().setText)
      case None ⇒
    }
    /** Update result value. */
    protected def updateResult = {
      for {
        secret ← Option(secretField.value)
      } yield secret
    } match {
      case Some(secret) if secret.nonEmpty ⇒
        result = Some(Right(XOR(secret)))
      case _ ⇒
        result = Some(Left(Messages.parametersRequired_text))
    }
  }
  /**
   * XOR adapter dialog
   */
  class XORDialog(parentShell: Shell, defaultValue: Option[XOR.Parameters], tag: String)
    extends TitleAreaDialog(parentShell) with EncryptionAdapter.Dialog {
    /** Private field with content's composite. */
    @volatile protected var content: XORComposite = null

    /** Get an error or encryption parameters. */
    def get(): Option[Either[String, Encryption.Parameters]] = Option(content) match {
      case Some(content) ⇒ content.get()
      case None ⇒ Some(Left(Messages.parametersRequired_text))
    }

    override protected def configureShell(shell: Shell) {
      super.configureShell(shell)
      shell.setText(Messages.Adapter_selectXParameters_text.format(XOR.Identifier.name.capitalize, tag))
    }
    override def create() {
      super.create()
      setTitle(XOR.Identifier.name.capitalize)
      setMessage(XOR.Identifier.description.capitalize)
    }
    override protected def createButton(parent: Composite, id: Int, label: String, defaultButton: Boolean): Button = {
      val button = super.createButton(parent, id, label, defaultButton)
      if (id == IDialogConstants.OK_ID) {
        button.setEnabled(content.get().map(_.isRight).getOrElse(false))
        content.secretField.addChangeListener { case (_, event) ⇒ button.setEnabled(content.get().map(_.isRight).getOrElse(false)) }
      }
      button
    }
    override protected def createDialogArea(parent: Composite): Control = {
      content = new XORAdapter.XORComposite(parent, SWT.NONE, defaultValue)
      content.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1))
      content
    }
  }
}
