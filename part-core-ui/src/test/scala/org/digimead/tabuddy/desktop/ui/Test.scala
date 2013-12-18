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

package org.digimead.tabuddy.desktop.ui

import akka.actor.ActorDSL.{Act, actor}
import com.escalatesoft.subcut.inject.NewBindingModule
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.{Exchanger, TimeUnit}
import org.digimead.digi.lib.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.lib.test.{LoggingHelper, OSGiHelper}
import org.digimead.tabuddy.desktop.core.{Core, EventLoop}
import org.digimead.tabuddy.desktop.core.AppService
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.support.Timeout
import org.eclipse.core.internal.runtime.{FindSupport, InternalPlatform}
import org.eclipse.osgi.service.environment.EnvironmentInfo
import org.eclipse.ui.internal.WorkbenchPlugin
import org.mockito.{ArgumentCaptor, InOrder, Mockito}
import org.mockito.verification.VerificationMode
import org.osgi.framework.Bundle
import org.scalatest.{Matchers, Tag}
import scala.collection.JavaConversions.asScalaIterator
import scala.concurrent.Future

object Test {
  trait Base extends Matchers with OSGiHelper with LoggingHelper with Loggable with EventLoop.Initializer {
    val testBundleClass = org.digimead.tabuddy.desktop.ui.default.getClass()
    val app = new ThreadLocal[Future[AnyRef]]()
    val wp = new ThreadLocal[WorkbenchPlugin]()
    val coreStartLogMessages = 30

    after { afterTest() }
    before { beforeTest() }

