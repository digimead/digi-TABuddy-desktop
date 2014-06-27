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

package org.digimead.tabuddy.desktop.core.support

import akka.actor.{ Actor, actorRef2Scala }
import akka.event.Logging
import akka.event.Logging.{ InitializeLogger, LogEvent, LoggerInitialized }
import org.digimead.digi.lib.api.XDependencyInjection
import org.digimead.digi.lib.log.api.XLoggable
import org.slf4j.LoggerFactory

/**
 * Akka log bridge.
 */
class AkkaLogBridge extends Actor with XLoggable {
  override def receive: Receive = {
    case InitializeLogger(_) ⇒ sender ! LoggerInitialized
    case event: LogEvent ⇒ try {
      record(event)
    } catch {
      case e: Throwable ⇒
        log.error(s"Unable to process '${event}': ${e.getMessage}", e)
    }
  }
  def record(event: LogEvent) = {
    val log = LoggerFactory.getLogger("¤." + event.logSource)
    event match {
      case e: Logging.Error if AkkaLogBridge.DI.logError ⇒
        Option(e.message) match {
          case Some(message) ⇒
            if (e.cause != null)
              log.error(e.message.toString, e.cause)
            else
              log.error(e.message.toString)
          case None ⇒ log.error(e.cause.getMessage(), e.cause.getCause())
        }
      case e: Logging.Warning if AkkaLogBridge.DI.logWarning ⇒ log.warn(e.message.toString)
      case e: Logging.Info if AkkaLogBridge.DI.logInfo ⇒ log.info(e.message.toString)
      case e: Logging.Debug if AkkaLogBridge.DI.logDebug ⇒ log.debug(e.message.toString)
      case e ⇒
    }
  }
}

object AkkaLogBridge {
  /**
   * Dependency injection routines
   */
  private object DI extends XDependencyInjection.PersistentInjectable {
    lazy val logError = injectOptional[Boolean]("Core.AkkaLogBridge.Error") getOrElse true
    lazy val logWarning = injectOptional[Boolean]("Core.AkkaLogBridge.Warning") getOrElse true
    lazy val logInfo = injectOptional[Boolean]("Core.AkkaLogBridge.Info") getOrElse false
    lazy val logDebug = injectOptional[Boolean]("Core.AkkaLogBridge.Debug") getOrElse false
  }
}
