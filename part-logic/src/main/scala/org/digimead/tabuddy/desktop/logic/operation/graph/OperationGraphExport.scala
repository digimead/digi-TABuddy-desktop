/**
 * This file is part of the TA Buddy project.
 * Copyright (c) 2014 Alexey Aksenov ezh@ezh.msk.ru
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

import java.io.{ File, IOException }
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.definition.Operation
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.logic.Logic
import org.digimead.tabuddy.desktop.logic.payload.maker.GraphMarker
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.graph.Graph
import org.digimead.tabuddy.model.serialization.Serialization
import org.eclipse.core.runtime.{ IAdaptable, IProgressMonitor }

/** 'Export graph' operation. */
class OperationGraphExport extends api.OperationGraphExport with Loggable {
  /**
   * Export graph.
   *
   * @param graph graph to export
   * @param location target directory
   * @param interactive show graph export wizard
   */
  def apply(graph: Graph[_ <: Model.Like], location: Option[File], overwrite: Boolean, interactive: Boolean) = GraphMarker(graph).safeUpdate { _ ⇒
    log.info(location match {
      case Some(location) ⇒ s"Export ${graph} to ${location}."
      case None ⇒ s"Export ${graph}."
    })
    if (interactive && !App.isUIAvailable)
      throw new IllegalArgumentException("Unable to import interactively without UI.")
    if (!interactive && location.isEmpty)
      throw new IllegalArgumentException("Unable to non interactively import graph without location.")
    if (!Logic.container.isOpen())
      throw new IllegalStateException("Workspace is not available.")
    val marker = GraphMarker(graph)
    if (!marker.markerIsValid)
      throw new IllegalStateException(marker + " is not valid.")
    if (!marker.graphIsOpen())
      throw new IllegalStateException(s"$graph is closed.")
    val destination = new File(location.get, marker.graphModelId.name)
    if (destination.exists())
      if (overwrite) {
        log.info("Clean " + destination)
        App.processRecursive(destination)(_.delete())
      } else
        throw new IOException("Destination directory is already exists.")
    val currentURI = Serialization.normalizeURI(marker.graphPath.toURI())
    val copy = graph.copy() { g ⇒
      g.storages = g.storages.filterNot(_ == currentURI)
    }
    Serialization.freeze(copy,
      storages = Some(Serialization.ExplicitStorages(Seq(destination.toURI()), Serialization.ExplicitStorages.ModeAppend)))
  }
  /**
   * Create 'Export graph' operation.
   *
   * @param graph graph to export
   * @param location target directory
   * @param interactive show graph export wizard
   * @return 'Export graph' operation
   */
  def operation(graph: Graph[_ <: Model.Like], location: Option[File], overwrite: Boolean, interactive: Boolean) =
    new Implemetation(graph, location, overwrite, interactive)

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

  class Implemetation(graph: Graph[_ <: Model.Like], location: Option[File], overwrite: Boolean, interactive: Boolean)
    extends OperationGraphExport.Abstract(graph, location, overwrite, interactive) with Loggable {
    @volatile protected var allowExecute = true

    override def canExecute() = allowExecute
    override def canRedo() = false
    override def canUndo() = false

    protected def execute(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[Unit] = {
      require(canExecute, "Execution is disabled.")
      try {
        val result = Option(OperationGraphExport.this(graph, location, overwrite, interactive))
        allowExecute = false
        Operation.Result.OK(result)
      } catch {
        case e: IOException if e.getMessage() == "Destination directory is already exists." ⇒
          Operation.Result.Error(s"Unable to export $graph: " + e.getMessage(), null, false)
        case e: Throwable ⇒
          Operation.Result.Error(s"Unable to export $graph.", e)
      }
    }
    protected def redo(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[Unit] =
      throw new UnsupportedOperationException
    protected def undo(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[Unit] =
      throw new UnsupportedOperationException
  }
}

object OperationGraphExport extends Loggable {
  /** Stable identifier with OperationGraphExport DI */
  lazy val operation = DI.operation.asInstanceOf[OperationGraphExport]

  /**
   * Build a new 'Export graph' operation.
   *
   * @param graph graph to export
   * @param location target directory
   * @param interactive show graph export wizard
   * @return 'Export graph' operation
   */
  @log
  def apply(graph: Graph[_ <: Model.Like], location: Option[File], overwrite: Boolean, interactive: Boolean): Option[Abstract] =
    Some(operation.operation(graph, location, overwrite, interactive))

  /** Bridge between abstract api.Operation[Unit] and concrete Operation[Unit] */
  abstract class Abstract(val graph: Graph[_ <: Model.Like], val location: Option[File], val overwrite: Boolean,
    val interactive: Boolean) extends Operation[Unit](location match {
    case Some(location) ⇒ s"Export ${graph} to ${location}."
    case None ⇒ s"Export ${graph}."
  }) {
    this: Loggable ⇒
  }
  /**
   * Dependency injection routines.
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    lazy val operation = injectOptional[api.OperationGraphExport] getOrElse new OperationGraphExport
  }
}
