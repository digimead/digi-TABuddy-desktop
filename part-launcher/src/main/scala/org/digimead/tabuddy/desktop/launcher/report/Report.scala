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

package org.digimead.tabuddy.desktop.launcher.report

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.FilenameFilter
import java.io.OutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.management.ManagementFactory
import java.util.Date
import java.util.Properties
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import java.util.zip.GZIPOutputStream

import scala.Array.canBuildFrom
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.future
import scala.util.control.ControlThrowable
import scala.collection.JavaConversions._

import org.digimead.digi.lib.DependencyInjection
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.log.Logging
import org.digimead.digi.lib.log.Logging.Logging2implementation
import org.digimead.digi.lib.log.Record
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.digi.lib.util.Util

import com.escalatesoft.subcut.inject.BindingModule
import com.escalatesoft.subcut.inject.Injectable
import com.google.common.io.ByteStreams

import language.implicitConversions
import language.reflectiveCalls

class Report(implicit val bindingModule: BindingModule) extends Report.Interface with Loggable {
  /** Thread with cleaner process */
  @volatile private var cleanThread: Option[Thread] = None
  /** Flag indicating whether stack trace generation enabled */
  val allowGenerateStackTrace = inject[Boolean]("Report.TraceFileEnabled")
  /** Number of saved log files */
  val keepLogFiles: Int = injectOptional[Int]("Report.KeepLogFiles") getOrElse 4
  /** Quantity of saved trace files */
  val keepTrcFiles: Int = injectOptional[Int]("Report.KeepTrcFiles") getOrElse 8
  /** Log file extension */
  val logFileExtension: String = injectOptional[String]("Report.LogFileExtension") getOrElse "log" // t(z)log, z - compressed
  /** Log file extension prefix */
  val logFileExtensionPrefix: String = injectOptional[String]("Report.LogFilePrefix") getOrElse "d"
  /** Path to report files */
  val path: File = inject[File]("Report.LogPath")
  /** Trace file extension */
  val traceFileExtension: String = injectOptional[String]("Report.TraceFileExtension") getOrElse "trc"

