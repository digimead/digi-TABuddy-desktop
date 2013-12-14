/**
 * This file is part of the TA Buddy project.
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

import java.util.concurrent.Exchanger
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

import org.digimead.digi.lib.log.api.Loggable

trait Thread {
  this: Generic with Loggable ⇒
  /** Execute runnable in the event thread. */
  def exec[T](f: ⇒ T): Unit = if (debug) {
    val t = new Throwable("Entry point.")
    if (isEventLoop) {
      val ts = System.currentTimeMillis()
      f
      val duration = System.currentTimeMillis() - ts
      if (duration > 500)
        log.error(s"Too heavy operation: ${duration}ms.", t)
    } else execAsync({ f })
  } else {
    if (isEventLoop) { f } else execAsync({ f })
  }
  /** Asynchronously execute runnable in the event thread. */
  def execAsync[T](f: ⇒ T): Unit = if (debug) {
    val t = new Throwable("Entry point.")
    display.asyncExec(new Runnable {
      def run = try {
        val ts = System.currentTimeMillis()
        f
        val duration = System.currentTimeMillis() - ts
        if (duration > 500)
          log.error(s"Too heavy operation: ${duration}ms.", t)
      } catch { case e: Throwable ⇒ log.error("Event thread exception: " + e, e) }
    })
  } else {
    display.asyncExec(new Runnable {
      def run = try { f } catch { case e: Throwable ⇒ log.error("Event thread exception: " + e, e) }
    })
  }
  /** Execute blocking runnable with it's own event queue. */
  def execBlocking[T](f: ⇒ T): T = {
    if (isEventLoop) {
      f
    } else {
      val exchanger = new Exchanger[Either[Throwable, T]]()
      display.asyncExec(new Runnable {
        def run = try { exchanger.exchange(Right(f)) } catch { case e: Throwable ⇒ exchanger.exchange(Left(e)) }
      })
      exchanger.exchange(null) match {
        case Left(e) ⇒ throw e
        case Right(r) ⇒ r
      }
    }
  }
  /** Execute runnable in event thread and return result or exception. */
  def execNGet[T](f: ⇒ T): T = if (debug) {
    val t = new Throwable("Entry point.")
    if (isEventLoop) {
      val ts = System.currentTimeMillis()
      val result = f
      val duration = System.currentTimeMillis() - ts
      if (duration > 500)
        log.error(s"Too heavy operation: ${duration}ms.", t)
      result
    } else execNGetAsync({ f })
  } else {
    if (isEventLoop) { f } else execNGetAsync({ f })
  }
  /** Asynchronously execute runnable in event thread and return result or exception. */
  def execNGetAsync[T](f: ⇒ T): T = {
    val exchanger = new Exchanger[Either[Throwable, T]]()
    if (debug) {
      val t = new Throwable("Entry point.")
      display.asyncExec(new Runnable {
        def run = {
          val ts = System.currentTimeMillis()
          try { exchanger.exchange(Right(f)) } catch { case e: Throwable ⇒ exchanger.exchange(Left(e)) }
          val duration = System.currentTimeMillis() - ts
          if (duration > 500)
            log.error(s"Too heavy operation: ${duration}ms.", t)
        }
      })
    } else {
      display.asyncExec(new Runnable {
        def run = try { exchanger.exchange(Right(f)) } catch { case e: Throwable ⇒ exchanger.exchange(Left(e)) }
      })
    }
    exchanger.exchange(null) match {
      case Left(e) ⇒
        throw e
      case Right(r) ⇒
        r
    }
  }
  /**
   * Asynchronously execute runnable in event thread with timeout and return result or exception
   * NB This routine block event thread, so it would unusual to freeze application for a few hours.
   */
  @throws[TimeoutException]("If the specified waiting time elapses before another thread enters the exchange")
  def execNGetAsync[T](timeout: Int, unit: TimeUnit = TimeUnit.MILLISECONDS)(f: ⇒ T): T = {
    if (isEventLoop)
      throw new IllegalStateException("Unable to spawn execNGetAsync runnable with timeout within event thread.")
    val exchanger = new Exchanger[Either[Throwable, T]]()
    display.asyncExec(new Runnable {
      def run = try { exchanger.exchange(Right(f)) } catch { case e: Throwable ⇒ exchanger.exchange(Left(e)) }
    })
    exchanger.exchange(null, timeout, unit) match {
      case Left(e) ⇒
        throw e
      case Right(r) ⇒
        r
    }
  }
}