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

package org.digimead.tabuddy.desktop.core.console.local

import java.io.{ IOException, PrintWriter }
import jline.console.ConsoleReader
import jline.console.completer.CandidateListCompletionHandler
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.console.{ Console, Projection, Reader ⇒ CReader, Writer ⇒ CWriter }
import org.digimead.tabuddy.desktop.core.definition.command.Command
import org.digimead.tabuddy.desktop.core.support.App
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.ref.WeakReference
import scala.tools.nsc.interpreter.{ Completion, ConsoleReaderHelper, JLineReader, NoCompletion }
import scala.tools.nsc.interpreter.session.JLineHistory.JLineFileHistory
import scala.tools.nsc.interpreter.session.SimpleHistory

class Local extends Projection with XLoggable {
  /** Thread with local console loop. */
  @volatile protected var thread = Option.empty[Thread]

  Local.synchronized {
    if (Local.instance.nonEmpty)
      throw new IllegalStateException("There can be only one local console per application.")
    Local.instance = Some(this)
  }

  def echo(msg: String) = out.foreach { out ⇒ out.echoAndRefresh(msg) }
  def echoNoNL(msg: String) = out.foreach { out ⇒ out.echoNoNL(msg) }
  def echoColumns(items: Seq[String]) = out.foreach { out ⇒ out.printColumns(items) }
  def start() {
    log.debug(s"Start local console.")
    in = Option(new Local.Reader(WeakReference(this)))
    out = Option(new Local.Writer(in.get.asInstanceOf[Local.Reader].consoleReader))
    val thread = new Thread(new Runnable {
      def run = try loop catch {
        case e: IOException ⇒
        case e: InterruptedException ⇒
      }
    })
    thread.setDaemon(true)
    thread.start()
    this.thread = Option(thread)
  }
  def stop() {
    log.debug(s"Stop local console.")
    val prevIn = in
    val prevOut = out
    in = None
    out = None
    thread.foreach(_.interrupt())
    prevIn.foreach { in ⇒
      val reader = in.asInstanceOf[Local.Reader].consoleReader
      reader.getOutput().flush()
    }
    thread = None
  }
}

object Local extends XLoggable {
  @volatile private var instance: Option[Local] = None

  class Reader(projection: WeakReference[Projection]) extends JLineReader(new Local.JLineCompletion(projection)) with CReader {
    override val consoleReader = new LocalConsoleReader()
    override lazy val history: scala.tools.nsc.interpreter.session.JLineHistory =
      try new JLineHistory catch { case _: Exception ⇒ new SimpleHistory() }
    consoleReader.postInit
    class LocalConsoleReader extends JLineConsoleReader {
      // A hook for running code after the repl is done initializing.
      override lazy val postInit: Unit = {
        this setBellEnabled false

        if (completion ne NoCompletion) {
          this addCompleter scalaToJline(completion.completer())
          this setAutoprintThreshold 400 // max completion candidates without warning
          setCompletionHandler(new LocalCandidateListCompletionHandler)
        }
      }
      override def back(num: Int) = super.back(num)
      override def flush() {
        instance.foreach(console ⇒ if (console.in.isEmpty) throw new IOException("Reader is closed."))
        super.flush()
      }
    }
  }
  /**
   * CandidateListCompletionHandler
   */
  class LocalCandidateListCompletionHandler extends CandidateListCompletionHandler {
    // fail if the only candidate is the same as the current buffer
    override def complete(reader: ConsoleReader, candidates: java.util.List[CharSequence], pos: Int): Boolean =
      if (candidates.size() == 1) candidates.get(0).equals(reader.getCursorBuffer().toString()) else true
  }
  class Writer(local: ConsoleReader with ConsoleReaderHelper) extends PrintWriter(local.getOutput(), true) with CWriter {
    def echoAndRefresh(msg: String) {
      local.println("\r\n" + msg)
      local.redrawLine()
      local.flush()
    }
    def echo(msg: String) {
      local.println(msg)
      local.flush()
    }
    def echoNoNL(msg: String) {
      local.print(msg)
      local.flush()
    }
    def printColumns(items: Seq[String]) {
      local.printColumns(items.asJava)
      local.flush()
    }
  }
  class JLineHistory extends JLineFileHistory {
    override protected lazy val historyFile: scala.reflect.io.File = {
      val hFile = App.bundle(getClass).getDataFile(Console.historyFileName)
      log.debug(s"Set local console history file to ${hFile}")
      new scala.reflect.io.File(hFile)
    }
  }
  class JLineCompleter(projection: WeakReference[Projection]) extends Completion.ScalaCompleter {
    def complete(buffer: String, cursor: Int): Completion.Candidates = try {
      val candidates = for {
        console ← instance
        projection ← projection.get
        reader ← console.in
      } yield reader match {
        case reader: Reader ⇒
          val (candidates, proposals) = getCandidatesAndProposals(projection.prefix + buffer, projection.prefix.length() + cursor, console)
          // process completions
          val buf = reader.consoleReader.getCursorBuffer()
          val pos = buf.cursor
          val completions = candidates.candidates.distinct
          // if there is only one completion, then fill in the buffer
          val updatedProposals = if (completions.length == 1) {
            val value = completions(0)
            // fail if the only candidate is the same as the current buffer
            if (!value.equals(buf.toString()))
              CandidateListCompletionHandler.setBuffer(reader.consoleReader, value, pos)
            proposals
          } else if (completions.length > 1 && completions.forall(_.length > 0)) {
            val common = Console.searchForCommonPart(completions)
            if (common.nonEmpty) {
              CandidateListCompletionHandler.setBuffer(reader.consoleReader, common, pos)
              val (candidates, proposals) = getCandidatesAndProposals(projection.prefix + buffer + common,
                projection.prefix.length() + cursor + common.length(), console)
              proposals
            } else proposals
          } else proposals
          // process proposals
          if (updatedProposals.nonEmpty) {
            drawProposals(updatedProposals, console)
            reader.consoleReader.drawLine()
          }
          candidates
      }
      candidates getOrElse Completion.NoCandidates
    } catch {
      case e: Throwable ⇒
        log.error("Unable to get proposal: " + e.getMessage, e)
        Completion.NoCandidates
    }

