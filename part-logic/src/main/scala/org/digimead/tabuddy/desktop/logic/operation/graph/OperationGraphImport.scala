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

import java.io.File
import java.net.URI
import java.util.UUID
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.definition.Operation
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.logic.Logic
import org.digimead.tabuddy.desktop.logic.payload.maker.GraphMarker
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.element.Element
import org.digimead.tabuddy.model.graph.Graph
import org.digimead.tabuddy.model.serialization.Serialization
import org.eclipse.core.runtime.{ IAdaptable, IProgressMonitor }

/** 'Import graph' operation. */
class OperationGraphImport extends api.OperationGraphImport with Loggable {
  /**
   * Import graph.
   *
   * @param origin graph origin
   * @param location source with imported graph
   * @param interactive show graph import wizard
   * @return imported graph
   */
  def apply(origin: Option[Symbol], location: Option[URI], interactive: Boolean): Graph[_ <: Model.Like] = {
    log.info(location match {
      case Some(location) ⇒ s"Import graph from ${location}."
      case None ⇒ "Import graph."
    })
    if (interactive && !App.isUIAvailable)
      throw new IllegalArgumentException("Unable to import interactively without UI.")
    if (!Logic.container.isOpen())
      throw new IllegalStateException("Workspace is not available.")

    val (graphOrigin, graphLocation) = if (interactive) {
      /*      UI.getActiveShell match {
        case Some(shell) ⇒
          App.execNGet {
            Wizards.open("org.digimead.tabuddy.desktop.graph.editor.wizard.ModelCreationWizard", shell, Some(name, location)) match {
              case marker: GraphMarker ⇒
                if (!marker.markerIsValid)
                  throw new IllegalStateException(marker + " is not valid.")
                marker
              case other if other == org.eclipse.jface.window.Window.CANCEL ⇒
                throw new CancellationException("Unable to create new graph. Operation canceled.")
              case other ⇒
                throw new IllegalStateException(s"Unable to create new graph. Result ${other}.")
            }
          }
        case None ⇒
          throw new IllegalStateException("Unable to create new graph dialog without parent shell.")
      }*/
      // TODO
      throw new UnsupportedOperationException("TODO")
    } else {
      (origin getOrElse { throw new IllegalArgumentException("Unable to non interactively import graph without origin.") },
        location getOrElse { throw new IllegalArgumentException("Unable to non interactively import graph without location.") })
    }
    Option[Graph[_ <: Model.Like]](Serialization.acquire(graphOrigin, graphLocation)) match {
      case Some(graph) ⇒
        val localGraphPath = if (graphLocation.getScheme() != new File(".").toURI().getScheme()) {
          // this is remote graph
          // create local copy
          val destination = new File(Logic.graphContainer, graph.model.eId.name)
          Serialization.freeze(graph,
            storages = Some(Serialization.ExplicitStorages(Seq(destination.toURI()), Serialization.ExplicitStorages.ModeAppend)))
          destination
        } else
          new File(graphLocation)
        val uuid = if (GraphMarker.list().contains(graph.node.unique))
          UUID.randomUUID()
        else
          graph.node.unique
        val newMarker = GraphMarker.createInTheWorkspace(uuid, localGraphPath, Element.timestamp(), graphOrigin)
        newMarker.lockUpdate(_.lockWrite(_.graphObject = Some(graph.copy() { g ⇒
          g.withData { data ⇒
            data(GraphMarker) = newMarker
          }
        })))
        newMarker.graphAcquire()
      case None ⇒
        throw new IllegalStateException(s"Unable to import graph with origin $graphOrigin from " + graphLocation)
    }
  }
  /**
   * Create 'Import graph' operation.
   *
   * @param origin graph origin
   * @param location source with imported graph
   * @param interactive show graph import wizard
   * @return 'Import graph' operation
   */
  def operation(origin: Option[Symbol], location: Option[URI], interactive: Boolean) =
    new Implemetation(origin, location, interactive)

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

  class Implemetation(origin: Option[Symbol], location: Option[URI], interactive: Boolean)
    extends OperationGraphImport.Abstract(origin, location, interactive) with Loggable {
    @volatile protected var allowExecute = true

    override def canExecute() = allowExecute
    override def canRedo() = false
    override def canUndo() = false

    protected def execute(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[Graph[_ <: Model.Like]] = {
      require(canExecute, "Execution is disabled.")
      try {
        val result = Option[Graph[_ <: Model.Like]](OperationGraphImport.this(origin, location, interactive))
        allowExecute = false
        Operation.Result.OK(result)
      } catch {
        case e: Throwable ⇒
          Operation.Result.Error(s"Unable to import graph.", e)
      }
    }
    protected def redo(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[Graph[_ <: Model.Like]] =
      throw new UnsupportedOperationException
    protected def undo(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[Graph[_ <: Model.Like]] =
      throw new UnsupportedOperationException
  }
}

object OperationGraphImport extends Loggable {
  /** Stable identifier with OperationGraphImport DI */
  lazy val operation = DI.operation.asInstanceOf[OperationGraphImport]

  /**
   * Build a new 'Import graph' operation.
   *
   * @param origin graph origin
   * @param location source with imported graph
   * @param interactive show graph import wizard
   * @return 'Import graph' operation
   */
  @log
  def apply(origin: Option[Symbol], location: Option[URI], interactive: Boolean): Option[Abstract] =
    Some(operation.operation(origin, location, interactive))

  /** Bridge between abstract api.Operation[Graph[_ <: Model.Like]] and concrete Operation[Graph[_ <: Model.Like]] */
  abstract class Abstract(val origin: Option[Symbol], val location: Option[URI], val interactive: Boolean)
    extends Operation[Graph[_ <: Model.Like]](location match {
      case Some(location) ⇒ s"Import graph with ${origin} from ${location}."
      case None ⇒ "Import graph."
    }) {
    this: Loggable ⇒
  }
  /**
   * Dependency injection routines.
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    lazy val operation = injectOptional[api.OperationGraphImport] getOrElse new OperationGraphImport
  }
}
