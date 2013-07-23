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

import org.digimead.tabuddy.desktop.support.app.menu.Base
import org.eclipse.e4.ui.model.application.ui.MExpression
import language.implicitConversions
import org.eclipse.jface.action.MenuManager

/** Menu template container. */
abstract class MenuItemTemplate extends menu.Template[MenuItemTemplate] {
  /**
   * The label to display for this element. If the label is expected to be internationalized
   * then the label may be set to a 'key' value to be used by the translation service.
   */
  val label: String
  /**
   * This field determines whether or not the given UIElement appears in the presentation
   * or whether it should be 'cached' for specialized use. Under normal circumstances
   * this flag should always be 'true'.
   *
   * The MinMaxAddon uses this flag for example when a stack becomes minimized. By
   * setting the flag to false the stack's widget is cleanly removed from the UI but
   * is still 'rendered'. Once the widget has been cached the minimized stack can then
   * display the widget using its own techniques.
   */
  val visible: Boolean = true
  /**
   * This field is provided as a way to inform accessibility screen readers with extra
   * information. The intent is that the reader should 'say' this phrase as well as what
   * it would normally emit given the widget hierarchy.
   */
  val accessibilityPhrase: Option[String] = None
  /**
   * Original menu element.
   */
  lazy val element = this
  /**
   * This field determines whether the associated menu is enabled or not.
   */
  val enabled: Option[Boolean] = None
  /**
   * This field contains a fully qualified URL defining the path to an Image to display
   * for this element.
   */
  val iconURI: Option[String] = None
  /**
   * This field is provided as a way to inform accessibility screen readers with extra
   * information. The intent is that the reader should 'say' this phrase as well as what
   * it would normally emit given the widget hierarchy.
   */
  val localizedAccessibilityPhrase: Option[String] = None
  /**
   * This is a method that will retrieve the internationalized label by using the current
   * value of the label itself and some translation service.
   */
  val localizedLabel: Option[String] = None
  /**
   * This is a method that will return the translated mnemonic for this element.
   */
  val localizedMnemonics: Option[String] = None
  /**
   * This is a method that will retrieve the internationalized tooltip by using the current
   * value of the label itself and some translation service.
   */
  val localizedTooltip: Option[String] = None
  /**
   * This is the character that is interpreted by the platform to allow for easier navigation
   * through menus.
   */
  val mnemonics: Option[String] = None
  /**
   * Tags are a list of Strings that are persistent parts of the UI Model. They can be used to 'refine' a particular
   * model element, supplying extra 'meta' information. These tags interact with the CSS engine so that it's
   * possible to write CSS specific to a particular tag. The platform currently uses this mechanism to cause the
   * color change in the stack containing the currently active part
   */
  val tags: Seq[String] = Seq()
  /**
   * The tooltip to display for this element. If the tooltip is expected to be internationalized
   * then the tooltip may be set to a 'key' value to be used by the translation service.
   */
  val tooltip: Option[String] = None
  /**
   * Contains uniform menu element.
   */
  lazy val uniform: MenuItemTemplate.Uniform = MenuItemTemplate.Uniform(this)
  /**
   * visibleWhen expression.
   */
  val visibleWhen: Option[MExpression] = None

  override def toString() = s"ITemplate[${getClass.getName().split("""\.""").last}/${label}]"
}

object MenuItemTemplate {
  case class Uniform(
    /** Menu wrapper. */
    val wrapper: MenuItemTemplate) extends menu.Template.Uniform[MenuItemTemplate] {
  }
  object Uniform {
    implicit def uniform2wrapper(u: Uniform) = u.wrapper
  }
}
