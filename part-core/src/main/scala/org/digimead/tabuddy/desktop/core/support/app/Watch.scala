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

import java.util.concurrent.{ Exchanger, ExecutionException, TimeUnit }
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.support.App
import scala.collection.mutable
import scala.concurrent.{ Await, Future }
import scala.concurrent.duration.Duration

/**
 * Watch trait that adds ability to track references(objects) and call hooks on start/stop
 */
trait Watch {
  /** Map of watchers. */
  protected val watchRef = mutable.HashMap[Int, Seq[Watch.Watcher]]()
  /** Set with started references. */
  protected val watchSet = mutable.HashSet[Int]()

  /** Get new watcher. */
  def watch(refs: AnyRef*): Watch.Watcher = new Watch.Watcher(refs.map(System.identityHashCode).toSet, new Throwable("Refs: " + refs.mkString(",")))
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
  class Watcher(val ids: Set[Int], val t: Throwable) extends Loggable {
    @volatile protected var argActive = false
    protected var argTimes = 1
    protected var hookAfterStart = Seq.empty[(Int, Function0[_])]
    protected var hookAfterStop = Seq.empty[(Int, Function0[_])]
    protected var hookBeforeStart = Seq.empty[(Int, Function0[_])]
    protected var hookBeforeStop = Seq.empty[(Int, Function0[_])]

