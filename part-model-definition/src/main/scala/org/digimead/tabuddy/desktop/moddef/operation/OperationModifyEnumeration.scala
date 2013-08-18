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

package org.digimead.tabuddy.desktop.moddef.operation

import java.util.concurrent.Exchanger

import scala.reflect.runtime.universe

import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.Messages
import org.digimead.tabuddy.desktop.definition.Operation
import org.digimead.tabuddy.desktop.logic.Data
import org.digimead.tabuddy.desktop.logic.payload
import org.digimead.tabuddy.desktop.moddef.dialog.enumed.EnumerationEditor
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.App.app2implementation
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.Model.model2implementation
import org.digimead.tabuddy.model.element.Element
import org.eclipse.core.runtime.IAdaptable
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.Status
import org.eclipse.jface.dialogs.ErrorDialog
import org.eclipse.swt.widgets.Shell

/**
 * Modify an enumeration list.
 */
class OperationModifyEnumeration(enumeration: payload.api.Enumeration[_ <: AnyRef with java.io.Serializable],
  enumerationList: Set[payload.api.Enumeration[_ <: AnyRef with java.io.Serializable]], modelId: Symbol)
  extends org.digimead.tabuddy.desktop.logic.operation.OperationModifyEnumeration.Abstract(enumeration, enumerationList, modelId) with Loggable {
  @volatile protected var allowExecute = true
  @volatile protected var allowRedo = false
  @volatile protected var allowUndo = false
  @volatile protected var savedEnumeration: Option[Element.Generic] = None

  override def canExecute() = allowExecute
  override def canRedo() = allowRedo
  override def canUndo() = allowUndo

  protected def dialog(): Operation.Result[payload.api.Enumeration[_ <: AnyRef with java.io.Serializable]] = {
    val exchanger = new Exchanger[Operation.Result[payload.api.Enumeration[_ <: AnyRef with java.io.Serializable]]]()
    App.assertUIThread(false)
    App.exec {
      App.getActiveShell match {
        case Some(shell) => if (Data.getAvailableTypes().isEmpty) {
          App.execBlocking {
            ErrorDialog.openError(shell, null, Messages.enumerationUnableToCreate_text,
              new Status(IStatus.INFO, "unknown", IStatus.OK, Messages.enumerationUnableToCreateNoTypes_text, null))
            exchanger.exchange(Operation.Result.Error(Messages.enumerationUnableToCreate_text, false))
          }
        } else {
          val dialog = new EnumerationEditor(shell, enumeration, enumerationList.toList)
          dialog.openOrFocus {
            case result if result == org.eclipse.jface.window.Window.OK =>
              exchanger.exchange(Operation.Result.OK({
                val enumeration = dialog.getModifiedEnumeration
                if (enumerationList.contains(enumeration) && payload.Enumeration.compareDeep(this.enumeration, enumeration))
                  None // nothing changes, and enumeration is already exists
                else
                  Some(enumeration)
              }))
            case result =>
              exchanger.exchange(Operation.Result.Cancel())
          }
        } case None =>
          exchanger.exchange(Operation.Result.Error("Unable to find active shell."))
      }
    }
    exchanger.exchange(null)
  }
  protected def execute(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[payload.api.Enumeration[_ <: AnyRef with java.io.Serializable]] = redo(monitor, info)
  protected def redo(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[payload.api.Enumeration[_ <: AnyRef with java.io.Serializable]] = {
    assert(Model.eId == modelId, "An unexpected model %s, expect %s".format(Model.eId, modelId))
    // process the job
    val result: Operation.Result[payload.api.Enumeration[_ <: AnyRef with java.io.Serializable]] = if (canRedo) {
      // TODO replay history, modify enumeration: before -> after
      Operation.Result.Error[payload.api.Enumeration[_ <: AnyRef with java.io.Serializable]]("Unimplemented")
    } else if (canExecute) {
      // TODO save modification history
      dialog
    } else
      Operation.Result.Error(s"Unable to process $this: redo and execute are prohibited")
    // update the job state
    result match {
      case Operation.Result.OK(_, _) =>
        allowExecute = false
        allowRedo = false
        allowUndo = true
      case Operation.Result.Cancel(_) =>
        allowExecute = true
        allowRedo = false
        allowUndo = false
      case _ =>
        allowExecute = false
        allowRedo = false
        allowUndo = false
    }
    // return the result
    result
  }
  protected def undo(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[payload.api.Enumeration[_ <: AnyRef with java.io.Serializable]] = {
    // TODO revert history, modify enumeration: after -> before
    Operation.Result.Error("Unimplemented")
  }
}
