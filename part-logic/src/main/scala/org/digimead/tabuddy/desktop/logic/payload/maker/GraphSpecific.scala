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

package org.digimead.tabuddy.desktop.logic.payload.maker

import java.io.File
import org.digimead.digi.lib.aop.log
import org.digimead.tabuddy.desktop.logic.Logic
import org.digimead.tabuddy.desktop.logic.payload.DSL._
import org.digimead.tabuddy.desktop.logic.payload.Payload
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.element.Element
import org.digimead.tabuddy.model.graph.Graph
import org.digimead.tabuddy.model.serialization.Serialization

/**
 * Part of the graph marker that contains graph specific logic.
 */
trait GraphSpecific {
  this: GraphMarker ⇒

  /** Load the specific graph from the predefined directory ${location}/id/ */
  def graphAcquire(reload: Boolean = false): Graph[_ <: Model.Like] = state.safeWrite { state ⇒
    assertState()
    log.debug(s"Acquire graph with marker ${this}.")
    if (!Logic.container.isOpen())
      throw new IllegalStateException("Workspace is not available.")
    val loaded = if (!reload) state.graphObject else None
    loaded getOrElse {
      val graph = loadGraph(takeItEasy = true) getOrElse {
        log.info("Create new empty graph " + graphModelId)
        /**
         * TABuddy - global TA Buddy space
         *  +-Settings - global TA Buddy settings
         *     +-Templates - global TA Buddy element templates
         *  +-Temp - temporary TA Buddy elements
         *     +-Templates - predefined TA Buddy element templates
         */
        // try to create model because we are unable to load it
        Graph[Model](graphModelId, graphOrigin, Model.scope, Payload.serialization, uuid, graphCreated) { g ⇒
          g.storages = g.storages :+ this.graphPath.toURI()
        }
      }
      graph.withData(_(GraphMarker) = GraphSpecific.this)
      state.graphObject = Option(graph)
      state.payloadObject = Option(initializePayload())
      graph
    }
  }
  /** Close the loaded graph. */
  def graphClose() = state.safeWrite { state ⇒
    assertState()
    log.info(s"Close '${state.graph}' with '${this}'.")
    state.contextRefs.keys.map(GraphMarker.unbind)
    state.contextRefs.clear()
    try markerSave() finally {
      state.graphObject = None
      state.payloadObject = None
    }
  }
  /** Graph creation timestamp. */
  def graphCreated: Element.Timestamp = getValueFromGraphProperties { p ⇒
    Element.Timestamp(p.getProperty(GraphMarker.fieldCreatedMillis).toLong, p.getProperty(GraphMarker.fieldCreatedNanos).toLong)
  }
  /** Store the graph to the predefined directory ${location}/id/ */
  def graphFreeze(storages: Option[Serialization.ExplicitStorages] = None): Unit = state.safeWrite { state ⇒
    assertState()
    log.info(s"Freeze '${state.graph}'.")
    if (!Logic.container.isOpen())
      throw new IllegalStateException("Workspace is not available.")
    Serialization.freeze(state.graph, storages = storages)
  }
  /** Check whether the graph is modified. */
  def graphIsDirty(): Boolean = graphIsOpen && !safeRead { state ⇒
    val ts = state.graph.modified
    state.graph.stored.contains(ts)
  }
  /** Check whether the graph is loaded. */
  def graphIsOpen(): Boolean = safeRead { state ⇒
    assertState()
    state.asInstanceOf[GraphMarker.ThreadUnsafeState].graphObject.nonEmpty
  }
  /** Model ID. */
  def graphModelId: Symbol = {
    assertState()
    Symbol(graphPath.getName)
  }
  /** Origin of the graph. */
  def graphOrigin: Symbol = getValueFromGraphProperties { p ⇒ Symbol(p.getProperty(GraphMarker.fieldOrigin)) }
  /** Path to the graph: base directory and graph directory name. */
  def graphPath: File = getValueFromGraphProperties { p ⇒ new File(p.getProperty(GraphMarker.fieldPath)) }
  /** Graph last save timestamp. */
  def graphStored: Element.Timestamp = getValueFromGraphProperties { p ⇒
    Element.Timestamp(p.getProperty(GraphMarker.fieldSavedMillis).toLong, p.getProperty(GraphMarker.fieldSavedNanos).toLong)
  }

  /** Load the graph. */
  @log
  protected def loadGraph(takeItEasy: Boolean = false): Option[Graph[_ <: Model.Like]] = try {
    if (!markerIsValid)
      return None
    Option[Graph[_ <: Model.Like]](Serialization.acquire(graphOrigin, graphPath.toURI))
  } catch {
    case e: Throwable ⇒
      if (takeItEasy)
        log.debug(s"Unable to load graph ${graphOrigin} from $graphPath: " + e.getMessage())
      else
        log.error(s"Unable to load graph ${graphOrigin} from $graphPath: " + e.getMessage(), e)
      None
  }
}
