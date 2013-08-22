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

package org.digimead.tabuddy.desktop.logic.payload.view

import java.util.UUID

import scala.collection.mutable
import scala.collection.immutable

import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.Messages
import org.digimead.tabuddy.desktop.logic.Data
import org.digimead.tabuddy.desktop.logic.payload.PropertyType
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.Record
import org.digimead.tabuddy.model.element.Coordinate
import org.digimead.tabuddy.model.element.Element

/**
 * Filter base trait.
 * Filter is a user selected group of Filter.Rule (element property <-> logic.filter tuple)
 * which is applied to the visible model.
 * Filter equality is based on the elementId.
 */
class Filter(
  /** Filter unique id */
  val id: UUID,
  /** Filter name */
  val name: String,
  /** Filter description */
  val description: String,
  /** Availability flag for user (some filters may exists, but not involved in element representation) */
  val availability: Boolean,
  /** Filter rules, sorted by hash code */
  val rules: mutable.LinkedHashSet[api.Filter.Rule]) extends api.Filter {
  /** Element id symbol */
  val elementId = Symbol(id.toString.replaceAll("-", "_"))

  /** The copy constructor */
  def copy(id: UUID = this.id,
    name: String = this.name,
    description: String = this.description,
    availability: Boolean = this.availability,
    rules: mutable.LinkedHashSet[api.Filter.Rule] = this.rules): this.type =
    new Filter(id, name, description, availability, rules).asInstanceOf[this.type]

  def canEqual(other: Any) =
    other.isInstanceOf[api.Filter]
  override def equals(other: Any) = other match {
    case that: api.Filter =>
      (this eq that) || {
        that.canEqual(this) &&
          elementId == that.elementId // elementId == UUID
      }
    case _ => false
  }
  override def hashCode() = elementId.hashCode
  override def toString() = s"Filter($id, $name)"
}

object Filter extends Loggable {
  /** Predefined default filter. */
  val allowAllFilter = new Filter(UUID.fromString("d9baaf38-fb98-4de5-9085-12156e668b0c"), Messages.default_text, "", true, mutable.LinkedHashSet())
  /** Fields limit per sort */
  val collectionMaximum = 100

