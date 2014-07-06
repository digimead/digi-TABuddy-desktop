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

import org.digimead.digi.lib.api.XDependencyInjection
import org.digimead.tabuddy.desktop.core.definition.command.Command
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.logic.payload.marker.GraphMarker
import scala.language.implicitConversions
import scala.util.DynamicVariable

/**
 * Graph argument parser builder.
 */
class GraphParser {
  import Command.parser._
  /** Thread local cache with current graph marker. */
  protected val localGraphMarker = new DynamicVariable[Option[GraphMarker]](None)
  /** Thread local cache with current graph name. */
  protected val localGraphName = new DynamicVariable[Option[String]](None)
  /** Thread local cache with current graph UUID. */
  protected val localGraphUUID = new DynamicVariable[Option[String]](None)
  /** Thread local cache with marker list. */
  protected val localMarkers = new DynamicVariable[Seq[GraphMarker]](Seq())
  /** UUID delimiter. */
  lazy val uuidLiteral = commandLiteral("#", Command.Hint("#", Some("Graph UUID. Actually, marker UUID, but in most cases they are the same.")))
  /** UUID mark. */
  lazy val uuidLiteralMark = commandLiteral("*#", Command.Hint("any name", Some("Graph UUID. Actually, graph marker UUID, but in most cases they are the same.")))

  /** Graph argument parser. */
  def apply(markerArgs: () ⇒ Seq[GraphMarker], keep: Boolean = false) = (nop ^^ { _ ⇒
    // clear thread local values at the beginning
    threadLocalClear()
    localMarkers.value = markerArgs()
  }) ~>
    ((graphNameParser ~ opt(uuidLiteral ~> graphUUIDParser)) | (uuidLiteralMark ~> graphUUIDParser)) ^^ { parserResult ⇒
      val (marker, name, uuid) = parserResult match {
        case ~(id: String, Some(uuid: String)) ⇒
          (localMarkers.value.find(m ⇒ m.graphModelId.name == id && m.uuid.toString().toLowerCase() == uuid), id, uuid)
        case ~(id: String, None) ⇒
          (localMarkers.value.find(m ⇒ m.graphModelId.name == id), id, "*")
        case uuid: String ⇒
          (localMarkers.value.find(m ⇒ m.uuid.toString().toLowerCase() == uuid), "*", uuid)
      }

      if (keep) {
        localGraphMarker.value = marker
        localGraphName.value = Some(name)
        localGraphUUID.value = Some(uuid)
      } else
        threadLocalClear()
      (marker, name, uuid)
    }
  /** Get thread local value of the graph marker. */
  def threadLocalGraphMarker = localGraphMarker.value
  /** Get thread local value of the graph name. */
  def threadLocalGraphName = localGraphName.value
  /** Get thread local value of the graph UUID. */
  def threadLocalGraphUUID = localGraphUUID.value
  /** Clear thread local values. */
  def threadLocalClear() = {
    localGraphMarker.value = None
    localGraphName.value = None
    localGraphUUID.value = None
    localMarkers.value = Seq()
  }

  /** Create parser for the graph name. */
  protected def graphNameParser: Command.parser.Parser[Any] =
    (commandRegex(s"${App.symbolPatternDefinition()}+${App.symbolPatternDefinition("_")}*".r, NameHintContainer) ^? {
      case CompletionRequest(name) if localMarkers.value.exists(_.graphModelId.name == name) ⇒
        localGraphName.value = Some(name)
        name
      case name if localMarkers.value.exists(_.graphModelId.name == name) ⇒
        localMarkers.value.find(_.graphModelId.name == name) getOrElse {
          threadLocalClear()
          throw Command.ParseException(s"Graph marker with name '$name' is not available.")
        }
        // save result for further parsers
        localGraphName.value = Some(name)
        name
    })
  /** Create parser for the graph marker UUID. */
  protected def graphUUIDParser: Command.parser.Parser[Any] =
    commandRegex("[0-9A-Fa-f-]+".r, UUIDHintContainer) ^? {
      case CompletionRequest(uuid) if localMarkers.value.exists(_.uuid.toString().toLowerCase() == uuid) ⇒
        val uuidLower = uuid.toLowerCase()
        localGraphUUID.value = Some(uuidLower)
        uuid
      case uuid if localMarkers.value.exists(_.uuid.toString().toLowerCase() == uuid) ⇒
        val uuidLower = uuid.toLowerCase()
        localMarkers.value.find(_.uuid.toString().toLowerCase() == uuid) getOrElse {
          threadLocalClear()
          throw Command.ParseException(s"Graph marker with UUID '$uuidLower' not available.")
        }
        // save result for further parsers
        localGraphUUID.value = Some(uuidLower)
        uuidLower
    }

  /** Hint container for graph name (model id). */
  object NameHintContainer extends Command.Hint.Container {
    /** Get parser hints for user provided argument. */
    def apply(arg: String): Seq[Command.Hint] = {
      val names = localMarkers.value.map(_.graphModelId.name).distinct
      names.filter(_.startsWith(arg)).map(proposal ⇒
        Command.Hint("graph name", Some(s"Model id which is scala symbol literal"), Seq(proposal.drop(arg.length)))).
        filter(_.completions.head.nonEmpty)
    }
  }
  /** Hint container for graph UUID. */
  object UUIDHintContainer extends Command.Hint.Container {
    /** Get parser hints for user provided argument. */
    def apply(arg: String): Seq[Command.Hint] = {
      val lowerArg = arg.toLowerCase()
      val uuids = localGraphName.value match {
        case Some(name) ⇒
          localMarkers.value.filter(_.graphModelId.name == name).map(_.uuid.toString())
        case None ⇒
          localMarkers.value.map(_.uuid.toString())
      }
      uuids.filter(_.startsWith(lowerArg)).map(proposal ⇒
        Command.Hint("graph marker UUID", Some(s"UUID that allow distinct graphs with the same name"), Seq(proposal.drop(arg.length)))).
        filter(_.completions.head.nonEmpty)
    }
  }
}

object GraphParser {
  implicit def parser2implementation(c: GraphParser.type): GraphParser = c.inner

  def inner = DI.implementation

  /**
   * Dependency injection routines
   */
  private object DI extends XDependencyInjection.PersistentInjectable {
    /** GraphParser implementation. */
    lazy val implementation = injectOptional[GraphParser] getOrElse new GraphParser
  }
}
