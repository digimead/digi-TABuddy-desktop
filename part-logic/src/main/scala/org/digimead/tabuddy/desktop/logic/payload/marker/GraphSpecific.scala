/**
 * This file is part of the TA Buddy project.
 * Copyright (c) 2013-2014 Alexey Aksenov ezh@ezh.msk.ru
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

package org.digimead.tabuddy.desktop.logic.payload.marker

import java.io.{ File, InputStream, OutputStream }
import java.net.URI
import org.digimead.digi.lib.aop.log
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.logic.Logic
import org.digimead.tabuddy.desktop.logic.payload.DSL._
import org.digimead.tabuddy.desktop.logic.payload.Payload
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.element.Element
import org.digimead.tabuddy.model.graph.Graph
import org.digimead.tabuddy.model.serialization.{ SData, Serialization }
import org.digimead.tabuddy.model.serialization.digest.Digest
import org.digimead.tabuddy.model.serialization.signature.Signature
import org.digimead.tabuddy.model.serialization.transport.Transport

/**
 * Part of the graph marker that contains graph specific logic.
 */
trait GraphSpecific {
  this: GraphMarker ⇒

  /** Load the specific graph from the predefined directory ${location}/id/ */
  def graphAcquire(modified: Option[Element.Timestamp] = None, reload: Boolean = false,
    takeItEasy: Boolean = false, sData: SData = new SData()): Unit = state.safeWrite { state ⇒
    assertState()
    log.debug(s"Acquire graph with marker ${this}.")
    if (!Logic.container.isOpen())
      throw new IllegalStateException("Workspace is not available.")
    if (reload || state.graphObject.isEmpty || state.payloadObject.isEmpty) {
      val graph = state.graphObject getOrElse {
        loadGraph(modified = modified, takeItEasy = takeItEasy, sData = sData) getOrElse {
          log.info("Create new empty graph " + graphModelId)
          /**
           * TABuddy - global TA Buddy space
           *  +-Settings - global TA Buddy settings
           *     +-Templates - global TA Buddy element templates
           *  +-Temp - temporary TA Buddy elements
           *     +-Templates - predefined TA Buddy element templates
           */
          // try to create model because we are unable to load it
          Graph[Model](graphModelId, graphOrigin, Model.scope, defaultSerialization, uuid, graphCreated) { g ⇒ }
        }
      }
      graph.withData(_(GraphMarker) = GraphSpecific.this)
      state.graphObject = Option(graph)
      state.payloadObject = Option(initializePayload())
      // Update properties.
      // Update graphAdditionalStorages.
      val storages = graph.storages.toSet
      val additionalStorages = graphAdditionalStorages
      if (storages.size != additionalStorages.size)
        graphAdditionalStorages = graphAdditionalStorages ++ {
          if (Payload.isUnknownStoragesRW)
            storages.map(Left(_)) ++ storages.map(Right(_)) // add new read/write storage to additional storages
          else
            storages.map(Right(_)) // add new read only storage to additional storages
        }
      // Update graphStored value.
      graphStored match {
        case GraphMarker.TimestampNil ⇒
        case timestamp if timestamp < graph.modified ⇒ graphPropertiesUpdate { p ⇒
          p.setProperty(GraphMarker.fieldSavedMillis, graph.modified.milliseconds.toString)
          p.setProperty(GraphMarker.fieldSavedNanos, graph.modified.nanoShift.toString)
        }
        case _ ⇒
      }
      App.publish(App.Message.Open(this, None))
    }
  }
  /** Acquire graph loader. */
  @log
  def graphAcquireLoader(modified: Option[Element.Timestamp] = None, sData: SData = new SData()): Serialization.Loader = {
    // Digest
    val sDataNDigest = digest.acquire match {
      case Some(parameters) if sData.isDefinedAt(Digest.Key.acquire) ⇒
        sData.updated(Digest.Key.acquire, parameters)
      case _ ⇒
        sData
    }
    // Signature
    val sDataNSignature = signature.acquire match {
      case Some(validatorId) if sData.isDefinedAt(Signature.Key.acquire) ⇒
        // TODO replace Signature.acceptAll with loadFromSomeWhere(validatorId)
        sDataNDigest.updated(Signature.Key.acquire, Signature.acceptAll)
      case _ ⇒
        sDataNDigest
    }
    // Container encryption
    val containerEncryptionMap = containerEncryption.encryption
    val sDataNContainerEncryption = if (!sData.isDefinedAt(SData.Key.convertURI))
      sDataNSignature.updated(SData.Key.convertURI,
        // encode
        ((name: String, sData: SData) ⇒ containerEncryptionMap.get(sData(SData.Key.storageURI)) match {
          case Some(parameters) ⇒
            parameters.encryption.toString(parameters.encryption.encrypt(name.getBytes(io.Codec.UTF8.charSet), parameters))
          case None ⇒
            name
        },
          // decode
          (name: String, sData: SData) ⇒ containerEncryptionMap.get(sData(SData.Key.storageURI)) match {
            case Some(parameters) ⇒
              new String(parameters.encryption.decrypt(parameters.encryption.fromString(name), parameters), io.Codec.UTF8.charSet)
            case None ⇒
              name
          }))
    else
      sDataNSignature
    // Content encryption
    val contentEncryptionMap = contentEncryption.encryption
    val sDataNContentEncryption = if (!sData.isDefinedAt(SData.Key.readFilter))
      sDataNContainerEncryption.updated(SData.Key.readFilter, ((is: InputStream, uri: URI, transport: Transport, sData: SData) ⇒
        contentEncryptionMap.get(sData(SData.Key.storageURI)) match {
          case Some(parameters) ⇒ parameters.encryption.decrypt(is, parameters)
          case None ⇒ is
        }))
    else
      sDataNContainerEncryption
    // Acquire
    Serialization.acquireLoader(graphPath.toURI, modified, sDataNContentEncryption)
  }

