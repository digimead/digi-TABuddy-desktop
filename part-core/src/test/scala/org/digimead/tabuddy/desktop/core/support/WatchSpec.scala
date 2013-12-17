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
import org.digimead.tabuddy.desktop.core.support.app.Watch
import org.digimead.tabuddy.desktop.core.{ Core, Test }
import org.eclipse.core.runtime.jobs.Job
import org.mockito.Matchers
import org.mockito.Mockito._
import org.scalatest.{ FunSpec, Finders }
import scala.collection.immutable
import scala.language.reflectiveCalls

class WatchSpec extends FunSpec with Test.Base {
  @volatile var savedWRef = immutable.Map[Int, Seq[Watch.Watcher]]()
  @volatile var savedWSet = Set[Int]()

  describe("A watcher") {
    describe("with always parameter") {
      it("should have proper beforeStart implementation") {
        val tester = mock[WatchSpec.Tester]
        val app = App.inner.asInstanceOf[WatchTestApp]
        val inOrderT = org.mockito.Mockito.inOrder(tester)
        app.watchRefT should be('empty)
        app.watchSetT should be('empty)
        val w = spy(app.watch(A)) always () makeBeforeStart { tester.a }
        val inOrderW = org.mockito.Mockito.inOrder(w)
        reset(w)
        verifyNoMoreInteractions(w)
        w on { tester.a1 }
        w.isActive should be(true)
        verifyReflection(inOrderW, w, times(1), "on") { case Seq(f) ⇒ f.getValue() shouldBe a[Function0[_]] }
        verifyReflection(inOrderW, w, times(2), "process") { case Seq(f) ⇒ f.getValue() shouldBe a[Iterable[_]] }

        app.watchRefT should have size (1)
        app.watchSetT should have size (1)
        inOrderT.verify(tester).a
        inOrderT.verify(tester).a1
        intercept[IllegalStateException] { w on { tester.a1 } }
        w.off()
        w.isActive should be(false)
        w.on { tester.a1 }
        w.isActive should be(true)
        inOrderT.verify(tester).a
        inOrderT.verify(tester).a1
        tester.a
        tester.a1
        inOrderT.verify(tester).a
        inOrderT.verify(tester).a1
        verifyNoMoreInteractions(tester)
        intercept[IllegalStateException] { app.watch(A) on () }
        app.watch(A) off ()
        w.isActive should be(false)
        app.watch(A) on ()
        w.isActive should be(true)
        inOrderT.verify(tester).a
        w.reset()
        app.watchRefT should be('empty)
        app.watch(A) off ()
        w.isActive should be(false)
        app.watch(A) on ()
        w.isActive should be(false)
        app.watch(A) off ()
        app.watchRefT should be('empty)

        app.watchSetT should be('empty)
        verifyNoMoreInteractions(tester)
      }
      it("should have proper afterStart implementation") {
        val tester = mock[WatchSpec.Tester]
        val app = App.inner.asInstanceOf[WatchTestApp]
        val inOrderT = org.mockito.Mockito.inOrder(tester)
        app.watchRefT should be('empty)
        app.watchSetT should be('empty)
        val w = spy(app.watch(A)) always () makeAfterStart { tester.a }
        val inOrderW = org.mockito.Mockito.inOrder(w)
        reset(w)
        verifyNoMoreInteractions(w)
        w on { tester.a1 }
        verifyReflection(inOrderW, w, times(1), "on") { case Seq(f) ⇒ f.getValue() shouldBe a[Function0[_]] }
        verifyReflection(inOrderW, w, times(2), "process") { case Seq(f) ⇒ f.getValue() shouldBe a[Iterable[_]] }

        app.watchRefT should have size (1)
        app.watchSetT should have size (1)
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
        app.watchRefT should be('empty)
        app.watch(A) off ()
        app.watch(A) on ()
        app.watch(A) off ()
        app.watchRefT should be('empty)

        app.watchSetT should be('empty)
        verifyNoMoreInteractions(tester)
      }
      it("should have proper beforeStop implementation") {
        val tester = mock[WatchSpec.Tester]
        val app = App.inner.asInstanceOf[WatchTestApp]
        val inOrderT = org.mockito.Mockito.inOrder(tester)
        app.watchRefT should be('empty)
        app.watchSetT should be('empty)
        val w = spy(app.watch(A)) always () makeBeforeStop { tester.a }
        val inOrderW = org.mockito.Mockito.inOrder(w)
        reset(w)
        verifyNoMoreInteractions(w)
        w on ()

        app.watchRefT should have size (1)
        app.watchSetT should have size (1)
        intercept[IllegalStateException] { w on { tester.a1 } }
        w.off { tester.a1 }
        verifyReflection(inOrderW, w, times(1), "on") { case Seq(f) ⇒ f.getValue() shouldBe a[Function0[_]] }
        verifyReflection(inOrderW, w, calls(2), "process") { case Seq(f) ⇒ f.getValue() shouldBe a[Iterable[_]] }
        verifyReflection(inOrderW, w, times(1), "off") { case Seq(f) ⇒ f.getValue() shouldBe a[Function0[_]] }
        verifyReflection(inOrderW, w, times(2), "process") { case Seq(f) ⇒ f.getValue() shouldBe a[Iterable[_]] }
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
        app.watchRefT should be('empty)
        app.watch(A) off ()
        app.watch(A) on ()
        app.watch(A) off ()
        app.watchRefT should be('empty)

        app.watchSetT should be('empty)
        verifyNoMoreInteractions(tester)
      }
      it("should have proper afterStop implementation") {
        val tester = mock[WatchSpec.Tester]
        val app = App.inner.asInstanceOf[WatchTestApp]
        val inOrderT = org.mockito.Mockito.inOrder(tester)
        app.watchRefT should be('empty)
        app.watchSetT should be('empty)
        val w = spy(app.watch(A)) always () makeAfterStop { tester.a }
        val inOrderW = org.mockito.Mockito.inOrder(w)
        reset(w)
        verifyNoMoreInteractions(w)
        w on ()

        app.watchRefT should have size (1)
        app.watchSetT should have size (1)
        intercept[IllegalStateException] { w on { tester.a1 } }
        w.off { tester.a1 }
        verifyReflection(inOrderW, w, times(1), "on") { case Seq(f) ⇒ f.getValue() shouldBe a[Function0[_]] }
        verifyReflection(inOrderW, w, calls(2), "process") { case Seq(f) ⇒ f.getValue() shouldBe a[Iterable[_]] }
        verifyReflection(inOrderW, w, times(1), "off") { case Seq(f) ⇒ f.getValue() shouldBe a[Function0[_]] }
        verifyReflection(inOrderW, w, times(2), "process") { case Seq(f) ⇒ f.getValue() shouldBe a[Iterable[_]] }
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
        app.watchRefT should be('empty)
        app.watch(A) off ()
        app.watch(A) on ()
        app.watch(A) off ()
        app.watchRefT should be('empty)

        app.watchSetT should be('empty)
        verifyNoMoreInteractions(tester)
      }
    }
    describe("with once parameter") {
      it("should have proper beforeStart implementation", Mark) {
        val tester = mock[WatchSpec.Tester]
        val app = App.inner.asInstanceOf[WatchTestApp]
        app.watchRefT should be('empty)
        app.watchSetT should be('empty)
        val w = spy(app.watch(A)) once () makeBeforeStart { tester.a }
        app.watchRefT should have size (1)
        app.watch(A) on ()
        app.watchRefT should have size (1) // it still active, but empty
        app.watch(A) off ()
        verify(tester).a()
        verifyNoMoreInteractions(tester)
        reset(tester)
        app.watchRefT should be('empty)

        w.isActive should be(false)

        getViaReflection[Seq[(Int, Function0[_])]](w, "hookAfterStart") should be('empty)
        getViaReflection[Seq[(Int, Function0[_])]](w, "hookAfterStop") should be('empty)
        getViaReflection[Seq[(Int, Function0[_])]](w, "hookBeforeStart") should be('empty)
        getViaReflection[Seq[(Int, Function0[_])]](w, "hookBeforeStop") should be('empty)
        w once () makeBeforeStart { tester.a } times (2) makeBeforeStart { tester.a }
        getViaReflection[Seq[(Int, Function0[_])]](w, "hookAfterStart") should be('empty)
        getViaReflection[Seq[(Int, Function0[_])]](w, "hookAfterStop") should be('empty)
        getViaReflection[Seq[(Int, Function0[_])]](w, "hookBeforeStart") should have size (2)
        getViaReflection[Seq[(Int, Function0[_])]](w, "hookBeforeStop") should be('empty)
        w.hooks._3 should have size (2)
        app.watchRefT should have size (1)
        app.watchSetT should be('empty)
        app.watch(A) on ()
        verify(tester, times(2)).a()
        reset(tester)
        app.watchRefT should have size (1)
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
        app.watchRefT should be('empty)

        app.watchSetT should be('empty)
      }
      it("should have proper afterStart implementation") {
        val tester = mock[WatchSpec.Tester]
        val app = App.inner.asInstanceOf[WatchTestApp]
        app.watchRefT should be('empty)
        app.watchSetT should be('empty)
        val w = spy(app.watch(A)) once () makeAfterStart { tester.a }
        app.watchRefT should have size (1)
        app.watch(A) on ()
        app.watchRefT should have size (1)
        app.watch(A) off ()
        app.watchRefT should be('empty)
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
        app.watchRefT should have size (1)
        app.watchSetT should be('empty)
        app.watch(A) on ()
        verify(tester, times(2)).a()
        reset(tester)
        app.watchRefT should have size (1)
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
        app.watchRefT should be('empty)

        app.watchSetT should be('empty)
      }
      it("should have proper beforeStop implementation") {
        val tester = mock[WatchSpec.Tester]
        val app = App.inner.asInstanceOf[WatchTestApp]
        app.watchRefT should be('empty)
        app.watchSetT should be('empty)
        val w = spy(app.watch(A)) once () makeBeforeStop { tester.a }
        app.watchRefT should have size (1)
        app.watch(A) on ()
        app.watchRefT should have size (1)
        app.watch(A) off ()
        app.watchRefT should be('empty)
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
        app.watchRefT should have size (1)
        app.watchSetT should be('empty)
        app.watch(A) on ()
        verify(tester, never).a()
        app.watchRefT should have size (1)
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
        app.watchRefT should be('empty)

        app.watchSetT should be('empty)
      }
      it("should have proper afterStop implementation") {
        val tester = mock[WatchSpec.Tester]
        val app = App.inner.asInstanceOf[WatchTestApp]
        app.watchRefT should be('empty)
        app.watchSetT should be('empty)
        val w = spy(app.watch(A)) once () makeAfterStop { tester.a }
        app.watchRefT should have size (1)
        app.watch(A) on ()
        app.watchRefT should have size (1)
        app.watch(A) off ()
        app.watchRefT should be('empty)
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
        app.watchRefT should have size (1)
        app.watchSetT should be('empty)
        app.watch(A) on ()
        verify(tester, never).a()
        app.watchRefT should have size (1)
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
        app.watchRefT should be('empty)
        app.watchSetT should be('empty)
      }
    }

    it("should handle arguments") {
      val app = App.inner.asInstanceOf[WatchTestApp]
      app.watchRefT should be('empty)
      app.watchSetT should be('empty)
      val w = spy(app.watch(A))
      getViaReflection[Seq[(Int, Function0[_])]](w, "argTimes") should be(1)
      w times (2)
      getViaReflection[Seq[(Int, Function0[_])]](w, "argTimes") should be(2)
      w once ()
      getViaReflection[Seq[(Int, Function0[_])]](w, "argTimes") should be(1)
      w always ()
      getViaReflection[Seq[(Int, Function0[_])]](w, "argTimes") should be(-1)
    }

    it("should handle complex behaviour") {
      var state = Set.empty[String]
      val app = App.inner.asInstanceOf[WatchTestApp]
      val ab = spy(app.watch(A, B)) always () makeAfterStart { app.synchronized { state += "ab" } } makeAfterStop { app.synchronized { state -= "ab" } }
      val ac = spy(app.watch(A, C)) always () makeAfterStart { app.synchronized { state += "ac" } } makeAfterStop { app.synchronized { state -= "ac" } }
      val bc = spy(app.watch(B, C)) always () makeAfterStart { app.synchronized { state += "bc" } } makeAfterStop { app.synchronized { state -= "bc" } }
      val abc = spy(app.watch(A, B, C)) always () makeAfterStart { app.synchronized { state += "abc" } } makeAfterStop { app.synchronized { state -= "abc" } }
      state should be('empty)
      app.watch(A) on ()
      state should be('empty)
      app.watch(A, B) on ()
      state should be(Set("ab"))
      app.watch(B, C) on ()
      state should be(Set("ab", "bc", "ac", "abc"))
      app.watch(B) off ()
      state should be(Set("ac"))
      app.watch(C) off ()
      state should be('empty)
      app.watch(B) on ()
      state should be(Set("ab"))
      app.watch(A, B) off ()
    }

    it("should have proper sequence") {
      val state = new collection.mutable.ArrayBuffer[String]() with collection.mutable.SynchronizedBuffer[String]
      val app = App.inner.asInstanceOf[WatchTestApp]
      val a = spy(app.watch(A)).always().
        makeBeforeStart { app.synchronized { state += "a makeBeforeStart" } }.
        makeAfterStart { app.synchronized { state += "a makeAfterStart" } }.
        makeBeforeStop { app.synchronized { state += "a makeBeforeStop" } }.
        makeAfterStop { app.synchronized { state += "a makeAfterStop" } }
      a on { state += "a on" }
      a off { state += "a off" }
      state should be(List("a makeBeforeStart", "a on", "a makeAfterStart", "a makeBeforeStop", "a off", "a makeAfterStop"))
      state.clear()

      for (i ← 1 until 1000) {
        N3.start
        N2.start
        N1.start
        N2.stop
        N2.start
        N1.stop
        N3.stop
        N2.stop
        val (a1, b1) = state.partition(_.contains("actual"))
        a1 should be(List("N1 actualStart", "N2 actualStart", "N3 actualStart", "N3 actualStop", "N2 actualStop",
          "N2 actualStart", "N3 actualStart", "N3 actualStop", "N2 actualStop", "N1 actualStop"))
        b1 should be(List("N3 start", "N2 start", "N1 start", "N2 stop", "N2 start", "N1 stop", "N3 stop", "N2 stop"))
        state.clear()

        N1.start
        N2.start
        N3.start
        app.watchRefT should have size (6)
        App.watch(N1) should not be ('empty)
        App.watch(N1.X, N2) should not be ('empty)
        App.watch(N2.X, N3) should not be ('empty)
        N3.stop
        N2.stop
        N1.stop
        val (a2, b2) = state.partition(_.contains("actual"))
        a2 should be(Seq("N1 actualStart", "N2 actualStart", "N3 actualStart", "N3 actualStop", "N2 actualStop", "N1 actualStop"))
        b2 should be(Seq("N1 start", "N2 start", "N3 start", "N3 stop", "N2 stop", "N1 stop"))
        state.clear()
        app.watchSetT should be('empty)
      }

      // N1 <- N2 <- N3
      object N1 {
        def start = {
          if (App.watch(N1).isEmpty) {
            App.watch(N1).always().
              makeAfterStart { actualStart }.
              makeBeforeStop { actualStop }
          }
          App.watch(N1) on { state += "N1 start" }
        }
        def stop = App.watch(N1) off { state += "N1 stop" }
        def actualStart = App.watch(N1.X) on { state += "N1 actualStart" }
        def actualStop = App.watch(N1.X) off { state += "N1 actualStop" }
        object X
      }
      object N2 {
        def start = {
          if (App.watch(N1.X, N2).isEmpty) {
            App.watch(N1.X, N2).always().
              makeAfterStart { actualStart }.
              makeBeforeStop { actualStop }
          }
          App.watch(N2) on { state += "N2 start" }
        }
        def stop = App.watch(N2) off { state += "N2 stop" }
        def actualStart = App.watch(N2.X) on { state += "N2 actualStart" }
        def actualStop = App.watch(N2.X) off { state += "N2 actualStop" }
        object X
      }
      object N3 {
        def start = {
          if (App.watch(N2.X, N3).isEmpty) {
            App.watch(N2.X, N3).always().
              makeAfterStart { actualStart }.
              makeBeforeStop { actualStop }
          }
          App.watch(N3) on { state += "N3 start" }
        }
        def stop = App.watch(N3) off { state += "N3 stop" }
        def actualStart = App.watch(N3.X) on { state += "N3 actualStart" }
        def actualStop = App.watch(N3.X) off { state += "N3 actualStop" }
        object X
      }
    }
    it("should synchronize state after startup if required") {
      val tester = mock[WatchSpec.Tester]
      val app = App.inner.asInstanceOf[WatchTestApp]
      app.watchRefT should be('empty)
      app.watchSetT should be('empty)
      app.watch(A) on ()
      val a = app.watch(A).always().
        makeBeforeStart { tester.a }.
        makeAfterStart { tester.a1 }.
        makeBeforeStop { tester.b }.
        makeAfterStop { tester.b1 }
      a.isActive should be(false)
      a.sync()
      a.isActive should be(true)
      verify(tester).a()
      verify(tester).a1()
      verify(tester, never).b()
      verify(tester, never).b1()
      verifyNoMoreInteractions(tester)
      a off ()
    }
  }

  override def beforeAll(configMap: org.scalatest.ConfigMap) {
    super.beforeAll(configMap)
    startCoreBeforeAll()
    startEventLoop()
  }
  override def beforeTest() {
    super.beforeTest()
    val app = App.inner.asInstanceOf[WatchTestApp]
    savedWRef = immutable.HashMap() ++ app.watchRefT
    savedWSet = Set() ++ app.watchSetT
    app.watchRefT.clear()
    app.watchSetT.clear()
  }
  override def afterAll(configMap: org.scalatest.ConfigMap) {
    stopEventLoop()
    super.afterAll(configMap)
  }
  override def afterTest() {
    super.afterTest()
    val app = App.inner.asInstanceOf[WatchTestApp]
    app.watchRefT.clear()
    app.watchSetT.clear()
    app.watchRefT ++= savedWRef
    app.watchSetT ++= savedWSet
    savedWRef = immutable.HashMap()
    savedWSet = Set()
  }
  override def config = new NewBindingModule(module ⇒ {
    module.bind[App] toSingle { new WatchTestApp }
  }) ~ super.config

  object A
  object B
  object C

  class WatchTestApp extends TestApp {
    def watchSetT = watchSet
    def watchRefT = watchRef
  }
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
