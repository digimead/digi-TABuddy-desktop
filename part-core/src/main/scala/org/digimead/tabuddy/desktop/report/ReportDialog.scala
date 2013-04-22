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

import java.io.File
import java.io.PrintWriter
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean

import scala.collection.JavaConversions.asScalaSet

import org.digimead.configgy.Configgy
import org.digimead.configgy.Configgy.getImplementation
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.log.Loggable
import org.digimead.digi.lib.log.logger.RichLogger.rich2slf4j
import org.digimead.digi.lib.util.Util
import org.digimead.tabuddy.desktop.Main
import org.digimead.tabuddy.desktop.report.Report.report2implementation
import org.digimead.tabuddy.desktop.res.Messages
import org.digimead.tabuddy.desktop.support.Timeout
import org.digimead.tabuddy.desktop.support.WritableValue
import org.digimead.tabuddy.desktop.support.WritableValue.wrapper2underlying
import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.Status
import org.eclipse.jface.databinding.swt.WidgetProperties
import org.eclipse.jface.dialogs.ErrorDialog
import org.eclipse.jface.dialogs.IDialogConstants
import org.eclipse.jface.window.Window
import org.eclipse.swt.SWT
import org.eclipse.swt.events.DisposeEvent
import org.eclipse.swt.events.DisposeListener
import org.eclipse.swt.graphics.GC
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Control
import org.eclipse.swt.widgets.Label
import org.eclipse.swt.widgets.Shell
import org.eclipse.swt.widgets.Text
import org.eclipse.ui.statushandlers.AbstractStatusAreaProvider
import org.eclipse.ui.statushandlers.StatusAdapter

class ReportDialog(parentShell: Shell, dialogTitle: String, message: String, status: IStatus, displayMask: Int)
  extends ErrorDialog(parentShell, dialogTitle, message, status, displayMask) with Loggable {
  override protected def createDialogArea(parent: Composite): Control = {
    val area = super.createDialogArea(parent)
    val layout = parent.getLayout().asInstanceOf[GridLayout]
    layout.numColumns = 1
    area
  }
  override protected def createButtonsForButtonBar(parent: Composite) {
    // create OK and Details buttons
    createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL, true)
    val cancel = createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, true)
    createDetailsButton(parent)
    getShell().setDefaultButton(cancel)
  }
}

object ReportDialog extends Loggable {
  /** Constant used to indicate an unknown plugin id.   */
  private val unknownId = "unknown"; //$NON-NLS-1$
  /** User email */
  private lazy val contact = Main.execNGet {
    val value = WritableValue(Configgy.getString("user.email") getOrElse "")
    value.addChangeListener { (email, event) => if (Configgy.getString("user.email").isEmpty) Configgy.setString("user.email", email) }
    value
  }
  /** Report additional information */
  private lazy val information = Main.execNGet { WritableValue("") }

  val searchAndSubmitLock = new AtomicBoolean(false)

  @log
  def submit(description: Option[String] = None): Unit = if (Report.submitInProgressLock.compareAndSet(false, true)) {
    log.debug("lock report submit")
    val result = Main.execNGet {
      val messageDescription = description getOrElse Messages.errorReportReasonDefault_text
      val messageDetails = Messages.errorReportDetails_text.format(Report.path)
      val message = "%s\n\n%s: %s".format(Messages.errorReportDescription_text,
        Messages.reason_text, messageDescription)
      val status = new Status(IStatus.ERROR, unknownId, 1, message, new Throwable(messageDetails))
      val customShell = new Shell(Main.display, SWT.NO_TRIM | SWT.ON_TOP)
      val dialog = new ReportDialog(customShell, Messages.errorReportTitle_text,
        null, status, IStatus.OK | IStatus.INFO | IStatus.WARNING | IStatus.ERROR)
      dialog.open()
    }
    if (result == Window.OK) {
      generateDescription()
      Storage.upload()
    }
    log.debug("unlock report submit")
    Report.submitInProgressLock.set(false)
    Report.cleanAfterReview()
  }
  /** Try to submit a report if there is stack trace */
  @log
  def searchAndSubmit() = if (!Report.submitInProgressLock.get && searchAndSubmitLock.compareAndSet(false, true)) {
    log.info("looking for stack trace reports in: " + Report.path)
    Thread.sleep(Timeout.short) // take it gently ;-)
    val reports = Option(Report.path.list()).getOrElse(Array[String]())
    if (reports.exists(_.endsWith(Report.traceFileExtension)))
      submit(Some("stack trace detected"))
  }

  protected def generateDescription() = Main.execNGet {
    log.debug("generate description")
    var writer: PrintWriter = null
    try {
      val file = new File(Report.path, Report.filePrefix + ".description")
      file.createNewFile()
      writer = new PrintWriter(file)
      val time = System.currentTimeMillis
      val date = Util.dateString(new Date(time))
      writer.println("generation time: " + date)
      writer.println("generation time (long): " + time)
      writer.println("email: " + contact.value)
      writer.println("description: " + information.value)
      writer.println("\n-- listing properties --")
      val properties = System.getProperties()
      properties.stringPropertyNames().toList.sorted.foreach { name =>
        writer.println("%s=%s".format(name, properties.getProperty(name)))
      }
    } catch {
      case e: Throwable =>
        log.error(e.getMessage, e)
    } finally {
      if (writer != null)
        writer.close()
    }
  }

  object SupportProvider extends AbstractStatusAreaProvider {
    def createSupportArea(parent: Composite, statusAdapter: StatusAdapter): Control = {
      ReportDialog.information.value = ""
      val area = new Composite(parent, SWT.NONE)
      val areaData = new GridData(GridData.FILL_BOTH)
      areaData.horizontalSpan = 2
      areaData.grabExcessVerticalSpace = false
      area.setLayout(new GridLayout(2, false))
      area.setLayoutData(areaData)
      // contact
      val contactLabel = new Label(area, SWT.NONE)
      contactLabel.setText(Messages.errorReportContact_text)
      contactLabel.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false))
      val contact = new Text(area, SWT.BORDER)
      contact.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false))
      contact.setMessage(Messages.errorReportContactHint_text)
      contact.setToolTipText(Messages.errorReportContactTip_text)
      val contactBinding = Main.bindingContext.bindValue(WidgetProperties.text(SWT.Modify).observeDelayed(50, contact), ReportDialog.contact)
      contact.addDisposeListener(new DisposeListener {
        def widgetDisposed(e: DisposeEvent) = Main.bindingContext.removeBinding(contactBinding)
      })
      // info
      val infoLabel = new Label(area, SWT.NONE)
      infoLabel.setText(Messages.description_text)
      infoLabel.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false))
      val info = new Text(area, SWT.MULTI | SWT.BORDER | SWT.WRAP | SWT.V_SCROLL)
      info.setMessage(Messages.additionalInformation_text)
      info.setToolTipText(Messages.additionalInformation_text)
      val gc = new GC(info)
      val fm = gc.getFontMetrics()
      val infoGridData = new GridData(SWT.FILL, SWT.FILL, true, false)
      infoGridData.heightHint = fm.getHeight() * 6
      info.setLayoutData(infoGridData)
      gc.dispose()
      val infoBinding = Main.bindingContext.bindValue(WidgetProperties.text(SWT.Modify).observeDelayed(50, info), ReportDialog.information)
      info.addDisposeListener(new DisposeListener {
        def widgetDisposed(e: DisposeEvent) = Main.bindingContext.removeBinding(infoBinding)
      })
      parent
    }
  }
}
