/**
 * This file is part of the TABuddy project.
 * Copyright (c) 2012-2013 Alexey Aksenov ezh@ezh.msk.ru
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

package org.digimead.tabuddy.desktop.payload

class TemplateProperty[T <: AnyRef with java.io.Serializable](
  /** Property name */
  val id: Symbol,
  /** Is property required */
  val required: Boolean,
  /** The property that representing an attached enumeration if any */
  val enumeration: Option[Symbol],
  /** The property that representing a type from the UI point of view */
  val ptype: PropertyType[T],
  /** The default value */
  val defaultValue: Option[T] = None)(implicit m: Manifest[T]) extends TemplateProperty.Interface[T] {
  /** The copy constructor */
  def copy(defaultValue: Option[T] = this.defaultValue,
    enumeration: Option[Symbol] = this.enumeration,
    ptype: PropertyType[T] = this.ptype,
    id: Symbol = this.id,
    required: Boolean = this.required) =
    new TemplateProperty[T](id, required, enumeration, ptype, defaultValue).asInstanceOf[this.type]
}

/**
 * Companion object that contain template property interface
 */
object TemplateProperty {
  /** The deep comparison of two template properties */
  def compareDeep(a: Interface[_ <: AnyRef with java.io.Serializable], b: Interface[_ <: AnyRef with java.io.Serializable]): Boolean =
    (a eq b) || (a == b && a.ptype == b.ptype && a.defaultValue == b.defaultValue && a.enumeration == b.enumeration && a.required == b.required)

  /**
   * the model.dsl.DSLType from the application point of view
   */
  trait Interface[T <: AnyRef with java.io.Serializable] {
    /** The default value */
    val defaultValue: Option[T]
    /** The property that representing an attached enumeration if any */
    val enumeration: Option[Symbol]
    /** The property that representing a type from the UI point of view */
    val ptype: PropertyType[T]
    /** The property name */
    val id: Symbol
    /** Is the property required */
    val required: Boolean

    /** The copy constructor */
    def copy(defaultValue: Option[T] = this.defaultValue,
      enumeration: Option[Symbol] = this.enumeration,
      ptype: PropertyType[T] = this.ptype,
      id: Symbol = this.id,
      required: Boolean = this.required): this.type

    def canEqual(other: Any) =
      other.isInstanceOf[org.digimead.tabuddy.desktop.payload.TemplateProperty.Interface[T]]
    override def equals(other: Any) = other match {
      case that: org.digimead.tabuddy.desktop.payload.TemplateProperty.Interface[T] =>
        (this eq that) || {
          that.canEqual(this) &&
            defaultValue == that.defaultValue &&
            enumeration == that.enumeration &&
            id == that.id &&
            required == that.required &&
            ptype.typeSymbol == ptype.typeSymbol
        }
      case _ => false
    }
    override def hashCode() = {
      /*
       * Of the remaining four, I'd probably select P(31), as it's the cheapest to calculate on a
       * RISC machine (because 31 is the difference of two powers of two). P(33) is
       * similarly cheap to calculate, but it's performance is marginally worse, and
       * 33 is composite, which makes me a bit nervous.
       */
      val p = 31
      p * (p * (p * (p * (p +
        ptype.typeSymbol.hashCode) + enumeration.hashCode()) + required.hashCode) + id.hashCode) + defaultValue.hashCode
    }
    override def toString() = "TemplateProperty[%s](%s)".format(ptype.id.name, id.name)
  }
}
