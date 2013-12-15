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

package org.digimead.tabuddy.desktop.core.support

import java.util.concurrent.{ CancellationException, Exchanger }
import org.digimead.digi.lib.aop.log
import org.digimead.tabuddy.desktop.core.{ Core, Test }
import org.digimead.tabuddy.desktop.core.command.Commands
import org.digimead.tabuddy.desktop.core.definition.Operation
import org.digimead.tabuddy.desktop.core.definition.command.Command
import org.eclipse.core.runtime.jobs.Job
import org.scalatest.{ FeatureSpec, Finders, GivenWhenThen }
import scala.language.reflectiveCalls
import org.scalatest.WordSpec
import com.escalatesoft.subcut.inject.NewBindingModule
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.EventLoop
import org.mockito.Mockito._
import org.scalatest.FlatSpec
import org.scalatest.FunSpec

class WatchSpec extends FunSpec with Test.Base {
  val tester = mock[WatchSpec.Tester]

  describe("A watcher") {
    describe("without timeout") {
      it("should have proper beforeStart implementation") {
        val app = App.inner.asInstanceOf[TestApp]
        app.watchRefT should have size (1)
        app.watchSetT should have size (1)
        val w = spy(app.watch(A)) always () makeBeforeStart { tester.a }
        w on { tester.a1 }
        app.watchRefT should have size (2)
        app.watchSetT should have size (2)
        verify(tester, timeout(1000).times(1)).a
        verify(tester, timeout(1000).times(1)).a1
        intercept[IllegalStateException] { w on { tester.a1 } }
        w.off()
        w.on { tester.a1 }
        verify(tester, timeout(1000).times(2)).a
        verify(tester, timeout(1000).times(2)).a1
        tester.a
        tester.a1
        verify(tester, timeout(1000).times(3)).a
        verify(tester, timeout(1000).times(3)).a1
        intercept[IllegalStateException] { app.watch(A) on () }
        app.watch(A) off ()
        app.watch(A) on ()
        verify(tester, timeout(1000).times(4)).a
        verify(tester, timeout(1000).times(3)).a1
        w.reset()
        app.watchRefT should have size (1)
        app.watch(A) off ()
        app.watch(A) on ()
        verify(tester, timeout(1000).times(4)).a
        verify(tester, timeout(1000).times(3)).a1
      }
      it("should have proper beforeStart implementation1") {
        /* val app = App.inner.asInstanceOf[TestApp]
        app.watchRefT should have size (1)
        app.watchSetT should have size (1)
        val w = spy(app.watch(A)) beforeStart { tester.a }
        w on { tester.a1 }
        app.watchRefT should have size (2)
        app.watchSetT should have size (2)
        verify(tester, timeout(1000).times(1)).a
        verify(tester, timeout(1000).times(1)).a1*/
      }
    }
  }
  //  "A Generic activation helper" should {
  //     describe("(when empty)") {
  //    "handle nested components" in {
  /*val app = App.inner.asInstanceOf[TestApp]
      app.startedT should have size (1)
      app.isActive(EventLoop) should be(true)
      // start B after A started
      App.afterStart(GenericSpec.B, Timeout.normal.toMillis, GenericSpec.A) {
        tester.b()
      }
      app.markAsActive(GenericSpec.C)()
      app.startedT should have size (2)
      verifyZeroInteractions(tester)
      app.markAsActive(GenericSpec.A)()
      app.startedT should have size (3)
      verify(tester, timeout(1000).times(1)).b()
      app.markAsPassive(GenericSpec.A)()
      app.startedT should have size (2)
      app.markAsPassive(GenericSpec.C)()
      app.startedT should have size (1)
      reset(tester)

      app.markAsActive(GenericSpec.A)()
      App.afterStart(GenericSpec.B, Timeout.normal.toMillis, GenericSpec.A) {
        tester.b()
      }
      verify(tester, timeout(1000).times(1)).b()
      app.markAsPassive(GenericSpec.A)()
      reset(tester)

      App.afterStart(GenericSpec.C, Timeout.normal.toMillis, GenericSpec.A, GenericSpec.B) {
        tester.c()
      }
      verify(tester, timeout(1000).times(0)).c()
      app.markAsActive(GenericSpec.B)()
      verify(tester, timeout(1000).times(0)).c()
      app.markAsActive(GenericSpec.A)()
      verify(tester, timeout(1000).times(1)).c()
      app.markAsPassive(GenericSpec.A)()
      app.markAsPassive(GenericSpec.B)()
      reset(tester)*/

  //    }
  //  }

  override def beforeAll(configMap: org.scalatest.ConfigMap) {
    super.beforeAll(configMap)
    startCoreBeforeAll()
    startEventLoop()
  }
  override def afterAll(configMap: org.scalatest.ConfigMap) {
    stopEventLoop()
    super.afterAll(configMap)
  }

  object A
  object B
  object C
}

object WatchSpec {
  trait Tester {
    def a() {}
    def a1() {}
    def b() {}
    def b1() {}
    def c() {}
    def c1() {}
  }
}
