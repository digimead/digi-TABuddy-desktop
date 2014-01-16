/**
 * This file is part of the TA Buddy project.
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
 * The base enumeration interface
 * The equality is based on element reference
 */
trait Enumeration[T <: AnyRef with java.io.Serializable] extends Equals {
  /** Availability flag for user (some enumeration may exists, but not involved in new element creation). */
  val availability: Boolean
  /** The enumeration name. */
  val name: String
  /** The enumeration element. */
  val element: Element
  /** The enumeration id/name. */
  val id: Symbol
  /** The type wrapper. */
  val ptype: PropertyType[T]
  /**
   * Sequence of enumeration constants.
   */
  val constants: Set[Enumeration.Constant[T]]

  /** Convert enumeration to common type. */
  def **(): Enumeration[_ <: AnyRef with java.io.Serializable] = this.asInstanceOf[Enumeration[_ <: AnyRef with java.io.Serializable]]
  /** The copy constructor. */
  def copy(availability: Boolean = this.availability,
    name: String = this.name,
    element: Element = this.element,
    ptype: PropertyType[T] = this.ptype,
    id: Symbol = this.id,
    constants: Set[Enumeration.Constant[T]] = this.constants): this.type
  /** Get the specific constant for the property or the first entry. */
  def getConstantSafe(property: TemplateProperty[T]): Enumeration.Constant[T]
  /** Get the specific constant for the value or the first entry. */
  def getConstantSafe(value: T): Enumeration.Constant[T]
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

object Enumeration {
  /**
   * The enumeration constant class
   * The equality is based on constant value
   */
  trait Constant[T <: AnyRef with java.io.Serializable] extends Equals {
    val value: T
    val alias: String
    val description: String
    val ptype: PropertyType[T]
    val m: Manifest[T]
    /** The enumeration constant user's representation */
    val view: String
  }
}
