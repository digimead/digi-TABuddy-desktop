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

import com.google.common.base.CharMatcher
import com.google.common.io.BaseEncoding
import java.io.{ InputStream, InputStreamReader, OutputStream, OutputStreamWriter }
import org.digimead.tabuddy.desktop.logic.payload.marker.api

/**
 * Base encryption implementation.
 */
class Base extends api.Encryption {
  /** Encryption description. */
  val description: String = "Simple base transformation."
  /** Unique encryption identifier. */
  val identifier = Base.Identifier

  /** Get encryption parameters. */
  def apply(key: Option[String], args: String*): BaseParameters = {
    if (key.nonEmpty)
      throw new IllegalArgumentException("Encryption key is not supported")
    args match {
      case Seq("64") ⇒ BaseParameters(Base.Dictionary64)
      case _ ⇒ throw new IllegalArgumentException("Incorrect parameters: " + args.mkString(", "))
    }
  }
  /** Decrypt data. */
  def decrypt(data: Array[Byte], parameters: api.Encryption.Parameters): Array[Byte] = parameters match {
    case BaseParameters(dictionaryLength) if dictionaryLength.length == 64 ⇒
      BaseEncoding.base64().decode(CharMatcher.WHITESPACE.removeFrom(new String(data, io.Codec.UTF8.charSet)))
    case _ ⇒
      throw new IllegalArgumentException("Incorrect parameters " + parameters)
  }
  /** Decrypt input stream. */
  def decrypt(inputStream: InputStream, parameters: api.Encryption.Parameters): InputStream = parameters match {
    case BaseParameters(dictionaryLength) if dictionaryLength.length == 64 ⇒
      BaseEncoding.base64().decodingStream(new InputStreamReader(inputStream, io.Codec.UTF8.charSet))
    case _ ⇒
      throw new IllegalArgumentException("Incorrect parameters " + parameters)
  }
  /** Encrypt data. */
  def encrypt(data: Array[Byte], parameters: api.Encryption.Parameters): Array[Byte] = parameters match {
    case BaseParameters(dictionaryLength) if dictionaryLength.length == 64 ⇒
      BaseEncoding.base64().encode(data).getBytes(io.Codec.UTF8.charSet)
    case _ ⇒
      throw new IllegalArgumentException("Incorrect parameters " + parameters)
  }
  /** Encrypt output stearm. */
  def encrypt(outputStream: OutputStream, parameters: api.Encryption.Parameters): OutputStream = parameters match {
    case BaseParameters(dictionaryLength) if dictionaryLength.length == 64 ⇒
      BaseEncoding.base64().encodingStream(new OutputStreamWriter(outputStream, io.Codec.UTF8.charSet))
    case _ ⇒
      throw new IllegalArgumentException("Incorrect parameters " + parameters)
  }
  /** Convert from string. */
  def fromString(data: String): Array[Byte] = data.getBytes(io.Codec.UTF8.charSet)
  /** Convert to string. */
  def toString(data: Array[Byte]): String = new String(data, io.Codec.UTF8.charSet)

  /**
   * Base encryption parameters.
   */
  case class BaseParameters(dictionaryLength: Base.LengthParameter) extends api.Encryption.Parameters {
    // Encryption key is not supported.
    val key = None
    /** Encryption instance. */
    lazy val encryption = Base.this

    /** Base parameters as sequence of strings. */
    def arguments: Seq[String] = Seq(dictionaryLength.length.toString)
  }
}

object Base {
  /** Get Base encryption parameters. */
  def apply(dictionaryLength: Base.LengthParameter): api.Encryption.Parameters =
    Encryption.perIdentifier.get(Identifier) match {
      case Some(encryption: Base) ⇒ encryption(None, dictionaryLength.length.toString)
      case _ ⇒ throw new IllegalStateException("Base encryption is not available.")
    }

  /**
   * Base encryption identifier.
   */
  object Identifier extends api.Encryption.Identifier { val name = "Base" }
  /**
   * Encryption dictionary length.
   */
  trait LengthParameter {
    val length: Int
  }
  /**
   * Base 64
   */
  case object Dictionary64 extends LengthParameter {
    val length = 64
  }
}
