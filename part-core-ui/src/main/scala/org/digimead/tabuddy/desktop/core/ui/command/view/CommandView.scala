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

package org.digimead.tabuddy.desktop.core.ui.command.view

import java.util.UUID
import java.util.concurrent.{ CancellationException, Exchanger }
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.definition.Operation
import org.digimead.tabuddy.desktop.core.definition.command.Command
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.ui.{ Messages, Resources }
import org.digimead.tabuddy.desktop.core.ui.UI
import org.digimead.tabuddy.desktop.core.ui.block.{ Configuration, View }
import org.digimead.tabuddy.desktop.core.ui.definition.widget.AppWindow
import org.digimead.tabuddy.desktop.core.ui.operation.OperationViewCreate
import org.eclipse.core.runtime.jobs.Job
import scala.concurrent.Future
import scala.util.DynamicVariable

/**
 * Create new or select exists view by name.
 */
object CommandView extends Loggable {
  import Command.parser._
  /** Akka execution context. */
  implicit lazy val ec = App.system.dispatcher
  /** Akka communication timeout. */
  implicit val timeout = akka.util.Timeout(UI.communicationTimeout)
  /** Command description. */
  implicit lazy val descriptor = Command.Descriptor(UUID.randomUUID())(Messages.view_text,
    Messages.viewDescriptionShort_text, Messages.viewDescriptionLong_text,
    (activeContext, parserContext, parserResult) ⇒ Future {
      val (viewFactory, viewName) = parserResult match {
        case ~(factory: View.Factory, name @ Some(_: String)) ⇒ (factory, name)
        case ~(factory: View.Factory, None) ⇒ (factory, None)
      }
      val exchanger = new Exchanger[Operation.Result[UUID]]()
      val appWindow = Option(activeContext.get(classOf[AppWindow])) orElse UI.getActiveWindow() getOrElse {
        throw new RuntimeException(s"Unable to find active window for ${this}: '${activeContext}'.")
      }
      viewName match {
        case Some(name) ⇒
          // Open exists.
          val (contentActorName, _) = viewFactory.contexts.find {
            case (_, (_, context)) ⇒ context.get(UI.Id.viewTitle) == name
          } getOrElse {
            throw new RuntimeException(s"Unable to find view with name: ${name}.")
          }
          val (_, vComposite) = UI.viewMap.find {
            case (_, vComposite) ⇒ vComposite.contentRef.path.name == contentActorName
          } getOrElse {
            throw new RuntimeException(s"Unable to find view actor for name: ${name}.")
          }
          App.exec {
            if (!vComposite.isDisposed()) {
              vComposite.getShell().setMinimized(false)
              vComposite.getShell().setActive()
              vComposite.getShell().forceActive()
              vComposite.forceFocus()
            }
          }
          "Open exists " + vComposite
        case None ⇒
          // Create new.
          OperationViewCreate(appWindow.id, Configuration.CView(viewFactory.configuration)).foreach { operation ⇒
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
              result.flatMap(UI.viewMap.get)
            case Operation.Result.Cancel(message) ⇒
              throw new CancellationException(s"Operation canceled, reason: ${message}.")
            case err: Operation.Result.Error[_] ⇒
              throw err
            case other ⇒
              throw new RuntimeException(s"Unable to complete operation: ${other}.")
          }
      }
    })
  /** Command parser. */
  lazy val parser = Command.CmdParser(((descriptor.name ^^ { _ ⇒ localFactory.value = None }) ~>
    (sp ~> viewFactoryParser) ~ opt(sp ~> viewNameParser)) ^^ { result ⇒ localFactory.value = None; result })
  /** Thread local cache with selected factory. */
  protected val localFactory = new DynamicVariable[Option[View.Factory]](None)

  /** Parser with view factory. */
  def viewFactoryParser: Command.parser.Parser[Any] =
    commandRegex(s"${App.symbolPatternDefinition()}+${App.symbolPatternDefinition("_")}*".r, ViewFactoryHintContainer) ^^ {
      case name ⇒
        val (factory, _) = Resources.factories.find { case (factory, available) ⇒ available && factory.name.name == name } getOrElse {
          localFactory.value = None
          throw Command.ParseException(s"View factory with name '$name' not found.")
        }
        // save result for further parsers
        localFactory.value = Some(factory)
        factory
    }
  /** Parser with view name. */
  def viewNameParser: Command.parser.Parser[Any] =
    commandRegex(s"${App.symbolPatternDefinition()}+${App.symbolPatternDefinition("_()")}*".r, ViewNameHintContainer)

  /** Hint container for factory name. */
  object ViewFactoryHintContainer extends Command.Hint.Container {
    /** Get parser hints for user provided path. */
    def apply(arg: String): Seq[Command.Hint] = {
      val viewFactories = Resources.factories.filter(_._2 == true).keys.toSeq
      viewFactories.filter(_.name.name.startsWith(arg)).map(proposal ⇒
        Command.Hint(proposal.name.name, Some(Messages.create_text + " or " +
          Messages.open_text + " " + proposal.shortDescription), Seq(proposal.name.name.drop(arg.length)))).
        filter(_.completions.head.nonEmpty)
    }
  }
  /** Hint container for view name. */
  object ViewNameHintContainer extends Command.Hint.Container {
    /** Get parser hints for user provided path. */
    def apply(arg: String): Seq[Command.Hint] = localFactory.value match {
      case Some(factory) ⇒
        factory.contexts.flatMap {
          case (_, (_, context)) ⇒ Option(context.get(UI.Id.viewTitle).toString())
        }.map(proposal ⇒
          Command.Hint(proposal, Some(s"${Messages.open_text} view with name '${proposal}'"), Seq(proposal.drop(arg.length)))).
          filter(_.completions.head.nonEmpty).toSeq
      case None ⇒
        Seq()
    }
  }
}
