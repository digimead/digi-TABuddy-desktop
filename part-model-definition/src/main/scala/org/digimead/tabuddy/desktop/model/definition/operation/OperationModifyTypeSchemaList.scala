/**
 * This file is part of the TA Buddy project.
 * Copyright (c) 2012-2014 Alexey Aksenov ezh@ezh.msk.ru
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

package org.digimead.tabuddy.desktop.model.definition.operation

import java.util.concurrent.{ CancellationException, Exchanger }
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.definition.Operation
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.logic
import org.digimead.tabuddy.desktop.logic.payload.maker.GraphMarker
import org.digimead.tabuddy.desktop.logic.payload.{ Payload, api ⇒ papi }
import org.digimead.tabuddy.desktop.model.definition.dialog.typelist.TypeList
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.graph.Graph
import org.eclipse.core.runtime.{ IAdaptable, IProgressMonitor }
import org.eclipse.e4.core.contexts.ContextInjectionFactory
import org.eclipse.swt.widgets.Shell

/**
 * Modify a type schema list.
 */
class OperationModifyTypeSchemaList extends logic.operation.OperationModifyTypeSchemaList with Loggable {
  /**
   * Modify a type schema list.
   *
   * @param graph graph that contains a type schema list
   * @param schemaList the initial type schema list
   * @param activeSchema the active type schema
   * @return the modified type schema list, the active type schema
   */
  def apply(graph: Graph[_ <: Model.Like], schemaList: Set[papi.TypeSchema], activeSchema: papi.TypeSchema): (Set[papi.TypeSchema], papi.TypeSchema) = {
    log.info(s"Modify the type schema list of ${graph}.")
    dialog(graph, schemaList, activeSchema) match {
      case Operation.Result.OK(Some((schemaList, activeSchema)), _) ⇒ (schemaList, activeSchema)
      case _ ⇒ (schemaList, activeSchema)
    }
  }
  /**
   * Create 'Modify a type schema list' operation.
   *
   * @param graph graph that contains a type schema list
   * @param schemaList the initial type schema list
   * @param activeSchema the active type schema
   * @return 'Modify a type schema list' operation
   */
  def operation(graph: Graph[_ <: Model.Like], schemaList: Set[papi.TypeSchema], activeSchema: papi.TypeSchema) =
    new Implemetation(graph, schemaList, activeSchema)

  protected def dialog(graph: Graph[_ <: Model.Like], schemaList: Set[papi.TypeSchema],
    activeSchema: papi.TypeSchema): Operation.Result[(Set[papi.TypeSchema], papi.TypeSchema)] = {
    val marker = GraphMarker(graph)
    val exchanger = new Exchanger[Operation.Result[(Set[papi.TypeSchema], papi.TypeSchema)]]()
    App.assertEventThread(false)
    // this lock is preparation that prevents freeze of the event loop thread
    marker.safeRead { _ ⇒
      App.exec {
        GraphMarker.shell(marker) match {
          case Some((context, shell)) ⇒
            // actual lock inside event loop thread
            marker.safeRead { state ⇒
              val dialogContext = context.createChild("TypeListDialog")
              dialogContext.set(classOf[Shell], shell)
              dialogContext.set(classOf[Graph[_ <: Model.Like]], graph)
              dialogContext.set(classOf[GraphMarker], marker)
              dialogContext.set(classOf[Payload], state.payload)
              dialogContext.set(classOf[Set[papi.TypeSchema]], schemaList)
              dialogContext.set(classOf[papi.TypeSchema], activeSchema)
              val dialog = ContextInjectionFactory.make(classOf[TypeList], dialogContext)
              dialog.openOrFocus { result ⇒
                context.removeChild(dialogContext)
                dialogContext.dispose()
                if (result == org.eclipse.jface.window.Window.OK)
                  exchanger.exchange(Operation.Result.OK(Some(dialog.getSchemaSet, dialog.getActiveSchema)))
                else
                  exchanger.exchange(Operation.Result.Cancel())
              }
            }
          case None ⇒
            exchanger.exchange(Operation.Result.Error("Unable to find active shell."))
        }
      }(App.LongRunnable)
    }
    exchanger.exchange(null)
  }

  class Implemetation(
    /** Graph container. */
    graph: Graph[_ <: Model.Like],
    /** The list of type schemas. */
    schemaList: Set[papi.TypeSchema],
    /** The active type schema. */
    activeSchema: papi.TypeSchema)
    extends logic.operation.OperationModifyTypeSchemaList.Abstract(graph, schemaList, activeSchema) with Loggable {
    @volatile protected var allowExecute = true

    override def canExecute() = allowExecute
    override def canRedo() = false
    override def canUndo() = false

    protected def execute(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[(Set[papi.TypeSchema], papi.TypeSchema)] =
      try dialog(graph, schemaList, activeSchema)
      catch {
        case e: IllegalArgumentException ⇒
          Operation.Result.Error(e.getMessage(), e)
        case e: IllegalStateException ⇒
          Operation.Result.Error(e.getMessage(), e)
        case e: CancellationException ⇒
          Operation.Result.Cancel()
      }
    protected def redo(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[(Set[papi.TypeSchema], papi.TypeSchema)] =
      throw new UnsupportedOperationException
    protected def undo(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[(Set[papi.TypeSchema], papi.TypeSchema)] =
      throw new UnsupportedOperationException
  }
}
