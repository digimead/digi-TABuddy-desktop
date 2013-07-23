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

import org.digimead.tabuddy.model.element.Element

/**
 * Base class of the handler for the property of the particular type
 * The equality is based on id: Symbol
 */
trait PropertyType[T <: AnyRef with java.io.Serializable] extends Equals {
  /** The property that determines that enumeration is supported */
  val enumerationSupported: Boolean
  /** The property type name */
  val id: Symbol
  /** The type class */
  val typeClass: Class[T]
  /** The type symbol */
  val typeSymbol: Symbol
  /** The property that contains an adapter for the given type */
  def adapter(): PropertyType.Adapter[T]

  /**
   * Result of comparing 'value1' with 'value2'.
   * returns `x' where
   * x < 0 iff value1 < value2
   * x == 0 iff value1 == value2
   * x > 0 iff value1 > value2
   */
  def compare(value1: T, value2: T): Int
  /** Create an editor for the given type */
  def createEditor(initial: Option[T], propertyId: Symbol, element: Element.Generic): PropertyType.Editor[T]
  /** Returns the new value */
  def createValue: T
  /** Returns an iterator for the new value generation */
  def createValues: Iterator[T]
  /** Create a viewer for the given type */
  def createViewer(initial: Option[T], propertyId: Symbol, element: Element.Generic): PropertyType.Viewer[T]
  /** Get name of the ptype from the type schema */
  def name: String
  /** Convert value to string */
  def valueToString(value: T): String
  /** Convert string to value */
  def valueFromString(value: String): T
}

object PropertyType {
  /**
   * Element property adapter
   */
  trait Adapter[T <: AnyRef with java.io.Serializable]
  /**
   * Element property trait that provides an editor widget
   */
  trait Editor[T <: AnyRef with java.io.Serializable] extends Viewer[T]
  /**
   * Element property trait that provides a viewer widget
   */
  trait Viewer[T <: AnyRef with java.io.Serializable]
}
