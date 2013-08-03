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

import scala.Option.option2Iterable
import scala.collection.JavaConversions.asScalaBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.future

import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.App.app2implementation
import org.digimead.tabuddy.desktop.support.app.MenuItemTemplate
import org.digimead.tabuddy.desktop.support.app.MenuTemplate
import org.eclipse.e4.ui.model.application.ui.MExpression
import org.eclipse.e4.ui.model.application.ui.menu.MMenu
import org.eclipse.e4.ui.workbench.renderers.swt.MenuManagerRenderer
import org.eclipse.emf.ecore.impl.MinimalEObjectImpl.Container
import org.eclipse.jface.action.ActionContributionItem
import org.eclipse.jface.action.IContributionItem
import org.eclipse.jface.action.MenuManager
import org.eclipse.swt.widgets.MenuItem

import language.implicitConversions

/** Wrapper for EMF menu. */
case class EMFMenu(
  /** Original menu element. */
  override val element: MMenu with Container) extends Menu[MMenu with Container](element) with EMFBase[MMenu with Container] with Loggable {
  /** Contains uniform menu element. */
  lazy val uniform: EMFMenu.Uniform = EMFMenu.Uniform(this)

  /** Adjust exists menu with template values. */
  @log
  def adjust[T](template: MenuTemplate)(f: Menu[_] => T) {
    val body = () => {
      App.findRenderer(element) match {
        case Some(renderer: MenuManagerRenderer) =>
          renderer.getManager(element) match {
            case manager: MenuManager =>
              adjust(this, manager, template).foreach { item =>
                f(item)
                manager.updateAll(true)
              }
            case _ =>
              log.fatal("Unable to find suitable menu manager for " + element)
          }
        case unsupported =>
          throw new UnsupportedOperationException("Unable to add menu to unsupported renderer " + unsupported)
      }
    }
    Option(element.getWidget()) match {
      case Some(widget) =>
        if (App.checkUIThread) body() else App.exec { body() }
      case None =>
        onWidgetSet() { (e, before, after) =>
          if (after != null)
            App.exec { body() }
          else
            throw new IllegalArgumentException("Widget value is null.")
        }
    }
  }
  /** Create menu child. */
  @log
  def create[T](template: MenuTemplate)(f: Menu[_] => T) {
    Base.custom.get(element).flatMap(_.get(template)) match {
      case Some(item) =>
        log.warn("Menu is already created.")
      case None =>
        val body = () => {
          App.findRenderer(element) match {
            case Some(renderer: MenuManagerRenderer) =>
              renderer.getManager(element) match {
                case manager: MenuManager =>
                  create(manager, template).foreach { item =>
                    f(item)
                    manager.updateAll(true)
                  }
                case _ =>
                  log.fatal("Unable to find suitable menu manager for " + element)
              }
            case unsupported =>
              throw new UnsupportedOperationException("Unable to add menu to unsupported renderer " + unsupported)
          }
        }
        Option(element.getWidget()) match {
          case Some(widget) =>
            if (App.checkUIThread) body() else App.exec { body() }
          case None =>
            onWidgetSet() { (e, before, after) =>
              if (after != null)
                App.exec { body() }
              else
                throw new IllegalArgumentException("Widget value is null.")
            }
        }
    }
  }
  /** Create menu item child. */
  @log
  def create[T](template: MenuItemTemplate)(f: Item[_] => T) {
    Base.custom.get(element).flatMap(_.get(template)) match {
      case Some(item) =>
        log.warn("Item is already created.")
      case None =>
        val body = () => {
          App.findRenderer(element) match {
            case Some(renderer: MenuManagerRenderer) =>
              renderer.getManager(element) match {
                case manager: MenuManager =>
                  create(manager, template).foreach { item =>
                    f(item)
                    manager.updateAll(true)
                  }
                case _ =>
                  log.fatal("Unable to find suitable menu manager for " + element)
              }
            case unsupported =>
              throw new UnsupportedOperationException("Unable to add menu to unsupported renderer " + unsupported)
          }
        }
        Option(element.getWidget()) match {
          case Some(widget) =>
            if (App.checkUIThread) body() else App.exec { body() }
          case None =>
            onWidgetSet() { (e, before, after) =>
              if (after != null)
                App.exec { body() }
              else
                throw new IllegalArgumentException("Widget value is null.")
            }
        }
    }
  }


  /** Adjust exists menu with template values within the UI thread. */
  @log
  protected def adjust[T](menu: Menu[_], menuManager: MenuManager, template: MenuTemplate): Option[Menu[_]] = {
    log.debug(s"Adjust EMF menu '${uniform.label}' from " + template)
    App.assertUIThread()
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

  override def toString() = s"EMF menu '${uniform.label}'"
}

object EMFMenu extends Loggable {
  case class Uniform(
    /** Menu wrapper. */
    val wrapper: EMFMenu) extends Menu.Uniform[MMenu with Container] {

    /**
     * This field is provided as a way to inform accessibility screen readers with extra
     * information. The intent is that the reader should 'say' this phrase as well as what
     * it would normally emit given the widget hierarchy.
     */
    def accessibilityPhrase: Option[String] = Option(wrapper.element.getAccessibilityPhrase())
    /**
     * This field determines whether the associated menu is enabled or not.
     */
    def enabled: Option[Boolean] = Option(wrapper.element.isEnabled())
    /**
     * Menu children.
     */
    @log
    def children: Seq[Base[_]] = {
      val before: Seq[Base[_]] = Option(wrapper.element.getTransientData.get(Base.beforeKey)).map {
        case custom: Seq[_] =>
          val items: Seq[Option[Base[_]]] = custom.map {
            case contribution: IContributionItem =>
              Some(contribution2wrapper(contribution))
            case unknown =>
              log.fatal(s"Unknown content of '${Base.beforeKey}' container: " + unknown)
              None
          }
          items.flatten
        case other =>
          log.fatal(s"Unknown '${Base.beforeKey}' container type: " + other.getClass)
          Seq[Base[_]]()
      } getOrElse (Seq[Base[_]]())
      val after: Seq[Base[_]] = wrapper.element.getChildren().map { child =>
        val chunk: Seq[Base[_]] = Option(wrapper.element.getTransientData.get(Base.afterKey)).map {
          case custom: Seq[_] =>
            val items: Seq[Option[Base[_]]] = custom.map {
              case contribution: IContributionItem =>
                Some(contribution2wrapper(contribution))
              case unknown =>
                log.fatal(s"Unknown content of '${Base.afterKey}' container: " + unknown)
                None
            }
            items.flatten
          case other =>
            log.fatal(s"Unknown '${Base.afterKey}' container type: " + other.getClass)
            Seq[Base[_]]()
        } getOrElse (Seq[Base[_]]())
        EMF2wrapper(child) match {
          case Some(child) => child +: chunk
          case None => chunk
        }
      }.flatten
      before ++ after
    }
    /**
     * This field contains a fully qualified URL defining the path to an Image to display
     * for this element.
     */
    def iconURI: Option[String] = Option(wrapper.element.getIconURI())
    /**
     * Element ID.
     */
    def id: Option[String] = Option(wrapper.element.getElementId())
    /**
     * The label to display for this element. If the label is expected to be internationalized
     * then the label may be set to a 'key' value to be used by the translation service.
     */
    def label: String = Option(wrapper.element.getLabel()) getOrElse ""
    /**
     * This field is provided as a way to inform accessibility screen readers with extra
     * information. The intent is that the reader should 'say' this phrase as well as what
     * it would normally emit given the widget hierarchy.
     */
    def localizedAccessibilityPhrase: Option[String] = Option(wrapper.element.getLocalizedAccessibilityPhrase())
    /**
     * This is a method that will retrieve the internationalized label by using the current
     * value of the label itself and some translation service.
     */
    def localizedLabel: Option[String] = Option(wrapper.element.getLocalizedLabel())
    /**
     * This is a method that will return the translated mnemonic for this element.
     */
    def localizedMnemonics: Option[String] = Option(wrapper.element.getLocalizedMnemonics())
    /**
     * This is a method that will retrieve the internationalized tooltip by using the current
     * value of the label itself and some translation service.
     */
    def localizedTooltip: Option[String] = Option(wrapper.element.getLocalizedTooltip())
    /**
     * This is the character that is interpreted by the platform to allow for easier navigation
     * through menus.
     */
    def mnemonics: Option[String] = Option(wrapper.element.getMnemonics())
    /**
     * This field is a reference to this element's container. Note that while this field is valid
     * for most UIElements there are a few (such as TrimBars and the Windows associated
     * with top level windows and perspectives) where this will return 'None'
     */
    def parent: Option[Base[_]] = Option(wrapper.element.getParent()).flatMap(EMF2wrapper)
    /**
     * Tags are a list of Strings that are persistent parts of the UI Model. They can be used to 'refine' a particular
     * model element, supplying extra 'meta' information. These tags interact with the CSS engine so that it's
     * possible to write CSS specific to a particular tag. The platform currently uses this mechanism to cause the
     * color change in the stack containing the currently active part
     */
    def tags: Seq[String] = wrapper.element.getTags()
    /**
     * The tooltip to display for this element. If the tooltip is expected to be internationalized
     * then the tooltip may be set to a 'key' value to be used by the translation service.
     */
    def tooltip: Option[String] = Option(wrapper.element.getTooltip())
    /**
     * This is a String to Object map into which any desired runtime information related to a particular element
     * may be stored. It is <i>not</i> persisted across sessions so it is not necessary that the 'values' be
     * serializable.
     */
    def transientData: java.util.Map[String, AnyRef] = wrapper.element.getTransientData()
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
    def visible: Boolean = wrapper.element.isVisible()
    /**
     * visibleWhen expression.
     */
    def visibleWhen: Option[MExpression] = Option(wrapper.element.getVisibleWhen())
  }
  object Uniform {
    implicit def uniform2wrapper(u: Uniform) = u.wrapper
  }
}
