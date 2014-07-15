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

package org.digimead.tabuddy.desktop.logic.ui.support.digest

import org.digimead.digi.lib.api.XDependencyInjection
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.logic.Messages
import org.digimead.tabuddy.desktop.logic.ui.support.digest.api.XDigestAdapter
import org.digimead.tabuddy.model.serialization.digest.{ Digest, Mechanism }
import org.eclipse.jface.dialogs.{ Dialog, TitleAreaDialog }
import org.eclipse.swt.SWT
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.widgets.{ Composite ⇒ JComposite, Control, Shell }
import org.eclipse.swt.widgets.Button

/**
 * Digest adapter interface.
 */
trait DigestAdapter extends XDigestAdapter {
  /** Identifier of the digest mechanism. */
  val identifier: Mechanism.Identifier

  /** Get composite for the digest configuration. */
  def composite(parent: JComposite, default: Option[Mechanism.Parameters]): Option[DigestAdapter.Composite]
  /** Get dialog for the digest configuration. */
  def dialog(parent: Shell, default: Option[Mechanism.Parameters], tag: String = Messages.digest_text): Option[DigestAdapter.Dialog]
  /** Flag indicating whether the parameters are supported. */
  def parameters: Boolean
}

object DigestAdapter extends XLoggable {
  /** Set of valid digest identifiers. */
  lazy val validIdentifiers = perIdentifier.map(_._1).toSet intersect Digest.perIdentifier.map(_._1).toSet
  /** All valid digest identifiers with an empty value. */
  lazy val allValidIdentifiers = validIdentifiers + Empty.Empty

  /** Map of all available digest adapters. */
  def perIdentifier = DI.perIdentifier

  /**
   * Composite with digest parameters
   */
  trait Composite extends JComposite {
    /** Get an error or digest parameters. */
    def get(): Option[Either[String, Mechanism.Parameters]]
  }
  /**
   * Dialog with digest parameters.
   */
  trait Dialog extends TitleAreaDialog {
    /** Get an error or digest parameters. */
    def get(): Option[Either[String, Mechanism.Parameters]]
  }
  /**
   * Empty digest adapter.
   */
  object Empty extends DigestAdapter {
    /** Identifier of the digest mechanism. */
    val identifier: Mechanism.Identifier = Empty

    /** Get composite for the digest configuration. */
    def composite(parent: JComposite, default: Option[Mechanism.Parameters]) = None
    /** Get dialog for the digest configuration. */
    def dialog(parent: Shell, default: Option[Mechanism.Parameters], tag: String = Messages.digest_text) = None
    /** Flag indicating whether the parameters are supported. */
    def parameters: Boolean = false

    object Empty extends Mechanism.Identifier {
      /** Mechanism name. */
      val name = "none"
      /** Mechanism description. */
      val description: String = "turn off digest calculation"
    }
  }
  /**
   * Dependency injection routines.
   */
  private object DI extends XDependencyInjection.PersistentInjectable {
    /**
     * Per identifier digest mechanism adapters map.
     *
     * Each collected adapter must be:
     *  1. an instance of (X)DigestAdapter
     *  2. has name that starts with "UI.Digest."
     */
    lazy val perIdentifier: Map[Mechanism.Identifier, DigestAdapter] = {
      val mechanisms = bindingModule.bindings.filter {
        case (key, value) ⇒ classOf[XDigestAdapter].isAssignableFrom(key.m.runtimeClass)
      }.map {
        case (key, value) ⇒
          key.name match {
            case Some(name) if name.startsWith("UI.Digest.") ⇒
              log.debug(s"'${name}' loaded.")
              bindingModule.injectOptional(key).asInstanceOf[Option[DigestAdapter]]
            case _ ⇒
              log.debug(s"'${key.name.getOrElse("Unnamed")}' digest adapter skipped.")
              None
          }
      }.flatten.toSeq
      assert(mechanisms.distinct.size == mechanisms.size, "digest adapters contain duplicated entities in " + mechanisms)
      Map(mechanisms.map(m ⇒ m.identifier -> m): _*)
    }
  }
}
