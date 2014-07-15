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

package org.digimead.tabuddy.desktop.logic.command.encryption

import org.digimead.digi.lib.api.XDependencyInjection
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.definition.command.Command
import org.digimead.tabuddy.desktop.logic.command.encryption.api.XEncryptionAdapter
import org.digimead.tabuddy.desktop.logic.payload.marker.serialization.encryption.Encryption
import scala.language.implicitConversions

/**
 * Parser builder for encryption argument.
 */
class EncryptionParser {
  import Command.parser._
  /** Set of encryption identifiers. */
  lazy val validIdentifiers = EncryptionParser.perIdentifier.map(_._1).toSet intersect Encryption.perIdentifier.map(_._1).toSet
  /** All valid ecryption identifiers with empty value. */
  lazy val allValidIdentifiers = validIdentifiers + Empty.Empty

  /** Create parser for the encryption configuration. */
  def apply(tag: String = ""): Command.parser.Parser[Any] =
    sp ~> commandRegex("\\w+".r, NameHintContainer) ^? {
      case CompletionRequest(name) ⇒
        validIdentifiers.map(EncryptionParser.perIdentifier).find(_.identifier.name == name) getOrElse {
          name
        }
      case name ⇒
        validIdentifiers.map(EncryptionParser.perIdentifier).find(_.identifier.name == name) getOrElse {
          if (name == Empty.identifier.name)
            Empty
          else
            throw Command.ParseException(s"Encryption with name '$name' not found.")
        }
    } into (_ match {
      case adapter: EncryptionAdapter ⇒ adapter(tag)
      case name ⇒ nop
    })

  /** Hint container for signature mechanism name. */
  object NameHintContainer extends Command.Hint.Container {
    /** Get parser hints for user provided argument. */
    def apply(arg: String): Seq[Command.Hint] = {
      val adapters = validIdentifiers.map(EncryptionParser.perIdentifier).toSeq.sortBy(_.identifier.name)
      (Empty +: adapters).filter(_.identifier.name.startsWith(arg)).map(proposal ⇒
        Command.Hint(proposal.identifier.name, Some(proposal.identifier.description), Seq(proposal.identifier.name.drop(arg.length)))).
        filter(_.completions.head.nonEmpty)
    }
  }
  /** Empty encryption adapter. */
  object Empty extends EncryptionAdapter {
    /** Identifier of the encryption mechanism. */
    val identifier: Encryption.Identifier = Empty

    /** Create parser for the encryption configuration. */
    def apply(tag: String): Command.parser.Parser[Any] = "" ^^^ { EncryptionParser.Argument(tag, None) }
    /** Check whecher the adapter is valid. */
    def valid: Boolean = true

    object Empty extends Encryption.Identifier {
      /** Encryption name. */
      val name: String = "none"
      /** Encryption description. */
      val description: String = "turn off encryption"
    }
  }
}

object EncryptionParser extends XLoggable {
  import Command.parser._
  implicit def parser2implementation(c: EncryptionParser.type): EncryptionParser = c.inner
  /** Container encryption option name. */
  val containerEncryptionArg = "-containerEncryption"
  /** Content encryption option name. */
  val contentEncryptionArg = "-contentEncryption"

  /** Container encryption parser. */
  def containerParser(tag: String = "container") = (containerEncryptionArg, Command.Hint(containerEncryptionArg, Some("Directory or file name encryption parameters"))) ~> EncryptionParser(tag)
  /** Content encryption parser. */
  def contentParser(tag: String = "content") = (contentEncryptionArg, Command.Hint(contentEncryptionArg, Some("File content encryption parameters"))) ~> EncryptionParser(tag)
  /** Get EncryptionParser implementation. */
  def inner() = DI.implementation
  /** Map of all available encryption adapters. */
  def perIdentifier = DI.perIdentifier

  /** Parser result. */
  case class Argument(tag: String, value: Option[Encryption.Parameters])

  /**
   * Dependency injection routines
   */
  private object DI extends XDependencyInjection.PersistentInjectable {
    /** EncryptionParser implementation. */
    lazy val implementation = injectOptional[EncryptionParser] getOrElse new EncryptionParser
    /**
     * Per identifier encryption adapters map.
     *
     * Each collected encryption must be:
     *  1. an instance of (X)EncryptionAdapter
     *  2. has name that starts with "Command.Encryption."
     */
    lazy val perIdentifier: Map[Encryption.Identifier, EncryptionAdapter] = {
      val mechanisms = bindingModule.bindings.filter {
        case (key, value) ⇒ classOf[XEncryptionAdapter].isAssignableFrom(key.m.runtimeClass)
      }.map {
        case (key, value) ⇒
          key.name match {
            case Some(name) if name.startsWith("Command.Encryption.") ⇒
              log.debug(s"'${name}' loaded.")
              bindingModule.injectOptional(key).asInstanceOf[Option[EncryptionAdapter]]
            case _ ⇒
              log.debug(s"'${key.name.getOrElse("Unnamed")}' encryption adapter skipped.")
              None
          }
      }.flatten.toSeq
      assert(mechanisms.distinct.size == mechanisms.size, "encryption adapters contain duplicated entities in " + mechanisms)
      Map(mechanisms.map(m ⇒ m.identifier -> m): _*)
    }
  }
}
