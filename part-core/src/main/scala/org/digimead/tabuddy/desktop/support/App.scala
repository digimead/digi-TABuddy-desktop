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

class App extends Loggable with app.Akka with app.Context with app.Generic with app.GUI with app.Reflection

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
  trait Message {
    val publisher: Option[ActorRef] = None
  }
  object Message {
    /*
     * Publisher argument is significant because messages are suitable for various transports.
     * Transports like Akka EventBus are lost the origin.
     * Argument of type Either is
     *   Left() - before/request
     *   Right() - after/reply
     */
    /** Attach actor. */
    case class Attach(props: Props, name: String) extends Message {
      override def productPrefix = "Message.Attach"
    }
    /** Close something. */
    case class Close[T <: AnyRef](val arg: Either[T, T], override val publisher: Option[ActorRef]) extends Message {
      override def productPrefix = "Message.Close"
    }
    object Close {
      def apply[T <: AnyRef](arg: Either[T, T], publisher: ActorRef) = new Close(arg, Some(publisher))
      def apply[T <: AnyRef](arg: Either[T, T]) = new Close(arg, None)
      override def toString = "Message.Close"
    }
    /** Element return integrity. */
    case class Consistent[T <: AnyRef](val element: T, override val publisher: Option[ActorRef]) extends Message {
      override def productPrefix = "Message.Consistent"
    }
    object Consistent {
      def apply[T <: AnyRef](element: T, publisher: ActorRef) = new Consistent(element, Some(publisher))
      def apply[T <: AnyRef](element: T) = new Consistent(element, None)
      override def toString = "Message.Consistent"
    }
    /** Create something. */
    case class Create[T <: AnyRef](val arg: Either[T, T], override val publisher: Option[ActorRef]) extends Message {
      override def productPrefix = "Message.Create"
    }
    object Create {
      def apply[T <: AnyRef](arg: Either[T, T], publisher: ActorRef) = new Create(arg, Some(publisher))
      def apply[T <: AnyRef](arg: Either[T, T]) = new Create(arg, None)
      override def toString = "Message.Create"
    }
    /** Destroy something. */
    case class Destroy[T <: AnyRef](val arg: Either[T, T], override val publisher: Option[ActorRef]) extends Message {
      override def productPrefix = "Message.Destroy"
    }
    object Destroy {
      def apply[T <: AnyRef](arg: Either[T, T], publisher: ActorRef) = new Destroy(arg, Some(publisher))
      def apply[T <: AnyRef](arg: Either[T, T]) = new Destroy(arg, None)
      override def toString = "Message.Destroy"
    }
    /** Operation error. */
    case class Error[T <: AnyRef](val arg: T, override val publisher: Option[ActorRef]) extends Message {
      override def productPrefix = "Message.Error"
    }
    object Error {
      def apply[T <: AnyRef](arg: T, publisher: ActorRef) = new Error(arg, Some(publisher))
      def apply[T <: AnyRef](arg: T) = new Error(arg, None)
      override def toString = "Message.Error"
    }
    /** Something lost consistency. */
    case class Inconsistent[T <: AnyRef](val element: T, override val publisher: Option[ActorRef]) extends Message {
      override def productPrefix = "Message.Inconsistent"
    }
    object Inconsistent {
      def apply[T <: AnyRef](element: T, publisher: ActorRef) = new Inconsistent(element, Some(publisher))
      def apply[T <: AnyRef](element: T) = new Inconsistent(element, None)
      override def toString = "Message.Inconsistent"
    }
    /** Open something. */
    case class Open[T <: AnyRef](val arg: Either[T, T], override val publisher: Option[ActorRef]) extends Message {
      override def productPrefix = "Message.Open"
    }
    object Open {
      def apply[T <: AnyRef](arg: Either[T, T], publisher: ActorRef) = new Open(arg, Some(publisher))
      def apply[T <: AnyRef](arg: Either[T, T]) = new Open(arg, None)
      override def toString = "Message.Open"
    }
    /** Restore something. */
    case class Restore[T <: AnyRef](val arg: Either[T, T], override val publisher: Option[ActorRef]) extends Message {
      override def productPrefix = "Message.Restore"
    }
    object Restore {
      def apply[T <: AnyRef](arg: Either[T, T], publisher: ActorRef) = new Restore(arg, Some(publisher))
      def apply[T <: AnyRef](arg: Either[T, T]) = new Restore(arg, None)
      override def toString = "Message.Restore"
    }
    /** Save something. */
    case class Save[T <: AnyRef](val arg: Either[T, T], override val publisher: Option[ActorRef]) extends Message {
      override def productPrefix = "Message.Save"
    }
    object Save {
      def apply[T <: AnyRef](arg: Either[T, T], publisher: ActorRef) = new Save(arg, Some(publisher))
      def apply[T <: AnyRef](arg: Either[T, T]) = new Save(arg, None)
      override def toString = "Message.Save"
    }
    /** Start something. */
    case class Start[T <: AnyRef](val arg: Either[T, T], override val publisher: Option[ActorRef]) extends Message {
      override def productPrefix = "Message.Start"
    }
    object Start {
      def apply[T <: AnyRef](arg: Either[T, T], publisher: ActorRef) = new Start(arg, Some(publisher))
      def apply[T <: AnyRef](arg: Either[T, T]) = new Start(arg, None)
      override def toString = "Message.Start"
    }
    /** Stop something. */
    case class Stop[T <: AnyRef](val arg: Either[T, T], override val publisher: Option[ActorRef]) extends Message {
      override def productPrefix = "Message.Stop"
    }
    object Stop {
      def apply[T <: AnyRef](arg: Either[T, T], publisher: ActorRef) = new Stop(arg, Some(publisher))
      def apply[T <: AnyRef](arg: Either[T, T]) = new Stop(arg, None)
      override def toString = "Message.Stop"
    }
    /** Update something. */
    case class Update[T <: AnyRef](val arg: Either[T, T], override val publisher: Option[ActorRef]) extends Message {
      override def productPrefix = "Message.Update"
    }
    object Update {
      def apply[T <: AnyRef](arg: Either[T, T], publisher: ActorRef) = new Update(arg, Some(publisher))
      def apply[T <: AnyRef](arg: Either[T, T]) = new Update(arg, None)
      override def toString = "Message.Update"
    }
  }
  /**
   * Dependency injection routines
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** App implementation */
    lazy val implementation = injectOptional[App] getOrElse new App
  }
}
