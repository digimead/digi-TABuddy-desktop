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

package org.digimead.tabuddy.desktop.logic.ui.support.encryption

import org.digimead.digi.lib.api.XDependencyInjection
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.logic.Messages
import org.digimead.tabuddy.desktop.logic.payload.marker.serialization.encryption.Encryption
import org.digimead.tabuddy.desktop.logic.ui.support.encryption.api.XEncryptionAdapter
import org.eclipse.jface.dialogs.TitleAreaDialog
import org.eclipse.swt.widgets.{ Composite ⇒ JComposite, Shell }

/**
 * Encryption adapter interface.
 */
trait EncryptionAdapter extends XEncryptionAdapter {
  /** Identifier of the encryption mechanism. */
  val identifier: Encryption.Identifier

  /** Get composite for the encryption configuration. */
  def composite(parent: JComposite, default: Option[Encryption.Parameters]): Option[EncryptionAdapter.Composite]
  /** Get dialog for the encryption configuration. */
  def dialog(parent: Shell, default: Option[Encryption.Parameters], tag: String = Messages.encryption_text): Option[EncryptionAdapter.Dialog]
  /** Flag indicating whether the parameters are supported. */
  def parameters: Boolean
}

object EncryptionAdapter extends XLoggable {
  /** Set of valid encryption identifiers. */
  lazy val validIdentifiers = perIdentifier.map(_._1).toSet intersect Encryption.perIdentifier.map(_._1).toSet
  /** All valid encryption identifiers with an empty value. */
  lazy val allValidIdentifiers = validIdentifiers + Empty.Empty

  /** Map of all available encryption adapters. */
  def perIdentifier = DI.perIdentifier

  /**
   * Composite with encryption parameters
   */
  trait Composite extends JComposite {
    /** Get an error or encryption parameters. */
    def get(): Option[Either[String, Encryption.Parameters]]
  }
  /**
   * Dialog with encryption parameters.
   */
  trait Dialog extends TitleAreaDialog {
    /** Get an error or encryption parameters. */
    def get(): Option[Either[String, Encryption.Parameters]]
  }
  /**
   * Empty encryption adapter.
   */
  object Empty extends EncryptionAdapter {
    /** Identifier of the encryption mechanism. */
    val identifier: Encryption.Identifier = Empty

    /** Get composite for the encryption configuration. */
    def composite(parent: JComposite, default: Option[Encryption.Parameters]) = None
    /** Get dialog for the encryption configuration. */
    def dialog(parent: Shell, default: Option[Encryption.Parameters], tag: String = Messages.encryption_text) = None
    /** Flag indicating whether the parameters are supported. */
    def parameters: Boolean = false

    object Empty extends Encryption.Identifier {
      /** Encryption name. */
      val name = "none"
      /** Encryption description. */
      val description: String = "turn off encryption"
    }
  }
  /**
   * Dependency injection routines.
   */
  private object DI extends XDependencyInjection.PersistentInjectable {
    /**
     * Per identifier encryption adapters map.
     *
     * Each collected encryption must be:
     *  1. an instance of (X)EncryptionAdapter
     *  2. has name that starts with "UI.Encryption."
     */
    lazy val perIdentifier: Map[Encryption.Identifier, EncryptionAdapter] = {
      val mechanisms = bindingModule.bindings.filter {
        case (key, value) ⇒ classOf[XEncryptionAdapter].isAssignableFrom(key.m.runtimeClass)
      }.map {
        case (key, value) ⇒
          key.name match {
            case Some(name) if name.startsWith("UI.Encryption.") ⇒
              log.debug(s"'${name}' loaded.")
              bindingModule.injectOptional(key).asInstanceOf[Option[EncryptionAdapter]]
            case _ ⇒
              log.debug(s"'${key.name.getOrElse("Unnamed")}' encryption adapter skipped.")
              None
          }
      }.flatten.toSeq
      assert(mechanisms.distinct.size == mechanisms.size, "encryption adapters contain duplicated entities in " + mechanisms)
      Map(mechanisms.map(m ⇒ m.identifier -> m): _*)
    }
  }
}
