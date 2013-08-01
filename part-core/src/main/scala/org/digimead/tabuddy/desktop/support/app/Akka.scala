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

package org.digimead.tabuddy.desktop.support.app

import java.util.concurrent.atomic.AtomicReference

import scala.concurrent.Await

import org.digimead.digi.lib.log.api.Loggable
import org.digimead.digi.lib.log.api.RichLogger
import org.digimead.tabuddy.desktop.MainService
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.App.app2implementation
import org.digimead.tabuddy.desktop.support.Timeout

import akka.actor.ActorIdentity
import akka.actor.ActorPath
import akka.actor.ActorRef
import akka.actor.ActorSelection
import akka.actor.Identify
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.pattern.ask

/**
 * Akka support trait
 */
trait Akka {
  this: MainService.Consumer with Loggable =>
  /** Support actor. */
  // System.nanoTime is needed because we may have more than one supportActor per JVM
  protected lazy val supportActor = system.actorOf(Props(classOf[Akka.Actor]), "Support." + System.nanoTime())

  /** Send argument to the actor and waiting for answer. */
  def askActor[A, B](path: Seq[String], argument: A): Option[B] = {
    getActorRef(path: _*).map { ref =>
      implicit val ec = App.system.dispatcher
      implicit val timeout = akka.util.Timeout(Timeout.shortest)
      Await.result(ref ? argument, Timeout.short).asInstanceOf[B]
    }
  }
  /** Get actor context via ActorSelection. */
  def getActorContext[T](selection: ActorSelection, timeout: Long = Timeout.longer.toMillis, period: Long = Timeout.shortest.toMillis): Option[ActorRef] = {
    log.debug("Lookup for reference via " + selection)
    val mark = System.currentTimeMillis() + timeout
    val result = new AtomicReference[Option[ActorRef]](null)
    val f: Option[ActorRef] => Unit = (ref) => result.synchronized {
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
      case result @ Some(_) =>
        log.debug(s"Lookup for ${selection} (${System.currentTimeMillis() - (mark - timeout)}ms) successful.")
        result
      case result @ None =>
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
    val f: Option[ActorRef] => Unit = (ref) => result.synchronized {
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
      case result @ Some(_) =>
        log.debug(s"Lookup for ${selection} (${System.currentTimeMillis() - (mark - timeout)}ms) successful.")
        result
      case result @ None =>
        log.debug(s"Lookup for ${selection} (${System.currentTimeMillis() - (mark - timeout)}ms) failed.")
        result
    }
  }
  /** Send argument to the actor. */
  def tellActor[A](path: Seq[String], argument: A): Unit =
    getActorRef(path: _*).map { _ ! argument }
  def traceMessage[T](message: AnyRef)(f: => T)(implicit l: RichLogger): T = try {
    l.trace(s"enteringHandler '${message}'")
    val result = f
    l.trace(s"leavingHandler '${message}'")
    result
  } catch {
    case e: Throwable =>
      l.error(e.getMessage, e)
      throw e
  }
}

object Akka extends Loggable {
  class Actor extends akka.actor.Actor {
    log.debug("Start actor " + self.path)

    def receive = {
      case ActorIdentity(id, Some(actorRef)) =>
        if (id.isInstanceOf[Function1[_, _]])
          id.asInstanceOf[Option[ActorRef] => Unit](Some(actorRef))
      case ActorIdentity(id, None) => // not alive
        if (id.isInstanceOf[Function1[_, _]])
          id.asInstanceOf[Option[ActorRef] => Unit](None)
    }
  }
}
