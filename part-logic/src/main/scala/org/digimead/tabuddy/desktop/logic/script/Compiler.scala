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

package org.digimead.tabuddy.desktop.logic.script

import java.util.concurrent.atomic.AtomicInteger
import org.digimead.digi.lib.api.DependencyInjection
import scala.collection.mutable
import scala.reflect.internal.util.{ BatchSourceFile, Position }
import scala.tools.nsc.io.VirtualDirectory
import scala.tools.nsc.reporters.AbstractReporter
import scala.tools.nsc.{ Global, Settings }
import org.digimead.digi.lib.log.api.Loggable

/**
 * Thread safe compiler implementation with global lock.
 */
class Compiler(val settings: Settings) extends Loggable {
  /** Compiler state. */
  lazy val global = new Global(settings, reporter)
  /** Synchronization lock. */
  val lock = new Object
  /** Dynamic script offset. */
  lazy val lineOffset = new AtomicInteger()
  /** Compiler messages. */
  lazy val reporter = new Compiler.Reporter(lineOffset, settings)

  /** Compile scala code. */
  def apply(code: String, container: Script.Container[_], lineOffset: Int, verbose: Boolean): Unit = lock.synchronized {
    log.debug(s"Compile ${container.className}.")
    try {
      // if you're looking for the performance hit, it's 1/2 this line...
      this.lineOffset.set(lineOffset)
      val compiler = new global.Run
      val sourceFiles = List(new BatchSourceFile("(inline)", code))
      // ...and 1/2 this line:
      compiler.compileSources(sourceFiles)
      if (reporter.hasErrors || reporter.WARNING.count > 0) {
        container.target.clear
        if (verbose) {
          var numLines = -lineOffset
          val header = "\nCode follows (%d bytes)".format(code.length)
          val body = for (line ← code.lines) yield {
            numLines += 1
            if (numLines < 1)
              "*".padTo(Compiler.codePad, ' ') + "| " + line
            else
              numLines.toString.padTo(Compiler.codePad, ' ') + "| " + line
          }
          throw new Script.Exception(List(List(header) ++ body) ++ reporter.accumulator.toList)
        } else
          throw new Script.Exception(reporter.accumulator.toList)
      }
    } finally reporter.reset
  }
}

object Compiler {
  /** Padding in verbose mode. */
  def codePad = DI.codePad

  /** Class that contains compilation logs and provides methods to issue information, warning and error messages. */
  class Reporter(val lineOffset: AtomicInteger, val settings: Settings) extends AbstractReporter {
    /** Buffer with accumulated messages. */
    val accumulator = new mutable.ListBuffer[List[String]]

    def display(pos: Position, message: String, severity: Severity) {
      severity.count += 1 // increment counter of this kind of messages
      val severityName = severity match {
        case ERROR ⇒ "error: "
        case WARNING ⇒ "warning: "
        case _ ⇒ ""
      }
      // the line number is not always available
      val lineMessage =
        try "line " + (pos.line - lineOffset.get())
        catch { case _: Throwable ⇒ "" }
      accumulator += (severityName + lineMessage + ": " + message) :: (if (pos.isDefined)
        pos.inUltimateSource(pos.source).lineContent.stripLineEnd :: (" " * (pos.column - 1) + "^") :: Nil
      else
        Nil)
    }
    def displayPrompt() {} // There is no prompt.
    override def reset {
      super.reset
      accumulator.clear()
    }
  }

  /**
   * Dependency injection routines
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** Padding in verbose mode. */
    lazy val codePad = injectOptional[Int]("Script.Padding") getOrElse 5
  }
}
