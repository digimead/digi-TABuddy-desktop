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

/** 'Open model' operation. */
class OperationModelOpen extends api.OperationModelOpen with Loggable {
  /**
   * Open model with newModelId.
   *
   * @param oldModelId old model if any
   * @param newModelId new model
   * @param force close previous model without saving
   * @return previous model marker, current model marker
   */
  def apply(oldModelId: Option[Symbol], newModelId: Symbol, force: Boolean): (Option[logic.api.ModelMarker], logic.api.ModelMarker) = ModelLock.synchronized {
    log.info(s"Open model ${newModelId}. Previous model is ${oldModelId}.")
    val (before, after) = getModelMarkers(oldModelId, newModelId)
    // close/save previous model
    before.foreach { marker =>
      log.___gaze("CLOSE/SAVE")
    }
    // open new model
    if (Model.eId == Payload.defaultModel.eId) {
      // There is the default model. Load different model.
      Payload.acquireModel(after)
      log.info(s"Model ${newModelId} is opened.")
      (before, after)
    } else
      throw new IllegalStateException(s"Unable to open model ${newModelId}. Another model ${Model.eId} is loaded.")
  }
  /**
   * Create 'Open model' operation.
   *
   * @param oldModelId old model if any
   * @param newModelId new model
   * @param force close previous model without saving
   * @return 'Open model' operation
   */
  def operation(oldModelId: Option[Symbol], newModelId: Symbol, force: Boolean) =
    new Implemetation(oldModelId, newModelId, force: Boolean)

  /**
   * Checks that this class can be subclassed.
   * <p>
   * The API class is intended to be subclassed only at specific,
   * controlled point. This method enforces this rule
   * unless it is overridden.
   * </p><p>
   * <em>IMPORTANT:</em> By providing an implementation of this
   * method that allows a subclass of a class which does not
   * normally allow subclassing to be created, the implementer
   * agrees to be fully responsible for the fact that any such
   * subclass will likely fail.
   * </p>
   */
  override protected def checkSubclass() {}
  /**
   * Get model markers for this operation.
   *
   * This method isn't collect marker of default model.
   *
   * @return tuple[Option(old model marker), new model marker]
   */
  protected def getModelMarkers(oldModelId: Option[Symbol],
    newModelId: Symbol): (Option[logic.api.ModelMarker], logic.api.ModelMarker) = {
    oldModelId match {
      case Some(oldModelId) =>
        if (Model.eId != oldModelId)
          throw new IllegalArgumentException("An unexpected model %s, expect %s".format(Model.eId, oldModelId))
      case None =>
        if (Model.eId != Payload.defaultModel.eId)
          throw new IllegalAccessException("An unexpected model %s, expect %s".format(Model.eId, Payload.defaultModel.eId))
    }
    if (newModelId == Payload.defaultModel.eId)
      throw new IllegalAccessException("Unable to open default model.")
    // Don't collect marker for default model.
    val before = if (oldModelId != Some(Payload.defaultModel.eId))
      oldModelId.flatMap(id => Payload.listModels.find(marker => marker.isValid && marker.id == id))
    else
      None
    val after = Payload.listModels.find(marker => marker.isValid && marker.id == newModelId)
    after match {
      case Some(after) =>
        (before, after)
      case None =>
        throw new IllegalArgumentException(s"Unable to find marker for new model: ${newModelId}.")
    }
  }

  class Implemetation(oldModelId: Option[Symbol], newModelId: Symbol, force: Boolean)
    extends OperationModelOpen.Abstract(oldModelId, newModelId, force) with Loggable {
    @volatile protected var allowExecute = true
    @volatile protected var allowRedo = false
    @volatile protected var allowUndo = false
    /** Before operation executed. */
    @volatile protected var before: Option[logic.api.ModelMarker] = None
    /** After operation executed. */
    @volatile protected var after: Option[logic.api.ModelMarker] = None

    override def canExecute() = allowExecute
    override def canRedo() = allowRedo
    override def canUndo() = allowUndo

    protected def execute(monitor: IProgressMonitor,
      info: IAdaptable): Operation.Result[logic.api.ModelMarker] = ModelLock.synchronized {
      assert(canExecute, "Unable to execute.")
      try {
        val (before, after) = OperationModelOpen.this(oldModelId, newModelId, force)
        this.before = before
        this.after = Some(after)
        allowExecute = false
        allowRedo = false
        allowUndo = true
        Operation.Result.OK(this.after)
      } catch {
        case e: Throwable =>
          Operation.Result.Error(s"Unable to open model ${newModelId}.", e)
      }
    }
    protected def redo(monitor: IProgressMonitor,
      info: IAdaptable): Operation.Result[logic.api.ModelMarker] = ModelLock.synchronized {
      assert(canRedo, "Unable to redo.")
      try {
        val (before, after) = OperationModelOpen.this(oldModelId, newModelId, force)
        allowExecute = false
        allowRedo = false
        allowUndo = true
        Operation.Result.OK(Some(after))
      } catch {
        case e: Throwable =>
          Operation.Result.Error(s"Unable to open model ${newModelId}.")
      }
    }
    protected def undo(monitor: IProgressMonitor,
      info: IAdaptable): Operation.Result[logic.api.ModelMarker] = ModelLock.synchronized {
      assert(canExecute, "Unable to undo.")
      this.before match {
        case Some(from) =>
          val newModelId = from.id
          val oldModelId = this.after.map(_.id)
          /*try {
          val (before, after) = open(oldModelId, newModelId)
          allowExecute = false
          allowRedo = true
          allowUndo = false
          Operation.Result.OK(Some(after))
        } catch {
          case e: Throwable =>
            Operation.Result.Error(s"Unable to open model ${newModelId}")
        }*/
          // TODO
          throw new UnsupportedOperationException
        case None =>
          Operation.Result.Error(s"Unable to find the previous model identifier.")
      }
    }
  }
}

object OperationModelOpen extends Loggable {
  /** Stable identifier with OperationModelOpen DI */
  lazy val operation = DI.operation.asInstanceOf[OperationModelOpen]

  /** Build a new 'Open model' operation */
  @log
  def apply(oldModelId: Option[Symbol], newModelId: Symbol, force: Boolean): Option[Abstract] =
    Some(operation.operation(oldModelId, newModelId, force))

  /** Bridge between abstract api.Operation[logic.api.ModelMarker] and concrete Operation[logic.api.ModelMarker] */
  abstract class Abstract(val oldModelId: Option[Symbol], val newModelId: Symbol, force: Boolean)
    extends Operation[logic.api.ModelMarker](s"Open model ${newModelId}.") {
    this: Loggable =>
  }
  /**
   * Dependency injection routines.
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    lazy val operation = injectOptional[api.OperationModelOpen] getOrElse new OperationModelOpen
  }
}
