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
import java.util.concurrent.{ CancellationException, Exchanger }
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.console.Console
import org.digimead.tabuddy.desktop.core.definition.Operation
import org.digimead.tabuddy.desktop.core.definition.command.Command
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.logic.Messages
import org.digimead.tabuddy.desktop.logic.operation.OperationGraphOpen
import org.digimead.tabuddy.desktop.logic.payload.maker.GraphMarker
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.graph.Graph
import org.eclipse.core.runtime.jobs.Job
import scala.concurrent.Future
import scala.util.DynamicVariable

object CommandGraphOpen extends Loggable {
  import Command.parser._
  /** Akka execution context. */
  implicit lazy val ec = App.system.dispatcher
  /** Command description. */
  implicit lazy val descriptor = Command.Descriptor(UUID.randomUUID())(Messages.graph_open_text,
    Messages.graph_openDescriptionShort_text, Messages.graph_openDescriptionLong_text,
    (activeContext, parserContext, parserResult) ⇒ Future {
      val markers = GraphMarker.list().map(GraphMarker(_)).filterNot(_.graphIsOpen())
      val (marker, name, uuid, origin) = parserResult match {
        case ~(~(~(name: String, Some(uuid: String)), _), origin: String) ⇒
          (markers.find(m ⇒ m.graphModelId.name == name && m.uuid.toString().toLowerCase() == uuid && m.graphOrigin.name == origin), name, uuid, origin)
        case ~(~(~(name: String, None), _), origin: String) ⇒
          (markers.find(m ⇒ m.graphModelId.name == name && m.graphOrigin.name == origin), name, "*", origin)
        case ~(~(uuid: String, _), origin: String) ⇒
          (markers.find(m ⇒ m.uuid.toString().toLowerCase() == uuid && m.graphOrigin.name == origin), "*", uuid, origin)
      }
      marker match {
        case Some(marker) ⇒
          val exchanger = new Exchanger[Operation.Result[Graph[_ <: Model.Like]]]()
          OperationGraphOpen(marker.uuid).foreach { operation ⇒
            operation.getExecuteJob() match {
              case Some(job) ⇒
                job.setPriority(Job.LONG)
                job.onComplete(exchanger.exchange).schedule()
              case None ⇒
                log.fatal(s"Unable to create job for ${operation}.")
            }
          }
          exchanger.exchange(null) match {
            case Operation.Result.OK(result, message) ⇒
              log.info(s"Operation completed successfully.")
              result.map(graph ⇒ GraphMarker.bind(GraphMarker(graph)))
              result
            case Operation.Result.Cancel(message) ⇒
              throw new CancellationException(s"Operation canceled, reason: ${message}.")
            case other ⇒
              throw new RuntimeException(s"Unable to complete operation: ${other}.")
          }
        case None ⇒
          Console.msgWarning.format(s"Graph '${name}#${uuid}@${origin}' not found.") + Console.RESET
      }
    })
  /** UUID delimiter. */
  lazy val uuidLiteral = commandLiteral("#", Command.Hint("#", Some("for graph UUID. Actually, for graph marker UUID, but in most cases they are the same.")))
  /** UUID mark. */
  lazy val uuidLiteralMark = commandLiteral("*#", Command.Hint("any name", Some("graph UUID. Actually, graph marker UUID, but in most cases they are the same.")))
  /** Origin delimiter. */
  lazy val originLiteral = commandLiteral("@", Command.Hint("@", Some("for graph origin")))
  /** Command parser. */
  lazy val parser = Command.CmdParser(descriptor.name ~ (sp ^^ { _ ⇒
    // clear thread local values at the beginning
    localGraphUUID.value = None
    localGraphName.value = None
  }) ~> (graphNameParser ~ opt(uuidLiteral ~> graphUUIDParser) | (uuidLiteralMark ~> graphUUIDParser)) ~ originLiteral ~ graphOriginParser)
  /** Thread local cache with current graph name. */
  protected val localGraphName = new DynamicVariable[Option[String]](None)
  /** Thread local cache with current graph UUID. */
  protected val localGraphUUID = new DynamicVariable[Option[String]](None)

