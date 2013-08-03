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

import org.eclipse.core.databinding.observable.ChangeEvent
import org.eclipse.core.databinding.observable.IChangeListener
import org.eclipse.core.databinding.observable.Realm
import org.eclipse.core.databinding.observable.value.{ WritableValue => OriginalWritableValue }

import language.implicitConversions

case class WritableValue[T <: AnyRef] private (val underlying: OriginalWritableValue) {
  def getValue(): T = {
    App.assertUIThread()
    underlying.getValue().asInstanceOf[T]
  }
  def setValue(newValue: T) = {
    App.assertUIThread()
    underlying.setValue(newValue)
  }
  def value: T = getValue()
  def value_=(newValue: T) = setValue(newValue)

  def addChangeListener[TT](listenerCallback: (T, ChangeEvent) => TT): IChangeListener = {
    val listener = new IChangeListener() {
      override def handleChange(event: ChangeEvent) =
        listenerCallback(event.getObservable.asInstanceOf[OriginalWritableValue].getValue().asInstanceOf[T], event)
    }
    underlying.addChangeListener(listener)
    listener
  }
}

object WritableValue {
  implicit def wrapper2underlying(wrapper: WritableValue[_]): OriginalWritableValue = {
    App.assertUIThread()
    wrapper.underlying
  }
  // Use the unit as the method indicator
  def apply[T <: AnyRef](indicator: Unit, underlying: OriginalWritableValue)(implicit m: Manifest[T]): WritableValue[T] =
    WritableValue[T](underlying)
  def apply[T <: AnyRef](implicit m: Manifest[T]): WritableValue[T] =
    WritableValue(new OriginalWritableValue(null, m.runtimeClass))
  def apply[T <: AnyRef](default: T)(implicit m: Manifest[T]): WritableValue[T] =
    WritableValue(new OriginalWritableValue(default, m.runtimeClass))
  def apply[T <: AnyRef](realm: Realm, default: T)(implicit m: Manifest[T]): WritableValue[T] =
    WritableValue(new OriginalWritableValue(realm, default, m.runtimeClass))
}