    /** Draw proposal list on the console. */
    protected def drawProposals(proposals: Seq[Proposal], console: Local) {
      console.echoNoNL("\r\n")
      proposals.sortBy(_.label).foreach {
        case Proposal(label, flagNL, proposalOutputFn) ⇒
          proposalOutputFn()
          if (flagNL)
            console.echoNoNL("\r\n")
      }
    }
    /** Get candidates and proposals. */
    protected def getCandidatesAndProposals(buffer: String, cursor: Int, console: Local): (Completion.Candidates, Seq[Proposal]) = {
      // little feature - notice jline that there is more than one completion
      // @see LocalCandidateListCompletionHandler.complete
      // ... else if (candidates.size() > 1 && !candidates.asScala.forall(_.length() == 0)) ...
      val ReDraw = Completion.Candidates(cursor, List("", ""))
      val completionExt = Command.parse(buffer.take(cursor)) match {
        case result @ Command.Success(_, _) ⇒
          if (cursor > 1)
            // Ok, but there may be more...
            // Remove one character and search for append proposals...
            Command.parse(buffer.take(cursor - 1)) match {
              case Command.MissingCompletionOrFailure(List(Command.Hint(None, None, Seq(" "))), message) ⇒
                result
              case Command.MissingCompletionOrFailure(completionList, message) if completionList.nonEmpty ⇒
                val previousCharacter = buffer.substring(cursor - 1, cursor)
                Command.MissingCompletionOrFailure(completionList.filter(_.completions.exists { completionFromPreviousCharacter ⇒
                  completionFromPreviousCharacter.startsWith(previousCharacter)
                }).map(hint ⇒ hint.copyWithCompletion(hint.completions.map(_.drop(1)): _*)), message)
              case _ ⇒
                result
            }
          else
            result
        case result ⇒
          result
      }
      val completion = (completionExt, Command.parse(Command.parser.CompletionRequest(buffer.take(cursor)))) match {
        case (Command.MissingCompletionOrFailure(listA, _), Command.MissingCompletionOrFailure(listB, _)) ⇒
          Command.MissingCompletionOrFailure((listA ++ listB).distinct, "union")
        case (completion @ Command.MissingCompletionOrFailure(_, _), _) ⇒
          completion
        case (_, completion @ Command.MissingCompletionOrFailure(_, _)) ⇒
          completion
        case (primary, secondary) ⇒
          primary
      }
      completion match {
        case Command.MissingCompletionOrFailure(List(Command.Hint(None, None, Seq(" "))), _) ⇒
          (Completion.Candidates(cursor, List(" ")), Seq.empty)
        case Command.MissingCompletionOrFailure(completionRaw, message) if completionRaw != Seq() ⇒
          val completionList = completionRaw.filterNot(hint ⇒ hint.label == None && hint.description == None).distinct
          completionList match {
            case Nil ⇒
              (Completion.NoCandidates, Seq.empty)
            case list ⇒
              val completionStrings = completionList.map { hint ⇒
                hint.completions match {
                  case Nil ⇒ Seq("")
                  case seq ⇒ seq
                }
              }.flatten.distinct
              if (completionStrings.size == 1 && completionStrings.head.nonEmpty) {
                (Completion.Candidates(cursor, completionStrings.toList), Seq.empty)
              } else {
                val proposals = getProposals(completionList, console)
                (Completion.Candidates(cursor, completionStrings.toList), proposals)
              }
          }
        case Command.Success(id, e) ⇒
          (Completion.NoCandidates, Seq.empty)
        case Command.Error(message) ⇒
          (ReDraw, Seq(Proposal(None, true, () ⇒ console.echoNoNL(Console.msgWarning.format(message) + Console.RESET))))
        case err ⇒
          log.trace(s"Unable to complete '$buffer': " + err)
          (Completion.NoCandidates, Seq.empty)
      }
    }
    /** Get proposals list from sequence of Command.Hint. */
    protected def getProposals(hints: Seq[Command.Hint], console: Local): Seq[Proposal] = hints map {
      case Command.Hint(label, description, Nil) ⇒
        Proposal(label, true, () ⇒
          console.echoNoNL(Console.hintToText(label.getOrElse("UNKNOWN"), description, "")))
      case Command.Hint(label, description, Seq(single)) ⇒
        Proposal(label, true, () ⇒
          console.echoNoNL(Console.hintToText(label.getOrElse("UNKNOWN"), description, single)))
      case Command.Hint(label, description, multiple) ⇒
        Proposal(label, false, () ⇒ {
          console.echoNoNL(Console.hintToText(label.getOrElse("UNKNOWN"), description, "") + "\r\n")
          console.echoColumns(multiple)
        })
    }
    case class Proposal(label: Option[String], flagNL: Boolean, proposalOutputFn: () ⇒ Unit)
  }
  class JLineCompletion(projection: WeakReference[Projection]) extends Completion {
    type ExecResult = Nothing
    def resetVerbosity() = ()
    def completer() = new JLineCompleter(projection)
  }
}
