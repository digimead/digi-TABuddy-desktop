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

package org.digimead.tabuddy.desktop

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.future

import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.command.Command
import org.digimead.tabuddy.desktop.command.Command.cmdLine2implementation
import org.digimead.tabuddy.desktop.gui.GUI
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.App.app2implementation
import org.digimead.tabuddy.desktop.support.Handler
import org.eclipse.e4.core.contexts.EclipseContextFactory
import org.eclipse.e4.core.internal.contexts.EclipseContext

import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.ScalaActorRef
import akka.actor.UnhandledMessage
import akka.actor.actorRef2Scala

import language.implicitConversions

/**
 * Root actor of the Core component.
 */
class Core extends akka.actor.Actor with Loggable {
  /** Inconsistent elements. */
  @volatile protected var inconsistentSet = Set[AnyRef]()
  log.debug("Start actor " + self.path)

  /*
   * Core component actors.
   */
  val windowGroup = context.actorOf(gui.WindowSupervisor.props, gui.WindowSupervisor.id)

  /** Get subscribers list. */
  def receive = {
    case message @ App.Message.Attach(props, name) => App.traceMessage(message) {
      sender ! context.actorOf(props, name)
    }

    case message @ App.Message.Inconsistent(element, sender) if element != Core => App.traceMessage(message) {
      if (inconsistentSet.isEmpty) {
        log.debug("Lost consistency.")
        context.system.eventStream.publish(App.Message.Inconsistent(Core, self))
      }
      inconsistentSet = inconsistentSet + element
    }

    case message @ App.Message.Consistent(element, sender) if element != Core => App.traceMessage(message) {
      inconsistentSet = inconsistentSet - element
      if (inconsistentSet.isEmpty) {
        log.debug("Return integrity.")
        context.system.eventStream.publish(App.Message.Consistent(Core, self))
      }
    }

    case message: Handler.Message =>
      log.trace(s"Container actor '${self.path.name}' received message '${message}' from actor ${sender.path}. Propagate.")
      context.children.foreach(_.forward(message))

    case message @ App.Message.Started(GUI, sender) => App.traceMessage(message) {
      future {
        App.verifyApplicationEnvironment
        Command.register(action.Exit.description)
        Command.addToContext(Core.context, action.Exit.parser)
        App.markAsStarted(Core.getClass)
      } onFailure {
        case e: Exception => log.error(e.getMessage(), e)
        case e => log.error(e.toString())
      }
    }

    case message @ App.Message.Stopped(GUI, sender) => App.traceMessage(message) {
      future {
        App.markAsStopped(Core.getClass)
      } onFailure {
        case e: Exception => log.error(e.getMessage(), e)
        case e => log.error(e.toString())
      }
    }

    case message @ App.Message.Inconsistent(element, sender) if element == Core => // skip
    case message @ App.Message.Consistent(element, sender) if element == Core => // skip

    case UnhandledMessage(message, sender, self) =>
      log.fatal(s"Received unexpected message '${sender}' -> '${self}': '${message}'")
  }
  override def postStop() = log.debug("Core actor is stopped.")
}

object Core {
  implicit def core2actorRef(c: Core.type): ActorRef = c.actor
  implicit def core2actorSRef(c: Core.type): ScalaActorRef = c.actor
  /** Core actor reference. */
  lazy val actor = App.system.actorOf(props, id)
  /** Core actor path. */
  lazy val actorPath = actor.path
  /** Root context. */
  val context = EclipseContextFactory.create("root").asInstanceOf[EclipseContext]
  /** Singleton identificator. */
  val id = getClass.getSimpleName().dropRight(1)
  // Initialize descendant actor singletons
  gui.WindowSupervisor

  /** Core actor reference configuration object. */
  def props = DI.props

  /**
   * Dependency injection routines
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** Core Akka factory. */
    lazy val props = injectOptional[Props]("Core") getOrElse Props[Core]
  }
}
