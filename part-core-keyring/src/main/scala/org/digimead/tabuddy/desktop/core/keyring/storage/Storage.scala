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

package org.digimead.tabuddy.desktop.core.keyring.storage

import java.io.{ IOException, PipedInputStream, PipedOutputStream }
import java.util.{ Properties, UUID }
import java.util.concurrent.{ ConcurrentHashMap, CountDownLatch, TimeUnit }
import org.bouncycastle.openpgp.PGPPublicKey
import org.digimead.digi.lib.api.XDependencyInjection
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.keyring.KeyRing
import org.digimead.tabuddy.desktop.core.keyring.storage.api.XStorage
import org.digimead.tabuddy.desktop.core.support.{ App, Timeout }
import org.eclipse.core.resources.IFile
import org.eclipse.core.runtime.NullProgressMonitor
import scala.collection.JavaConverters.mapAsScalaConcurrentMapConverter
import scala.concurrent.{ Await, Future }
import scala.language.implicitConversions

/**
 * TA Buddy public keys storage.
 */
class Storage extends XLoggable {
  /** Akka execution context. */
  implicit lazy val ec = App.system.dispatcher
  /** Map of objects that are shared between key instances. */
  protected val shared = new ConcurrentHashMap[UUID, (PGPPublicKey, Properties)].asScala

  /** Get key from shared map or load it from storage. */
  def getKey(id: UUID): PGPPublicKey = shared.get(id) match {
    case Some((key, properties)) ⇒
      key
    case None ⇒
      val keyFile = Storage.getKeyFile(id)
      val stream = keyFile.getContents(true)
      try KeyRing.importPGPPublicKey(stream)
      finally try stream.close() catch { case e: Throwable ⇒ log.error(s"Unable to close stream for ${keyFile}: " + e.getMessage(), e) }
  }
  /** Get or create properties container for the key. */
  def getProperty(id: UUID, key: Option[PGPPublicKey] = None): Properties =
    shared.get(id) match {
      case Some((key, properties)) ⇒
        properties
      case None ⇒
        shared.synchronized {
          shared.get(id) match {
            case Some((key, properties)) ⇒
              properties
            case None ⇒
              val properties = new Properties
              val propertiesFile = Storage.getPropertiesFile(id)
              if (propertiesFile.exists()) {
                val stream = propertiesFile.getContents(true)
                try properties.load(stream)
                finally try stream.close() catch { case e: Throwable ⇒ log.error(s"Unable to close stream for ${propertiesFile}: " + e.getMessage(), e) }
              }
              val keyObject = key getOrElse {
                throw new IllegalStateException(s"Unable to create properties without PGP key ${id}.")
              }
              this.shared(id) = (keyObject, properties)
              properties
          }
        }
    }
  /** List key ids. */
  def list(): Set[UUID] = shared.keySet.toSet ++ Storage.predefinedKeys.map(_.id) ++ {
    try {
      Storage.container.members(true).flatMap { resource ⇒
        if (resource.getName().endsWith(".key")) {
          try Option(UUID.fromString(resource.getName().dropRight(4)))
          catch {
            case e: Throwable ⇒
              log.error("Incorrect key file name " + resource.getName())
              None
          }
        } else
          None
      }.toSet
    } catch {
      case e: Throwable ⇒ Set() // return empty set if Storage.container is not available
    }
  }
  /** Save keys. */
  def save(): Unit = shared.synchronized {
    val futures = shared.map { case (id, (key, properties)) ⇒ Future { save(id) } }
    Await.result(Future.sequence(futures), Timeout.longer)
  }
  /** Save key. */
  def save(id: UUID): Unit = shared.get(id) foreach {
    case (key, properties) ⇒
      properties.synchronized {
        log.debug(s"Save key ${id} data.")
        val completionLatch = new CountDownLatch(2)
        val propertiesFile = Storage.getPropertiesFile(id)
        val keyFile = Storage.getKeyFile(id)
        val progressMonitor = new NullProgressMonitor() {
          override def done() = completionLatch.countDown()
        }

        // save properties
        if (!propertiesFile.exists() || properties.getProperty("hashCode", "0").toInt != properties.##) {
          log.debug("Save " + propertiesFile)
          val in = new PipedInputStream()
          val out = new PipedOutputStream(in)
          Future {
            if (!propertiesFile.exists())
              propertiesFile.create(in, true, progressMonitor)
            else
              propertiesFile.setContents(in, true, true, progressMonitor)
            in.close()
          } onFailure { case e: Throwable ⇒ log.error("Error while saving properties: " + e.getMessage(), e) }
          properties.setProperty("hashCode", properties.##.toString())
          try properties.store(out, id.toString())
          finally try out.close() catch { case e: Throwable ⇒ log.error(s"Unable to close stream for ${propertiesFile}: " + e.getMessage(), e) }
        } else {
          completionLatch.countDown()
        }

        // save key
        if (!keyFile.exists()) {
          log.debug("Save " + keyFile)
          val in = new PipedInputStream()
          val out = new PipedOutputStream(in)
          Future {
            if (!keyFile.exists())
              keyFile.create(in, true, progressMonitor)
            else
              keyFile.setContents(in, true, true, progressMonitor)
            in.close()
          } onFailure { case e: Throwable ⇒ log.error("Error while saving key: " + e.getMessage(), e) }
          try KeyRing.export(key, out)
          finally try out.close() catch { case e: Throwable ⇒ log.error(s"Unable to close stream for ${keyFile}: " + e.getMessage(), e) }
        } else {
          completionLatch.countDown()
        }

        completionLatch.await(Timeout.long.toMillis, TimeUnit.MILLISECONDS)
      }
  }
}

object Storage extends XLoggable {
  implicit def storage2implementation(s: Storage.type): Storage = s.inner
  /** Storage container. */
  lazy val container = {
    val folder = KeyRing.container.getFolder(DI.storageName)
    val progressMonitor = new NullProgressMonitor()
    if (!folder.exists())
      folder.create(true, true, progressMonitor)
    folder
  }

