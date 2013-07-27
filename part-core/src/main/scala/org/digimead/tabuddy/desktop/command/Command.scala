/**
 * This file is part of the TABuddy project.
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

package org.digimead.tabuddy.desktop.command

import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

import scala.Array.canBuildFrom
import scala.collection.immutable
import scala.collection.mutable

import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.Core
import org.eclipse.e4.core.contexts.IEclipseContext
import org.eclipse.e4.core.contexts.RunAndTrack
import org.eclipse.e4.core.internal.contexts.EclipseContext
import org.eclipse.jface.fieldassist.ContentProposal
import org.eclipse.jface.fieldassist.IContentProposal
import org.eclipse.jface.fieldassist.IContentProposalProvider

import language.implicitConversions

/**
 * Command supervisor.
 * There is only one command parser of the specific type per context
 */
class Command extends Loggable {
  /**
   * A Composite from all actual command parser combinators over the application.
   * The is based on the current active context.
   */
  protected val actualParserCombinators = new AtomicReference[Command.parser.Parser[Any]]()
  /** Registry with registered commands. Command id -> command description. */
  protected val registered = new mutable.HashMap[UUID, Command.Description] with mutable.SynchronizedMap[UUID, Command.Description]
  /** Registry with information about all active parsers within application contexts. Unique id of actual parser -> actual information. */
  protected val perContext = new mutable.HashMap[UUID, Command.ActualInformation] with mutable.SynchronizedMap[UUID, Command.ActualInformation]
  /** Run and track active branch context listener. */
  protected val listener = new Command.Listener(actualParserCombinators)
  private val contextCommandsAccessLock = new Object

  // Listen for actual commands based on active branch.
  // We add this from the beginning till the end of the component life,
  // because of code monkeys that designs EclipseContext and forgot to add ability to remove RATs
  Core.context.runAndTrack(listener)
  log.debug("Alive. Add global active context listener.")

  /** Create new proposal provider for the text field. */
  def getProposalProvider(): Command.ProposalProvider = {
    log.debug("Get proposal provider.")
    new Command.ProposalProvider(actualParserCombinators)
  }
  /** Parse input. */
  def parse(parser: Command.parser.Parser[Any], input: String): Command.Result = {
    val (commandId, result) = Command.parser.successfullCommand.withValue(None) {
      val result = Command.parser.parse(parser, input)
      (Command.parser.successfullCommand.value, result)
    }
    result match {
      case r @ Command.parser.Success(result, next) =>
        commandId match {
          case Some(commandId) => Command.Success(commandId, result)
          case None => Command.Error("Unable to find command id for: " + r)
        }
      case Command.parser.MissingCompletionOrFailure(list, message, next) =>
        Command.MissingCompletionOrFailure(list, message)
      case Command.parser.Failure(message, next) =>
        Command.Failure(message)
      case Command.parser.Error(message, next) =>
        Command.Error(message)
    }
  }
  /**
   * Add command parser to context.
   * Create command parser unique copy and bind it to the context.
   * So lately we may retrieve result and additional information.
   */
  def addToContext(context: EclipseContext, commandParserTemplate: Command.CmdParser): Unit = contextCommandsAccessLock.synchronized {
    val commandId = commandParserTemplate.uniqueId
    log.debug(s"Add command ${commandId} to context ${context}.")
    if (!registered.contains(commandId))
      throw new IllegalArgumentException(s"Unable to add parser to context: command id ${commandId} not found")
    Option(context.get(Command.contextKey)) match {
      case Some(commandsGeneric: immutable.HashMap[_, _]) =>
        val uniqueCommandParser = commandParserTemplate.copy(uniqueId = UUID.randomUUID())
        perContext(uniqueCommandParser.uniqueId) = Command.ActualInformation(commandId, uniqueCommandParser, context)
        context.set(Command.contextKey, commandsGeneric.asInstanceOf[immutable.HashMap[UUID, Command.CmdParser]] + (commandId -> uniqueCommandParser))
      case Some(unknown) =>
        log.fatal("Unknown context commands keunknowny value: " + unknown.getClass())
      case None =>
        val uniqueCommandParser = commandParserTemplate.copy(uniqueId = UUID.randomUUID())
        perContext(uniqueCommandParser.uniqueId) = Command.ActualInformation(commandId, uniqueCommandParser, context)
        context.set(Command.contextKey, immutable.HashMap[UUID, Command.CmdParser](commandId -> uniqueCommandParser))
    }
    listener.changed(Core.context)
  }
  /** Register command. */
  def register(commandDescription: Command.Description): Unit = contextCommandsAccessLock.synchronized {
    log.debug(s"""Register command "${commandDescription.name}" with id${commandDescription.commandId}.""")
    registered += (commandDescription.commandId -> commandDescription)
  }
  /** Remove all actual parser that have specific command Id from the context. */
  def removeFromContext(context: EclipseContext, commandId: UUID) = contextCommandsAccessLock.synchronized {
    log.debug(s"Remove command ${commandId} from context ${context}.")
    Option(context.get(Command.contextKey)) match {
      case Some(commandsGeneric: immutable.HashMap[_, _]) =>
        context.set(Command.contextKey, commandsGeneric.asInstanceOf[immutable.HashMap[UUID, Command.CmdParser]] - commandId)
      case Some(unknown) =>
        log.fatal("Unknown context commands keunknowny value: " + unknown.getClass())
      case None =>
    }
    val uniqueIdToRemove = perContext.filter { case (uniqueId, information) => information.context == context && information.commandId == commandId }.map(_._1)
    uniqueIdToRemove.foreach(perContext.remove)
    listener.changed(Core.context)
  }
  /** Remove all actual parser that have specific command Id from the context. */
  def removeFromContext(context: EclipseContext, commandParserTemplate: Command.CmdParser) {
    val commandId = commandParserTemplate.uniqueId
    if (!registered.contains(commandId))
      throw new IllegalArgumentException(s"Unable to add parser to context: command id ${commandId} not found")
    removeFromContext(context, commandId)
  }
  /** Unregister command. */
  def unregister(commandId: UUID): Unit = contextCommandsAccessLock.synchronized {
    log.debug(s"Register command ${commandId}.")
    val uniqueIdToRemove = perContext.filter { case (uniqueId, information) => information.commandId == commandId }.map(_._1)
    uniqueIdToRemove.foreach { uniqueId =>
      perContext.remove(uniqueId).foreach { information =>
        Option(information.context.get(Command.contextKey)) match {
          case Some(commandsGeneric: immutable.HashMap[_, _]) =>
            information.context.set(Command.contextKey, commandsGeneric.asInstanceOf[immutable.HashMap[UUID, Command.CmdParser]] - commandId)
          case Some(unknown) =>
            log.fatal("Unknown context commands keunknowny value: " + unknown.getClass())
          case None =>
        }
      }
    }
    registered -= commandId
    listener.changed(Core.context)
  }
  /** Unregister command. */
  def unregister(commandDescription: Command.Description) {
    log.debug(s"Register command ${commandDescription.commandId}: ${commandDescription.name}.")
    val commandId = commandDescription.commandId
    if (!registered.contains(commandId))
      throw new IllegalArgumentException(s"Unable to add parser to context: command id ${commandId} not found")
    unregister(commandId)
  }
}

