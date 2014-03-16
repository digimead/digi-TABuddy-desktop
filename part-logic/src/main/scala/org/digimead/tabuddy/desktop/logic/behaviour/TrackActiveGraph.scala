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

package org.digimead.tabuddy.desktop.logic.behaviour

import java.util.concurrent.locks.ReentrantReadWriteLock
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.logic.payload.maker.GraphMarker
import scala.language.implicitConversions

class TrackActiveGraph extends Loggable {
  /** Set of tracked graphs. */
  protected var trackObjects = Set[GraphMarker]()
  /** Set of track listeners. */
  protected var trackListeners = Set[TrackActiveGraph.Listener]()
  /** TrackActiveGraph lock. */
  protected val lock = new ReentrantReadWriteLock()

  /** Get active graphs. */
  def active = {
    lock.readLock().lock()
    try trackObjects
    finally lock.readLock().unlock()
  }
  /** Add listener. */
  def addListener[T](listener: TrackActiveGraph.Listener, runSafe: () ⇒ T = null) {
    lock.writeLock().lock()
    try {
      trackListeners = trackListeners + listener
      if (runSafe != null) runSafe()
    } finally lock.writeLock().unlock()
  }
  /** Remove marker from tracked objects. */
  def close(marker: GraphMarker) = {
    lock.writeLock().lock()
    try if (trackObjects(marker)) {
      log.debug(s"Remove ${marker} from tracked objects.")
      trackObjects = trackObjects - marker
      trackListeners.par.foreach(listener ⇒ try listener.close(marker) catch { case e: Throwable ⇒ log.error(e.getMessage, e) })
    } finally lock.writeLock().unlock()
  }
  /** Add marker to tracked objects. */
  def open(marker: GraphMarker) = {
    lock.writeLock().lock()
    try if (!trackObjects(marker)) {
      log.debug(s"Add ${marker} to tracked objects.")
      trackObjects = trackObjects + marker
      trackListeners.par.foreach(listener ⇒ try listener.open(marker) catch { case e: Throwable ⇒ log.error(e.getMessage, e) })
    } finally lock.writeLock().unlock()
  }
  /** Remove listener. */
  def removeListener[T](listener: TrackActiveGraph.Listener, runSafe: () ⇒ T = null) {
    lock.writeLock().lock()
    try {
      trackListeners = trackListeners - listener
      if (runSafe != null) runSafe()
    } finally lock.writeLock().unlock()
  }
}

object TrackActiveGraph {
  implicit def class2implementation(c: TrackActiveGraph.type): TrackActiveGraph = c.inner

  /** TrackActiveGraph implementation. */
  def inner = DI.implementation

  /** Active graph listener.*/
  trait Listener {
    /** Remove marker from tracked objects. */
    def close(marker: GraphMarker)
    /** Add marker to tracked objects. */
    def open(marker: GraphMarker)
  }
  /**
   * Dependency injection routines.
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** TrackActiveGraph implementation. */
    lazy val implementation = injectOptional[TrackActiveGraph] getOrElse new TrackActiveGraph
  }
}
