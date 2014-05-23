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

package org.digimead.tabuddy.desktop.logic.payload.marker.serialization.encryption

import org.digimead.tabuddy.desktop.logic.payload.marker.api

/**
 * Simple XOR encryption implementation.
 */
class XOR extends api.Encryption {
  /** Encryption description. */
  val description: String = "Simple XOR transformation."
  /** Unique encryption identifier. */
  val identifier = XOR.Identifier

  /** Get encryption parameters. */
  def apply(key: Option[String], args: String*): XORParameters = args match {
    case Nil ⇒ XORParameters(key)
    case _ ⇒ throw new IllegalArgumentException("Incorrect parameters: " + args.mkString(", "))
  }
  /** Encrypt data. */
  def encrypt(data: Array[Byte], parameters: api.Encryption.Parameters): Array[Byte] = parameters match {
    case XORParameters(Some(key)) ⇒
      xorWithKey(data, key.getBytes())
    case _ ⇒
      throw new IllegalArgumentException("Incorrect parameters " + parameters)
  }
  /** Decrypt data. */
  def decrypt(data: Array[Byte], parameters: api.Encryption.Parameters): Array[Byte] = parameters match {
    case XORParameters(Some(key)) ⇒
      xorWithKey(data, key.getBytes())
    case _ ⇒
      throw new IllegalArgumentException("Incorrect parameters " + parameters)
  }

  /** XOR data. */
  protected def xorWithKey(a: Array[Byte], key: Array[Byte]): Array[Byte] = {
    val out = new Array[Byte](a.length)
    for (i ← 0 until a.length)
      out(i) = (a(i) ^ key(i % key.length)).toByte
    out
  }

  /**
   * XOR encryption parameters.
   */
  case class XORParameters(val key: Option[String]) extends api.Encryption.Parameters {
    if (key.isEmpty)
      throw new IllegalArgumentException("Encryption key is not defined")
    /** Encryption instance. */
    lazy val encryption = XOR.this

    /** XOR parameters as sequence of strings. */
    def arguments: Seq[String] = Seq.empty
  }
}

object XOR {
  /** Get XOR encryption parameters. */
  def apply(key: String): api.Encryption.Parameters =
    Encryption.perIdentifier.get(Identifier) match {
      case Some(encryption: XOR) ⇒ encryption(Some(key))
      case _ ⇒ throw new IllegalStateException("XOR encryption is not available.")
    }

  /**
   * XOR encryption identifier.
   */
  object Identifier extends api.Encryption.Identifier { val name = "XOR" }
}
