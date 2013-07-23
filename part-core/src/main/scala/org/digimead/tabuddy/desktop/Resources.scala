/**
 * This file is part of the TABuddy project.
 * Copyright (c) 2012-2013 Alexey Aksenov ezh@ezh.msk.ru
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

package org.digimead.tabuddy.desktop

import scala.collection.JavaConversions._
import scala.collection.immutable
import scala.collection.mutable
import scala.collection.mutable.Publisher

import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.App.app2implementation
import org.eclipse.e4.ui.model.application.ui.MElementContainer
import org.eclipse.e4.ui.model.application.ui.MUIElement
import org.eclipse.e4.ui.model.application.ui.basic.MWindow
import org.eclipse.e4.ui.model.application.ui.menu.MMenu
import org.eclipse.e4.ui.model.application.ui.menu.MToolBar
import org.eclipse.jface.action.MenuManager
import org.eclipse.jface.action.ToolBarManager
import org.eclipse.jface.fieldassist.FieldDecorationRegistry
import org.eclipse.swt.graphics.Font
import org.eclipse.swt.graphics.Image
import org.eclipse.swt.widgets.Shell
import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext

import language.implicitConversions

/**
 * Handle application resources.
 */
class Resources extends BundleActivator with Loggable {
  val small = 0.315
  val medium = 0.7
  val large = 1
  /** Application menu set. */
  protected val menuSet = new Resources.MenuSet
  /** Application toolbar set. */
  protected val toolbarSet = new Resources.ToolBarSet
  /** Application top window set. */
  protected val topWindowSet = new Resources.TopWindowSet

  /** the large font */
  lazy val fontLarge = {
    val fD = App.display.getSystemFont().getFontData()
    fD.head.setHeight(fD.head.getHeight + 1)
    new Font(App.display, fD.head)
  }
  /** The small font */
  lazy val fontSmall = {
    val fD = App.display.getSystemFont().getFontData()
    fD.head.setHeight(fD.head.getHeight - 1)
    new Font(App.display, fD.head)
  }
  def getImage(path: String, k: Double) = {
    val image = ResourceManager.getImage(getClass, path)
    scale(image, k)
  }
  def scale(image: Image, k: Double): Image = {
    val width = image.getBounds().width
    val height = image.getBounds().height
    new Image(App.display, image.getImageData().scaledTo((width * k).toInt, (height * k).toInt))
  }
  /** Recreate font with specific style */
  def setFontStyle(font: Font, style: Int): Font = {
    val fD = font.getFontData()
    fD.head.setStyle(style)
    new Font(App.display, fD.head)
  }

  def start(context: BundleContext) {
    fontLarge
    fontSmall
  }
  def stop(context: BundleContext) = {
    Image.error.dispose()
    Image.required.dispose()
    ResourceManager.dispose()
  }
  /** Get map of menus. */
  def menus(): immutable.Set[MMenu] = menuSet.toSet
  /** Get menu by id. */
  def menu(id: String): Seq[MMenu] = menuSet.id(id)
  /** Get map of toolbars. */
  def toolbars(): immutable.Set[MToolBar] = toolbarSet.toSet
  /** Get toolbar by id. */
  def toolbar(id: String): Seq[MToolBar] = toolbarSet.id(id)
  /** Get map of top windows. */
  def windows(): immutable.Set[MWindow] = topWindowSet.toSet
  /** Get top window by id. */
  def window(id: String): Seq[MWindow] = topWindowSet.id(id)
  /** Validate resource leaks on shutdown. */
  def validateOnShutdown() {
    if (menuSet.nonEmpty) log.fatal("Menu leaks: " + menuSet)
    if (toolbarSet.nonEmpty) log.fatal("Toolbar leaks: " + menuSet)
    if (topWindowSet.nonEmpty) log.fatal("Top window leaks: " + menuSet)
  }

  object Image {
    lazy val error = FieldDecorationRegistry.getDefault().getFieldDecoration(FieldDecorationRegistry.DEC_ERROR).getImage()
    lazy val required = FieldDecorationRegistry.getDefault().getFieldDecoration(FieldDecorationRegistry.DEC_REQUIRED).getImage()
  }
}

/**
 * Application UI resources.
 */
object Resources extends Loggable {
  implicit def resources2implementation(c: Resources.type): Resources = c.inner

  /** Resources implementation. */
  def inner() = DI.implementation

