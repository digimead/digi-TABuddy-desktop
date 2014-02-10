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

package org.digimead.tabuddy.desktop.core.ui.command.window

import akka.pattern.ask
import java.util.UUID
import java.util.concurrent.{ CancellationException, Exchanger }
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.definition.Operation
import org.digimead.tabuddy.desktop.core.definition.command.Command
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.support.Timeout
import org.digimead.tabuddy.desktop.core.ui.{ Messages, Resources }
import org.digimead.tabuddy.desktop.core.ui.UI
import org.digimead.tabuddy.desktop.core.ui.block.{ Configuration, View }
import org.digimead.tabuddy.desktop.core.ui.definition.widget.AppWindow
import org.digimead.tabuddy.desktop.core.ui.operation.OperationViewCreate
import org.eclipse.core.runtime.jobs.Job
import scala.concurrent.{ Await, Future }

/**
 * Create new or select exists window by name.
 */
object CommandWindow extends Loggable {
  import Command.parser._
  private val newArg = "-new"
  /** Akka execution context. */
  implicit lazy val ec = App.system.dispatcher
  /** Command description. */
  implicit lazy val descriptor = Command.Descriptor(UUID.randomUUID())(Messages.window_text,
    Messages.windowDescriptionShort_text, Messages.windowDescriptionLong_text,
    (activeContext, parserContext, parserResult) ⇒ Future {
      log.___glance("w")
      /*      val (viewFactory, createNew) = parserResult match {
        case ~(factory: View.Factory, Some(newArg)) ⇒ (factory, true)
        case ~(factory: View.Factory, None) ⇒ (factory, false)
      }
      implicit val ec = App.system.dispatcher
      implicit val timeout = akka.util.Timeout(Timeout.short)
      val exchanger = new Exchanger[Operation.Result[UUID]]()
      val appWindow = Option(activeContext.get(classOf[AppWindow])) orElse UI.getActiveWindow() getOrElse {
        throw new RuntimeException(s"Unable to find active window for ${this}: '${activeContext}'.")
      }
      val stackConfiguration = Await.result((appWindow.supervisorRef ? App.Message.Get(Configuration)).mapTo[Configuration], timeout.duration)
      val existView = if (createNew) None else stackConfiguration.asMap.find {
        case (elementId, (parentId, configuration: Configuration.CView)) ⇒ configuration.factory == viewFactory
        case _ ⇒ false
      }
      existView match {
        case Some(view) ⇒
          throw new CancellationException(s"Operation canceled, reason: Not implemented.")
        case None ⇒
          OperationViewCreate(appWindow, Configuration.CView(viewFactory.configuration)).foreach { operation ⇒
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
              result.flatMap(id ⇒ UI.viewMap.find(_._1.id == id))
            case Operation.Result.Cancel(message) ⇒
              throw new CancellationException(s"Operation canceled, reason: ${message}.")
            case err: Operation.Result.Error[_] ⇒
              throw err
            case other ⇒
              throw new RuntimeException(s"Unable to complete operation: ${other}.")
          }
      }*/
    })
  /** Command parser. */
  lazy val parser = Command.CmdParser(descriptor.name ~> sp ~> (windowParser ^^ {
    result ⇒
    /*      Resources.factories().find { case (factory, available) ⇒ available && factory.name.name == result }.map(_._1).getOrElse {
        throw Command.ParseException(s"View with name '$result' not found.")
      }*/
  }) ~ opt(sp ~> (newArg, Command.Hint(newArg, Some("force to create new view")))))

  def windowParser: Command.parser.Parser[Any] =
    commandRegex(s"${App.symbolPatternDefinition()}+${App.symbolPatternDefinition("_")}*".r, WindowHintContainer)

  /** Hint container for window name. */
  object WindowHintContainer extends Command.Hint.Container {
    /** Get parser hints for user provided path. */
    def apply(arg: String): Seq[Command.Hint] = {
      val viewFactories = Resources.factories().filter(_._2 == true).keys.toSeq
      viewFactories.filter(_.name.name.startsWith(arg)).map(proposal ⇒
        Command.Hint("view name", Some(Messages.open_text + " " + proposal.shortDescription), Seq(proposal.name.name.drop(arg.length)))).
        filter(_.completions.head.nonEmpty)
    }
  }
}
