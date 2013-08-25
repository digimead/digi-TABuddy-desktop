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

package org.digimead.tabuddy.desktop.element.editor.approver

import scala.reflect.runtime.universe

import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.definition.Operation
import org.digimead.tabuddy.desktop.definition.OperationApprover
import org.digimead.tabuddy.desktop.logic.operation.OperationDeleteElement.Abstract
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.App.app2implementation
import org.eclipse.core.commands.operations.IOperationHistory
import org.eclipse.core.commands.operations.IUndoableOperation
import org.eclipse.core.runtime.IAdaptable
import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.Status
import org.eclipse.jface.dialogs.MessageDialog

class OperationDeleteElement extends OperationApprover with Loggable {
  def proceedExecuting(operation: IUndoableOperation, history: IOperationHistory, info: IAdaptable): IStatus = operation match {
    case operation: Abstract if operation.interactive =>
      App.execBlocking {
        App.getActiveShell match {
          case Some(shell) =>
            // ask user confirmation
            MessageDialog.openConfirm(shell, "Are you sure?",
              "Please confirm delete of %s element".format(operation.element)) match {
                case true =>
                  Status.OK_STATUS
                case false =>
                  Operation.Result.Cancel()
              }
          case None =>
            log.error("Unable to find active shell.")
            Status.OK_STATUS
        }
      }
    case operation => Status.OK_STATUS
  }
  def proceedRedoing(operation: IUndoableOperation, history: IOperationHistory, info: IAdaptable): IStatus = Status.OK_STATUS
  def proceedUndoing(operation: IUndoableOperation, history: IOperationHistory, info: IAdaptable): IStatus = Status.OK_STATUS
}