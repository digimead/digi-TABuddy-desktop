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

package org.digimead.tabuddy.desktop.logic.payload.maker

import java.io.File
import org.digimead.digi.lib.aop.log
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
  def graphAcquire(): Graph[_ <: Model.Like] = state.lockWrite { state ⇒
    log.debug(s"Acquire model with marker ${this}.")
    loadGraph() getOrElse {
      log.info("Create new empty model " + graphModelId)
      /**
       * TABuddy - global TA Buddy space
       *  +-Settings - global TA Buddy settings
       *     +-Templates - global TA Buddy element templates
       *  +-Temp - temporary TA Buddy elements
       *     +-Templates - predefined TA Buddy element templates
       */
      // try to create model because we are unable to load it
      state.graphObject = Option(Graph[Model](graphModelId, graphOrigin, Model.scope, Payload.serialization, uuid, graphCreated) { g ⇒ })
      state.payloadObject = Option(initializePayload())
      state.graphObject.get
    }
  }
  /** Close the loaded graph. */
  def graphClose() = state.lockWrite { state ⇒
    log.info(s"Close graph '${state.graph}' with '${this}'.")
    try markerSave() finally {
      state.graphObject = None
      state.payloadObject = None
    }
  }
  /** Graph creation timestamp. */
  def graphCreated: Element.Timestamp = getValueFromGraphDescriptor { p ⇒
    Element.Timestamp(p.getProperty(GraphMarker.fieldCreatedMillis).toLong, p.getProperty(GraphMarker.fieldCreatedNanos).toLong)
  }
  /** Graph descriptor location. */
  def graphDescriptor = new File(graphPath.getParentFile(), graphPath.getName() + "." + Payload.extensionGraph)
  /** Store the graph to the predefined directory ${location}/id/ */
  def graphFreeze(): Unit = state.lockWrite { state ⇒
    log.info(s"Freeze graph '${state.graph}'.")
    Serialization.freeze(state.graph)
  }
  /** Check whether the graph is loaded. */
  def graphIsLoaded(): Boolean = lockRead(_.asInstanceOf[GraphMarker.ThreadUnsafeState].graphObject.nonEmpty)
  /** Model ID. */
  def graphModelId: Symbol = Symbol(graphPath.getName)
  /** Origin of the graph. */
  def graphOrigin: Symbol = getValueFromGraphDescriptor { p ⇒ Symbol(p.getProperty(GraphMarker.fieldOrigin)) }
  /** Path to the graph: base directory and graph directory name. */
  def graphPath: File = getValueFromIResourceProperties { p ⇒ new File(p.getProperty(GraphMarker.fieldPath)) }
  /** Graph last save timestamp. */
  def graphStored: Element.Timestamp = getValueFromGraphDescriptor { p ⇒
    Element.Timestamp(p.getProperty(GraphMarker.fieldSavedMillis).toLong, p.getProperty(GraphMarker.fieldSavedNanos).toLong)
  }

  /** Load the graph. */
  @log
  protected def loadGraph(): Option[Graph[_ <: Model.Like]] = try {
    if (!markerIsValid)
      return None
    Option[Graph[_ <: Model.Like]](Serialization.acquire(graphOrigin, graphPath.toURI))
  } catch {
    case e: Throwable ⇒
      log.error(s"Unable to load graph ${graphOrigin} from $graphPath: " + e.getMessage(), e)
      None
  }
}