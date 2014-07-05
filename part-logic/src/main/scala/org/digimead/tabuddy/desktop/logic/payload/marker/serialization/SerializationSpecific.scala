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

package org.digimead.tabuddy.desktop.logic.payload.marker.serialization

import java.net.URI
import java.util.UUID
import org.bouncycastle.util.encoders.Base64
import org.digimead.tabuddy.desktop.core.keyring.KeyRing
import org.digimead.tabuddy.desktop.id.ID
import org.digimead.tabuddy.desktop.logic.payload.Payload
import org.digimead.tabuddy.desktop.logic.payload.marker.GraphMarker
import org.digimead.tabuddy.desktop.logic.payload.marker.api.XGraphMarker
import org.digimead.tabuddy.desktop.logic.payload.marker.serialization.encryption.Encryption
import org.digimead.tabuddy.model.serialization.Serialization
import org.digimead.tabuddy.model.serialization.digest.Digest
import org.digimead.tabuddy.model.serialization.signature.Signature
import scala.collection.JavaConverters.asScalaSetConverter

/**
 * Part of the graph marker that contains serialization settings specific logic.
 */
trait SerializationSpecific {
  this: GraphMarker ⇒
  /** Load container encryption settings from java.util.Properties. */
  def containerEncryption: XGraphMarker.Encryption =
    encryptionLoad(GraphMarker.fieldContainerEncryption, "container")
  /** Store container encryption settings to java.util.Properties. */
  def containerEncryption_=(settings: XGraphMarker.Encryption) =
    encryptionStore(settings, GraphMarker.fieldContainerEncryption, "container")
  /** Load content encryption settings from java.util.Properties. */
  def contentEncryption: XGraphMarker.Encryption =
    encryptionLoad(GraphMarker.fieldContentEncryption, "content")
  /** Store content encryption settings to java.util.Properties. */
  def contentEncryption_=(settings: XGraphMarker.Encryption) =
    encryptionStore(settings, GraphMarker.fieldContentEncryption, "content")
  /** Load default serialization identifier value from java.util.Properties. */
  def defaultSerialization: Serialization.Identifier = graphProperties { p ⇒
    Option(p.getProperty(GraphMarker.fieldDefaultSerialization)).map(serializationExtension ⇒
      Serialization.perIdentifier.find(_._1.extension.name == serializationExtension) match {
        case Some((identifier, mechanism)) ⇒
          identifier
        case None ⇒
          log.error(s"Unable to find serialization mechanism with extension '${serializationExtension}', use default ${Payload.defaultSerialization}")
          Payload.defaultSerialization
      }) getOrElse Payload.defaultSerialization
  }
  /** Load digest settings from java.util.Properties. */
  def digest: XGraphMarker.Digest = graphProperties { p ⇒
    // acquire
    val acquireSetting = p.getProperty(GraphMarker.fieldDigestAcquire) match {
      case "required" ⇒ Some(true)
      case "optional" ⇒ Some(false)
      case _ ⇒ None
    }
    // freeze
    val URIArgumentsLength = Option(p.getProperty(GraphMarker.fieldDigestFreeze)) match {
      case Some(length) if length == "0" ⇒
        return XGraphMarker.Digest(acquireSetting, Some(Map()))
      case Some(length) ⇒
        length.toInt
      case None ⇒
        return XGraphMarker.Digest(acquireSetting, None)
    }
    if (URIArgumentsLength < 0)
      throw new IllegalStateException("Digest URI arguments length must be greater than zero.")
    val URIArguments = for (i ← 0 until URIArgumentsLength)
      yield p.getProperty(s"${GraphMarker.fieldDigestFreeze}_${i}") match {
      case null ⇒ throw new IllegalStateException("There is 'null' value in digest URI argument.")
      case location ⇒ (i, new URI(location))
    }
    val freezeSettings = for ((i, location) ← URIArguments) yield {
      val argumentsLength = Option(p.getProperty(s"${GraphMarker.fieldDigestFreeze}_${i}_0")).map(_.toInt).getOrElse(0)
      if (argumentsLength < 0)
        throw new IllegalStateException(s"Digest arguments length for '${location}' must be greater than zero.")
      val arguments = for (n ← (1 to argumentsLength).toList)
        yield p.getProperty(s"${GraphMarker.fieldDigestFreeze}_${i}_${n}") match {
        case null ⇒ throw new IllegalStateException("There is 'null' value in digest argument.")
        case argument ⇒ argument
      }
      val parameters = arguments match {
        case mechanismIdentifier :: xs ⇒
          Digest.perIdentifier.find(_._1.name == mechanismIdentifier) match {
            case Some((identifier, mechanism)) ⇒
              val (algorithmName :: args) = xs
              mechanism(algorithmName, args: _*)
            case None ⇒
              throw new IllegalStateException(s"Unable to find registered digest mechanism for ${mechanismIdentifier}.")
          }
        case Nil ⇒
          Digest.NoDigest
      }
      location -> parameters
    }
    XGraphMarker.Digest(acquireSetting, Some(Map(freezeSettings: _*)))
  }
  /** Store digest settings to java.util.Properties. */
  def digest_=(settings: XGraphMarker.Digest) = graphPropertiesUpdate { p ⇒
    // acquire
    settings.acquire match {
      case Some(true) ⇒ p.setProperty(GraphMarker.fieldDigestAcquire, "required")
      case Some(false) ⇒ p.setProperty(GraphMarker.fieldDigestAcquire, "optional")
      case None ⇒ p.remove(GraphMarker.fieldDigestAcquire)
    }
    // freeze
    p.stringPropertyNames().asScala.filter(_.startsWith(GraphMarker.fieldDigestFreeze)).foreach(p.remove)
    settings.freeze.foreach { parameters ⇒
      if (parameters.isEmpty) {
        p.setProperty(GraphMarker.fieldDigestFreeze, 0.toString)
      } else {
        p.setProperty(GraphMarker.fieldDigestFreeze, parameters.size.toString)
        var index = 0
        parameters.foreach {
          case (uri, parameters) ⇒
            val arguments = parameters.arguments
            p.setProperty(s"${GraphMarker.fieldDigestFreeze}_${index}", uri.toString())
            if (parameters == Digest.NoDigest) {
              p.setProperty(s"${GraphMarker.fieldDigestFreeze}_${index}_0", 0.toString)
            } else {
              p.setProperty(s"${GraphMarker.fieldDigestFreeze}_${index}_0", (arguments.size + 2).toString())
              p.setProperty(s"${GraphMarker.fieldDigestFreeze}_${index}_1", parameters.mechanism.identifier.name)
              p.setProperty(s"${GraphMarker.fieldDigestFreeze}_${index}_2", parameters.algorithm)
              var subindex = 3
              arguments.foreach { argument ⇒
                p.setProperty(s"${GraphMarker.fieldDigestFreeze}_${index}_${subindex}", argument)
                subindex += 1
              }
            }
            index += 1
        }
      }
    }
  }
  /** Load signature settings from java.util.Properties. */
  def signature: XGraphMarker.Signature = graphProperties { p ⇒
    // acquire
    val acquireSetting = p.getProperty(GraphMarker.fieldSignatureAcquire) match {
      case null ⇒ None
      case validatorId ⇒ Some(UUID.fromString(validatorId))
    }
    // freeze
    val URIArgumentsLength = Option(p.getProperty(GraphMarker.fieldSignatureFreeze)) match {
      case Some(length) if length == "0" ⇒
        return XGraphMarker.Signature(acquireSetting, Some(Map()))
      case Some(length) ⇒
        length.toInt
      case None ⇒
        return XGraphMarker.Signature(acquireSetting, None)
    }
    if (URIArgumentsLength < 0)
      throw new IllegalStateException("Signature URI arguments length must be greater than zero.")
    val URIArguments = for (i ← 0 until URIArgumentsLength)
      yield p.getProperty(s"${GraphMarker.fieldSignatureFreeze}_${i}") match {
      case null ⇒ throw new IllegalStateException("There is 'null' value in signature URI argument.")
      case location ⇒ (i, new URI(location))
    }
    val freezeSettings = for ((i, location) ← URIArguments) yield {
      val argumentsLength = Option(p.getProperty(s"${GraphMarker.fieldSignatureFreeze}_${i}_0")).map(_.toInt).getOrElse(0)
      if (argumentsLength < 0)
        throw new IllegalStateException(s"Signature arguments length for '${location}' must be greater than zero.")
      val arguments = for (n ← (1 to argumentsLength).toList)
        yield p.getProperty(s"${GraphMarker.fieldSignatureFreeze}_${i}_${n}") match {
        case null ⇒ throw new IllegalStateException("There is 'null' value in signature argument.")
        case argument ⇒ argument
      }
      val parameters = arguments match {
        case mechanismIdentifier :: xs ⇒
          Signature.perIdentifier.find(_._1.name == mechanismIdentifier) match {
            case Some((identifier, mechanism)) ⇒
              val (algorithmName :: args) = xs
              mechanism(algorithmName, args: _*)
            case None ⇒
              throw new IllegalStateException(s"Unable to find registered signature mechanism for ${mechanismIdentifier}.")
          }
        case Nil ⇒
          Signature.NoSignature
      }
      location -> parameters
    }
    XGraphMarker.Signature(acquireSetting, Some(Map(freezeSettings: _*)))
  }
  /** Store signature settings to java.util.Properties. */
  def signature_=(settings: XGraphMarker.Signature) = graphPropertiesUpdate { p ⇒
    // acquire
    settings.acquire match {
      case Some(validatorId) ⇒ p.setProperty(GraphMarker.fieldSignatureAcquire, validatorId.toString())
      case None ⇒ p.remove(GraphMarker.fieldSignatureAcquire)
    }
    // freeze
    p.stringPropertyNames().asScala.filter(_.startsWith(GraphMarker.fieldSignatureFreeze)).foreach(p.remove)
    settings.freeze.foreach { parameters ⇒
      if (parameters.isEmpty) {
        p.setProperty(GraphMarker.fieldSignatureFreeze, 0.toString)
      } else {
        p.setProperty(GraphMarker.fieldSignatureFreeze, parameters.size.toString)
        var index = 0
        parameters.foreach {
          case (uri, parameters) ⇒
            val arguments = parameters.arguments
            p.setProperty(s"${GraphMarker.fieldSignatureFreeze}_${index}", uri.toString())
            if (parameters == Signature.NoSignature) {
              p.setProperty(s"${GraphMarker.fieldSignatureFreeze}_${index}_0", 0.toString)
            } else {
              p.setProperty(s"${GraphMarker.fieldSignatureFreeze}_${index}_0", (arguments.size + 2).toString())
              p.setProperty(s"${GraphMarker.fieldSignatureFreeze}_${index}_1", parameters.mechanism.identifier.name)
              p.setProperty(s"${GraphMarker.fieldSignatureFreeze}_${index}_2", parameters.algorithm)
              var subindex = 3
              arguments.foreach { argument ⇒
                p.setProperty(s"${GraphMarker.fieldSignatureFreeze}_${index}_${subindex}", argument)
                subindex += 1
              }
            }
            index += 1
        }
      }
    }
  }

