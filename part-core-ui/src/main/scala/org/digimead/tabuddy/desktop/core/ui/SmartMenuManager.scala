/**
 * This file is part of the TA Buddy project.
 * Copyright (c) 2014-2015 Alexey Aksenov ezh@ezh.msk.ru
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
 * that is created or manipulated using TA Buddy.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license. Buying such a license is mandatory as soon as you
 * develop commercial activities involving the TA Buddy software without
 * disclosing the source code of your own applications.
 * These activities include: offering paid services to customers,
 * serving files in a web or/and network application,
 * shipping TA Buddy with a closed source product.
 *
 * For more information, please contact Digimead Team at this
 * address: ezh@ezh.msk.ru
 */

package org.digimead.tabuddy.desktop.core.ui

import akka.pattern.ask
import com.google.common.base.Charsets
import com.google.common.io.Files
import java.io.File
import java.util.{ ArrayList, UUID }
import java.util.concurrent.atomic.AtomicBoolean
import org.digimead.digi.lib.api.XDependencyInjection
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.support.Timeout
import org.digimead.tabuddy.desktop.core.ui.block.WindowSupervisor
import org.digimead.tabuddy.desktop.core.ui.definition.widget.AppWindow
import org.eclipse.jface.action.{ IAction, IContributionItem, IMenuManager, MenuManager, Separator }
import org.eclipse.jface.resource.ImageDescriptor
import org.yaml.snakeyaml.Yaml
import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.immutable.ListMap
import scala.collection.mutable
import scala.language.implicitConversions
import scala.util.{ Failure, Success }

/**
 * Smart menu manager.
 */
class SmartMenuManager extends XLoggable {
  /** Akka execution context. */
  implicit lazy val ec = App.system.dispatcher
  /** Actor request timeout. */
  implicit lazy val timeout = akka.util.Timeout(Timeout.short)

  /** Nested array of generic menu entries. */
  val generic = try convertListToArray(loadGenericMenu()) catch {
    case e: Throwable ⇒
      log.error(e.getMessage(), e)
      throw e
  }
  /** Map of menu containers and map of menu item binded to parent. */
  val (genericContainers, genericItems) = parseGenericMenu(generic, Map.empty, Map.empty, None)