    override def afterAll(configMap: org.scalatest.ConfigMap) {
      adjustOSGiAfter
    }
    /** Hook that is invoked after each test. */
    def afterTest() {
      adjustLoggingAfter
    }
    override def beforeAll(configMap: org.scalatest.ConfigMap) {
      adjustLoggingBeforeAll(configMap)
      DependencyInjection(config, false)
      System.setProperty(FindSupport.PROP_NL, "")
      adjustOSGiBefore
      startOSGiEnv()
    }
    /** Hook that is invoked before each test. */
    def beforeTest() {
      //DependencyInjection(org.digimead.digi.lib.default, false)
      adjustLoggingBefore
    }
    /** Test component config. */
    def config = org.digimead.digi.lib.cache.default ~ org.digimead.digi.lib.default ~ new NewBindingModule(module ⇒ {
      module.bind[File] identifiedBy ("Config") toSingle { App.bundle(getClass).getDataFile("ui_config") }
      module.bind[App] toSingle { new TestApp }
    })
    /** Get Core bundle. */
    def coreBundle = osgiRegistry.get.getBundleContext().getBundles().find(_.getSymbolicName() == "org.digimead.tabuddy.desktop.core").get
    /** Get protected method via reflection. */
    def getViaReflection[T](instance: AnyRef, name: String): T =
      instance.getClass().getDeclaredMethods().find(_.getName() == name) match {
        case Some(method) ⇒
          if (method.isAccessible())
            method.setAccessible(true)
          method.invoke(instance).asInstanceOf[T]
        case None ⇒
          throw new IllegalArgumentException(s"Method '${name}' not found.")
      }
    /** Start OSGi environment. */
    def startOSGiEnv() {
      for {
        registry ← osgiRegistry
        descriptors ← Option(registry.getBundleDescriptors())
      } {
        descriptors.iterator().flatMap(descriptor ⇒ Option(registry.loadBundle(descriptor))).foreach {
          case skip if (skip.getSymbolicName() == "org.digimead.tabuddy.desktop.core") ⇒
          case skip if (skip.getSymbolicName() == "org.digimead.tabuddy.desktop.ui") ⇒
          case skip if (skip.getSymbolicName() == "org.eclipse.core.resources") ⇒
          case skip if (skip.getSymbolicName() == "org.eclipse.core.runtime") ⇒
          case skip if (skip.getSymbolicName() == "org.eclipse.help") ⇒
          case skip if (skip.getSymbolicName() == "org.eclipse.osgi") ⇒
          case skip if (skip.getSymbolicName() == "org.eclipse.ui") ⇒
          case skip if (skip.getSymbolicName() == "org.eclipse.ui.workbench") ⇒
          case bundle ⇒ bundle.start()
        }
      }
      for {
        registry ← osgiRegistry
        bundle ← registry.getBundleContext().getBundles()
      } if (bundle.getState() == Bundle.ACTIVE)
        log.debug(s"Bundle $bundle is ACTIVE")
    }
    /** Start Core bundle after OSGi environment. */
    def startCoreBeforeAll(n: Int = coreStartLogMessages, l: Boolean = true) {
      if (l) adjustLoggingBefore
      withLogCaptor({
        coreBundle.start()
        implicit val akka = App.system
        val exchanger = new Exchanger[Boolean]()
        val listener = actor(new Act {
          become { case App.Message.Consistent(Core, _) ⇒ exchanger.exchange(true, 1000, TimeUnit.MILLISECONDS) }
          whenStarting { App.system.eventStream.subscribe(self, classOf[App.Message.Consistent[_]]) }
          whenStopping { App.system.eventStream.unsubscribe(self, classOf[App.Message.Consistent[_]]) }
        })
        exchanger.exchange(false, 10000, TimeUnit.MILLISECONDS) should be(true)
      })(_ ⇒ {})(Mockito.atLeast(n)) // assert that Core bundle started successful
      // stop is invoked on test shutdown
      if (l) adjustLoggingAfter
    }
    /** Start InternalPlatform after OSGi environment. */
    def startInternalPlatformBeforeAll() {
      val environmentInfo = mock[EnvironmentInfo]
      osgiRegistry.get.registerService(classOf[EnvironmentInfo].getName(), environmentInfo, null)
      InternalPlatform.getDefault().start(osgiRegistry.get.getBundleContext())
    }
    /** Start event loop after core bundle. */
    def startEventLoop() {
      wp.set(new WorkbenchPlugin())
      WorkbenchPlugin.getDefault() should not be (null)
      EventLoop.thread.start()
      eventLoopThreadSync()
      app.set(Future { AppService.start() }(App.system.dispatcher))
      EventLoop.thread.waitWhile { _ == null }
      assert(App.watch(Core).waitForStart(Timeout.long).isActive, "Unable to start EventLoop and Core actor.")
    }
    /** Stop event loop after core bundle. */
    def stopEventLoop() {
      AppService.stop()
      EventLoop.thread.waitWhile { _.isEmpty }
      App.watch(Core).waitForStop(Timeout.long).isActive should be(false)
    }
    /**
     * Verify via reflection.
     */
    @inline
    def verifyReflection[A, B](mock: A, mode: VerificationMode, methodName: String)(test: Seq[ArgumentCaptor[_]] ⇒ B): B = {
      mock.getClass.getDeclaredMethods().find(_.getName() == methodName) match {
        case Some(method) ⇒
          if (method.isAccessible())
            method.setAccessible(true)
          val args = method.getParameterTypes().map(clazz ⇒ ArgumentCaptor.forClass(clazz)).toSeq
          try method.invoke(org.mockito.Mockito.verify(mock, mode), args.map(_.capture().asInstanceOf[AnyRef]): _*)
          catch {
            case e: InvocationTargetException if e.getCause() != null && e.getCause().isInstanceOf[AssertionError] ⇒
              throw e.getCause()
          }
          test(args)
        case None ⇒
          throw new IllegalArgumentException(s"Method '${methodName}' not found.")
      }
    }
    /**
     * Verify via reflection.
     */
    @inline
    def verifyReflection[A, B](inOrder: InOrder, mock: A, mode: VerificationMode, methodName: String)(test: Seq[ArgumentCaptor[_]] ⇒ B): B = {
      mock.getClass.getDeclaredMethods().find(_.getName() == methodName) match {
        case Some(method) ⇒
          if (method.isAccessible())
            method.setAccessible(true)
          val args = method.getParameterTypes().map(clazz ⇒ ArgumentCaptor.forClass(clazz)).toSeq
          try method.invoke(inOrder.verify(mock, mode), args.map(_.capture().asInstanceOf[AnyRef]): _*)
          catch {
            case e: InvocationTargetException if e.getCause() != null && e.getCause().isInstanceOf[AssertionError] ⇒
              throw e.getCause()
          }
          test(args)
        case None ⇒
          throw new IllegalArgumentException(s"Method '${methodName}' not found.")
      }
    }
    /** Get UI bundle. */
    def UIBundle = osgiRegistry.get.getBundleContext().getBundles().find(_.getSymbolicName() == "org.digimead.tabuddy.desktop.ui").get

    class TestApp extends App {
      override def bundle(clazz: Class[_]) = clazz.getName() match {
        case clazzName if clazzName.startsWith("org.digimead.tabuddy.desktop.core.") ⇒ coreBundle
        case clazzName if clazzName.startsWith("org.digimead.tabuddy.desktop.ui.") ⇒ UIBundle
        case c ⇒ throw new RuntimeException("TestApp unknown class " + c)
      }
    }

    object Mark extends Tag("Mark")
  }
}
