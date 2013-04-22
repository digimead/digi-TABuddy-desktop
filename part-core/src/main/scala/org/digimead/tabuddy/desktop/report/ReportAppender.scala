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

package org.digimead.tabuddy.desktop.report

import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter

import scala.Array.canBuildFrom

import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.log.Logging
import org.digimead.digi.lib.log.Logging.Logging2implementation
import org.digimead.digi.lib.log.Record
import org.digimead.digi.lib.log.appender.Appender
import org.digimead.digi.lib.log.logger.RichLogger.rich2slf4j
import org.digimead.tabuddy.desktop.report.Report.report2implementation

object ReportAppender extends Appender {
  @volatile private var file: Option[File] = None
  @volatile private var output: Option[BufferedWriter] = None
  private val fileLimit = 102400 // 100kB
  private val checkEveryNLines = 1000
  @volatile private var counter = 0
  protected var f = (records: Array[Record.Message]) => synchronized {
    // rotate
    for {
      output <- output
      file <- file
    } {
      counter += records.size
      if (counter > checkEveryNLines) {
        counter = 0
        if (file.length > fileLimit)
          openLogFile()
      }
    }
    // write
    output.foreach {
      output =>
        output.write(records.map(r => {
          r.toString() +
            r.throwable.map(t => try {
              "\n" + t.getStackTraceString
            } catch {
              case e: Throwable =>
                "stack trace \"" + t.getMessage + "\" unaviable "
            }).getOrElse("")
        }).mkString("\n"))
        output.newLine
        output.flush
    }
  }
  @log
  override def init() = synchronized {
    openLogFile()
    output.foreach(_.flush)
  }
  override def deinit() = synchronized {
    try {
      // close output if any
      output.foreach(_.close)
      output = None
      file = None
    } catch {
      case e: Throwable =>
        Logging.commonLogger.error(e.getMessage, e)
    }
  }
  override def flush() = synchronized {
    try { output.foreach(_.flush) } catch { case e: Throwable => }
  }

  private def getLogFileName() =
    Report.filePrefix + "." + Report.logFileExtensionPrefix + Report.logFileExtension
  /** Close and compress the previous log file, prepare and open new one */
  private def openLogFile() = try {
    deinit
    // open new
    file = {
      val file = new File(Report.path, getLogFileName)
      if (!Report.path.exists)
        Report.path.mkdirs
      if (file.exists) {
        Logging.commonLogger.debug("open new log file " + file)
        Some(file)
      } else if (file.createNewFile) {
        Logging.commonLogger.info("create new log file " + file)
        Some(file)
      } else {
        Logging.commonLogger.error("unable to create log file " + file)
        None
      }
    }
    output = file.map(f => {
      // write header
      val writer = new BufferedWriter(new FileWriter(f, true))
      writer.write("=== TA-Buddy desktop (if you have a question or suggestion, email ezh@ezh.msk.ru) ===\n")
      writer.write(Report.info)
      writer.write("=====================================================================================\n\n")
      // -rw-r--r--
      f.setReadable(true, false)
      writer
    })
    Report.compress()
  } catch {
    case e: Throwable =>
      Logging.commonLogger.error(e.getMessage, e)
  }
}
