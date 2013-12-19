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

package org.digimead.tabuddy.desktop.logic

import com.escalatesoft.subcut.inject.NewBindingModule
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.support.App.app2implementation
import org.mockito.Mockito
import org.osgi.framework.Bundle
import org.scalatest.WordSpec
import scala.collection.JavaConverters.asScalaBufferConverter

class LogicSpec extends WordSpec with Test.Base {
  //  before {
  //    EclipseHelper.helpWithContextDebugHelper()
  //    val internalPlatform = org.eclipse.core.internal.runtime.InternalPlatform.getDefault()
  //EnvironmentInfo
  /*println("!!!!!!!!" + eeeeeee)
    DependencyInjection(
      new NewBindingModule(module ⇒ {
        module.bind[Report] toModuleSingle { implicit module ⇒ null }
        module.bind[api.Config] toModuleSingle { implicit module ⇒ new Config }
      }) ~ org.digimead.tabuddy.desktop.logic.default ~
        org.digimead.tabuddy.desktop.default ~
        org.digimead.tabuddy.model.default ~
        org.digimead.digi.lib.default, false)*/
  //    adjustLoggingBefore
  //    adjustOSGiBefore
  //  }
  /*  "A Logic" should {
    "should successfully loaded" in {
      // All minimal dependencies are correct.
      Activator
      Config
      Data
      Default
      Logic
      assert(true)
    }
  }*/
  "A Logic" must {
    "be consistent after startup" in {
      implicit val option = Mockito.atLeast(1)
      startInternalPlatformBeforeAll()
      startCoreBeforeAll(coreStartLogMessages, false)
      withLogCaptor {
        logicBundle.start()
        startEventLoop()
        App.watch(Logic) waitForStart ()
      } { logCaptor ⇒
        val messages = logCaptor.getAllValues().asScala
        //println(messages.map(m ⇒ "---> " + m.getLoggerName() + m.getMessage()).mkString("\n"))
        messages.find { event ⇒ event.getLoggerName() == "@logic.Logic" && event.getMessage == "Return integrity." } should not be ('empty)
      }
      coreBundle.getBundleContext() should not be (null)
      coreBundle.getState() should be(Bundle.ACTIVE)
      UIBundle.getBundleContext() should be(null)
      logicBundle.getBundleContext() should not be (null)
      logicBundle.getState() should be(Bundle.ACTIVE)
      App.isUIAvailable should be(false)

      stopEventLoop()
      stopInternalPlatformAfterAll()
      // stop is invoked on test shutdown
    }
  }

  override def config = super.config ~ new NewBindingModule(module ⇒ {
    module.bind[api.Config] toModuleSingle { implicit module ⇒ new Config }
  }) ~ org.digimead.tabuddy.desktop.logic.default ~
    org.digimead.tabuddy.desktop.core.default ~
    org.digimead.tabuddy.model.default ~
    org.digimead.digi.lib.default
}
