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

package org.digimead.tabuddy.desktop.logic.payload.view

import java.util.UUID
import org.digimead.digi.lib.api.XDependencyInjection
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.Messages
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.logic.Default
import org.digimead.tabuddy.desktop.logic.payload.DSL._
import org.digimead.tabuddy.desktop.logic.payload.marker.GraphMarker
import org.digimead.tabuddy.desktop.logic.payload.view.api.XSorting
import org.digimead.tabuddy.desktop.logic.payload.{ PredefinedElements, PropertyType }
import org.digimead.tabuddy.model.Record
import org.digimead.tabuddy.model.element.Element
import scala.collection.mutable

/**
 * Sorting is a user selected group of Sorting.Definition (element property <-> logic.comparator tuple)
 * which is applied to the visible model.
 * Sorting equality is based on the elementId.
 */
class Sorting(
  /** Sort unique id. */
  val id: UUID,
  /** Sort name. */
  val name: String,
  /** Sort description. */
  val description: String,
  /** Availability flag for user (some sortings may exists, but not involved in element representation). */
  val availability: Boolean,
  /** Sorting definitions. */
  val definitions: mutable.LinkedHashSet[XSorting.Definition]) extends XSorting {
  /** Element id symbol. */
  val elementId = Symbol(id.toString.replaceAll("-", "_"))

  /** The copy constructor */
  def copy(id: UUID = this.id,
    name: String = this.name,
    description: String = this.description,
    availability: Boolean = this.availability,
    definitions: mutable.LinkedHashSet[XSorting.Definition] = this.definitions): this.type =
    new Sorting(id, name, description, availability, definitions).asInstanceOf[this.type]

  def canEqual(other: Any): Boolean =
    other.isInstanceOf[Sorting]
  override def equals(other: Any) = other match {
    case that: Sorting ⇒
      (this eq that) || {
        that.canEqual(this) &&
          elementId == that.elementId // elementId == UUID
      }
    case _ ⇒ false
  }
  override def hashCode() = elementId.hashCode
}

object Sorting extends XLoggable {
  /** Predefined default sort */
  val simpleSorting = new Sorting(UUID.fromString("da7303d6-d432-49bd-9634-48d1638c2775"), Messages.default_text,
    "default sort order", true, mutable.LinkedHashSet())
  /** Fields limit per sort */
  val collectionMaximum = 100