  /** Get the bunch of additional storages where the left part is a write storage and the right part is a read one. */
  def graphAdditionalStorages: Set[Either[URI, URI]] = graphProperties { p ⇒
    val localPath = graphPath.toURI().toString()
    val size = try p.getProperty(GraphMarker.fieldAdditionalStorages, "0").toInt catch { case _: Throwable ⇒ 0 }
    if (size == 0)
      return Set()

    {
      for (i ← 0 until size)
        yield p.getProperty(s"${GraphMarker.fieldAdditionalStorages}_${i}") match {
        case null ⇒
          throw new IllegalStateException(s"There is 'null' value in additional storages values.")
        case location if location.drop(2).startsWith(localPath) ⇒
          None // filter graphPath
        case location if location.startsWith("R_") ⇒
          Some(Right(new URI(location.drop(2))))
        case location if location.startsWith("W_") ⇒
          Some(Left(new URI(location.drop(2))))
        case unknown ⇒
          throw new IllegalStateException(s"There is unknown '${unknown}' value in additional storages.")
      }
    }.flatten.toSet
  }
  /** Set the bunch of additional storages where the left part is a write storage and the right part is a read one. */
  def graphAdditionalStorages_=(rawStorages: Set[Either[URI, URI]]): Unit = graphPropertiesUpdate { p ⇒
    val storages = rawStorages.map {
      case Left(uri) ⇒ Left(Serialization.inner.addTrailingSlash(uri))
      case Right(uri) ⇒ Right(Serialization.inner.addTrailingSlash(uri))
    }
    if (storages == graphAdditionalStorages)
      return
    p.setProperty(GraphMarker.fieldAdditionalStorages, storages.size.toString())
    var i = 0
    for { storage ← storages } {
      storage match {
        case Left(uri) ⇒ p.setProperty(s"${GraphMarker.fieldAdditionalStorages}_${i}", s"W_${uri.toString}")
        case Right(uri) ⇒ p.setProperty(s"${GraphMarker.fieldAdditionalStorages}_${i}", s"R_${uri.toString}")
      }
      i += 1
    }
  }
  /** Close the loaded graph. */
  def graphClose(): Unit = state.safeWrite { state ⇒
    assertState()
    log.info(s"Close '${state.graph}' with '${this}'.")
    state.graph.removeSubscriptions()
    state.contextRefs.keys.map(GraphMarker.unbind)
    state.contextRefs.clear()
    try markerSave() finally {
      state.graphObject = None
      state.payloadObject = None
    }
    App.publish(App.Message.Close(this, None))
  }
  /** Graph creation timestamp. */
  def graphCreated: Element.Timestamp = graphProperties { p ⇒
    Element.Timestamp(p.getProperty(GraphMarker.fieldCreatedMillis).toLong, p.getProperty(GraphMarker.fieldCreatedNanos).toLong)
  }
  /** Store the graph to the predefined directory ${location}/id/ */
  def graphFreeze(storages: Option[Serialization.Storages] = None): Unit = state.safeWrite { state ⇒
    assertState()
    log.info(s"Freeze '${state.graph}'.")
    if (!Logic.container.isOpen())
      throw new IllegalStateException("Workspace is not available.")
    // Additional storages
    val sDataNStorages = storages match {
      case Some(parameter) ⇒
        SData(SData.Key.explicitStorages ->
          Serialization.Storages(parameter.seq :+ Serialization.Storages.Real(graphPath.toURI)))
      case None ⇒
        val additionalStorages = graphAdditionalStorages
        if (additionalStorages.isEmpty) {
          log.debug("Graph haven't any active additional locations.")
          // freeze local copy that is hidden from anyone
          // with updated list of storages
          SData(SData.Key.explicitStorages -> Serialization.Storages(
            state.graph.storages.map(Serialization.Storages.Public) :+
              Serialization.Storages.Real(graphPath.toURI)))
        } else {
          // list with read only locations and list with write only locations
          val (additionalPublicStorages, additionalRealStorages) = {
            val (read, write) = additionalStorages.partition(_.isRight)
            (read.map(value ⇒ Serialization.Storages.Public(value.right.get)),
              write.map(value ⇒ Serialization.Storages.Real(value.left.get)))
          }
          SData(SData.Key.explicitStorages -> Serialization.Storages(
            state.graph.storages.map(Serialization.Storages.Public) ++
              additionalPublicStorages ++ additionalRealStorages :+
              Serialization.Storages.Real(graphPath.toURI)))
        }
    }
    // Digest
    val sDataNDigest = digest.freeze match {
      case Some(parameters) ⇒
        sDataNStorages.updated(Digest.Key.freeze, parameters)
      case None ⇒
        sDataNStorages
    }
    // Signature
    val sDataNSignature = signature.freeze match {
      case Some(parameters) ⇒
        sDataNDigest.updated(Signature.Key.freeze, parameters)
      case None ⇒
        sDataNDigest
    }
    // Container encryption
    val containerEncryptionMap = containerEncryption.encryption
    val sDataNContainerEncryption = sDataNDigest.updated(SData.Key.convertURI,
      // encode
      ((name: String, sData: SData) ⇒ containerEncryptionMap.get(sData(SData.Key.storageURI)) match {
        case Some(parameters) ⇒
          parameters.encryption.toString(parameters.encryption.encrypt(name.getBytes(io.Codec.UTF8.charSet), parameters))
        case None ⇒
          name
      },
        // decode
        (name: String, sData: SData) ⇒ containerEncryptionMap.get(sData(SData.Key.storageURI)) match {
          case Some(parameters) ⇒
            new String(parameters.encryption.decrypt(parameters.encryption.fromString(name), parameters), io.Codec.UTF8.charSet)
          case None ⇒
            name
        }))
    // Content encryption
    val contentEncryptionMap = contentEncryption.encryption
    val sDataNContentEncryption = sDataNContainerEncryption.updated(SData.Key.writeFilter, ((os: OutputStream, uri: URI, transport: Transport, sData: SData) ⇒
      contentEncryptionMap.get(sData(SData.Key.storageURI)) match {
        case Some(parameters) ⇒ parameters.encryption.encrypt(os, parameters)
        case None ⇒ os
      }))
    // Freeze
    saveTypeSchemas(App.execNGet { state.payload.typeSchemas.values.toSet }, sDataNContentEncryption)
    Serialization.freeze(state.graph, sDataNContentEncryption)
    if (graphPath.listFiles().nonEmpty)
      graphPropertiesUpdate { p ⇒
        // Modify this fields only if there are local files.
        p.setProperty(GraphMarker.fieldSavedMillis, state.graph.modified.milliseconds.toString())
        p.setProperty(GraphMarker.fieldSavedNanos, state.graph.modified.nanoShift.toString())
      }
    App.publish(App.Message.Save(this, None))
  }
  /** Check whether the graph is modified. */
  def graphIsDirty(): Boolean = graphIsOpen && !safeRead { state ⇒
    val ts = state.graph.modified
    state.graph.retrospective.last == Some(ts)
  }
  /** Check whether the graph is loaded. */
  def graphIsOpen(): Boolean = try safeRead { state ⇒
    assertState()
    state.asInstanceOf[GraphMarker.ThreadUnsafeState].graphObject.nonEmpty
  } catch {
    case e: IllegalStateException if e.getMessage.endsWith(" points to disposed data.") ⇒ false
  }
  /** Model ID. */
  def graphModelId: Symbol = {
    assertState()
    Symbol(graphPath.getName)
  }
  /** Origin of the graph. */
  def graphOrigin: Symbol = graphProperties { p ⇒ Symbol(p.getProperty(GraphMarker.fieldOrigin)) }
  /** Path to the graph: base directory and graph directory name. */
  def graphPath: File = graphProperties { p ⇒ new File(p.getProperty(GraphMarker.fieldPath)) }
  /** The latest graph modified timestamp that was saved to storages. */
  def graphStored: Element.Timestamp = graphProperties { p ⇒
    Element.Timestamp(p.getProperty(GraphMarker.fieldSavedMillis).toLong, p.getProperty(GraphMarker.fieldSavedNanos).toLong)
  }

  /** Load the graph. */
  @log
  protected def loadGraph(modified: Option[Element.Timestamp], takeItEasy: Boolean, sData: SData): Option[Graph[_ <: Model.Like]] = try {
    if (!markerIsValid)
      return None

    val loader = graphAcquireLoader(modified, sData)
    Option[Graph[_ <: Model.Like]](loader.load())
  } catch {
    case e: Throwable if takeItEasy ⇒
      log.debug(s"Unable to load graph ${graphOrigin} from $graphPath: " + e.getMessage())
      None
  }
}
