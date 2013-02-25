/**
 * This file is part of the TABuddy project.
 * Copyright (c) 2012-2013 Alexey Aksenov ezh@ezh.msk.ru
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

package org.digimead.tabuddy.desktop.debug

import java.lang.management.ManagementFactory

import org.digimead.digi.lib.log.Loggable
import org.digimead.digi.lib.log.logger.RichLogger.rich2slf4j
import org.digimead.tabuddy.desktop.Main
import org.digimead.tabuddy.desktop.debug.Console.console2implementation
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.Model.model2implementation
import org.digimead.tabuddy.model.element.Element

import javax.management.ObjectName

// The defined MBean Interface
trait DebugMBean {
  def console()
  def dumpModel()
  def subscribeChanges()
  def unsubscribeChanges()
}

class Debug extends DebugMBean with Loggable {
  ManagementFactory.getPlatformMBeanServer.registerMBean(this, new ObjectName("org.digimead.tabuddy.desktop:type=Debug"))

  def console {
    new Thread(new Runnable {
      def run = try {
        log.debug("show console")
        Console.show
      } catch {
        case e: Throwable =>
          log.error("unable to show console: " + e, e)
      }
    }).start
  }
  def dumpModel {
    log.debug("current model dump: \n" + Model.eDump(false))
  }
  def subscribeChanges {
    log.debug("subscribe to element changes")
    if (!Debug.elementSubscriberActive)
      Element.Event.subscribe(Debug.elementSubscriber)
  }
  def unsubscribeChanges {
    log.debug("unsubscribe to element changes")
    if (Debug.elementSubscriberActive)
      Element.Event.removeSubscription(Debug.elementSubscriber)
  }
}

object Debug extends Loggable {
  @volatile private var elementSubscriberActive = false
  val elementSubscriber = new Element.Event.Sub() {
    def notify(pub: Element.Event.Pub, event: Element.Event) = event match {
      case Element.Event.ValueInclude(el, v, m) =>
        log.___gaze("INC " + v)
      case _ =>
        log.___glance("event " + event)
    }
  }

  /** Starts application via scala interactive console */
  def main(args: Array[String]) {
    val thread = new Thread(new Runnable { def run = Main.main(args) })
    thread.start
  }
  /** Invoke protected private method from specific object */
  def invoke[T <: Any](obj: AnyRef, method: String, args: AnyRef*): T = {
    val methodRefl = obj.getClass().getMethod(method, args.map(_.getClass): _*)
    methodRefl.invoke(obj, args: _*).asInstanceOf[T]
  }
}
