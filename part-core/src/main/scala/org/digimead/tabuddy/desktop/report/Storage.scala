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

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.lang.reflect.InvocationTargetException
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.concurrent.future

import org.digimead.digi.lib.DependencyInjection
import org.digimead.digi.lib.log.Loggable
import org.digimead.digi.lib.log.logger.RichLogger.rich2slf4j
import org.digimead.digi.lib.util.FileUtil
import org.digimead.tabuddy.desktop.Main
import org.digimead.tabuddy.desktop.report.Report.report2implementation
import org.digimead.tabuddy.desktop.res.Messages
import org.digimead.tabuddy.desktop.support.Timeout
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jface.dialogs.MessageDialog
import org.eclipse.jface.dialogs.ProgressMonitorDialog
import org.eclipse.jface.operation.IRunnableWithProgress
import org.eclipse.swt.SWT
import org.eclipse.swt.widgets.Shell

import com.escalatesoft.subcut.inject.BindingModule
import com.escalatesoft.subcut.inject.Injectable

class Storage(implicit val bindingModule: BindingModule) extends Storage.Interface with Injectable with Loggable {
  // Default connection and socket timeout of 5 seconds. Tweak to taste.
  private val SOCKET_OPERATION_TIMEOUT = 5 * 1000
  private val uploadPoolSize = 4
  private val retry = 3
  /** Connection proxy */
  val proxy = injectOptional[Proxy]
  /** Storage settings url */
  val uploadSettingsURL = new URL(new String(new sun.misc.BASE64Decoder().decodeBuffer(inject[String]("Report.UploadSettingsURL")), "UTF-8"))
  /** Upload URL */
  @volatile var uploadURL: Option[URL] = None

  /** Prepare storage for uploading */
  def prepare(monitor: IProgressMonitor) {
    log.debug("preparing storage for uploading")
    download(uploadSettingsURL, Messages.errorReportUploadAquire_text, monitor) match {
      case Some(raw) =>
        try {
          val content = new String(raw, "UTF-8")
          if (content.trim.isEmpty()) {
            log.warn("Unable to acquire the upload settings: content is empty")
            throw new RuntimeException(Messages.errorReportUploadAquireFailed_text)
          }
          uploadURL = Option(new URL(new String(new sun.misc.BASE64Decoder().decodeBuffer(content.trim), "UTF-8")))
        } catch {
          case e: Throwable =>
            log.warn("Unable to acquire the upload settings: " + e, e)
            throw new RuntimeException(Messages.errorReportUploadAquireFailed_text)
        }
      case None =>
        throw new RuntimeException(Messages.errorReportUploadAquireFailed_text)
    }
    monitor.worked(1)
  }
  /** Upload files to storage */
  def upload(files: Seq[File], monitor: IProgressMonitor) {
    val executorService = Executors.newFixedThreadPool(3)
    implicit val executionContextExecutor = ExecutionContext.fromExecutorService(executorService)
    val futures = for (i <- 0 until files.length if !monitor.isCanceled()) yield {
      future({
        try {
          val file = files(i)
          monitor.subTask("Uploading " + file.getName)
          upload(file)
          monitor.worked(1)
        } catch {
          case e: Throwable =>
            log.error("" + e.getMessage() + e.printStackTrace())
            throw new InterruptedException(e.getMessage())
        }
      })
    }
    try {
      Await.result(Future.sequence(futures), Timeout.longer.millis)
    } catch {
      case e: Throwable =>
        executionContextExecutor.shutdownNow()
        throw new RuntimeException(Messages.errorReportUploadAquireFailed_text)
    }
  }

