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

import org.eclipse.e4.ui.model.application.ui.MExpression
import language.implicitConversions
import org.eclipse.jface.action.MenuManager
import org.eclipse.jface.action.IContributionItem

trait Template[A <: Template[_]] extends Base[A] {
  /**
   * This field is provided as a way to inform accessibility screen readers with extra
   * information. The intent is that the reader should 'say' this phrase as well as what
   * it would normally emit given the widget hierarchy.
   */
  val accessibilityPhrase: Option[String]
  /**
   * This field determines whether the associated menu is enabled or not.
   */
  val enabled: Option[Boolean]
  /**
   * This field contains a fully qualified URL defining the path to an Image to display
   * for this element.
   */
  val iconURI: Option[String]
  /**
   * Element ID.
   */
  val id: String
  /**
   * The label to display for this element. If the label is expected to be internationalized
   * then the label may be set to a 'key' value to be used by the translation service.
   */
  val label: String
  /**
   * This field is provided as a way to inform accessibility screen readers with extra
   * information. The intent is that the reader should 'say' this phrase as well as what
   * it would normally emit given the widget hierarchy.
   */
  val localizedAccessibilityPhrase: Option[String]
  /**
   * This is a method that will retrieve the internationalized label by using the current
   * value of the label itself and some translation service.
   */
  val localizedLabel: Option[String]
  /**
   * This is a method that will return the translated mnemonic for this element.
   */
  val localizedMnemonics: Option[String]
  /**
   * This is a method that will retrieve the internationalized tooltip by using the current
   * value of the label itself and some translation service.
   */
  val localizedTooltip: Option[String]
  /**
   * This is the character that is interpreted by the platform to allow for easier navigation
   * through menus.
   */
  val mnemonics: Option[String]
  /**
   * Tags are a list of Strings that are persistent parts of the UI Model. They can be used to 'refine' a particular
   * model element, supplying extra 'meta' information. These tags interact with the CSS engine so that it's
   * possible to write CSS specific to a particular tag. The platform currently uses this mechanism to cause the
   * color change in the stack containing the currently active part
   */
  val tags: Seq[String]
  /**
   * The tooltip to display for this element. If the tooltip is expected to be internationalized
   * then the tooltip may be set to a 'key' value to be used by the translation service.
   */
  val tooltip: Option[String]
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
  val visible: Boolean
  /**
   * visibleWhen expression.
   */
  val visibleWhen: Option[MExpression]

  /** Create contribution item. */
  def contribution: IContributionItem
  /** Insert contribution into menu manager. */
  def insertInto(menuManager: MenuManager): Option[IContributionItem] = try {
    val contribution = this.contribution
    assert(contribution.getId() != null, "Unable to add contribution with empty ID.")
    menuManager.add(contribution)
    Some(contribution)
  } catch {
    case e: Throwable =>
      log.error(e.getMessage(), e)
      None
  }
  /** Remove entity from menu. */
  def remove() = throw new UnsupportedOperationException
}

object Template {
  trait Uniform[A <: Template[_]] extends Base.Uniform[A] {
    /** Item wrapper. */
    val wrapper: Template[A]

    /**
     * This field is provided as a way to inform accessibility screen readers with extra
     * information. The intent is that the reader should 'say' this phrase as well as what
     * it would normally emit given the widget hierarchy.
     */
    def accessibilityPhrase: Option[String] = wrapper.element.accessibilityPhrase
    /**
     * This field determines whether the associated menu is enabled or not.
     */
    def enabled: Option[Boolean] = wrapper.element.enabled
    /**
     * This field contains a fully qualified URL defining the path to an Image to display
     * for this element.
     */
    def iconURI: Option[String] = wrapper.element.iconURI
    /**
     * Element ID.
     */
    def id: Option[String] = Option(wrapper.element.id)
    /**
     * The label to display for this element. If the label is expected to be internationalized
     * then the label may be set to a 'key' value to be used by the translation service.
     */
    def label: String = wrapper.element.label
    /**
     * This field is provided as a way to inform accessibility screen readers with extra
     * information. The intent is that the reader should 'say' this phrase as well as what
     * it would normally emit given the widget hierarchy.
     */
    def localizedAccessibilityPhrase: Option[String] = wrapper.element.localizedAccessibilityPhrase
    /**
     * This is a method that will retrieve the internationalized label by using the current
     * value of the label itself and some translation service.
     */
    def localizedLabel: Option[String] = wrapper.element.localizedLabel
    /**
     * This is a method that will return the translated mnemonic for this element.
     */
    def localizedMnemonics: Option[String] = wrapper.element.localizedMnemonics
    /**
     * This is a method that will retrieve the internationalized tooltip by using the current
     * value of the label itself and some translation service.
     */
    def localizedTooltip: Option[String] = wrapper.element.localizedTooltip
    /**
     * This is the character that is interpreted by the platform to allow for easier navigation
     * through menus.
     */
    def mnemonics: Option[String] = wrapper.element.mnemonics
    /**
     * Tags are a list of Strings that are persistent parts of the UI Model. They can be used to 'refine' a particular
     * model element, supplying extra 'meta' information. These tags interact with the CSS engine so that it's
     * possible to write CSS specific to a particular tag. The platform currently uses this mechanism to cause the
     * color change in the stack containing the currently active part
     */
    def tags: Seq[String] = wrapper.element.tags
    /**
     * The tooltip to display for this element. If the tooltip is expected to be internationalized
     * then the tooltip may be set to a 'key' value to be used by the translation service.
     */
    def tooltip: Option[String] = wrapper.element.tooltip
    /**
     * This is a String to Object map into which any desired runtime information related to a particular element
     * may be stored. It is <i>not</i> persisted across sessions so it is not necessary that the 'values' be
     * serializable.
     */
    def transientData: java.util.Map[String, AnyRef] = new java.util.HashMap[String, AnyRef]()
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
    def visible: Boolean = wrapper.element.visible
    /**
     * visibleWhen expression.
     */
    def visibleWhen: Option[MExpression] = wrapper.element.visibleWhen
  }
  object Uniform {
    implicit def uniform2wrapper[T <: Template[_]](u: Uniform[T]) = u.wrapper
  }
}