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

package org.digimead.tabuddy.desktop.logic.filter

import java.util.UUID
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.logic.filter.api.XFilter
import org.digimead.tabuddy.desktop.logic.payload.{ PropertyType, TemplateProperty }
import org.digimead.tabuddy.model.element.Element

class ByPropertyText extends XFilter[ByPropertyTextArgument] with XLoggable {
  type FilterTemplateProperty[T <: AnyRef with java.io.Serializable] = TemplateProperty[T]
  type FilterPropertyType[T <: AnyRef with java.io.Serializable] = PropertyType[T]
  val id = UUID.fromString("74db4f4c-261c-443c-b014-fae7d864357b")
  val name = "By property text"
  val description = "Compare two element's properties via text representation"
  val isArgumentSupported = true

  /** Convert Argument trait to the serialized string */
  def argumentToString(argument: ByPropertyTextArgument): String = argument.value
  /** Convert Argument trait to the text representation for the user */
  def argumentToText(argument: ByPropertyTextArgument): String = argument.value
  /** Check whether filtering is available */
  def canFilter(clazz: Class[_ <: AnyRef with java.io.Serializable]): Boolean = true
  /** Filter element property */
  def filter[A <: AnyRef with java.io.Serializable](propertyId: Symbol, ptype: PropertyType[A], e: Element, argument: Option[ByPropertyTextArgument]): Boolean =
    argument match {
      case Some(argument) ⇒
        val text = e.eGet(propertyId, ptype.typeSymbol).map(value ⇒ ptype.valueToString(value.get.asInstanceOf[A])).getOrElse("").trim
        text.toLowerCase().contains(argument.value.toLowerCase())
      case None ⇒
        log.warn("argument is absent")
        true
    }
  /** Convert the serialized argument to Argument trait */
  def stringToArgument(argument: String): Option[ByPropertyTextArgument] = Some(ByPropertyTextArgument(argument.trim()))
}

sealed case class ByPropertyTextArgument(val value: String) extends XFilter.Argument

object ByPropertyText extends ByPropertyText
