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

package org.digimead.tabuddy.desktop.core.console

import akka.actor.{ Actor, ActorRef, Props, ScalaActorRef, actorRef2Scala }
import java.util.Properties
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.Core
import org.digimead.tabuddy.desktop.core.definition.command.Command
import org.digimead.tabuddy.desktop.core.definition.command.{ api ⇒ cmdapi }
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.{ Messages, Report }
import scala.language.implicitConversions
import scala.util.Failure
import scala.util.Properties.{ javaVersion, javaVmName, versionString }
import scala.util.Success

/**
 * Console actor of the application.
 */
class Console extends Actor with Loggable {
  /** Akka execution context. */
  implicit lazy val ec = App.system.dispatcher
  log.debug("Start actor " + self.path)

  /** Is called asynchronously after 'actor.stop()' is invoked. */
  override def postStop() = {
    log.debug(s"Stop application consoles.")
    Console.list.foreach(_.stop)
    log.debug(self.path.name + " actor is stopped.")
  }
  /** Is called when an Actor is started. */
  override def preStart() {
    log.debug(s"Start application consoles.")
    Console.list.foreach(_.start)
    log.debug(self.path.name + " actor is started.")
    Core ! App.Message.Consistent(Console, self)
  }
  def receive = {
    case message @ App.Message.Start(Left(Console), _) ⇒ App.traceMessage(message) {
      Console.list.foreach(_.enablePrompt())
    }

    case message @ Console.Message.Command(string, from) ⇒ App.traceMessage(message) {
      command(string.trim, from, sender)
    }

    case Console.Message.Notice(string) ⇒
      echo(Console.DI.msgNotice.format(string))

    case Console.Message.Info(string) ⇒
      echo(Console.DI.msgInfo.format(string))

    case Console.Message.Important(string) ⇒
      echo(Console.DI.msgImportant.format(string))

    case Console.Message.Warning(string) ⇒
      echo(Console.DI.msgWarning.format(string))

    case Console.Message.Alert(string) ⇒
      echo(Console.DI.msgAlert.format(string))
  }

  /** Run one command submitted by the user. */
  protected def command(line: String, from: Option[api.Console.Projection], sender: ActorRef) = Command.parse(line) match {
    case Command.Success(contextParserId, result) ⇒
      Command.getContextParserInfo(contextParserId) match {
        case Some(info) ⇒
          Command.getDescriptor(info.parserId) match {
            case Some(commandDescriptor) ⇒
              val activeContext = Core.context.getActiveLeaf()
              Console.log.info(s"Execute command '${commandDescriptor.name}' within context '${info.context}' with argument: " + result)
              implicit val ec = App.system.dispatcher
              commandDescriptor.callback(activeContext, info.context, result) onComplete {
                case result @ Success(r) ⇒
                  if (sender != App.system.deadLetters)
                    sender ! result
                  commandOnSuccess(commandDescriptor, r, from)
                case result @ Failure(e) ⇒
                  if (sender != App.system.deadLetters)
                    sender ! result
                  commandOnFailure(commandDescriptor, e, line, from)
              }
            case None ⇒
              if (sender != App.system.deadLetters)
                sender ! Failure(new RuntimeException("Unable to find command description for " + info))
              Console.log.fatal("Unable to find command description for " + info)
          }
        case None ⇒
          if (sender != App.system.deadLetters)
            sender ! Failure(new RuntimeException("Unable to find command information for unique Id " + contextParserId))
          Console.log.fatal("Unable to find command information for unique Id " + contextParserId)
      }
    case Command.MissingCompletionOrFailure(appender, completionList, message) ⇒
      if (sender != App.system.deadLetters)
        sender ! Failure(new RuntimeException("Autocomplete: " + message))
      Console.log.debug("Autocomplete: " + message)
    case Command.Failure(message) ⇒
      if (sender != App.system.deadLetters)
        sender ! Failure(new RuntimeException(message))
      Console.log.debug(message)
    case Command.Error(message) ⇒
      if (sender != App.system.deadLetters)
        sender ! Failure(new RuntimeException(message))
      Console.log.fatal(message)
  }
  /** Command future is failed. */
  protected def commandOnFailure(commandDescriptor: Command.Descriptor, error: Throwable, line: String, from: Option[api.Console.Projection]) {
    from.foreach(_.echo(Console.DI.msgAlert.format(s"\n${commandDescriptor.name} is failed: " + error) + Console.RESET))
  }
  /** Command future is successful completed. */
  protected def commandOnSuccess(commandDescriptor: Command.Descriptor, result: Any, from: Option[api.Console.Projection]) {
    val message = Console.convert(commandDescriptor, result) match {
      case "" ⇒ s"""Command "${commandDescriptor.name}" is completed."""
      case result ⇒ s"""Command "${commandDescriptor.name}" is completed:\n""" + Console.RESET + result
    }
    Console ! Console.Message.Notice(message)
  }
  /** Echo message. */
  protected def echo(msg: String) = Console.list.foreach(_.echo(msg + Console.RESET))
}

