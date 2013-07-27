package org.digimead.tabuddy.desktop.action

import java.util.UUID
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.Messages
import org.digimead.tabuddy.desktop.command.Command
import org.digimead.tabuddy.desktop.command.Command.parser.commandLiteral
import org.eclipse.jface.action.Action
import org.digimead.tabuddy.desktop.gui.GUI

object View extends Action("view") with Loggable {
  import Command.parser._
  /** Command description. */
  implicit lazy val description = Command.Description(UUID.randomUUID())("view", "my exit", (context, parserResult) => run)
  /** Command parser. */
  lazy val parser = Command.CmdParser("view" ~ "default")

  @log
  override def run = log.___glance("SHOW")
}