  sealed trait IconTheme {
    val name: String
  }
  object IconTheme {
    case object Light extends Resources.IconTheme { val name = "light" }
    case object Dark extends Resources.IconTheme { val name = "dark" }
  }
  /**
   * Trait for b4e.Addon that allow to track top level windows.
   */
  trait ResourceWatcher {
    private val resourceLock = new Object
    // Trait methods is invoked by Addon EventBroker Listener
    /** Register new menu. */
    protected def addMenu(menu: MMenu, widget: MenuManager): Unit = resourceLock.synchronized {
      if (menu.getElementId() == null || menu.getElementId() == "") {
        log.debug(s"Skip menu ${menu} with an empty id.")
        return
      }
      log.debug(s"Add menu ${menu}.")
      Resources.Message.publish(Message.MenuCreating(menu, widget))
      DI.implementation.menuSet += menu
    }
    /** Register new toolbar. */
    protected def addToolBar(toolbar: MToolBar, widget: ToolBarManager): Unit = resourceLock.synchronized {
      if (toolbar.getElementId() == null || toolbar.getElementId() == "") {
        log.debug(s"Skip toolbar ${toolbar} with an empty id.")
        return
      }
      if (!DI.implementation.toolbarSet(toolbar)) {
        log.debug(s"Add toolbar ${toolbar}.")
        Resources.Message.publish(Message.ToolbarCreating(toolbar, widget))
        DI.implementation.toolbarSet += toolbar
      }
    }
    /** Register new top window. */
    protected def addTopWindow(window: MWindow, widget: Shell): Unit = resourceLock.synchronized {
      if (window.getElementId() == null || window.getElementId() == "") {
        log.debug(s"Skip window ${window} with an empty id")
        Resources.Message.publish(Message.TopWindowCreating(window, widget))
        return
      }
      log.debug(s"Add top window ${window}.")
      DI.implementation.topWindowSet += window
    }
    /** Unregister disposed menu. */
    protected def removeMenu(menu: MMenu) = resourceLock.synchronized {
      log.debug(s"Remove menu ${menu}")
      processContainer(menu)
      DI.implementation.menuSet -= menu
    }
    /** Unregister disposed toolbar. */
    protected def removeToolBar(toolbar: MToolBar) = resourceLock.synchronized {
      log.debug(s"Remove toolbar ${toolbar}.")
      processContainer(toolbar)
      DI.implementation.toolbarSet -= toolbar
    }
    /** Unregister disposed window. */
    protected def removeTopWindow(window: MWindow) = resourceLock.synchronized {
      log.debug(s"Remove top window ${window}.")
      processContainer(window)
      collectGarbage(window)
      DI.implementation.topWindowSet -= window
    }
    /**
     * Remove sub elements because Eclipse forget about shit that it is allocated.
     * So dispose event may lost sporadically (for some elements).
     */
    protected def processContainer(window: MElementContainer[_]): Unit =
      for (child <- window.getChildren()) child match {
        case menu: MMenu =>
          processContainer(menu)
          DI.implementation.menu(menu.getElementId()).foreach {
            case (menu) =>
              log.debug("Remove menu " + menu)
              DI.implementation.menuSet -= menu
          }
        case toolbar: MToolBar =>
          processContainer(toolbar)
          DI.implementation.toolbar(toolbar.getElementId()).foreach {
            case (toolbar) =>
              log.debug("Remove toolbar " + toolbar)
              DI.implementation.toolbarSet -= toolbar
          }
        case window: MWindow =>
          processContainer(window)
          DI.implementation.window(window.getElementId()).foreach {
            case (window) =>
              log.debug("Remove window " + window)
              DI.implementation.topWindowSet -= window
          }
        case other: MElementContainer[_] =>
          processContainer(other)
        case other =>
      }
    /** Collect all garbage from disposed shell. */
    protected def collectGarbage(window: MWindow) {
      val menusRemove = DI.implementation.menuSet.filter {
        case (menu) => menu.getParent() == null
      }.map {
        case (menu) =>
          log.debug("Remove menu " + menu)
          menu
      }
      DI.implementation.menuSet --= menusRemove
      val toolbarsRemove = DI.implementation.toolbarSet.filter {
        case (toolbar) => toolbar.getParent == null
      }.map {
        case (toolbar) =>
          log.debug("Remove toolbar " + toolbar)
          toolbar
      }
      DI.implementation.toolbarSet --= toolbarsRemove
    }
  }
  class MenuSet extends mutable.HashSet[MMenu]() with mutable.SynchronizedSet[MMenu] with ElementSet[MMenu]
  class ToolBarSet extends mutable.HashSet[MToolBar]() with mutable.SynchronizedSet[MToolBar] with ElementSet[MToolBar]
  class TopWindowSet extends mutable.HashSet[MWindow]() with mutable.SynchronizedSet[MWindow] with ElementSet[MWindow]
  /** Resource map with id index. */
  trait ElementSet[A <: MUIElement] extends mutable.Set[A] {
    /** Map of id -> Seq[UI element in shell/part#1, UI element in shell/part#2 ... UI element in shell/part#N] */
    protected val ids = new mutable.HashMap[String, Seq[A]]() with mutable.SynchronizedMap[String, Seq[A]]

