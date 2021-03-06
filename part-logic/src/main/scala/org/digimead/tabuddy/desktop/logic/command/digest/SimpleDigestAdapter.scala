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

package org.digimead.tabuddy.desktop.logic.command.digest

import org.digimead.tabuddy.desktop.core.definition.command.Command
import org.digimead.tabuddy.model.serialization.digest.{ Mechanism, SimpleDigest }

/**
 * Adapter between model.serialization.digest.SimpleDigest and console parser
 */
class SimpleDigestAdapter extends DigestAdapter {
  import Command.parser._
  /** Identifier of the digest mechanism. */
  val identifier: Mechanism.Identifier = SimpleDigest.Identifier

  /** Create parser for SimpleDigest configuration. */
  def apply(tag: String): Command.parser.Parser[Any] = sp ~> (
    // http://docs.oracle.com/javase/7/docs/technotes/guides/security/StandardNames.html#MessageDigest
    (("SHA-512", Command.Hint("SHA-512", Some("hash algorithms defined in the FIPS PUB 180-2."))) ^^
      { _ ⇒ DigestParser.Argument(tag, Some(SimpleDigest("SHA-512"))) }) |
      (("SHA-384", Command.Hint("SHA-384", Some("hash algorithms defined in the FIPS PUB 180-2."))) ^^
        { _ ⇒ DigestParser.Argument(tag, Some(SimpleDigest("SHA-384"))) }) |
        (("SHA-256", Command.Hint("SHA-256", Some("hash algorithms defined in the FIPS PUB 180-2."))) ^^
          { _ ⇒ DigestParser.Argument(tag, Some(SimpleDigest("SHA-256"))) }) |
          (("SHA", Command.Hint("SHA", Some("hash algorithms defined in the FIPS PUB 180-2."))) ^^
            { _ ⇒ DigestParser.Argument(tag, Some(SimpleDigest("SHA-1"))) }) |
            (("MD5", Command.Hint("MD5", Some("the MD5 message digest algorithm as defined in RFC 1321."))) ^^
              { _ ⇒ DigestParser.Argument(tag, Some(SimpleDigest("MD5"))) }) |
              (("MD2", Command.Hint("MD2", Some("the MD2 message digest algorithm as defined in RFC 1319."))) ^^
                { _ ⇒ DigestParser.Argument(tag, Some(SimpleDigest("MD2"))) }))
}
