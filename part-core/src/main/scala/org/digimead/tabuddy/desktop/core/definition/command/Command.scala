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

package org.digimead.tabuddy.desktop.core.definition.command

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.XDependencyInjection
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.Core
import org.digimead.tabuddy.desktop.core.definition.Context
import org.digimead.tabuddy.desktop.core.definition.command.api.XCommand
import org.eclipse.e4.core.contexts.{ ContextFunction, IEclipseContext, RunAndTrack }
import org.eclipse.jface.fieldassist.{ ContentProposal, IContentProposal, IContentProposalProvider }
import scala.collection.JavaConverters.mapAsScalaMapConverter
import scala.collection.immutable
import scala.concurrent.Future
import scala.language.implicitConversions
import scala.util.DynamicVariable
import scala.util.parsing.input.CharSequenceReader

/**
 * Command supervisor.
 */
class Command extends XLoggable {
  /**
   * The composite parser from all actual parser combinators over the application.
   * It is based on the current active context branch.
   */
  protected val actualParserCombinators = new AtomicReference[Command.parser.Parser[Any]](Command.parser.stubParser)
  /** Registry with registered commands. Parser id -> command descriptor. */
  protected val registry = new ConcurrentHashMap[UUID, Command.Descriptor].asScala
  /** Registry with information about all active parsers within application contexts. Unique id of parser -> context information. */
  protected val perContext = new ConcurrentHashMap[UUID, Command.ContextInformation].asScala
  /** Run and track active branch context listener. */
  protected lazy val listener = new Command.Listener(actualParserCombinators)
  private val contextCommandsAccessLock = new Object

  // Listen for actual commands based on active branch.
  // We add this from the beginning till the end of the component life,
  // because of code monkeys that designs EclipseContext and forgot to add ability to remove RATs
  Core.context.runAndTrack(listener)
  log.debug("Alive. Add global active context listener.")

