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

package org.digimead.tabuddy.desktop.support.app

import scala.collection.IterableLike
import scala.collection.mutable.LazyBuilder

import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.support.app.menu.Base
import org.eclipse.e4.ui.model.application.ui.menu.MMenu

/*class MenuRich(val element: Base) extends Iterable[Base] with IterableLike[Base, MenuRich] with Loggable {
  def iterator = element.children.iterator
  override protected def newBuilder = new MenuRich.MenuRichBuilder

  /** Get or create menu based on template. */
  def getOrCreate(template: MenuTemplate): MenuRich = {
    /*mmenu match {
  }
    case parent: MMenu =>
    case element =>
      throw new UnsupportedOperationException("Unable to create submenu for unknown element " + element)*/
    null
  }

  /** Adjust exists menu with templates values */
  @log
  def adjust(template: MenuTemplate) {

  }
  @log
  def create(template: MenuTemplate): menu.Base = {
    log.debug("Create new menu for " + template)
    log.___glance("CREATE")
    null
  }
  /*
  menu match {
    case parent: MMenu =>
      parent.getChildren().find(c => Option(c.getLabel()).getOrElse("").filterNot(_ == '&').toLowerCase() == templateLabel) match {
        case Some(child) =>
          // exists
          val rich = new MenuRich(child)
          rich.adjust(template)
          f(rich)
        case None =>
          val rich = create(template)
          // not exists
          null.asInstanceOf[T]
      }
    case element =>
      throw new UnsupportedOperationException("Unable to create submenu for unknown element " + element)
  }*/
  def get(menu: MenuTemplate): Option[MenuRich] = {
    None
  }
  def remove() {

  }

}

object MenuRich {
  class MenuRichBuilder extends LazyBuilder[Base, MenuRich] {
    def result(): MenuRich = {
      val coll = parts.toList.flatten
      new Composite(new menu.CompositeMenu(coll))
    }
  }

  def apply(m: menu.Base): MenuRich = new MenuRich(m)
  def apply(m: MMenu): MenuRich = new MenuRich(new menu.E4Menu(m))

  class Composite(children: Base) extends MenuRich(children)
}
*/