  /** Clean report files */
  @log
  def clean(): Unit = if (!submitInProgressLock.get()) synchronized {
    if (cleanThread.nonEmpty) {
      log.warn("cleaning in progress, skip")
      return
    }
    cleanThread = Some(new Thread("report cleaner for " + Report.getClass.getName) {
      log.debug("new report cleaner thread %s alive".format(this.getId.toString))
      this.setDaemon(true)
      override def run() = try {
        toClean(path, Seq())._2.foreach(_.delete)
      } catch {
        case e: Throwable =>
          log.error(e.getMessage, e)
      } finally {
        cleanThread = None
      }
    })
    cleanThread.get.start
  }
  /** Clean report files except active */
  @log
  def cleanAfterReview(dir: File = path): Unit = if (!submitInProgressLock.get()) synchronized {
    log.debug("clean reports after review")
    val reports = Option(dir.list()).getOrElse(Array[String]())
    if (reports.isEmpty)
      return
    try {
      reports.foreach(name => {
        val report = new File(dir, name)
        val active = try {
          val name = report.getName
          val pid = name.split("""-""")(2).drop(1).reverse.dropWhile(_ != '.').drop(1).reverse
          this.pid == pid
        } catch {
          case e: Throwable =>
            log.error(s"unable to find pid for %s: %s".format(report.getName(), e.getMessage()), e)
            false
        }
        if (!active || !report.getName.endsWith(logFileExtension)) {
          log.info("delete " + report.getName)
          report.delete
        }
      })
    } catch {
      case e: Throwable =>
        log.error(e.getMessage, e)
    }
  }
  /** Compress report logs */
  @log
  def compress(): Unit = synchronized {
    val reports: Array[File] = Option(path.listFiles(new FilenameFilter {
      def accept(dir: File, name: String) =
        name.toLowerCase.endsWith(logFileExtension) && !name.toLowerCase.endsWith("z" + logFileExtension)
    }).sortBy(_.getName).reverse).getOrElse(Array[File]())
    if (reports.isEmpty)
      return
    try {
      reports.foreach(report => {
        val reportName = report.getName
        val compressed = new File(path, reportName.substring(0, reportName.length - logFileExtension.length) + "z" + logFileExtension)
        val active = try {
          val pid = reportName.split("""-""")(2).drop(1).reverse.dropWhile(_ != '.').drop(1).reverse
          if (this.pid == pid) {
            // "-Pnnnnn.dlog"
            val suffix = reportName.substring(reportName.length - logFileExtensionPrefix.length - logFileExtension.length - 7)
            reports.find(_.getName.endsWith(suffix)) match {
              case Some(activeName) =>
                activeName.getName == reportName
              case None =>
                false
            }
          } else
            false
        } catch {
          case e: Throwable =>
            log.error(s"unable to find pid for %s: %s".format(report.getName(), e.getMessage()), e)
            false
        }
        if (!active && report.length > 0) {
          // compress log files
          log.info("save compressed log file " + compressed.getName)
          val is = new BufferedInputStream(new FileInputStream(report))
          var zos: OutputStream = null
          try {
            zos = new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(compressed)))
            ByteStreams.copy(is, zos)
          } finally {
            if (zos != null)
              zos.close()
          }
          if (compressed.length > 0) {
            log.info("delete uncompressed log file " + reportName)
            report.delete
          } else {
            log.warn("unable to compress " + reportName + ", delete broken archive")
            compressed.delete
          }
        }
      })
    } catch {
      case e: Throwable =>
        log.error(e.getMessage, e)
    }
  }
  /** Generate the stack trace report */
  @log
  def generateStackTrace(e: Throwable, when: Date) {
    if (!path.exists())
      if (!path.mkdirs()) {
        log.fatal("Unable to create log path " + path)
        return
      }
    // take actual view
    // TODO user interaction
    // Main.execNGet { takeScreenshot() }
    // Here you should have a more robust, permanent record of problems
    val reportName = filePrefix + "." + logFileExtensionPrefix + traceFileExtension
    val result = new StringWriter()
    val printWriter = new PrintWriter(result)
    e.printStackTrace(printWriter)
    if (e.getCause() != null) {
      printWriter.println("\nCause:\n")
      e.getCause().printStackTrace(printWriter)
    }
    try {
      val file = new File(path, reportName)
      log.debug("Writing unhandled exception to: " + file)
      // Write the stacktrace to disk
      val bos = new BufferedWriter(new FileWriter(file))
      bos.write(Util.dateString(when) + "\n")
      bos.write(result.toString())
      bos.flush()
      // Close up everything
      bos.close()
      // -rw-r--r--
      file.setReadable(true, false)
    } catch {
      // Nothing much we can do about this - the game is over
      case e: Throwable =>
        System.err.println("Fatal error " + e)
        e.printStackTrace()
    }
  }
  /** Returns general information about application */
  def info(): String = {
    // Get components information if any.
    val versionProperties = getClass.getClassLoader.getResources("version.properties")
    val components = for (resURL <- versionProperties) yield try {
      val properties = new Properties
      properties.load(resURL.openStream())
      Option(properties.getProperty("name")).map { name =>
        val version = Option(properties.getProperty("version")).getOrElse("0")
        val build = Option(properties.getProperty("build")) match {
          case Some(rawBuild) =>
            (try { Util.dateString(new Date(rawBuild.toLong * 1000)) } catch { case e: Throwable => rawBuild }) + s" (${rawBuild})"
          case None => "0"
        }
        s"${name}: version: ${version}, build: ${build}"
      }
    } catch {
      case e: Throwable => //
        log.error("Unable to load version.properties for " + resURL, e)
        None
    }
    // Get SWT information if any.
    val platform = try {
      Option(Class.forName("org.eclipse.swt.SWT").getMethod("getPlatform").invoke(null))
    } catch {
      case e: Throwable =>
        log.error(e.getMessage(), e)
        None
    }
    // Generate report
    "report path: " + Report.path + "\n" +
      "os: " + Option(System.getProperty("os.name")).getOrElse("UNKNOWN") + "\n" +
      "arch: " + Option(System.getProperty("os.arch")).getOrElse("UNKNOWN") + "\n" +
      platform.map(p => s"platform: $p\n").getOrElse("") +
      components.flatten.toSeq.sorted.mkString("\n") + "\n"
  }
  /** Process the new report */
  def process(record: Option[Record.Message]) = {
    //ReportDialog.submit(record.map(r => "Exception " + r.message))
  }
  /**
   * Submit error reports for investigation
   */
  @log
  def submit(force: Boolean, uploadCallback: Option[(Int, Int) => Any] = None): Boolean = synchronized {
    log.debug("looking for error reports in: " + path)
    val reports: Array[File] = Option(path.listFiles()).getOrElse(Array[File]())
    if (reports.isEmpty)
      return true
    Logging.bufferedAppender.foreach(_.init)
    Thread.sleep(500) // waiting for no reason ;-)
    try {
      if (force || reports.exists(_.getName.endsWith(traceFileExtension))) {
        val fileN = new AtomicInteger
        val sessionId = UUID.randomUUID.toString + "-"
        Logging.flush(500)
        //GoogleCloud.upload(reports, sessionId, { uploadCallback.foreach(_(fileN.incrementAndGet, reports.size)) })
        true
      } else {
        true
      }
    } catch {
      case ce: ControlThrowable => throw ce // propagate
      case e: Throwable =>
        log.error(e.getMessage, e)
        false
    }
  }

  /**
   * Build sequence of files to delete
   * @return keep suffixes, files to delete
   */
  private def toClean(dir: File, keep: Seq[String]): (Seq[String], Seq[File]) = try {
    var result: Seq[File] = Seq()
    val files = Option(dir.listFiles()).getOrElse(Array[File]()).map(f => f.getName.toLowerCase -> f)
    val traceFiles = files.filter(_._1.endsWith(traceFileExtension)).sortBy(_._1).reverse
    traceFiles.drop(keepTrcFiles).foreach {
      case (name, file) =>
        log.info("delete outdated stacktrace file " + name)
        result = result :+ file
    }
    files.filter(_._1.endsWith(".description")).foreach {
      case (name, file) =>
        log.info("delete outdated description file " + name)
        result = result :+ file
    }
    files.filter(_._1.endsWith(".png")).foreach {
      case (name, file) =>
        log.info("delete outdated png file " + name)
        result = result :+ file
    }
    // sequence of name suffixes: Tuple2(uncompressed suffix, compressed suffix)
    val keepForTraceReport = traceFiles.take(keepTrcFiles).map(t => {
      val name = t._1
      val traceSuffix = name.substring(name.length - name.reverse.takeWhile(_ != '-').length - 1)
      Array(traceSuffix.takeWhile(_ != '.') + "." + logFileExtensionPrefix + logFileExtension,
        traceSuffix.takeWhile(_ != '.') + "." + logFileExtensionPrefix + "z" + logFileExtension)
    }).flatten.distinct
    val logFiles = files.filter(_._1.endsWith(logFileExtension)).sortBy(_._1).reverse
    // sequence of name suffixes: Tuple2(uncompressed suffix, compressed suffix)
    // keep all log files with PID == last run
    val keepLog = logFiles.take(keepLogFiles).map(_._1 match {
      case compressed if compressed.endsWith("z" + logFileExtension) =>
        // for example "-P0000.dzlog"
        Array(compressed.substring(compressed.length - compressed.reverse.takeWhile(_ != '-').length - 1))
      case plain =>
        // for example "-P0000.dlog"
        val logSuffix = plain.substring(plain.length - plain.reverse.takeWhile(_ != '-').length - 1)
        Array(logSuffix.takeWhile(_ != '.') + "." + logFileExtensionPrefix + logFileExtension,
          logSuffix.takeWhile(_ != '.') + "." + logFileExtensionPrefix + "z" + logFileExtension)
    }).flatten.distinct
    log.debug("keep log files with suffixes: " + (keepLog ++ keepForTraceReport).mkString(", "))
    val keepSuffixes = (keepLog ++ keepForTraceReport ++ keep).distinct
    logFiles.drop(keepLogFiles).foreach {
      case (name, file) =>
        if (!keepSuffixes.exists(name.endsWith)) {
          log.info("delete outdated log file " + name)
          result = result :+ file
        }
    }
    (keepSuffixes, result)
  }
  object LogSubscriber extends Logging.Event.Sub {
    val lock = new ReentrantLock
    def notify(pub: Logging.Event.Pub, event: Logging.Event) = if (!lock.isLocked()) {
      event match {
        case event: Logging.Event.Outgoing =>
          if (event.record.throwable.nonEmpty && event.record.level == Record.Level.Error) {
            future {
              if (lock.tryLock()) try {
                if (allowGenerateStackTrace)
                  generateStackTrace(event.record.throwable.get, event.record.date)
                Report.this.process(Option(event.record))
              } finally {
                lock.unlock()
              }
            } onFailure {
              case e: Exception => log.error(e.getMessage(), e)
              case e => log.error(e.toString())
            }
          }
        case _ =>
      }
    }
  }
}

