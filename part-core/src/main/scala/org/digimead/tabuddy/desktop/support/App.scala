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

import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props

import language.implicitConversions

class App extends Loggable with app.Akka with app.Context with app.Generic with app.Model with app.Workbench with app.GUI

/** Application singleton. */
object App {
  implicit def app2implementation(l: App.type): App = inner

  def inner(): App = DI.implementation
  /** An empty actor implementation. */
  trait ContainerActor extends Actor {
    this: Loggable =>
    log.debug("Start actor " + self.path)

    def receive = {
      case message: Message =>
        log.trace(s"Container actor '${self.path.name}' received message '${message}' from actor ${sender.path}. Propagate.")
        context.children.foreach(_.forward(message))
    }
  }
  /**
   * Base messages
   */
  trait Message
  object Message {
    /*
     * Sender argument is sufficient because messages are suitable for various transports.
     * Transports like Akka EventBus that lost original sender are supported too.
     */
    /** Actor send hello with indirect(over bundles) dependency sequence. */
    case class Attach(props: Props, name: String) extends Message
    /** Close something. */
    case class Close[T <: AnyRef](arg: T, sender: ActorRef) extends Message
    /** Something closed. */
    case class Closed[T <: AnyRef](arg: T, sender: ActorRef) extends Message
    /** Element return integrity. */
    case class Consistent[T <: AnyRef](element: T, sender: ActorRef) extends Message
    /** Create something. */
    case class Create[T <: AnyRef](arg: T, sender: ActorRef) extends Message
    /** Something created. */
    case class Created[T <: AnyRef](arg: T, sender: ActorRef) extends Message
    /** Destroy something. */
    case class Destroy[T <: AnyRef](arg: T, sender: ActorRef) extends Message
    /** Something destroyed. */
    case class Destroyed[T <: AnyRef](arg: T, sender: ActorRef) extends Message
    /** Something lost consistency. */
    case class Inconsistent[T <: AnyRef](element: T, sender: ActorRef) extends Message
    /** Open something. */
    case class Open[T <: AnyRef](arg: T, sender: ActorRef) extends Message
    /** Something opened. */
    case class Opened[T <: AnyRef](arg: T, sender: ActorRef) extends Message
    /** Restore something. */
    case class Restore[T <: AnyRef](arg: T, sender: ActorRef) extends Message
    /** Save something. */
    case class Save[T <: AnyRef](arg: T, sender: ActorRef) extends Message
    /** Start something. */
    case class Start[T <: AnyRef](arg: T, sender: ActorRef) extends Message
    /** Something started. */
    case class Started[T <: AnyRef](arg: T, sender: ActorRef) extends Message
    /** Stop something. */
    case class Stop[T <: AnyRef](arg: T, sender: ActorRef) extends Message
    /** Something stopped. */
    case class Stopped[T <: AnyRef](arg: T, sender: ActorRef) extends Message
    /** Something updated. */
    case class Updated[T <: AnyRef](arg: T, sender: ActorRef) extends Message
  }
  /**
   * Dependency injection routines
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** App implementation */
    lazy val implementation = injectOptional[App] getOrElse new App
  }
}
