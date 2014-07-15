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

package org.digimead.tabuddy.desktop.logic.ui.support.signature

import org.digimead.digi.lib.api.XDependencyInjection
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.logic.Messages
import org.digimead.tabuddy.desktop.logic.ui.support.signature.api.XSignatureAdapter
import org.digimead.tabuddy.model.serialization.signature.{ Mechanism, Signature }
import org.eclipse.jface.dialogs.TitleAreaDialog
import org.eclipse.swt.SWT
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.widgets.{ Button, Composite ⇒ JComposite, Control, Shell }

/**
 * Signature adapter interface.
 */
trait SignatureAdapter extends XSignatureAdapter {
  /** Identifier of the signature mechanism. */
  val identifier: Mechanism.Identifier

  /** Get composite for the signature configuration. */
  def composite(parent: JComposite, default: Option[Mechanism.Parameters]): Option[SignatureAdapter.Composite]
  /** Get dialog for the signature configuration. */
  def dialog(parent: Shell, default: Option[Mechanism.Parameters], tag: String = Messages.signature_text): Option[SignatureAdapter.Dialog]
  /** Flag indicating whether the parameters are supported. */
  def parameters: Boolean
}

object SignatureAdapter extends XLoggable {
  /** Set of valid signature identifiers. */
  lazy val validIdentifiers = perIdentifier.map(_._1).toSet intersect Signature.perIdentifier.map(_._1).toSet
  /** All valid signature identifiers with an empty value. */
  lazy val allValidIdentifiers = validIdentifiers + Empty.Empty

  /** Map of all available signature adapters. */
  def perIdentifier = DI.perIdentifier

  /**
   * Composite with signature parameters
   */
  trait Composite extends JComposite {
    /** Get an error or signature parameters. */
    def get(): Option[Either[String, Mechanism.Parameters]]
  }
  /**
   * Dialog with signature parameters.
   */
  trait Dialog extends TitleAreaDialog {
    /** Get an error or signature parameters. */
    def get(): Option[Either[String, Mechanism.Parameters]]
  }
  /**
   * Empty signature adapter.
   */
  object Empty extends SignatureAdapter {
    /** Identifier of the signature mechanism. */
    val identifier: Mechanism.Identifier = Empty

    /** Get composite for the signature configuration. */
    def composite(parent: JComposite, default: Option[Mechanism.Parameters]) = None
    /** Get dialog for the signature configuration. */
    def dialog(parent: Shell, default: Option[Mechanism.Parameters], tag: String = Messages.signature_text) = None
    /** Flag indicating whether the parameters are supported. */
    def parameters: Boolean = false

    object Empty extends Mechanism.Identifier {
      /** Mechanism name. */
      val name = "none"
      /** Mechanism description. */
      val description: String = "turn off signature generation"
    }
  }
  /**
   * Dependency injection routines.
   */
  private object DI extends XDependencyInjection.PersistentInjectable {
    /**
     * Per identifier signature mechanism adapters map.
     *
     * Each collected adapter must be:
     *  1. an instance of (X)SignatureAdapter
     *  2. has name that starts with "UI.Signature."
     */
    lazy val perIdentifier: Map[Mechanism.Identifier, SignatureAdapter] = {
      val mechanisms = bindingModule.bindings.filter {
        case (key, value) ⇒ classOf[XSignatureAdapter].isAssignableFrom(key.m.runtimeClass)
      }.map {
        case (key, value) ⇒
          key.name match {
            case Some(name) if name.startsWith("UI.Signature.") ⇒
              log.debug(s"'${name}' loaded.")
              bindingModule.injectOptional(key).asInstanceOf[Option[SignatureAdapter]]
            case _ ⇒
              log.debug(s"'${key.name.getOrElse("Unnamed")}' signature adapter skipped.")
              None
          }
      }.flatten.toSeq
      assert(mechanisms.distinct.size == mechanisms.size, "signature adapters contain duplicated entities in " + mechanisms)
      Map(mechanisms.map(m ⇒ m.identifier -> m): _*)
    }
  }
}
