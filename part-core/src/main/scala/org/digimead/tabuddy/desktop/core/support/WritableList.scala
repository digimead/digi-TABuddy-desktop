/**
 * This file is part of the TA Buddy project.
 * Copyright (c) 2012-2013 Alexey Aksenov ezh@ezh.msk.ru
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

import java.util.ArrayList

import scala.collection.JavaConversions._
import scala.collection.mutable

import org.eclipse.core.databinding.observable.ChangeEvent
import org.eclipse.core.databinding.observable.IChangeListener
import org.eclipse.core.databinding.observable.Realm
import org.eclipse.core.databinding.observable.list.{ WritableList ⇒ OriginalWritableList }

import language.implicitConversions

class WritableList[A](val underlying: OriginalWritableList) extends mutable.Buffer[A] with Equals {
  def length = {
    App.assertEventThread()
    underlying.size
  }
  override def isEmpty = {
    App.assertEventThread()
    underlying.isEmpty
  }
  override def iterator: Iterator[A] = {
    App.assertEventThread()
    underlying.iterator.asInstanceOf[java.util.Iterator[A]]
  }
  def apply(i: Int) = {
    App.assertEventThread()
    underlying.get(i).asInstanceOf[A]
  }
  def update(i: Int, elem: A) = {
    App.assertEventThread()
    underlying.set(i, elem)
  }
  def +=:(elem: A) = {
    App.assertEventThread()
    underlying.subList(0, 0).asInstanceOf[java.util.List[A]] add elem
    this
  }
  def +=(elem: A): this.type = {
    App.assertEventThread()
    underlying add elem
    this
  }
  def insertAll(i: Int, elems: Traversable[A]) = {
    App.assertEventThread()
    val ins = underlying.subList(0, i).asInstanceOf[java.util.List[A]]
    elems.seq.foreach(ins.add(_))
  }
  def remove(i: Int) = {
    App.assertEventThread()
    underlying.remove(i).asInstanceOf[A]
  }
  def clear() = {
    App.assertEventThread()
    underlying.clear()
  }
  // Note: Clone cannot just call underlying.clone because in Java, only specific collections
  // expose clone methods.  Generically, they're protected.
  override def clone(): WritableList[A] =
    throw new UnsupportedOperationException
  //override def clone(): JListWrapper[A] = JListWrapper(new ju.ArrayList[A](underlying))

  def addChangeListener[A](listenerCallback: (ChangeEvent) ⇒ A): IChangeListener = {
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
  override def canEqual(that: Any): Boolean = that.isInstanceOf[WritableList[A]]
  /**
   * The universal equality method defined in `AnyRef`.
   */
  override def equals(that: Any): Boolean = that match {
    case that: WritableList[_] ⇒ (that canEqual this) && this.underlying.equals(that.underlying)
    case _ ⇒ false
  }
  /** HashCode from underlying. */
  override def hashCode() = this.underlying.hashCode()
}

object WritableList {
  implicit def wrapper2underlying(wrapper: WritableList[_]): OriginalWritableList = {
    App.assertEventThread()
    wrapper.underlying
  }
  // Use the unit as the method indicator
  def apply[A <: AnyRef](indicator: Unit, underlying: OriginalWritableList)(implicit m: Manifest[A]): WritableList[A] =
    new WritableList[A](underlying)
  def apply[A <: AnyRef](implicit m: Manifest[A]): WritableList[A] =
    new WritableList[A](new OriginalWritableList(new ArrayList[A](), m.runtimeClass))
  def apply[A <: AnyRef](collection: List[A])(implicit m: Manifest[A]): WritableList[A] =
    new WritableList[A](new OriginalWritableList(new ArrayList[A](collection), m.runtimeClass))
  def apply[A <: AnyRef](realm: Realm, collection: List[A])(implicit m: Manifest[A]): WritableList[A] =
    new WritableList[A](new OriginalWritableList(realm, new ArrayList[A](collection), m.runtimeClass))
}
