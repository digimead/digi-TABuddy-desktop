/**
 * This file is part of the TA Buddy project.
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
import java.net.URI
import java.util.concurrent.{ Callable, CancellationException, Exchanger }
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.definition.Operation
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.ui.definition.INewWizard
import org.digimead.tabuddy.desktop.core.{ Messages ⇒ CMessages }
import org.digimead.tabuddy.desktop.logic.Messages
import org.digimead.tabuddy.desktop.logic.operation.graph.OperationGraphNew
import org.digimead.tabuddy.desktop.logic.payload.marker.GraphMarker
import org.digimead.tabuddy.desktop.logic.payload.marker.api.XGraphMarker
import org.digimead.tabuddy.desktop.logic.payload.marker.serialization.encryption.Encryption
import org.digimead.tabuddy.desktop.logic.ui.support.digest.DigestAdapter
import org.digimead.tabuddy.desktop.logic.ui.support.encryption.EncryptionAdapter
import org.digimead.tabuddy.desktop.logic.ui.support.signature.SignatureAdapter
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.graph.Graph
import org.eclipse.core.runtime.jobs.Job
import org.eclipse.jface.dialogs.ErrorDialog
import org.eclipse.jface.wizard.Wizard

/**
 * New graph wizard.
 */
class NewGraphWizard extends Wizard with INewWizard with XLoggable {
  /** The only available page. */
  lazy val one = new NewGraphWizardPageOne()
  setWindowTitle(Messages.NewGraphWizard_shellTitleEmpty_text)

  /**
   * Adds any last-minute pages to this wizard.
   * <p>
   * This method is called just before the wizard becomes visible, to give the
   * wizard the opportunity to add any lazily created pages.
   * </p>
   */
  override def addPages() {
    addPage(one)
  }
  /** This method is invoked before wizard opening. */
  def init(argument: AnyRef) {}
  /**
   * Performs any actions appropriate in response to the user
   * having pressed the Finish button, or refuse if finishing
   * now is not permitted.
   *
   * Normally this method is only called on the container's
   * current wizard. However if the current wizard is a nested wizard
   * this method will also be called on all wizards in its parent chain.
   * Such parents may use this notification to save state etc. However,
   * the value the parents return from this method is ignored.
   *
   * @return <code>true</code> to indicate the finish request
   *   was accepted, and <code>false</code> to indicate
   *   that the finish request was refused
   */
  @log
  def performFinish() = {
    try {
      one.getGraphParameters() match {
        case Some((graphName, graphContainer, containerEncryption, contentEncryption, digestAcquireField, digestFreezeField,
          serializationType, signatureAcquireField, signatureFreezeField)) ⇒
          /*
           * Return Callable[GraphMarker] since OperationGraphNew blocks UI thread
           */
          result = Some(new Callable[GraphMarker] {
            def call = {
              val exchanger = new Exchanger[Operation.Result[Graph[_ <: Model.Like]]]()
              OperationGraphNew(graphName, new File(graphContainer), serializationType).foreach { operation ⇒
                operation.getExecuteJob() match {
                  case Some(job) ⇒
                    job.setPriority(Job.LONG)
                    job.onComplete(exchanger.exchange).schedule()
                  case None ⇒
                    throw new RuntimeException(s"Unable to create job for ${operation}.")
                }
              }
              exchanger.exchange(null) match {
                case Operation.Result.OK(Some(graph), message) ⇒
                  val marker = GraphMarker(graph)
                  // containerEncryption
                  val containerEncryptionArgument = containerEncryption match {
                    case (EncryptionAdapter.Empty.identifier, _) ⇒ Map[URI, Encryption.Parameters]()
                    case (identifier, Some(parameters)) ⇒ Map[URI, Encryption.Parameters](marker.graphPath.toURI() -> parameters)
                    case (_, _) ⇒ Map[URI, Encryption.Parameters]()
                  }
                  marker.containerEncryption = XGraphMarker.Encryption(containerEncryptionArgument)
                  // contentEncryption
                  val contentEncryptionArgument = contentEncryption match {
                    case (EncryptionAdapter.Empty.identifier, _) ⇒ Map[URI, Encryption.Parameters]()
                    case (identifier, Some(parameters)) ⇒ Map[URI, Encryption.Parameters](marker.graphPath.toURI() -> parameters)
                    case (_, _) ⇒ Map[URI, Encryption.Parameters]()
                  }
                  marker.contentEncryption = XGraphMarker.Encryption(contentEncryptionArgument)
                  // digest
                  val digestAquire = digestAcquireField
                  val digestFreeze = digestFreezeField match {
                    case (DigestAdapter.Empty.identifier, _) ⇒ None
                    case (identifier, Some(parameters)) ⇒ Some(Map(marker.graphPath.toURI() -> parameters))
                    case (_, _) ⇒ None
                  }
                  marker.digest = XGraphMarker.Digest(digestAcquireField, digestFreeze)
                  // signature
                  val signatureAquire = signatureAcquireField match {
                    case NewGraphWizardPageOne.Acquire.Signature.SelectionRequired ⇒ None
                    case NewGraphWizardPageOne.Acquire.Signature.Disabled ⇒ None
                    case validator ⇒ Some(validator.id)
                  }
                  val signatureFreeze = signatureFreezeField match {
                    case (SignatureAdapter.Empty.identifier, _) ⇒ None
                    case (identifier, Some(parameters)) ⇒ Some(Map(marker.graphPath.toURI() -> parameters))
                    case (_, _) ⇒ None
                  }
                  marker.signature = XGraphMarker.Signature(signatureAquire, signatureFreeze)
                  log.info(s"Operation completed successfully.")
                  marker
                case Operation.Result.OK(None, message) ⇒
                  throw new CancellationException(s"Operation canceled, reason: ${message}.")
                case Operation.Result.Cancel(message) ⇒
                  throw new CancellationException(s"Operation canceled, reason: ${message}.")
                case err: Operation.Result.Error[_] ⇒
                  throw err
                case other ⇒
                  throw new RuntimeException(s"Unable to complete operation: ${other}.")
              }
            }
          })
          true
        case None ⇒
          log.fatal("Unable to get graph parameters.")
      }
      true
    } catch {
      case e: Throwable ⇒
        val status = App.throwableToMultiStatus(e, App.bundle(getClass))
        ErrorDialog.openError(one.getShell(), CMessages.error_text + ".",
          Messages.NewGraphWizardPageOne_creationError_text, status)
        false
    }
  }
}
