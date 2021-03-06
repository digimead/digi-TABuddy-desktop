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

package org.digimead.tabuddy.desktop.logic.payload

import java.util.UUID
import org.digimead.digi.lib.api.XDependencyInjection
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.definition.Context
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.support.{ WritableMap, WritableValue }
import org.digimead.tabuddy.desktop.logic.Logic
import org.digimead.tabuddy.desktop.logic.payload.marker.GraphMarker
import org.digimead.tabuddy.desktop.logic.payload.view.{ Filter, Sorting, View }
import org.digimead.tabuddy.model.serialization.Serialization

/**
 * Singleton that contains information related to specific graph.
 */
/*
 * Most of lazy fields of this class is initialized from the event loop at GraphMarker.initializePayload
 */
class Payload(val marker: GraphMarker) extends XLoggable {
  /** The property representing all available element templates for user, contains at least one predefined element. */
  lazy val elementTemplates = WritableMap[Symbol, ElementTemplate]
  /** The property representing original element templates. */
  lazy val originalElementTemplates = {
    /*
     * This property is initialized from GraphMarker.initializePayload, so
     * 1. This marker is already locked for writing.
     * 2. ElementTemplate.load will lock marker for reading.
     * 3. We will modify WritableMap within event loop thread.
     */
    val (original, user) = ElementTemplate.load(marker)
    App.execNGet {
      elementTemplates.clear
      user.foreach(template ⇒ elementTemplates(template.id) = template) // push user templates to elementTemplates
    }
    original
  }
  /** The property representing all available enumerations. */
  lazy val enumerations = WritableMap[Symbol, Enumeration[_ <: AnyRef with java.io.Serializable]]
  /** Predefined type schemas that are available for this application. */
  lazy val predefinedTypeSchemas: Seq[TypeSchema] = TypeSchema.predefined
  /** The property representing all available type schemas. */
  lazy val typeSchemas = WritableMap[UUID, TypeSchema]
  /** The property representing the active type schema. */
  lazy val typeSchema = WritableValue[TypeSchema]
  /** The property representing all available view definitions. */
  lazy val viewDefinitions = WritableMap[UUID, View]
  /** The property representing all available view filters. */
  lazy val viewFilters = WritableMap[UUID, Filter]
  /** The property representing all available view sortings. */
  lazy val viewSortings = WritableMap[UUID, Sorting]

  /** Get user enumerations. */
  def getAvailableElementTemplates(): List[ElementTemplate] =
    App.execNGet { elementTemplates.values.filter(_.availability).toList }
  /** Get user enumerations. */
  def getAvailableEnumerations(): List[Enumeration[_ <: AnyRef with java.io.Serializable]] =
    App.execNGet { enumerations.values.filter(_.availability).toList }
  /** Get user types. */
  def getAvailableTypes(defaultValue: Boolean = true): List[PropertyType[_ <: AnyRef with java.io.Serializable]] = App.execNGet {
    val currentTypeSchema = typeSchema.value
    PropertyType.container.values.toList.filter(ptype ⇒
      currentTypeSchema.entity.get(ptype.id).map(_.availability).getOrElse(defaultValue))
  }
  /** Get all available view definitions. */
  def getAvailableViewDefinitions(): Set[View] = App.execNGet {
    val result = View.displayName +: viewDefinitions.values.filter(_.availability).toList.sortBy(_.name)
    if (result.isEmpty) Set(View.displayName) else result.toSet
  }
  /** Get all available view filters. */
  def getAvailableViewFilters(): Set[Filter] = App.execNGet {
    val result = Filter.allowAllFilter +: viewFilters.values.filter(_.availability).toList.sortBy(_.name)
    if (result.isEmpty) Set(Filter.allowAllFilter) else result.toSet
  }
  /** Get all available view sortings. */
  def getAvailableViewSortings(): Set[Sorting] = App.execNGet {
    val result = Sorting.simpleSorting +: viewSortings.values.filter(_.availability).toList.sortBy(_.name)
    if (result.isEmpty) Set(Sorting.simpleSorting) else result.toSet
  }
  /** Get selected view definitions. */
  def getSelectedViewDefinition(context: Context, local: Boolean = false): Option[View] = {
    if (local)
      Option(context.getLocal(Logic.Id.selectedView).asInstanceOf[UUID])
    else
      Option(context.get(Logic.Id.selectedView).asInstanceOf[UUID])
  } flatMap (viewDefinitions.get)
  /** Get selected view filter. */
  def getSelectedViewFilter(context: Context, local: Boolean = false): Option[Filter] = {
    if (local)
      Option(context.getLocal(Logic.Id.selectedFilter).asInstanceOf[UUID])
    else
      Option(context.get(Logic.Id.selectedFilter).asInstanceOf[UUID])
  } flatMap (viewFilters.get)
  /** Get selected view sorting. */
  def getSelectedViewSorting(context: Context, local: Boolean = false): Option[Sorting] = {
    if (local)
      Option(context.getLocal(Logic.Id.selectedSorting).asInstanceOf[UUID])
    else
      Option(context.get(Logic.Id.selectedSorting).asInstanceOf[UUID])
  } flatMap (viewSortings.get)
  /** Generate new name/id/... */
  def generateNew(base: String, suffix: String, exists: (String) ⇒ Boolean): String = {
    val iterator = new Iterator[String] {
      @volatile private var n = 0
      def hasNext = true
      def next = {
        val result = if (n == 0) base else base + suffix + n
        n += 1
        result
      }
    }
    var newValue = iterator.next
    while (exists(newValue))
      newValue = iterator.next
    newValue
  }
}

/**
 * Payload contains
 * - Model, binded to current device with unique ID
 * - Records, binded to Model
 */
object Payload extends XLoggable {
  /** Get set of available serialization mechanisms. */
  def availableSerialization = DI.availableSerialization + defaultSerialization
  /** Get default serialization mechanism. */
  def defaultSerialization = DI.defaultSerialization
  /** Get flag for unknown storages (by default 'read only' or 'read write') */
  def isUnknownStoragesRW = DI.isUnknownStoragesRW
  /** Get file extension for the graph descriptor. */
  def extensionGraph = DI.extensionGraph
  /** Get local origin. */
  def origin = DI.origin

  trait YAMLProcessor[T] {
    /** Convert YAML to object */
    def from(yaml: String): Option[T]
    /** Convert object to YAML */
    def to(obj: T): String
  }
  /**
   * Dependency injection routines
   */
  private object DI extends XDependencyInjection.PersistentInjectable {
    /** UUID of the default TypeSchema. */
    lazy val default = inject[UUID]("Payload.TypeSchema.Default")
    /** Default serialization mechanism. */
    lazy val defaultSerialization = inject[Serialization.Identifier]("Payload.Serialization")
    /** Set of available serialization mechanisms. */
    lazy val availableSerialization = inject[Set[Serialization.Identifier]]("Payload.Serialization.Available")
    /** File extension for the graph descriptor. */
    lazy val extensionGraph = injectOptional[Symbol]("Payload.Extension.Graph") getOrElse "graph"
    /** The local origin. */
    lazy val origin = injectOptional[Symbol]("Origin") getOrElse 'default
    /** Flag for unknown storages (by default 'read only' or 'read write') */
    lazy val isUnknownStoragesRW = injectOptional[Boolean]("Payload.Serialization.UnknownStoragesRW") getOrElse false
  }
}