  /** Create parser for the graph name. */
  protected def graphNameParser: Command.parser.Parser[Any] =
    commandRegex(s"${App.symbolPatternDefinition()}+${App.symbolPatternDefinition("_")}*".r, NameHintContainer) ^^ { name ⇒
      GraphMarker.list().map(GraphMarker(_)).find(m ⇒ !m.graphIsOpen() && m.graphModelId.name == name) getOrElse {
        throw Command.ParseException(s"Graph marker with name '$name' not found.")
      }
      // save result for further parsers
      localGraphName.value = Some(name)
      name
    }
  /** Create parser for the graph origin. */
  protected def graphOriginParser: Command.parser.Parser[Any] =
    commandRegex(s"${App.symbolPatternDefinition()}+${App.symbolPatternDefinition("_")}*".r, OriginHintContainer)
  /** Create parser for the graph marker UUID. */
  protected def graphUUIDParser: Command.parser.Parser[Any] =
    commandRegex("[0-9A-Fa-f-]+".r, UUIDHintContainer) ^^ { uuid ⇒
      val uuidLower = uuid.toLowerCase()
      GraphMarker.list().map(GraphMarker(_)).find(m ⇒ !m.graphIsOpen() && m.uuid.toString().toLowerCase() == uuid) getOrElse {
        throw Command.ParseException(s"Graph marker with UUID '$uuidLower' not found.")
      }
      // save result for further parsers
      localGraphUUID.value = Some(uuidLower)
      uuidLower
    }

  /** Hint container for graph name (model id). */
  object NameHintContainer extends Command.Hint.Container {
    /** Get parser hints for user provided argument. */
    def apply(arg: String): Seq[Command.Hint] = {
      val markers = GraphMarker.list().map(GraphMarker(_)).filterNot(_.graphIsOpen()).sortBy(_.graphModelId.name).sortBy(_.graphOrigin.name)
      val names = markers.map(_.graphModelId.name).distinct
      names.filter(_.startsWith(arg)).map(proposal ⇒
        Command.Hint("graph name", Some(s"model id which is scala symbol literal"), Seq(proposal.drop(arg.length)))).
        filter(_.completions.head.nonEmpty)
    }
  }
  /** Hint container for graph UUID. */
  object UUIDHintContainer extends Command.Hint.Container {
    /** Get parser hints for user provided argument. */
    def apply(arg: String): Seq[Command.Hint] = {
      val lowerArg = arg.toLowerCase()
      val markers = GraphMarker.list().map(GraphMarker(_)).filterNot(_.graphIsOpen()).sortBy(_.graphModelId.name).sortBy(_.graphOrigin.name)
      val uuids = localGraphName.value match {
        case Some(name) ⇒
          markers.filter(_.graphModelId.name == name).map(_.uuid.toString())
        case None ⇒
          markers.map(_.uuid.toString())
      }
      uuids.filter(_.startsWith(lowerArg)).map(proposal ⇒
        Command.Hint("graph marker UUID", Some(s"UUID that allow distincts graphs with the same name"), Seq(proposal.drop(arg.length)))).
        filter(_.completions.head.nonEmpty)
    }
  }
  /** Hint container for graph origin. */
  object OriginHintContainer extends Command.Hint.Container {
    /** Get parser hints for user provided path. */
    def apply(arg: String): Seq[Command.Hint] = {
      val markers = GraphMarker.list().map(GraphMarker(_)).filterNot(_.graphIsOpen()).sortBy(_.graphModelId.name).sortBy(_.graphOrigin.name)
      val origins = markers.map { marker ⇒
        val name = marker.graphModelId.name
        val uuid = marker.uuid.toString().toLowerCase()
        if (localGraphName.value.map(_ == name).getOrElse(true) &&
          localGraphUUID.value.map(_.toLowerCase() == uuid).getOrElse(true))
          Some(marker.graphOrigin.name)
        else
          None
      }.flatten
      origins.filter(_.startsWith(arg)).map(proposal ⇒
        Command.Hint("origin", Some(s"scala symbol literal"), Seq(proposal.drop(arg.length)))).
        filter(_.completions.head.nonEmpty)
    }
  }
}
