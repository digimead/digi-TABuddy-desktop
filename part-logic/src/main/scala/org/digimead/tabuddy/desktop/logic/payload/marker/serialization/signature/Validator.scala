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

package org.digimead.tabuddy.desktop.logic.payload.marker.serialization.signature

import java.security.PublicKey
import java.util.UUID
import java.util.concurrent.locks.ReentrantReadWriteLock
import org.digimead.digi.lib.api.XDependencyInjection
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.Preferences
import org.digimead.tabuddy.desktop.core.keyring.storage.Storage
import org.digimead.tabuddy.desktop.logic.payload.marker.serialization.signature.api.XValidator
import org.digimead.tabuddy.model.serialization.signature.Signature
import org.eclipse.jface.preference.PreferenceStore
import scala.language.implicitConversions

/**
 * Signature validators container.
 */
class Validator extends XLoggable {
  /** Preference store. */
  protected lazy val preferenceStore = Preferences.getPreferenceStore(getClass)
  /** Preference rwl. */
  protected val preferenceLock = new ReentrantReadWriteLock()

  /** Get validator with the specified UUID. */
  def get(id: UUID): Option[XValidator] = Validator.predefinedValidators.find(_.id == id) orElse {
    withPreferenceStoreRO { store ⇒
      val dataField = id.toString() + "_data"
      val nameField = id.toString() + "_name"
      val descriptionField = id.toString() + "_description"
      val validator = for {
        name ← Option(store.getString(getNameField(id)))
        description ← Option(store.getString(getDescriptionField(id)))
        implicitlyAccept ← Option(store.getBoolean(getPositiveField(id)))
        acceptUnsigned ← Option(store.getBoolean(getSignedField(id)))
        data ← Option(store.getString(getDataField(id)))
      } yield try {
        val elements = data.split(";")
        val implicitlyAccept = elements(0).toBoolean
        val acceptUnsigned = elements(1).toBoolean
        val keys = for (keyId ← elements.drop(2))
          yield Storage.Key(UUID.fromString(keyId))
        val rule = XValidator.Rule(implicitlyAccept, acceptUnsigned, keys.toSeq)
        Some(new Validator.Custom(id, Symbol(name), description, rule))
      } catch {
        case e: Throwable ⇒
          log.error(s"Unable to unpack validator value for ${id}: " + e.getMessage(), e)
          None
      }
      validator getOrElse {
        log.fatal(s"Broken validator ${id}")
        None
      }
    }
  }
  /** Get user validators. */
  def getAll(): Set[XValidator] = withPreferenceStoreRO { store ⇒
    store.preferenceNames().par.flatMap(_ match {
      case key if key.endsWith("_name") ⇒
        try Some(UUID.fromString(key.dropRight(5)))
        catch {
          case e: Throwable ⇒
            log.error(s"Incorrect preference key ${key}: " + e.getMessage, e)
            None
        }
      case _ ⇒
        None
    }).toSet.flatMap(get).seq
  }
  /** Get name field. */
  def getNameField(id: UUID) = id.toString() + "_name"
  /** Get description field. */
  def getDescriptionField(id: UUID) = id.toString() + "_description"
  /** Get positive field. */
  def getPositiveField(id: UUID) = id.toString() + "_positive"
  /** Get signed field. */
  def getSignedField(id: UUID) = id.toString() + "_signed"
  /** Get data field. */
  def getDataField(id: UUID) = id.toString() + "_data"
  /** Get all available validators. */
  def validators() = Validator.predefinedValidators ++ getAll
  /** Update exists or save new validator */
  def set(validator: XValidator): Unit = withPreferenceStoreRW { store ⇒
    val id = validator.id
    store.setValue(getNameField(id), validator.name.name)
    store.setValue(getDescriptionField(id), validator.description)
    store.setValue(getPositiveField(id), validator.rule.implicitlyAccept)
    store.setValue(getSignedField(id), validator.rule.acceptUnsigned)
    store.setValue(getDataField(id), validator.rule.keys.map(_.id.toString()).mkString(";"))
    store.save()
  }
  /** Remove validator from preference store. */
  def remove(id: UUID): Unit = withPreferenceStoreRW { store ⇒
    store.setToDefault(getNameField(id))
    store.setToDefault(getDescriptionField(id))
    store.setToDefault(getPositiveField(id))
    store.setToDefault(getSignedField(id))
    store.setToDefault(getDataField(id))
    store.save()
  }
  /** Invoke f with read only preference store. */
  def withPreferenceStoreRO[T](store: PreferenceStore ⇒ T) = {
    preferenceLock.readLock().lock()
    try store(preferenceStore) finally preferenceLock.readLock().unlock()
  }
  /** Invoke f with read write preference store. */
  def withPreferenceStoreRW[T](store: PreferenceStore ⇒ T) = {
    preferenceLock.writeLock().lock()
    try store(preferenceStore) finally preferenceLock.writeLock().unlock()
  }
}

