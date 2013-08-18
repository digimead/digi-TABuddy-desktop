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

package org.digimead.tabuddy.desktop.viewmod

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.future

import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.gui.GUI
import org.digimead.tabuddy.desktop.logic.Logic
import org.digimead.tabuddy.desktop.logic.Logic.logic2actorRef
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.App.app2implementation
import org.digimead.tabuddy.desktop.support.Timeout
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.element.Element

import akka.actor.ActorRef
import akka.actor.Inbox
import akka.actor.Props
import akka.actor.ScalaActorRef
import akka.actor.actorRef2Scala

import scala.language.implicitConversions

/**
 * Root actor of the View modification component.
 */
class ViewMod extends akka.actor.Actor with Loggable {
  /** Inconsistent elements. */
  @volatile protected var inconsistentSet = Set[AnyRef]()
  /** Flag indicating whether GUI is valid. */
  @volatile protected var fGUIStarted = false
  /** Current bundle */
  protected lazy val thisBundle = App.bundle(getClass())
  private val initializationLock = new Object
  log.debug("Start actor " + self.path)

  /*
   * Logic component actors.
   */
  //val actionRef = context.actorOf(action.Action.props, action.Action.id)

  /** Is called asynchronously after 'actor.stop()' is invoked. */
  override def postStop() = {
    App.system.eventStream.unsubscribe(self, classOf[Element.Event.ModelReplace[_ <: Model.Interface[_ <: Model.Stash], _ <: Model.Interface[_ <: Model.Stash]]])
    App.system.eventStream.unsubscribe(self, classOf[App.Message.Consistent[_]])
    App.system.eventStream.unsubscribe(self, classOf[App.Message.Inconsistent[_]])
    App.system.eventStream.unsubscribe(self, classOf[App.Message.Stop[_]])
    App.system.eventStream.unsubscribe(self, classOf[App.Message.Start[_]])
    log.debug(self.path.name + " actor is stopped.")
  }
  /** Is called when an Actor is started. */
  override def preStart() {
    App.system.eventStream.subscribe(self, classOf[App.Message.Start[_]])
    App.system.eventStream.subscribe(self, classOf[App.Message.Stop[_]])
    App.system.eventStream.subscribe(self, classOf[App.Message.Inconsistent[_]])
    App.system.eventStream.subscribe(self, classOf[App.Message.Consistent[_]])
    App.system.eventStream.subscribe(self, classOf[Element.Event.ModelReplace[_ <: Model.Interface[_ <: Model.Stash], _ <: Model.Interface[_ <: Model.Stash]]])
    log.debug(self.path.name + " actor is started.")
  }
  def receive = {
    case message @ App.Message.Attach(props, name) => App.traceMessage(message) {
      sender ! context.actorOf(props, name)
    }
    case message @ App.Message.Inconsistent(element, _) if element != ViewMod && App.bundle(element.getClass()) == thisBundle => App.traceMessage(message) {
      if (inconsistentSet.isEmpty) {
        log.debug("Lost consistency.")
        context.system.eventStream.publish(App.Message.Inconsistent(ViewMod, self))
      }
      inconsistentSet = inconsistentSet + element
    }
    case message @ App.Message.Consistent(element, _) if element != ViewMod && App.bundle(element.getClass()) == thisBundle => App.traceMessage(message) {
      inconsistentSet = inconsistentSet - element
      if (inconsistentSet.isEmpty) {
        log.debug("Return integrity.")
        context.system.eventStream.publish(App.Message.Consistent(ViewMod, self))
      }
    }
    case message @ Element.Event.ModelReplace(oldModel, newModel, modified) => App.traceMessage(message) {
      if (fGUIStarted) log.___gaze("Close all/Reload all")
    }
    case message @ App.Message.Start(Right(GUI), _) => App.traceMessage(message) {
      fGUIStarted = true
      future { onGUIValid() } onFailure {
        case e: Exception => log.error(e.getMessage(), e)
        case e => log.error(e.toString())
      }
    }
    case message @ App.Message.Stop(Right(GUI), _) => App.traceMessage(message) {
      fGUIStarted = false
      future { onGUIInvalid } onFailure {
        case e: Exception => log.error(e.getMessage(), e)
        case e => log.error(e.toString())
      }
    }
    case message @ App.Message.Inconsistent(element, _) => // skip
    case message @ App.Message.Consistent(element, _) => // skip
  }

  /** This callback is invoked when GUI is valid. */
  @log
  protected def onGUIValid() = initializationLock.synchronized {
    App.afterStart("Desktop View Modification", Timeout.normal.toMillis, Logic.getClass()) {
      val context = thisBundle.getBundleContext()
      //Actions.configure
      App.markAsStarted(ViewMod.getClass)
    }
  }
  /** This callback is invoked when GUI is invalid. */
  @log
  protected def onGUIInvalid() = initializationLock.synchronized {
    val context = thisBundle.getBundleContext()
    App.markAsStopped(ViewMod.getClass())
    // Prepare for shutdown.
    //Actions.unconfigure
    if (inconsistentSet.nonEmpty)
      log.fatal("Inconsistent elements detected: " + inconsistentSet)
    // The everything is stopped. Absolutely consistent.
    App.publish(App.Message.Consistent(ViewMod, self))
  }
}

object ViewMod {
  implicit def ViewMod2actorRef(m: ViewMod.type): ActorRef = m.actor
  implicit def ViewMod2actorSRef(m: ViewMod.type): ScalaActorRef = m.actor
  /** ViewMod actor reference. */
  lazy val actor = {
    val inbox = Inbox.create(App.system)
    inbox.send(Logic, App.Message.Attach(props, id))
    inbox.receive(Timeout.long) match {
      case actorRef: ActorRef =>
        actorRef
      case other =>
        throw new IllegalStateException(s"Unable to attach actor ${id} to ${Logic.path}.")
    }
  }
  /** ViewMod actor path. */
  lazy val actorPath = Logic.path / id
  /** Singleton identificator. */
  val id = getClass.getSimpleName().dropRight(1)
  /** ViewMod actor reference configuration object. */
  lazy val props = DI.props
  // Initialize descendant actor singletons
  //action.Action

  override def toString = "ViewMod[Singleton]"

  /**
   * Dependency injection routines
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** ViewMod actor reference configuration object. */
    lazy val props = injectOptional[Props]("ViewMod") getOrElse Props[ViewMod]
  }
}
