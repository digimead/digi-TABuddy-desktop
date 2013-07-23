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

package org.digimead.tabuddy.desktop.support.app.menu

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.future

import org.digimead.digi.lib.Disposable
import org.digimead.digi.lib.log.api.Loggable
import org.eclipse.e4.ui.model.application.ui.impl.UiPackageImpl
import org.eclipse.emf.common.notify.Notification
import org.eclipse.emf.common.notify.impl.AdapterImpl
import org.eclipse.emf.ecore.impl.EAttributeImpl
import org.eclipse.emf.ecore.impl.MinimalEObjectImpl.Container

/**
 * Trait with base EMF functions.
 */
trait EMFBase[T <: Container] extends Base[T] {
  /** Fire function when renderer is set. */
  def onRendererSet[A](fork: Boolean = true, removeAfter: Boolean = true)(f: (T, AnyRef, AnyRef) => A): Option[AdapterImpl] =
    onEvent(Notification.SET, UiPackageImpl.UI_ELEMENT__RENDERER, fork, removeAfter, f)
  /** Fire function when widget is set. */
  def onWidgetSet[A](fork: Boolean = true, removeAfter: Boolean = true)(f: (T, AnyRef, AnyRef) => A): Option[AdapterImpl] =
    onEvent(Notification.SET, UiPackageImpl.UI_ELEMENT__WIDGET, fork, removeAfter, f)
  /** Fire function when event is fired. */
  def onEvent[A](eventType: Int, featureID: Int, fork: Boolean, removeAfter: Boolean, f: (T, AnyRef, AnyRef) => A): Option[AdapterImpl] = {
    val adapter = new EMFBase.Listener(element, eventType, featureID, fork, removeAfter, f)
    if (element.eAdapters().add(adapter))
      Some(adapter)
    else
      None
  }
  /** Remove item from menu. */
  def remove() {
    // We don't touch EMF model.
    log.fatal("Unable to remove " + this)
  }
}

object EMFBase extends Loggable {
  /* ENotificationImpl contains
   *   protected InternalEObject notifier
   *   protected int featureID
   *   and no getters... Fuck em all.
   * I transfer element to the constructor. I know that this is sad, but I moved this technical debt to the end user.
   * Feel free to overload this implementation with your own - the better.
   * Please, create pull request if you wish to discharge this debt.
   *   Ezh.
   */
  /** Listener for EMF notifications. */
  class Listener[A <: Container, B](element: A, eventType: Int, featureID: Int, fork: Boolean,
    removeAfter: Boolean, f: (A, AnyRef, AnyRef) => B) extends AdapterImpl {
    override def notifyChanged(notification: Notification) = (notification.getEventType(), notification.getFeature(), notification.getOldValue(), notification.getNewValue()) match {
      case (eventType, feature: EAttributeImpl, before, after) if feature.getFeatureID == featureID =>
        if (removeAfter) element.eAdapters().remove(this)
        if (fork)
          future { f(element, before, after) } onFailure { case e: Throwable => log.error(e.getMessage, e) }
        else f(element, before, after)
        if (removeAfter) Disposable.clean(this) // clean via reflection
      case _ =>
    }
  }
}
