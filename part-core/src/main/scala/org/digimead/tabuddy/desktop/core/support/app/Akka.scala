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

package org.digimead.tabuddy.desktop.core.support.app

import akka.actor.ActorDSL.{ Act, actor }
import akka.actor.{ ActorIdentity, ActorPath, ActorRef, ActorSelection, Identify, Props }
import akka.pattern.ask
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.{ Exchanger, TimeUnit }
import org.digimead.digi.lib.log.api.{ XLoggable, XRichLogger }
import org.digimead.tabuddy.desktop.core.EventLoop
import org.digimead.tabuddy.desktop.core.support.{ App, Timeout }
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.reflect.ClassTag
import scala.reflect.runtime.universe.runtimeMirror

/**
 * Akka support trait
 */
trait Akka {
  this: EventLoop.Consumer with XLoggable ⇒
  /** Support actor. */
  // System.nanoTime is needed because we may have more than one supportActor per JVM
  protected lazy val supportActor = system.actorOf(Props(classOf[Akka.Actor]), "TABuddyAppSupport." + System.currentTimeMillis() + System.nanoTime())

  /** Send argument to the actor and waiting for answer. */
  def askActor[A, B](path: Seq[String], argument: A, timeout: Duration = Timeout.short): Option[B] = {
    getActorRef(path: _*).map { ref ⇒
      implicit val ec = App.system.dispatcher
      implicit val t = akka.util.Timeout(timeout.toMillis, TimeUnit.MILLISECONDS)
      Await.result(ref ? argument, timeout).asInstanceOf[B]
    }
  }
  /** Bind to the system event bus and waiting for message. */
  def askSystem[A, B](f: A ⇒ Option[B], timeout: Duration = Timeout.short)(implicit ev: ClassTag[A]): Option[B] = {
    implicit val system = App.system
    val mirror = runtimeMirror(getClass.getClassLoader)
    val exchanger = new Exchanger[B]()
    val limit = System.currentTimeMillis() + timeout.toMillis

    val listener = actor(new Act {
      become {
        case message ⇒ f(message.asInstanceOf[A]).foreach(result ⇒
          exchanger.exchange(result, math.min(limit - System.currentTimeMillis(), 0), TimeUnit.MILLISECONDS))
      }
      override def preStart() = App.system.eventStream.subscribe(self, ev.runtimeClass)
      override def postStop() = App.system.eventStream.unsubscribe(self, ev.runtimeClass)
    })
    try Option(exchanger.exchange(null.asInstanceOf[B], timeout.toMillis, TimeUnit.MILLISECONDS))
    finally App.system.stop(listener)
  }
  /** Get actor context via ActorSelection. */
  def getActorContext[T](selection: ActorSelection, timeout: Long = Timeout.longer.toMillis, period: Long = Timeout.shortest.toMillis): Option[ActorRef] = {
    log.debug("Lookup for reference via " + selection)
    val mark = System.currentTimeMillis() + timeout
    val result = new AtomicReference[Option[ActorRef]](null)
    val f: Option[ActorRef] ⇒ Unit = (ref) ⇒ result.synchronized {
      result.set(ref)
      result.notifyAll()
    }
    selection.tell(Identify(f), supportActor)
    while ((Option(result.get) getOrElse None).isEmpty && System.currentTimeMillis() < mark)
      result.synchronized {
        result.wait(period)
        selection.tell(Identify(f), supportActor)
      }

    Option(result.get) getOrElse None match {
      case result @ Some(_) ⇒
        log.debug(s"Lookup for ${selection} (${System.currentTimeMillis() - (mark - timeout)}ms) successful.")
        result
      case result @ None ⇒
        log.debug(s"Lookup for ${selection} (${System.currentTimeMillis() - (mark - timeout)}ms) failed.")
        result
    }
  }
  /** Get ActorRef via Path. */
  def getActorRef(path: String*): Option[ActorRef] =
    getActorRef(App.system.actorSelection(App.system / path))
  /** Get ActorRef via Path. */
  def getActorRef(path: Seq[String], timeout: Long): Option[ActorRef] =
    getActorRef(App.system.actorSelection(App.system / path), timeout)
  /** Get ActorRef via Path. */
  def getActorRef(path: Seq[String], timeout: Long, period: Long): Option[ActorRef] =
    getActorRef(App.system.actorSelection(App.system / path), timeout, period)
  /** Get ActorRef via ActorPath. */
  def getActorRef(path: ActorPath): Option[ActorRef] =
    getActorRef(system.actorSelection(path), Timeout.long.toMillis)
  /** Get ActorRef via ActorSelection. */
  def getActorRef(path: ActorPath, timeout: Long): Option[ActorRef] =
    getActorRef(system.actorSelection(path), timeout, Timeout.shortest.toMillis)
  /** Get ActorRef via ActorSelection. */
  def getActorRef(path: ActorPath, timeout: Long, period: Long): Option[ActorRef] =
    getActorRef(system.actorSelection(path), timeout, period)
  /** Get ActorRef via ActorSelection. */
  def getActorRef(selection: ActorSelection): Option[ActorRef] =
    getActorRef(selection, Timeout.long.toMillis)
  /** Get ActorRef via ActorSelection. */
  def getActorRef(selection: ActorSelection, timeout: Long): Option[ActorRef] =
    getActorRef(selection, timeout, Timeout.shortest.toMillis)
  /** Get ActorRef via ActorSelection. */
  def getActorRef(selection: ActorSelection, timeout: Long, period: Long): Option[ActorRef] = {
    log.debug("Lookup for reference via " + selection)
    val mark = System.currentTimeMillis() + timeout
    val result = new AtomicReference[Option[ActorRef]](null)
    val f: Option[ActorRef] ⇒ Unit = (ref) ⇒ result.synchronized {
      result.set(ref)
      result.notifyAll()
    }
    selection.tell(Identify(f), supportActor)
    while ((Option(result.get) getOrElse None).isEmpty && System.currentTimeMillis() < mark)
      result.synchronized {
        result.wait(period)
        selection.tell(Identify(f), supportActor)
      }

    Option(result.get) getOrElse None match {
      case result @ Some(_) ⇒
        log.debug(s"Lookup for ${selection} (${System.currentTimeMillis() - (mark - timeout)}ms) successful.")
        result
      case result @ None ⇒
        log.debug(s"Lookup for ${selection} (${System.currentTimeMillis() - (mark - timeout)}ms) failed.")
        result
    }
  }
  /** Publish event to Akka Event Bus */
  def publish = system.eventStream.publish _
  /** Send argument to the actor. */
  def tellActor[A](path: Seq[String], argument: A): Unit =
    getActorRef(path: _*).map { _ ! argument }
  def traceMessage[T](message: AnyRef)(f: ⇒ T)(implicit l: XRichLogger): Option[T] = try {
    l.trace(s"enteringHandler '${message}'")
    val result = f
    l.trace(s"leavingHandler '${message}'")
    Some(result)
  } catch {
    case e: Throwable ⇒
      l.error(e.getMessage, e)
      None
  }
}

object Akka extends XLoggable {
  class Actor extends akka.actor.Actor {
    log.debug("Start internal actor " + self.path)

    def receive = {
      case ActorIdentity(id, Some(actorRef)) ⇒
        if (id.isInstanceOf[Function1[_, _]])
          id.asInstanceOf[Option[ActorRef] ⇒ Unit](Some(actorRef))
      case ActorIdentity(id, None) ⇒ // not alive
        if (id.isInstanceOf[Function1[_, _]])
          id.asInstanceOf[Option[ActorRef] ⇒ Unit](None)
    }
  }
}
