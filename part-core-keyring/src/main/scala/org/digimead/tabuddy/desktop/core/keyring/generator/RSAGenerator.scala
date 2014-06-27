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

package org.digimead.tabuddy.desktop.core.keyring.generator

import java.math.BigInteger
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags
import org.bouncycastle.crypto.AsymmetricCipherKeyPairGenerator
import org.bouncycastle.crypto.generators.RSAKeyPairGenerator
import org.bouncycastle.crypto.params.RSAKeyGenerationParameters
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.keyring.KeyRing
import org.digimead.tabuddy.desktop.core.keyring.generator.api.XGenerator

/**
 * RSA PGP keyring generator.
 */
class RSAGenerator extends Generator with XLoggable {
  /** PublicKeyAlgorithmTags for encryption keys. */
  // PublicKeyAlgorithmTags.RSA_ENCRYPT is "unknown algorithm 2" with gpg (GnuPG) 2.0.22 libgcrypt 1.5.3
  val encAlgorithm: Int = PublicKeyAlgorithmTags.RSA_GENERAL
  /** Identifier of the generation mechanism. */
  val identifier = RSAGenerator.Identifier
  /** PublicKeyAlgorithmTags for signature keys. */
  // PublicKeyAlgorithmTags.RSA_SIGN is "unknown algorithm 3" with gpg (GnuPG) 2.0.22 libgcrypt 1.5.3
  val signAlgorithm: Int = PublicKeyAlgorithmTags.RSA_GENERAL

  /** Create new PGP secret keyring. */
  def apply(args: AnyRef*): XGenerator.AsymmetricCipherKeyPairGenerator = args match {
    case Seq(strength: Integer) ⇒
      if (strength == 1024 || strength == 2048 || strength == 4096)
        XGenerator.AsymmetricCipherKeyPairGenerator(signAlgorithm, encAlgorithm, createACKPGenerator(strength))
      else
        throw new IllegalArgumentException("Illegal key size argument (must be 1024, 2048 or 4096): " + args)
    case _ ⇒
      throw new IllegalArgumentException("Illegal arguments: " + args)
  }
  /** Create new PGP asymmetric cipher key pair generator. */
  def createACKPGenerator(strength: Int = 2048): AsymmetricCipherKeyPairGenerator = {
    val generator = new RSAKeyPairGenerator()
    generator.init(new RSAKeyGenerationParameters(BigInteger.valueOf(0x10001), KeyRing.random, strength, 12))
    generator
  }
}

object RSAGenerator {
  /**
   * RSAGenerator identifier.
   */
  object Identifier extends XGenerator.Identifier { val name = "BouncyCastleRSA" }
}
