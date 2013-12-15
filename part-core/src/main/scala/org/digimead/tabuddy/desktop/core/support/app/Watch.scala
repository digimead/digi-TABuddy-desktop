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

import java.io.{ PrintWriter, StringWriter }
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.EventLoop
import org.eclipse.core.databinding.observable.Realm
import org.eclipse.core.runtime.{ IStatus, MultiStatus, Status }
import org.eclipse.core.runtime.preferences.InstanceScope
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.ui.preferences.ScopedPreferenceStore
import org.osgi.framework.{ Bundle, FrameworkUtil }
import scala.Array.canBuildFrom
import scala.collection.mutable
import java.util.concurrent.locks.ReentrantReadWriteLock
import scala.concurrent.duration.Duration
import org.digimead.tabuddy.desktop.core.support.App
import scala.concurrent.Future
import scala.concurrent.Await
import java.util.concurrent.ExecutionException

trait Watch {
  /** Map of watchers. */
  protected val watchRef = new mutable.HashMap[Int, Seq[Watch.Watcher]]()
  /** Set with started references. */
  protected val watchSet = new mutable.HashSet[Int]()

  /** Get new watcher. */
  def watch(refs: AnyRef*): Watch.Watcher = new Watch.Watcher(refs.map(System.identityHashCode).distinct, new Throwable("Refs: " + refs.mkString(",")))
  /** Reset watchers. */
  def watchResetList() = watchSet.synchronized {
    watchRef.clear()
    watchSet.clear()
  }
  /** Get reference state. */
  def isActive(ref: AnyRef) = watchSet.synchronized { watchSet(System.identityHashCode(ref)) }
}

/*
 * App.watch(myObject1, ...).once().always().timeout(n).afterStart({ ... })
 */
object Watch extends Loggable {
  implicit lazy val ec = App.system.dispatcher
  /** Watcher implementation. */
  class Watcher(val ids: Seq[Int], val t: Throwable) {
    protected var hookAfterStart = Seq.empty[(Int, Function0[_])]
    protected var hookAfterStop = Seq.empty[(Int, Function0[_])]
    protected var hookBeforeStart = Seq.empty[(Int, Function0[_])]
    protected var hookBeforeStop = Seq.empty[(Int, Function0[_])]
    protected var argTimes = 1

    def makeAfterStart[A](f: ⇒ A): Watcher = App.watchSet.synchronized {
      hookAfterStart = addHook(() ⇒ f, hookAfterStart)
      this
    }
    def makeAfterStop[A](f: ⇒ A): Watcher = App.watchSet.synchronized {
      hookAfterStop = addHook(() ⇒ f, hookAfterStop)
      this
    }
    def always(): Watcher = times(-1)
    def makeBeforeStart[A](f: ⇒ A): Watcher = App.watchSet.synchronized {
      hookBeforeStart = addHook(() ⇒ f, hookBeforeStart)
      this
    }
    def makeBeforeStop[A](f: ⇒ A): Watcher = App.watchSet.synchronized {
      hookBeforeStop = addHook(() ⇒ f, hookBeforeStop)
      this
    }
    def once(): Watcher = times(1)
    def reset(): Unit = App.watchSet.synchronized {
      hookAfterStart = Seq.empty
      hookAfterStop = Seq.empty
      hookBeforeStart = Seq.empty
      hookBeforeStop = Seq.empty
      compress()
    }
    def off[A](f: ⇒ A = {}): Watcher = {
      val (before, after) = App.watchSet.synchronized {
        if (ids.forall(!App.watchSet(_)))
          throw new IllegalStateException(this + " is already off")
        App.watchSet --= ids
        val before = hookBeforeStop.map(_._2)
        hookBeforeStop = hookBeforeStop.map {
          case t @ (times, callback) if times == -1 ⇒ Some(t) // Infinite
          case (times, callback) if times > 1 ⇒ Some(times - 1, callback) // Reduce
          case (times, callback) ⇒ None // Delete
        }.flatten
        val after = hookAfterStop.map(_._2)
        hookAfterStop = hookAfterStop.map {
          case t @ (times, callback) if times == -1 ⇒ Some(t) // Infinite
          case (times, callback) if times > 1 ⇒ Some(times - 1, callback) // Reduce
          case (times, callback) ⇒ None // Delete
        }.flatten
        (before, after)
      }
      process(before)
      f
      process(after :+ (() ⇒ App.watchSet.synchronized { compress }))
      this
    }
    def on[A](f: ⇒ A = {}): Watcher = {
      val (before, after) = App.watchSet.synchronized {
        if (ids.forall(App.watchSet))
          throw new IllegalStateException(this + " is already on")
        App.watchSet ++= ids
        val before = hookBeforeStart.map(_._2)
        hookBeforeStart = hookBeforeStart.map {
          case t @ (times, callback) if times == -1 ⇒ Some(t) // Infinite
          case (times, callback) if times > 1 ⇒ Some(times - 1, callback) // Reduce
          case (times, callback) ⇒ None // Delete
        }.flatten
        val after = hookAfterStart.map(_._2)
        hookAfterStart = hookAfterStart.map {
          case t @ (times, callback) if times == -1 ⇒ Some(t) // Infinite
          case (times, callback) if times > 1 ⇒ Some(times - 1, callback) // Reduce
          case (times, callback) ⇒ None // Delete
        }.flatten
        (before, after)
      }
      process(before)
      f
      process(after :+ (() ⇒ compress))
      this
    }
    def times(n: Int): Watcher = App.watchSet.synchronized {
      argTimes = n
      this
    }

    /** Add before/after hook. */
    protected def addHook[A](f: () ⇒ A, container: Seq[(Int, Function0[_])]): Seq[(Int, Function0[_])] = {
      if (hookAfterStart.isEmpty && hookAfterStop.isEmpty && hookBeforeStart.isEmpty && hookBeforeStop.isEmpty)
        // register
        ids.foreach { refId ⇒
          App.watchRef.get(refId) match {
            case Some(seq) ⇒ App.watchRef(refId) = seq :+ this
            case None ⇒ App.watchRef(refId) = Seq(this)
          }
        }
      container :+ (argTimes, f)
    }
    /** Process hooks. */
    protected def process(callbacks: Seq[Function0[_]]) =
      Await.ready(Future.sequence(callbacks.map { cb ⇒
        val f = Future[Unit] { cb() }
        f onFailure {
          case e ⇒
            val exception = new ExecutionException(e.getMessage(), e)
            exception.setStackTrace(t.getStackTrace())
            log.error(e.getMessage(), exception)
        }
        f
      }), Duration.Inf)
    /** Drop garbage. */
    protected def compress() =
      if (hookAfterStart.isEmpty && hookAfterStop.isEmpty && hookBeforeStart.isEmpty && hookBeforeStop.isEmpty) {
        App.watchRef.foreach {
          case (key, value) ⇒
            val compressed = value.filterNot(_ == this)
            if (compressed.isEmpty)
              App.watchRef.remove(key)
            else if (compressed.size != value.size)
              App.watchRef(key) = compressed
        }
      }
  }
}
