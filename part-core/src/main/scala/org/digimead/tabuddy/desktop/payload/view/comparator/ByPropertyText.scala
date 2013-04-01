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

package org.digimead.tabuddy.desktop.payload.view.comparator

import java.util.UUID

import org.digimead.digi.lib.log.Loggable
import org.digimead.tabuddy.desktop.payload.PropertyType
import org.digimead.tabuddy.model.element.Element

class ByPropertyText extends Comparator.Interface[Comparator.Argument] with Loggable {
  val id = UUID.fromString("84b24863-145a-40a7-aade-e25547c52b41")
  val name = "By property text"
  val description = "Compare two element's properties via text representation"
  val isArgumentSupported = false

  /** Convert Argument trait to the serialized string */
  def argumentToString(argument: Comparator.Argument): String = ""
  /** Convert Argument trait to the text representation for the user */
  def argumentToText(argument: Comparator.Argument): String = ""
  /** Check whether comparation is available */
  def canCompare(clazz: Class[_ <: AnyRef with java.io.Serializable]): Boolean = true
  /** Compare two element's properties */
  def compare[T <: AnyRef with java.io.Serializable](propertyId: Symbol, ptype: PropertyType[T], e1: Element.Generic, e2: Element.Generic, argument: Option[Comparator.Argument]): Int = {
    val text1 = e1.eGet(propertyId, ptype.typeSymbol).map(value => ptype.valueToString(value.get.asInstanceOf[T])).getOrElse("").trim
    val text2 = e2.eGet(propertyId, ptype.typeSymbol).map(value => ptype.valueToString(value.get.asInstanceOf[T])).getOrElse("").trim
    text1.compareTo(text2)
  }
  /** Convert the serialized argument to Argument trait */
  def stringToArgument(argument: String): Option[Comparator.Argument] = None
}

object ByPropertyText extends ByPropertyText
