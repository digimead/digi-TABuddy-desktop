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

import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.App.app2implementation
import org.digimead.tabuddy.desktop.support.app.MenuItemTemplate
import org.digimead.tabuddy.desktop.support.app.MenuTemplate
import org.eclipse.e4.ui.model.application.ui.MExpression
import org.eclipse.jface.action.MenuManager
import org.eclipse.swt.widgets.MenuItem
import language.implicitConversions
import org.eclipse.jface.action.ActionContributionItem

/** Wrapper for JFace menu. */
case class JFaceMenu(
  /** Original menu element. */
  override val element: MenuManager) extends Menu[MenuManager](element) with Loggable {
  /** Contains uniform menu element. */
  lazy val uniform: JFaceMenu.Uniform = JFaceMenu.Uniform(this)

  /** Adjust exists entity with template values */
  @log
  def adjust[T](template: MenuTemplate)(f: Menu[_] => T) {
    log.debug(s"Adjust JFace menu '${uniform.label}' from " + template)

  }

  /** Create menu child. */
  @log
  def create[T](template: MenuTemplate)(f: Menu[_] => T) = {
    log.debug(s"Create new menu from ${template} for menu '${uniform.label}'")
  }
  /** Create menu item child. */
  @log
  def create[T](template: MenuItemTemplate)(f: Item[_] => T) = {
    Base.custom.get(element).flatMap(_.get(template)) match {
      case Some(item) =>
        log.debug("Item is already created.")
      case None =>
        val body = () => {
          create(element, template).foreach { item =>
            f(item)
            element.updateAll(true)
          }
        }
        if (App.checkThread) body() else App.exec { body() }
    }
  }
  /** Remove item from menu. */
  @log
  def remove() {

  }

  /** Adjust exists menu with template values within the UI thread. */
  protected def adjust[T](menu: Menu[_], menuManager: MenuManager, template: MenuTemplate): Option[Menu[_]] = {
    log.debug(s"Adjust menu '${uniform.label}' from " + template)
    App.checkThread()
    val menuItem = {
      val field = menuManager.getClass().getDeclaredField("menuItem")
      if (!field.isAccessible()) field.setAccessible(true)
      field.get(menuManager).asInstanceOf[MenuItem]
    }
    //template.accessibilityPhrase.foreach()
    //template.enabled
    //template.iconURI
    //template.id
    //template.localizedAccessibilityPhrase
    // adjust label/localizedLabel
    val menuText = template.localizedLabel getOrElse template.label
    if (menuManager.getMenuText() != menuText) menuItem.setText(menuText)
    //template.localizedMnemonics
    //template.localizedTooltip
    //template.mnemonics
    //template.tags
    //template.tooltip
    menuManager.setVisible(template.visible)
    //template.visibleWhen
    Some(menu)
  }

  override def toString() = s"JFace menu '${uniform.label}'"
}

object JFaceMenu {
  case class Uniform(
    /** Menu wrapper. */
    val wrapper: JFaceMenu) extends Menu.Uniform[MenuManager] {

    /**
     * This field is provided as a way to inform accessibility screen readers with extra
     * information. The intent is that the reader should 'say' this phrase as well as what
     * it would normally emit given the widget hierarchy.
     */
    def accessibilityPhrase: Option[String] = None
    /**
     * Menu children.
     */
    def children: Seq[Item[_]] = Seq()
    /**
     * This field determines whether the associated menu is enabled or not.
     */
    def enabled: Option[Boolean] = ???
    /**
     * This field contains a fully qualified URL defining the path to an Image to display
     * for this element.
     */
    def iconURI: Option[String] = ???
    /**
     * Element ID.
     */
    def id: Option[String] = ???
    /**
     * The label to display for this element. If the label is expected to be internationalized
     * then the label may be set to a 'key' value to be used by the translation service.
     */
    def label: String = wrapper.element.getMenuText()
    /**
     * This field is provided as a way to inform accessibility screen readers with extra
     * information. The intent is that the reader should 'say' this phrase as well as what
     * it would normally emit given the widget hierarchy.
     */
    def localizedAccessibilityPhrase: Option[String] = ???
    /**
     * This is a method that will retrieve the internationalized label by using the current
     * value of the label itself and some translation service.
     */
    def localizedLabel: Option[String] = ???
    /**
     * This is a method that will return the translated mnemonic for this element.
     */
    def localizedMnemonics: Option[String] = ???
    /**
     * This is a method that will retrieve the internationalized tooltip by using the current
     * value of the label itself and some translation service.
     */
    def localizedTooltip: Option[String] = ???
    /**
     * This is the character that is interpreted by the platform to allow for easier navigation
     * through menus.
     */
    def mnemonics: Option[String] = ???
    /**
     * This field is a reference to this element's container. Note that while this field is valid
     * for most UIElements there are a few (such as TrimBars and the Windows associated
     * with top level windows and perspectives) where this will return 'None'
     */
    def parent: Option[Base[_]] = ???
    /**
     * Tags are a list of Strings that are persistent parts of the UI Model. They can be used to 'refine' a particular
     * model element, supplying extra 'meta' information. These tags interact with the CSS engine so that it's
     * possible to write CSS specific to a particular tag. The platform currently uses this mechanism to cause the
     * color change in the stack containing the currently active part
     */
    def tags: Seq[String] = ???
    /**
     * The tooltip to display for this element. If the tooltip is expected to be internationalized
     * then the tooltip may be set to a 'key' value to be used by the translation service.
     */
    def tooltip: Option[String] = ???
    /**
     * This is a String to Object map into which any desired runtime information related to a particular element
     * may be stored. It is <i>not</i> persisted across sessions so it is not necessary that the 'values' be
     * serializable.
     */
    def transientData: java.util.Map[String, AnyRef] = ???
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
    def visible: Boolean = ???
    /**
     * visibleWhen expression.
     */
    def visibleWhen: Option[MExpression] = ???
  }
  object Uniform {
    implicit def uniform2wrapper(u: Uniform) = u.wrapper
  }
}
