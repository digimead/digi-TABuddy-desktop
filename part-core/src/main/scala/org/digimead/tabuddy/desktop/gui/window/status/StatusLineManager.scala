package org.digimead.tabuddy.desktop.gui.window.status

import org.eclipse.jface.action.{ StatusLineManager => JStatusLineManager }
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Control
import org.eclipse.swt.SWT
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.widgets.Text
import org.eclipse.jface.fieldassist.AutoCompleteField
import org.eclipse.jface.fieldassist.TextContentAdapter
import org.eclipse.core.runtime.IProgressMonitor
import org.digimead.tabuddy.desktop.Core
import org.eclipse.e4.core.contexts.RunAndTrack
import org.eclipse.e4.core.contexts.IEclipseContext
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.command.Command
import org.eclipse.jface.fieldassist.ContentProposalAdapter
import org.digimead.tabuddy.desktop.command.Parsers
import java.util.UUID

/**
 * Composite status manager for WComposite
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
    val controlContentAdapter = new TextContentAdapter()
    val adapter = new ContentProposalAdapter(textField, controlContentAdapter, proposalProvider, null, null)
    adapter.setPropagateKeys(true)
    adapter.setProposalAcceptanceStyle(ContentProposalAdapter.PROPOSAL_REPLACE)
    //new AutoCompleteField(textField, new TextContentAdapter(),
    //  Array[String]("autocomplete option 1", "autocomplete option 2"))
    //"name" | "toys" | "tolerance"
    //val command1 = Command.commandParser("nameCmd", "nameDesc", () => log.___gaze("NAME+")) { implicit description =>
    //  description.parser.literal("name")
    //}
    //val command2 = Command.commandParser("nameCmd2", "nameDesc2", () => log.___gaze("NAME2+")) { implicit description =>
    //  description.parser.literal("name2")
    //}
    //z.literal("name") ^^ { x => log.___glance("NAME"); x }
    //val command2 = z.literal("name2") ^^ { x => log.___glance("NAME2"); x }
    //val command3 = z.literal("aa") ^^ { x => log.___glance("AA"); x }
    //val q = command1 | command2 | command3

    /*val command1 = {
      import Command.parser._
      implicit val id = UUID.randomUUID
      val parser: Parser[Any] = "name"
      new CommandParser(parser)
    }
    val command2 = {
      import Command.parser._
      implicit val id = UUID.randomUUID
      val parser: Parser[Any] = "name2"
      new CommandParser(parser)
    }
    Command.parse(command1 | command2, "name") match {
      case y: Command.Success => log.___glance("OK" + y.commandId + y.result)
      case x => log.___glance("!!!" + x)
    }*/
    //log.___glance("a" + a)
    textField
  }
}

object StatusLineManager extends Loggable {

}
