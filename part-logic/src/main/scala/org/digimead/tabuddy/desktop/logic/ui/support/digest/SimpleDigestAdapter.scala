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

package org.digimead.tabuddy.desktop.logic.ui.support.digest

import org.digimead.tabuddy.desktop.core.support.{ App, WritableValue }
import org.digimead.tabuddy.desktop.logic.Messages
import org.digimead.tabuddy.model.serialization.digest.{ Mechanism, SimpleDigest }
import org.eclipse.core.databinding.DataBindingContext
import org.eclipse.jface.databinding.viewers.ViewersObservables
import org.eclipse.jface.dialogs.{ IDialogConstants, TitleAreaDialog }
import org.eclipse.jface.viewers.{ LabelProvider, StructuredSelection }
import org.eclipse.swt.SWT
import org.eclipse.swt.events.{ DisposeEvent, DisposeListener }
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.widgets.{ Button, Composite, Control, Shell }

/**
 * Adapter between model.serialization.digest.SimpleDigest and UI
 */
class SimpleDigestAdapter extends DigestAdapter {
  /** Identifier of the digest mechanism. */
  val identifier: Mechanism.Identifier = SimpleDigest.Identifier

  /** Get composite for the digest configuration. */
  def composite(parent: Composite, default: Option[Mechanism.Parameters]) =
    default match {
      case Some(p: SimpleDigest.Parameters) ⇒
        Option(new SimpleDigestAdapter.SimpleDigestComposite(parent, SWT.NONE, Option(p)))
      case Some(other) ⇒
        throw new IllegalArgumentException(s"Expect Base.Parameters, but ${other} found")
      case None ⇒
        Option(new SimpleDigestAdapter.SimpleDigestComposite(parent, SWT.NONE, None))
    }
  /** Get dialog for the digest configuration. */
  def dialog(parent: Shell, default: Option[Mechanism.Parameters], tag: String = Messages.digest_text) =
    default match {
      case Some(p: SimpleDigest.Parameters) ⇒
        Option(new SimpleDigestAdapter.SimpleDigestDialog(parent, Option(p), tag))
      case Some(other) ⇒
        throw new IllegalArgumentException(s"Expect Base.Parameters, but ${other} found")
      case None ⇒
        Option(new SimpleDigestAdapter.SimpleDigestDialog(parent, None, tag))
    }
  /** Flag indicating whether the parameters are supported. */
  def parameters: Boolean = true
}

object SimpleDigestAdapter {
  /** All supported algorithms by mechanism. */
  val algorithms = Seq(("SHA-512", "hash algorithms defined in the FIPS PUB 180-2.", SimpleDigest("SHA-512")),
    ("SHA-384", "hash algorithms defined in the FIPS PUB 180-2.", SimpleDigest("SHA-384")),
    ("SHA-256", "hash algorithms defined in the FIPS PUB 180-2.", SimpleDigest("SHA-256")),
    ("SHA", "hash algorithms defined in the FIPS PUB 180-2.", SimpleDigest("SHA-1")),
    ("MD5", "the MD5 message digest algorithm as defined in RFC 1321.", SimpleDigest("MD5")),
    ("MD2", "the MD2 message digest algorithm as defined in RFC 1319.", SimpleDigest("MD2"))).sortBy(_._1)