/**
 * Monitor all actual commands add provide them with IContentProposalProvider
 */
object Command extends Loggable {
  implicit def cmdLine2implementation(c: Command.type): Command = c.inner
  /** Context command line key. */
  val contextKey = "Commands"
  /** Command parser implementation. */
  lazy val parser = DI.parser

  /** Command implementation. */
  def inner = DI.implementation

  sealed trait Result
  case class Success(commandId: UUID, result: Any) extends Result
  case class MissingCompletionOrFailure(completion: List[(String, UUID)], message: String) extends Result
  case class Failure(message: String) extends Result
  case class Error(message: String) extends Result
  /** Information about command parser that is  added to specific context. */
  case class ActualInformation private[Command] (commandId: UUID, parser: Command.parser.Parser[Any], context: EclipseContext)
  /** Command description. */
  case class Description(val commandId: UUID)(val name: String, val description: String, callback: (Any) => Unit)
  /** Application wide context listener that rebuild commands. */
  class Listener(val commandParserCombinators: AtomicReference[parser.Parser[Any]]) extends RunAndTrack() {
    override def changed(context: IEclipseContext): Boolean = {
      log.trace("Update command line parser combinators.")
      val leaf = Core.context.getActiveLeaf()
      def getCommands(context: IEclipseContext): Seq[Null] = {
        null
      }
      getCommands(leaf)
      true
    }
  }
  /** Command parser that wraps base parser combinator with 'phrase' sentence. */
  class CmdParser(val uniqueId: UUID, base: parser.Parser[Any])
    extends parser.CmdParser(uniqueId, base) {
    /** Copy constructor. */
    def copy(uniqueId: UUID = this.uniqueId, base: parser.Parser[Any] = this.base) =
      new CmdParser(uniqueId, base)
    /** Equals by uniqueId. */
    override def equals(other: Any) = other match {
      case that: CmdParser => (this eq that) || uniqueId == that.uniqueId
      case _ => false
    }
    /** HashCode from uniqueId. */
    override def hashCode = uniqueId.hashCode()
  }
  object CmdParser {
    def apply(base: parser.Parser[Any])(implicit description: Description) =
      new CmdParser(description.commandId, base)
  }
  /** ProposalProvider for a text field. */
  class ProposalProvider(val actualParserCombinators: AtomicReference[parser.Parser[Any]])
    extends IContentProposalProvider {
    @volatile protected var input = ""

    /** Set input for current proposal. */
    def setInput(text: String) = input = text
    /** Return an array of content proposals representing the valid proposals for a field. */
    def getProposals(contents: String, position: Int): Array[IContentProposal] = {
      val a = Array("autocomplete option 1+", "autocomplete option 2+")
      for (i <- a) yield new ContentProposal(i, i + "desc")
    }
  }
  /**
   * Dependency injection routines.
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** Command implementation. */
    lazy val implementation = injectOptional[Command] getOrElse new Command
    /** Parser implementation. */
    lazy val parser = injectOptional[Parser] getOrElse new Parser
  }
}
