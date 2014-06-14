/**
 * This file is part of the TA Buddy project.
 * Copyright (c) 2013-2014 Alexey Aksenov ezh@ezh.msk.ru
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

package org.digimead.tabuddy.desktop.core

import java.io.File
import java.text.{ DateFormat, SimpleDateFormat }
import java.util.Date
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.api.XInfo
import org.digimead.tabuddy.desktop.core.support.App
import org.osgi.framework.{ BundleContext, ServiceReference }
import org.osgi.util.tracker.ServiceTrackerCustomizer
import scala.language.{ implicitConversions, reflectiveCalls }

/**
 * This class interact with launcher report service.
 */
class Report(context: BundleContext) extends ServiceTrackerCustomizer[AnyRef, AnyRef] with Loggable {
  /** Report service. */
  protected var serviceRef: Option[AnyRef] = None
  /** Exception listener. */
  protected val listener = new Runnable { def run = onException() }
  private val modifyServiceLock = new Object

  def addingService(reference: ServiceReference[AnyRef]): AnyRef = modifyServiceLock.synchronized {
    if (serviceRef.isEmpty) {
      log.debug("Register report service to TA Buddy application.")
      val service = context.getService(reference)
      this.serviceRef = Some(service)
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
    if (Some(service) == this.serviceRef) {
      log.debug("Unregister report service from TA Buddy application.")
      service.asInstanceOf[{ def unregister(listener: Runnable) }].unregister(listener)
      this.serviceRef = None
    }
  }

  /** Returns general information about application. */
  def info(): Option[Report.Info] = serviceRef.flatMap { service ⇒
    try {
      val infoFromOutside = service.asInstanceOf[{ val info: AnyRef }].info.
        asInstanceOf[{ val component: Seq[AnyRef]; val os: String; val arch: String; val platform: String }]
      Some(Report.Info(infoFromOutside.component.map { c ⇒
        val component = c.asInstanceOf[{ val name: String; val version: String; val build: Date; val rawBuild: String; val bundleSymbolicName: String }]
        XInfo.Component(component.name, component.version, component.build, component.rawBuild, component.bundleSymbolicName)
      }, infoFromOutside.os, infoFromOutside.arch, infoFromOutside.platform))
    } catch {
      case e: Throwable ⇒
        log.error(s"Unable to get info: ${e.getMessage()}.", e)
        None
    }
  }
  /** Get report service reference. */
  def service = serviceRef
  /** The exception callback. */
  protected def onException() = serviceRef.foreach { service ⇒
    log.___glance("BOOM!!!")
  }
}

/** Report singleton. */
object Report {
  implicit def report2implementation(c: Report.type): Report = c.inner

  /** Returns string representation of the specific date. */
  def dateString(date: Date) = DI.df.format(date)
  /** Report implementation. */
  def inner() = DI.implementation

  /**
   * Application information acquired from launcher via OSGi service.
   */
  case class Info(val component: Seq[XInfo.Component], os: String, arch: String, platform: String) extends XInfo {
    def header() = """=== TA-Buddy desktop (if you have a question or suggestion, email ezh@ezh.msk.ru) ===""" +
      toString + """=====================================================================================\n\n"""
    override def toString() = s"""report path: ${inner.service.map(_.asInstanceOf[{ val path: File }].path.toString()).getOrElse("UNKNOWN")}
      |os: ${os}
      |arch: ${arch}
      |platform: ${platform}
      |${component.map(c ⇒ s"${c.name}: version: ${c.version}, build: ${Report.dateString(c.build)} (${c.rawBuild})").sorted.mkString("\n")}""".stripMargin
  }
  /**
   * Dependency injection routines.
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** Date representation format. */
    val df = injectOptional[DateFormat]("Report.DateFormat") getOrElse new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ")
    /** Report implementation. */
    lazy val implementation = injectOptional[Report] getOrElse new Report(App.bundle(getClass).getBundleContext())
  }
}
