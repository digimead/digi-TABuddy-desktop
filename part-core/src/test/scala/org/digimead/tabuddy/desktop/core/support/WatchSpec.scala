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

import com.escalatesoft.subcut.inject.NewBindingModule
import java.util.concurrent.{ CancellationException, Exchanger }
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.EventLoop
import org.digimead.tabuddy.desktop.core.command.Commands
import org.digimead.tabuddy.desktop.core.definition.Operation
import org.digimead.tabuddy.desktop.core.definition.command.Command
import org.digimead.tabuddy.desktop.core.{ Core, Test }
import org.eclipse.core.runtime.jobs.Job
import org.mockito.Matchers
import org.mockito.Mockito._
import org.scalatest.{ FunSpec, Finders }
import scala.language.reflectiveCalls

class WatchSpec extends FunSpec with Test.Base {
  describe("A watcher") {
    describe("with always parameter") {
      it("should have proper beforeStart implementation") {
        val tester = mock[WatchSpec.Tester]
        val app = App.inner.asInstanceOf[TestApp]
        val inOrderT = org.mockito.Mockito.inOrder(tester)
        app.watchRefT should have size (1)
        app.watchSetT should have size (1)
        val w = spy(app.watch(A)) always () makeBeforeStart { tester.a }
        val inOrderW = org.mockito.Mockito.inOrder(w)
        reset(w)
        verifyNoMoreInteractions(w)
        w on { tester.a1 }
        verifyReflection(inOrderW, w, times(1), "on") { case Seq(f) ⇒ f.getValue() shouldBe a[Function0[_]] }
        verifyReflection(inOrderW, w, times(2), "process") { case Seq(f) ⇒ f.getValue() shouldBe a[List[_]] }

        app.watchRefT should have size (2)
        app.watchSetT should have size (2)
        inOrderT.verify(tester).a
        inOrderT.verify(tester).a1
        intercept[IllegalStateException] { w on { tester.a1 } }
        w.off()
        w.on { tester.a1 }
        inOrderT.verify(tester).a
        inOrderT.verify(tester).a1
        tester.a
        tester.a1
        inOrderT.verify(tester).a
        inOrderT.verify(tester).a1
        verifyNoMoreInteractions(tester)
        intercept[IllegalStateException] { app.watch(A) on () }
        app.watch(A) off ()
        app.watch(A) on ()
        inOrderT.verify(tester).a
        w.reset()
        app.watchRefT should have size (1)
        app.watch(A) off ()
        app.watch(A) on ()
        app.watch(A) off ()
        app.watchRefT should have size (1)
        app.watchSetT should have size (1)
        verifyNoMoreInteractions(tester)
      }
      it("should have proper afterStart implementation") {
        val tester = mock[WatchSpec.Tester]
        val app = App.inner.asInstanceOf[TestApp]
        val inOrderT = org.mockito.Mockito.inOrder(tester)
        app.watchRefT should have size (1)
        app.watchSetT should have size (1)
        val w = spy(app.watch(A)) always () makeAfterStart { tester.a }
        val inOrderW = org.mockito.Mockito.inOrder(w)
        reset(w)
        verifyNoMoreInteractions(w)
        w on { tester.a1 }
        verifyReflection(inOrderW, w, times(1), "on") { case Seq(f) ⇒ f.getValue() shouldBe a[Function0[_]] }
        verifyReflection(inOrderW, w, times(2), "process") { case Seq(f) ⇒ f.getValue() shouldBe a[List[_]] }

        app.watchRefT should have size (2)
        app.watchSetT should have size (2)
        inOrderT.verify(tester).a1
        inOrderT.verify(tester).a
        intercept[IllegalStateException] { w on { tester.a1 } }
        w.off()
        w.on { tester.a1 }
        inOrderT.verify(tester).a1
        inOrderT.verify(tester).a
        tester.a
        tester.a1
        inOrderT.verify(tester).a
        inOrderT.verify(tester).a1
        verifyNoMoreInteractions(tester)
        intercept[IllegalStateException] { app.watch(A) on () }
        app.watch(A) off ()
        app.watch(A) on ()
        inOrderT.verify(tester).a
        w.reset()
        app.watchRefT should have size (1)
        app.watch(A) off ()
        app.watch(A) on ()
        app.watch(A) off ()
        app.watchRefT should have size (1)
        app.watchSetT should have size (1)
        verifyNoMoreInteractions(tester)
      }
      it("should have proper beforeStop implementation") {
        val tester = mock[WatchSpec.Tester]
        val app = App.inner.asInstanceOf[TestApp]
        val inOrderT = org.mockito.Mockito.inOrder(tester)
        app.watchRefT should have size (1)
        app.watchSetT should have size (1)
        val w = spy(app.watch(A)) always () makeBeforeStop { tester.a }
        val inOrderW = org.mockito.Mockito.inOrder(w)
        reset(w)
        verifyNoMoreInteractions(w)
        w on ()

        app.watchRefT should have size (2)
        app.watchSetT should have size (2)
        intercept[IllegalStateException] { w on { tester.a1 } }
        w.off { tester.a1 }
        verifyReflection(inOrderW, w, times(1), "on") { case Seq(f) ⇒ f.getValue() shouldBe a[Function0[_]] }
        verifyReflection(inOrderW, w, calls(2), "process") { case Seq(f) ⇒ f.getValue() shouldBe a[List[_]] }
        verifyReflection(inOrderW, w, times(1), "off") { case Seq(f) ⇒ f.getValue() shouldBe a[Function0[_]] }
        verifyReflection(inOrderW, w, times(2), "process") { case Seq(f) ⇒ f.getValue() shouldBe a[List[_]] }
        inOrderT.verify(tester).a
        inOrderT.verify(tester).a1
        w.on()
        tester.a
        tester.a1
        inOrderT.verify(tester).a
        inOrderT.verify(tester).a1
        verifyNoMoreInteractions(tester)
        intercept[IllegalStateException] { app.watch(A) on () }
        app.watch(A) off { tester.a1 }
        inOrderT.verify(tester).a
        inOrderT.verify(tester).a1
        app.watch(A) on ()
        w.reset()
        app.watchRefT should have size (1)
        app.watch(A) off ()
        app.watch(A) on ()
        app.watch(A) off ()
        app.watchRefT should have size (1)
        app.watchSetT should have size (1)
        verifyNoMoreInteractions(tester)
      }
      it("should have proper afterStop implementation") {
        val tester = mock[WatchSpec.Tester]
        val app = App.inner.asInstanceOf[TestApp]
        val inOrderT = org.mockito.Mockito.inOrder(tester)
        app.watchRefT should have size (1)
        app.watchSetT should have size (1)
        val w = spy(app.watch(A)) always () makeAfterStop { tester.a }
        val inOrderW = org.mockito.Mockito.inOrder(w)
        reset(w)
        verifyNoMoreInteractions(w)
        w on ()

        app.watchRefT should have size (2)
        app.watchSetT should have size (2)
        intercept[IllegalStateException] { w on { tester.a1 } }
        w.off { tester.a1 }
        verifyReflection(inOrderW, w, times(1), "on") { case Seq(f) ⇒ f.getValue() shouldBe a[Function0[_]] }
        verifyReflection(inOrderW, w, calls(2), "process") { case Seq(f) ⇒ f.getValue() shouldBe a[List[_]] }
        verifyReflection(inOrderW, w, times(1), "off") { case Seq(f) ⇒ f.getValue() shouldBe a[Function0[_]] }
        verifyReflection(inOrderW, w, times(2), "process") { case Seq(f) ⇒ f.getValue() shouldBe a[List[_]] }
        inOrderT.verify(tester).a1
        inOrderT.verify(tester).a
        w.on()
        tester.a
        tester.a1
        inOrderT.verify(tester).a
        inOrderT.verify(tester).a1
        verifyNoMoreInteractions(tester)
        intercept[IllegalStateException] { app.watch(A) on () }
        app.watch(A) off { tester.a1 }
        inOrderT.verify(tester).a1
        inOrderT.verify(tester).a
        app.watch(A) on ()
        w.reset()
        app.watchRefT should have size (1)
        app.watch(A) off ()
        app.watch(A) on ()
        app.watch(A) off ()
        app.watchRefT should have size (1)
        app.watchSetT should have size (1)
        verifyNoMoreInteractions(tester)
      }
    }
    describe("with once parameter") {
      it("should have proper beforeStart implementation") {
        val tester = mock[WatchSpec.Tester]
        val app = App.inner.asInstanceOf[TestApp]
        app.watchRefT should have size (1)
        app.watchSetT should have size (1)
        val w = spy(app.watch(A)) once () makeBeforeStart { tester.a }
        app.watchRefT should have size (2)
        app.watch(A) on ()
        app.watchRefT should have size (1)
        app.watch(A) off ()
        verify(tester).a()
        verifyNoMoreInteractions(tester)
        reset(tester)

        getViaReflection[Seq[(Int, Function0[_])]](w, "hookAfterStart") should be('empty)
        getViaReflection[Seq[(Int, Function0[_])]](w, "hookAfterStop") should be('empty)
        getViaReflection[Seq[(Int, Function0[_])]](w, "hookBeforeStart") should be('empty)
        getViaReflection[Seq[(Int, Function0[_])]](w, "hookBeforeStop") should be('empty)
        w once () makeBeforeStart { tester.a } times (2) makeBeforeStart { tester.a }
        getViaReflection[Seq[(Int, Function0[_])]](w, "hookAfterStart") should be('empty)
        getViaReflection[Seq[(Int, Function0[_])]](w, "hookAfterStop") should be('empty)
        getViaReflection[Seq[(Int, Function0[_])]](w, "hookBeforeStart") should have size (2)
        getViaReflection[Seq[(Int, Function0[_])]](w, "hookBeforeStop") should be('empty)
        app.watchRefT should have size (2)
        app.watchSetT should have size (1)
        app.watch(A) on ()
        verify(tester, times(2)).a()
        reset(tester)
        app.watchRefT should have size (2)
        app.watch(A) off ()
        verify(tester, never).a()
        getViaReflection[Seq[(Int, Function0[_])]](w, "hookAfterStart") should be('empty)
        getViaReflection[Seq[(Int, Function0[_])]](w, "hookAfterStop") should be('empty)
        getViaReflection[Seq[(Int, Function0[_])]](w, "hookBeforeStart") should have size (1)
        getViaReflection[Seq[(Int, Function0[_])]](w, "hookBeforeStop") should be('empty)
        app.watch(A) on ()
        app.watch(A) off ()
        getViaReflection[Seq[(Int, Function0[_])]](w, "hookAfterStart") should be('empty)
        getViaReflection[Seq[(Int, Function0[_])]](w, "hookAfterStop") should be('empty)
        getViaReflection[Seq[(Int, Function0[_])]](w, "hookBeforeStart") should be('empty)
        getViaReflection[Seq[(Int, Function0[_])]](w, "hookBeforeStop") should be('empty)
        app.watchRefT should have size (1)
        app.watchSetT should have size (1)
      }
      it("should have proper afterStart implementation") {
        val tester = mock[WatchSpec.Tester]
        val app = App.inner.asInstanceOf[TestApp]
        app.watchRefT should have size (1)
        app.watchSetT should have size (1)
        val w = spy(app.watch(A)) once () makeAfterStart { tester.a }
        app.watchRefT should have size (2)
        app.watch(A) on ()
        app.watchRefT should have size (1)
        app.watch(A) off ()
        verify(tester).a()
        verifyNoMoreInteractions(tester)
        reset(tester)

        getViaReflection[Seq[(Int, Function0[_])]](w, "hookAfterStart") should be('empty)
        getViaReflection[Seq[(Int, Function0[_])]](w, "hookAfterStop") should be('empty)
        getViaReflection[Seq[(Int, Function0[_])]](w, "hookBeforeStart") should be('empty)
        getViaReflection[Seq[(Int, Function0[_])]](w, "hookBeforeStop") should be('empty)
        w once () makeAfterStart { tester.a } times (2) makeAfterStart { tester.a }
        getViaReflection[Seq[(Int, Function0[_])]](w, "hookAfterStart") should have size (2)
        getViaReflection[Seq[(Int, Function0[_])]](w, "hookAfterStop") should be('empty)
        getViaReflection[Seq[(Int, Function0[_])]](w, "hookBeforeStart") should be('empty)
        getViaReflection[Seq[(Int, Function0[_])]](w, "hookBeforeStop") should be('empty)
        app.watchRefT should have size (2)
        app.watchSetT should have size (1)
        app.watch(A) on ()
        verify(tester, times(2)).a()
        reset(tester)
        app.watchRefT should have size (2)
        app.watch(A) off ()
        verify(tester, never).a()
        getViaReflection[Seq[(Int, Function0[_])]](w, "hookAfterStart") should have size (1)
        getViaReflection[Seq[(Int, Function0[_])]](w, "hookAfterStop") should be('empty)
        getViaReflection[Seq[(Int, Function0[_])]](w, "hookBeforeStart") should be('empty)
        getViaReflection[Seq[(Int, Function0[_])]](w, "hookBeforeStop") should be('empty)
        app.watch(A) on ()
        app.watch(A) off ()
        getViaReflection[Seq[(Int, Function0[_])]](w, "hookAfterStart") should be('empty)
        getViaReflection[Seq[(Int, Function0[_])]](w, "hookAfterStop") should be('empty)
        getViaReflection[Seq[(Int, Function0[_])]](w, "hookBeforeStart") should be('empty)
        getViaReflection[Seq[(Int, Function0[_])]](w, "hookBeforeStop") should be('empty)
        app.watchRefT should have size (1)
        app.watchSetT should have size (1)
      }
      it("should have proper beforeStop implementation") {
        val tester = mock[WatchSpec.Tester]
        val app = App.inner.asInstanceOf[TestApp]
        app.watchRefT should have size (1)
        app.watchSetT should have size (1)
        val w = spy(app.watch(A)) once () makeBeforeStop { tester.a }
        app.watchRefT should have size (2)
        app.watch(A) on ()
        app.watchRefT should have size (2)
        app.watch(A) off ()
        app.watchRefT should have size (1)
        verify(tester).a()
        verifyNoMoreInteractions(tester)
        reset(tester)

        getViaReflection[Seq[(Int, Function0[_])]](w, "hookAfterStart") should be('empty)
        getViaReflection[Seq[(Int, Function0[_])]](w, "hookAfterStop") should be('empty)
        getViaReflection[Seq[(Int, Function0[_])]](w, "hookBeforeStart") should be('empty)
        getViaReflection[Seq[(Int, Function0[_])]](w, "hookBeforeStop") should be('empty)
        w once () makeBeforeStop { tester.a } times (2) makeBeforeStop { tester.a }
        getViaReflection[Seq[(Int, Function0[_])]](w, "hookAfterStart") should be('empty)
        getViaReflection[Seq[(Int, Function0[_])]](w, "hookAfterStop") should be('empty)
        getViaReflection[Seq[(Int, Function0[_])]](w, "hookBeforeStart") should be('empty)
        getViaReflection[Seq[(Int, Function0[_])]](w, "hookBeforeStop") should have size (2)
        app.watchRefT should have size (2)
        app.watchSetT should have size (1)
        app.watch(A) on ()
        verify(tester, never).a()
        app.watchRefT should have size (2)
        app.watch(A) off ()
        verify(tester, times(2)).a()
        reset(tester)
        getViaReflection[Seq[(Int, Function0[_])]](w, "hookAfterStart") should be('empty)
        getViaReflection[Seq[(Int, Function0[_])]](w, "hookAfterStop") should be('empty)
        getViaReflection[Seq[(Int, Function0[_])]](w, "hookBeforeStart") should be('empty)
        getViaReflection[Seq[(Int, Function0[_])]](w, "hookBeforeStop") should have size (1)
        app.watch(A) on ()
        app.watch(A) off ()
        getViaReflection[Seq[(Int, Function0[_])]](w, "hookAfterStart") should be('empty)
        getViaReflection[Seq[(Int, Function0[_])]](w, "hookAfterStop") should be('empty)
        getViaReflection[Seq[(Int, Function0[_])]](w, "hookBeforeStart") should be('empty)
        getViaReflection[Seq[(Int, Function0[_])]](w, "hookBeforeStop") should be('empty)
        app.watchRefT should have size (1)
        app.watchSetT should have size (1)
      }
      it("should have proper afterStop implementation") {
        val tester = mock[WatchSpec.Tester]
        val app = App.inner.asInstanceOf[TestApp]
        app.watchRefT should have size (1)
        app.watchSetT should have size (1)
        val w = spy(app.watch(A)) once () makeAfterStop { tester.a }
        app.watchRefT should have size (2)
        app.watch(A) on ()
        app.watchRefT should have size (2)
        app.watch(A) off ()
        app.watchRefT should have size (1)
        verify(tester).a()
        verifyNoMoreInteractions(tester)
        reset(tester)

        getViaReflection[Seq[(Int, Function0[_])]](w, "hookAfterStart") should be('empty)
        getViaReflection[Seq[(Int, Function0[_])]](w, "hookAfterStop") should be('empty)
        getViaReflection[Seq[(Int, Function0[_])]](w, "hookBeforeStart") should be('empty)
        getViaReflection[Seq[(Int, Function0[_])]](w, "hookBeforeStop") should be('empty)
        w once () makeAfterStop { tester.a } times (2) makeAfterStop { tester.a }
        getViaReflection[Seq[(Int, Function0[_])]](w, "hookAfterStart") should be('empty)
        getViaReflection[Seq[(Int, Function0[_])]](w, "hookAfterStop") should have size (2)
        getViaReflection[Seq[(Int, Function0[_])]](w, "hookBeforeStart") should be('empty)
        getViaReflection[Seq[(Int, Function0[_])]](w, "hookBeforeStop") should be('empty)
        app.watchRefT should have size (2)
        app.watchSetT should have size (1)
        app.watch(A) on ()
        verify(tester, never).a()
        app.watchRefT should have size (2)
        app.watch(A) off ()
        verify(tester, times(2)).a()
        reset(tester)
        getViaReflection[Seq[(Int, Function0[_])]](w, "hookAfterStart") should be('empty)
        getViaReflection[Seq[(Int, Function0[_])]](w, "hookAfterStop") should have size (1)
        getViaReflection[Seq[(Int, Function0[_])]](w, "hookBeforeStart") should be('empty)
        getViaReflection[Seq[(Int, Function0[_])]](w, "hookBeforeStop") should be('empty)
        app.watch(A) on ()
        app.watch(A) off ()
        getViaReflection[Seq[(Int, Function0[_])]](w, "hookAfterStart") should be('empty)
        getViaReflection[Seq[(Int, Function0[_])]](w, "hookAfterStop") should be('empty)
        getViaReflection[Seq[(Int, Function0[_])]](w, "hookBeforeStart") should be('empty)
        getViaReflection[Seq[(Int, Function0[_])]](w, "hookBeforeStop") should be('empty)
        app.watchRefT should have size (1)
        app.watchSetT should have size (1)
      }
    }

    it("should handle arguments") {

    }

    it("should handle complex behaviour") {

    }
  }

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