//
// Validator format
//   UUID_name - String name
//   UUID_description - String description
//   UUID_positive - Boolean
//     true - accept all except
//     false - accept none except
//   UUID_signed
//     true - accept unsigned
//     false - accept only signed
//   UUID_data
//     keyId;keyId;keyId - UUID keys
//

object Validator extends XLoggable {
  implicit def validator2implementation(v: Validator.type): Validator = v.inner

  /** Get Validator implementation. */
  def inner = DI.implementation
  /** Get collected predefined validators. */
  def predefinedValidators = DI.predefinedValidators

  /**
   * Predefined 'Accept all' validator.
   */
  class AcceptAll extends XValidator {
    /** Validator ID. */
    val id: UUID = UUID.fromString("333df070-0b1a-11e4-9191-0800200c9a66")
    /** Validator name. */
    val name: Symbol = 'AcceptAll
    /** Validator description. */
    val description: String = "accept all data, signed and unsigned"

    /** Get validator rule. */
    val rule: XValidator.Rule = XValidator.Rule(true, true, Seq.empty)
    /** Validator routine. */
    val validator: Option[PublicKey] ⇒ Boolean = Signature.acceptAll
  }
  /**
   * Predefined 'Accept signed' validator.
   */
  class AcceptSigned extends XValidator {
    /** Validator ID. */
    val id: UUID = UUID.fromString("76525540-0b1a-11e4-9191-0800200c9a66")
    /** Validator name. */
    val name: Symbol = 'AcceptSigned
    /** Validator description. */
    val description: String = "accept only signed data"

    /** Get validator rule. */
    val rule: XValidator.Rule = XValidator.Rule(true, false, Seq.empty)
    /** Validator routine. */
    val validator: Option[PublicKey] ⇒ Boolean = Signature.acceptSigned
  }
  /**
   * Custom validator.
   */
  class Custom(val id: UUID, val name: Symbol, val description: String, val rule: XValidator.Rule) extends XValidator {
    /** Validator routine. */
    val validator: Option[PublicKey] ⇒ Boolean = (key) ⇒ {
      false
    }
  }
  /**
   * Dependency injection routines.
   */
  private object DI extends XDependencyInjection.PersistentInjectable {
    /** Validator implementation. */
    lazy val implementation = injectOptional[Validator] getOrElse new Validator()
    /**
     * Collection of validators.
     *
     * Each collected validator must be:
     *  1. an instance of XValidator
     *  2. has name that starts with "Signature.Validator."
     */
    lazy val predefinedValidators: Set[XValidator] = {
      val validators = bindingModule.bindings.filter {
        case (key, value) ⇒ classOf[XValidator].isAssignableFrom(key.m.runtimeClass)
      }.map {
        case (key, value) ⇒
          key.name match {
            case Some(name) if name.startsWith("Signature.Validator.") ⇒
              log.debug(s"'${name}' loaded.")
              bindingModule.injectOptional(key).asInstanceOf[Option[XValidator]]
            case _ ⇒
              log.debug(s"'${key.name.getOrElse("Unnamed")}' validator skipped.")
              None
          }
      }.flatten.toSeq
      assert(validators.distinct.size == validators.size, "signature validators contain duplicated entities in " + validators)
      validators.toSet
    }
  }
}
