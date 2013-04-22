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

package org.digimead.tabuddy.desktop.support

import org.digimead.digi.lib.DependencyInjection

import com.escalatesoft.subcut.inject.BindingModule

object Timeout {
  val shortest = DI.shortest
  val short = DI.short
  val normal = DI.normal
  val long = DI.long
  val longer = DI.longer
  val longest = DI.longest

  /**
   * Dependency injection routines
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    implicit def bindingModule = DependencyInjection()
    @volatile var shortest = injectOptional[Int]("Timeout.Shortest") getOrElse 1000
    @volatile var short = injectOptional[Int]("Timeout.Short") getOrElse 5000
    @volatile var normal = injectOptional[Int]("Timeout.Normal") getOrElse 10000
    @volatile var long = injectOptional[Int]("Timeout.Long") getOrElse 20000
    @volatile var longer = injectOptional[Int]("Timeout.Longer") getOrElse 60000
    @volatile var longest = injectOptional[Int]("Timeout.Longest") getOrElse 5 * 60000

    override def injectionAfter(newModule: BindingModule) {
      injectOptional[Int]("Timeout.Shortest") foreach (shortest = _)
      injectOptional[Int]("Timeout.Short") foreach (short = _)
      injectOptional[Int]("Timeout.Normal") foreach (normal = _)
      injectOptional[Int]("Timeout.Long") foreach (long = _)
      injectOptional[Int]("Timeout.Longer") foreach (longer = _)
      injectOptional[Int]("Timeout.Longest") foreach (longest = _)
    }
  }
}
