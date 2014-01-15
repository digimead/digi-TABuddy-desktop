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
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.Messages
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.logic.payload.DSL._
import org.digimead.tabuddy.desktop.logic.payload.PredefinedElements
import org.digimead.tabuddy.desktop.logic.payload.maker.GraphMarker
import org.digimead.tabuddy.model.element.Element
import scala.collection.mutable

/**
 * View is group of user specific filters and user specific sortings.
 * View equality is based on the elementId.
 */
class View(
  /** View unique id. */
  val id: UUID,
  /** View name. */
  val name: String,
  /** View description. */
  val description: String,
  /** Availability flag for user (some views may exists, but not involved in element representation). */
  val availability: Boolean,
  /** View fields (visible properties). */
  val fields: mutable.LinkedHashSet[Symbol],
  /** View filters (visible filters). */
  val filters: mutable.LinkedHashSet[UUID],
  /** View sortings (visible sortings). */
  val sortings: mutable.LinkedHashSet[UUID]) extends api.View {
  /** Element id symbol. */
  val elementId = Symbol(id.toString.replaceAll("-", "_"))

  /** The copy constructor */
  def copy(id: UUID = this.id,
    name: String = this.name,
    description: String = this.description,
    availability: Boolean = this.availability,
    fields: mutable.LinkedHashSet[Symbol] = this.fields,
    filters: mutable.LinkedHashSet[UUID] = this.filters,
    sortings: mutable.LinkedHashSet[UUID] = this.sortings): this.type =
    new View(id, name, description, availability, fields, filters, sortings).asInstanceOf[this.type]

  def canEqual(other: Any) =
    other.isInstanceOf[api.View]
  override def equals(other: Any) = other match {
    case that: api.View ⇒
      (this eq that) || {
        that.canEqual(this) &&
          elementId == that.elementId
      }
    case _ ⇒ false
  }
  override def hashCode() = elementId.hashCode
}

