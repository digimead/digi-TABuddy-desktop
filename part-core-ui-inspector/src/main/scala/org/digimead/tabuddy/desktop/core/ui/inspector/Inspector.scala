/**
 * This file is part of the TA Buddy project.
 * Copyright (c) 2014 Alexey Aksenov ezh@ezh.msk.ru
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

package org.digimead.tabuddy.desktop.core.ui.inspector

import akka.actor.{ ActorRef, Inbox, Props, ScalaActorRef, actorRef2Scala }
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.XDependencyInjection
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.console.Console
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.support.Timeout
import org.digimead.tabuddy.desktop.core.ui.UI
import org.digimead.tabuddy.desktop.core.ui.inspector.command.Commands
import scala.language.implicitConversions

/**
 * Root actor of the UI inspector component.
 */
class Inspector extends akka.actor.Actor with XLoggable {
  /** Inconsistent elements. */
  @volatile protected var inconsistentSet = Set[AnyRef](Inspector)
  /** Current bundle */
  protected lazy val thisBundle = App.bundle(getClass())
  /** Start/stop initialization lock. */
  private val initializationLock = new Object
  log.debug("Start actor " + self.path)

  if (App.watch(Activator, UI, this).hooks.isEmpty)
    App.watch(Activator, UI, this).always().
      makeAfterStart { onGUIStarted() }.
      makeBeforeStop { onGUIStopped() }.sync()

  /** Is called asynchronously after 'actor.stop()' is invoked. */
  override def postStop() = {
    App.system.eventStream.unsubscribe(self, classOf[App.Message.Consistent[_]])
    App.system.eventStream.unsubscribe(self, classOf[App.Message.Inconsistent[_]])
    App.watch(this) off ()
    log.debug(self.path.name + " actor is stopped.")
  }
  /** Is called when an Actor is started. */
  override def preStart() {
    App.system.eventStream.subscribe(self, classOf[App.Message.Inconsistent[_]])
    App.system.eventStream.subscribe(self, classOf[App.Message.Consistent[_]])
    App.watch(this) on ()
    log.debug(self.path.name + " actor is started.")
  }
  def receive = {
    case message @ App.Message.Attach(props, name, _) ⇒ App.traceMessage(message) {
      sender ! context.actorOf(props, name)
    }

    case message @ App.Message.Consistent(element, from, _) if from != Some(self) &&
      App.bundle(element.getClass()) == thisBundle ⇒ App.traceMessage(message) {
      if (inconsistentSet.nonEmpty) {
        inconsistentSet = inconsistentSet - element
        if (inconsistentSet.isEmpty) {
          log.debug("Return integrity.")
          context.system.eventStream.publish(App.Message.Consistent(Inspector, self))
        }
      } else
        log.debug(s"Skip message ${message}. Inspector is already consistent.")
    }

    case message @ App.Message.Inconsistent(element, from, _) if from != Some(self) &&
      App.bundle(element.getClass()) == thisBundle ⇒ App.traceMessage(message) {
      if (inconsistentSet.isEmpty) {
        log.debug("Lost consistency.")
        context.system.eventStream.publish(App.Message.Inconsistent(Inspector, self))
      }
      inconsistentSet = inconsistentSet + element
    }

    case message @ App.Message.Inconsistent(element, _, _) ⇒ // skip
    case message @ App.Message.Consistent(element, _, _) ⇒ // skip
  }

  /** This callback is invoked when GUI is valid. */
  @log
  protected def onGUIStarted() = initializationLock.synchronized {
    App.watch(Inspector) on {
      self ! App.Message.Inconsistent(Inspector, None)
      Inspector.actor // Initialize lazy actor.
      Commands.configure
      Console ! Console.Message.Notice("UI inspector component is started.")
      self ! App.Message.Consistent(Inspector, None)
    }
  }
  /** This callback is invoked when GUI is invalid. */
  @log
  protected def onGUIStopped() = initializationLock.synchronized {
    App.watch(Inspector) off {
      self ! App.Message.Inconsistent(Inspector, None)
      Commands.unconfigure
      val lost = inconsistentSet - Inspector
      if (lost.nonEmpty)
        log.debug("Inconsistent elements: " + lost)
      Console ! Console.Message.Notice("UI inspector component is stopped.")
    }
  }
}

object Inspector {
  implicit def inspector2actorRef(i: Inspector.type): ActorRef = i.actor
  implicit def inspector2actorSRef(i: Inspector.type): ScalaActorRef = i.actor
  /** Inspector actor reference. */
  lazy val actor = {
    val inbox = Inbox.create(App.system)
    inbox.send(UI, App.Message.Attach(props, id))
    inbox.receive(Timeout.long) match {
      case actorRef: ActorRef ⇒
        actorRef
      case other ⇒
        throw new IllegalStateException(s"Unable to attach actor ${id} to ${UI.path}.")
    }
  }
  /** Inspector actor path. */
  lazy val actorPath = UI.path / id
  /** Singleton identificator. */
  val id = getClass.getSimpleName().dropRight(1)
  /** Inspector actor reference configuration object. */
  lazy val props = DI.props

  override def toString = "Inspector[Singleton]"

  /*
   * Explicit import for runtime components/bundle manifest generation.
   */
  private def explicitToggleState: org.digimead.tabuddy.desktop.core.Messages = ???

  /**
   * Dependency injection routines
   */
  private object DI extends XDependencyInjection.PersistentInjectable {
    /** Inspector actor reference configuration object. */
    lazy val props = injectOptional[Props]("Inspector") getOrElse Props[Inspector]
  }
}
