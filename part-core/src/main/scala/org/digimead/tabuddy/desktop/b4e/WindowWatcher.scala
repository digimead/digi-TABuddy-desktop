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
import org.digimead.tabuddy.desktop.Resources
import org.digimead.tabuddy.desktop.Resources.resources2implementation
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.App.app2implementation
import org.digimead.tabuddy.desktop.support.Handler
import org.eclipse.e4.ui.model.application.ui.basic.MWindow
import org.eclipse.ui.PlatformUI
import org.eclipse.ui.internal.WorkbenchWindow

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.ScalaActorRef
import akka.actor.actorRef2Scala

import language.implicitConversions

/**
 * Application windows watcher.
 */
class WindowWatcher extends Actor with Loggable {
  private val adjustWindowLock = new Object
  log.debug("Start actor " + self.path)

  /** Get subscribers list. */
  def receive = {
    case message @ WindowAdvisor.Message.PostWindowCreate(configurer) =>
      log.debug(s"Process '${message}'.")
      configurer.getWindow() match {
        case window: WorkbenchWindow =>
          log.debug(s"Apply modifications to top level window ${window}.")
          val shell = window.getShell()
          if (shell == null || shell.isDisposed())
            log.debug(s"Skip window ${window} configuration: shell is not valid.")
          else
            Resources.windows.find(_.getWidget() == shell) match {
              case Some(model) =>
                App.exec { adjust(window, model) }
              case None =>
                log.debug(s"Skip window ${window} configuration: not a top window.")
            }
        case garbage =>
          log.fatal("Unable to process unknown garbage: " + garbage)
      }

    case message @ WindowAdvisor.Message.PreWindowShellClose(configurer) =>
      log.debug(s"Process '${message}'.")
      configurer.getWindow() match {
        case window: WorkbenchWindow =>
          log.debug(s"Unapply modifications from top level window ${window}.")
          val shell = window.getShell()
          if (shell == null || shell.isDisposed())
            log.debug(s"Skip window ${window} configuration: shell is not valid.")
          else
            Resources.windows.find(_.getWidget() == shell) match {
              case Some(model) =>
                if (PlatformUI.isWorkbenchRunning() && App.display != null)
                  App.exec { revert(window, model) }
                else
                  log.debug(s"Skip top window ${window} revert: workbench is stopped.")
              case None =>
                log.debug(s"Skip window ${window} configuration: not a top window.")
            }
        case garbage =>
          log.fatal("Unable to process unknown garbage: " + garbage)
      }

    case message: Handler.Message => // skip
  }
  /** Adjust window configuration. */
  @log
  protected def adjust(implementation: WorkbenchWindow, model: MWindow) = adjustWindowLock.synchronized {
    log.debug(s"Adjust top window ${model}.")
    App.checkThread
  }
  /** Revert window configuration. */
  @log
  protected def revert(implementation: WorkbenchWindow, model: MWindow) = adjustWindowLock.synchronized {
    log.debug(s"Revert top window ${model}.")
    App.checkThread
  }
}

/** Window watcher singleton. */
object WindowWatcher extends Loggable {
  implicit def watcher2actorRef(w: WindowWatcher.type): ActorRef = w.actor
  implicit def watcher2actorSRef(w: WindowWatcher.type): ScalaActorRef = w.actor
  /** WindowWatcher actor reference. */
  lazy val actor = App.getActorRef(App.system.actorSelection(actorPath)) getOrElse {
    throw new IllegalStateException("Unable to locate actor with path " + actorPath)
  }
  /** WindowWatcher actor path. */
  lazy val actorPath = Core.path / id
  /** Singleton identificator. */
  val id = getClass.getSimpleName().dropRight(1)

  /** WindowWatcher actor reference configuration object. */
  def props = DI.props

  /**
   * Dependency injection routines.
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** WindowWatcher actor reference configuration object. */
    lazy val props = injectOptional[Props]("WindowWatcher") getOrElse Props[WindowWatcher]
  }
}
