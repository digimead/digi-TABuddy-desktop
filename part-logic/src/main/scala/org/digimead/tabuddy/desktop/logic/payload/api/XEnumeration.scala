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

package org.digimead.tabuddy.desktop.logic.payload.api

import org.digimead.tabuddy.model.element.Element

/**
 * The base enumeration interface.
 * The equality is based on the element reference.
 */
trait XEnumeration[A <: AnyRef with java.io.Serializable, B <: XPropertyType[A], C <: XTemplateProperty[A, B], D <: XEnumeration.Constant[A, B]] extends Equals {
  /** Availability flag for user (some enumeration may exists, but not involved in new element creation). */
  val availability: Boolean
  /** The sequence of enumeration constants. */
  val constants: Set[D]
  /** The enumeration element. */
  val element: Element#RelativeType
  /** The enumeration id/name. */
  val id: Symbol
  /** The enumeration name. */
  val name: String
  /** The type wrapper. */
  val ptype: B

  /** The copy constructor. */
  def copy(availability: Boolean = this.availability,
    constants: Set[D] = this.constants,
    element: Element#RelativeType = this.element,
    id: Symbol = this.id,
    name: String = this.name,
    ptype: B = this.ptype): this.type
  /** Get the specific constant for the property or the first entry. */
  def getConstantSafe(property: C): D
  /** Get the specific constant for the value or the first entry. */
  def getConstantSafe(value: A): D
  /** Returns an identificator for the availability field. */
  def getFieldIDAvailability(): Symbol
  /** Returns an identificator for the name field. */
  def getFieldIDName(): Symbol
  /** Returns an identificator for the type wrapper field. */
  def getFieldIDType(): Symbol
  /** Returns an identificator for the value field of the enumeration constant. */
  def getFieldIDConstantValue(n: Int): Symbol
  /** Returns an identificator for the alias field of the enumeration constant. */
  def getFieldIDConstantAlias(n: Int): Symbol
  /** Returns an identificator for the description field of the enumeration constant. */
  def getFieldIDConstantDescription(n: Int): Symbol
}

object XEnumeration {
  type Generic = XEnumeration[_ <: AnyRef with java.io.Serializable, _ <: XPropertyType[_ <: AnyRef with java.io.Serializable],
    _ <: XTemplateProperty[_ <: AnyRef with java.io.Serializable, _ <: XPropertyType[_ <: AnyRef with java.io.Serializable]],
    _ <: XEnumeration.Constant[_, _ <: XPropertyType[_ <: AnyRef with java.io.Serializable]]]
  /**
   * The enumeration constant class.
   * The equality is based on the constant value.
   */
  trait Constant[A <: AnyRef with java.io.Serializable, B <: XPropertyType[A]] extends Equals {
    val value: A
    val alias: String
    val description: String
    val ptype: B
    val m: Manifest[A]
    /** The enumeration constant user's representation */
    val view: String
  }
}