object Report extends Loggable {
  implicit def report2implementation(r: Report.type): Interface = r.inner
  @volatile private var active: Boolean = false

  def inner() = DI.implementation

  trait Interface extends Injectable {
    /** Number of saved log files */
    val keepLogFiles: Int
    /** Quantity of saved trace files */
    val keepTrcFiles: Int
    /** Log file extension */
    val logFileExtension: String
    /** Log file extension prefix */
    val logFileExtensionPrefix: String
    /** Path to report files */
    val path: File
    /** Process ID */
    val pid = ManagementFactory.getRuntimeMXBean().getName()
    /** Flag indicating if the submit process is on going */
    val submitInProgressLock = new AtomicBoolean(false)
    /** Trace file extension */
    val traceFileExtension: String
    /** User ID */
    val uid = System.getProperty("user.name")

    /** Clean report files */
    def clean(): Unit
    /** Clean report files after review */
    def cleanAfterReview(dir: File = path): Unit
    /** Compress report logs */
    def compress(): Unit
    /** Process an error event */
    def process(record: Option[Record.Message])
    /** Returns file prefix */
    def filePrefix(): String = {
      val uid = "U" + this.uid
      val date = Util.dateFile(new Date())
      val pid = "P" + this.pid
      Seq(uid, date, pid).map(_.replaceAll("""[/?*:\.;{}\\-]+""", "_")).mkString("-")
    }
    /** Returns general information about application */
    def info(): String
  }
  /**
   * Dependency injection routines
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** Report implementation */
    val implementation = inject[Interface]
  }
}