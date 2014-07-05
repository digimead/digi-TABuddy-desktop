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

package org.digimead.tabuddy.desktop.logic.command

import org.digimead.digi.lib.api.XDependencyInjection
import org.digimead.tabuddy.desktop.core.definition.command.Command
import org.digimead.tabuddy.desktop.logic.payload.Payload
import org.digimead.tabuddy.model.serialization.Serialization
import scala.language.implicitConversions

/**
 * Parser builder for serialization type argument.
 */
class SerializationTypeParser {
  import Command.parser._
  /** Set of valid digest identifiers. */
  lazy val validIdentifiers = Payload.availableSerialization

  /** Create parser for the digest configuration. */
  def apply(tag: String = ""): Command.parser.Parser[Any] =
    sp ~> commandRegex("\\w+".r, NameHintContainer) ^? {
      case CompletionRequest(extension) ⇒
        validIdentifiers.find(_.extension.name.toUpperCase() == extension).map(_.extension.name) getOrElse { extension }
      case extension ⇒
        validIdentifiers.find(_.extension.name.toUpperCase() == extension).map(_.extension.name) getOrElse {
          throw new Command.ParseException(s"Serialization mechanism with extension '$extension' not found.")
        }
    } ^^ (extension ⇒ SerializationTypeParser.Argument(tag, validIdentifiers.find(_.extension.name == extension).getOrElse {
      throw new Command.ParseException(s"Serialization mechanism with extension '$extension' not found.")
    }))

  /** Hint container for digest mechanism name. */
  object NameHintContainer extends Command.Hint.Container {
    /** Get parser hints for user provided argument. */
    def apply(arg: String): Seq[Command.Hint] = {
      validIdentifiers.toSeq.sortBy(_.extension.name).filter(_.extension.name.startsWith(arg)).map(proposal ⇒
        Command.Hint(proposal.extension.name.toUpperCase(), Some(proposal.description), Seq(proposal.extension.name.toUpperCase().drop(arg.length)))).
        filter(_.completions.head.nonEmpty)
    }
  }
}

object SerializationTypeParser {
  import Command.parser._
  implicit def parser2implementation(c: SerializationTypeParser.type): SerializationTypeParser = c.inner
  /** Serialization type option name. */
  private val serializationArg = "-serialization"

  /** Get SerializationTypeParser implementation. */
  def inner() = DI.implementation
  /** SerializationType parser. */
  def parser(tag: String = "serialization") = (serializationArg, Command.Hint(serializationArg, Some("Serialization type for data files"))) ~> SerializationTypeParser(tag)

  /** Parser result. */
  case class Argument(tag: String, value: Serialization.Identifier)

  /**
   * Dependency injection routines
   */
  private object DI extends XDependencyInjection.PersistentInjectable {
    /** SerializationTypeParser implementation. */
    lazy val implementation = injectOptional[SerializationTypeParser] getOrElse new SerializationTypeParser
  }
}
