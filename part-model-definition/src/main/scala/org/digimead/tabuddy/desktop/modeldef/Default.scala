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

package org.digimead.tabuddy.desktop.modeldef

import org.digimead.digi.lib.api.DependencyInjection
import org.eclipse.swt.graphics.Point

object Default {
  /** Ascending sort constant */
  val ASCENDING = DI.ASCENDING
  /** Descending sort constant */
  val DESCENDING = DI.DESCENDING
  /** Auto resize column padding */
  val columnPadding = DI.columnPadding
  /** Default sort direction */
  val sortingDirection = DI.sortingDirection
  /**
   * Return the amount of pixels in x and y direction you want the tool tip to
   * pop up from the mouse pointer. The default shift is 10px right and 0px
   * below your mouse cursor. Be aware of the fact that you should at least
   * position the tool tip 1px right to your mouse cursor else click events
   * may not get propagated properly.
   */
  val toolTipShift = DI.toolTipShift
  /** The time in milliseconds until the tool tip is displayed. */
  val toolTipDisplayDelayTime = DI.toolTipDisplayDelayTime
  /** The time in milliseconds the tool tip is shown for. */
  val toolTipTimeDisplayed = DI.toolTipTimeDisplayed
  /**
   * Dependency injection routines.
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** Ascending sort constant */
    lazy val ASCENDING = injectOptional[Boolean]("Editor.Sorting.ASCENDING") getOrElse false
    /** Descending sort constant */
    lazy val DESCENDING = injectOptional[Boolean]("Editor.Sorting.DESCENDING") getOrElse true
    /** Auto resize column padding */
    lazy val columnPadding = injectOptional[Int]("Editor.columnPadding") getOrElse 10
    /** Default sort direction */
    lazy val sortingDirection = injectOptional[Boolean]("Editor.Sorting.Direction") getOrElse Default.ASCENDING
    /**
     * Return the amount of pixels in x and y direction you want the tool tip to
     * pop up from the mouse pointer. The default shift is 10px right and 0px
     * below your mouse cursor. Be aware of the fact that you should at least
     * position the tool tip 1px right to your mouse cursor else click events
     * may not get propagated properly.
     */
    lazy val toolTipShift = injectOptional[Point]("Editor.ToolTip.Shift") getOrElse new Point(5, 5)
    /** The time in milliseconds until the tool tip is displayed. */
    lazy val toolTipDisplayDelayTime = injectOptional[Int]("Editor.ToolTip.DisplayDelayTime") getOrElse 100 //msec
    /** The time in milliseconds the tool tip is shown for. */
    lazy val toolTipTimeDisplayed = injectOptional[Int]("Editor.ToolTip.TimeDisplayed") getOrElse 5000 //msec
  }
}
