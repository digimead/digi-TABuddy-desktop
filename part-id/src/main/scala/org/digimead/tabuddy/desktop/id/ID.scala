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

import com.google.common.io.Files
import java.io.File
import org.bouncycastle.openpgp.{ PGPPublicKey, PGPPublicKeyRing, PGPSecretKey, PGPSecretKeyRing }
import org.digimead.digi.lib.api.XDependencyInjection
import org.digimead.tabuddy.desktop.core.support.App
import scala.language.implicitConversions

/**
 * TABuddy identification information.
 */
class ID extends ID.Interface with Someone with Universe with Guest {
  /** Someone's secret key ring. */
  val thisPlainSecretKeyRing: String = {
    if (thisPlainPublicKeyRing == guestPlainPublicKeyRing)
      // Unregistered version. Use guest key as Someone's
      guestPlainSecretKeyRing
    else {
      if (!ID.secretKeyRingLocation.exists())
        throw new RuntimeException("Unable to find secret key at " + ID.secretKeyRingLocation.getAbsolutePath())
      Files.toString(ID.secretKeyRingLocation, io.Codec.UTF8.charSet)
    }
  }
}

/**
 * Identificator for the fuzzy group of users.
 */
object ID {
  implicit def ID2implementation(i: ID.type): ID = i.inner

  /** Get ID implementation. */
  def inner = DI.implementation
  /** Get secret key location. */
  def secretKeyRingLocation = DI.secretKeyRingLocation

  /** ID interface. */
  trait Interface {
    /** Universe public key. */
    def thatPublicKey: PGPPublicKey
    /** Encryption public key of fuzzy group.*/
    def thisPublicEncryptionKey: PGPPublicKey
    /** Public key ring of fuzzy group. */
    def thisPublicKeyRing: PGPPublicKeyRing
    /** Signing public key of fuzzy group.*/
    def thisPublicSigningKey: PGPPublicKey
    /** Encryption secret key of fuzzy group.*/
    def thisSecretEncryptionKey: PGPSecretKey
    /** Secret key ring of fuzzy group. */
    def thisSecretKeyRing: PGPSecretKeyRing
    /** Signing secret key of fuzzy group.*/
    def thisSecretSigningKey: PGPSecretKey
  }
  /**
   * Dependency injection routines
   */
  private object DI extends XDependencyInjection.PersistentInjectable {
    /** ID implementation. */
    lazy val implementation = injectOptional[ID] getOrElse new ID
    /** Someone's secret key ring location. */
    lazy val secretKeyRingLocation = injectOptional[File]("My.Key.Location") getOrElse new File(App.data, "tabuddy.key")
  }
}
