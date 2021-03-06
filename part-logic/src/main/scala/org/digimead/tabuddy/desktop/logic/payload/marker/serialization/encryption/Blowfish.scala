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

import com.google.common.io.BaseEncoding
import java.io.{ InputStream, OutputStream }
import org.bouncycastle.crypto.PBEParametersGenerator
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.engines.BlowfishEngine
import org.bouncycastle.crypto.generators.PKCS12ParametersGenerator
import org.bouncycastle.crypto.io.{ CipherInputStream, CipherOutputStream }
import org.bouncycastle.crypto.modes.CBCBlockCipher
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher
import org.bouncycastle.crypto.params.{ ParametersWithIV, ParametersWithRandom }
import org.bouncycastle.util.encoders.Base64
import org.digimead.tabuddy.desktop.core.keyring.KeyRing
import org.digimead.tabuddy.desktop.logic.payload.marker.serialization.encryption.api.XEncryption

/**
 * Blowfish encryption implementation.
 */
class Blowfish extends XEncryption {
  /** Unique encryption identifier. */
  val identifier = Blowfish.Identifier

  /** Get encryption parameters. */
  def apply(key: Option[String], args: String*): Blowfish.Parameters = args match {
    case Seq("256", salt: String) ⇒ Blowfish.Parameters(key, Blowfish.Strength256, Base64.decode(salt.getBytes(io.Codec.UTF8.charSet)))
    case Seq("448", salt: String) ⇒ Blowfish.Parameters(key, Blowfish.Strength448, Base64.decode(salt.getBytes(io.Codec.UTF8.charSet)))
    case _ ⇒ throw new IllegalArgumentException("Incorrect parameters: " + args.mkString(", "))
  }
  /** Decrypt data. */
  def decrypt(data: Array[Byte], parameters: XEncryption.Parameters): Array[Byte] = parameters match {
    case Blowfish.Parameters(Some(key), strength, salt) ⇒
      val cipher = new PaddedBufferedBlockCipher(new CBCBlockCipher(new BlowfishEngine))
      val pGen = new PKCS12ParametersGenerator(new SHA256Digest())
      pGen.init(PBEParametersGenerator.PKCS12PasswordToBytes(key.toCharArray()), salt, AES.iterationCount)
      val paramsWithIV = pGen.generateDerivedParameters(strength.length, 64).asInstanceOf[ParametersWithIV]
      cipher.init(false, new ParametersWithRandom(paramsWithIV, KeyRing.random))
      val buffer = new Array[Byte](cipher.getOutputSize(data.length))
      var resultLength = cipher.processBytes(data, 0, data.length, buffer, 0)
      resultLength += cipher.doFinal(buffer, resultLength)
      val result = new Array[Byte](resultLength)
      System.arraycopy(buffer, 0, result, 0, result.length)
      result
    case _ ⇒
      throw new IllegalArgumentException("Incorrect parameters " + parameters)
  }
  /** Decrypt input stream. */
  def decrypt(inputStream: InputStream, parameters: XEncryption.Parameters): InputStream = parameters match {
    case Blowfish.Parameters(Some(key), strength, salt) ⇒
      val cipher = new PaddedBufferedBlockCipher(new CBCBlockCipher(new BlowfishEngine))
      val pGen = new PKCS12ParametersGenerator(new SHA256Digest())
      pGen.init(PBEParametersGenerator.PKCS12PasswordToBytes(key.toCharArray()), salt, AES.iterationCount)
      val paramsWithIV = pGen.generateDerivedParameters(strength.length, 64).asInstanceOf[ParametersWithIV]
      cipher.init(false, new ParametersWithRandom(paramsWithIV, KeyRing.random))
      new CipherInputStream(inputStream, cipher)
    case _ ⇒
      throw new IllegalArgumentException("Incorrect parameters " + parameters)
  }
  /** Encrypt data. */
  def encrypt(data: Array[Byte], parameters: XEncryption.Parameters): Array[Byte] = parameters match {
    case Blowfish.Parameters(Some(key), strength, salt) ⇒
      val cipher = new PaddedBufferedBlockCipher(new CBCBlockCipher(new BlowfishEngine))
      val pGen = new PKCS12ParametersGenerator(new SHA256Digest())
      pGen.init(PBEParametersGenerator.PKCS12PasswordToBytes(key.toCharArray()), salt, AES.iterationCount)
      val paramsWithIV = pGen.generateDerivedParameters(strength.length, 64).asInstanceOf[ParametersWithIV]
      cipher.init(true, new ParametersWithRandom(paramsWithIV, KeyRing.random))
      val buffer = new Array[Byte](cipher.getOutputSize(data.length))
      var resultLength = cipher.processBytes(data, 0, data.length, buffer, 0)
      resultLength += cipher.doFinal(buffer, resultLength)
      val result = new Array[Byte](resultLength)
      System.arraycopy(buffer, 0, result, 0, result.length)
      result
    case _ ⇒
      throw new IllegalArgumentException("Incorrect parameters " + parameters)
  }
  /** Encrypt output stearm. */
  def encrypt(outputStream: OutputStream, parameters: XEncryption.Parameters): OutputStream = parameters match {
    case Blowfish.Parameters(Some(key), strength, salt) ⇒
      val cipher = new PaddedBufferedBlockCipher(new CBCBlockCipher(new BlowfishEngine))
      val pGen = new PKCS12ParametersGenerator(new SHA256Digest())
      pGen.init(PBEParametersGenerator.PKCS12PasswordToBytes(key.toCharArray()), salt, AES.iterationCount)
      val paramsWithIV = pGen.generateDerivedParameters(strength.length, 64).asInstanceOf[ParametersWithIV]
      cipher.init(true, new ParametersWithRandom(paramsWithIV, KeyRing.random))
      new CipherOutputStream(outputStream, cipher)
    case _ ⇒
      throw new IllegalArgumentException("Incorrect parameters " + parameters)
  }
  /** Convert from string. */
  def fromString(data: String): Array[Byte] = BaseEncoding.base64().decode(data)
  /** Convert to string. */
  def toString(data: Array[Byte]): String = BaseEncoding.base64().encode(data)
}

