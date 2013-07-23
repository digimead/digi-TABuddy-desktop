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

import scala.collection.immutable

import org.digimead.tabuddy.model.element.Element

/**
 * model.Element from the application point of view
 */
trait ElementTemplate extends Equals {
  /** Availability flag for user (some template may exists, but not involved in new element creation) */
  val availability: Boolean
  /** The template name */
  val name: String
  /** The template element */
  val element: Element.Generic
  /** The factory for the element that contains template data */
  val factory: (Element.Generic, Symbol, Symbol) => Element.Generic
  /** The template id/name/element scope */
  val id: Symbol
  /**
   * Map of element properties from the model point of view
   * or map of form fields from the application point of view
   * Key - property group, Value - sequence of element properties
   */
  val properties: ElementTemplate.propertyMap

  /** The copy constructor */
  def copy(availability: Boolean,
    name: String = this.name,
    element: Element.Generic = this.element,
    factory: (Element.Generic, Symbol, Symbol) => Element.Generic = this.factory,
    id: Symbol = this.id,
    properties: ElementTemplate.propertyMap = this.properties): this.type
  /** Returns an ID for the availability field */
  def getFieldIDAvailability(): Symbol
  /** Returns an ID for the name field */
  def getFieldIDName(): Symbol
  /** Returns an ID for the sequence of id/type tuples field */
  def getFieldIDProperties(): Symbol
  /** Returns an ID for the property default value field */
  def getFieldIDPropertyDefault(id: Symbol): Symbol
  /** Returns an ID for the property enumeration field */
  def getFieldIDPropertyEnumeration(id: Symbol): Symbol
  /** Returns an ID for the property required field */
  def getFieldIDPropertyRequired(id: Symbol): Symbol
  /** Returns an ID for the property group field */
  def getFieldIDPropertyGroup(id: Symbol): Symbol
  /** Returns a new ElementTemplate with the updated availability */
  def updated(availability: Boolean): this.type
  /** Returns a new ElementTemplate with the updated name */
  def updated(name: String): this.type
  /** Returns a new ElementTemplate with the updated id */
  def updated(id: Symbol): this.type
}

object ElementTemplate {
  type propertyMap = immutable.HashMap[TemplatePropertyGroup, Seq[TemplateProperty[_ <: AnyRef with java.io.Serializable]]]
}
