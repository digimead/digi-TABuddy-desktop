/**
 * This file is part of the TA Buddy project.
 * Copyright (c) 2013-2014 Alexey Aksenov ezh@ezh.msk.ru
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

package org.digimead.tabuddy.desktop.logic.operation.graph

import java.util.UUID
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.XDependencyInjection
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.definition.Operation
import org.digimead.tabuddy.desktop.logic.Logic
import org.digimead.tabuddy.desktop.logic.operation.graph.api.XOperationGraphOpen
import org.digimead.tabuddy.desktop.logic.payload.marker.GraphMarker
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.element.Element
import org.digimead.tabuddy.model.graph.Graph
import org.eclipse.core.runtime.{ IAdaptable, IProgressMonitor }

/** 'Open graph' operation. */
class OperationGraphOpen extends XOperationGraphOpen with XLoggable {
  /**
   * Open graph for graph marker.
   *
   * @param markerId marker Id with graph to open
   */
  def apply(markerId: UUID, modified: Option[Element.Timestamp] = None): Graph[_ <: Model.Like] = {
    val marker = GraphMarker(markerId)
    if (!marker.markerIsValid)
      throw new IllegalStateException(marker + " is not valid.")
    log.info(s"Open graph for marker $marker.")
    if (!Logic.container.isOpen())
      throw new IllegalStateException("Workspace is not available.")
    val graph = marker.safeUpdate { state ⇒
      if (marker.graphIsOpen())
        throw new IllegalStateException("Graph is already opened.")
      marker.graphAcquire(modified)
      state.graph
    }
    graph
  }
  /**
   * Create 'Open graph' operation.
   *
   * @param markerId marker Id with graph to open
   * @return 'Open graph' operation
   */
  def operation(markerId: UUID, modified: Option[Element.Timestamp] = None) =
    new Implemetation(markerId, modified)

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

  class Implemetation(markerId: UUID, modified: Option[Element.Timestamp])
    extends OperationGraphOpen.Abstract(markerId, modified) with XLoggable {
    @volatile protected var allowExecute = true

    override def canExecute() = allowExecute
    override def canRedo() = false
    override def canUndo() = false

    protected def execute(monitor: IProgressMonitor,
      info: IAdaptable): Operation.Result[Graph[_ <: Model.Like]] = {
      require(canExecute, "Execution is disabled.")
      val marker = GraphMarker(markerId)
      try {
        val result = Option[Graph[_ <: Model.Like]](OperationGraphOpen.this(markerId, modified))
        allowExecute = false
        Operation.Result.OK(result)
      } catch {
        case e: Throwable ⇒
          Operation.Result.Error(s"Unable to open graph for $marker: " + e.getMessage(), e)
      }
    }
    protected def redo(monitor: IProgressMonitor,
      info: IAdaptable): Operation.Result[Graph[_ <: Model.Like]] =
      throw new UnsupportedOperationException
    protected def undo(monitor: IProgressMonitor,
      info: IAdaptable): Operation.Result[Graph[_ <: Model.Like]] =
      throw new UnsupportedOperationException
  }
}

object OperationGraphOpen extends XLoggable {
  /** Stable identifier with OperationGraphOpen DI */
  lazy val operation = DI.operation.asInstanceOf[OperationGraphOpen]

  /**
   * Build a new 'Open graph' operation.
   *
   * @param markerId marker Id with graph to open
   * @return 'Open graph' operation
   */
  @log
  def apply(markerId: UUID): Option[Abstract] =
    Some(operation.operation(markerId))

  /** Bridge between abstract XOperation[Graph[_ <: Model.Like]] and concrete Operation[Graph[_ <: Model.Like]] */
  abstract class Abstract(val markerId: UUID, val modified: Option[Element.Timestamp])
    extends Operation[Graph[_ <: Model.Like]](modified match {
      case Some(modified) ⇒ s"Open graph for marker with Id $markerId and modification ${modified}."
      case None ⇒ s"Open graph for marker with Id $markerId."
    }) {
    this: XLoggable ⇒
  }
  /**
   * Dependency injection routines.
   */
  private object DI extends XDependencyInjection.PersistentInjectable {
    lazy val operation = injectOptional[XOperationGraphOpen] getOrElse new OperationGraphOpen
  }
}