object View extends Loggable {
  /** Predefined default view */
  val displayName = new View(UUID.fromString("033ca060-8493-11e2-9e96-0800200c9a66"), Messages.default_text,
    "The default minimal view with 'name' column", true, mutable.LinkedHashSet('name), mutable.LinkedHashSet(), mutable.LinkedHashSet())
  /** Columns limit per view */
  val collectionMaximum = 10000

  /** Add view element. */
  def add(marker: GraphMarker, view: api.View) = marker.safeUpdate { state ⇒
    val container = PredefinedElements.eViewDefinition(state.graph)
    val element = (container | RecordLocation(view.elementId)).eRelative
    element.eRemoveAll()
    // set element's properties
    element.eSet[java.lang.Boolean](getFieldIDAvailability, view.availability)
    element.eSet[String](getFieldIDName, view.name)
    element.eSet[String](getFieldIDDescription, view.description, "")
    // fields
    if (view.fields.size > View.collectionMaximum)
      log.error("%s fields sequence is too long, %d elements will be dropped".format(view, view.fields.size - View.collectionMaximum))
    val iteratorFields = view.fields.toIterator
    for {
      i ← 0 until math.min(view.fields.size, View.collectionMaximum)
      field = iteratorFields.next
    } element.eSet[String](getFieldIDField(i), field.name)
    // filters
    if (view.filters.size > View.collectionMaximum)
      log.error("%s filters sequence is too long, %d elements will be dropped".format(view, view.filters.size - View.collectionMaximum))
    val iteratorFilters = view.filters.toIterator
    for {
      i ← 0 until math.min(view.filters.size, View.collectionMaximum)
      filter = iteratorFilters.next
    } element.eSet[String](getFieldIDFilter(i), filter.toString())
    // sortings
    if (view.sortings.size > View.collectionMaximum)
      log.error("%s sortings sequence is too long, %d elements will be dropped".format(view, view.sortings.size - View.collectionMaximum))
    val iteratorSortings = view.sortings.toIterator
    for {
      i ← 0 until math.min(view.sortings.size, View.collectionMaximum)
      sorting = iteratorSortings.next
    } element.eSet[String](getFieldIDSorting(i), sorting.toString())
  }
  /** The deep comparison of two sortings */
  def compareDeep(a: api.View, b: api.View): Boolean =
    (a eq b) || (a == b && a.description == b.description && a.availability == b.availability && a.name == b.name &&
      // fields sequence order is important
      a.fields.sameElements(b.fields) &&
      a.filters.size == b.filters.size && a.filters.toSeq.sortBy(_.hashCode).sameElements(b.filters.toSeq.sortBy(_.hashCode)) &&
      a.sortings.size == b.sortings.size && a.sortings.toSeq.sortBy(_.hashCode).sameElements(b.sortings.toSeq.sortBy(_.hashCode)))
  /** Get view from element */
  def get(element: Element): Option[View] = {
    val id = element.eId.name.replaceAll("_", "-")
    val uuid = try { Option(UUID.fromString(id)) } catch {
      case e: Throwable ⇒
        log.error("Unable to load view with id " + id)
        None
    }
    val availability = element.eGet[java.lang.Boolean](getFieldIDAvailability).map(_.get)
    val description = element.eGet[String](getFieldIDDescription).map(_.get)
    val name = element.eGet[String](getFieldIDName).map(_.get)
    var next = true
    // fields
    val fieldsRaw = for (i ← 0 until View.collectionMaximum if next) yield {
      element.eGet[String](getFieldIDField(i)) match {
        case Some(column) ⇒
          Some(column.get)
        case None ⇒
          next = false
          None
      }
    }
    val fields = mutable.LinkedHashSet(fieldsRaw.flatten.map((id: String) ⇒ Symbol(id)): _*)
    // filters
    next = true
    val filtersRaw = for (i ← 0 until View.collectionMaximum if next) yield {
      element.eGet[String](getFieldIDFilter(i)) match {
        case Some(uuid) ⇒ try {
          Some(UUID.fromString(uuid.get))
        } catch {
          case e: Throwable ⇒
            log.error(s"Unable to read filter uuid $uuid for view $name")
            None
        }
        case None ⇒
          next = false
          None
      }
    }
    val filters = mutable.LinkedHashSet(filtersRaw.flatten: _*)
    // sortings
    next = true
    val sortingsRaw = for (i ← 0 until View.collectionMaximum if next) yield {
      element.eGet[String](getFieldIDSorting(i)) match {
        case Some(uuid) ⇒ try {
          Some(UUID.fromString(uuid.get))
        } catch {
          case e: Throwable ⇒
            log.error(s"Unable to read sorting uuid $uuid for view $name")
            None
        }
        case None ⇒
          next = false
          None
      }
    }
    val sortings = mutable.LinkedHashSet(sortingsRaw.flatten: _*)
    for {
      uuid ← uuid if fields.nonEmpty
      availability ← availability
      name ← name
    } yield new View(uuid, name, description.getOrElse(""), availability, fields, filters, sortings)
  }
  /** Returns an ID for the availability field */
  def getFieldIDAvailability() = 'availability
  /** Returns an ID for the description field */
  def getFieldIDDescription() = 'description
  /** Returns an ID for the 'field' field */
  def getFieldIDField(n: Int) = Symbol(n + "_field")
  /** Returns an ID for the 'filter' field */
  def getFieldIDFilter(n: Int) = Symbol(n + "_filter")
  /** Returns an ID for the 'sorting' field */
  def getFieldIDSorting(n: Int) = Symbol(n + "_sorting")
  /** Returns an ID for the name field */
  def getFieldIDName() = 'name
  /** Get all view definitions. */
  def load(marker: GraphMarker): Set[View] = marker.safeRead { state ⇒
    log.debug("Load view definition list for graph " + state.graph)
    val container = PredefinedElements.eViewDefinition(state.graph)
    val result = container.eNode.freezeRead(_.children.map(_.rootBox.e)).toSet.map(View.get).flatten
    if (result.contains(View.displayName)) result else result + View.displayName
  }
  /** Update only modified view definitions. */
  def save(marker: GraphMarker, views: Set[api.View]) = marker.safeUpdate { state ⇒
    log.debug("Save view definition list for graph " + state.graph)
    val oldViews = App.execNGet { state.payload.viewDefinitions.values }
    val newViews = views - displayName
    val deleted = oldViews.filterNot(oldView ⇒ newViews.exists(compareDeep(_, oldView)))
    val added = newViews.filterNot(newView ⇒ oldViews.exists(compareDeep(_, newView)))
    if (deleted.nonEmpty) {
      log.debug("Delete Set(%s)".format(deleted.mkString(", ")))
      App.execNGet { deleted.foreach { view ⇒ state.payload.viewDefinitions.remove(view.id) } }
      deleted.foreach(remove(marker, _))
    }
    if (added.nonEmpty) {
      log.debug("Add Set(%s)".format(added.mkString(", ")))
      added.foreach(add(marker, _))
      App.execNGet { added.foreach { view ⇒ state.payload.viewDefinitions(view.id) = view } }
    }
  }
  /** Remove view element. */
  def remove(marker: GraphMarker, view: api.View) = marker.safeUpdate { state ⇒
    val container = PredefinedElements.eViewDefinition(state.graph)
    container.eNode.safeWrite { node ⇒ node.children.find(_.rootBox.e.eId == view.elementId).foreach(node -= _) }
  }
}