  /**
   * SimpleDigest adapter composite
   */
  class SimpleDigestComposite(parent: Composite, style: Int, defaultParameters: Option[SimpleDigest.Parameters])
    extends SimpleDigestAdapterSkel(parent, style) with DigestAdapter.Composite {
    /** Binding context. */
    lazy val bindingContext = new DataBindingContext(App.realm)
    /** Digest algorythm field. */
    lazy val digestAlgorithmField = App.execNGet(WritableValue[(String, String, SimpleDigest.Parameters)])
    /** Composite result */
    @volatile protected var result = Option.empty[Either[String, Mechanism.Parameters]]

    initializeUI()
    initializeBindings()
    initializeDefaults()
    this.addDisposeListener(new DisposeListener {
      def widgetDisposed(e: DisposeEvent) = onDispose
    })

    /** Get an error or digest parameters. */
    def get(): Option[Either[String, Mechanism.Parameters]] = result

    /** On dispose callback. */
    protected def onDispose {
      updateResult
      bindingContext.dispose()
      digestAlgorithmField.dispose()
    }
    /** Initialize UI part. */
    protected def initializeUI() {
      App.assertEventThread()
      val comboViewerDigestAlgorithm = getComboViewerDigestAlgorithm()
      comboViewerDigestAlgorithm.setLabelProvider(new LabelProvider() {
        override def getText(element: AnyRef): String = element match {
          case (null, null, null) ⇒ Messages.Adapter_selectDigestAlgorithm_text
          case (name, description: String, length) ⇒ s"${name} - ${description.capitalize}"
          case unknown ⇒ super.getText(unknown)
        }
      })
      val digestAlgorithmFieldBinding = bindingContext.bindValue(ViewersObservables.
        observeDelayedValue(50, ViewersObservables.observeSingleSelection(comboViewerDigestAlgorithm)), digestAlgorithmField)
      comboViewerDigestAlgorithm.getCCombo().addDisposeListener(new DisposeListener {
        def widgetDisposed(e: DisposeEvent) = bindingContext.removeBinding(digestAlgorithmFieldBinding)
      })
      comboViewerDigestAlgorithm.add((null, null, null))
      SimpleDigestAdapter.algorithms.foreach(comboViewerDigestAlgorithm.add)
    }
    /** Initialize binding part. */
    protected def initializeBindings() {
      digestAlgorithmField.addChangeListener { case ((name, description, length), event) ⇒ updateResult }
    }
    /** Initialize default values. */
    protected def initializeDefaults() = defaultParameters match {
      case Some(parameters) ⇒
        SimpleDigestAdapter.algorithms.find(_._3 == parameters).foreach(value ⇒ getComboViewerDigestAlgorithm.setSelection(new StructuredSelection(value)))
      case None ⇒
        getComboViewerDigestAlgorithm.setSelection(new StructuredSelection((null, null, null)))
    }
    /** Update result value. */
    protected def updateResult = {
      for {
        digestAlgorithm ← Option(digestAlgorithmField.value) if digestAlgorithm._3 != null
      } yield digestAlgorithm._3
    } match {
      case Some(algorithm) ⇒
        result = Some(Right(algorithm))
      case _ ⇒
        result = Some(Left(Messages.parametersRequired_text))
    }
  }
  /**
   * SimpleDigest adapter dialog
   */
  class SimpleDigestDialog(parentShell: Shell, defaultValue: Option[SimpleDigest.Parameters], tag: String)
    extends TitleAreaDialog(parentShell) with DigestAdapter.Dialog {
    /** Private field with content's composite. */
    @volatile protected var content: SimpleDigestComposite = null

    /** Get an error or digest parameters. */
    def get(): Option[Either[String, Mechanism.Parameters]] = Option(content) match {
      case Some(content) ⇒ content.get()
      case None ⇒ Some(Left(Messages.parametersRequired_text))
    }

    override protected def configureShell(shell: Shell) {
      super.configureShell(shell)
      shell.setText(Messages.Adapter_selectXParameters_text.format(SimpleDigest.Identifier.name.capitalize, tag))
    }
    override def create() {
      super.create()
      setTitle(SimpleDigest.Identifier.name.capitalize)
      setMessage(SimpleDigest.Identifier.description.capitalize)
    }
    override protected def createButton(parent: Composite, id: Int, label: String, defaultButton: Boolean): Button = {
      val button = super.createButton(parent, id, label, defaultButton)
      if (id == IDialogConstants.OK_ID) {
        button.setEnabled(content.get().map(_.isRight).getOrElse(false))
        content.digestAlgorithmField.addChangeListener { case (_, event) ⇒ button.setEnabled(content.get().map(_.isRight).getOrElse(false)) }
      }
      button
    }
    override protected def createDialogArea(parent: Composite): Control = {
      content = new SimpleDigestAdapter.SimpleDigestComposite(parent, SWT.NONE, defaultValue)
      content.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1))
      content
    }
  }
}