  /** Get file with key. */
  def getKeyFile(id: UUID): IFile = Storage.container.getFile(id.toString() + ".key")
  /** Get file with key meta information. */
  def getPropertiesFile(id: UUID): IFile = Storage.container.getFile(id.toString() + ".properties")
  /** Get Storage implementation. */
  def inner = DI.implementation
  /** Get predefined keys. */
  def predefinedKeys = DI.predefinedKeys

  /**
   * Key container.
   */
  case class Key(val id: UUID) extends XStorage.Key {
    /** Key itself. */
    val publicKey: PGPPublicKey = try Storage.getKey(id) catch {
      case e: Throwable ⇒ throw new IOException(s"Unable to load public key ${id}: " + e.getMessage(), e)
    }
    /** Key fingerprint. */
    lazy val fingerprint: Seq[Byte] = publicKey.getFingerprint().toSeq
    /** Key meta information. */
    protected lazy val properties: Properties = Storage.getProperty(id, Some(publicKey))

    /** Update properties information. */
    def withProperties[T](f: Properties ⇒ T) =
      properties.synchronized { f(properties) }
  }
  object Key {
    /** Create key with explicit parameters. */
    def apply(id: UUID, explicitKey: PGPPublicKey): Key =
      apply(id, explicitKey, new Properties)
    /** Create key with explicit parameters. */
    def apply(id: UUID, explicitKey: PGPPublicKey, explicitProperties: Properties): Key = {
      // Get or create properties
      val properties = Storage.shared.synchronized {
        Storage.shared.get(id) match {
          case Some((key, properties)) ⇒
            properties
          case None ⇒
            val properties = new Properties
            Storage.shared(id) = (explicitKey, properties)
            // Storage container may be unavailable at this time.
            properties
        }
      }
      properties.synchronized { properties.putAll(explicitProperties) }
      Key(id)
    }
  }
  /**
   * Dependency injection routines.
   */
  private object DI extends XDependencyInjection.PersistentInjectable {
    /** Storage implementation. */
    lazy val implementation = injectOptional[Storage] getOrElse new Storage()
    /**
     * Collection of predefined public keys.
     *
     * Each collected key must be:
     *  1. an instance of XStorage.Key
     *  2. has name that starts with "KeyRing.Key."
     */
    lazy val predefinedKeys: Set[XStorage.Key] = {
      val keys = bindingModule.bindings.filter {
        case (key, value) ⇒ classOf[XStorage.Key].isAssignableFrom(key.m.runtimeClass)
      }.map {
        case (key, value) ⇒
          key.name match {
            case Some(name) if name.startsWith("KeyRing.Key.") ⇒
              log.debug(s"'${name}' loaded.")
              bindingModule.injectOptional(key).asInstanceOf[Option[XStorage.Key]]
            case _ ⇒
              log.debug(s"'${key.name.getOrElse("Unnamed")}' public key skipped.")
              None
          }
      }.flatten.toSeq
      assert(keys.distinct.size == keys.size, "public keys contain duplicated entities in " + keys)
      keys.toSet
    }
    /** Storage folder name. */
    lazy val storageName = injectOptional[String]("KeyRing.Container.Storage") getOrElse "PublicKeyStorage"
  }
}
