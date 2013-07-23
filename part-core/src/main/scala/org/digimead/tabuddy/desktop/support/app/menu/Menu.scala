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

package org.digimead.tabuddy.desktop.support.app.menu

import scala.collection.IterableLike
import scala.collection.mutable.LazyBuilder
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.support.app.MenuItemTemplate
import org.digimead.tabuddy.desktop.support.app.MenuTemplate
import language.implicitConversions
import org.eclipse.jface.action.MenuManager
import org.eclipse.swt.widgets.MenuItem
import org.digimead.tabuddy.desktop.support.App
import org.eclipse.jface.action.ActionContributionItem

/** Base class for menu. */
abstract class Menu[A](
  /** Original menu element. */
  override val element: A) extends Base[A] with Iterable[Base.Uniform[_]] with IterableLike[Base.Uniform[_], Menu[_]] with Loggable {
  override protected def newBuilder = new Menu.MenuBuilder
  /** Contains uniform menu element. */
  val uniform: Menu.Uniform[A]

  /** Adjust exists menu with template values */
  def adjust[T](template: MenuTemplate)(f: Menu[_] => T)
  /** Create menu child. */
  def create[T](template: MenuTemplate)(f: Menu[_] => T)
  /** Create menu item child. */
  def create[T](template: MenuItemTemplate)(f: Item[_] => T)
  /** Create or adjust menu item entity. */
  @log
  def createSmart[T](template: MenuTemplate)(f: Menu[_] => T): Unit =
    find(child => child.isInstanceOf[Menu.Uniform[_]] && compareLabels(child, template)) match {
      case Some(child: Menu.Uniform[_]) => // exists
        child.wrapper.adjust(template)(f)
      case Some(other) =>
        throw new IllegalStateException(s"Unable to process $other as menu.")
      case None => // not exists
        create(template)(f)
    }
  /** Create or adjust menu entity. */
  @log
  def createSmart[T](template: MenuItemTemplate)(f: Item[_] => T): Unit =
    find(child => child.isInstanceOf[Item.Uniform[_]] && compareLabels(child, template)) match {
      case Some(child: Item.Uniform[_]) => // exists
        child.wrapper.adjust(template)(f)
      case Some(other) =>
        throw new IllegalStateException(s"Unable to process $other as item.")
      case None => // not exists
        create(template)(f)
    }
  /** Menu iterator. */
  def iterator = uniform.children.iterator.map { case child: Base[_] => child.uniform }

  /** Adjust exists menu with template values within the UI thread. */
  protected def adjust[T](menu: Menu[_], menuManager: MenuManager, template: MenuTemplate): Option[Menu[_]]
  /** Create menu child within the UI thread. */
  @log
  protected def create[T](menuManager: MenuManager, template: MenuTemplate): Option[Menu[_]] = {
    log.debug(s"Create submenu from ${template} for '${this}'.")
    App.assertThread()
    template.insertInto(menuManager).map {
      case contribution: MenuManager =>
        new JFaceMenu(contribution)
    }
  }
  /** Create menu item child within the UI thread. */
  @log
  protected def create[T](menuManager: MenuManager, template: MenuItemTemplate): Option[Item[_]] = {
    log.debug(s"Create item from ${template} for '${this}'.")
    App.assertThread()
    template.insertInto(menuManager).map {
      case action: ActionContributionItem =>
        new JFaceAItem(action)
      case contribution =>
        new JFaceCItem(contribution)
    }
  }
}

object Menu {
  class MenuBuilder extends LazyBuilder[Base.Uniform[_], Menu[_]] {
    def result(): Menu[_] = {
      val coll = parts.toList.flatten
      new CompositeMenu(coll)
    }
  }
  trait Uniform[A] extends Base.Uniform[A] {
    /** Menu wrapper. */
    val wrapper: Menu[A]

    /**
     * Menu children.
     */
    def children: Seq[Base[_]]
  }
  object Uniform {
    implicit def uniform2wrapper[T](u: Uniform[T]) = u.wrapper
  }
}