  /**
   * Add command parser to context.
   * Create command parser unique copy and bind it to the context.
   * So lately we may retrieve result and additional information.
   * @return unique id of the commandParserTemplate copy
   */
  @log
  def addToContext(context: Context, parser: Command.CmdParser): Option[UUID] = contextCommandsAccessLock.synchronized {
    val commandDescriptor = registry.get(parser.parserId) match {
      case Some(commandDescriptor) ⇒ commandDescriptor
      case None ⇒ throw new IllegalArgumentException(s"Unable to add parser to context: parser id ${parser.parserId} not found.")
    }
    log.debug(s"""Add command "${commandDescriptor.name}"(${commandDescriptor.parserId}) to context '${context}'.""")
    if (commandDescriptor.name.trim().isEmpty())
      throw new IllegalArgumentException(s"Unable to add parser to context: command name is absent.")
    val newParserUniqueId = Option(context.get(Command.contextKey)) match {
      case Some(commandsGeneric: immutable.HashMap[_, _]) ⇒
        // there is already registered at least one context parser
        val contextParser = parser.copy(uniqueId = UUID.randomUUID()).named(s""""${commandDescriptor.name}"(${commandDescriptor.parserId})""")
        perContext(contextParser.parserId) = Command.ContextInformation(parser.parserId, contextParser, context)
        context.set(Command.contextKey, commandsGeneric.asInstanceOf[immutable.HashMap[UUID, Command.CmdParser]] + (contextParser.parserId -> contextParser))
        Some(contextParser.parserId)
      case Some(unknown) ⇒
        log.fatal("Unknown context commands value: " + unknown.getClass())
        None
      case None ⇒
        // there are no any registered parsers
        val contextParser = parser.copy(uniqueId = UUID.randomUUID()).named(s""""${commandDescriptor.name}"(${commandDescriptor.parserId})""")
        perContext(contextParser.parserId) = Command.ContextInformation(parser.parserId, contextParser, context)
        context.set(Command.contextKey, immutable.HashMap[UUID, Command.CmdParser](contextParser.parserId -> contextParser))
        Some(contextParser.parserId)
    }
    newParserUniqueId
  }
  /** Get command descriptor for UUID. */
  def apply(key: UUID) = registry.apply(key: UUID)
  /** List all commands that is binded to context(s). */
  def binded = perContext.values
  /** Get command descriptor for UUID. */
  def get(key: UUID): Option[Command.Descriptor] = registry.get(key)
  /** Create new proposal provider for the text field. */
  def getProposalProvider(): Command.ProposalProvider = {
    log.debug("Get proposal provider.")
    new Command.ProposalProvider(actualParserCombinators)
  }
  /** Get descriptor for commandId. */
  def getDescriptor(commandId: UUID) = registry.get(commandId)
  /** Get information for uniqueId of a context parser. */
  def getContextParserInfo(uniqueId: UUID) = perContext.get(uniqueId)
  /** List all registered commands. */
  def registered = registry.values
  /** Parse input. */
  def parse(input: String, parser: Command.parser.Parser[Any] = actualParserCombinators.get): Command.Result = {
    val (parserId, proposals, result) = Command.triggeredCmdParserId.withValue(None) {
      Command.completionProposal.withValue(Seq.empty) {
        try {
          val result = Command.parser.parse(parser, input)
          (Command.triggeredCmdParserId.value, Command.completionProposal.value, result)
        } catch {
          case e: Command.ParseException ⇒
            (Command.triggeredCmdParserId.value, Seq(), Command.parser.Error(e.getMessage(), new CharSequenceReader(input)))
        }
      }
    }
    result match {
      case r @ Command.parser.Success(result, next) ⇒
        parserId match {
          case Some(parserId) ⇒ Command.Success(parserId, result)
          case None ⇒ Command.Error("Unable to find parser id for: " + r)
        }
      case Command.parser.MissingCompletionOrFailure(list, message, next) ⇒
        if (proposals.nonEmpty)
          // returns append proposals if any
          proposals.foldLeft(Command.MissingCompletionOrFailure(Seq(), "empty append proposal"))((a, b) ⇒
            Command.MissingCompletionOrFailure(a.completion ++ b.completions, "append proposals"))
        else
          Command.MissingCompletionOrFailure(list, message)
      case Command.parser.Failure(message, next) ⇒
        if (proposals.nonEmpty)
          // returns append proposals if any
          proposals.foldLeft(Command.MissingCompletionOrFailure(Seq(), "empty append proposal"))((a, b) ⇒
            Command.MissingCompletionOrFailure(a.completion ++ b.completions, "append proposals"))
        else
          Command.Failure(message)
      case Command.parser.Error(message, next) ⇒
        Command.Error(message)
    }
  }
  /** Register command. */
  def register(commandDescriptor: Command.Descriptor): Unit = contextCommandsAccessLock.synchronized {
    log.debug(s"""Register command "${commandDescriptor.name}" with id${commandDescriptor.parserId}.""")
    registry += (commandDescriptor.parserId -> commandDescriptor)
  }
  /** Remove all actual parser that have specific unique Id from the context. */
  def removeFromContext(context: Context, uniqueId: UUID) = contextCommandsAccessLock.synchronized {
    log.debug(s"Remove parser ${uniqueId} from context ${context}.")
    Option(context.get(Command.contextKey)) match {
      case Some(commandsGeneric: immutable.HashMap[_, _]) ⇒
        context.set(Command.contextKey, commandsGeneric.asInstanceOf[immutable.HashMap[UUID, Command.CmdParser]] - uniqueId)
      case Some(unknown) ⇒
        log.fatal("Unknown context commands keunknowny value: " + unknown.getClass())
      case None ⇒
    }
    perContext.remove(uniqueId)
    listener.changed(Core.context)
  }
  /** Remove all actual parser that have specific command Id from the context. */
  def removeFromContext(context: Context, parser: Command.CmdParser) {
    if (!registry.contains(parser.parserId))
      throw new IllegalArgumentException(s"Unable to add parser to context: command id ${parser.parserId} not found")
    perContext.filter { case (uniqueId, information) ⇒ information.parserId == parser.parserId }.foreach(kv ⇒ removeFromContext(context, kv._1))
  }
  /** Unregister command. */
  def unregister(parserId: UUID): Unit = contextCommandsAccessLock.synchronized {
    log.debug(s"Unregister command ${parserId}.")
    val uniqueIdToRemove = perContext.filter { case (uniqueId, information) ⇒ information.parserId == parserId }.map(_._1)
    uniqueIdToRemove.foreach { uniqueId ⇒
      perContext.remove(uniqueId).foreach { information ⇒
        Option(information.context.get(Command.contextKey)) match {
          case Some(commandsGeneric: immutable.HashMap[_, _]) ⇒
            information.context.set(Command.contextKey, commandsGeneric.asInstanceOf[immutable.HashMap[UUID, Command.CmdParser]] - parserId)
          case Some(unknown) ⇒
            log.fatal("Unknown context commands keunknowny value: " + unknown.getClass())
          case None ⇒
        }
      }
    }
    registry -= parserId
    listener.changed(Core.context)
  }
  /** Unregister command. */
  def unregister(commandDescriptor: Command.Descriptor) {
    log.debug(s"Unregister command ${commandDescriptor.parserId}: ${commandDescriptor.name}.")
    val commandId = commandDescriptor.parserId
    if (!registry.contains(commandId))
      throw new IllegalArgumentException(s"Unable to add parser to context: command id ${commandId} not found")
    unregister(commandId)
  }
}

