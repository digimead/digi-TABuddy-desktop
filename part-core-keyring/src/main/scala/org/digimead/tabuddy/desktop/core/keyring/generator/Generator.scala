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

import java.util.Date
import org.bouncycastle.bcpg.{ HashAlgorithmTags, SymmetricKeyAlgorithmTags }
import org.bouncycastle.bcpg.sig.{ Features, KeyFlags }
import org.bouncycastle.crypto.AsymmetricCipherKeyPairGenerator
import org.bouncycastle.openpgp.{ PGPKeyRingGenerator, PGPSignature, PGPSignatureSubpacketGenerator }
import org.bouncycastle.openpgp.operator.bc.{ BcPBESecretKeyEncryptorBuilder, BcPGPContentSignerBuilder, BcPGPDigestCalculatorProvider, BcPGPKeyPair }
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.keyring.KeyRing

/**
 * PGP keyring generator base class.
 */
abstract class Generator extends api.Generator {
  this: Loggable ⇒
  /** PublicKeyAlgorithmTags for encryption keys. */
  val encAlgorithm: Int
  /** PublicKeyAlgorithmTags for signature keys. */
  val signAlgorithm: Int

  /** Create new asymmetric key pair generator. */
  def apply(args: AnyRef*): api.Generator.AsymmetricCipherKeyPairGenerator
}

object Generator extends Loggable {
  /** Get the default generator. */
  def default = DI.default
  /** Map of all available keyring generators . */
  def perIdentifier = DI.perIdentifier

  /**
   * Dependency injection routines.
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** Default generator algorithm. */
    lazy val default = injectOptional[api.Generator.AsymmetricCipherKeyPairGenerator]("KeyRing.Generator.Default") getOrElse (new RSAGenerator)(4096: Integer)
    /**
     * Per identifier generators map.
     *
     * Each collected mechanism must be:
     *  1. an instance of api.Generator
     *  2. has name that starts with "KeyRing.Generator."
     */
    lazy val perIdentifier: Map[api.Generator.Identifier, Generator] = {
      val generators = bindingModule.bindings.filter {
        case (key, value) ⇒ classOf[api.Generator].isAssignableFrom(key.m.runtimeClass)
      }.map {
        case (key, value) ⇒
          key.name match {
            case Some(name) if name.startsWith("KeyRing.Generator.") ⇒
              log.debug(s"'${name}' loaded.")
              bindingModule.injectOptional(key).asInstanceOf[Option[Generator]]
            case _ ⇒
              log.debug(s"'${key.name.getOrElse("Unnamed")}' keyring generator skipped.")
              None
          }
      }.flatten.toSeq
      assert(generators.distinct.size == generators.size, "Keyring generators contain duplicated entities in " + generators)
      Map(generators.map(g ⇒ g.identifier -> g): _*)
    }
  }
}
