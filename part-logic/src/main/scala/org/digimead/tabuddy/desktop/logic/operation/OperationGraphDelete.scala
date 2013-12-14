/**
 * This file is part of the TA Buddy project.
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

package org.digimead.tabuddy.desktop.logic.operation

import java.util.UUID
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.definition.Operation
import org.digimead.tabuddy.desktop.logic.payload.maker.GraphMarker
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.graph.Graph
import org.eclipse.core.runtime.{ IAdaptable, IProgressMonitor }

/** 'Delete graph' operation. */
class OperationGraphDelete extends api.OperationGraphDelete with Loggable {
  protected val operationLock = new Object()
  /**
   * Delete graph.
   *
   * @param graph graph to delete
   * @param askBefore askUser before delete
   * @return deleted graph read only marker
   */
  def apply(graph: Graph[_ <: Model.Like], askBefore: Boolean): UUID = GraphMarker(graph).lockUpdate { state ⇒
    log.info(s"Delete graph $graph.")
    GraphMarker(graph).graphClose()
    val roMarker = GraphMarker.deleteFromWorkspace(GraphMarker(graph))
    log.info(s"Graph $graph is deleted.")
    roMarker.uuid
  }
  /**
   * Create 'Delete graph' operation.
   *
   * @param graph graph to delete
   * @param askBefore askUser before delete
   * @return 'Delete graph' operation
   */
  def operation(graph: Graph[_ <: Model.Like], askBefore: Boolean) =
    new Implementation(graph, askBefore)

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

  class Implementation(graph: Graph[_ <: Model.Like], askBefore: Boolean)
    extends OperationGraphDelete.Abstract(graph, askBefore) with Loggable {
    @volatile protected var allowExecute = true

    override def canExecute() = allowExecute
    override def canRedo() = false
    override def canUndo() = false

    protected def execute(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[UUID] = {
      require(canExecute, "Execution is disabled.")
      try {
        val result = Option(OperationGraphDelete.this(graph, askBefore))
        allowExecute = false
        Operation.Result.OK(result)
      } catch {
        case e: Throwable ⇒
          Operation.Result.Error(s"Unable to delete graph $graph.")
      }
    }
    protected def redo(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[UUID] =
      throw new UnsupportedOperationException
    protected def undo(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[UUID] =
      throw new UnsupportedOperationException
  }
}

object OperationGraphDelete extends Loggable {
  /** Stable identifier with OperationGraphDelete DI */
  lazy val operation = DI.operation.asInstanceOf[OperationGraphDelete]

  /**
   * Build a new 'Delete graph' operation.
   *
   * @param graph graph to delete
   * @param askBefore askUser before delete
   * @return 'Delete graph' operation
   */
  @log
  def apply(graph: Graph[_ <: Model.Like], askBefore: Boolean): Option[Abstract] =
    Some(operation.operation(graph, askBefore).asInstanceOf[Abstract])

  /** Bridge between abstract api.Operation[UUID] and concrete Operation[UUID] */
  abstract class Abstract(val graph: Graph[_ <: Model.Like], val askBefore: Boolean)
    extends Operation[UUID](s"Delete graph $graph.") {
    this: Loggable ⇒
  }
  /**
   * Dependency injection routines.
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    lazy val operation = injectOptional[api.OperationGraphDelete] getOrElse new OperationGraphDelete
  }
}
