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

package org.digimead.tabuddy.desktop.logic.command.graph

import java.util.UUID
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.console.Console
import org.digimead.tabuddy.desktop.core.definition.command.Command
import org.digimead.tabuddy.desktop.core.definition.command.api.XCommand
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.logic.Messages
import org.digimead.tabuddy.desktop.logic.payload.marker.GraphMarker
import scala.concurrent.Future

/**
 * List all known graphs.
 */
object CommandGraphList extends Loggable {
  import Command.parser._
  private val bindedArg = "-binded"
  /** Akka execution context. */
  implicit lazy val ec = App.system.dispatcher
  /** Console converter. */
  lazy val converter: PartialFunction[(XCommand.Descriptor, Any), String] = {
    case (this.descriptor, (true, Seq())) ⇒
      "There are no binded graphs"
    case (this.descriptor, (false, Seq())) ⇒
      "There are no graphs"
    case (this.descriptor, (true, graphMarkers @ Seq(_*))) ⇒
      graphMarkers.asInstanceOf[Seq[GraphMarker]].filter(_.markerIsValid).sortBy(_.graphModelId.name).sortBy(_.graphOrigin.name).map { marker ⇒
        s"${Console.BWHITE}${marker.graphOrigin.name}${Console.RESET} " +
          s"${Console.BWHITE}${marker.graphModelId.name}${Console.RESET} " +
          s"${Console.BWHITE}${marker.uuid}${Console.RESET} binded to ${GraphMarker.markerToContext(marker).mkString(", ")}"
      }.mkString("\n")
    case (this.descriptor, (false, graphMarkers @ Seq(_*))) ⇒
      graphMarkers.asInstanceOf[Seq[GraphMarker]].
        sortBy(m ⇒ try m.graphModelId.name catch { case e: Throwable ⇒ "-" }).
        sortBy(m ⇒ try m.graphOrigin.name catch { case e: Throwable ⇒ "-" }).map { marker ⇒
          val state = marker match {
            case broken if !marker.markerIsValid ⇒ s"[${Console.BRED}broken${Console.RESET}]"
            case dirty if marker.graphIsOpen() && marker.graphIsDirty() ⇒ s"[${Console.BYELLOW}unsaved${Console.RESET}]"
            case opened if marker.graphIsOpen() ⇒ s"[${Console.BGREEN}opened${Console.RESET}]"
            case closed ⇒ s"[${Console.BBLACK}closed${Console.RESET}]"
          }
          s"${Console.BWHITE}${
            try marker.graphOrigin.name catch { case e: Throwable ⇒ "-" }
          }${Console.RESET} " +
            s"${Console.BWHITE}${
              try marker.graphModelId.name catch { case e: Throwable ⇒ "-" }
            }${Console.RESET} " +
            s"${Console.BWHITE}${marker.uuid}${Console.RESET} ${state} at ${marker.graphPath}"
        }.mkString("\n")
  }
  /** Command description. */
  implicit lazy val descriptor = Command.Descriptor(UUID.randomUUID())(Messages.graph_list_text,
    Messages.graph_listDescriptionShort_text, Messages.graph_listDescriptionLong_text,
    (activeContext, parserContext, parserResult) ⇒ Future[(Boolean, Seq[GraphMarker])] {
      parserResult match {
        case Some(loadedArg) ⇒
          (true, GraphMarker.list().map(GraphMarker(_)).filter(_.graphIsOpen()))
        case None ⇒
          (false, GraphMarker.list().map(GraphMarker(_)))
      }
    })
  /** Command parser. */
  lazy val parser = Command.CmdParser(descriptor.name ~> opt(sp ~> bindedArg))
}
