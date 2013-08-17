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

import scala.reflect.runtime.universe

import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.definition.Operation
import org.digimead.tabuddy.desktop.logic
import org.digimead.tabuddy.desktop.logic.payload.Payload
import org.digimead.tabuddy.desktop.logic.payload.Payload.payload2implementation
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.Model.model2implementation
import org.eclipse.core.runtime.IAdaptable
import org.eclipse.core.runtime.IProgressMonitor

/** 'Delete model' operation. */
class OperationModelDelete extends api.OperationModelDelete with Loggable {
  protected val operationLock = new Object()
  /**
   * Delete model with modelId.
   *
   * @param modelId model to delete
   * @param askBefore askUser before delete
   * @return deleted model marker
   */
  def apply(modelId: Symbol, askBefore: Boolean): logic.api.ModelMarker = ModelLock.synchronized {
    log.info(s"Delete model ${modelId}.")
    if (modelId == Payload.defaultModel.eId) {
      throw new IllegalArgumentException("Unable to delete the default model.")
    } else {
      val marker = getModelMarker(modelId)
      // close model if needed
      log.___gaze("CLOSE")
      Payload.deleteModel(marker)
      log.info(s"Model ${modelId} is deleted.")
      marker
    }
  }
  /**
   * Create 'Delete model' operation.
   *
   * @param modelId model to delete
   * @param askBefore askUser before delete
   * @return 'Delete model' operation
   */
  def operation(modelId: Symbol, askBefore: Boolean): Operation[logic.api.ModelMarker] =
    new Implementation(modelId, askBefore)

  /**
   * Get operation model marker for this operation.
   *
   * This method isn't collect marker of default model.
   */
  protected def getModelMarker(modelId: Symbol): logic.api.ModelMarker = {
    if (modelId == Payload.defaultModel.eId)
      throw new IllegalAccessException("Unable to delete the default model.")
    Payload.listModels.find(marker => marker.isValid && marker.id == modelId) match {
      case Some(marker) =>
        marker
      case None =>
        throw new IllegalArgumentException(s"Unable to find marker for the model: ${modelId}.")
    }
  }

  class Implementation(modelId: Symbol, askBefore: Boolean)
    extends OperationModelDelete.Abstract(modelId, askBefore) with Loggable {
    @volatile protected var allowExecute = true

    override def canExecute() = allowExecute
    override def canRedo() = false
    override def canUndo() = false

    protected def execute(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[logic.api.ModelMarker] = {
      Operation.Result.OK(Option(OperationModelDelete.this(modelId, askBefore)))
    }
    protected def redo(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[logic.api.ModelMarker] =
      throw new UnsupportedOperationException
    protected def undo(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[logic.api.ModelMarker] =
      throw new UnsupportedOperationException
  }
}

object OperationModelDelete extends Loggable {
  /** Stable identifier with OperationModelDelete DI */
  lazy val operation = DI.operation

  /** Build a new 'Delete model' operation */
  @log
  def apply(modelId: Symbol, askBefore: Boolean): Option[Abstract] =
    Some(operation.operation(modelId, askBefore).asInstanceOf[Abstract])

  /** Bridge between abstract api.Operation[logic.api.ModelMarker] and concrete Operation[logic.api.ModelMarker] */
  abstract class Abstract(val modelId: Symbol, val askBefore: Boolean) extends Operation[logic.api.ModelMarker](s"Delete model ${modelId}.") {
    this: Loggable =>
  }
  /**
   * Dependency injection routines.
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    lazy val operation = injectOptional[api.OperationModelDelete] getOrElse new OperationModelDelete
  }
}
