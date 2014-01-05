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

package org.digimead.tabuddy.desktop.core.command

import java.util.UUID
import java.util.concurrent.{ CancellationException, Exchanger }
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.Messages
import org.digimead.tabuddy.desktop.core.console.Console
import org.digimead.tabuddy.desktop.core.definition.Operation
import org.digimead.tabuddy.desktop.core.definition.command.Command
import org.digimead.tabuddy.desktop.core.definition.command.api.Command.Descriptor
import org.digimead.tabuddy.desktop.core.operation.OperationCommands
import org.digimead.tabuddy.desktop.core.support.App
import org.eclipse.core.runtime.jobs.Job
import scala.concurrent.Future

/**
 * Help command that starts 'Get available commands' operation.
 */
object CommandHelp extends Loggable {
  import Command.parser._
  private val allArg = "-all"
  /** Akka execution context. */
  implicit lazy val ec = App.system.dispatcher
  /** Console converter. */
  lazy val converter: PartialFunction[(Descriptor, Any), String] = {
    case (this.descriptor, Seq()) ⇒
      "There are no commands"
    case (this.descriptor, descriptors @ Seq(_*)) ⇒
      descriptors.asInstanceOf[Seq[Command.Descriptor]].sortBy(_.name).map { d ⇒
        s"${Console.BWHITE}${d.name}${Console.RESET} ${d.shortDescription}"
      }.mkString("\n")
  }
  /** Command description. */
  implicit lazy val descriptor = Command.Descriptor(UUID.randomUUID())(Messages.help_text,
    Messages.helpDescriptionShort_text, Messages.helpDescriptionLong_text,
    (activeContext, parserContext, parserResult) ⇒ Future {
      parserResult match {
        case Some(descriptor @ Command.Descriptor(id)) ⇒
          s"${Console.BWHITE}${descriptor.name}${Console.RESET} - ${descriptor.shortDescription}\n\n${descriptor.longDescription}"
        case _ ⇒
          val exchanger = new Exchanger[Operation.Result[Seq[Command.Descriptor]]]()
          OperationCommands(parserResult != Some(allArg)).foreach { operation ⇒
            operation.getExecuteJob() match {
              case Some(job) ⇒
                job.setPriority(Job.LONG)
                job.onComplete(exchanger.exchange).schedule()
              case None ⇒
                log.fatal(s"Unable to create job for ${operation}.")
            }
          }
          exchanger.exchange(null) match {
            case Operation.Result.OK(result, message) ⇒
              log.info(s"Operation completed successfully.")
              result getOrElse Seq.empty
            case Operation.Result.Cancel(message) ⇒
              throw new CancellationException(s"Operation canceled, reason: ${message}.")
            case other ⇒
              throw new RuntimeException(s"Unable to complete operation: ${other}.")
          }
      }
    })
  /** Command parser. */
  lazy val parser = Command.CmdParser(descriptor.name ~> opt(sp ~> ((allArg, Command.Hint(allArg, Some("list all commands"))) | commandsParser)))

  /** Create parser for the list of commands. */
  protected def commandsParser: Command.parser.Parser[Any] = {
    val registered = Command.registered
    if (registered.nonEmpty)
      registered.map(descriptor ⇒
        commandLiteral(descriptor.name, Command.Hint(descriptor.name, Some(s"show '${descriptor.name}' description"))) ^^ { _ ⇒ descriptor }).
        reduceLeft(_ | _)
    else
      success(None)
  }
}
