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

package org.digimead.tabuddy.desktop.logic.filter.api

import java.util.UUID
import org.digimead.tabuddy.desktop.logic.payload.api.{ XPropertyType, XTemplateProperty }
import org.digimead.tabuddy.model.element.Element

/** The Base interface of the model filter */
trait XFilter[T <: XFilter.Argument] {
  /** The comparator identificator */
  val id: UUID
  /** The comparator name */
  val name: String
  /** The comparator description */
  val description: String
  /** The flag determines whether or not the comparator uses an argument */
  val isArgumentSupported: Boolean

  /** Convert Argument instance to the serialized string */
  def argumentToString(argument: T): String
  /** Convert Argument instance to the text representation for the user */
  def argumentToText(argument: T): String
  /** Check whether filtering is available */
  def canFilter(clazz: Class[_ <: AnyRef with java.io.Serializable]): Boolean
  /** Filter element property */
  def filter[U <: AnyRef with java.io.Serializable](property: XTemplateProperty[U], e: Element, argument: Option[T]): Boolean =
    filter(property.id, property.ptype, e, argument)
  /** Filter element property */
  def filter[U <: AnyRef with java.io.Serializable](propertyId: Symbol, ptype: XPropertyType[U], e: Element, argument: Option[T]): Boolean
  /** Returns the generic type filter */
  def generic = this.asInstanceOf[XFilter[XFilter.Argument]]
  /** Convert the serialized argument to Argument instance */
  def stringToArgument(argument: String): Option[T]
  /** Convert the serialized argument to the text representation for the user */
  def stringToText(argument: String): Option[String] = stringToArgument(argument).map(argumentToText)
}

object XFilter {
  /** Contains comparator options */
  trait Argument
}
