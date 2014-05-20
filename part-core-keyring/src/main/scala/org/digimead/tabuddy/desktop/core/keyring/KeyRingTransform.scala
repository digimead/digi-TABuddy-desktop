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

package org.digimead.tabuddy.desktop.core.keyring

import java.io.{ ByteArrayOutputStream, IOException }
import org.bouncycastle.bcpg.{ PublicKeyAlgorithmTags, RSAPublicBCPGKey, RSASecretBCPGKey }
import org.bouncycastle.crypto.{ AsymmetricBlockCipher, CipherParameters, InvalidCipherTextException }
import org.bouncycastle.crypto.encodings.PKCS1Encoding
import org.bouncycastle.crypto.engines.{ ElGamalEngine, RSAEngine }
import org.bouncycastle.crypto.params.{ ParametersWithRandom, RSAKeyParameters, RSAPrivateCrtKeyParameters }
import org.bouncycastle.openpgp.{ PGPPrivateKey, PGPPublicKey, PGPSecretKey }
import org.bouncycastle.openpgp.operator.bc.{ BcPBESecretKeyDecryptorBuilder, BcPGPDigestCalculatorProvider }

/**
 * Signature part for data transformation.
 */
trait KeyRingTransform {
  /** Get an encryptor for the public key. */
  def encrypt(publicKey: PGPPublicKey): KeyRing.Encryptor = publicKey.getAlgorithm() match {
    case PublicKeyAlgorithmTags.RSA_ENCRYPT ⇒
      new KeyRingTransform.RSAEncryptor(publicKey)
    case PublicKeyAlgorithmTags.RSA_GENERAL ⇒
      new KeyRingTransform.RSAEncryptor(publicKey)
    case PublicKeyAlgorithmTags.RSA_SIGN ⇒
      throw new SecurityException("PGP RSA public key suitable for signing only")
    case PublicKeyAlgorithmTags.DSA ⇒
      throw new SecurityException("PGP DSA public key suitable for signing only")
    case PublicKeyAlgorithmTags.ELGAMAL_ENCRYPT ⇒
      new KeyRingTransform.ElGamalEncryptor(publicKey)
    case PublicKeyAlgorithmTags.ELGAMAL_GENERAL ⇒
      new KeyRingTransform.ElGamalEncryptor(publicKey)
    case PublicKeyAlgorithmTags.EC ⇒
      throw new SecurityException("PGP EC public key suitable for signing only")
    case PublicKeyAlgorithmTags.ECDSA ⇒
      throw new SecurityException("PGP ECDSA public key suitable for signing only")
    case unknown ⇒
      throw new SecurityException("Unknown PGP public key algorithm encountered " + unknown)
  }
  /** Get a decryptor for the secret key. */
  def decrypt(secretKey: PGPSecretKey, passPhrase: String = KeyRing.defaultPassPhrase): KeyRing.Decryptor =
    decrypt(secretKey.extractPrivateKey(new BcPBESecretKeyDecryptorBuilder(new BcPGPDigestCalculatorProvider()).
      build(passPhrase.toCharArray())))
  /** Get a decryptor for the private key. */
  def decrypt(privateKey: PGPPrivateKey): KeyRing.Decryptor = privateKey.getPublicKeyPacket().getAlgorithm() match {
    case PublicKeyAlgorithmTags.RSA_ENCRYPT ⇒
      new KeyRingTransform.RSADecryptor(privateKey)
    case PublicKeyAlgorithmTags.RSA_GENERAL ⇒
      new KeyRingTransform.RSADecryptor(privateKey)
    case PublicKeyAlgorithmTags.RSA_SIGN ⇒
      throw new SecurityException("PGP RSA public key suitable for signing only")
    case PublicKeyAlgorithmTags.DSA ⇒
      throw new SecurityException("PGP DSA public key suitable for signing only")
    case PublicKeyAlgorithmTags.ELGAMAL_ENCRYPT ⇒
      new KeyRingTransform.ElGamalDecryptor(privateKey)
    case PublicKeyAlgorithmTags.ELGAMAL_GENERAL ⇒
      new KeyRingTransform.ElGamalDecryptor(privateKey)
    case PublicKeyAlgorithmTags.EC ⇒
      throw new SecurityException("PGP EC public key suitable for signing only")
    case PublicKeyAlgorithmTags.ECDSA ⇒
      throw new SecurityException("PGP ECDSA public key suitable for signing only")
    case unknown ⇒
      throw new SecurityException("Unknown PGP public key algorithm encountered " + unknown)
  }
}

