/**
 * This file is part of the TA Buddy project.
 * Copyright (c) 2014 Alexey Aksenov ezh@ezh.msk.ru
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

package org.digimead.tabuddy.desktop.core.ui.inspector.command

import java.util.UUID
import java.util.concurrent.{ CancellationException, Exchanger }
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.definition.Operation
import org.digimead.tabuddy.desktop.core.definition.command.Command
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.ui.inspector.Messages
import org.digimead.tabuddy.desktop.core.ui.inspector.operation.OperationInspector
import org.eclipse.core.runtime.jobs.Job
import scala.concurrent.Future

/**
 * Show inspector.
 */
object CommandInspector extends XLoggable {
  import Command.parser._
  /** Akka execution context. */
  implicit lazy val ec = App.system.dispatcher
  /** Command description. */
  implicit lazy val descriptor = Command.Descriptor(UUID.randomUUID())(Messages.inspector_text,
    Messages.inspectorDescriptionShort_text, Messages.inspectorDescriptionLong_text,
    (activeContext, parserContext, parserResult) ⇒ Future {
      val exchanger = new Exchanger[Operation.Result[Unit]]()
      OperationInspector().foreach { operation ⇒
        operation.getExecuteJob() match {
          case Some(job) ⇒
            job.setPriority(Job.LONG)
            job.onComplete(exchanger.exchange).schedule()
          case None ⇒
            throw new RuntimeException(s"Unable to create job for ${operation}.")
        }
      }
      exchanger.exchange(null) match {
        case Operation.Result.OK(result, message) ⇒
          log.info(s"Operation completed successfully.")
        case Operation.Result.Cancel(message) ⇒
          throw new CancellationException(s"Operation canceled, reason: ${message}.")
        case err: Operation.Result.Error[_] ⇒
          throw err
        case other ⇒
          throw new RuntimeException(s"Unable to complete operation: ${other}.")
      }
    })
  /** Command parser. */
  lazy val parser = Command.CmdParser(descriptor.name)
}
