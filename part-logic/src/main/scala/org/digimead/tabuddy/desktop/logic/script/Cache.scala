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

package org.digimead.tabuddy.desktop.logic.script

import com.google.common.cache.{ CacheBuilder, CacheLoader, LoadingCache, RemovalListener, RemovalNotification }
import java.util.concurrent.TimeUnit
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import scala.language.implicitConversions
import scala.util.DynamicVariable

/**
 * Global, application wide cache with script containers.
 */
class Cache {
  /** Thread local script evaluator. */
  protected val evaluator = new DynamicVariable[Option[() ⇒ Script.Container[_]]](None)
  /** Cache storage. */
  val storage = CacheBuilder.newBuilder().
    softValues().
    maximumSize(Cache.maxSize).
    expireAfterAccess(Cache.entryTTL, TimeUnit.SECONDS).
    removalListener(new Cache.ScriptRemovalListener).
    build(new Cache.ScriptLoader)
}

object Cache extends Loggable {
  implicit def cache2implementation(c: Cache.type): Cache = c.inner
  implicit def cache2storage(c: Cache.type): LoadingCache[String, Script.Container[_]] = c.inner.storage

  /** Get Cache entry time to live (in seconds). */
  def entryTTL = DI.entryTTL
  /** Get Cache implementation. */
  def inner = DI.implementation
  /** Get Cache maximum size. */
  def maxSize = DI.maxSize
  /** Get cached or evaluate new script. */
  def withScript[T](unique: String)(f: ⇒ Script.Container[T]): Script.Container[T] =
    inner.evaluator.withValue(Some(() ⇒ f)) { inner.storage.get(unique).asInstanceOf[Script.Container[T]] }

  /**
   * Thread local loader.
   */
  class ScriptLoader extends CacheLoader[String, Script.Container[_]] {
    def load(unique: String): Script.Container[_] = {
      Cache.log.debug("Looking up script container with key:" + unique)
      val evaluator = inner.evaluator.value.get // throw NoSuchElementException as expected
      val container = evaluator()
      if (container.className != "Evaluator__" + unique)
        throw new IllegalArgumentException(s"Expect ${"Evaluator__" + unique} but found ${container.className}")
      container
    }
  }
  /**
   * Clear script container before dispose.
   */
  class ScriptRemovalListener extends RemovalListener[String, Script.Container[_]] {
    val lock = new Object
    def onRemoval(notification: RemovalNotification[String, Script.Container[_]]) = lock.synchronized {
      Cache.log.debug(s"Script container associated with the key(${notification.getKey()}) is removed.")
      Option(notification.getValue()).foreach(_.clear()) // value maybe GC'ed
    }
  }
  /**
   * Dependency injection routines
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** Cache implementation. */
    lazy val implementation = injectOptional[Cache] getOrElse new Cache
    /** Cache maximum size. */
    lazy val maxSize = injectOptional[Int]("Script.Cache.MaxSize") getOrElse 100
    /** Cache entry time to live (in seconds). */
    lazy val entryTTL = injectOptional[Long]("Script.Cache.TTL") getOrElse 3600L // 1h
  }
}
