/**
 * This file is part of the TABuddy project.
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

package org.digimead.tabuddy.desktop.logic.payload.api

import java.io.File
import java.net.URI
import java.util.UUID

import scala.collection.immutable

import org.digimead.tabuddy.desktop.logic.api.ModelMarker
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.element.Element
import org.digimead.tabuddy.model.element.Reference
import org.osgi.framework.BundleActivator

/**
 * Payload contains
 * - Model, binded to current device with unique ID
 * - Records, binded to Model
 */
trait Payload {
  /** File extension for the serialized element. */
  val extensionElement: String
  /** File extension for the model descriptors. */
  val extensionModel: String
  /** Type schemas folder name. */
  val folderTypeSchemas: String

  /** Load the specific model from the predefined directory ${location}/id/ */
  def acquireModel(marker: ModelMarker): Option[Model.Interface[_ <: Model.Stash]]
  /** Close the active model. */
  def closeModel(marker: ModelMarker)
  /** Create the specific model at the specific directory. */
  def createModel(fullPath: File): ModelMarker
  /** Delete the model. */
  def deleteModel(marker: ModelMarker): ModelMarker
  /** Store the specific model to the predefined directory ${location}/id/ */
  def saveModel(marker: ModelMarker, model: Model.Interface[_ <: Model.Stash]): URI
  /** Generate new name/id/... */
  def generateNew(base: String, suffix: String, exists: (String) => Boolean): String
  /** Returns the element storage. */
  def getElementStorage(marker: ModelMarker, reference: Reference): Option[URI]
  /** Get marker for loaded model. */
  def getModelMarker(model: Model.Interface[_ <: Model.Stash]): Option[ModelMarker]
  /** Get a model list. */
  def listModels(): Seq[ModelMarker]
  /** Load type schemas. */
  def loadTypeSchemas(marker: ModelMarker): immutable.HashSet[TypeSchema]
  /** Get marker for loaded model or throw error. */
  def modelMarker(model: Model.Interface[_ <: Model.Stash]): ModelMarker
  /** Save type schemas. */
  def saveTypeSchemas(marker: ModelMarker, schemas: immutable.Set[TypeSchema])
  /** Get a model settings container. */
  def settings(): Element.Generic
}

object Payload {
  trait YAMLProcessor[T] {
    /** Convert YAML to object */
    def from(yaml: String): Option[T]
    /** Convert object to YAML */
    def to(obj: T): String
  }
}
