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

package org.digimead.tabuddy.desktop.core.keyring

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream }
import java.util.{ ArrayList, Date }
import org.bouncycastle.bcpg.{ HashAlgorithmTags, PublicKeyAlgorithmTags, SymmetricKeyAlgorithmTags }
import org.bouncycastle.bcpg.sig.{ Features, KeyFlags }
import org.bouncycastle.openpgp.{ PGPKeyRingGenerator, PGPPrivateKey, PGPPublicKeyRingCollection, PGPSecretKey, PGPSecretKeyRingCollection, PGPSignature, PGPSignatureSubpacketGenerator }
import org.bouncycastle.openpgp.operator.bc.{ BcKeyFingerprintCalculator, BcPBESecretKeyDecryptorBuilder, BcPBESecretKeyEncryptorBuilder, BcPGPContentSignerBuilder, BcPGPDigestCalculatorProvider, BcPGPKeyPair }
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.keyring.generator.Generator
import org.digimead.tabuddy.desktop.core.keyring.generator.api.XGenerator
import org.eclipse.core.runtime.NullProgressMonitor

/**
 * General part for keyring implementation.
 */
trait KeyRingGeneral {
  this: KeyRing.Implementation with XLoggable ⇒
  /** PublicKeyRingCollection with the latest content. */
  protected var actualPublicKeyRingCollection = new PGPPublicKeyRingCollection(new ArrayList())
  /** SecretKeyRingCollection with the latest content. */
  protected var actualSecretKeyRingCollection = new PGPSecretKeyRingCollection(new ArrayList())
  /** PublicKeyRingCollection access lock. */
  protected val publicKeyRingCollectionLock = new Object
  /** SecretKeyRingCollection access lock. */
  protected val secretKeyRingCollectionLock = new Object