    abstract override def +=(elem: A): this.type = {
      val id = elem.getElementId()
      assert(id != null && id != "")
      ids.get(id) match {
        case Some(values) =>
          ids(id) = values :+ elem
        case None =>
          ids(id) = Seq(elem)
      }
      elem match {
        case menu: MMenu => App.publish(Message.MenuCreated(menu))
        case toolbar: MToolBar => App.publish(Message.ToolbarCreated(toolbar))
        case window: MWindow => App.publish(Message.TopWindowCreated(window))
      }
      super.+=(elem)
    }
    abstract override def -=(elem: A): this.type = {
      val id = elem.getElementId()
      ids.get(id) foreach { seq =>
        if (seq.size == 1 && seq.head == elem)
          ids -= id // remove the only/last element
        else
          ids(id) = seq.filterNot { case (element) => element == elem }
        elem match {
          case menu: MMenu => App.publish(Message.MenuDisposed(menu))
          case toolbar: MToolBar => App.publish(Message.ToolbarDisposed(toolbar))
          case window: MWindow => App.publish(Message.TopWindowDisposed(window))
        }
      }
      super.-=(elem)
    }
    abstract override def clear(): Unit = {
      ids.clear
      super.clear
    }
    def id(key: String): Seq[A] = ids.get(key).getOrElse(Seq[A]())
  }
  /** Menu listener. */
  abstract class ResourceMenuSubscriber(id: String) extends Message.Sub {
    assert(id != null && id.nonEmpty, "Invalid id.")
    def notify(pub: Message.Pub, message: Message) = message match {
      case Message.MenuCreating(element, widget) if element.getElementId() == id => onMenuCreating(element, widget)
      case _ =>
    }
    protected def onMenuCreating(element: MMenu, widget: MenuManager)
  }
  /** ToolBar listener. */
  abstract class ResourceToolBarSubscriber(id: String) extends Message.Sub {
    assert(id != null && id.nonEmpty, "Invalid id.")
    def notify(pub: Message.Pub, message: Message) = message match {
      case Message.ToolbarCreating(element, widget) if element.getElementId() == id => onToolBarCreating(element, widget)
      case _ =>
    }
    protected def onToolBarCreating(element: MToolBar, widget: ToolBarManager)
  }
  /** Window listener. */
  abstract class ResourceWindowSubscriber(id: String) extends Message.Sub {
    assert(id != null && id.nonEmpty, "Invalid id.")
    def notify(pub: Message.Pub, message: Message) = message match {
      case Message.TopWindowCreating(element, widget) if element.getElementId() == id => onTopWindowCreating(element, widget)
      case _ =>
    }
    protected def onTopWindowCreating(element: MWindow, widget: Shell)
  }
  sealed trait Message extends App.Message {
    /** Event model element. */
    val element: MUIElement
  }
  object Message extends Publisher[Message] {
    private val publishLock = new Object
    // Published via Publisher within UI thread.
    case class MenuCreating(val element: MMenu, widget: MenuManager) extends Message
    case class MenuCreated(val element: MMenu) extends Message
    case class MenuDisposed(val element: MMenu) extends Message
    // Published via Publisher within UI thread.
    case class ToolbarCreating(val element: MToolBar, widget: ToolBarManager) extends Message
    case class ToolbarCreated(val element: MToolBar) extends Message
    case class ToolbarDisposed(val element: MToolBar) extends Message
    // Published via Publisher within UI thread.
    case class TopWindowCreating(val element: MWindow, widget: Shell) extends Message
    case class TopWindowCreated(val element: MWindow) extends Message
    case class TopWindowDisposed(val element: MWindow) extends Message

    /** Publish events to all registered subscribers */
    override protected[Resources] def publish(message: Message) = publishLock.synchronized {
      try {
        super.publish(message)
      } catch {
        case e: Throwable =>
          // catch all subscriber exceptions
          log.error(e.getMessage(), e)
      }
    }
  }
  /**
   * Dependency injection routines
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** Resources implementation. */
    lazy val implementation = injectOptional[Resources] getOrElse new Resources()
    /** Icon theme. */
    lazy val theme = injectOptional[Resources.IconTheme] getOrElse IconTheme.Light
  }
}
