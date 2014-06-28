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

package org.digimead.tabuddy.desktop.core.console

import java.util.concurrent.Exchanger
import java.util.concurrent.TimeUnit
import org.digimead.tabuddy.desktop.core.console.api.XConsole

/**
 * Projection is a base trait for the specific console interface.
 */
trait Projection extends XConsole.Projection {
  /** The input stream from which commands come. */
  protected var in: Option[Reader] = None
  /** Last input from user. */
  protected var lastLine = ""
  /** The output stream for which command results come. */
  protected var out: Option[Writer] = None
  /** Flag indicating whether a multiline mode is active. */
  protected var multiline = Seq.empty[String]

  /** Barrier that freezes processing of the prompt until explicit signal. */
  protected val waitForPromptPermission = new Exchanger[Null]

  /** Start prompt processing. */
  def enablePrompt() =
    waitForPromptPermission.exchange(null, 1000, TimeUnit.MILLISECONDS)
  /** Get multiline prefix. */
  def prefix = multiline.mkString

  /**
   * The main loop for the console. It calls
   *  command() for each line of input, and stops when
   *  command() returns false.
   */
  protected def loop() {
    def readOneLine() = if (in.isEmpty || out.isEmpty) {
      None
    } else {
      withOut(_.flush())
      withIn { in ⇒ in readLine prompt }
    }
    waitForPromptPermission.exchange(null)
    var next = true
    while (next && in.nonEmpty && out.nonEmpty) {
      readOneLine() match {
        case Some(lastLine) ⇒
          this.lastLine = lastLine
          if (lastLine.nonEmpty)
            // return false if loop should exit
            // assume null means EOF
            next = if (lastLine eq null) {
              false
            } else {
              val nextLineSymbolIndex = lastLine.lastIndexOf("""\""")
              if (nextLineSymbolIndex >= 0 && lastLine.drop(nextLineSymbolIndex + 1).trim().isEmpty()) {
                val chunk = lastLine.take(nextLineSymbolIndex).trim() + " "
                if (chunk == " ") {
                  // empty line
                  multiline = Seq.empty
                  Console ! Console.Message.Command(multiline.mkString, Some(this))
                } else {
                  multiline = multiline :+ chunk
                }
              } else {
                Console ! Console.Message.Command(multiline.mkString + lastLine, Some(this))
                multiline = Seq.empty
              }
              true
            }
          else if (multiline.nonEmpty) {
            Console ! Console.Message.Command(multiline.mkString + lastLine, Some(this))
            multiline = Seq.empty
          }
        case None ⇒
          next = false
      }
    }
  }
  /** Prompt to print when awaiting input. */
  protected def prompt = if (multiline.nonEmpty) ">" else Console.prompt
  /** Replay question message for broken command. */
  protected def replayQuestionMessage(err: String) =
    s"""|The command execution was interrupted with an error: ${err}
       |Shall I replay this command? [y/n]
    """.trim.stripMargin
  /** Thread safe input. */
  protected def withIn[T](f: Reader ⇒ T): Option[T] =
    in.map { in ⇒ in.synchronized { f(in) } }
  /** Thread safe output. */
  protected def withOut[T](f: Writer ⇒ T): Option[T] =
    out.map { out ⇒ out.synchronized { f(out) } }
}
