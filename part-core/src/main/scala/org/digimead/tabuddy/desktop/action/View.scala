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

package org.digimead.tabuddy.desktop.action

import java.util.UUID

import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.command.Command
import org.digimead.tabuddy.desktop.gui
import org.digimead.tabuddy.desktop.gui.window.WComposite
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.App.app2implementation
import org.eclipse.e4.core.internal.contexts.EclipseContext
import org.eclipse.jface.action.Action

import akka.actor.actorRef2Scala

/** Show the specific view. */
object View extends Action("view") with Loggable {
  import Command.parser._
  /** Command description. */
  implicit lazy val descriptor = Command.Descriptor(UUID.randomUUID())("view", "Create the specific view.",
    (activeContext, parserContext, parserResult) => parserResult match {
      case viewFactory: gui.ViewLayer.Factory => show(activeContext, viewFactory)
      case unknown => log.fatal(s"Unknown parser result: ${unknown.getClass}/${unknown}.")
    })

  /** Command parser. */
  def parser(viewFactory: gui.ViewLayer.Factory) = Command.CmdParser("view" ~ "." ~> viewNameParser(viewFactory))
  /** Create parser for the specific view factory. */
  protected def viewNameParser(viewFactory: gui.ViewLayer.Factory): Command.parser.Parser[Any] =
    commandLiteral(viewFactory.name, CompletionHint(viewFactory.name,
      Some(viewFactory.description))) ^^ { result => viewFactory }
  @log
  override def run = log.___glance("SHOW")
  /** Create view from the view factory. */
  protected def show(activeContext: EclipseContext, viewFactory: gui.ViewLayer.Factory) {
    log.debug("Create new view from " + viewFactory)
    activeContext.get(gui.GUI.windowContextKey) match {
      case window: WComposite =>
        window.ref ! App.Message.Create(viewFactory, App.system.deadLetters)
      case unknwon =>
        log.fatal(s"Unable to find active window for ${this}: '${activeContext}', '${viewFactory}'.")
    }
  }
}