object KeyRingTransform {
  /** Decrypt payload with cipher. */
  def decrypt(cipher: AsymmetricBlockCipher, in: Array[Byte], inOff: Int, len: Int,
    inputBlockSize: Int, outputBlockSize: Int): Array[Byte] = {
    var inputOffset = inOff
    val buffer = new Array[Byte](inputBlockSize)
    val outputStream = if (len % inputBlockSize == 0)
      new ByteArrayOutputStream((len / inputBlockSize) * outputBlockSize)
    else
      throw new IllegalArgumentException("Unable to process input with incorrect length")

    while (inputOffset < len) {
      // Copy next piece of input.
      var bufLength = inputBlockSize
      if (inputOffset + inputBlockSize > len)
        bufLength = len - inputOffset
      System.arraycopy(in, inputOffset, buffer, 0, bufLength)
      inputOffset += bufLength
      // Produce output for the current piece.
      val outputBlock = cipher.processBlock(buffer, 0, bufLength)
      try outputStream.write(outputBlock) catch {
        case e: IOException ⇒ throw new InvalidCipherTextException(e.getMessage())
      }
    }
    outputStream.toByteArray()
  }
  /** Encrypt payload with cipher. */
  def encrypt(cipher: AsymmetricBlockCipher, in: Array[Byte], inOff: Int, len: Int,
    inputBlockSize: Int, outputBlockSize: Int): Array[Byte] = {
    var inputOffset = inOff
    val buffer = new Array[Byte](inputBlockSize)
    val outputStream = if (len % inputBlockSize == 0)
      new ByteArrayOutputStream((len / inputBlockSize) * outputBlockSize)
    else
      new ByteArrayOutputStream((len / inputBlockSize + 1) * outputBlockSize)

    while (inputOffset < len) {
      // Copy next piece of input.
      var bufLength = inputBlockSize
      if (inputOffset + inputBlockSize > len)
        bufLength = len - inputOffset
      System.arraycopy(in, inputOffset, buffer, 0, bufLength)
      inputOffset += bufLength
      // Produce output for the current piece.
      val outputBlock = cipher.processBlock(buffer, 0, bufLength)
      try outputStream.write(outputBlock) catch {
        case e: IOException ⇒ throw new InvalidCipherTextException(e.getMessage())
      }
    }
    outputStream.toByteArray()
  }

  /**
   * ElGamal encryptor
   */
  class ElGamalEncryptor(parameters: CipherParameters) extends KeyRing.Encryptor {
    def this(privateKey: PGPPrivateKey) = this(privateKey.asInstanceOf[CipherParameters])
    def this(publicKey: PGPPublicKey) = this(publicKey.asInstanceOf[CipherParameters])

    lazy val cipher = {
      val encoding = new PKCS1Encoding(new ElGamalEngine())
      encoding.init(true, new ParametersWithRandom(parameters, KeyRing.random))
      encoding
    }

    /** Apply transformation. */
    def apply(source: Array[Byte]): Array[Byte] =
      KeyRingTransform.encrypt(cipher, source, 0, source.size,
        cipher.getInputBlockSize(), cipher.getOutputBlockSize()): Array[Byte]
    /**
     * Return the maximum size for an input block to this engine.
     *
     * @return maximum size for an input block.
     */
    def getInputBlockSize(): Int = cipher.getInputBlockSize()
  }
  /**
   * RSA decryptor
   */
  class ElGamalDecryptor(parameters: CipherParameters) extends KeyRing.Decryptor {
    def this(privateKey: PGPPrivateKey) = this(privateKey.asInstanceOf[CipherParameters])
    def this(publicKey: PGPPublicKey) = this(publicKey.asInstanceOf[CipherParameters])

    lazy val cipher = {
      val encoding = new PKCS1Encoding(new ElGamalEngine())
      encoding.init(false, new ParametersWithRandom(parameters, KeyRing.random))
      encoding
    }

    def apply(source: Array[Byte]): Array[Byte] =
      KeyRingTransform.decrypt(cipher, source, 0, source.size,
        cipher.getInputBlockSize(), cipher.getOutputBlockSize()): Array[Byte]
  }
  /**
   * RSA encryptor
   */
  class RSAEncryptor(parameters: CipherParameters) extends KeyRing.Encryptor {
    def this(publicKey: PGPPublicKey) = this(publicKey.getPublicKeyPacket().getKey() match {
      case key: RSAPublicBCPGKey ⇒ new RSAKeyParameters(false, key.getModulus(), key.getPublicExponent())
      case unknown ⇒ throw new IllegalArgumentException("Unexpected key type " + unknown.getClass())
    })

    lazy val cipher = {
      val encoding = new PKCS1Encoding(new RSAEngine())
      encoding.init(true, new ParametersWithRandom(parameters, KeyRing.random))
      encoding
    }

    /** Apply transformation. */
    def apply(source: Array[Byte]): Array[Byte] =
      KeyRingTransform.encrypt(cipher, source, 0, source.size,
        cipher.getInputBlockSize(), cipher.getOutputBlockSize()): Array[Byte]
    /**
     * Return the maximum size for an input block to this engine.
     *
     * @return maximum size for an input block.
     */
    def getInputBlockSize(): Int = cipher.getInputBlockSize()
  }
  /**
   * RSA decryptor
   */
  class RSADecryptor(parameters: CipherParameters) extends KeyRing.Decryptor {
    def this(privateKey: PGPPrivateKey) = this(privateKey.getPrivateKeyDataPacket() match {
      case key: RSASecretBCPGKey ⇒ new RSAPrivateCrtKeyParameters(key.getModulus(),
        privateKey.getPublicKeyPacket().getKey().asInstanceOf[RSAPublicBCPGKey].getPublicExponent(),
        key.getPrivateExponent(), key.getPrimeP(), key.getPrimeQ(),
        key.getPrimeExponentP(), key.getPrimeExponentQ(), key.getCrtCoefficient())
      case unknown ⇒ throw new IllegalArgumentException("Unexpected key type " + unknown.getClass())
    })

    lazy val cipher = {
      val decoding = new PKCS1Encoding(new RSAEngine)
      decoding.init(false, parameters)
      decoding
    }

    def apply(source: Array[Byte]): Array[Byte] =
      KeyRingTransform.decrypt(cipher, source, 0, source.size,
        cipher.getInputBlockSize(), cipher.getOutputBlockSize()): Array[Byte]
  }
}
