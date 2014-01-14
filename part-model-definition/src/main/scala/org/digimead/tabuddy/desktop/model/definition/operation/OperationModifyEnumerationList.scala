/**
 * This file is part of the TA Buddy project.
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

import java.util.concurrent.CancellationException
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.definition.Operation
import org.digimead.tabuddy.desktop.logic
import org.digimead.tabuddy.desktop.logic.payload
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.graph.Graph
import org.eclipse.core.runtime.{IAdaptable, IProgressMonitor}

/**
 * Modify an enumeration list.
 */
class OperationModifyEnumerationList extends logic.operation.OperationModifyEnumerationList with Loggable {
  /**
   * Modify an enumeration list.
   *
   * @param graph graph that contains an enumeration list
   * @param enumerationList exists enumerations
   * @return the modified enumeration list
   */
  def apply(graph: Graph[_ <: Model.Like],
    enumerationList: Set[payload.api.Enumeration[_ <: AnyRef with java.io.Serializable]]): Set[payload.api.Enumeration[_ <: AnyRef with java.io.Serializable]] = {
    /*    val exchanger = new Exchanger[Operation.Result[Set[Enumeration[_ <: AnyRef with java.io.Serializable]]]]()
    App.assertUIThread(false)
    App.exec {
      App.getActiveShell match {
        case Some(shell) =>
          val dialog = new EnumerationList(shell, enumerationList.toList)
          dialog.openOrFocus {
            case result if result == org.eclipse.jface.window.Window.OK =>
              exchanger.exchange(Operation.Result.OK(Some(dialog.getModifiedEnumerations())))
            case result =>
              exchanger.exchange(Operation.Result.Cancel[Set[Enumeration[_ <: AnyRef with java.io.Serializable]]]())
          }
        case None =>
          exchanger.exchange(Operation.Result.Error("Unable to find active shell."))
      }
    }
    exchanger.exchange(null)*/
    null
  }
  /**
   * Create 'Modify an enumeration list' operation.
   *
   * @param graph graph that contains an enumeration list
   * @param enumerationList exists enumerations
   * @return 'Modify an enumeration list' operation
   */
  def operation(graph: Graph[_ <: Model.Like], enumerationList: Set[payload.api.Enumeration[_ <: AnyRef with java.io.Serializable]]) =
    new Implemetation(graph, enumerationList)

  class Implemetation(
    /** Graph container. */
    graph: Graph[_ <: Model.Like],
    /** The list of enumerations. */
    enumerationList: Set[payload.api.Enumeration[_ <: AnyRef with java.io.Serializable]])
    extends logic.operation.OperationModifyEnumerationList.Abstract(graph, enumerationList) with Loggable {
    @volatile protected var allowExecute = true

    override def canExecute() = allowExecute
    override def canRedo() = false
    override def canUndo() = false

    protected def execute(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[Set[payload.api.Enumeration[_ <: AnyRef with java.io.Serializable]]] = {
      try {
        Operation.Result.OK(Option(OperationModifyEnumerationList.this(graph, enumerationList)))
      } catch {
        case e: CancellationException ⇒
          Operation.Result.Cancel()
      }
    }
    protected def redo(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[Set[payload.api.Enumeration[_ <: AnyRef with java.io.Serializable]]] =
      throw new UnsupportedOperationException
    protected def undo(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[Set[payload.api.Enumeration[_ <: AnyRef with java.io.Serializable]]] =
      throw new UnsupportedOperationException
  }
}

