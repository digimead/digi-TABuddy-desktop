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

package org.digimead.tabuddy.desktop.ui.widget.status

import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.Core
import org.digimead.tabuddy.desktop.core.definition.command.Command
import org.digimead.tabuddy.desktop.core.support.App
import org.eclipse.core.databinding.observable.{ ChangeEvent, IChangeListener }
import org.eclipse.core.databinding.observable.value.IObservableValue
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jface.action.{ StatusLineManager ⇒ JStatusLineManager }
import org.eclipse.jface.databinding.swt.SWTObservables
import org.eclipse.jface.fieldassist.{ ContentProposalAdapter, TextContentAdapter }
import org.eclipse.swt.SWT
import org.eclipse.swt.events.{ TraverseEvent, TraverseListener }
import org.eclipse.swt.layout.{ GridData, GridLayout }
import org.eclipse.swt.widgets.{ Composite, Control, Text }
import scala.concurrent.future

/**
 * Composite status manager for AppWindow
 */
class StatusLineManager extends JStatusLineManager with Loggable {
  /** The status line control; <code>null</code> before creation and after disposal. */
  protected var statusLineContainer: Composite = null
  /** The command line control; <code>null</code> before creation and after disposal. */
  protected var commandLine: Text = null

  /** Creates and returns this manager's status line control. */
  override def createControl(parent: Composite, style: Int): Control = {
    statusLineContainer = new Composite(parent, SWT.NONE)
    statusLineContainer.setLayout(new GridLayout(2, false))
    commandLine = createCommandLine(statusLineContainer)
    // This is critical. Without setFocus SWT lost FocusIn and FocusOut events. This is INITIAL window element that gains focus.
    commandLine.setFocus()
    val statusLine = super.createControl(statusLineContainer, SWT.NONE)
    statusLine.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1))
    statusLineContainer
  }
  /**
   * Disposes of this status line manager and frees all allocated SWT resources.
   * Notifies all contribution items of the dispose. Note that this method does
   * not clean up references between this status line manager and its associated
   * contribution items. Use <code>removeAll</code> for that purpose.
   */
  override def dispose() {
    super.dispose
    commandLine.dispose()
    commandLine = null
    statusLineContainer.dispose()
    statusLineContainer = null
  }
  /** Returns the control used by this StatusLineManager. */
  override def getControl(): Control = statusLineContainer
  /** Returns the status line control. */
  def getStatusLine: Composite with IProgressMonitor =
    Option(statusLineContainer).map(_.getChildren().last).getOrElse(null).asInstanceOf[Composite with IProgressMonitor]
  /** Returns the command line control. */
  def getCommandLine: Text = commandLine

  protected def createCommandLine(parent: Composite): Text = {
    val textField = new Text(statusLineContainer, SWT.NONE)
    val textFieldLayoutData = new GridData()
    textFieldLayoutData.widthHint = 200
    textField.setLayoutData(textFieldLayoutData)
    val proposalProvider = Command.getProposalProvider()
    SWTObservables.observeText(textField, SWT.Modify).addChangeListener(new IChangeListener() {
      override def handleChange(event: ChangeEvent) =
        proposalProvider.setInput(event.getObservable().asInstanceOf[IObservableValue].getValue().asInstanceOf[String])
    })
    textField.addTraverseListener(new TraverseListener {
      def keyTraversed(event: TraverseEvent) {
        if (event.detail == SWT.TRAVERSE_RETURN) {
          Command.parse(event.widget.asInstanceOf[Text].getText()) match {
            case Command.Success(uniqueId, result) ⇒
              Command.getContextParserInfo(uniqueId) match {
                case Some(info) ⇒
                  Command.getDescriptor(info.parserId) match {
                    case Some(commandDescriptor) ⇒
                      val activeContext = Core.context.getActiveLeaf()
                      textField.setText("")
                      implicit val ec = App.system.dispatcher
                      future {
                        log.info(s"Execute command '${commandDescriptor.name}' within context '${info.context}' with argument: " + result)
                        commandDescriptor.callback(activeContext, info.context, result)
                      }
                    case None ⇒
                      log.fatal("Unable to find command description for " + info)
                  }
                case None ⇒
                  log.fatal("Unable to find command information for unique Id " + uniqueId)
              }
            case Command.MissingCompletionOrFailure(completionList, message) ⇒
              log.debug("Autocomplete: " + message)
            case Command.Failure(message) ⇒
              log.debug(message)
            case Command.Error(message) ⇒
              log.fatal(message)
          }
        }
      }
    })
    val controlContentAdapter = new TextContentAdapter()
    val adapter = new ContentProposalAdapter(textField, controlContentAdapter, proposalProvider, null, null)
    adapter.setPropagateKeys(true)
    adapter.setProposalAcceptanceStyle(ContentProposalAdapter.PROPOSAL_INSERT)
    textField
  }
}