  /** The deep comparison of two filters */
  def compareDeep(a: api.Filter, b: api.Filter): Boolean =
    (a eq b) || (a == b && a.description == b.description && a.availability == b.availability && a.name == b.name &&
      a.rules.size == b.rules.size && a.rules.toSeq.sortBy(_.hashCode).sameElements(b.rules.toSeq.sortBy(_.hashCode)))
  /** Filter elements container */
  def container(): Element.Generic = DI.definition
  /** Load filter from element */
  def get(element: Element.Generic): Option[Filter] = {
    val id = element.eId.name.replaceAll("_", "-")
    val uuid = try { Option(UUID.fromString(id)) } catch {
      case e: Throwable =>
        log.error("unable to load view with id " + id)
        None
    }
    val availability = element.eGet[java.lang.Boolean](getFieldIDAvailability).map(_.get)
    val description = element.eGet[String](getFieldIDDescription).map(_.get)
    val name = element.eGet[String](getFieldIDName).map(_.get)
    var next = true
    val rulesRaw = for (i <- 0 until Filter.collectionMaximum if next) yield {
      element.eGet[String](getFieldIDRuleProperty(i)) match {
        case Some(property) =>
          val argument = element.eGet[String](getFieldIDRuleArgument(i)).map(_.get).getOrElse("")
          val filter = element.eGet[String](getFieldIDRuleFilter(i)).flatMap(id =>
            try { Some(UUID.fromString(id.get)) } catch { case e: Throwable => None })
          val not = element.eGet[java.lang.Boolean](getFieldIDRuleNot(i)).map(n =>
            Boolean.unbox(n.get)).getOrElse(false)
          val propertyType = element.eGet[String](getFieldIDRuleType(i)).map(_.get)
          for {
            filter <- filter
            propertyType <- propertyType if (PropertyType.container.isDefinedAt(Symbol(propertyType)))
          } yield api.Filter.Rule(Symbol(property), Symbol(propertyType), not, filter, argument)
        case None =>
          next = false
          None
      }
    }
    val rules = mutable.LinkedHashSet(rulesRaw.flatten: _*)
    for {
      uuid <- uuid if rules.nonEmpty
      availability <- availability
      name <- name
    } yield new Filter(uuid, name, description.getOrElse(""), availability, rules)
  }
  /** Returns an ID for the availability field */
  def getFieldIDAvailability() = 'availability
  /** Returns an ID for the description field */
  def getFieldIDDescription() = 'description
  /** Returns an ID for the label field */
  def getFieldIDName() = 'name
  /** Returns an ID for the rule's argument field */
  def getFieldIDRuleArgument(n: Int) = Symbol(n + "_argument")
  /** Returns an ID for the rule's comparator field */
  def getFieldIDRuleFilter(n: Int) = Symbol(n + "_filter")
  /** Returns an ID for the rule's inverter field */
  def getFieldIDRuleNot(n: Int) = Symbol(n + "_not")
  /** Returns an ID for the rule's property field */
  def getFieldIDRuleProperty(n: Int) = Symbol(n + "_property")
  /** Returns an ID for the rule's type field */
  def getFieldIDRuleType(n: Int) = Symbol(n + "_type")
  /** Get all view filters for the current model. */
  def load(): Set[Filter] = {
    log.debug("load view filter list for model " + Model.eId)
    val result = Filter.container.eChildren.map(Filter.get).flatten.toList.sortBy(_.name)
    if (result.contains(Filter.allowAllFilter)) result.toSet else (Filter.allowAllFilter +: result).toSet
  }
  /** Update only modified view filters */
  def save(filters: Set[api.Filter]) = App.exec {
    log.debug("save view filter list for model " + Model.eId)
    val oldFilters = Data.viewFilters.values
    val newFilters = filters - allowAllFilter
    val deleted = oldFilters.filterNot(oldFilter => newFilters.exists(compareDeep(_, oldFilter)))
    val added = newFilters.filterNot(newFilter => oldFilters.exists(compareDeep(_, newFilter)))
    if (deleted.nonEmpty) {
      log.debug("delete Set(%s)".format(deleted.mkString(", ")))
      deleted.foreach { filter =>
        Data.viewFilters.remove(filter.id)
        remove(filter)
      }
    }
    if (added.nonEmpty) {
      log.debug("add Set(%s)".format(added.mkString(", ")))
      added.foreach { filter =>
        set(filter)
        Data.viewFilters(filter.id) = filter
      }
    }
  }
  /** Save filter to element */
  def set(filter: api.Filter, container: Element.Generic = container) = {
    val element = Record(container, filter.elementId, Coordinate.root.coordinate)
    // remove all element properties
    element.eStash.property.clear
    // set element's properties
    element.eSet[java.lang.Boolean](getFieldIDAvailability, filter.availability)
    element.eSet[String](getFieldIDName, filter.name)
    element.eSet[String](getFieldIDDescription, filter.description, "")
    if (filter.rules.size > Filter.collectionMaximum)
      log.error("%s definition sequence is too long, %d elements will be dropped".format(filter, filter.rules.size - Filter.collectionMaximum))
    val iterator = filter.rules.toIterator
    for {
      i <- 0 until math.min(filter.rules.size, Filter.collectionMaximum)
      rule = iterator.next
    } {
      element.eSet[String](getFieldIDRuleArgument(i), rule.argument)
      element.eSet[String](getFieldIDRuleFilter(i), rule.filter.toString)
      element.eSet[java.lang.Boolean](getFieldIDRuleNot(i), rule.not)
      element.eSet[String](getFieldIDRuleProperty(i), rule.property.name)
      element.eSet[String](getFieldIDRuleType(i), rule.propertyType.name)
    }
  }
  /** Remove filter element */
  def remove(filter: api.Filter, container: Element.Generic = container) {
    container.eChildren.find(_.eId == filter.elementId).foreach(element => container.eChildren -= element)
  }

  /**
   * Dependency injection routines
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    org.digimead.digi.lib.DependencyInjection.assertDynamic[Record.Interface[_ <: Record.Stash]]("eViewFilter")
    /** Get or create dynamically eViewFilter container inside current active model. */
    def definition = inject[Record.Interface[_ <: Record.Stash]]("eViewFilter")
  }
}
