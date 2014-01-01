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

package org.digimead.tabuddy.desktop.logic.payload

import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.tabuddy.model.{ Model, Record }
import org.digimead.tabuddy.model.graph.Graph

/**
 * List of predefined elements.
 *
 * TABuddy - global TA Buddy space
 *  +-Settings - global TA Buddy settings
 *     +-Templates - global TA Buddy element templates
 *     +-Enumerations
 *     +-...
 *  +-Temp - temporary TA Buddy elements
 *     +-Templates - predefined TA Buddy element templates
 */
object PredefinedElements {
  /** Get a graph application container. */
  def eTABuddy(graph: Graph[_ <: Model.Like]): Record.Relative[_ <: Record.Like] =
    PredefinedElements.DI.inject[Graph[_ <: Model.Like] ⇒ Record.Like]("eTABuddy").apply(graph).eRelative
  /** Get a graph settings container. */
  def eSettings(graph: Graph[_ <: Model.Like]): Record.Relative[_ <: Record.Like] =
    PredefinedElements.DI.inject[Graph[_ <: Model.Like] ⇒ Record.Like]("eSettings").apply(graph).eRelative
  /** Get a graph enumerations container. */
  def eEnumeration(graph: Graph[_ <: Model.Like]): Record.Relative[_ <: Record.Like] =
    PredefinedElements.DI.inject[Graph[_ <: Model.Like] ⇒ Record.Like]("eEnumeration").apply(graph).eRelative
  /** Get a graph element templates container. */
  def eElementTemplate(graph: Graph[_ <: Model.Like]): Record.Relative[_ <: Record.Like] =
    PredefinedElements.DI.inject[Graph[_ <: Model.Like] ⇒ Record.Like]("eElementTemplate").apply(graph).eRelative
  /** Get a graph element templates container with unmodified templates. */
  def eElementTemplateOriginal(graph: Graph[_ <: Model.Like]): Record.Relative[_ <: Record.Like] =
    PredefinedElements.DI.inject[Graph[_ <: Model.Like] ⇒ Record.Like]("eElementTemplateOriginal").apply(graph).eRelative
  /** Get a graph element templates container with modified templates. */
  def eElementTemplateUser(graph: Graph[_ <: Model.Like]): Record.Relative[_ <: Record.Like] =
    PredefinedElements.DI.inject[Graph[_ <: Model.Like] ⇒ Record.Like]("eElementTemplateUser").apply(graph).eRelative
  /** Get a graph temporary elements container. */
  def eTemporary(graph: Graph[_ <: Model.Like]): Record.Relative[_ <: Record.Like] =
    PredefinedElements.DI.inject[Graph[_ <: Model.Like] ⇒ Record.Like]("eTemporary").apply(graph).eRelative
  /** Get a graph temporary element templates container. */
  def eTemporaryElementTemplate(graph: Graph[_ <: Model.Like]): Record.Relative[_ <: Record.Like] =
    PredefinedElements.DI.inject[Graph[_ <: Model.Like] ⇒ Record.Like]("eTemporaryTemplate").apply(graph).eRelative
  /** Get a graph view modificator elements container. */
  def eView(graph: Graph[_ <: Model.Like]): Record.Relative[_ <: Record.Like] =
    PredefinedElements.DI.inject[Graph[_ <: Model.Like] ⇒ Record.Like]("eView").apply(graph).eRelative
  /** Get a graph view definitions container. */
  def eViewDefinition(graph: Graph[_ <: Model.Like]): Record.Relative[_ <: Record.Like] =
    PredefinedElements.DI.inject[Graph[_ <: Model.Like] ⇒ Record.Like]("eViewDefinition").apply(graph).eRelative
  /** Get a graph view sortings container. */
  def eViewSorting(graph: Graph[_ <: Model.Like]): Record.Relative[_ <: Record.Like] =
    PredefinedElements.DI.inject[Graph[_ <: Model.Like] ⇒ Record.Like]("eViewSorting").apply(graph).eRelative
  /** Get a graph view filters container. */
  def eViewFilter(graph: Graph[_ <: Model.Like]): Record.Relative[_ <: Record.Like] =
    PredefinedElements.DI.inject[Graph[_ <: Model.Like] ⇒ Record.Like]("eViewFilter").apply(graph).eRelative
  /**
   * Dependency injection routines
   */
  private object DI extends DependencyInjection.PersistentInjectable
}
