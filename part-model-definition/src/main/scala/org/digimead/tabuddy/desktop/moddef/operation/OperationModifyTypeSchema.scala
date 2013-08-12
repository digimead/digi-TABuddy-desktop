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

package org.digimead.tabuddy.desktop.moddef.operation

import scala.reflect.runtime.universe

import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.definition.Operation
import org.digimead.tabuddy.desktop.logic.payload
import org.digimead.tabuddy.desktop.moddef.dialog.typelist.TypeList
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.App.app2implementation
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.Model.model2implementation
import org.eclipse.core.runtime.IAdaptable
import org.eclipse.core.runtime.IProgressMonitor

class OperationModifyTypeSchema(schema: payload.api.TypeSchema, schemaList: Set[payload.api.TypeSchema], isActive: Boolean, modelId: Symbol)
  extends org.digimead.tabuddy.desktop.logic.operation.OperationModifyTypeSchema.Abstract(schema, schemaList, isActive, modelId) with Loggable {
  @volatile protected var allowExecute = true
  @volatile protected var allowRedo = false
  @volatile protected var allowUndo = false
  @volatile protected var jobResult: Option[Operation.Result[(payload.api.TypeSchema, Boolean)]] = None

  override def canExecute() = allowExecute
  override def canRedo() = allowRedo
  override def canUndo() = allowUndo

  protected def execute(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[(payload.api.TypeSchema, Boolean)] = redo(monitor, info)
  protected def redo(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[(payload.api.TypeSchema, Boolean)] = {
    assert(Model.eId == modelId, "unexpected model %s, expect %s".format(Model.eId, modelId))
    // process the job
    val result: Operation.Result[(payload.api.TypeSchema, Boolean)] = if (canRedo) {
      jobResult.get
    } else if (canExecute) {
      /*  Main.execNGet {
        val dialog = new TypeEditor(Window.currentShell(), schema, schemaList.toList, isActive)
        Window.currentShell.withValue(Some(dialog.getShell)) {
          dialog.open() == org.eclipse.jface.window.Window.OK
        } match {
          case true => Operation.Result.OK({
            val schema = dialog.getModifiedSchema
            if (dialog.getModifiedSchemaActiveFlag == isActive && schemaList.contains(schema) && TypeSchema.compareDeep(this.schema, schema))
              None // nothing changes, and schema is already exists
            else
              Some(schema, dialog.getModifiedSchemaActiveFlag)
          })
          case false => Operation.Result.Cancel()
        }
      }*/
      Operation.Result.Error("")
    } else
      Operation.Result.Error(s"Unable to process $this: redo and execute are prohibited")
    // update the job state
    result match {
      case r @ Operation.Result.OK(_, _) =>
        jobResult = Some(r)
        allowExecute = false
        allowRedo = false
        allowUndo = true
      case Operation.Result.Cancel(_) =>
      case _ =>
        allowExecute = false
        allowRedo = false
        allowUndo = false
    }
    // update the job state
    result
  }
  protected def undo(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[(payload.api.TypeSchema, Boolean)] = {
    if (canUndo) {
      allowExecute = false
      allowRedo = true
      Operation.Result.OK(Some(schema, isActive))
    } else
      Operation.Result.Error(s"Unable to process $this: redo and execute are prohibited")
  }
}