object Console extends api.Console with Loggable {
  implicit def console2actorRef(c: Console.type): ActorRef = c.actor
  implicit def console2actorSRef(c: Console.type): ScalaActorRef = c.actor
  /** Console actor reference. */
  lazy val actor = App.getActorRef(App.system.actorSelection(path)) getOrElse {
    throw new IllegalStateException("Unable to locate actor with path " + path)
  }
  /** Default hint to text converter. */
  lazy val defaultHintToText = (hintLabel: String, hintDescription: Option[String], proposal: String) ⇒ (proposal, hintDescription) match {
    case (_, Some(description)) if hintLabel == proposal ⇒ s"${CYAN}${hintLabel}${RESET} - ${description}"
    case (_, Some(description)) ⇒ s"${hintLabel}(${CYAN}${proposal}${RESET}) - ${description}"
    case (_, None) if hintLabel == proposal ⇒ s"${CYAN}${hintLabel}${RESET} - description is absent"
    case (_, None) ⇒ s"${hintLabel}(${CYAN}${proposal}${RESET}) - description is absent"
    case ("", _) ⇒ s"""proposal is empty for "${hintLabel}""""
  }
  /** Singleton identificator. */
  val id = getClass.getSimpleName().dropRight(1)
  /** Default command results converter. */
  val defaultCommandResultsConverter: PartialFunction[(cmdapi.Command.Descriptor, Any), String] = {
    case (_, ()) ⇒ ""
    case (_, result: AnyRef) ⇒ result.toString()
    case (_, result) ⇒ String.valueOf(result)
  }
  /** Console actor path. */
  lazy val path = Core.path / id

  /** Console converter. */
  def convert = DI.converter
  /** Convert hint to text. */
  def hintToText(hintLabel: String, hintDescription: Option[String], proposal: String) = DI.defaultHintToText(hintLabel, hintDescription, proposal)
  /** History file name. */
  def historyFileName = DI.historyFileName
  /** List with console projections. */
  def list = DI.consoleList
  /** Prompt to print when awaiting input. */
  def prompt = DI.prompt
  /** Console actor reference configuration object. */
  def props = DI.props
  /** Print a welcome message */
  def welcomeMessage(): String = {
    import Properties._
    val info = Report.info
    val arch = info.map(_.arch).getOrElse("UNKNOWN")
    val core = info.flatMap(_.component.find(_.name == Core.name))
    val coreVersion = core.map(_.version).getOrElse("0")
    val coreBuild = core.map(r ⇒ Report.dateString(r.build)).getOrElse("UNKNOWN")
    val coreRawBuild = core.map(_.rawBuild).getOrElse("0")
    val platform = info.map(_.platform).getOrElse("UNKNOWN")
    val os = info.map(_.os).getOrElse("UNKNOWN")
    val welcomeMsg =
      s"""|${Console.BBLACK}Welcome to TA Buddy: Desktop v${Console.WHITE}${coreVersion}${Console.BBLACK} (build ${Console.WHITE}${coreBuild}${Console.BBLACK}; ${Console.WHITE}${coreRawBuild}${Console.BBLACK}).
        |Scala ${Console.WHITE}${versionString}${Console.BBLACK}, running on ${javaVmName}, Java ${javaVersion}.
        |Platform ${Console.WHITE}${platform}${Console.BBLACK}, ${os} ${Console.WHITE}${arch}${Console.BBLACK}
        |Type in commands to have them evaluated.
        |Type ${Console.WHITE}${Messages.help_text}${Console.BBLACK} for more information.""".stripMargin
    welcomeMsg
  }

