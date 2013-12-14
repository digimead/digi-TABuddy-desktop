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

import akka.actor.ActorDSL.{ Act, actor }
import com.escalatesoft.subcut.inject.NewBindingModule
import java.util.concurrent.{ Exchanger, TimeUnit }
import org.digimead.digi.lib.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.lib.test.{ LoggingHelper, OSGiHelper }
import org.digimead.tabuddy.desktop.core.support.App
import org.mockito.Mockito
import org.osgi.framework.Bundle
import org.scalatest.{ Matchers, Tag }
import scala.collection.JavaConversions.asScalaIterator

object Test {
  trait Base extends Matchers with OSGiHelper with LoggingHelper with Loggable {
    val testBundleClass = org.digimead.digi.lib.default.getClass()

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
      module.bind[App] toSingle { new TestApp }
    })
    /** Get Core bundle. */
    def coreBundle = osgiRegistry.get.getBundleContext().getBundles().find(_.getSymbolicName() == "org.digimead.tabuddy.desktop.core").get
    /** Start OSGi environment. */
    def startOSGiEnv() {
      for {
        registry ← osgiRegistry
        descriptors ← Option(registry.getBundleDescriptors())
      } {
        descriptors.iterator().flatMap(descriptor ⇒ Option(registry.loadBundle(descriptor))).foreach {
          case skip if (skip.getSymbolicName() == "org.digimead.tabuddy.desktop.core") ⇒
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
    def startCoreBeforeAll(n: Int = 31) {
      adjustLoggingBefore
      withLogCaptor({
        coreBundle.start()
        implicit val akka = App.system
        val exchanger = new Exchanger[Boolean]()
        val listener = actor(new Act {
          become { case App.Message.Consistent(Core, _) ⇒ exchanger.exchange(true) }
          whenStarting { App.system.eventStream.subscribe(self, classOf[App.Message.Consistent[_]]) }
          whenStopping { App.system.eventStream.unsubscribe(self, classOf[App.Message.Consistent[_]]) }
        })
        exchanger.exchange(false, 1000, TimeUnit.MILLISECONDS) should be(true)
      })(_ ⇒ {})(Mockito.times(n)) // assert that Core bundle started successful
      // stop is invoked on test shutdown
      adjustLoggingAfter
    }

    class TestApp extends App {
      override def bundle(clazz: Class[_]) = clazz.getName() match {
        case "org.digimead.tabuddy.desktop.core.Report$DI$" ⇒ coreBundle
        case "org.digimead.tabuddy.desktop.core.Messages$" ⇒ coreBundle
        case c ⇒ throw new RuntimeException("TestApp unknown class " + c)
      }
    }

    object Mark extends Tag("Mark")
  }
}
