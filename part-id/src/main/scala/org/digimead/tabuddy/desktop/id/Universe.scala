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
import org.bouncycastle.openpgp.PGPPublicKey
import org.digimead.tabuddy.desktop.core.keyring.KeyRing
import org.digimead.tabuddy.desktop.core.keyring.storage.Storage
import scala.language.implicitConversions

/**
 * Universe part of ID.
 */
trait Universe {
  this: ID ⇒

  /** Universe public key that separates sources to friends and enemies. */
  val thatPlainPublicKey = """
-----BEGIN PGP PUBLIC KEY BLOCK-----
Version: GnuPG v2.0.22 (GNU/Linux)

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
C0/8QdbSltU2uw9MD1+IHNz5PaK4uGGdVO9r1oU=
=7Buk
-----END PGP PUBLIC KEY BLOCK-----
"""
  /** Universe public key. */
  lazy val thatPublicKey: PGPPublicKey = KeyRing.importPGPPublicKey(new ByteArrayInputStream(thatPlainPublicKey.getBytes(io.Codec.UTF8.charSet)))
  /** Universe storage public key */
  lazy val thatPublicStorageKey: Storage.Key = Storage.Key(UUID.fromString("00000000-0000-0000-0000-000000000000"),
    thatPublicKey,
    {
      val properties = new Properties()
      properties.setProperty(KeyRing.Attr.Nick, "TA Buddy Universe")
      properties.setProperty(KeyRing.Attr.Description, "Infrastructure wide public key")
      properties.setProperty(KeyRing.Attr.EMail, "digimead@gmail.org")
      properties
    })
}
