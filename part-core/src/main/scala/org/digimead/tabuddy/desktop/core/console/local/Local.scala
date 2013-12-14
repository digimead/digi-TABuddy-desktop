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

package org.digimead.tabuddy.desktop.core.console.local

import java.io.PrintWriter
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.console.{ Console, Projection, Reader ⇒ CReader, Writer ⇒ CWriter }
import org.digimead.tabuddy.desktop.core.definition.command.Command
import org.digimead.tabuddy.desktop.core.definition.command.Command.cmdLine2implementation
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.support.App.app2implementation
import scala.tools.nsc.interpreter.{ Completion, JLineReader, NoCompletion }
import scala.tools.nsc.interpreter.session.JLineHistory.JLineFileHistory
import scala.tools.nsc.interpreter.session.SimpleHistory
import scala.tools.jline.console.completer.CandidateListCompletionHandler
import scala.tools.jline.console.ConsoleReader
import java.util.Collection

class Local extends Projection with Loggable {
  /** Thread with local console loop. */
  @volatile protected var thread = Option.empty[Thread]

  Local.synchronized {
    if (Local.instance.nonEmpty)
      throw new IllegalStateException("There can be only one local console per application.")
    Local.instance = Some(this)
  }

  def echo(msg: String) = out.foreach { out ⇒ out.echoAndRefresh(msg) }
  def echoHint(msg: String) = out.foreach { out ⇒ out.echo("\n" + msg) }
  def start() {
    log.debug(s"Start local console.")
    in = Option(new Local.Reader)
    out = Option(new Local.Writer(this))
    val thread = new Thread(new Runnable { def run = loop })
    thread.setDaemon(true)
    thread.start()
    this.thread = Option(thread)
  }
  def stop() {
    log.debug(s"Stop local console.")
    out.foreach(_.echo(""))
    in = None
    out = None
  }
}

object Local extends Loggable {
  @volatile private var instance: Option[Local] = None

  class Reader extends JLineReader(JLineCompletion) with CReader {
    override val consoleReader = new LocalConsoleReader()
    override lazy val history: scala.tools.nsc.interpreter.session.JLineHistory =
      try new JLineHistory catch { case x: Exception ⇒ new SimpleHistory() }
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
    }
  }
  /**
   * CandidateListCompletionHandler
   */
  /*
   * copy'n'paste hell
   * getUnambiguousCompletions marked as private :-(
   * I hate those pals.
   * Such type of 'private' is useful only at commercial projects
   *   when someone want to restrict consumer API and reduce support cost
   *   because consumer is potential enemy.
   * I am is NOT a consumer that affects ROI(Return on investment)...
   *
   * Original class is distributed under the BSD license.
   */
  class LocalCandidateListCompletionHandler extends CandidateListCompletionHandler {
    override def complete(reader: ConsoleReader, candidates: java.util.List[CharSequence], pos: Int): Boolean = {
      val buf = reader.getCursorBuffer();

      // if there is only one completion, then fill in the buffer
      if (candidates.size() == 1) {
        val value = candidates.get(0)

        // fail if the only candidate is the same as the current buffer
        if (value.equals(buf.toString()))
          return false
        CandidateListCompletionHandler.setBuffer(reader, value, pos);
        return true
      } else if (candidates.size() > 1) {
        var value = hackForUnambiguousCompletions(candidates)
        CandidateListCompletionHandler.setBuffer(reader, value, pos)
      }
      // redraw the current console buffer
      reader.drawLine()
      true
    }
    /**
     * Returns a root that matches all the {@link String} elements of the specified {@link List},
     * or null if there are no commonalities. For example, if the list contains
     * <i>foobar</i>, <i>foobaz</i>, <i>foobuz</i>, the method will return <i>foob</i>.
     */
    def hackForUnambiguousCompletions(candidates: java.util.List[CharSequence]): String = {
      if (candidates == null || candidates.isEmpty())
        return null

      // convert to an array for speed
      val strings = candidates.toArray(new Array[String](candidates.size()))
      val first = strings(0)
      val candidate = new StringBuilder()
      for (i ← 0 until first.length()) {
        if (startsWith(first.substring(0, i + 1), strings)) {
          candidate.append(first.charAt(i))
        } else {
          return candidate.toString()
        }
      }
      candidate.toString()
    }
    /**
     * @return true is all the elements of <i>candidates</i> start with <i>starts</i>
     */
    def startsWith(starts: String, candidates: Array[String]): Boolean = {
      for (candidate ← candidates) {
        if (!candidate.startsWith(starts)) {
          return false
        }
      }
      return true
    }
  }
  class Writer(local: Local) extends PrintWriter(scala.Console.out, true) with CWriter {
    def in = local.in
  }
  class JLineHistory extends JLineFileHistory {
    override protected lazy val historyFile: scala.reflect.io.File = {
      val hFile = App.bundle(getClass).getDataFile(Console.historyFileName)
      log.debug(s"Set local console history file to ${hFile}")
      new scala.reflect.io.File(hFile)
    }
  }
  object JLineCompleter extends Completion.ScalaCompleter {
    def complete(buffer: String, cursor: Int): Completion.Candidates = {
      Command.parse(buffer.take(cursor)) match {
        case Command.MissingCompletionOrFailure(true, completionList, message) ⇒
          // sort by hint.completionLabel
          val completions = completionList.map {
            case (string, proposals) ⇒ proposals.map(hint ⇒ (hint.completionLabel, string,
              Console.hintToText(hint.completionLabel, hint.completionDescription, string)))
          }.flatten.sortBy(_._1)
          if (completions.size > 1)
            instance.foreach(_.echoHint(completions.map(_._3).mkString("\n")))
          Completion.Candidates(cursor, completions.map(_._2).toList.distinct)
        case Command.Success(id, _) ⇒
          // Ok, but there may be more...
          // Add space character and search for append proposals...
          Command.parse(buffer.take(cursor) + " ") match {
            case Command.MissingCompletionOrFailure(true, completionList, message) if completionList.nonEmpty ⇒
              Completion.Candidates(cursor, List(" "))
            case _ ⇒
              Completion.NoCandidates
          }
        case _ ⇒
          Completion.NoCandidates
      }
    }
  }
  object JLineCompletion extends Completion {
    type ExecResult = Nothing
    def resetVerbosity() = ()
    def completer() = JLineCompleter
  }
}