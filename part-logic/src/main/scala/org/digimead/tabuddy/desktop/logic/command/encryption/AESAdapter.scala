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

import org.digimead.tabuddy.desktop.core.definition.command.Command
import org.digimead.tabuddy.desktop.logic.payload.marker.serialization.encryption.{ AES, Encryption }

/**
 * Adapter between logic.payload.marker.serialization.encryption.AES and console parser
 */
class AESAdapter extends EncryptionAdapter {
  import Command.parser._
  /** Identifier of the encryption mechanism. */
  val identifier: Encryption.Identifier = AES.Identifier
  /** Encryption name. */
  val name: String = "AES"
  /** Encryption description. */
  val description: String = "The Advanced Encryption Standard symmetric block cipher"

  /** Create parser for AES configuration. */
  def apply(tag: String): Command.parser.Parser[Any] = (sp ~>
    (("128", Command.Hint("128", Some("Build encryption key with length 128 bits long."))) |
      ("192", Command.Hint("192", Some("Build encryption key with length 192 bits long."))) |
      ("256", Command.Hint("256", Some("Build encryption key with length 256 bits long.")))) ~ sp ~
      opt(commandLiteral("-iv", Command.Hint("-iv", Some("Initialization vector"))) ~ sp ~>
        commandRegex("'[^']+?'".r, Command.Hint.Container(Command.Hint("iv", Some("Initialization vector. Secret phrase surrounded by single quotes"), Seq.empty))) <~ sp) ~
      commandRegex("'[^']+?'".r, Command.Hint.Container(Command.Hint("key", Some("Encryption key. Secret phrase surrounded by single quotes"), Seq.empty)))) ^^
      (_ match {
        case ~(~(~(length, _), iv), key) ⇒
          val keyLength = length match {
            case "128" ⇒ AES.Strength128
            case "192" ⇒ AES.Strength192
            case "256" ⇒ AES.Strength256
            case unknown ⇒ throw new IllegalArgumentException("Unable to process incorrect key length " + unknown)
          }
          (length, iv) match {
            case (length, None) ⇒
              EncryptionParser.Argument(tag, Some(AES(optionContent(key), keyLength)))
            case (length, Some(iv)) ⇒
              EncryptionParser.Argument(tag, Some(AES(optionContent(key), keyLength, optionContent(iv).getBytes(io.Codec.UTF8.charSet))))
          }
      })

  /** Get option content. */
  protected def optionContent(option: String) = option.substring(1, option.length() - 1)
}
