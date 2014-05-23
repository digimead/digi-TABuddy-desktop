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

package org.digimead.tabuddy.desktop.logic.payload.marker.api

/**
 * Base encryption interface.
 */
trait Encryption {
  /** Encryption description. */
  val description: String
  /** Unique encryption identifier. */
  val identifier: Encryption.Identifier

  /** Get encryption parameters. */
  def apply(key: Option[String], args: String*): Encryption.Parameters
  /** Encrypt data. */
  def encrypt(data: Array[Byte], parameters: Encryption.Parameters): Array[Byte]
  /** Decrypt data. */
  def decrypt(data: Array[Byte], parameters: Encryption.Parameters): Array[Byte]
}

object Encryption {
  /**
   * Identifier that is associated with the encryption.
   */
  trait Identifier extends Equals with java.io.Serializable {
    val name: String

    override def canEqual(that: Any) = that.isInstanceOf[Identifier]
    override def equals(that: Any): Boolean = that match {
      case that: Identifier ⇒ that.canEqual(this) && that.name.equals(this.name)
      case _ ⇒ false
    }
    override def hashCode = name.##

    override def toString = s"Encryption.Identifier(${name})"
  }
  /**
   * Encryption parameters.
   */
  trait Parameters {
    /** Encryption parameters as sequence of strings. */
    def arguments: Seq[String]
    /** Encryption instance. */
    def encryption: Encryption
    /** Encryption key. */
    def key: Option[String]
  }
}
