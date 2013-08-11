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

package org.digimead.tabuddy.desktop.logic.operation

import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.Model.model2implementation
import org.eclipse.core.runtime.IAdaptable
import org.eclipse.core.runtime.IProgressMonitor
import org.digimead.tabuddy.desktop.definition.Operation
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.tabuddy.desktop.logic.payload.Payload

class OperationModelAcquire private (oldModelID: Option[Symbol], newModelID: Symbol)
  extends OperationModelAcquire.Abstract(oldModelID, newModelID) {
  @volatile protected var allowExecute = true
  @volatile protected var allowRedo = false
  @volatile protected var allowUndo = false
  @volatile protected var before: Option[Model.Interface[_ <: Model.Stash]] = None
  @volatile protected var after: Option[Model.Interface[_ <: Model.Stash]] = None

  override def canExecute() = allowExecute
  override def canRedo() = allowRedo
  override def canUndo() = allowUndo

  protected def execute(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[Model.Interface[_ <: Model.Stash]] = redo(monitor, info)
  protected def redo(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[Model.Interface[_ <: Model.Stash]] = {
    // process the job
    val result: Operation.Result[Model.Interface[_ <: Model.Stash]] = if (canRedo) {
      assert(Model.eId == newModelID, "An unexpected model %s, expect %s".format(Model.eId, newModelID))
      after match {
        case Some(redoModel) =>
          Model.reset(redoModel)
          Operation.Result.OK(after)
        case None =>
          Operation.Result.Error(s"Lost redo model for $this")
      }
    } else if (canExecute) {
      oldModelID.foreach(id => assert(Model.eId == id, "An unexpected model %s, expect %s".format(Model.eId, id)))
      before = Some(Model.inner)
      //after = Payload.acquireModel(newModelID)
      if (after.nonEmpty)
        Operation.Result.OK(after)
      else
        Operation.Result.Error(s"Unable to aquire model")
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
  protected def undo(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[Model.Interface[_ <: Model.Stash]] = {
    assert(canUndo, "undo is prohibited")
    allowExecute = false
    allowRedo = true
    allowUndo = false
    before match {
      case Some(undoModel) =>
        Model.reset(undoModel)
        Operation.Result.OK(before)
      case None =>
        Operation.Result.Error(s"Lost undo model for $this")
    }
  }
}

object OperationModelAcquire extends Loggable {
  @log
  def apply(oldModelId: Option[Symbol], newModelId: Symbol): Option[OperationModelAcquire] = {
    val modelId = Model.eId
    DI.jobFactory.asInstanceOf[Option[(Option[Symbol], Symbol) => OperationModelAcquire]] match {
      case Some(factory) =>
        Option(factory(oldModelId, newModelId))
      case None =>
        log.error("OperationModelAcquire implementation is not defined.")
        None
    }
  }

  abstract class Abstract(val oldModelID: Option[Symbol], val newModelID: Symbol)
    extends Operation[Model.Interface[_ <: Model.Stash]](s"Acquire model ${newModelID}.") with api.OperationModelAcquire
  /**
   * Dependency injection routines.
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    // Element[_ <: Stash] == Element.Generic, avoid 'erroneous or inaccessible type' error
    lazy val jobFactory = injectOptional[(Option[Symbol], Symbol) => api.OperationModelAcquire]
  }
}