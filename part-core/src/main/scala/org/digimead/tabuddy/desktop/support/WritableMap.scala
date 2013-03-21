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

import scala.collection.mutable

import org.digimead.tabuddy.desktop.Main
import org.eclipse.core.databinding.observable.ChangeEvent
import org.eclipse.core.databinding.observable.IChangeListener
import org.eclipse.core.databinding.observable.Realm
import org.eclipse.core.databinding.observable.map.{ WritableMap => OriginalWritableMap }

import language.implicitConversions

case class WritableMap[A, B] private (val underlying: OriginalWritableMap) extends mutable.Map[A, B] with mutable.MapLike[A, B, WritableMap[A, B]] {
  override def size = {
    Main.checkThread
    underlying.size
  }

  def get(k: A) = {
    Main.checkThread
    val v = underlying get k
    if (v != null)
      Some(v.asInstanceOf[B])
    else if (underlying containsKey k)
      Some(null.asInstanceOf[B])
    else
      None
  }

  def +=(kv: (A, B)): this.type = {
    Main.checkThread
    underlying.put(kv._1, kv._2); this
  }
  def -=(key: A): this.type = {
    Main.checkThread
    underlying remove key; this
  }

  override def put(k: A, v: B): Option[B] = {
    Main.checkThread
    val r = underlying.put(k, v)
    if (r != null) Some(r.asInstanceOf[B]) else None
  }

  override def remove(k: A): Option[B] = {
    Main.checkThread
    val r = underlying remove k
    if (r != null) Some(r.asInstanceOf[B]) else None
  }

  def iterator: Iterator[(A, B)] = {
    Main.checkThread
    new Iterator[(A, B)] {
      val ui = underlying.entrySet.iterator.asInstanceOf[java.util.Iterator[java.util.Map.Entry[A, B]]]
      def hasNext = ui.hasNext
      def next() = { val e = ui.next(); (e.getKey, e.getValue) }
    }
  }

  override def clear() = {
    Main.checkThread
    underlying.clear()
  }

  override def empty: WritableMap[A, B] = {
    Main.checkThread
    WritableMap(new OriginalWritableMap(underlying.getRealm(), underlying.getKeyType(), underlying.getValueType()))
  }

  def addChangeListener[T](listenerCallback: (ChangeEvent) => T): IChangeListener = {
    val listener = new IChangeListener() { override def handleChange(event: ChangeEvent) = listenerCallback(event) }
    underlying.addChangeListener(listener)
    listener
  }
}

object WritableMap {
  implicit def wrapper2underlying(wrapper: WritableMap[_, _]): OriginalWritableMap = {
    Main.checkThread
    wrapper.underlying
  }
  // Use the unit as the method indicator
  def apply[P <: AnyRef: Manifest, Q <: AnyRef: Manifest](indicator: Unit, underlying: OriginalWritableMap): WritableMap[P, Q] =
    WritableMap[P, Q](underlying)
  def apply[P <: AnyRef, Q <: AnyRef](implicit p: Manifest[P], q: Manifest[Q]): WritableMap[P, Q] =
    WritableMap[P, Q](new OriginalWritableMap(Realm.getDefault(), p.runtimeClass, q.runtimeClass))
  def apply[P <: AnyRef, Q <: AnyRef](map: Map[P, Q])(implicit p: Manifest[P], q: Manifest[Q]): WritableMap[P, Q] = {
    val wmap = WritableMap[P, Q](new OriginalWritableMap(p.runtimeClass, q.runtimeClass))
    map.foreach(t => wmap(t._1) = t._2)
    wmap
  }
  def apply[P <: AnyRef, Q <: AnyRef](realm: Realm, map: Map[P, Q])(implicit p: Manifest[P], q: Manifest[Q]): WritableMap[P, Q] = {
    val wmap = WritableMap[P, Q](new OriginalWritableMap(realm, p.runtimeClass, q.runtimeClass))
    map.foreach(t => wmap(t._1) = t._2)
    wmap
  }
}
