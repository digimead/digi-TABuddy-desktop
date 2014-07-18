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

package org.digimead.tabuddy.desktop.core.support

import org.eclipse.core.databinding.observable.{ ChangeEvent, IChangeListener, Realm }
import org.eclipse.core.databinding.observable.map.{ WritableMap ⇒ OriginalWritableMap }
import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.language.implicitConversions

class WritableMap[A, B](val underlying: OriginalWritableMap) extends mutable.Map[A, B] with mutable.MapLike[A, B, WritableMap[A, B]] with Equals {
  override def size = {
    App.assertEventThread()
    if (underlying.isDisposed())
      throw new IllegalStateException("Size called on disposed observable " + underlying)
    underlying.size
  }

  def get(k: A) = {
    App.assertEventThread()
    if (underlying.isDisposed())
      throw new IllegalStateException("Get called on disposed observable " + underlying)
    val v = underlying get k
    if (v != null)
      Some(v.asInstanceOf[B])
    else if (underlying containsKey k)
      Some(null.asInstanceOf[B])
    else
      None
  }

  def +=(kv: (A, B)): this.type = {
    App.assertEventThread()
    if (underlying.isDisposed())
      throw new IllegalStateException("Add called on disposed observable " + underlying)
    underlying.put(kv._1, kv._2); this
  }
  def -=(key: A): this.type = {
    App.assertEventThread()
    if (underlying.isDisposed())
      throw new IllegalStateException("Remove called on disposed observable " + underlying)
    underlying remove key; this
  }

  override def put(k: A, v: B): Option[B] = {
    App.assertEventThread()
    if (underlying.isDisposed())
      throw new IllegalStateException("Put called on disposed observable " + underlying)
    val r = underlying.put(k, v)
    if (r != null) Some(r.asInstanceOf[B]) else None
  }

  override def remove(k: A): Option[B] = {
    App.assertEventThread()
    if (underlying.isDisposed())
      throw new IllegalStateException("Remove called on disposed observable " + underlying)
    val r = underlying remove k
    if (r != null) Some(r.asInstanceOf[B]) else None
  }

  def iterator: Iterator[(A, B)] = {
    App.assertEventThread()
    if (underlying.isDisposed())
      throw new IllegalStateException("Iterator called on disposed observable " + underlying)
    new Iterator[(A, B)] {
      val ui = underlying.entrySet.iterator.asInstanceOf[java.util.Iterator[java.util.Map.Entry[A, B]]]
      def hasNext = ui.hasNext
      def next() = { val e = ui.next(); (e.getKey, e.getValue) }
    }
  }

  override def clear() = {
    App.assertEventThread()
    if (underlying.isDisposed())
      throw new IllegalStateException("Clear called on disposed observable " + underlying)
    underlying.clear()
  }

  override def empty: WritableMap[A, B] = {
    App.assertEventThread()
    if (underlying.isDisposed())
      throw new IllegalStateException("Empty called on disposed observable " + underlying)
    new WritableMap(new OriginalWritableMap(underlying.getRealm(), underlying.getKeyType(), underlying.getValueType()))
  }

  // thread safe
  def addChangeListener[T](listenerCallback: (ChangeEvent) ⇒ T): IChangeListener = {
    val listener = new IChangeListener() { override def handleChange(event: ChangeEvent) = listenerCallback(event) }
    underlying.addChangeListener(listener)
    listener
  }

  /**
   * A method that should be called from every well-designed equals method
   *  that is open to be overridden in a subclass. See Programming in Scala,
   *  Chapter 28 for discussion and design.
   *
   *  @param    that    the value being probed for possible equality
   *  @return   true if this instance can possibly equal `that`, otherwise false
   */
  override def canEqual(that: Any): Boolean = that.isInstanceOf[WritableMap[A, B]]
  /**
   * The universal equality method defined in `AnyRef`.
   */
  override def equals(that: Any): Boolean = that match {
    case that: WritableMap[_, _] ⇒ (that canEqual this) && this.underlying.equals(that.underlying)
    case _ ⇒ false
  }
  /** HashCode from underlying. */
  override def hashCode() = this.underlying.hashCode()

  override def toString() = if (App.isEventLoop())
    s"WritableMap {${
      (for (entry ← underlying.entrySet())
        yield entry.asInstanceOf[java.util.Map.Entry[_, _]].getKey() + "->" +
        entry.asInstanceOf[java.util.Map.Entry[_, _]].getValue()).mkString(",")
    }}"
  else
    "WritableMap {*Wrong Thread*}"
}

object WritableMap {
  implicit def wrapper2underlying(wrapper: WritableMap[_, _]): OriginalWritableMap = {
    App.assertEventThread()
    wrapper.underlying
  }
  // Use the unit as the method indicator
  def apply[P <: AnyRef: Manifest, Q <: AnyRef: Manifest](indicator: Unit, underlying: OriginalWritableMap): WritableMap[P, Q] =
    new WritableMap[P, Q](underlying)
  def apply[P <: AnyRef, Q <: AnyRef](implicit p: Manifest[P], q: Manifest[Q]): WritableMap[P, Q] =
    new WritableMap[P, Q](new OriginalWritableMap(Realm.getDefault(), p.runtimeClass, q.runtimeClass))
  def apply[P <: AnyRef, Q <: AnyRef](map: Map[P, Q])(implicit p: Manifest[P], q: Manifest[Q]): WritableMap[P, Q] = {
    val wmap = new WritableMap[P, Q](new OriginalWritableMap(p.runtimeClass, q.runtimeClass))
    map.foreach(t ⇒ wmap(t._1) = t._2)
    wmap
  }
  def apply[P <: AnyRef, Q <: AnyRef](realm: Realm, map: Map[P, Q])(implicit p: Manifest[P], q: Manifest[Q]): WritableMap[P, Q] = {
    val wmap = new WritableMap[P, Q](new OriginalWritableMap(realm, p.runtimeClass, q.runtimeClass))
    map.foreach(t ⇒ wmap(t._1) = t._2)
    wmap
  }
}
