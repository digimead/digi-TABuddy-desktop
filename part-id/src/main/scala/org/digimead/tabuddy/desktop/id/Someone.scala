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

package org.digimead.tabuddy.desktop.id

import java.io.ByteArrayInputStream
import java.util.{ Properties, UUID }
import org.bouncycastle.openpgp.{ PGPPublicKey, PGPPublicKeyRing, PGPSecretKey, PGPSecretKeyRing }
import org.digimead.tabuddy.desktop.core.keyring.KeyRing
import org.digimead.tabuddy.desktop.core.keyring.storage.Storage
import scala.collection.JavaConverters.asScalaIteratorConverter
import scala.language.implicitConversions

/**
 * Someone's part of ID.
 */
trait Someone {
  this: ID ⇒

  /** Someone's encryption key ID. */
  val thisEncryptionKeyID = 0x49977B98FA9EA7F2L
  /** Someone's public key ring. */
  val thisPlainPublicKeyRing = """
-----BEGIN PGP PUBLIC KEY BLOCK-----
Version: BCPG v1.50

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
rUn2SAF8gor5FaLPF1BN9Xg=
=GAXd
-----END PGP PUBLIC KEY BLOCK-----
"""

  /** Someone's signing key ID. */
  val thisSigningKeyID = 0x03967CDFDAE50DD0L

  /** Public key ring of a fuzzy group. */
  lazy val thisPublicKeyRing: PGPPublicKeyRing = {
    val publicKeyRing = KeyRing.importPGPPublicKeyRing(new ByteArrayInputStream(thisPlainPublicKeyRing.getBytes(io.Codec.UTF8.charSet)))
    val keys = publicKeyRing.getPublicKeys().asInstanceOf[java.util.Iterator[PGPPublicKey]].asScala.partition(_.isMasterKey())
    val masterKeys = keys._1.toIndexedSeq
    val subKeys = keys._2.toIndexedSeq
    masterKeys.foreach { masterKey ⇒
      if (!KeyRing.verify(masterKey, thatPublicKey))
        throw new SecurityException("Unable to load instance public key ring: untrusted master key 0x" +
          masterKey.getKeyID().toHexString.substring(8).toUpperCase())
    }
    subKeys.foreach { subKey ⇒
      if (!masterKeys.exists(KeyRing.verify(subKey, _)))
        throw new SecurityException("Unable to load instance public key ring: untrusted sub key 0x" +
          subKey.getKeyID().toHexString.substring(8).toUpperCase())
    }
    publicKeyRing
  }
  /** Signing public key of a fuzzy group.*/
  lazy val thisPublicSigningKey: PGPPublicKey = {
    val signingKey = thisPublicKeyRing.getPublicKey(thisSigningKeyID)
    if (signingKey == null)
      throw new SecurityException("Unable to load public signing key: 0x" +
        signingKey.getKeyID().toHexString.substring(8).toUpperCase() + " not found")
    if (!signingKey.isMasterKey())
      throw new SecurityException("Unable to load public signing key: not a master key 0x" +
        signingKey.getKeyID().toHexString.substring(8).toUpperCase())
    signingKey
  }
  /** Encryption secret key of a fuzzy group.*/
  lazy val thisSecretEncryptionKey: PGPSecretKey = {
    val encryptionKey = thisSecretKeyRing.getSecretKey(thisEncryptionKeyID)
    if (encryptionKey == null)
      throw new SecurityException("Unable to load secret encryption key: 0x" +
        encryptionKey.getKeyID().toHexString.substring(8).toUpperCase() + " not found")
    if (encryptionKey.isMasterKey())
      throw new SecurityException("Unable to load secret encryption key: not a sub key 0x" +
        encryptionKey.getKeyID().toHexString.substring(8).toUpperCase())
    encryptionKey
  }
  /** Secret key ring of a fuzzy group. */
  lazy val thisSecretKeyRing: PGPSecretKeyRing = {
    val secretKeyRing = KeyRing.importPGPSecretKeyRing(new ByteArrayInputStream(thisPlainSecretKeyRing.getBytes(io.Codec.UTF8.charSet)))
    val keys = secretKeyRing.getSecretKeys().asInstanceOf[java.util.Iterator[PGPSecretKey]].asScala.toIndexedSeq
    keys.foreach { secretKey ⇒
      if (thisPublicKeyRing.getPublicKey(secretKey.getKeyID()) == null)
        throw new SecurityException("Unable to load instance private key ring: unbinded private key 0x" +
          secretKey.getKeyID().toHexString.substring(8).toUpperCase())
    }
    secretKeyRing
  }
  /** Signing secret key of a fuzzy group.*/
  lazy val thisSecretSigningKey: PGPSecretKey = {
    val signingKey = thisSecretKeyRing.getSecretKey(thisSigningKeyID)
    if (signingKey == null)
      throw new SecurityException("Unable to load secret signing key: 0x" +
        signingKey.getKeyID().toHexString.substring(8).toUpperCase() + " not found")
    if (!signingKey.isMasterKey())
      throw new SecurityException("Unable to load secret signing key: not a master key 0x" +
        signingKey.getKeyID().toHexString.substring(8).toUpperCase())
    signingKey
  }
  /** Encryption public key of a fuzzy group.*/
  lazy val thisPublicEncryptionKey: PGPPublicKey = {
    val encryptionKey = thisSecretKeyRing.getPublicKey(thisEncryptionKeyID)
    if (encryptionKey == null)
      throw new SecurityException("Unable to load public encryption key: 0x" +
        encryptionKey.getKeyID().toHexString.substring(8).toUpperCase() + " not found")
    if (encryptionKey.isMasterKey())
      throw new SecurityException("Unable to load public encryption key: not a sub key 0x" +
        encryptionKey.getKeyID().toHexString.substring(8).toUpperCase())
    encryptionKey
  }
  /** Storage public key of a fuzzy group.*/
  lazy val thisPublicStorageKey: Storage.Key = Storage.Key(UUID.fromString("00000000-0000-0000-0000-000000000002"),
    thisPublicSigningKey,
    {
      val properties = new Properties()
      properties.setProperty(KeyRing.Attr.Nick, "I am.")
      properties.setProperty(KeyRing.Attr.Description, "My key.")
      properties
    })
}