  /** Load encryption settings. */
  protected def encryptionLoad(field: String, typeName: String): XGraphMarker.Encryption = graphProperties { p ⇒
    lazy val typeNameTitle = typeName.capitalize
    val URIArgumentsLength = Option(p.getProperty(field)) match {
      case Some(length) if length != "0" ⇒
        length.toInt
      case _ ⇒
        return XGraphMarker.Encryption(Map())
    }
    if (URIArgumentsLength < 0)
      throw new IllegalStateException(s"${typeNameTitle} URI arguments length must be greater than zero.")
    val URIArguments = for (i ← 0 until URIArgumentsLength)
      yield p.getProperty(s"${field}_${i}") match {
      case null ⇒ throw new IllegalStateException(s"There is 'null' value in ${typeName} encryption URI argument.")
      case location ⇒ (i, new URI(location))
    }
    val encryptionSettings = for ((i, location) ← URIArguments) yield {
      val name = Option(p.getProperty(s"${field}_${i}_name")) match {
        case Some(name) ⇒ name
        case None ⇒ throw new IllegalStateException(s"Unable to find name of the ${typeName} encryption algorithm.")
      }
      val key = Option(p.getProperty(s"${field}_${i}_key")).map { encBase64Bytes ⇒
        val secretKey = ID.thisSecretEncryptionKey
        val encBytes = Base64.decode(encBase64Bytes)
        new String(KeyRing.decrypt(secretKey, KeyRing.defaultPassPhrase)(encBytes), io.Codec.UTF8.charSet)
      }
      val parameters = Encryption.perIdentifier.find(_._1.name == name) match {
        case Some((identifier, encryption)) ⇒
          val argumentsLength = Option(p.getProperty(s"${field}_${i}_0")).map(_.toInt).getOrElse(0)
          val arguments = for (n ← (1 to argumentsLength).toList)
            yield p.getProperty(s"${field}_${i}_${n}") match {
            case null ⇒ throw new IllegalStateException(s"There is 'null' value in ${typeName} encryption argument.")
            case argument ⇒ argument
          }
          encryption(key, arguments: _*)
        case None ⇒
          throw new IllegalStateException(s"Unable to find registered encryption for ${name}.")
      }
      location -> parameters
    }
    XGraphMarker.Encryption(Map(encryptionSettings: _*))
  }
  /** Store encryption settings. */
  protected def encryptionStore(settings: XGraphMarker.Encryption, field: String, typeName: String): Unit = graphPropertiesUpdate { p ⇒
    p.stringPropertyNames().asScala.filter(_.startsWith(field)).foreach(p.remove)
    if (settings.encryption.isEmpty) {
      p.setProperty(field, 0.toString)
    } else {
      p.setProperty(field, settings.encryption.size.toString)
      var index = 0
      settings.encryption.foreach {
        case (uri, parameters) ⇒
          p.setProperty(s"${field}_${index}", uri.toString())
          p.setProperty(s"${field}_${index}_name", parameters.encryption.identifier.name)
          parameters.key.foreach { key ⇒
            val publicKey = ID.thisPublicEncryptionKey
            val encBytes = KeyRing.encrypt(publicKey)(key.getBytes(io.Codec.UTF8.charSet))
            val encBase64Bytes = Base64.encode(encBytes)
            p.setProperty(s"${field}_${index}_key", new String(encBase64Bytes, io.Codec.UTF8.charSet))
          }
          // store arguments
          val arguments = parameters.arguments
          p.setProperty(s"${field}_${index}_0", arguments.size.toString)
          var subindex = 1
          arguments.foreach { argument ⇒
            p.setProperty(s"${field}_${index}_${subindex}", argument)
            subindex += 1
          }
          index += 1
      }
    }
  }
}
