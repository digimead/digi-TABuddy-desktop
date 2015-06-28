/**
 * This file is part of the TA Buddy project.
 * Copyright (c) 2014-2015 Alexey Aksenov ezh@ezh.msk.ru
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

import com.google.common.io.BaseEncoding
import java.io.{ InputStream, OutputStream }
import org.bouncycastle.crypto.{ CipherParameters, DataLengthException, OutputLengthException, StreamCipher }
import org.bouncycastle.crypto.io.{ CipherInputStream, CipherOutputStream }
import org.digimead.tabuddy.desktop.logic.payload.marker.serialization.encryption.api.XEncryption

/**
 * Simple XOR encryption implementation.
 */
class XOR extends XEncryption {
  /** Unique encryption identifier. */
  val identifier = XOR.Identifier

  /** Get encryption parameters. */
  def apply(key: Option[String], args: String*): XOR.Parameters = args match {
    case Nil ⇒ XOR.Parameters(key)
    case _ ⇒ throw new IllegalArgumentException("Incorrect parameters: " + args.mkString(", "))
  }
  /** Decrypt data. */
  def decrypt(data: Array[Byte], parameters: XEncryption.Parameters): Array[Byte] = parameters match {
    case XOR.Parameters(Some(key)) ⇒
      xorWithKey(data, key.getBytes(io.Codec.UTF8.charSet))
    case _ ⇒
      throw new IllegalArgumentException("Incorrect parameters " + parameters)
  }
  /** Decrypt input stream. */
  def decrypt(inputStream: InputStream, parameters: XEncryption.Parameters): InputStream = parameters match {
    case XOR.Parameters(Some(key)) ⇒
      new CipherInputStream(inputStream, new XStreamCipher(key.getBytes(io.Codec.UTF8.charSet)))
    case _ ⇒
      throw new IllegalArgumentException("Incorrect parameters " + parameters)
  }
  /** Encrypt data. */
  def encrypt(data: Array[Byte], parameters: XEncryption.Parameters): Array[Byte] = parameters match {
    case XOR.Parameters(Some(key)) ⇒
      xorWithKey(data, key.getBytes(io.Codec.UTF8.charSet))
    case _ ⇒
      throw new IllegalArgumentException("Incorrect parameters " + parameters)
  }
  /** Encrypt output stearm. */
  def encrypt(outputStream: OutputStream, parameters: XEncryption.Parameters): OutputStream = parameters match {
    case XOR.Parameters(Some(key)) ⇒
      new CipherOutputStream(outputStream, new XStreamCipher(key.getBytes(io.Codec.UTF8.charSet)))
    case _ ⇒
      throw new IllegalArgumentException("Incorrect parameters " + parameters)
  }
  /** Convert from string. */
  def fromString(data: String): Array[Byte] = BaseEncoding.base64().decode(data)
  /** Convert to string. */
  def toString(data: Array[Byte]): String = BaseEncoding.base64().encode(data)

  /** XOR data. */
  protected def xorWithKey(a: Array[Byte], key: Array[Byte], shift: Int = 0): Array[Byte] = {
    val out = new Array[Byte](a.length)
    for (i ← 0 until a.length)
      out(i) = (a(i) ^ key((i + shift) % key.length)).toByte
    out
  }

  /**
   * XOR Decrypt StreamCipher
   */
  class XStreamCipher(key: Array[Byte]) extends StreamCipher {
    /** Key shift. */
    var shift = 0

    /** Initialise the cipher. */
    def init(forEncryption: Boolean, params: CipherParameters) {}
    /** Return the name of the algorithm the cipher implements. */
    def getAlgorithmName() = "Base64"
    /** Encrypt/decrypt a single byte returning the result. */
    def returnByte(in: Byte): Byte = xorWithKey(Array(in), key).head
    /** Process a block of bytes from in putting the result into out. */
    def processBytes(in: Array[Byte], inOff: Int, len: Int, out: Array[Byte], outOff: Int): Int = synchronized {
      if ((inOff + len) > in.length)
        throw new DataLengthException("Input buffer too short")
      if ((outOff + len) > out.length)
        throw new OutputLengthException("Output buffer too short")
      System.arraycopy(xorWithKey(in.drop(inOff).take(len), key, shift), 0, out, outOff, len)
      shift = (shift + len) % key.length
      len
    }
    /** Reset the cipher. */
    def reset() {}
  }
}

object XOR {
  /** Get XOR encryption parameters. */
  def apply(key: String): XEncryption.Parameters =
    Encryption.perIdentifier.get(Identifier) match {
      case Some(encryption: XOR) ⇒ encryption(Some(key))
      case _ ⇒ throw new IllegalStateException("XOR encryption is not available.")
    }

  /**
   * XOR encryption parameters.
   */
  case class Parameters(val key: Option[String]) extends XEncryption.Parameters {
    if (key.isEmpty)
      throw new IllegalArgumentException("Encryption key is not defined")
    /** Encryption instance. */
    lazy val encryption = Encryption.perIdentifier(Identifier).asInstanceOf[XOR]

    /** XOR parameters as sequence of strings. */
    val arguments: Seq[String] = Seq.empty
  }

  /**
   * XOR encryption identifier.
   */
  object Identifier extends XEncryption.Identifier {
    /** Encryption name. */
    val name = "XOR"
    /** Encryption description. */
    val description: String = "extremely simple additive cipher"
  }
}