object Blowfish {
  /** Get Blowfish encryption parameters. */
  def apply(key: String, keyLength: Blowfish.LengthParameter, salt: Array[Byte] = Encryption.defaultSalt): XEncryption.Parameters =
    Encryption.perIdentifier.get(Identifier) match {
      case Some(encryption: Blowfish) ⇒ encryption(Some(key), keyLength.length.toString, new String(Base64.encode(salt), io.Codec.UTF8.charSet))
      case _ ⇒ throw new IllegalStateException("Blowfish encryption is not available.")
    }

  /**
   * Blowfish encryption parameters.
   */
  case class Parameters(val key: Option[String], keyLength: Blowfish.LengthParameter, val salt: Array[Byte]) extends XEncryption.Parameters {
    if (key.isEmpty)
      throw new IllegalArgumentException("Encryption key is not defined")
    /** Encryption instance. */
    lazy val encryption = Encryption.perIdentifier(Identifier).asInstanceOf[Blowfish]

    /** Blowfish encryption parameters as sequence of strings. */
    val arguments: Seq[String] = Seq(keyLength.length.toString, new String(Base64.encode(salt), io.Codec.UTF8.charSet))
  }

  /**
   * Blowfish encryption identifier.
   */
  object Identifier extends XEncryption.Identifier {
    /** Encryption name. */
    val name = "blowfish"
    /** Encryption description. */
    val description: String = "symmetric-key block cipher, designed by Bruce Schneier."
  }
  /**
   * Encryption key length.
   */
  trait LengthParameter {
    val length: Int
  }
  /**
   * Blowfish 256
   */
  case object Strength256 extends LengthParameter {
    val length = 256
  }
  /**
   * Blowfish 448
   */
  case object Strength448 extends LengthParameter {
    val length = 448
  }
}
