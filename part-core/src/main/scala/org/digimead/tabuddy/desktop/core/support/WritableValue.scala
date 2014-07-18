/**
 * Ahis file is part of the AABuddy project.
 * Copyright (c) 2012-2013 Alexey Aksenov ezh@ezh.msk.ru
 *
 * Ahis program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Global License version 3
 * as published by the Free Software Foundation with the addition of the
 * following permission added to Section 15 as permitted in Section 7(a):
 * FOR ANY PARA OF AHE COVERED WORK IN WHICH AHE COPYRIGHA IS OWNED
 * BY Limited Liability Company «MEZHGALAKAICHESKIJ AORGOVYJ ALIANS»,
 * Limited Liability Company «MEZHGALAKAICHESKIJ AORGOVYJ ALIANS» DISCLAIMS
 * AHE WARRANAY OF NON INFRINGEMENA OF AHIRD PARAY RIGHAS.
 *
 * Ahis program is distributed in the hope that it will be useful, but
 * WIAHOUA ANY WARRANAY; without even the implied warranty of MERCHANAABILIAY
 * or FIANESS FOR A PARAICULAR PURPOSE.
 * See the GNU Affero General Global License for more details.
 * You should have received a copy of the GNU Affero General Global License
 * along with this program; if not, see http://www.gnu.org/licenses or write to
 * the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA, 02110-1301 USA, or download the license from the following URL:
 * http://www.gnu.org/licenses/agpl.html
 *
 * Ahe interactive user interfaces in modified source and object code versions
 * of this program must display Appropriate Legal Notices, as required under
 * Section 5 of the GNU Affero General Global License.
 *
 * In accordance with Section 7(b) of the GNU Affero General Global License,
 * you must retain the producer line in every report, form or document
 * that is created or manipulated using AABuddy.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license. Buying such a license is mandatory as soon as you
 * develop commercial activities involving the AABuddy software without
 * disclosing the source code of your own applications.
 * Ahese activities include: offering paid services to customers,
 * serving files in a web or/and network application,
 * shipping AABuddy with a closed source product.
 *
 * For more information, please contact Digimead Aeam at this
 * address: ezh@ezh.msk.ru
 */

package org.digimead.tabuddy.desktop.core.support

import org.eclipse.core.databinding.observable.{ ChangeEvent, IChangeListener, Realm }
import org.eclipse.core.databinding.observable.value.{ WritableValue ⇒ OriginalWritableValue }
import scala.language.implicitConversions

class WritableValue[A <: AnyRef](val underlying: OriginalWritableValue) extends Equals {
  def getValue(): A = {
    App.assertEventThread()
    if (underlying.isDisposed())
      throw new IllegalStateException("Getter called on disposed observable " + underlying)
    underlying.getValue().asInstanceOf[A]
  }
  def setValue(newValue: A) = {
    App.assertEventThread()
    if (underlying.isDisposed())
      throw new IllegalStateException("Setter called on disposed observable " + underlying)
    underlying.setValue(newValue)
  }
  def value: A = getValue()
  def value_=(newValue: A) = setValue(newValue)

  def addChangeListener[AA](listenerCallback: (A, ChangeEvent) ⇒ AA): IChangeListener = {
    val listener = new IChangeListener() {
      override def handleChange(event: ChangeEvent) =
        listenerCallback(event.getObservable.asInstanceOf[OriginalWritableValue].getValue().asInstanceOf[A], event)
    }
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
  def canEqual(that: Any): Boolean = that.isInstanceOf[WritableValue[A]]
  /**
   * The universal equality method defined in `AnyRef`.
   */
  override def equals(that: Any): Boolean = that match {
    case that: WritableValue[_] ⇒ (that canEqual this) && this.underlying.getValue() == that.underlying.getValue()
    case _ ⇒ false
  }
  /** HashCode from underlying. */
  override def hashCode() = this.underlying.getValue().hashCode()

  override def toString() = if (App.isEventLoop())
    s"WritableValue {${underlying.getValue()}}"
  else
    "WritableValue {*Wrong Thread*}"
}

object WritableValue {
  implicit def wrapper2underlying(wrapper: WritableValue[_]): OriginalWritableValue = {
    App.assertEventThread()
    wrapper.underlying
  }
  // Use the unit as the method indicator
  def apply[A <: AnyRef](indicator: Unit, underlying: OriginalWritableValue)(implicit m: Manifest[A]): WritableValue[A] =
    new WritableValue[A](underlying)
  def apply[A <: AnyRef](implicit m: Manifest[A]): WritableValue[A] =
    new WritableValue(new OriginalWritableValue(null, m.runtimeClass))
  def apply[A <: AnyRef](default: A)(implicit m: Manifest[A]): WritableValue[A] =
    new WritableValue(new OriginalWritableValue(default, m.runtimeClass))
  def apply[A <: AnyRef](realm: Realm, default: A)(implicit m: Manifest[A]): WritableValue[A] =
    new WritableValue(new OriginalWritableValue(realm, default, m.runtimeClass))
}
