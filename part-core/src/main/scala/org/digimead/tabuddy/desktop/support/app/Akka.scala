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
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.MainService
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.Timeout
import akka.actor.ActorIdentity
import akka.actor.ActorRef
import akka.actor.ActorSelection
import akka.actor.Identify
import akka.actor.Props
import org.digimead.digi.lib.log.api.RichLogger

/**
 * Akka support trait
 */
trait Akka {
  this: MainService.Consumer with Loggable =>
  /** Support actor. */
  protected lazy val supportActor = system.actorOf(Props(classOf[Akka.Actor]), "Support")

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
  /** Get ActorRef via ActorSelection. */
  def getActorRef[T](selection: ActorSelection, timeout: Long = Timeout.long.toMillis, period: Long = Timeout.shortest.toMillis): Option[ActorRef] = {
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
