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
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.console.{ Console, Projection, Reader ⇒ CReader, Writer ⇒ CWriter }
import org.digimead.tabuddy.desktop.core.definition.command.Command
import org.digimead.tabuddy.desktop.core.support.App
import scala.collection.JavaConverters._
import scala.tools.jline.console.ConsoleReader
import scala.tools.jline.console.completer.CandidateListCompletionHandler
import scala.tools.nsc.interpreter.{ Completion, ConsoleReaderHelper, JLineReader, NoCompletion }
import scala.tools.nsc.interpreter.session.JLineHistory.JLineFileHistory
import scala.tools.nsc.interpreter.session.SimpleHistory

class Local extends Projection with Loggable {
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
    in = Option(new Local.Reader)
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

object Local extends Loggable {
  @volatile private var instance: Option[Local] = None

  class Reader() extends JLineReader(new Local.JLineCompletion) with CReader {
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
  class JLineCompleter extends Completion.ScalaCompleter {
    def complete(buffer: String, cursor: Int): Completion.Candidates = try {
      val candidates = for {
        console ← instance
        reader ← console.in
      } yield reader match {
        case reader: Reader ⇒
          val (candidates, proposals) = getCandidatesAndProposals(buffer, cursor, console)
          // process completions
          val buf = reader.consoleReader.getCursorBuffer()
          val pos = reader.consoleReader.currentPos
          val completions = candidates.candidates.filter(_.nonEmpty)
          // if there is only one completion, then fill in the buffer
          val completionShift = if (completions.length == 1) {
            val value = completions(0)
            // fail if the only candidate is the same as the current buffer
            if (!value.equals(buf.toString()))
              CandidateListCompletionHandler.setBuffer(reader.consoleReader, value, pos)
            0
          } else if (completions.length > 1 && !completions.forall(_.length == 0)) {
            val common = Console.searchForCommonPart(completions)
            if (common.nonEmpty) {
              CandidateListCompletionHandler.setBuffer(reader.consoleReader, common, pos)
              common.length()
            } else 0
          } else 0
          // process proposals
          if (proposals.nonEmpty) {
            drawProposals(proposals, completionShift, console)
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
    protected def drawProposals(proposals: Seq[(Option[String], Boolean, (Int) ⇒ Unit)], completionShift: Int, console: Local) {
      console.echoNoNL("\r\n")
      proposals.sortBy(_._1).foreach {
        case (label, flagNL, proposalOutputFn) ⇒
          proposalOutputFn(completionShift)
          if (flagNL)
            console.echoNoNL("\r\n")
      }
    }
    /** Get candidates and proposals. */
    protected def getCandidatesAndProposals(buffer: String, cursor: Int,
      console: Local): (Completion.Candidates, Seq[(Option[String], Boolean, (Int) ⇒ Unit)]) = {
      // little feature - notice jline that there is more than one completion
      // @see LocalCandidateListCompletionHandler.complete
      // ... else if (candidates.size() > 1 && !candidates.asScala.forall(_.length() == 0)) ...
      val ReDraw = Completion.Candidates(cursor, List("", ""))
      Command.completionProposalMode.withValue(true) { Command.parse(buffer.take(cursor)) } match {
        case Command.MissingCompletionOrFailure(true, completionRaw, message) ⇒
          val completionList = completionRaw.distinct
          val completionStrings = completionList.map(_.completions).flatten.filter(_.nonEmpty).distinct
          completionStrings match {
            case Nil ⇒
              val proposals = completionList.map {
                case Command.Hint(label, description, Seq(single)) ⇒
                  (label, true, (shift: Int) ⇒ console.echoNoNL(Console.hintToText(label.getOrElse("UNKNOWN"), description, single.drop(shift))))
                case Command.Hint(label, description, multiple) ⇒
                  (label, false, (shift: Int) ⇒ {
                    console.echoNoNL(Console.hintToText(label.getOrElse("UNKNOWN"), description, "") + "\r\n")
                    console.echoColumns(multiple)
                  })
              }
              if (proposals.nonEmpty)
                (ReDraw, proposals)
              else
                (Completion.NoCandidates, Seq.empty)
            case Seq(single) ⇒
              (Completion.Candidates(cursor, List(single)), Seq.empty)
            case multiple ⇒
              val proposals = completionList.filter(_.completions.nonEmpty).map {
                case Command.Hint(label, description, Seq(single)) ⇒
                  (label, true, (shift: Int) ⇒ console.echoNoNL(Console.hintToText(label.getOrElse("UNKNOWN"), description, single.drop(shift))))
                case Command.Hint(label, description, multiple) ⇒
                  (label, false, (shift: Int) ⇒ {
                    console.echoNoNL(Console.hintToText(label.getOrElse("UNKNOWN"), description, "") + "\r\n")
                    console.echoColumns(multiple.map(_.drop(shift)))
                  })
              }
              (Completion.Candidates(cursor, multiple.toList), proposals)
          }
        case Command.Success(id, e) ⇒
          // Ok, but there may be more...
          // Add space character and search for append proposals...
          Command.parse(buffer.take(cursor) + " ") match {
            case Command.MissingCompletionOrFailure(true, completionList, message) if completionList.nonEmpty ⇒
              (Completion.Candidates(cursor, List(" ")), Seq.empty)
            case _ ⇒
              (Completion.NoCandidates, Seq.empty)
          }
        case Command.Error(message) ⇒
          (ReDraw, Seq((None, true, (shift: Int) ⇒ console.echoNoNL(Console.msgWarning.format(message) + Console.RESET))))
        case err ⇒
          log.trace(s"Unable to complete '$buffer': " + err)
          (Completion.NoCandidates, Seq.empty)
      }
    }
  }
  class JLineCompletion extends Completion {
    type ExecResult = Nothing
    def resetVerbosity() = ()
    def completer() = new JLineCompleter
  }
}
