/**
 * This file is part of the TABuddy project.
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

import java.util.HashSet

import scala.collection.JavaConversions.asScalaIterator
import scala.collection.JavaConversions.seqAsJavaList
import scala.collection.mutable

import org.digimead.tabuddy.desktop.Main
import org.eclipse.core.databinding.observable.ChangeEvent
import org.eclipse.core.databinding.observable.IChangeListener
import org.eclipse.core.databinding.observable.Realm
import org.eclipse.core.databinding.observable.set.{ WritableSet => OriginalWritableSet }

import language.implicitConversions

case class WritableSet[A] private (val underlying: OriginalWritableSet) extends mutable.Set[A] with mutable.SetLike[A, WritableSet[A]] {
  override def size = {
    Main.checkThread
    underlying.size
  }

  def iterator = {
    Main.checkThread
    underlying.iterator.asInstanceOf[java.util.Iterator[A]]
  }

  def contains(elem: A): Boolean = {
    Main.checkThread
    underlying.contains(elem)
  }

  def +=(elem: A): this.type = {
    Main.checkThread
    underlying add elem; this
  }
  def -=(elem: A): this.type = {
    Main.checkThread
    underlying remove elem; this
  }

  override def add(elem: A): Boolean = {
    Main.checkThread
    underlying add elem
  }
  override def remove(elem: A): Boolean = {
    Main.checkThread
    underlying remove elem
  }
  override def clear() = {
    Main.checkThread
    underlying.clear()
  }

  override def empty = {
    Main.checkThread
    WritableSet(new OriginalWritableSet(underlying.getRealm(), new HashSet[A](), underlying.getElementType()))
  }
  // Note: Clone cannot just call underlying.clone because in Java, only specific collections
  // expose clone methods.  Generically, they're protected.
  override def clone() =
    throw new UnsupportedOperationException
  //new WritableSet[A](new ju.LinkedHashSet[A](underlying))

  def addChangeListener[T](listenerCallback: (ChangeEvent) => T): IChangeListener = {
    val listener = new IChangeListener() { override def handleChange(event: ChangeEvent) = listenerCallback(event) }
    underlying.addChangeListener(listener)
    listener
  }
}

object WritableSet {
  implicit def wrapper2underlying(wrapper: WritableSet[_]): OriginalWritableSet = {
    Main.checkThread
    wrapper.underlying
  }
  // Use the unit as the method indicator
  def apply[T <: AnyRef](indicator: Unit, underlying: OriginalWritableSet)(implicit m: Manifest[T]): WritableSet[T] =
    WritableSet[T](underlying)
  def apply[T <: AnyRef](collection: Set[T])(implicit m: Manifest[T]): WritableSet[T] =
    WritableSet[T](new OriginalWritableSet(new HashSet[T](collection.toList), m.runtimeClass))
  def apply[T <: AnyRef](realm: Realm, collection: Set[T])(implicit m: Manifest[T]): WritableSet[T] =
    WritableSet[T](new OriginalWritableSet(realm, new HashSet[T](collection.toList), m.runtimeClass))
}
