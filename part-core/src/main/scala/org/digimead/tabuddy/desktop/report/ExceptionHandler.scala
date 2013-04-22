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

package org.digimead.tabuddy.desktop.report

import java.lang.Thread.UncaughtExceptionHandler

import org.digimead.digi.lib.log.Loggable
import org.digimead.digi.lib.log.logger.RichLogger.rich2slf4j

class ExceptionHandler extends Loggable {
  ExceptionHandler // initiate lazy initialization

  def register() {
    // don't register again if already registered
    val currentHandler = Thread.getDefaultUncaughtExceptionHandler()
    if (currentHandler.isInstanceOf[ExceptionHandler.Default])
      return
    log.debug("registering default exceptions handler")
    if (currentHandler != null)
      log.debug("current handler class=" + currentHandler.getClass.getName())
    // register default exceptions handler
    Thread.setDefaultUncaughtExceptionHandler(new ExceptionHandler.Default(currentHandler))
  }
}

object ExceptionHandler extends Loggable {
  @annotation.tailrec
  def retry[T](n: Int, timeout: Int = -1)(fn: => T): T = {
    val r = try { Some(fn) } catch { case e: Exception if n > 1 => None }
    r match {
      case Some(x) => x
      case None =>
        if (timeout >= 0) Thread.sleep(timeout)
        log.warn("retry #" + (n - (n - 1)))
        retry(n - 1, timeout)(fn)
    }
  }

  class Default(val defaultExceptionHandler: UncaughtExceptionHandler) extends UncaughtExceptionHandler with Loggable {
    // Default exception handler
    def uncaughtException(t: Thread, e: Throwable) {
      log.error("Unhandled exception in %s: %s".format(t, e), e)
      // call original handler, handler blown up if java.lang.Throwable.getStackTrace return null :-)
      try {
        defaultExceptionHandler.uncaughtException(t, e)
      } catch {
        // catch all exceptions
        case e: Throwable =>
      }
    }
  }
}