  /** Add action to menu only if not exists. */
  def add(menu: IMenuManager, action: IAction): Boolean = {
    if (action.getId == null)
      throw new IllegalArgumentException(s"Unable to add action ${action.getText} without id.")
    Option(menu.find(action.getId())) match {
      case Some(action) ⇒
        log.debug(s"""Action "${action.getId}" is already exists.""")
        false
      case None ⇒
        val actionId = action.getId
        genericContainers.get(menu.getId()) match {
          case Some(container) ⇒
            smartAdd(menu, actionId, container) match {
              case Some((insertSeparatorBefore, insertSeparatorAfter, Left(id))) ⇒
                log.debug(s"""Insert action "${actionId}" before ${id} in menu "${menu.getId}".""")
                menu.insertBefore(id, action)
                if (insertSeparatorBefore || insertSeparatorAfter) {
                  val (before, after) = menu.getItems.span { _.getId != actionId }
                  if (insertSeparatorBefore && before.nonEmpty && before.lastOption.map { _.getClass } != Some(classOf[Separator])) {
                    log.debug(s"""Insert separator before action "${actionId}".""")
                    menu.insertBefore(actionId, new Separator())
                  }
                  if (insertSeparatorAfter && after.size > 1 && after.drop(1).headOption.map { _.getClass } != Some(classOf[Separator])) {
                    log.debug(s"""Insert separator after action "${actionId}".""")
                    menu.insertAfter(actionId, new Separator())
                  }
                }
              case Some((insertSeparatorBefore, insertSeparatorAfter, Right(id))) ⇒
                log.debug(s"""Insert action "${actionId}" after ${id} in menu "${menu.getId}".""")
                menu.insertAfter(id, action)
                if (insertSeparatorBefore || insertSeparatorAfter) {
                  val (before, after) = menu.getItems.span { _.getId != actionId }
                  if (insertSeparatorBefore && before.nonEmpty && before.lastOption.map { _.getClass } != Some(classOf[Separator])) {
                    log.debug(s"""Insert separator before action "${actionId}".""")
                    menu.insertBefore(actionId, new Separator())
                  }
                  if (insertSeparatorAfter && after.size > 1 && after.drop(1).headOption.map { _.getClass } != Some(classOf[Separator])) {
                    log.debug(s"""Insert separator after action "${actionId}".""")
                    menu.insertAfter(actionId, new Separator())
                  }
                }
              case None ⇒
                log.debug(s"""Append action "${actionId}" to menu "${menu.getId}".""")
                menu.add(action)
            }
            // Hide item if needed
            container.find {
              case Array(id, visibility: AtomicBoolean, _*) if id == actionId ⇒
                if (!visibility.get) {
                  log.debug("Hide action " + actionId)
                  Option(menu.find(actionId)).foreach(_.setVisible(false))
                }
                true
              case _ ⇒
                false
            }
          case None ⇒
            log.debug(s"""Append action "${actionId}" to unknown menu "${menu.getId}".""")
            menu.add(action)
        }
        true
    }
  }
  /** Add contribution to menu only if not exists. */
  def add(menu: IMenuManager, item: IContributionItem): Boolean = {
    if (item.getId == null)
      throw new IllegalArgumentException(s"Unable to add item ${item.getClass} without id.")
    Option(menu.find(item.getId())) match {
      case Some(item) ⇒
        log.debug(s"""Contribution item "${item.getId}" is already exists.""")
        false
      case None ⇒
        val itemId = item.getId
        (if (menu.getId() == null) Some(generic) else genericContainers.get(menu.getId())) match {
          case Some(container) ⇒
            smartAdd(menu, itemId, container) match {
              case Some((insertSeparatorBefore, insertSeparatorAfter, Left(id))) ⇒
                log.debug(s"""Insert contribution item "${itemId}" before ${id} in menu "${menu.getId}".""")
                menu.insertBefore(id, item)
                if (insertSeparatorBefore || insertSeparatorAfter) {
                  val (before, after) = menu.getItems.span { _.getId != itemId }
                  if (insertSeparatorBefore && before.nonEmpty && before.lastOption.map { _.getClass } != Some(classOf[Separator])) {
                    log.debug(s"""Insert separator before contribution item "${itemId}".""")
                    menu.insertBefore(itemId, new Separator())
                  }
                  if (insertSeparatorAfter && after.size > 1 && after.drop(1).headOption.map { _.getClass } != Some(classOf[Separator])) {
                    log.debug(s"""Insert separator after contribution item "${itemId}".""")
                    menu.insertAfter(itemId, new Separator())
                  }
                }
              case Some((insertSeparatorBefore, insertSeparatorAfter, Right(id))) ⇒
                log.debug(s"""Insert contribution item "${itemId}" after ${id} in menu "${menu.getId}".""")
                menu.insertAfter(id, item)
                if (insertSeparatorBefore || insertSeparatorAfter) {
                  val (before, after) = menu.getItems.span { _.getId != itemId }
                  if (insertSeparatorBefore && before.nonEmpty && before.lastOption.map { _.getClass } != Some(classOf[Separator])) {
                    log.debug(s"""Insert separator before contribution item "${itemId}".""")
                    menu.insertBefore(itemId, new Separator())
                  }
                  if (insertSeparatorAfter && after.size > 1 && after.drop(1).headOption.map { _.getClass } != Some(classOf[Separator])) {
                    log.debug(s"""Insert separator after contribution item "${itemId}".""")
                    menu.insertAfter(itemId, new Separator())
                  }
                }
              case None ⇒
                log.debug(s"""Append contribution item "${itemId}" to menu "${menu.getId}".""")
                menu.add(item)
            }
            // Hide item if needed
            container.find {
              case Array(id, visibility: AtomicBoolean, _*) if id == itemId ⇒
                if (!visibility.get) {
                  log.debug("Hide contribution item " + itemId)
                  Option(menu.find(itemId)).foreach(_.setVisible(false))
                }
                true
              case _ ⇒
                false
            }

          case None ⇒
            log.debug(s"""Append contribution item "${itemId}" to unknown menu "${menu.getId}".""")
            menu.add(item)
        }
        true
    }
  }
  /** Get menu with the specific id from the window. */
  def apply(parent: AppWindow, menuDescriptor: SmartMenuManager.Descriptor): IMenuManager =
    apply(parent.getMenuBarManager(), menuDescriptor)
  /** Get menu with the specific id from the window. */
  def apply(manager: IMenuManager, menuDescriptor: SmartMenuManager.Descriptor): IMenuManager = {
    Option(manager.findMenuUsingPath(menuDescriptor.id)) match {
      case Some(menu) ⇒ menu
      case None ⇒
        val menu = new MenuManager(menuDescriptor.text, menuDescriptor.image.getOrElse(null), menuDescriptor.id)
        if (manager.getId == null)
          log.debug(s"""Add menu "${menu.getId}" to root menu.""")
        else
          log.debug(s"""Add menu "${menu.getId}" to menu "${manager.getId}".""")
        add(manager, menu)
        menu
    }
  }
  /** Collect content of MenuManager. */
  def collectForMenuManager(mm: MenuManager): ListMap[IContributionItem, Option[_]] = {
    val items = mm.getItems.map {
      case mm: MenuManager ⇒
        mm -> Some(collectForMenuManager(mm))
      case item ⇒
        item -> None
    }
    ListMap(items: _*)
  }
  /** Convert Array to ArrayList. */
  def convertArrayToList(menu: Array[_]): ArrayList[_] = menu match {
    case Array(element: String, visibility: AtomicBoolean, submenu: Array[_]) ⇒
      val arr = new ArrayList[Any]()
      arr.add(element)
      arr.add(visibility.get)
      arr.add(convertArrayToList(submenu))
      arr
    case Array(element: String, visibility: AtomicBoolean) ⇒
      val arr = new ArrayList[Any]()
      arr.add(element)
      arr.add(visibility.get)
      arr
    case Array() ⇒
      new ArrayList[Any]()
    case array ⇒
      val arr = new ArrayList[Any]()
      array.foreach { case entry: Array[_] ⇒ arr.add(convertArrayToList(entry)) }
      arr
  }
  /** Convert ArrayList to Array. */
  def convertListToArray(menu: ArrayList[_]): Array[_] = menu.asScala match {
    case mutable.Buffer(element: String, visibility: java.lang.Boolean, submenu: ArrayList[_]) ⇒
      Array(element, new AtomicBoolean(visibility), convertListToArray(submenu))
    case mutable.Buffer(element: String, visibility: java.lang.Boolean) ⇒
      Array(element, new AtomicBoolean(visibility))
    case mutable.Buffer() ⇒
      Array.empty
    case buffer ⇒
      buffer.toArray.map { case entry: ArrayList[_] ⇒ convertListToArray(entry) }
  }
  /** Find IContributionItem in window. */
  def find(id: String, window: AppWindow): Option[(Seq[IMenuManager], IContributionItem, AtomicBoolean)] = {
    val chainOfIds = buildChainOfIds(id).dropRight(1)
    var parent: IMenuManager = window.getMenuBarManager
    val chainOfItems = chainOfIds.flatMap { id ⇒
      Option(parent.find(id)).flatMap {
        case menu: IMenuManager ⇒
          parent = menu
          Some(menu)
        case menu ⇒
          None
      }
    }
    chainOfItems.lastOption.flatMap { container ⇒ Option(container.find(id)) } match {
      case Some(item) ⇒
        genericContainers(chainOfIds.last).find {
          case Array(itemId, visible: AtomicBoolean, _*) if itemId == id ⇒ true
          case _ ⇒ false
        }.map { case Array(itemId, visible: AtomicBoolean, _*) ⇒ (chainOfItems, item, visible) }
      case None ⇒
        None
    }
  }
  /** Hide menu. */
  def hide(id: String) {
    (WindowSupervisor.actor ? App.Message.Get(WindowSupervisor.PointerMap)).onComplete {
      case Success(pointerMap: Map[_, _]) ⇒
        pointerMap.foreach {
          case (windowId: UUID, pointer: WindowSupervisor.WindowPointer) ⇒
            (pointer.windowActor ? App.Message.Get(AppWindow)).onComplete {
              case Success(Some(window: AppWindow)) ⇒
                App.exec {
                  find(id, window).foreach {
                    case (containers, item, visible) ⇒
                      log.debug(s"Hide ${id} in ${window}")
                      item.setVisible(false)
                      visible.set(false)
                      containers.lastOption.foreach { mm ⇒
                        mm.update(id)
                        mm.update(true) // WTF ??? Sometimes mm.update(id) is not enough
                      }
                  }
                }
              case Success(unexpected) ⇒
                log.error(s"Unable to get AppWindow from ${pointer}: unexpected result " + unexpected)
              case Failure(error) ⇒
                log.error(s"Unable to get AppWindow from ${pointer}: " + error)
            }
        }
      case Success(unexpected) ⇒
        log.error("Unable to get window list from WindowSupervisor: unexpected result " + unexpected)
      case Failure(error) ⇒
        log.error("Unable to get window list from WindowSupervisor: " + error)
    }
  }
  /** Show menu. */
  def show(id: String) {
    (WindowSupervisor.actor ? App.Message.Get(WindowSupervisor.PointerMap)).onComplete {
      case Success(pointerMap: Map[_, _]) ⇒
        pointerMap.foreach {
          case (windowId: UUID, pointer: WindowSupervisor.WindowPointer) ⇒
            (pointer.windowActor ? App.Message.Get(AppWindow)).onComplete {
              case Success(Some(window: AppWindow)) ⇒
                App.exec {
                  find(id, window).foreach {
                    case (containers, item, visible) ⇒
                      log.debug(s"Show ${id} in ${window}")
                      item.setVisible(true)
                      visible.set(true)
                      containers.lastOption.foreach { mm ⇒
                        mm.update(id)
                        mm.update(true) // WTF ??? Sometimes mm.update(id) is not enough
                      }
                  }
                }
              case Success(unexpected) ⇒
                log.error(s"Unable to get AppWindow from ${pointer}: unexpected result " + unexpected)
              case Failure(error) ⇒
                log.error(s"Unable to get AppWindow from ${pointer}: " + error)
            }
        }
      case Success(unexpected) ⇒
        log.error("Unable to get window list from WindowSupervisor: unexpected result " + unexpected)
      case Failure(error) ⇒
        log.error("Unable to get window list from WindowSupervisor: " + error)
    }
  }

