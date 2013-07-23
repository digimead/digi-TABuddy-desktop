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

import scala.collection.mutable
import org.digimead.digi.lib.log.api.Loggable
import org.eclipse.e4.ui.model.application.ui.MExpression
import org.eclipse.e4.ui.model.application.ui.MUIElement
import org.eclipse.e4.ui.model.application.ui.menu.MMenuItem
import org.eclipse.e4.ui.model.application.ui.menu.MMenuSeparator
import org.eclipse.e4.ui.model.application.ui.menu.impl.MenuImpl
import org.eclipse.jface.action.ActionContributionItem
import org.eclipse.jface.action.IContributionItem
import org.eclipse.jface.action.MenuManager
import language.implicitConversions
import org.eclipse.e4.ui.model.application.ui.menu.impl.MenuItemImpl
import org.eclipse.e4.ui.model.application.ui.menu.impl.MenuSeparatorImpl

/** Base trait for a menu entity. */
trait Base[A] extends Loggable {
  /** Original menu element. */
  val element: A
  /** Contains uniform menu element. */
  val uniform: Base.Uniform[A]

  /**
   * Compare labels without case sensitivity and '&' sign.
   */
  def compareLabels[X, Y](e1: Base[X], e2: Base[Y]): Boolean = {
    val normalized1 = e1.uniform.label.trim.filterNot(_ == '&').toLowerCase()
    val normalized2 = e2.uniform.label.trim.filterNot(_ == '&').toLowerCase()
    if (normalized1 == normalized2 && normalized1.isEmpty()) {
      log.warn("Compare two menu elements 1:${normalized1} 2:${normalized2} with empty labels")
      false // empty labels are always different
    } else
      normalized1 == normalized2
  }
  /** Remove entity from menu. */
  def remove()
}

object Base extends Loggable {
  /** EMF key name of transient storage that holds custom elements from head of the menu.  */
  val beforeKey = getClass.getName() + "#before"
  /** EMF key name of transient storage that holds custom elements from body and tail of the menu.  */
  val afterKey = getClass.getName() + "#after"
  /** Hash map of modified menu item with set of applied templates. */
  val custom = new mutable.WeakHashMap[AnyRef, mutable.HashMap[Template[_], Item.Uniform[_]]] with mutable.SynchronizedMap[AnyRef, mutable.HashMap[Template[_], Item.Uniform[_]]]

  /** Base trait for inner object. */
  trait Uniform[A] {
    /** Element wrapper. */
    val wrapper: Base[A]

    /**
     * This field is provided as a way to inform accessibility screen readers with extra
     * information. The intent is that the reader should 'say' this phrase as well as what
     * it would normally emit given the widget hierarchy.
     */
    def accessibilityPhrase: Option[String]
    /**
     * This field determines whether the associated menu is enabled or not.
     */
    def enabled: Option[Boolean]
    /**
     * This field contains a fully qualified URL defining the path to an Image to display
     * for this element.
     */
    def iconURI: Option[String]
    /**
     * Element ID.
     */
    def id: Option[String]
    /**
     * The label to display for this element. If the label is expected to be internationalized
     * then the label may be set to a 'key' value to be used by the translation service.
     */
    def label: String
    /**
     * This field is provided as a way to inform accessibility screen readers with extra
     * information. The intent is that the reader should 'say' this phrase as well as what
     * it would normally emit given the widget hierarchy.
     */
    def localizedAccessibilityPhrase: Option[String]
    /**
     * This is a method that will retrieve the internationalized label by using the current
     * value of the label itself and some translation service.
     */
    def localizedLabel: Option[String]
    /**
     * This is a method that will return the translated mnemonic for this element.
     */
    def localizedMnemonics: Option[String]
    /**
     * This is a method that will retrieve the internationalized tooltip by using the current
     * value of the label itself and some translation service.
     */
    def localizedTooltip: Option[String]
    /**
     * This is the character that is interpreted by the platform to allow for easier navigation
     * through menus.
     */
    def mnemonics: Option[String]
    /**
     * Tags are a list of Strings that are persistent parts of the UI Model. They can be used to 'refine' a particular
     * model element, supplying extra 'meta' information. These tags interact with the CSS engine so that it's
     * possible to write CSS specific to a particular tag. The platform currently uses this mechanism to cause the
     * color change in the stack containing the currently active part
     */
    def tags: Seq[String]
    /**
     * The tooltip to display for this element. If the tooltip is expected to be internationalized
     * then the tooltip may be set to a 'key' value to be used by the translation service.
     */
    def tooltip: Option[String]
    /**
     * This is a String to Object map into which any desired runtime information related to a particular element
     * may be stored. It is <i>not</i> persisted across sessions so it is not necessary that the 'values' be
     * serializable.
     */
    def transientData: java.util.Map[String, AnyRef]
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
    def visible: Boolean
    /**
     * visibleWhen expression.
     */
    def visibleWhen: Option[MExpression]

    /** Convert IContributionItem to uniform wrapper */
    protected def contribution2wrapper(contribution: IContributionItem): Base[_] = contribution match {
      case menu: MenuManager => new JFaceMenu(menu)
      case action: ActionContributionItem => new JFaceAItem(action)
      case contribution => new JFaceCItem(contribution)
    }
    /** Convert EMF item to uniform wrapper */
    protected def EMF2wrapper(element: MUIElement): Option[Base[_]] = element match {
      case menu: MenuImpl => Some(new EMFMenu(menu))
      case item: MenuItemImpl => Some(new EMFItem(item))
      case separator: MenuSeparatorImpl => Some(new EMFSeparator(separator))
      case unknown =>
        log.fatal("Unknown menu item " + unknown)
        None
    }
  }
  object Uniform {
    implicit def uniform2wrapper[T](u: Uniform[T]) = u.wrapper
  }
}
