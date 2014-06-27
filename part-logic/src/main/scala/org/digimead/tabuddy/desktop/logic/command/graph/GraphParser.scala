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
  /** Thread local cache with current graph origin. */
  protected val localGraphOrigin = new DynamicVariable[Option[String]](None)
  /** Thread local cache with current graph UUID. */
  protected val localGraphUUID = new DynamicVariable[Option[String]](None)
  /** Thread local cache with marker list. */
  protected val localMarkers = new DynamicVariable[Seq[GraphMarker]](Seq())
  /** Origin delimiter. */
  lazy val originLiteral = commandLiteral("@", Command.Hint("@", Some("for graph origin")))
  /** UUID delimiter. */
  lazy val uuidLiteral = commandLiteral("#", Command.Hint("#", Some("for graph UUID. Actually, for graph marker UUID, but in most cases they are the same.")))
  /** UUID mark. */
  lazy val uuidLiteralMark = commandLiteral("*#", Command.Hint("any name", Some("graph UUID. Actually, graph marker UUID, but in most cases they are the same.")))

  /** Graph argument parser. */
  def apply(markerArgs: () ⇒ Seq[GraphMarker], keep: Boolean = false) = ((sp ^^ { _ ⇒
    // clear thread local values at the beginning
    threadLocalClear()
    localMarkers.value = markerArgs()

  }) ~> (graphNameParser ~ opt(uuidLiteral ~> graphUUIDParser) | (uuidLiteralMark ~> graphUUIDParser)) ~ originLiteral ~ graphOriginParser) ^^ { parserResult ⇒
    // returns marker
    val (marker, name, uuid, origin) = parserResult match {
      case ~(~(~(name: String, Some(uuid: String)), _), origin: String) ⇒
        (localMarkers.value.find(m ⇒ m.graphModelId.name == name && m.uuid.toString().toLowerCase() == uuid && m.graphOrigin.name == origin), name, uuid, origin)
      case ~(~(~(name: String, None), _), origin: String) ⇒
        (localMarkers.value.find(m ⇒ m.graphModelId.name == name && m.graphOrigin.name == origin), name, "*", origin)
      case ~(~(uuid: String, _), origin: String) ⇒
        (localMarkers.value.find(m ⇒ m.uuid.toString().toLowerCase() == uuid && m.graphOrigin.name == origin), "*", uuid, origin)
    }
    if (keep) {
      localGraphMarker.value = marker
      localGraphName.value = Some(name)
      localGraphOrigin.value = Some(origin)
      localGraphUUID.value = Some(uuid)
    } else
      threadLocalClear()
    (marker, name, uuid, origin)
  }
  /** Get thread local value of the graph marker. */
  def threadLocalGraphMarker = localGraphMarker.value
  /** Get thread local value of the graph name. */
  def threadLocalGraphName = localGraphName.value
  /** Get thread local value of the graph origin. */
  def threadLocalGraphOrigin = localGraphOrigin.value
  /** Get thread local value of the graph UUID. */
  def threadLocalGraphUUID = localGraphUUID.value
  /** Clear thread local values. */
  def threadLocalClear() = {
    localGraphMarker.value = None
    localGraphName.value = None
    localGraphOrigin.value = None
    localGraphUUID.value = None
    localMarkers.value = Seq()
  }

  /** Create parser for the graph name. */
  protected def graphNameParser: Command.parser.Parser[Any] =
    commandRegex(s"${App.symbolPatternDefinition()}+${App.symbolPatternDefinition("_")}*".r, NameHintContainer) ^^ { name ⇒
      localMarkers.value.find(_.graphModelId.name == name) getOrElse {
        threadLocalClear()
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
      localMarkers.value.find(_.uuid.toString().toLowerCase() == uuid) getOrElse {
        threadLocalClear()
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
      val names = localMarkers.value.map(_.graphModelId.name).distinct
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
      val uuids = localGraphName.value match {
        case Some(name) ⇒
          localMarkers.value.filter(_.graphModelId.name == name).map(_.uuid.toString())
        case None ⇒
          localMarkers.value.map(_.uuid.toString())
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
      val origins = localMarkers.value.map { marker ⇒
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
