/**
 * This file is part of the TA Buddy project.
 * Copyright (c) 2015 Alexey Aksenov ezh@ezh.msk.ru
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

package org.digimead.tabuddy.desktop.logic.comparator

import java.util.UUID
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.logic.comparator.api.XComparator
import org.digimead.tabuddy.desktop.logic.payload.{ PropertyType, TemplateProperty }
import org.digimead.tabuddy.model.element.Element

class ByPropertyNumeric extends XComparator[XComparator.Argument] with XLoggable {
  type ComparatorTemplateProperty[T <: AnyRef with java.io.Serializable] = TemplateProperty[T]
  type ComparatorPropertyType[T <: AnyRef with java.io.Serializable] = PropertyType[T]
  val id = UUID.fromString("f4705a6b-feff-4919-b723-214bcb6d2b9d")
  val name = "By numeric property"
  val description = "Compare two element's properties at numeric field"
  val isArgumentSupported = false

  /** Convert Argument trait to the serialized string */
  def argumentToString(argument: XComparator.Argument): String = ""
  /** Convert Argument trait to the text representation for the user */
  def argumentToText(argument: XComparator.Argument): String = ""
  /** Check whether comparation is available */
  def canCompare(clazz: Class[_ <: AnyRef with java.io.Serializable]): Boolean =
    clazz.isInstanceOf[java.lang.Byte] || clazz.isInstanceOf[java.lang.Double] || clazz.isInstanceOf[java.lang.Float] ||
      clazz.isInstanceOf[java.lang.Integer] || clazz.isInstanceOf[java.lang.Long] || clazz.isInstanceOf[java.lang.Short]
  /** Compare two element's properties */
  def compare[T <: AnyRef with java.io.Serializable](propertyId: Symbol, ptype: PropertyType[T], e1: Element, e2: Element, argument: Option[XComparator.Argument]): Int =
    ptype.typeSymbol match {
      case 'Byte ⇒
        (e1.eGet[java.lang.Byte](propertyId).map(_.get()), e2.eGet[java.lang.Byte](propertyId)) match {
          case (Some(n1), Some(n2)) ⇒ n1.compareTo(n2)
          case (None, Some(_)) ⇒ -1
          case (Some(_), None) ⇒ 1
          case (None, None) ⇒ 0
        }
      case 'Double ⇒
        (e1.eGet[java.lang.Double](propertyId).map(_.get()), e2.eGet[java.lang.Double](propertyId)) match {
          case (Some(n1), Some(n2)) ⇒ n1.compareTo(n2)
          case (None, Some(_)) ⇒ -1
          case (Some(_), None) ⇒ 1
          case (None, None) ⇒ 0
        }
      case 'Float ⇒
        (e1.eGet[java.lang.Float](propertyId).map(_.get()), e2.eGet[java.lang.Float](propertyId)) match {
          case (Some(n1), Some(n2)) ⇒ n1.compareTo(n2)
          case (None, Some(_)) ⇒ -1
          case (Some(_), None) ⇒ 1
          case (None, None) ⇒ 0
        }
      case 'Integer ⇒
        (e1.eGet[java.lang.Integer](propertyId).map(_.get()), e2.eGet[java.lang.Integer](propertyId)) match {
          case (Some(n1), Some(n2)) ⇒ n1.compareTo(n2)
          case (None, Some(_)) ⇒ -1
          case (Some(_), None) ⇒ 1
          case (None, None) ⇒ 0
        }
      case 'Long ⇒
        (e1.eGet[java.lang.Long](propertyId).map(_.get()), e2.eGet[java.lang.Long](propertyId)) match {
          case (Some(n1), Some(n2)) ⇒ n1.compareTo(n2)
          case (None, Some(_)) ⇒ -1
          case (Some(_), None) ⇒ 1
          case (None, None) ⇒ 0
        }
      case 'Short ⇒
        (e1.eGet[java.lang.Short](propertyId).map(_.get()), e2.eGet[java.lang.Short](propertyId)) match {
          case (Some(n1), Some(n2)) ⇒ n1.compareTo(n2)
          case (None, Some(n)) ⇒ -1
          case (Some(n), None) ⇒ 1
          case (None, None) ⇒ 0
        }
      case _ ⇒
        throw new IllegalArgumentException("Unable to compare unsupported properties with type " + ptype)
    }
  /** Convert the serialized argument to Argument trait */
  def stringToArgument(argument: String): Option[XComparator.Argument] = None
}

object ByPropertyNumeric extends ByPropertyNumeric
