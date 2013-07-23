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

package org.digimead.tabuddy.desktop.b4e

import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.Core
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.App.app2implementation
import org.digimead.tabuddy.desktop.support.Handler
import org.eclipse.jface.action.IMenuListener2
import org.eclipse.jface.action.IMenuManager
import org.eclipse.jface.internal.MenuManagerEventHelper

import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.ScalaActorRef
import akka.actor.actorRef2Scala

import language.implicitConversions

/**
 * Application menu watcher.
 */
class MenuWatcher extends akka.actor.Actor with Loggable {
  private val globalHookLock = new Object
  log.debug("Start actor " + self.path)

  /** Get subscribers list. */
  def receive = {
    case message @ WindowAdvisor.Message.PostWindowCreate(configurer) =>
      log.debug(s"Process '${message}'.")
      installGlobalHook()

    case message @ WindowAdvisor.Message.PreWindowShellClose(configurer) =>
      log.debug(s"Process '${message}'.")
      uninstallGlobalHook()

    case message: Handler.Message => // skip
  }
  /** Configure menu. */
  @log
  def installGlobalHook() = globalHookLock.synchronized {
    log.debug("Install menu hook.")

    MenuManagerEventHelper.showHelper = {
      val previous = Option(MenuManagerEventHelper.showHelper)
      log.debug("Install show helper. Previous helper was " + previous)
      new EventShowHelper(previous)
    }
    MenuManagerEventHelper.hideHelper = {
      val previous = Option(MenuManagerEventHelper.hideHelper)
      log.debug("Install hide helper. Previous helper was " + previous)
      new EventHideHelper(previous)
    }
  }
  /** Revert menu to the original state. */
  @log
  def uninstallGlobalHook() = globalHookLock.synchronized {
    log.debug("Uninstall menu hook.")

    MenuManagerEventHelper.showHelper match {
      case managerHelper: EventShowHelper =>
        MenuManagerEventHelper.showHelper = managerHelper.previuos getOrElse null
        log.debug("Revert show helper to " + MenuManagerEventHelper.showHelper)
      case other =>
    }
    MenuManagerEventHelper.hideHelper match {
      case managerHelper: EventHideHelper =>
        MenuManagerEventHelper.hideHelper = managerHelper.previuos getOrElse null
        log.debug("Revert hide helper to " + MenuManagerEventHelper.hideHelper)
      case other =>
    }
  }
  def menuAboutToShowPre(manager: IMenuManager) {
    //log.trace("Before show " + manager.getId())
  }
  def menuAboutToShowPost(manager: IMenuManager) {
    //log.trace("After show " + manager.getId())
  }
  def menuAboutToHidePre(manager: IMenuManager) {
    //log.trace("Before hide " + manager.getId())
  }
  def menuAboutToHidePost(manager: IMenuManager) {
    //log.trace("After hide " + manager.getId())
  }
  /** Another technical debt minimizer for MenuWatcherEventHelper.showHelper. */
  class EventShowHelper(val previuos: Option[IMenuListener2]) extends IMenuListener2 {
    /**
     * Before show.
     * Flaky design from Eclipse developers.
     */
    def menuAboutToShow(manager: IMenuManager) {
      previuos.foreach(_.menuAboutToShow(manager))
      menuAboutToShowPre(manager)
    }
    /**
     * After show.
     * Flaky design from Eclipse developers. Self-contradictory method name.
     */
    def menuAboutToHide(manager: IMenuManager) {
      menuAboutToShowPost(manager)
      previuos.foreach(_.menuAboutToHide(manager))
    }
  }
  /** Another technical debt minimizer for MenuWatcherEventHelper.hideHelper. */
  class EventHideHelper(val previuos: Option[IMenuListener2]) extends IMenuListener2 {
    /**
     * Before hide.
     * Flaky design from Eclipse developers. Self-contradictory method name.
     */
    def menuAboutToShow(manager: IMenuManager) {
      previuos.foreach(_.menuAboutToShow(manager))
      menuAboutToHidePre(manager)
    }
    /**
     * After hide.
     * Flaky design from Eclipse developers.
     */
    def menuAboutToHide(manager: IMenuManager) {
      menuAboutToHidePost(manager)
      previuos.foreach(_.menuAboutToHide(manager))
    }
  }
}

/** Menu watcher singleton. */
object MenuWatcher {
  implicit def watcher2actorRef(w: MenuWatcher.type): ActorRef = w.actor
  implicit def watcher2actorSRef(w: MenuWatcher.type): ScalaActorRef = w.actor
  /** MenuWatcher actor reference. */
  lazy val actor = App.getActorRef(App.system.actorSelection(actorPath)) getOrElse {
    throw new IllegalStateException("Unable to locate actor with path " + actorPath)
  }
  /** MenuWatcher actor path. */
  lazy val actorPath = Core.path / id
  /** Singleton identificator. */
  val id = getClass.getSimpleName().dropRight(1)

  /** MenuWatcher actor reference configuration object. */
  def props = DI.props

  /**
   * Dependency injection routines.
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** MenuWatcher actor reference configuration object. */
    lazy val props = injectOptional[Props]("MenuWatcher") getOrElse Props[MenuWatcher]
  }
}
