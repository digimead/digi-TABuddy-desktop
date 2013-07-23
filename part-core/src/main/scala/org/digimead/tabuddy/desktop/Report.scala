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

package org.digimead.tabuddy.desktop

import java.io.File

import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.App.app2implementation
import org.osgi.framework.BundleContext
import org.osgi.framework.ServiceReference
import org.osgi.util.tracker.ServiceTrackerCustomizer

import language.implicitConversions
import language.reflectiveCalls

/**
 * This class interact with launcher report service.
 */
class Report(context: BundleContext) extends ServiceTrackerCustomizer[AnyRef, AnyRef] with Loggable {
  /** Report service. */
  protected var service: Option[AnyRef] = None
  /** Exception listener. */
  protected val listener = new Runnable { def run = onException() }
  private val modifyServiceLock = new Object

  def addingService(reference: ServiceReference[AnyRef]): AnyRef = modifyServiceLock.synchronized {
    if (service.isEmpty) {
      log.debug("Register report service to TA Buddy application.")
      val service = context.getService(reference)
      this.service = Some(service)
      service.asInstanceOf[{ def register(listener: Runnable) }].register(listener)
      // Search to stack traces
      //future { ReportDialog.searchAndSubmit() }
      service
    } else {
      log.warn("Skip report service to TA Buddy application: already registered.")
      null
    }
  }
  def modifiedService(reference: ServiceReference[AnyRef], service: AnyRef) {}
  def removedService(reference: ServiceReference[AnyRef], service: AnyRef) = modifyServiceLock.synchronized {
    context.ungetService(reference)
    if (Some(service) == this.service) {
      log.debug("Unregister report service from TA Buddy application.")
      service.asInstanceOf[{ def unregister(listener: Runnable) }].unregister(listener)
      this.service = None
    }
  }

  /** Returns general information about application. */
  def info(): Option[String] = service.map(_.asInstanceOf[{ val info: String }].info)
  /** The exception callback. */
  protected def onException() = service.foreach { service =>
    log.___glance("BOOM!!!")
  }
}

/** Report singleton. */
object Report {
  implicit def report2implementation(c: Report.type): Report = c.inner

  /** Report implementation. */
  def inner() = DI.implementation

  /**
   * Dependency injection routines
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** Implementation DI cache */
    lazy val implementation = injectOptional[Report] getOrElse new Report(App.bundle(getClass).getBundleContext())
  }
}