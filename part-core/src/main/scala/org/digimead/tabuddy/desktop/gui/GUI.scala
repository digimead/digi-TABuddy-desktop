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

package org.digimead.tabuddy.desktop.gui

import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

import scala.collection.mutable

import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.Core
import org.digimead.tabuddy.desktop.action
import org.digimead.tabuddy.desktop.command.Command
import org.digimead.tabuddy.desktop.command.Command.cmdLine2implementation
import org.digimead.tabuddy.desktop.gui.WindowSupervisor.windowGroup2actorSRef
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.App.app2implementation

import language.implicitConversions

/**
 * Run main loop, save and restore windows.
 */
class GUI extends Loggable {
  /** Main loop exit code. */
  protected val exitCode = new AtomicReference[Option[GUI.Exit]](None)
  /** List of all application view factories. */
  protected val viewFactories = new GUI.ViewFactoryMap with mutable.SynchronizedMap[ViewLayer.Factory, GUI.ViewFactoryInfo]

  /** Stop main loop with the specific exit code. */
  def stop(code: GUI.Exit) = {
    log.debugWhere("Stop main loop with code " + code)
    if (exitCode.compareAndSet(None, Some(code)))
      App.display.wake()
    else
      log.error(s"Unable to set new exit code ${code}. There is already ${exitCode.get}.")
  }
  @log
  def run(): GUI.Exit = {
    log.debug("Main loop is running.")
    val display = App.display
    App.publish(App.Message.Started(GUI, App.system.deadLetters))
    WindowSupervisor ! App.Message.Restore
    while (exitCode.get.isEmpty) try {
      if (!display.readAndDispatch())
        display.sleep()
    } catch {
      case e: Throwable =>
        log.error(e.getMessage, e)
    }
    App.publish(App.Message.Stopped(GUI, App.system.deadLetters))
    if (!display.isDisposed()) display.update()
    log.debug("Main loop is finishing. Process pending UI messages.")
    while (display.readAndDispatch()) {}
    log.debug("Main loop is finished.")
    exitCode.get.getOrElse {
      log.fatal("Unexpected termination without exit code.")
      GUI.Exit.Error
    }
  }
  /** Add view factory to the map of the application known views. */
  def registerViewFactory(factory: ViewLayer.Factory, enabled: Boolean) = {
    log.debug("Add " + factory)
    viewFactories += factory -> GUI.ViewFactoryInfo(enabled)
  }
  /** Remove view factory from the map of the application known views. */
  def unregisterViewFactory(factory: ViewLayer.Factory) = {
    log.debug("Remove " + factory)
    viewFactories -= factory
  }
}

object GUI {
  implicit def gui2implementation(g: GUI.type): GUI = g.inner
  /** SWT Data ID key */
  val swtId = getClass.getName() + "#ID"
  /** Context key with current view. */
  lazy val viewContextKey = DI.viewContextKey
  /** Context key with current view. */
  lazy val windowContextKey = DI.windowContextKey

  def inner(): GUI = DI.implementation

  sealed trait Exit
  object Exit {
    case object Ok extends Exit
    case object Error extends Exit
    case object Restart extends Exit
  }
  /** Application view map. This class is responsible for action.View command update. */
  class ViewFactoryMap extends mutable.WeakHashMap[ViewLayer.Factory, ViewFactoryInfo] {
    override def +=(kv: (ViewLayer.Factory, ViewFactoryInfo)): this.type = {
      val (key, value) = kv
      get(key).foreach(_.uniqueActionParserId.foreach(Command.removeFromContext(Core.context, _)))
      if (value.enabled)
        value.uniqueActionParserId = Command.addToContext(Core.context, action.View.parser(key))
      super.+=(kv)
      this
    }

    override def -=(key: ViewLayer.Factory): this.type = {
      get(key).foreach(_.uniqueActionParserId.foreach(Command.removeFromContext(Core.context, _)))
      super.-=(key)
    }

    override def clear(): Unit = {
      values.foreach(_.uniqueActionParserId.foreach(Command.removeFromContext(Core.context, _)))
      super.clear()
    }
  }
  /** ViewFactory information with unique action parser id if any. */
  case class ViewFactoryInfo(enabled: Boolean) {
    @volatile private[GUI] var uniqueActionParserId: Option[UUID] = None
  }
  /**
   * Dependency injection routines
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** Context key with current view. */
    lazy val viewContextKey = injectOptional[String]("GUI.Context.ViewKey") getOrElse "view"
    /** Context key with current view. */
    lazy val windowContextKey = injectOptional[String]("GUI.Context.WindowKey") getOrElse "window"
    /** GUI implementation */
    lazy val implementation = injectOptional[GUI] getOrElse new GUI
  }
}
