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

import java.io.File
import java.net.URI
import java.util.{ Date, UUID }
import org.digimead.tabuddy.desktop.core.command.URIParser
import org.digimead.tabuddy.desktop.core.console.Console
import org.digimead.tabuddy.desktop.core.definition.command.Command
import org.digimead.tabuddy.desktop.core.definition.command.api.XCommand
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.logic.{ Logic, Messages }
import org.digimead.tabuddy.desktop.logic.payload.marker.GraphMarker
import org.digimead.tabuddy.model.element.Element
import org.digimead.tabuddy.model.serialization.{ Serialization, digest }
import org.digimead.tabuddy.model.serialization.digest.Digest
import org.digimead.tabuddy.model.serialization.signature
import org.digimead.tabuddy.model.serialization.signature.Signature
import org.digimead.tabuddy.model.serialization.transport.Transport
import scala.concurrent.Future

/**
 * Show graph history.
 */
object CommandGraphHistory {
  import Command.parser._
  /** Akka execution context. */
  implicit lazy val ec = App.system.dispatcher
  /** Console converter. */
  lazy val converter: PartialFunction[(XCommand.Descriptor, Any), String] = {
    case (this.descriptor, (true, graphMarkers @ Seq(_*))) ⇒
      graphMarkers.asInstanceOf[Seq[GraphMarker]].filter(_.markerIsValid).sortBy(_.graphModelId.name).sortBy(_.graphOrigin.name).map { marker ⇒
        s"${Console.BWHITE}${marker.graphOrigin.name}${Console.RESET} " +
          s"${Console.BWHITE}${marker.graphModelId.name}${Console.RESET} " +
          s"${Console.BWHITE}${marker.uuid}${Console.RESET} binded to ${GraphMarker.markerToContext(marker).mkString(", ")}"
      }.mkString("\n")
    case (this.descriptor, records: Iterable[_]) ⇒
      records.asInstanceOf[Iterable[(Element.Timestamp, Map[URI, (Option[digest.Mechanism.Parameters], Option[signature.Mechanism.Parameters])])]].
        toSeq.sortBy(_._1)(Ordering[Element.Timestamp].reverse) match {
          case Nil ⇒
            s"${Console.BYELLOW}There are no history records.${Console.RESET}"
          case sorted ⇒
            sorted.map {
              case (record, map) ⇒
                val builder = new StringBuilder()
                builder ++= s"${Console.BWHITE}${new Date(record.milliseconds)} [${record}]${Console.RESET}\n"
                map.toSeq.sortBy(_._1).foreach {
                  case (uri, (digest, signature)) ⇒
                    builder ++= s"    ${uri}: ${digest getOrElse "no digest"}, ${signature getOrElse "no signature"}"
                }
                builder.result
            }.mkString("\n")
        }
  }
  /** Command description. */
  implicit lazy val descriptor = Command.Descriptor(UUID.randomUUID())(Messages.graph_history_text,
    Messages.graph_historyDescriptionShort_text, Messages.graph_historyDescriptionLong_text,
    (activeContext, parserContext, parserResult) ⇒ Future {
      parserResult match {
        case (Some(marker: GraphMarker), _, _) ⇒
          { try Option(marker.graphAcquireLoader()) catch { case e: Throwable ⇒ None } } match {
            case Some(loader) ⇒
              getHistory(loader)
            case None ⇒
              val graphHistory = marker.safeRead(_.graph).retrospective.history
              val records = graphHistory.keys
              records.map { record ⇒
                (record, Map())
              }
          }
        case uri: URI ⇒
          if (uri.getScheme() == "file") {
            val graphPath = new File(uri).getAbsoluteFile()
            GraphMarker.list().map(GraphMarker(_)).find(_.graphPath.getAbsoluteFile() == graphPath) match {
              case Some(marker) ⇒
                { try marker.graphAcquireLoader() catch { case e: Throwable ⇒ e.getMessage() } } match {
                  case loader: Serialization.Loader ⇒
                    getHistory(loader)
                  case error ⇒
                    "Unable to get graph history: " + error
                }
              case None ⇒
                { try Serialization.acquireLoader(graphPath.toURI) catch { case e: Throwable ⇒ e.getMessage() } } match {
                  case loader: Serialization.Loader ⇒
                    getHistory(loader)
                  case error ⇒
                    "Unable to acquire graph history: " + error
                }
            }
          } else {
            { try Serialization.acquireLoader(uri) catch { case e: Throwable ⇒ e.getMessage() } } match {
              case loader: Serialization.Loader ⇒
                getHistory(loader)
              case error ⇒
                "Unable to acquire graph history: " + error
            }
          }
      }
    })
  /** Command parser. */
  lazy val parser = Command.CmdParser(descriptor.name ~ sp ~> (uriParser | graphParser))

  /** Get history from loader. */
  def getHistory(loader: Serialization.Loader): Iterable[(Element.Timestamp, Map[URI, (Option[digest.Mechanism.Parameters], Option[signature.Mechanism.Parameters])])] = {
    val loaderHistory = loader.history
    val digestHistory = Digest.history(loader)
    val signatureHistory = Signature.history(loader)
    val records = loaderHistory.keys
    records.map { record ⇒
      (record, Map(loaderHistory(record).toSeq.map { uri ⇒
        uri -> (digestHistory.get(record).flatMap(_.get(uri)), signatureHistory.get(record).flatMap(_.get(uri)))
      }: _*))
    }
  }
  /** Graph argument parser. */
  def graphParser = GraphParser(() ⇒ GraphMarker.list().map(GraphMarker(_)).
    filter(m ⇒ m.markerIsValid).sortBy(_.graphModelId.name).sortBy(_.graphOrigin.name))
  /** URI argument parser. */
  def uriParser = URIParser(() ⇒ Logic.graphContainer.toURI(),
    () ⇒ "location", () ⇒ Some(s"path to the graph")) { (uri: URI, transport: Transport) ⇒
      transport.isDirectory(uri) == Some(true)
    }
}
