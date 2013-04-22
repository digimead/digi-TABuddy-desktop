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

package org.digimead.tabuddy.desktop.payload.view.filter

import java.util.UUID

import scala.collection.immutable

import org.digimead.digi.lib.DependencyInjection
import org.digimead.digi.lib.log.Loggable
import org.digimead.tabuddy.desktop.payload.PropertyType
import org.digimead.tabuddy.desktop.payload.TemplateProperty
import org.digimead.tabuddy.model.element.Element

import com.escalatesoft.subcut.inject.BindingModule

object Filter extends DependencyInjection.PersistentInjectable with Loggable {
  implicit def bindingModule = DependencyInjection()
  @volatile private var defaultFilter: Filter.Interface[_ <: Filter.Argument] = _

  def default = defaultFilter

  /*
   * dependency injection
   */
  /** Predefined filters that are available for this application */
  def map = inject[immutable.HashMap[UUID, Filter.Interface[_ <: Filter.Argument]]]
  override def injectionAfter(newModule: BindingModule) {
    defaultFilter = map(inject[UUID]("Filter.Default"))
  }
  override def injectionBefore(newModule: BindingModule) {
    DependencyInjection.assertLazy[immutable.HashMap[UUID, Filter.Interface[_ <: Filter.Argument]]](None, newModule)
  }

  /** The Base interface of the filter */
  trait Interface[Q <: Argument] {
    /** The comparator identificator */
    val id: UUID
    /** The comparator name */
    val name: String
    /** The comparator description */
    val description: String
    /** The flag determines whether or not the comparator uses an argument */
    val isArgumentSupported: Boolean

    /** Convert Argument instance to the serialized string */
    def argumentToString(argument: Q): String
    /** Convert Argument instance to the text representation for the user */
    def argumentToText(argument: Q): String
    /** Check whether filtering is available */
    def canFilter(clazz: Class[_ <: AnyRef with java.io.Serializable]): Boolean
    /** Filter element property */
    def filter[U <: AnyRef with java.io.Serializable](property: TemplateProperty[U], e: Element.Generic, argument: Option[Q]): Boolean =
      filter(property.id, property.ptype, e, argument)
    /** Filter element property */
    def filter[U <: AnyRef with java.io.Serializable](propertyId: Symbol, ptype: PropertyType[U], e: Element.Generic, argument: Option[Q]): Boolean
    /** Returns the generic type filter */
    def generic = this.asInstanceOf[Filter.Interface[Filter.Argument]]
    /** Convert the serialized argument to Argument instance */
    def stringToArgument(argument: String): Option[Argument]
  }
  /** Contains comparator options */
  trait Argument
}