  override def toString = "Console[Singleton]"

  /**
   * Dependency injection routines
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** List of application consoles. */
    lazy val consoleList: Seq[api.Console.Projection] = {
      bindingModule.bindings.filter {
        case (key, value) ⇒ classOf[api.Console.Projection].isAssignableFrom(key.m.runtimeClass)
      }.map {
        case (key, value) ⇒
          key.name match {
            case Some(name) if name.startsWith("Console.") ⇒
              log.debug(s"'${name}' loaded.")
            case _ ⇒
              log.debug(s"'${key.name.getOrElse("Unnamed")}' console skipped.")
          }
          bindingModule.injectOptional(key).asInstanceOf[Option[api.Console.Projection]]
      }.flatten.toSeq
    }
    /** Converter that transforms command result to text. */
    lazy val converter: PartialFunction[(cmdapi.Command.Descriptor, Any), String] = {
      val converters = bindingModule.bindings.filter {
        case (key, value) ⇒ classOf[PartialFunction[(cmdapi.Command.Descriptor, Any), String]].isAssignableFrom(key.m.runtimeClass)
      }.map {
        case (key, value) ⇒
          key.name match {
            case Some(name) if name.startsWith("Console.Converter.") ⇒
              log.debug(s"'${name}' loaded.")
            case _ ⇒
              log.debug(s"'${key.name.getOrElse("Unnamed")}' console skipped.")
          }
          bindingModule.injectOptional(key).asInstanceOf[Option[PartialFunction[(cmdapi.Command.Descriptor, Any), String]]]
      }.flatten
      (converters ++ Iterator(defaultConverter)).reduceLeft(_ orElse _)
    }
    /** Default converter that transforms command result to text. */
    lazy val defaultConverter = injectOptional[PartialFunction[(cmdapi.Command.Descriptor, Any), String]]("Console.DefaultConverter") getOrElse Console.defaultCommandResultsConverter
    /** Hint to text converter. */
    lazy val defaultHintToText = injectOptional[(String, Option[String], String) ⇒ String] getOrElse Console.defaultHintToText
    /** History file name. */
    lazy val historyFileName = injectOptional[String]("Console.JLine.History") getOrElse "jline_history"
    /** Console command message. */
    lazy val msgCommand = injectOptional[String]("Console.Message.Command") getOrElse Console.BBLACK + "%s"
    /** Console notice message. */
    lazy val msgNotice = injectOptional[String]("Console.Message.Notice") getOrElse Console.BBLACK + "%s"
    /** Console info message. */
    lazy val msgInfo = injectOptional[String]("Console.Message.Info") getOrElse Console.WHITE + "%s"
    /** Console important message. */
    lazy val msgImportant = injectOptional[String]("Console.Message.Important") getOrElse Console.BWHITE + "%s"
    /** Console warning message. */
    lazy val msgWarning = injectOptional[String]("Console.Message.Warning") getOrElse Console.BYELLOW + "%s"
    /** Console alert message. */
    lazy val msgAlert = injectOptional[String]("Console.Message.Alert") getOrElse {
      Console.RED + "*ALERT*" + Console.WHITE + "%s" + Console.RED + "*ALERT*" + Console.WHITE
    }
    /** Console prompt. */
    lazy val prompt = injectOptional[String]("Console.Prompt") getOrElse "ta-desktop>"
    /** Console actor reference configuration object. */
    lazy val props = injectOptional[Props]("Core.Console") getOrElse Props[Console]
  }
}