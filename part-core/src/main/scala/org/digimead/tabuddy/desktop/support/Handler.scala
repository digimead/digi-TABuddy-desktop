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

package org.digimead.tabuddy.desktop.support

import scala.annotation.elidable
import scala.annotation.elidable.ASSERTION
import scala.collection.JavaConversions._
import scala.collection.mutable

import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.support.App.app2implementation
import org.eclipse.core.commands.AbstractHandler
import org.eclipse.core.commands.HandlerEvent
import org.eclipse.e4.core.contexts.IEclipseContext
import org.eclipse.ui.commands.ICommandService

import akka.actor.Actor
import akka.actor.ActorPath
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.ScalaActorRef
import akka.actor.actorRef2Scala

import language.implicitConversions

/**
 * Handler base class.
 */
abstract class Handler(singleton: Handler.Singleton) extends AbstractHandler {
  this: Loggable =>
  /** Enabled context key id. */
  lazy val idEnabled = singleton.commandId + "/ENABLED"

  assert(!singleton.instance.contains(this), "Handler is already initialized.")
  singleton.instance += this -> {}

  /** Returns whether this handler is capable of executing at this moment in time. */
  override def isEnabled(): Boolean =
    Option(App.model.getContext().getActiveLeaf().get(idEnabled)).asInstanceOf[Option[Boolean]].getOrElse(super.isEnabled) && super.isEnabled
  /** Called by the framework to allow the handler to update its enabled state. */
  def setEnabled(evaluationContext: Boolean): Unit =
    setBaseEnabled(evaluationContext)
  /** Called by the framework to allow the handler to update its enabled state. */
  override def setEnabled(evaluationContext: AnyRef): Unit = evaluationContext match {
    case value: Boolean.type => setBaseEnabled(Boolean.unbox(value))
    case unknown => // log.trace("Skip unknown evaluation context " + unknown)
  }
  /** Called by the framework to allow the handler to update its enabled state. */
  def setEnabled(enabled: Boolean, context: IEclipseContext) {
    App.checkThread
    context.set(idEnabled, enabled)
    fireHandlerChanged(new HandlerEvent(this, true, false))
  }
}

/**
 * Handler singleton helpers.
 */
object Handler {
  trait Singleton {
    this: Loggable =>
    implicit def handler2actorRef(h: this.type): ActorRef = h.actor
    implicit def handler2actorSRef(h: this.type): ScalaActorRef = h.actor

    /** All SelectModel instances. */
    protected[Handler] val instance = new mutable.WeakHashMap[Handler, Unit] with mutable.SynchronizedMap[Handler, Unit]
    /** Handler actor reference. */
    lazy val actor = App.getActorRef(App.system.actorSelection(actorPath)) getOrElse {
      throw new IllegalStateException("Unable to locate actor with path " + actorPath)
    }
    /** Handler actor path. */
    val actorPath: ActorPath
    /** Singleton identificator. */
    val id = getClass.getSimpleName().dropRight(1)
    /** Command id for the current handler. */
    val commandId: String

    /** Handler actor reference configuration object. */
    def props: Props
  }
  abstract class Behaviour(singleton: Handler.Singleton) extends Actor {
    this: Loggable =>
    /** Enabled context key id. */
    lazy val idEnabled = singleton.commandId + "/ENABLED"
    log.debug("Start actor " + self.path)

    def receive = {
      case message @ Message.Enable =>
        log.debug(s"Process '${message}'.")
        setEnabled(true, App.model.getContext())

      case message @ Message.Enable(context) =>
        log.debug(s"Process '${message}'.")
        setEnabled(true, context)

      case message @ Message.Disable =>
        log.debug(s"Process '${message}'.")
        setEnabled(false, App.model.getContext())

      case message @ Message.Disable(context) =>
        log.debug(s"Process '${message}'.")
        setEnabled(false, context)

      case message @ Message.Refresh =>
        log.debug(s"Process '${message}'.")
        App.exec { refresh() }
    }

    /** Refresh handlers binded to command. */
    protected def refresh(filter: Option[Map[_, _]] = None): Unit =
      Option(App.workbench.getService(classOf[ICommandService]).asInstanceOf[ICommandService]).foreach { service =>
        val id = singleton.commandId
        log.debug(s"Refresh handler '${id}' with filter '${filter}'")
        if (!service.getDefinedCommandIds.contains(id)) {
          log.error("Unable to refresh undefined command: " + id)
          return
        }
        filter match {
          case Some(filter) => service.refreshElements(id, filter)
          case None => service.refreshElements(id, null.asInstanceOf[java.util.Map[_, _]])
        }
      }
    /** Called by the framework to allow the handler to update its enabled state. */
    protected def setEnabled(enabled: Boolean, context: IEclipseContext) {
      App.checkThread
      singleton.instance.keys match {
        case Nil =>
          context.set(idEnabled, enabled)
        case seq =>
          seq.foreach(_.setEnabled(enabled, context))
      }
    }
  }
  /** Handler messages. */
  sealed trait Message extends App.Message
  object Message {
    /** Disable handler. */
    case object Disable extends Handler.Message
    /** Disable handler. */
    case class Disable(context: IEclipseContext = App.model.getContext) extends Handler.Message
    /** Enable handler. */
    case object Enable extends Handler.Message
    /** Enable handler. */
    case class Enable(context: IEclipseContext = App.model.getContext) extends Handler.Message
    /** Refresh handler. */
    case object Refresh extends Handler.Message
  }
}
