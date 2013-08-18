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

package org.digimead.tabuddy.desktop.modeldef.operation

import java.util.concurrent.Exchanger

import scala.reflect.runtime.universe

import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.definition.Operation
import org.digimead.tabuddy.desktop.logic.payload
import org.digimead.tabuddy.desktop.modeldef.dialog.eltemed.ElementTemplateEditor
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.App.app2implementation
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.Model.model2implementation
import org.eclipse.core.runtime.IAdaptable
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.swt.widgets.Shell

/**
 * Modify an elementTemplate.
 */
class OperationModifyElementTemplate(
  /** The initial element template */
  template: payload.api.ElementTemplate,
  /** The list of element template */
  templateList: Set[payload.api.ElementTemplate],
  modelId: Symbol)
  extends org.digimead.tabuddy.desktop.logic.operation.OperationModifyElementTemplate.Abstract(template, templateList, modelId) with Loggable {
  @volatile protected var allowExecute = true
  @volatile protected var allowRedo = false
  @volatile protected var allowUndo = false

  override def canExecute() = allowExecute
  override def canRedo() = allowRedo
  override def canUndo() = allowUndo

  protected def dialog(): Operation.Result[payload.api.ElementTemplate] = {
    val exchanger = new Exchanger[Operation.Result[payload.api.ElementTemplate]]()
    App.assertUIThread(false)
    App.exec {
      App.getActiveShell match {
        case Some(shell) =>
          val dialog = new ElementTemplateEditor(shell, template, templateList)
          dialog.openOrFocus {
            case result if result == org.eclipse.jface.window.Window.OK =>
              exchanger.exchange(Operation.Result.OK({
                val after = dialog.getModifiedTemplate
                // compare templates and deep compare template properties
                if (templateList.exists(template => payload.ElementTemplate.compareDeep(template, after)))
                  None // nothing changes, and template is already exists
                else
                  Some(after)
              }))
            case result =>
              exchanger.exchange(Operation.Result.Cancel[payload.api.ElementTemplate]())
          }
        case None =>
          exchanger.exchange(Operation.Result.Error("Unable to find active shell."))
      }
    }
    exchanger.exchange(null)
  }
  protected def execute(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[payload.api.ElementTemplate] = redo(monitor, info)
  protected def redo(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[payload.api.ElementTemplate] = {
    assert(Model.eId == modelId, "An unexpected model %s, expect %s".format(Model.eId, modelId))
    // process the job
    val result: Operation.Result[payload.api.ElementTemplate] = if (canRedo) {
      // TODO replay history, modify elementTemplate: before -> after
      Operation.Result.Error("Unimplemented")
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
  protected def undo(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[payload.api.ElementTemplate] = {
    // TODO revert history, modify elementTemplate: after -> before
    Operation.Result.Error("Unimplemented")
  }
}
