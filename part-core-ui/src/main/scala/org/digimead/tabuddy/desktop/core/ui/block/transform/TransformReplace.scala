/**
 * This file is part of the TA Buddy project.
 * Copyright (c) 2013-2015 Alexey Aksenov ezh@ezh.msk.ru
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

package org.digimead.tabuddy.desktop.core.ui.block.transform

import akka.pattern.{ ask, AskTimeoutException }
import org.digimead.digi.lib.api.XDependencyInjection
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.support.Timeout
import org.digimead.tabuddy.desktop.core.ui.block.{ Configuration, StackSupervisor }
import org.digimead.tabuddy.desktop.core.ui.block.builder.ViewContentBuilder
import org.digimead.tabuddy.desktop.core.ui.definition.widget.{ AppWindow, VComposite, VEmpty }
import scala.concurrent.{ Await, Future }
import scala.language.implicitConversions

class TransformReplace extends XLoggable {
  /** Akka execution context. */
  implicit lazy val ec = App.system.dispatcher
  /** Akka communication timeout. */
  implicit val timeout = akka.util.Timeout(Timeout.short)

  def apply(ss: StackSupervisor, window: AppWindow, viewConfiguration: Configuration.CView): Option[VComposite] = {
    ss.wComposite.flatMap { wComposite ⇒
      log.debug(s"Replace window ${window} content with ${viewConfiguration}.")
      val errors = try {
        val futures = ss.context.children.map {
          child ⇒
            log.debug("Destroy " + child)
            try {
              implicit val timeout = akka.util.Timeout(Timeout.shortest / 2)
              child ? App.Message.Destroy()
            } catch {
              case e: AskTimeoutException ⇒
                log.warn("Unable to destroy view: " + e.getMessage, e)
                // Ignore timeout exception
                // This situation usually occurs when actor was stopped within ask pattern
                Future { App.Message.Destroy(e.getMessage, None) }
            }
        }
        val futureSeq =
          try Future.sequence(futures)
          catch {
            case e: AskTimeoutException ⇒
              log.warn("Unable to built future sequence for views destruction: " + e.getMessage, e)
              // Ignore timeout exception
              // This situation usually occurs when actor was stopped within ask pattern
              Future { Seq.empty }
          }
        val result =
          try Await.result(futureSeq, Timeout.short)
          catch {
            case e: AskTimeoutException ⇒
              log.warn("Unable to await results: " + e.getMessage, e)
              // Ignore timeout exception
              // This situation usually occurs when actor was stopped within ask pattern
              Seq.empty
          }
        result.filter(_ match {
          case App.Message.Error(message, _) ⇒ true
          case _ ⇒ false
        })
      } catch {
        case e: AskTimeoutException ⇒
          log.warn("Skip views destruction: " + e.getMessage, e)
          // Ignore timeout exception
          // This situation usually occurs when actor was stopped within ask pattern
          Seq.empty
      }
      if (errors.isEmpty) {
        ss.lastActiveViewIdForCurrentWindow.set(None)
        val exists = App.execNGet {
          // There may be only VEmpty child or nothing by design
          wComposite.getChildren().flatMap {
            case empty: VEmpty ⇒
              empty.dispose()
              None
            case other ⇒
              Some(other)
          }
        }
        if (exists.nonEmpty)
          throw new IllegalStateException(s"Unable to create ${viewConfiguration}. Container already in use by ${exists.mkString(",")}.")
        ViewContentBuilder.container(viewConfiguration, wComposite, ss.parentContext, ss.context, None)
      } else {
        errors.foreach(err ⇒ log.error(s"Unable to TransformReplace ${viewConfiguration}: ${err.asInstanceOf[App.Message.Error].message}"))
        None
      }
    }
  }
}

object TransformReplace {
  implicit def transform2implementation(t: TransformReplace.type): TransformReplace = inner

  def inner: TransformReplace = DI.implementation

  /**
   * Dependency injection routines
   */
  private object DI extends XDependencyInjection.PersistentInjectable {
    /** TransformReplace implementation */
    lazy val implementation = injectOptional[TransformReplace] getOrElse new TransformReplace
  }
}
