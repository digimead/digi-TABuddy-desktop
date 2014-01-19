/**
 * This file is part of the TA Buddy project.
 * Copyright (c) 2013-2014 Alexey Aksenov ezh@ezh.msk.ru
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

package org.digimead.tabuddy.desktop.logic.ui

import akka.actor.{ Actor, ActorRef, Props }
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.ui.widget.AppWindow

/**
 * Register action in new windows.
 * There is no need subscribe to App.Message.Destroyed because SWT dispose will do all job.
 */
class UI extends Actor with Loggable {
  log.debug("Start actor " + self.path)

  /** Is called asynchronously after 'actor.stop()' is invoked. */
  override def postStop() = {
    App.system.eventStream.unsubscribe(self, classOf[App.Message.Create[_]])
    log.debug(self.path.name + " actor is stopped.")
  }
  /** Is called when an Actor is started. */
  override def preStart() {
    App.system.eventStream.subscribe(self, classOf[App.Message.Create[_]])
    log.debug(self.path.name + " actor is started.")
  }
  def receive = {
    case message @ App.Message.Create(Right(window: AppWindow), Some(publisher)) ⇒ App.traceMessage(message) {
      onCreated(window, publisher)
    }

    case message @ App.Message.Create(_, _) ⇒
  }

  /** Register actions in new window. */
  protected def onCreated(window: AppWindow, sender: ActorRef) = {
    // block actor
    App.execNGet {
      log.debug(s"Update window ${window} composite.")
      adjustMenu(window)
      adjustToolbar(window)
    }
    // publish that window menu and toolbar are ready
    //App.publish(App.Message.Create(Right(Core, window), self))
  }
  /** Adjust window menu. */
  @log
  protected def adjustMenu(window: AppWindow) {
    //val file = WindowMenu(window, Core.fileMenu)
    //file.add(action.ActionExit)
  }
  /** Adjust window toolbar. */
  @log
  protected def adjustToolbar(window: AppWindow) {
    //val commonToolBar = WindowToolbar(window, Core.commonToolbar)
    //commonToolBar.getToolBarManager().add(action.ActionExit)
    //commonToolBar.getToolBarManager().add(action.ActionTest)
    //window.getCoolBarManager2().update(true)
  }
}

object UI extends Loggable {
  /*/** This callback is invoked at an every model initialization. */
  @log
  protected def onModelInitialization(oldModel: Model.Like, newModel: Model.Like, modified: Element.Timestamp) = try {
    TypeSchema.onModelInitialization(oldModel, newModel, modified)
    ElementTemplate.onModelInitialization(oldModel, newModel, modified)
    Data.onModelInitialization(oldModel, newModel, modified)
    Config.save()
  } catch {
    case e: Throwable =>
      log.error(e.getMessage, e)
  }
  /** This callback is invoked when GUI is valid. */
  @log
  protected def onGUIValid() = initializationLock.synchronized {
    App.afterStart("Desktop Logic", Timeout.normal.toMillis, Core.getClass()) {
      val context = thisBundle.getBundleContext()
      openContainer()
      // Prepare for startup.
      // Reset for sure. There is maybe still something in memory after the bundle reload.
      Model.reset(Payload.defaultModel)
      // Startup sequence.
      Config.start(context) // Initialize the application configuration based on Configgy
      //Transport.start() // Initialize the network transport(s)
      Actions.configure
      App.markAsStarted(Logic.getClass)
      future { onApplicationStartup() } onFailure {
        case e: Exception => log.error(e.getMessage(), e)
        case e => log.error(e.toString())
      }
    }
  }
  /** This callback is invoked when GUI is invalid. */
  @log
  protected def onGUIInvalid() = initializationLock.synchronized {
    val context = thisBundle.getBundleContext()
    App.markAsStopped(Logic.getClass())
    // Prepare for shutdown.
    Actions.unconfigure
    //Transport.stop()
    Config.stop(context)
    closeContainer()
    // Avoid deadlock.
    App.system.eventStream.unsubscribe(self, classOf[org.digimead.tabuddy.model.element.Element.Event.ModelReplace[_ <: org.digimead.tabuddy.model.Model.Interface[_ <: org.digimead.tabuddy.model.Model.Stash], _ <: org.digimead.tabuddy.model.Model.Interface[_ <: org.digimead.tabuddy.model.Model.Stash]]])
    Model.reset(Payload.defaultModel)
    if (inconsistentSet.nonEmpty)
      log.fatal("Inconsistent elements detected: " + inconsistentSet)
    // The everything is stopped. Absolutely consistent.
    App.publish(App.Message.Consistent(Logic, self))
  }
      case message @ App.Message.Start(Right(GUI), _) ⇒ App.traceMessage(message) {
      fGUIStarted = true
      future { onGUIValid() } onFailure {
        case e: Exception ⇒ log.error(e.getMessage(), e)
        case e ⇒ log.error(e.toString())
      }
    }
    case message @ App.Message.Stop(Right(GUI), _) ⇒ App.traceMessage(message) {
      fGUIStarted = false
      future { onGUIInvalid } onFailure {
        case e: Exception ⇒ log.error(e.getMessage(), e)
        case e ⇒ log.error(e.toString())
      }
    }*/

  /** Singleton identificator. */
  val id = getClass.getSimpleName().dropRight(1)

  /** Core actor reference configuration object. */
  def props = DI.props

  /**
   * Dependency injection routines.
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** Core actor reference configuration object. */
    lazy val props = injectOptional[Props]("Logic.UI") getOrElse Props[UI]
  }
}
