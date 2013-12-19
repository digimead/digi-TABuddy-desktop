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

package org.digimead.tabuddy.desktop.core

import com.escalatesoft.subcut.inject.NewBindingModule
import java.io.File
import org.digimead.tabuddy.desktop.core.Report.report2implementation
import org.mockito.Mockito.{ spy, times }
import org.osgi.util.tracker.ServiceTracker
import org.scalatest.WordSpec

class ReportSpec extends WordSpec with Test.Base {
  "A Report" should {
    "be empty if report is absent" in {
      val info = Report.info()
      info should be('empty)
    }
    "be non empty if report is present" in {
      val service = coreBundle.getBundleContext().registerService("org.digimead.digi.launcher.report.api.Report", ReportSpec.service, null)
      val reportServiceTracker = new ServiceTracker(coreBundle.getBundleContext(), "org.digimead.digi.launcher.report.api.Report", Report)
      reportServiceTracker.open()
      val info = Report.info()
      info should be('nonEmpty)
      info.get should be(ReportSpec.Service.info)
      reportServiceTracker.close()
      service.unregister()

      val inOrder = org.mockito.Mockito.inOrder(ReportSpec.service)
      inOrder.verify(ReportSpec.service, times(1)).register(ReportX.listenerN)
      inOrder.verify(ReportSpec.service, times(1)).info
      inOrder.verify(ReportSpec.service, times(1)).unregister(ReportX.listenerN)
      inOrder.verifyNoMoreInteractions()
    }
  }

  override def beforeAll(configMap: org.scalatest.ConfigMap) {
    super.beforeAll(configMap)
    startCoreBeforeAll()
  }
  override def config = super.config ~ new NewBindingModule(module ⇒ {
    module.bind[Report] toSingle { ReportX }
  })
  object ReportX extends Report(coreBundle.getBundleContext()) {
    def listenerN = listener
  }
}

object ReportSpec {
  val service = spy(Service)
  object Service {
    val info: AnyRef = Report.Info(Seq(), "os", "arch", "platform")
    val path: File = new File(".")
    def register(listener: Runnable) {}
    def unregister(listener: Runnable) {}
  }
}