  /** Build chain of Ids. */
  protected def buildChainOfIds(id: String): Seq[String] =
    genericItems.get(id) match {
      case Some(Some(parent)) ⇒ buildChainOfIds(parent) :+ id
      case _ ⇒ Seq(id)
    }
  /** Load menu.configuration from bundle. */
  protected def loadGenericMenu() = {
    val customMenu: Option[ArrayList[_]] = if (SmartMenuManager.menuConfiguration.exists()) try {
      val content = Files.asCharSource(SmartMenuManager.menuConfiguration, Charsets.UTF_8).read()
      log.debug("Load custom menu from " + SmartMenuManager.menuConfiguration)
      Some((new Yaml).load(content).asInstanceOf[ArrayList[_]])
    } catch {
      case e: Throwable ⇒
        log.error(s"Unable to read menu configuration ${SmartMenuManager.menuConfiguration}:" + e.getMessage, e)
        None
    }
    else
      None
    // Or load default menu from resource.
    customMenu orElse {
      Option(App.bundle(getClass).getEntry("/menu.configuration")).map { url ⇒
        val content = com.google.common.io.Resources.asCharSource(url, Charsets.UTF_8).read()
        (new Yaml).load(content).asInstanceOf[ArrayList[_]]
      }
    } getOrElse new ArrayList()
  }
  /** Parse nested array with menu entries. */
  protected def parseGenericMenu(menu: Array[_], containers: Map[String, Array[_]], parents: Map[String, Option[String]],
    parentId: Option[String]): (Map[String, Array[_]], Map[String, Option[String]]) =
    menu match {
      case Array(element: String, visibility: AtomicBoolean, submenu: Array[_]) ⇒
        parseGenericMenu(submenu, containers.updated(element, submenu), parents.updated(element, parentId), Some(element))
      case Array(element: String, visibility: AtomicBoolean) ⇒
        (containers, parents.updated(element, parentId))
      case Array() ⇒
        (containers, parents)
      case arr ⇒
        arr.foldLeft((containers, parents)) {
          case ((cMap, pMap), entry: Array[_]) ⇒
            val (a, b) = parseGenericMenu(entry, cMap, pMap, parentId)
            (cMap ++ a, pMap ++ b)
        }
    }
  /** Smart add to menu. */
  protected def smartAdd(menu: IMenuManager, itemId: String, container: Array[_]): Option[(Boolean, Boolean, Either[String, String])] = {
    if (container.isEmpty)
      return None // menu.add
    var insertSeparatorBefore = false
    var insertSeparatorAfter = false
    // prepare summary
    val summaryItems = mutable.Buffer.empty[String]
    container.foreach {
      case Array(id: String, _*) ⇒
        if (summaryItems.nonEmpty && id == itemId && summaryItems.last == null)
          insertSeparatorBefore = true
        summaryItems += id
      case Array() ⇒
        if (summaryItems.nonEmpty && summaryItems.last == itemId)
          insertSeparatorAfter = true
        summaryItems += null
    }
    // process
    val menuItems = menu.getItems.map(_.getId)
    val prefixMap = summaryItems.takeWhile { _ != itemId }.filterNot { _ == null }.toSet
    if (prefixMap.size == summaryItems.size) {
      log.debug(s"Append unknown item ${itemId} to menu")
      return None
    } else if (prefixMap.isEmpty) {
      log.debug(s"Prepend item ${itemId} to menu")
      if (menuItems.isEmpty)
        return None // menu.add
      else
        return Some(insertSeparatorBefore, insertSeparatorAfter, Left(menuItems.head)) // menu.insertBefore
    }
    menuItems.reverse.foreach { id ⇒
      if (prefixMap(id))
        return Some(insertSeparatorBefore, insertSeparatorAfter, Right(id)) // menu.insertAfter
    }
    // not found
    // append item
    None
  }

  override def toString = "core.ui.block.SmartMenuManager"
}

object SmartMenuManager {
  implicit def manager2implementation(m: SmartMenuManager.type): SmartMenuManager = m.inner

  /** Get SmartMenuManager implementation. */
  def inner = DI.implementation
  /** Get location of user menu. */
  def menuConfiguration = DI.menuConfiguration

  override def toString = "core.ui.block.SmartMenuManager[Singleton]"

  /** Menu descriptor. */
  case class Descriptor(text: String, image: Option[ImageDescriptor], id: String)

  /**
   * Dependency injection routines
   */
  private object DI extends XDependencyInjection.PersistentInjectable {
    /** SmartMenuManager implementation. */
    lazy val implementation = injectOptional[SmartMenuManager] getOrElse new SmartMenuManager
    /** Location of user menu. */
    lazy val menuConfiguration = injectOptional[File]("Menu.Location") getOrElse new File(UI.container, "menu.configuration")
  }
}
