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

package org.digimead.tabuddy.desktop.job

import org.digimead.tabuddy.desktop.Main
import org.digimead.tabuddy.desktop.payload.TypeSchema
import org.digimead.tabuddy.desktop.ui.Window
import org.digimead.tabuddy.desktop.ui.dialog.typeed.TypeEditor
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.Model.model2implementation
import org.eclipse.core.runtime.IAdaptable
import org.eclipse.core.runtime.IProgressMonitor

class JobShowTypeEditor private (val schema: TypeSchema.Interface, schemaList: List[TypeSchema.Interface], isActive: Boolean, val modelID: Symbol)
  extends Job[(TypeSchema.Interface, Boolean)]("Edit %s for model %s".format(schema, modelID)) {
  @volatile protected var allowExecute = true
  @volatile protected var allowRedo = false
  @volatile protected var allowUndo = false
  @volatile protected var jobResult: Option[Job.Result[(TypeSchema.Interface, Boolean)]] = None

  override def canExecute() = allowExecute
  override def canRedo() = allowRedo
  override def canUndo() = allowUndo

  protected def redo(monitor: IProgressMonitor, info: IAdaptable): Job.Result[(TypeSchema.Interface, Boolean)] = {
    assert(Model.eId == modelID, "unexpected model %s, expect %s".format(Model.eId, modelID))
    // process the job
    val result: Job.Result[(TypeSchema.Interface, Boolean)] = if (canRedo) {
      jobResult.get
    } else if (canExecute) {
      Main.execNGet {
        val dialog = new TypeEditor(Window.currentShell(), schema, schemaList, isActive)
        Window.currentShell.withValue(Some(dialog.getShell)) {
          dialog.open() == org.eclipse.jface.window.Window.OK
        } match {
          case true => Job.Result.OK({
            val schema = dialog.getModifiedSchema
            if (dialog.getModifiedSchemaActiveFlag == isActive && schemaList.contains(schema) && TypeSchema.compareDeep(this.schema, schema))
              None // nothing changes, and schema is already exists
            else
              Some(schema, dialog.getModifiedSchemaActiveFlag)
          })
          case false => Job.Result.Cancel()
        }
      }
    } else
      Job.Result.Error(s"Unable to process $this: redo and execute are prohibited")
    // update the job state
    result match {
      case r @ Job.Result.OK(_, _) =>
        jobResult = Some(r)
        allowExecute = false
        allowRedo = false
        allowUndo = true
      case Job.Result.Cancel(_) =>
      case _ =>
        allowExecute = false
        allowRedo = false
        allowUndo = false
    }
    // update the job state
    result
  }
  protected def undo(monitor: IProgressMonitor, info: IAdaptable): Job.Result[(TypeSchema.Interface, Boolean)] = {
    if (canUndo) {
      allowExecute = false
      allowRedo = true
      Job.Result.OK(Some(schema, isActive))
    } else
      Job.Result.Error(s"Unable to process $this: redo and execute are prohibited")
  }
}

object JobShowTypeEditor {
  def apply(schema: TypeSchema.Interface, schemaList: List[TypeSchema.Interface], isSchemaActive: Boolean): Option[JobBuilder[JobShowTypeEditor]] = {
    val modelID = Model.eId
    Some(new JobBuilder(JobShowTypeEditor, () => new JobShowTypeEditor(schema, schemaList, isSchemaActive, modelID)))
  }
}