/**
 * Monitor all actual commands add provide them with IContentProposalProvider
 */
object Command extends XLoggable {
  implicit def cmdLine2implementation(c: Command.type): Command = c.inner
  /** Last parsing process completion. */
  val completionProposal = new DynamicVariable(Seq.empty[CommandParsers#MissingCompletionOrFailure])
  /** Context commands map key. */
  val contextKey = "Commands"
  /** Context commands composite parser key. */
  val contextParserKey = "CommandsParser"
  /** Singleton identificator. */
  val id = getClass.getSimpleName().dropRight(1)
  /** Command parser implementation. */
  lazy val parser = DI.parser
  /** Last successful parser id. */
  val triggeredCmdParserId = new DynamicVariable[Option[UUID]](None)

  /** Command implementation. */
  def inner = DI.implementation

  sealed trait Result
  case class Success(val uniqueId: UUID, val result: Any) extends Result
  case class MissingCompletionOrFailure(val completion: Seq[Hint], val message: String) extends Result
  case class Failure(val message: String) extends Result
  case class Error(val message: String) extends Result
  /** Information about command parser that is added to specific context. */
  case class ContextInformation private[Command] (val parserId: UUID, val contextParser: Command.parser.Parser[Any], val context: Context)
  /** Command descriptor where callback is (active context, parser context, parser result) => Unit */
  case class Descriptor(val parserId: UUID)(val name: String, val shortDescription: String, val longDescription: String, val callback: (Context, Context, Any) ⇒ Future[Any])
    extends XCommand.Descriptor {
    override lazy val toString = s"Command.Descriptor(${name}, ${parserId})"
  }
  /** Command parser that wraps base parser combinator with 'phrase' sentence. */
  class CmdParser(val parserId: UUID, base: parser.Parser[Any])
    extends parser.CmdParser(parserId, base) {
    /** Copy constructor. */
    def copy(uniqueId: UUID = this.parserId, base: parser.Parser[Any] = this.base) =
      new CmdParser(uniqueId, base)
    /** Equals by uniqueId. */
    override def equals(other: Any) = other match {
      case that: CmdParser ⇒ (this eq that) || parserId == that.parserId
      case _ ⇒ false
    }
    /** HashCode from parserId. */
    override def hashCode = parserId.hashCode()
  }
  object CmdParser {
    def apply(base: parser.Parser[Any])(implicit descriptor: Descriptor) =
      new CmdParser(descriptor.parserId, base)
  }
  /** Application wide context listener that rebuild commands. */
  class Listener(val commandParserCombinators: AtomicReference[parser.Parser[Any]]) extends RunAndTrack() {
    private val lock = new Object
    override def changed(context: IEclipseContext): Boolean = lock.synchronized {
      log.trace("Update command line parser combinators.")
      val leaf = Core.context.getActiveLeaf()
      def getCompositeParsers(context: IEclipseContext): Seq[Option[parser.Parser[Any]]] = {
        val contextCompositeParser = Option(context.getLocal(Command.contextParserKey).asInstanceOf[Option[parser.Parser[Any]]]).getOrElse {
          context.set(Command.contextParserKey, new CompositeParserComputation)
          context.getLocal(Command.contextParserKey).asInstanceOf[Option[parser.Parser[Any]]]
        }
        Option(context.getParent()) match {
          case Some(parent) ⇒ contextCompositeParser +: getCompositeParsers(parent)
          case None ⇒ Seq(contextCompositeParser)
        }
      }
      getCompositeParsers(leaf).flatten match {
        case Nil ⇒ commandParserCombinators.set(Command.parser.stubParser)
        case seq if seq.nonEmpty ⇒ commandParserCombinators.set(seq.reduceLeft[parser.Parser[Any]] { (acc, p) ⇒ acc | p })
      }
      true
    }
  }
  /** Parser exception that correctly terminate parse sequence. */
  case class ParseException(message: String) extends java.text.ParseException(message, -1)
  /** ProposalProvider for a text field. */
  class ProposalProvider(val actualParserCombinators: AtomicReference[parser.Parser[Any]])
    extends IContentProposalProvider {
    @volatile protected var input = ""

    /** Set input for current proposal. */
    def setInput(text: String) = input = text
    /** Return an array of content proposals representing the valid proposals for a field. */
    def getProposals(contents: String, position: Int): Array[IContentProposal] = {
      Command.parse(input, actualParserCombinators.get) match {
        case Command.Success(uniqueId, result) ⇒
          Array()
        case Command.MissingCompletionOrFailure(hints, message) ⇒
          {
            hints.map {
              case Hint(Some(label), description, list) ⇒
                val completionList = list.filter(_.nonEmpty)
                if (completionList.size == 1)
                  completionList.map(completion ⇒ new ContentProposal(completion, label, description getOrElse null))
                else
                  completionList.map(completion ⇒ new ContentProposal(completion, s"${label}(${completion})", description getOrElse null))
              case Hint(None, description, list) ⇒
                list.filter(_.nonEmpty).map(completion ⇒ new ContentProposal(completion, completion, description getOrElse null))
            }
          }.flatten.toArray
        case Command.Failure(message) ⇒
          log.fatal(message)
          Array()
        case Command.Error(message) ⇒
          log.fatal(message)
          Array()
      }
    }
  }
  /** Completion hint. */
  abstract class Hint {
    /** Completion label. */
    def label: Option[String]
    /** Completion description. */
    def description: Option[String]
    /** Get copy of this hint with updated completions field. */
    def copyWithCompletion(completions: String*): this.type
    /** Completion list. */
    def completions: Seq[String]

    def canEqual(other: Any) = other.isInstanceOf[Hint]
    override def equals(other: Any) = other match {
      case that: Hint ⇒ (this eq that) || {
        that.canEqual(this) && label == that.label && description == that.description && completions == that.completions
      }
      case _ ⇒ false
    }
    override def hashCode() = lazyHashCode
    protected lazy val lazyHashCode = java.util.Arrays.hashCode(Array[AnyRef](label, description, completions))

    override def toString = "Command.Hint(" + s"$label, $description, $completions)"
  }
  object Hint {
    /** Get static Hint instance. */
    def apply(completionLabel: String, completionDescription: Option[String] = None, explicitCompletion: Seq[String] = Seq.empty): Hint =
      new Static(Some(completionLabel), completionDescription, explicitCompletion)
    /** Get static Hint instance. */
    def apply(explicitCompletion: String*): Hint =
      new Static(None, None, explicitCompletion)
    /** Hint extractor implementation. */
    def unapply(hint: Hint): Option[(Option[String], Option[String], Seq[String])] =
      Some(hint.label, hint.description, hint.completions)

    /** Simple Hint implementation with static fields. */
    class Static(
      /** Completion label. */
      val label: Option[String],
      /** Completion description. */
      val description: Option[String],
      /** Completion list. */
      val completions: Seq[String]) extends Hint {
      /** Get copy of this hint with updated completions field. */
      def copyWithCompletion(completions: String*): this.type =
        new Static(label, description, completions).asInstanceOf[this.type]
    }
    /** Hint container returns list of hints to parser with regards of argument. */
    trait Container {
      def apply(arg: String): Seq[Hint]
    }
    object Container {
      /** Get simple Hints container. */
      def apply(hints: Hint*): Container = new Simple(hints)
      /** Get simple Hints container. */
      def apply(hints: Traversable[Hint]): Container = new Simple(hints.toSeq)

      /** Simple Hints container that returns predefined sequence, regardless of argument. */
      class Simple(val hints: Seq[Hint]) extends Container {
        def apply(arg: String) = hints
      }
    }
  }
  /** Computation that calculates composite parser for current context. */
  class CompositeParserComputation extends ContextFunction {
    override def compute(context: IEclipseContext, fnKey: String): Option[parser.Parser[Any]] =
      Option(context.getLocal(Command.contextKey)) match {
        case Some(commands: immutable.HashMap[_, _]) if commands.nonEmpty ⇒
          Some(commands.values.asInstanceOf[Iterable[CmdParser]].reduceLeft[parser.Parser[Any]] { (acc, p) ⇒ acc | p })
        case _ ⇒
          None
      }
  }
  /**
   * Dependency injection routines.
   */
  private object DI extends XDependencyInjection.PersistentInjectable {
    /** Command implementation. */
    lazy val implementation = injectOptional[Command] getOrElse new Command
    /** Parser implementation. */
    lazy val parser = injectOptional[CommandParsers] getOrElse new CommandParsers
  }
}