  /** Create new PGP keyring generator. */
  def createPGPKeyRingGenerator(userID: String): PGPKeyRingGenerator =
    createPGPKeyRingGenerator(Generator.default, userID, KeyRing.defaultPassPhrase, 0xc0, new Date)
  /** Create new PGP keyring generator. */
  def createPGPKeyRingGenerator(userID: String, generator: XGenerator.AsymmetricCipherKeyPairGenerator): PGPKeyRingGenerator =
    createPGPKeyRingGenerator(generator, userID, KeyRing.defaultPassPhrase, 0xc0, new Date)
  /** Create new PGP keyring generator. */
  def createPGPKeyRingGenerator(userID: String, passPrase: String): PGPKeyRingGenerator =
    createPGPKeyRingGenerator(Generator.default, userID, passPrase, 0xc0, new Date)
  /** Create new PGP keyring generator. */
  def createPGPKeyRingGenerator(userID: String, passPrase: String, generator: XGenerator.AsymmetricCipherKeyPairGenerator): PGPKeyRingGenerator =
    createPGPKeyRingGenerator(generator, userID, passPrase, 0xc0, new Date)
  /** Create new PGP keyring generator. */
  def createPGPKeyRingGenerator(userID: String, passPrase: String, now: Date): PGPKeyRingGenerator =
    createPGPKeyRingGenerator(Generator.default, userID, passPrase, 0xc0, now)
  /** Create new PGP keyring generator. */
  def createPGPKeyRingGenerator(userID: String, passPrase: String, now: Date, generator: XGenerator.AsymmetricCipherKeyPairGenerator): PGPKeyRingGenerator =
    createPGPKeyRingGenerator(generator, userID, passPrase, 0xc0, now)
  /** Create new PGP keyring generator. */
  // Note: s2kcount is a number between 0 and 0xff that controls the
  // number of times to iterate the password hash before use. More
  // iterations are useful against offline attacks, as it takes more
  // time to check each password. The actual number of iterations is
  // rather complex, and also depends on the hash function in use.
  // Refer to Section 3.7.1.3 in rfc4880.txt. Bigger numbers give
  // you more iterations.  As a rough rule of thumb, when using
  // SHA256 as the hashing function, 0x10 gives you about 64
  // iterations, 0x20 about 128, 0x30 about 256 and so on till 0xf0,
  // or about 1 million iterations. The maximum you can go to is
  // 0xff, or about 2 million iterations.  We may use 0xc0 as a
  // default -- about 130,000 iterations.
  def createPGPKeyRingGenerator(generator: XGenerator.AsymmetricCipherKeyPairGenerator, userID: String,
    passPrase: String = KeyRing.defaultPassPhrase, s2kcount: Int = 0xc0, now: Date = new Date): PGPKeyRingGenerator = {
    // First create the master (signing) key with the generator.
    val masterKeyForSign = new BcPGPKeyPair(generator.signAlgorithm, generator.ackpg.generateKeyPair(), now)
    // Add a self-signature on the id.
    val signSSGen = new PGPSignatureSubpacketGenerator

    // Add signed metadata on the signature.
    // 1. Declare its purpose.
    signSSGen.setKeyFlags(true, KeyFlags.CERTIFY_OTHER | KeyFlags.SIGN_DATA)
    // 2. Set preferences for secondary crypto algorithms to use
    //    when sending messages to this key.
    signSSGen.setPreferredSymmetricAlgorithms(false, Array[Int](
      SymmetricKeyAlgorithmTags.AES_256,
      SymmetricKeyAlgorithmTags.AES_192,
      SymmetricKeyAlgorithmTags.AES_128))
    signSSGen.setPreferredHashAlgorithms(false, Array[Int](
      HashAlgorithmTags.SHA256,
      HashAlgorithmTags.SHA1,
      HashAlgorithmTags.SHA384,
      HashAlgorithmTags.SHA512,
      HashAlgorithmTags.SHA224))
    // 3. Request senders add additional checksums to the
    //    message (useful when verifying unsigned messages.)
    signSSGen.setFeature(false, Features.FEATURE_MODIFICATION_DETECTION)

    // Objects used to encrypt the secret key.
    val sha1Calc = new BcPGPDigestCalculatorProvider().get(HashAlgorithmTags.SHA1)
    val sha256Calc = new BcPGPDigestCalculatorProvider().get(HashAlgorithmTags.SHA256)
    val keyEncryptor = new BcPBESecretKeyEncryptorBuilder(SymmetricKeyAlgorithmTags.AES_256, sha256Calc, s2kcount).
      setSecureRandom(KeyRing.random).build(passPrase.toCharArray())
    val keySignerBuilder = new BcPGPContentSignerBuilder(masterKeyForSign.getPublicKey().getAlgorithm(), HashAlgorithmTags.SHA1)

    // Finally, create the keyring itself. The constructor
    // takes parameters that allow it to generate the self
    // signature.
    new PGPKeyRingGenerator(PGPSignature.POSITIVE_CERTIFICATION, masterKeyForSign, userID,
      sha1Calc, signSSGen.generate, null, keySignerBuilder, keyEncryptor)
  }
  /** Create new PGP encryption subkey. */
  def createPGPEncriptionSubKey(generator: XGenerator.AsymmetricCipherKeyPairGenerator = Generator.default,
    now: Date = new Date): (BcPGPKeyPair, PGPSignatureSubpacketGenerator) = {
    // Then an encryption subkey.
    val subKeyForEnc = new BcPGPKeyPair(generator.encAlgorithm, generator.ackpg.generateKeyPair(), now)
    // Create a signature on the encryption subkey.
    val encSSGen = new PGPSignatureSubpacketGenerator()
    // Add metadata to declare its purpose.
    encSSGen.setKeyFlags(false, KeyFlags.ENCRYPT_COMMS | KeyFlags.ENCRYPT_STORAGE)
    (subKeyForEnc, encSSGen)
  }
  /** Get private key from secret key. */
  def getPGPPrivateKey(secretKey: PGPSecretKey, passPhrase: String = KeyRing.defaultPassPhrase): PGPPrivateKey =
    secretKey.extractPrivateKey(new BcPBESecretKeyDecryptorBuilder(new BcPGPDigestCalculatorProvider()).
      build(passPhrase.toCharArray()))
  /** Get public key algorithm name. */
  def getPublicKeyAlgorithmName(key: Int) = key match {
    case PublicKeyAlgorithmTags.DIFFIE_HELLMAN ⇒ "Diffie Hellman"
    case PublicKeyAlgorithmTags.DSA ⇒ "DSA"
    case PublicKeyAlgorithmTags.EC ⇒ "EC"
    case PublicKeyAlgorithmTags.ECDSA ⇒ "ECDSA"
    case PublicKeyAlgorithmTags.ELGAMAL_ENCRYPT ⇒ "Elgamal (Encrypt)"
    case PublicKeyAlgorithmTags.ELGAMAL_GENERAL ⇒ "Elgamal (Encrypt & Sign)"
    case PublicKeyAlgorithmTags.RSA_ENCRYPT ⇒ "RSA (Encrypt)"
    case PublicKeyAlgorithmTags.RSA_GENERAL ⇒ "RSA (Encrypt & Sign)"
    case PublicKeyAlgorithmTags.RSA_SIGN ⇒ "RSA (Sign)"
    case _ ⇒ "Unknown"
  }
  /** Load collection of public keyrings. */
  def loadPublicKeyRingCollection(): PGPPublicKeyRingCollection =
    publicKeyRingCollectionLock.synchronized {
      log.info(s"Open public keyring collection.")
      if (!KeyRing.container.isOpen())
        throw new IllegalStateException("Workspace is not available.")
      val publicKeyRingResource = KeyRing.container.getFile(KeyRing.publicKeyRingName) // throws IllegalStateException: Workspace is closed.
      val result = if (publicKeyRingResource.exists()) {
        new PGPPublicKeyRingCollection(publicKeyRingResource.getContents(), new BcKeyFingerprintCalculator())
      } else {
        new PGPPublicKeyRingCollection(new ArrayList())
      }
      actualPublicKeyRingCollection = result
      result
    }
  /** Load collection of secret keyrings. */
  def loadSecretKeyRingCollection(): PGPSecretKeyRingCollection =
    secretKeyRingCollectionLock.synchronized {
      log.info(s"Open public keyring collection.")
      if (!KeyRing.container.isOpen())
        throw new IllegalStateException("Workspace is not available.")
      val privateKeyRingResource = KeyRing.container.getFile(KeyRing.secretKeyRingName) // throws IllegalStateException: Workspace is closed.
      val result = if (privateKeyRingResource.exists()) {
        new PGPSecretKeyRingCollection(privateKeyRingResource.getContents(), new BcKeyFingerprintCalculator())
      } else {
        new PGPSecretKeyRingCollection(new ArrayList())
      }
      actualSecretKeyRingCollection = result
      result
    }
  /** Get collection of public keyrings. */
  def publicKeyRingCollection: PGPPublicKeyRingCollection =
    publicKeyRingCollectionLock.synchronized { actualPublicKeyRingCollection }
  /** Save collection of public keyrings. */
  def savePublicKeyRingCollection(collection: PGPPublicKeyRingCollection) =
    publicKeyRingCollectionLock.synchronized {
      log.info(s"Save public keyring collection.")
      if (!KeyRing.container.isOpen())
        throw new IllegalStateException("Workspace is not available.")
      val publicKeyRingResource = KeyRing.container.getFile(KeyRing.publicKeyRingName) // throws IllegalStateException: Workspace is closed.
      val bOut = new ByteArrayOutputStream()
      collection.encode(bOut)
      bOut.close()
      val bIn = new ByteArrayInputStream(bOut.toByteArray())
      if (publicKeyRingResource.exists())
        publicKeyRingResource.setContents(bIn, true, false, new NullProgressMonitor)
      else
        publicKeyRingResource.create(bIn, true, new NullProgressMonitor)
      actualPublicKeyRingCollection = collection
    }
  /** Save collection of secret keyrings. */
  def saveSecretKeyRingCollection(collection: PGPSecretKeyRingCollection) =
    secretKeyRingCollectionLock.synchronized {
      log.info(s"Save secret keyring collection.")
      if (!KeyRing.container.isOpen())
        throw new IllegalStateException("Workspace is not available.")
      val secretKeyRingResource = KeyRing.container.getFile(KeyRing.secretKeyRingName) // throws IllegalStateException: Workspace is closed.
      val bOut = new ByteArrayOutputStream()
      collection.encode(bOut)
      bOut.close()
      val bIn = new ByteArrayInputStream(bOut.toByteArray())
      if (secretKeyRingResource.exists())
        secretKeyRingResource.setContents(bIn, true, false, new NullProgressMonitor)
      else
        secretKeyRingResource.create(bIn, true, new NullProgressMonitor)
      actualSecretKeyRingCollection = collection
    }

  /** Get collection of secret keyrings. */
  def secretKeyRingCollection: PGPSecretKeyRingCollection =
    secretKeyRingCollectionLock.synchronized { actualSecretKeyRingCollection }
}
