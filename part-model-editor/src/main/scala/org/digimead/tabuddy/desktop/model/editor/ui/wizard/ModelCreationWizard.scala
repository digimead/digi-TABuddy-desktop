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
//
//import org.digimead.digi.lib.aop.log
//import org.digimead.digi.lib.log.api.Loggable
//import org.digimead.tabuddy.desktop.model.editor.Messages
//import org.digimead.tabuddy.desktop.logic.payload.Payload
//import org.eclipse.core.runtime.IStatus
//import org.eclipse.core.runtime.Status
//import org.eclipse.jface.dialogs.ErrorDialog
//import org.eclipse.jface.wizard.Wizard
//
//class ModelCreationWizard extends Wizard with INewWizard with Loggable {
//  /** The only available page. */
//  lazy val one = new ModelCreationWizardPageOne()
//  setWindowTitle(Messages.ModelCreationWizardPageOne_shellTitleEmpty_text)
//
//  /**
//   * Adds any last-minute pages to this wizard.
//   * <p>
//   * This method is called just before the wizard becomes visible, to give the
//   * wizard the opportunity to add any lazily created pages.
//   * </p>
//   */
//  override def addPages() {
//    addPage(one)
//  }
//  /** This method is invoked before wizard opening. */
//  def init(argument: AnyRef) {}
//  /**
//   * Performs any actions appropriate in response to the user
//   * having pressed the Finish button, or refuse if finishing
//   * now is not permitted.
//   *
//   * Normally this method is only called on the container's
//   * current wizard. However if the current wizard is a nested wizard
//   * this method will also be called on all wizards in its parent chain.
//   * Such parents may use this notification to save state etc. However,
//   * the value the parents return from this method is ignored.
//   *
//   * @return <code>true</code> to indicate the finish request
//   *   was accepted, and <code>false</code> to indicate
//   *   that the finish request was refused
//   */
//  @log
//  def performFinish() = {
//    val location = one.getModelLocation()
//    val id = location.getName()
//    try {
//      val marker = Payload.createModel(location)
//      if (Payload.acquireModel(marker).isEmpty) {
//        val status = new Status(IStatus.ERROR, Messages.ModelCreationWizardPageOne_title_text,
//          Messages.ModelCreationWizardPageOne_creationError_text)
//        ErrorDialog.openError(one.getShell(), CoreMessages.error_text + ".",
//          Messages.ModelCreationWizardPageOne_creationError_text, status)
//        false
//      } else {
//        result = Some(marker)
//        true
//      }
//    } catch {
//      case e: Throwable =>
//        val status = App.throwableToMultiStatus(e, App.bundle(getClass))
//        ErrorDialog.openError(one.getShell(), CoreMessages.error_text + ".",
//          Messages.ModelCreationWizardPageOne_creationError_text, status)
//        false
//    }
//  }
//}