  /** Add sorting element. */
  def add(marker: GraphMarker, sorting: Sorting) = marker.safeUpdate { state ⇒
    val container = PredefinedElements.eViewSorting(state.graph)
    val element = (container | RecordLocation(sorting.elementId)).eRelative
    // remove all element properties
    element.eRemoveAll()
    // set element's properties
    element.eSet[java.lang.Boolean](getFieldIDAvailability, sorting.availability)
    element.eSet[String](getFieldIDName, sorting.name)
    element.eSet[String](getFieldIDDescription, sorting.description, "")
    if (sorting.definitions.size > Sorting.collectionMaximum)
      log.error("%s definition sequence is too long, %d elements will be dropped".format(sorting, sorting.definitions.size - Sorting.collectionMaximum))
    val iterator = sorting.definitions.toIterator
    for {
      i ← 0 until math.min(sorting.definitions.size, Sorting.collectionMaximum)
      definition = iterator.next
    } {
      element.eSet[String](getFieldIDDefinitionArgument(i), definition.argument)
      element.eSet[String](getFieldIDDefinitionComparator(i), definition.comparator.toString)
      element.eSet[java.lang.Boolean](getFieldIDDefinitionDirection(i), definition.direction)
      element.eSet[String](getFieldIDDefinitionProperty(i), definition.property.name)
      element.eSet[String](getFieldIDDefinitionType(i), definition.propertyType.name)
    }
  }
  /** The deep comparison of two sortings */
  def compareDeep(a: Sorting, b: Sorting): Boolean =
    (a eq b) || (a == b && a.description == b.description && a.availability == b.availability && a.name == b.name &&
      // definition sequence order is important
      a.definitions.sameElements(b.definitions))
  /** Sorting elements container */
  def container(): Element = DI.definition
  /** Load sorting from element */
  def get(element: Element): Option[Sorting] = {
    val id = element.eId.name.replaceAll("_", "-")
    val uuid = try { Option(UUID.fromString(id)) } catch {
      case e: Throwable ⇒
        log.error("Unable to load sorting with id " + id)
        None
    }
    val availability = element.eGet[java.lang.Boolean](getFieldIDAvailability).map(_.get)
    val description = element.eGet[String](getFieldIDDescription).map(_.get)
    val name = element.eGet[String](getFieldIDName).map(_.get)
    var next = true
    val definitionsRaw = for (i ← 0 until Sorting.collectionMaximum if next) yield {
      element.eGet[String](getFieldIDDefinitionProperty(i)) match {
        case Some(property) ⇒
          val argument = element.eGet[String](getFieldIDDefinitionArgument(i)).map(_.get).getOrElse("")
          val comparator = element.eGet[String](getFieldIDDefinitionComparator(i)).flatMap(id ⇒
            try { Some(UUID.fromString(id.get)) } catch { case e: Throwable ⇒ None })
          val direction = element.eGet[java.lang.Boolean](getFieldIDDefinitionDirection(i)).map(n ⇒
            Boolean.unbox(n.get)).getOrElse(Default.sortingDirection)
          val propertyType = element.eGet[String](getFieldIDDefinitionType(i)).map(_.get)
          for {
            comparator ← comparator
            propertyType ← propertyType if (PropertyType.container.isDefinedAt(Symbol(propertyType)))
          } yield XSorting.Definition(Symbol(property), Symbol(propertyType), direction, comparator, argument)
        case None ⇒
          next = false
          None
      }
    }
    val definitions = mutable.LinkedHashSet(definitionsRaw.flatten: _*)
    for {
      uuid ← uuid if definitions.nonEmpty
      availability ← availability
      name ← name
    } yield new Sorting(uuid, name, description.getOrElse(""), availability, definitions)
  }
  /** Returns an ID for the availability field */
  def getFieldIDAvailability() = 'availability
  /** Returns an ID for the definition's argument field */
  def getFieldIDDefinitionArgument(n: Int) = Symbol(n + "_argument")
  /** Returns an ID for the definition's comparator field */
  def getFieldIDDefinitionComparator(n: Int) = Symbol(n + "_comparator")
  /** Returns an ID for the definition's direction field */
  def getFieldIDDefinitionDirection(n: Int) = Symbol(n + "_direction")
  /** Returns an ID for the definition's property field */
  def getFieldIDDefinitionProperty(n: Int) = Symbol(n + "_property")
  /** Returns an ID for the definition's type field */
  def getFieldIDDefinitionType(n: Int) = Symbol(n + "_type")
  /** Returns an ID for the description field */
  def getFieldIDDescription() = 'description
  /** Returns an ID for the label field */
  def getFieldIDName() = 'name
  /** Get all view sortings for the current model. */
  def load(marker: GraphMarker): Set[Sorting] = marker.safeRead { state ⇒
    log.debug("Load view sorting list for graph " + state.graph)
    val container = PredefinedElements.eViewSorting(state.graph)
    val result = container.eNode.freezeRead(_.children.map(_.rootBox.e)).toSet.map(Sorting.get).flatten
    if (result.contains(Sorting.simpleSorting)) result else result + Sorting.simpleSorting
  }
  /** Update only modified view sortings */
  def save(marker: GraphMarker, sortings: Set[Sorting]) = marker.safeUpdate { state ⇒
    log.debug("Save view sorting list for graph " + state.graph)
    val oldSortings = App.execNGet { state.payload.viewSortings.values.toSeq }
    val newSortings = sortings - simpleSorting
    val deleted = oldSortings.filterNot(oldSorting ⇒ newSortings.exists(compareDeep(_, oldSorting)))
    val added = newSortings.filterNot(newSorting ⇒ oldSortings.exists(compareDeep(_, newSorting)))
    if (deleted.nonEmpty) {
      log.debug("Delete Set(%s)".format(deleted.mkString(", ")))
      App.execNGet { deleted.foreach { sorting ⇒ state.payload.viewSortings.remove(sorting.id) } }
      deleted.foreach(remove(marker, _))
    }
    if (added.nonEmpty) {
      log.debug("Add Set(%s)".format(added.mkString(", ")))
      added.foreach(add(marker, _))
      App.execNGet { added.foreach { sorting ⇒ state.payload.viewSortings(sorting.id) = sorting } }
    }
  }
  /** Remove sorting element. */
  def remove(marker: GraphMarker, sorting: Sorting) = marker.safeUpdate { state ⇒
    val container = PredefinedElements.eViewSorting(state.graph)
    container.eNode.safeWrite { node ⇒ node.children.find(_.rootBox.e.eId == sorting.elementId).foreach(node -= _) }
  }

  /**
   * Dependency injection routines
   */
  private object DI extends XDependencyInjection.PersistentInjectable {
    org.digimead.digi.lib.DependencyInjection.assertDynamic[Record.Like]("eViewSorting")
    /** Get or create dynamically eViewSorting container inside current active model. */
    def definition = inject[Record.Like]("eViewSorting")
  }
}