    /** Get active state. */
    def isActive = App.watchSet.synchronized { argActive }
    /** Check that there are no hooks. */
    def isEmpty = {
      val h = hooks
      h._1.isEmpty && h._2.isEmpty && h._3.isEmpty && h._4.isEmpty
    }
    /** Execute always. */
    def always(): Watcher = times(-1)
    /** Get all hooks for such type of watchers. */
    def hooks: (Seq[(Int, Function0[_])], Seq[(Int, Function0[_])], Seq[(Int, Function0[_])], Seq[(Int, Function0[_])]) = App.watchSet.synchronized {
      val likeThisWatchers = ids.flatMap(w ⇒ App.watchRef.get(w).map(_.filter(_.ids == ids))).flatten // get all watchers sequences for Id set
      val seqAfterStart = hookAfterStart.genericBuilder[Seq[(Int, Function0[_])]]
      val seqAfterStop = hookAfterStop.genericBuilder[Seq[(Int, Function0[_])]]
      val seqBeforeStart = hookBeforeStart.genericBuilder[Seq[(Int, Function0[_])]]
      val seqBeforeStop = hookBeforeStop.genericBuilder[Seq[(Int, Function0[_])]]
      likeThisWatchers.foreach { watcher ⇒
        seqAfterStart += watcher.hookAfterStart
        seqAfterStop += watcher.hookAfterStop
        seqBeforeStart += watcher.hookBeforeStart
        seqBeforeStop += watcher.hookBeforeStop
      }
      (seqAfterStart.result().flatten, seqAfterStop.result().flatten, seqBeforeStart.result().flatten, seqBeforeStop.result().flatten)
    }
    /** Make AfterStart hook. */
    def makeAfterStart[A](f: ⇒ A): Watcher =
      App.watchSet.synchronized { hookAfterStart = addHook(() ⇒ f, hookAfterStart); this }
    /** Make AfterStop hook. */
    def makeAfterStop[A](f: ⇒ A): Watcher =
      App.watchSet.synchronized { hookAfterStop = addHook(() ⇒ f, hookAfterStop); this }
    /** Make BeforeStart hook. */
    def makeBeforeStart[A](f: ⇒ A): Watcher =
      App.watchSet.synchronized { hookBeforeStart = addHook(() ⇒ f, hookBeforeStart); this }
    /** Make BeforeStop hook. */
    def makeBeforeStop[A](f: ⇒ A): Watcher =
      App.watchSet.synchronized { hookBeforeStop = addHook(() ⇒ f, hookBeforeStop); this }
    /** Check that there is at least one hook. */
    def nonEmpty = isEmpty
    /** Stop Id's. */
    @log
    def off[A](f: ⇒ A = {}): A = {
      log.debug("off " + this)
      val (before, after, watchers) = App.watchSet.synchronized {
        if (ids.forall(!App.watchSet(_)))
          throw new IllegalStateException(this + " is already off")
        val activeWatchers = ids.flatMap {
          case id if App.watchSet(id) ⇒ App.watchRef.get(id)
          case _ ⇒ None
        }.flatten // get all active watchers sequences for Id set
        App.watchSet --= ids
        val inactiveWatchers = activeWatchers.filter(w ⇒ w.argActive && { w.argActive = false; true })
        argActive = false
        val (before, after) = inactiveWatchers.map { watcher ⇒
          val before = watcher.hookBeforeStop.map(_._2)
          watcher.hookBeforeStop = watcher.hookBeforeStop.map {
            case t @ (times, callback) if times == -1 ⇒ Some(t) // Infinite
            case (times, callback) if times > 1 ⇒ Some(times - 1, callback) // Decrement
            case (times, callback) ⇒ None // Delete
          }.flatten
          val after = watcher.hookAfterStop.map(_._2)
          watcher.hookAfterStop = watcher.hookAfterStop.map {
            case t @ (times, callback) if times == -1 ⇒ Some(t) // Infinite
            case (times, callback) if times > 1 ⇒ Some(times - 1, callback) // Decrement
            case (times, callback) ⇒ None // Delete
          }.flatten
          (before, after)
        }.unzip
        (before.flatten, after.flatten, inactiveWatchers)
      }
      process(before)
      val result = f
      process(after + (() ⇒ watchers.map(_.compress)))
      result
    }
    /** Start Id's. */
    @log
    def on[A](f: ⇒ A = {}): A = {
      log.debug("on " + this)
      val (before, after, watchers) = App.watchSet.synchronized {
        if (ids.forall(App.watchSet))
          throw new IllegalStateException(this + " is already on")
        val inactiveWatchers = ids.flatMap {
          case id if !App.watchSet(id) ⇒ App.watchRef.get(id)
          case _ ⇒ None
        }.flatten // get all inactive watchers sequences for Id set
        App.watchSet ++= ids // activate
        val activeWatchers = inactiveWatchers.filter(w ⇒ w.ids.forall(App.watchSet) && !w.argActive && { w.argActive = true; true })
        argActive = true
        val (before, after) = activeWatchers.map { watcher ⇒
          val before = watcher.hookBeforeStart.map(_._2)
          watcher.hookBeforeStart = watcher.hookBeforeStart.map {
            case t @ (times, callback) if times == -1 ⇒ Some(t) // Infinite
            case (times, callback) if times > 1 ⇒ Some(times - 1, callback) // Decrement
            case (times, callback) ⇒ None // Delete
          }.flatten
          val after = watcher.hookAfterStart.map(_._2)
          watcher.hookAfterStart = watcher.hookAfterStart.map {
            case t @ (times, callback) if times == -1 ⇒ Some(t) // Infinite
            case (times, callback) if times > 1 ⇒ Some(times - 1, callback) // Decrement
            case (times, callback) ⇒ None // Delete
          }.flatten
          (before, after)
        }.unzip
        (before.flatten, after.flatten, activeWatchers)
      }
      process(before)
      val result = f
      process(after + (() ⇒ watchers.map(_.compress)))
      result
    }
    /** Execute once. */
    def once(): Watcher = times(1)
    /** Reset and remove this watcher. */
    def reset(): Unit = App.watchSet.synchronized {
      hookAfterStart = Seq.empty
      hookAfterStop = Seq.empty
      hookBeforeStart = Seq.empty
      hookBeforeStop = Seq.empty
      argActive = ids.forall(App.watchSet)
      compress()
    }
    /** Synchronize watcher state. */
    def sync(): Unit = {
      val hooks = App.watchSet.synchronized {
        val actual = ids.forall(App.watchSet)
        if (argActive != actual) {
          argActive = actual
          if (actual)
            hookBeforeStart.map(_._2) ++ hookAfterStart.map(_._2)
          else
            hookBeforeStop.map(_._2) ++ hookAfterStop.map(_._2)
        } else
          Seq.empty
      }
      process(hooks)
    }
    /** Set times argument for new hooks */
    def times(n: Int): Watcher = App.watchSet.synchronized {
      argTimes = n
      this
    }
    /** Wait for start. */
    def waitForStart(timeout: Duration = Duration.Inf): Watcher = {
      val exchanger = App.watchSet.synchronized {
        if (ids.forall(App.watchSet)) {
          argActive = true
          return this
        }
        argActive = ids.forall(App.watchSet) // force because we will wait termination
        val exchanger = new Exchanger[Null]
        val savedArgTimes = argTimes
        argTimes = 1
        makeAfterStart { exchanger.exchange(null) }
        argTimes = savedArgTimes
        exchanger
      }
      if (timeout.isFinite())
        exchanger.exchange(null, timeout.toMillis, TimeUnit.MILLISECONDS)
      else
        exchanger.exchange(null)
      this
    }
    /** Wait for stop. */
    def waitForStop(timeout: Duration = Duration.Inf): Watcher = {
      val exchanger = App.watchSet.synchronized {
        if (ids.forall(!App.watchSet(_))) {
          argActive = false
          return this
        }
        argActive = ids.forall(App.watchSet) // force because we will wait termination
        val exchanger = new Exchanger[Null]
        val savedArgTimes = argTimes
        argTimes = 1
        makeAfterStop { exchanger.exchange(null) }
        argTimes = savedArgTimes
        exchanger
      }
      if (timeout.isFinite())
        exchanger.exchange(null, timeout.toMillis, TimeUnit.MILLISECONDS)
      else
        exchanger.exchange(null)
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
    // callbacks.map(_()) - single threaded version
    protected def process(callbacks: Iterable[Function0[_]]) =
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
    protected def compress() = App.watchSet.synchronized {
      if (hookAfterStart.isEmpty && hookAfterStop.isEmpty && hookBeforeStart.isEmpty && hookBeforeStop.isEmpty) {
        App.watchRef.foreach {
          case (key, value) ⇒
            val compressed = value.filterNot(_ == this)
            if (compressed.isEmpty) {
              if (!argActive)
                App.watchRef.remove(key)
            } else if (compressed.size != value.size)
              App.watchRef(key) = compressed
        }
      }
    }

    override def toString() = s"Watcher(${t.getMessage()})"
  }
}