  /** Download file from URL */
  protected def download(url: URL, taskName: String, monitor: IProgressMonitor): Option[Array[Byte]] = {
    log.debug(s"downloading from $url")
    try {
      log.debug("open connection to " + url)
      val connection = proxy match {
        case Some(proxy) => url.openConnection(proxy)
        case None => url.openConnection()
      }
      connection.connect()
      // this will be useful so that you can show a typical 0-100% progress bar
      val total = connection.getContentLength() match {
        case n if n > 0 => (n.toDouble / 1024).toInt
        case _ => "?"
      }
      // download the file
      val input = new BufferedInputStream(url.openStream());
      val output = new ByteArrayOutputStream()
      val data = new Array[Byte](1024)
      var downloaded = 0L
      var count = input.read(data)
      while (count != -1) {
        downloaded += count
        // publishing the progress....
        monitor.subTask(taskName.format((downloaded.toDouble / 1024).toInt, total, url))
        output.write(data, 0, count)
        count = input.read(data)
      }
      log.debug("file downloaded, close connection")
      output.flush()
      output.close()
      input.close()
      Option(output.toByteArray())
    } catch {
      case e: Throwable =>
        log.error(s"Unable to download $url: " + e.getMessage())
        None
    }
  }
  /** Upload file to storage */
  protected def upload(file: File): Boolean = uploadURL.map { base =>
    val url = new URL(base.toURI().toString() + URLEncoder.encode(file.getName(), "UTF-8"))
    upload(url, file)
  } getOrElse false
  /** Upload file to URL */
  protected def upload(url: URL, file: File): Boolean = {
    log.debug(s"uploading to $url")
    var writer: OutputStream = null
    try {
      log.debug("open connection to " + url)
      val connection = proxy match {
        case Some(proxy) => url.openConnection(proxy).asInstanceOf[HttpURLConnection]
        case None => url.openConnection().asInstanceOf[HttpURLConnection]
      }
      connection.setDoInput(true)
      connection.setDoOutput(true)
      connection.setRequestProperty("Content-Type", "application/octet-stream")
      connection.setRequestMethod("PUT")
      connection.connect()
      val output = connection.getOutputStream()
      writer = new BufferedOutputStream(output)

      // request part
      var requestStream: InputStream = null
      try {
        requestStream = new FileInputStream(file)
        FileUtil.writeToStream(requestStream, writer)
        writer.flush() // Important! Output cannot be closed. Close of writer will close output as well.
      } finally {
        Option(requestStream).foreach(s => try { s.close() } catch { case e: IOException => })
      }
      writer.flush()

      // response part
      var responseStream: InputStream = null
      val response = try {
        responseStream = new BufferedInputStream(connection.getInputStream())
        val responseStreamReader = new BufferedReader(new InputStreamReader(responseStream))
        val responseBuilder = new StringBuilder()
        var responseLine = responseStreamReader.readLine()
        while (responseLine != null) {
          responseBuilder.append(responseLine).append("\n")
          responseLine = responseStreamReader.readLine()
        }
        responseStreamReader.close()
        responseBuilder.toString()
      } finally {
        Option(responseStream).foreach(s => try { s.close() } catch { case e: IOException => })
      }
      log.debug("file uploaded, close connection")
      true
    } catch {
      case e: Throwable =>
        log.error(s"unable to upload ${file.getName}: " + e)
        false
    } finally {
      writer.flush
      writer.close
    }
  }
}
/**
 * Application act as web server ;-) strictly within Google OAuth2 draft10 manual, Ezh
 */
object Storage extends Loggable {
  def upload() {
    log.debug("upload report")
    Main.execNGet {
      val customShell = new Shell(Main.display, SWT.NO_TRIM | SWT.ON_TOP)
      try {
        val dialog = new ProgressMonitorDialog(customShell)
        dialog.run(true, true, new UploadOperation)
      } catch {
        case e: InvocationTargetException =>
          val message = Option(e.getMessage()) getOrElse ""
          val causeMessage = Option(e.getCause()).map(_.getMessage) getOrElse ""
          if (message.trim().nonEmpty)
            MessageDialog.openError(customShell, Messages.error_text, message)
          else if (causeMessage.trim().nonEmpty)
            MessageDialog.openError(customShell, Messages.error_text, causeMessage)
          else if (Option(e.getCause()).nonEmpty)
            MessageDialog.openError(customShell, Messages.error_text, e.getCause().toString)
          else
            MessageDialog.openError(customShell, Messages.error_text, e.toString)
        case e: InterruptedException =>
          MessageDialog.openInformation(customShell, Messages.cancelled_text, e.getMessage())
      }
    }
  }

  /**
   * Storage interface
   */
  trait Interface {
    /** Prepare storage for uploading */
    def prepare(monitor: IProgressMonitor)
    /** Upload file to storage */
    def upload(file: Seq[File], monitor: IProgressMonitor)
  }
  /**
   * This class represents a long running upload operation
   */
  class UploadOperation extends IRunnableWithProgress {
    val reportSeq = Report.path.listFiles()
    val nThreads = 4
    /**
     * Runs the long running operation
     *
     * @param monitor the progress monitor
     */
    def run(monitor: IProgressMonitor) {
      monitor.beginTask(Messages.errorReportUploadTitle_text, reportSeq.length + 1)
      Storage.DI.implementation.prepare(monitor)
      Storage.DI.implementation.upload(reportSeq, monitor)
      monitor.done()
      if (monitor.isCanceled())
        throw new InterruptedException(Messages.errorReportUploadCancelled_text)
    }
  }
  /**
   * Dependency injection routines
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    implicit def bindingModule = DependencyInjection()
    /** Implementation DI cache */
    @volatile var implementation = inject[Interface]

    override def injectionAfter(newModule: BindingModule) {
      implementation = inject[Interface]
    }
  }
}
