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

package org.digimead.tabuddy.desktop.logic.ui.dialog

import javax.inject.Inject
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.ui.definition.Dialog
import org.eclipse.e4.core.contexts.IEclipseContext
import org.eclipse.swt.custom.StackLayout
import org.eclipse.swt.events.{ DisposeEvent, DisposeListener, FocusEvent, FocusListener, ShellAdapter, ShellEvent }
import org.eclipse.swt.widgets.{ Composite, Control, Shell }
import org.eclipse.swt.dnd.DropTarget
import org.eclipse.swt.dnd.DND
import org.eclipse.swt.dnd.Transfer
import org.eclipse.swt.dnd.TextTransfer
import org.eclipse.swt.dnd.DropTargetAdapter
import org.eclipse.swt.dnd.DropTargetEvent
import org.eclipse.swt.dnd.URLTransfer
import org.eclipse.swt.dnd.FileTransfer
import java.io.File
import java.net.URL
import java.io.IOException
import java.net.URI
import org.digimead.tabuddy.desktop.core.ui.view.Loading
import org.eclipse.swt.SWT
import org.digimead.digi.lib.jfx4swt.JFX
import org.digimead.tabuddy.desktop.logic.operation.graph.OperationGraphInfo

/**
 * Graph import dialog.
 */
class GraphImportDialog @Inject() (
  /** This dialog context. */
  val context: IEclipseContext,
  /** Parent shell. */
  val parentShell: Shell) extends GraphImportDialogSkel(parentShell) with Dialog with Loggable {
  /** File transfer instance. */
  lazy val fileTransfer = FileTransfer.getInstance()
  /** Text transfer instance. */
  lazy val textTransfer = TextTransfer.getInstance()
  /** URL transfer instance. */
  lazy val urlTransfer = URLTransfer.getInstance()

  /** Create contents of the dialog. */
  override protected def createDialogArea(parent: Composite): Control = {
    val result = super.createDialogArea(parent)
    context.set(classOf[Composite], parent)

    configureCompositeEmpty()

    getContainer().getLayout().asInstanceOf[StackLayout].topControl = getCompositeEmpty()
    getContainer().layout()

    getShell().addShellListener(ShellContextActivator)
    getShell().addFocusListener(FocusContextActivator)
    // Add the dispose listener
    getShell().addDisposeListener(new DisposeListener {
      def widgetDisposed(e: DisposeEvent) {
        getShell().removeFocusListener(FocusContextActivator)
        getShell().removeShellListener(ShellContextActivator)
      }
    })
    result
  }
  protected def configureCompositeEmpty() {
    val composite = getCompositeEmpty()

    val dt = new DropTarget(composite, DND.DROP_DEFAULT | DND.DROP_COPY | DND.DROP_MOVE | DND.DROP_LINK)
    dt.setTransfer(Array[Transfer](textTransfer, urlTransfer, fileTransfer))
    dt.addDropListener(new DropTargetAdapter() {
      override def dragEnter(event: DropTargetEvent) =
        if (event.detail == DND.DROP_DEFAULT) event.detail = DND.DROP_COPY
      override def dragOperationChanged(event: DropTargetEvent) =
        if (event.detail == DND.DROP_DEFAULT) event.detail = DND.DROP_COPY
      override def drop(event: DropTargetEvent) {
        // Get data
        val data = event.data match {
          case string: String ⇒ Option(string)
          case arrayOfString: Array[String] ⇒ arrayOfString.headOption
          case _ ⇒ None
        }
        // Get URI
        val uri = try data.map(data ⇒ if (fileTransfer.isSupportedType(event.currentDataType)) {
          new File(data).toURI()
        } else if (urlTransfer.isSupportedType(event.currentDataType)) {
          new URL(data).toURI()
        } else {
          try {
            val file = new File(data)
            if (!file.exists())
              throw new IllegalArgumentException("Not a file.")
          } catch {
            case e: Throwable ⇒
              try new URL(data).toURI()
              catch { case e: Throwable ⇒ new URI(data) }
          }
        }) catch {
          case e: Throwable ⇒
            log.debug(s"Unable to parse data '${data}': ${e.getMessage()}.")
            None
        }
        // Assign
        uri.foreach { uri ⇒
          // Set loading
          val loading = new Loading(getContainer(), SWT.NONE)
          getContainer().getLayout().asInstanceOf[StackLayout].topControl = loading
          loading.initializeSWT()
          JFX.exec { loading.initializeJFX() }
          getContainer().layout()
          // Set
          getTextURI().setText(uri.toString())
          getTextURI().setEditable(false)
          getBtnLocal().setEnabled(false)
          // Get graph info
          //OperationGraphInfo(uri)
        }
      }
    })
  }

  /** Activate context on focus. */
  object FocusContextActivator extends FocusListener() {
    def focusGained(e: FocusEvent) = context.activateBranch()
    def focusLost(e: FocusEvent) {}
  }
  /** Activate context on shell events. */
  object ShellContextActivator extends ShellAdapter() {
    override def shellActivated(e: ShellEvent) = context.activateBranch()
  }
}
