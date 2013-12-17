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

import akka.actor.ActorDSL.{ Act, actor }
import java.util.concurrent.{ Exchanger, TimeUnit }
import org.digimead.tabuddy.desktop.core.Core
import org.digimead.tabuddy.desktop.core.support.App
import org.mockito.Mockito
import org.scalatest.WordSpec
import scala.collection.JavaConverters.asScalaBufferConverter

class UISpec extends WordSpec with Test.Base {
  "A UI" must {
    "be consistent after startup" in {
      implicit val option = Mockito.times(31)
      withLogCaptor {
        coreBundle.start()
        implicit val akka = App.system
        val exchanger = new Exchanger[Boolean]()
        val listener = actor(new Act {
          become { case App.Message.Consistent(Core, _) ⇒ exchanger.exchange(true) }
          whenStarting { App.system.eventStream.subscribe(self, classOf[App.Message.Consistent[_]]) }
          whenStopping { App.system.eventStream.unsubscribe(self, classOf[App.Message.Consistent[_]]) }
        })
        exchanger.exchange(false, 1000, TimeUnit.MILLISECONDS) should be(true)
      } { logCaptor ⇒
        val a = logCaptor.getAllValues().asScala
        val b = a.dropWhile(m ⇒ m.getLevel() == org.apache.log4j.Level.DEBUG &&
          m.getMessage() == "Start TA Buddy Desktop core.")
        b should not be ('empty)
        val c = b.dropWhile(m ⇒ m.getLevel() == org.apache.log4j.Level.WARN &&
          m.getMessage() == "Skip DI initialization and event loop creation in test environment.")
        c should not be ('empty)
        c.find(m ⇒ m.getLevel() == org.apache.log4j.Level.DEBUG &&
          m.getMessage().toString.startsWith("started (org.digimead.tabuddy.desktop.core.Core@")) should not be ('empty)
      }
      Thread.sleep(1000)
      log.___glance("AAAAAAA")
      startEventLoop()
      /*withLogCaptor {
        UIBundle.start()
        implicit val akka = App.system
        val exchanger = new Exchanger[Boolean]()
        val listener = actor(new Act {
          become { case App.Message.Consistent(UI, _) ⇒ exchanger.exchange(true) }
          whenStarting { App.system.eventStream.subscribe(self, classOf[App.Message.Consistent[_]]) }
          whenStopping { App.system.eventStream.unsubscribe(self, classOf[App.Message.Consistent[_]]) }
        })
        exchanger.exchange(false, 1000, TimeUnit.MILLISECONDS) should be(true)
      } { logCaptor ⇒
        val a = logCaptor.getAllValues().asScala
        val b = a.dropWhile(m ⇒ m.getLevel() == org.apache.log4j.Level.DEBUG &&
          m.getMessage() == "Start TA Buddy Desktop core.")
        b should not be ('empty)
        val c = b.dropWhile(m ⇒ m.getLevel() == org.apache.log4j.Level.WARN &&
          m.getMessage() == "Skip DI initialization and event loop creation in test environment.")
        c should not be ('empty)
        c.find(m ⇒ m.getLevel() == org.apache.log4j.Level.DEBUG &&
          m.getMessage().toString.startsWith("started (org.digimead.tabuddy.desktop.core.Core@")) should not be ('empty)
      }*/
      Thread.sleep(1000)
      log.___glance("BBBB")
      stopEventLoop()
      // stop is invoked on test shutdown
    }
  }
}
