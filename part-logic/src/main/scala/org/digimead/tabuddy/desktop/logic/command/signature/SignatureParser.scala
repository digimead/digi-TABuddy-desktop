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

package org.digimead.tabuddy.desktop.logic.command.signature

import org.digimead.digi.lib.api.XDependencyInjection
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.definition.command.Command
import org.digimead.tabuddy.desktop.logic.command.signature.api.XSignatureAdapter
import org.digimead.tabuddy.model.serialization.signature.{ Mechanism, Signature }
import scala.language.implicitConversions

/**
 * Signature argument parser builder.
 */
class SignatureParser {
  import Command.parser._
  /** Set of valid signature identifiers. */
  lazy val validIdentifiers = SignatureParser.perIdentifier.map(_._1).toSet intersect Signature.perIdentifier.map(_._1).toSet

  /** Create parser for the signature configuration. */
  def apply(tag: String = ""): Command.parser.Parser[Any] =
    sp ~> commandRegex("\\w+".r, NameHintContainer) ^^ { name ⇒
      validIdentifiers.map(SignatureParser.perIdentifier).find(_.name == name) getOrElse {
        if (Empty.name == name)
          Empty
        else
          throw Command.ParseException(s"Signature with name '$name' not found.")
      }
    } into (adapter ⇒ adapter(tag))

  /** Hint container for signature mechanism name. */
  object NameHintContainer extends Command.Hint.Container {
    /** Get parser hints for user provided argument. */
    def apply(arg: String): Seq[Command.Hint] = {
      val adapters = validIdentifiers.map(SignatureParser.perIdentifier).toSeq.sortBy(_.name)
      (Empty +: adapters.filter(_.name.startsWith(arg))).filter(_.name.startsWith(arg)).map(proposal ⇒
        Command.Hint(proposal.name, Some(proposal.description), Seq(proposal.name.drop(arg.length)))).
        filter(_.completions.head.nonEmpty)
    }
  }
  /** Empty signature argument. */
  object Empty extends SignatureAdapter {
    /** Identifier of the digest mechanism. */
    val identifier: Mechanism.Identifier = Empty
    /** Mechanism name. */
    val name: String = "None"
    /** Mechanism description. */
    val description: String = "Turn off signature calculation"

    /** Create parser for the digest configuration. */
    def apply(tag: String): Command.parser.Parser[Any] = "" ^^^ { SignatureParser.Argument(tag, None) }

    object Empty extends Mechanism.Identifier {
      val name = "None"
    }
  }
}

object SignatureParser extends XLoggable {
  import Command.parser._
  implicit def parser2implementation(c: SignatureParser.type): SignatureParser = c.inner
  /** Signature parameters option name. */
  private val signatureArg = "-signature"

  /** Get SignatureParser implementation. */
  def inner() = DI.implementation
  /** Map of all available signature adapters. */
  def perIdentifier = DI.perIdentifier
  /** Signature parser. */
  def signatureParser = (signatureArg, Command.Hint(signatureArg, Some("Signature calculation parameters"))) ~> SignatureParser("signature")

  /** Parser result. */
  case class Argument(tag: String, value: Option[Mechanism.Parameters])

  /**
   * Dependency injection routines
   */
  private object DI extends XDependencyInjection.PersistentInjectable {
    /** SignatureParser implementation. */
    lazy val implementation = injectOptional[SignatureParser] getOrElse new SignatureParser
    /**
     * Per identifier signature mechanism adapters map.
     *
     * Each collected adapter must be:
     *  1. an instance of (X)SignatureAdapter
     *  2. has name that starts with "Command.Signature."
     */
    lazy val perIdentifier: Map[Mechanism.Identifier, SignatureAdapter] = {
      val mechanisms = bindingModule.bindings.filter {
        case (key, value) ⇒ classOf[XSignatureAdapter].isAssignableFrom(key.m.runtimeClass)
      }.map {
        case (key, value) ⇒
          key.name match {
            case Some(name) if name.startsWith("Command.Signature.") ⇒
              log.debug(s"'${name}' loaded.")
              bindingModule.injectOptional(key).asInstanceOf[Option[SignatureAdapter]]
            case _ ⇒
              log.debug(s"'${key.name.getOrElse("Unnamed")}' signature adapter skipped.")
              None
          }
      }.flatten.toSeq
      assert(mechanisms.distinct.size == mechanisms.size, "signature adapters contain duplicated entities in " + mechanisms)
      Map(mechanisms.map(m ⇒ m.identifier -> m): _*)
    }
  }
}
