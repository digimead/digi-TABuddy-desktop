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

package org.digimead.tabuddy.desktop.logic.payload.maker.api

import java.io.File
import java.util.UUID
import org.digimead.tabuddy.desktop.logic.payload.api
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.element.Element
import org.digimead.tabuddy.model.graph.Graph
import org.digimead.tabuddy.model.serialization.Serialization
import scala.collection.immutable

/**
 * Graph marker is an object that holds an association between real graph at client
 *   and Eclipse IResource within container(project).
 */
trait GraphMarker {
  /** Autoload property file if suitable information needed. */
  val autoload: Boolean
  /** Container IResource unique id. */
  val uuid: UUID

  /** Assert marker state. */
  def assertState()
  /** Load the specific graph from the predefined directory ${location}/id/ */
  def graphAcquire(reload: Boolean = false): Graph[_ <: Model.Like]
  /** Close the loaded graph. */
  def graphClose()
  /** Graph creation timestamp. */
  def graphCreated: Element.Timestamp
  /** Store the graph to the predefined directory ${location}/id/ */
  def graphFreeze(storages: Option[Serialization.ExplicitStorages] = None)
  /** Check whether the graph is modified. */
  def graphIsDirty(): Boolean
  /** Check whether the graph is loaded. */
  def graphIsOpen(): Boolean
  /** Model ID. */
  def graphModelId: Symbol
  /** Origin of the graph. */
  def graphOrigin: Symbol
  /** Path to the graph: base directory and graph directory name. */
  def graphPath: File
  /** Graph last save timestamp. */
  def graphStored: Element.Timestamp
  /** Load type schemas from local storage. */
  def loadTypeSchemas(): immutable.HashSet[api.TypeSchema]
  /** The validation flag indicating whether the marker is consistent. */
  def markerIsValid: Boolean
  /** Marker last access timestamp. */
  def markerLastAccessed: Long
  /** Load marker properties. */
  def markerLoad()
  /** Save marker properties. */
  def markerSave()
  /** Save type schemas to the local storage. */
  def saveTypeSchemas(schemas: immutable.Set[api.TypeSchema])
}