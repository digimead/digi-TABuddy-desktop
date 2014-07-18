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

import java.util.HashSet
import org.eclipse.core.databinding.observable.{ ChangeEvent, IChangeListener, Realm }
import org.eclipse.core.databinding.observable.set.{ WritableSet ⇒ OriginalWritableSet }
import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.language.implicitConversions

class WritableSet[A](val underlying: OriginalWritableSet) extends mutable.Set[A] with mutable.SetLike[A, WritableSet[A]] with Equals {
  override def size = {
    App.assertEventThread()
    if (underlying.isDisposed())
      throw new IllegalStateException("Size called on disposed observable " + underlying)
    underlying.size
  }

  def iterator = {
    App.assertEventThread()
    if (underlying.isDisposed())
      throw new IllegalStateException("Iterator called on disposed observable " + underlying)
    underlying.iterator.asInstanceOf[java.util.Iterator[A]]
  }

  def contains(elem: A): Boolean = {
    App.assertEventThread()
    if (underlying.isDisposed())
      throw new IllegalStateException("Contains called on disposed observable " + underlying)
    underlying.contains(elem)
  }

  def +=(elem: A): this.type = {
    App.assertEventThread()
    if (underlying.isDisposed())
      throw new IllegalStateException("Add called on disposed observable " + underlying)
    underlying add elem; this
  }
  def -=(elem: A): this.type = {
    App.assertEventThread()
    if (underlying.isDisposed())
      throw new IllegalStateException("Remove called on disposed observable " + underlying)
    underlying remove elem; this
  }

  override def add(elem: A): Boolean = {
    App.assertEventThread()
    if (underlying.isDisposed())
      throw new IllegalStateException("Add called on disposed observable " + underlying)
    underlying add elem
  }
  override def remove(elem: A): Boolean = {
    App.assertEventThread()
    if (underlying.isDisposed())
      throw new IllegalStateException("Remove called on disposed observable " + underlying)
    underlying remove elem
  }
  override def clear() = {
    App.assertEventThread()
    if (underlying.isDisposed())
      throw new IllegalStateException("Clear called on disposed observable " + underlying)
    underlying.clear()
  }

  override def empty = {
    App.assertEventThread()
    if (underlying.isDisposed())
      throw new IllegalStateException("Empty called on disposed observable " + underlying)
    new WritableSet(new OriginalWritableSet(underlying.getRealm(), new HashSet[A](), underlying.getElementType()))
  }
  // Note: Clone cannot just call underlying.clone because in Java, only specific collections
  // expose clone methods.  Generically, they're protected.
  override def clone() =
    throw new UnsupportedOperationException
  //new WritableSet[A](new ju.LinkedHashSet[A](underlying))

  // thread safe
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
  override def canEqual(that: Any): Boolean = that.isInstanceOf[WritableSet[A]]
  /**
   * The universal equality method defined in `AnyRef`.
   */
  override def equals(that: Any): Boolean = that match {
    case that: WritableSet[_] ⇒ (that canEqual this) && this.underlying.equals(that.underlying)
    case _ ⇒ false
  }
  /** HashCode from underlying. */
  override def hashCode() = this.underlying.hashCode()

  override def toString() = if (App.isEventLoop())
    s"WritableSet {${underlying.iterator.mkString(",")}}"
  else
    "WritableSet {*Wrong Thread*}"
}

object WritableSet {
  implicit def wrapper2underlying(wrapper: WritableSet[_]): OriginalWritableSet = {
    App.assertEventThread()
    wrapper.underlying
  }
  // Use the unit as the method indicator
  def apply[A <: AnyRef](indicator: Unit, underlying: OriginalWritableSet)(implicit m: Manifest[A]): WritableSet[A] =
    new WritableSet[A](underlying)
  def apply[A <: AnyRef](implicit m: Manifest[A]): WritableSet[A] =
    new WritableSet[A](new OriginalWritableSet(new HashSet[A](), m.runtimeClass))
  def apply[A <: AnyRef](collection: Set[A])(implicit m: Manifest[A]): WritableSet[A] =
    new WritableSet[A](new OriginalWritableSet(new HashSet[A](collection.toList), m.runtimeClass))
  def apply[A <: AnyRef](realm: Realm, collection: Set[A])(implicit m: Manifest[A]): WritableSet[A] =
    new WritableSet[A](new OriginalWritableSet(realm, new HashSet[A](collection.toList), m.runtimeClass))
}
