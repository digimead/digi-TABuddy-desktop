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

import org.eclipse.core.runtime.IAdaptable
import org.eclipse.core.runtime.IProgressMonitor
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.tabuddy.desktop.logic.payload.Payload
import org.digimead.tabuddy.desktop.definition.Operation

class OperationModelDelete private (modelId: Symbol)
  extends OperationModelDelete.Abstract(modelId) {
  @volatile protected var allowExecute = true

  override def canExecute() = allowExecute
  override def canRedo() = false
  override def canUndo() = false

  protected def run(monitor: IProgressMonitor): Operation.Result[Unit] = {
    Operation.Result.OK()
  }

  protected def execute(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[Unit] = redo(monitor, info)

  protected def redo(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[Unit] = {
    // TODO delete
    Operation.Result.Error("Unimplemented")
  }
  protected def undo(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[Unit] =
    throw new UnsupportedOperationException
}

object OperationModelDelete extends Loggable {
  @log
  def apply(modelId: Symbol): Option[OperationModelDelete] = {
    DI.jobFactory.asInstanceOf[Option[(Symbol) => OperationModelDelete]] match {
      case Some(factory) =>
        if (modelId != Payload.defaultModel.eId && modelId != Symbol(""))
          Option(factory(modelId))
        else {
          log.warn("Unable to delete model with the default name")
          None
        }
      case None =>
        log.error("OperationModelDelete implementation is not defined.")
        None
    }
  }

  abstract class Abstract(val modelId: Symbol)
    extends Operation[Unit](s"Delete model ${modelId}.") with api.OperationModelDelete
  /**
   * Dependency injection routines.
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    // Element[_ <: Stash] == Element.Generic, avoid 'erroneous or inaccessible type' error
    lazy val jobFactory = injectOptional[(Symbol) => api.OperationModelDelete]
  }
}