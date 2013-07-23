/**
 * This file is part of the TABuddy project.
 * Copyright (c) 2012-2013 Alexey Aksenov ezh@ezh.msk.ru
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
 * that is created or manipulated using TABuddy.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license. Buying such a license is mandatory as soon as you
 * develop commercial activities involving the TABuddy software without
 * disclosing the source code of your own applications.
 * These activities include: offering paid services to customers,
 * serving files in a web or/and network application,
 * shipping TABuddy with a closed source product.
 *
 * For more information, please contact Digimead Team at this
 * address: ezh@ezh.msk.ru
 */

package org.digimead.tabuddy.desktop.logic.payload

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileFilter
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.URI
import java.util.Properties
import java.util.UUID

import scala.collection.immutable
import scala.collection.mutable

import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.digi.lib.util.FileUtil
import org.digimead.tabuddy.desktop.Report
import org.digimead.tabuddy.desktop.logic.Data
import org.digimead.tabuddy.desktop.logic.Logic
import org.digimead.tabuddy.desktop.logic.ModelMarker
import org.digimead.tabuddy.desktop.logic.{ api => lapi }
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.Model.Stash
import org.digimead.tabuddy.model.Model.model2implementation
import org.digimead.tabuddy.model.Record
import org.digimead.tabuddy.model.element.Element
import org.digimead.tabuddy.model.element.Reference
import org.digimead.tabuddy.model.serialization.Serialization
import org.eclipse.core.resources.IResource
import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext

import com.escalatesoft.subcut.inject.BindingModule
import com.escalatesoft.subcut.inject.Injectable
import com.google.common.base.Charsets
import com.google.common.io.Files

import language.implicitConversions

/**
 * Singleton that contains information about loaded models and provides high level model serialization API.
 */
class Payload(implicit val bindingModule: BindingModule) extends api.Payload with Injectable with Loggable {
  val elementNameTemplate = "e %s {%08X}" // hash that prevents case insensitivity collision
  /** File extension for the serialized element. */
  val extensionElement = Payload.DI.extensionElement
  /** File extension for the model descriptors. */
  val extensionModel = "model"
  /** Type schemas folder name. */
  val folderTypeSchemas = "typeSchemas"
  /** The element's record name template */
  /** Loaded models marker registry populated by 'acquireModel'. */
  protected lazy val markerRegistry = {
    val map = new mutable.WeakHashMap[Model.Interface[_ <: Model.Stash], lapi.ModelMarker]() with mutable.SynchronizedMap[Model.Interface[_ <: Model.Stash], lapi.ModelMarker]
    map(Payload.defaultModel) = new ModelMarker.DefaultModelMarker
    map
  }

  /**
   * Load the specific model from the predefined directory ${location}/id/
   */
  @log
  def acquireModel(marker: lapi.ModelMarker): Option[Model.Interface[_ <: Model.Stash]] = {
    log.debug(s"Acquire model with marker ${marker}.")
    try {
      loadModel(marker) orElse {
        log.info("Create new empty model " + marker.id)
        // try to create model if we are unable to load it
        val stash = new Model.Stash(marker.id, marker.uuid)
        /**
         * TABuddy - global TA Buddy space
         *  +-Settings - global TA Buddy settings
         *     +-Templates - global TA Buddy element templates
         *  +-Temp - temporary TA Buddy elements
         *     +-Templates - predefined TA Buddy element templates
         */
        val model = new org.digimead.tabuddy.desktop.logic.payload.PayloadModel(stash)
        markerRegistry(model) = marker
        Model.reset(model)
        Some(model)
      }
    } catch {
      // catch all throwables from serialization process
      case e: Throwable =>
        log.error("Unable to load model that marked with id %s: %s".format(marker, e), e)
        Model.reset()
        None
    }
  }
  /**
   * Close the current active model.
   */
  @log
  def close(marker: lapi.ModelMarker) {
    log.info(s"Close model '${Model}' with marker '${marker}'.")
    try {
      marker.save()
    } finally {
      Model.reset(Payload.defaultModel)
    }
  }
  /**
   * Create the specific model at the specific directory.
   */
  @log
  def createModel(fullPath: File): lapi.ModelMarker = {
    log.info(s"Create model at '${fullPath.getCanonicalPath()}'.")
    val resourceUUID = UUID.randomUUID()
    val createdAt = System.currentTimeMillis()
    val creator = Model.origin
    ModelMarker(resourceUUID, fullPath, createdAt, creator)
  }
  /**
   * Store the specific model to the predefined directory ${location}/id/
   */
  @log
  def freezeModel(marker: lapi.ModelMarker, model: Model.Interface[_ <: Model.Stash]) {
    log.debug(s"Freeze model ${model}.")
    if (marker.id != model.eId)
      throw new IllegalArgumentException(s"Model marker id ${marker.id} and model id ${model.eId} are different.")
    try {
      val storage = saveModel(marker, model)
      val typeSchemas = App.execNGet { Data.typeSchemas.values.toSet }
      saveTypeSchemas(marker, typeSchemas)
    } catch {
      // catch all throwables from serialization process
      case e: Throwable =>
        log.error("Unable to save model %s: %s".format(model, e))
    }
  }
  /** Generate new name/id/... */
  def generateNew(base: String, suffix: String, exists: (String) => Boolean): String = {
    val iterator = new Iterator[String] {
      @volatile private var n = 0
      def hasNext = true
      def next = {
        val result = if (n == 0) base else base + suffix + n
        n += 1
        result
      }
    }
    var newValue = iterator.next
    while (exists(newValue))
      newValue = iterator.next
    newValue
  }
  /** Returns the element storage */
  def getElementStorage(marker: lapi.ModelMarker, reference: Reference): Option[URI] = {
    Model.e(reference) match {
      case Some(element) if element.eStash.model == Some(Model.inner) =>
        val ancestors = element.eAncestors
        if (ancestors.isEmpty) {
          log.warn(s"unable to get storage for element $reference. There are no ancestors")
          return None
        }
        val base = marker.path
        val ancestorDirectory = new File(base, element.eAncestors.map(e =>
          elementNameTemplate.format(e.eId.name, e.eId.name.hashCode())).reverse.mkString(File.separator))
        val elementDirectory = new File(ancestorDirectory,
          elementNameTemplate.format(element.eId.name, element.eId.name.hashCode()))
        if (!elementDirectory.exists())
          if (!elementDirectory.mkdirs()) {
            log.warn(s"unable to get storage for element $reference. Unable to create directory " + elementDirectory.getAbsolutePath())
            return None
          }
        Some(elementDirectory.toURI())
      case _ =>
        log.warn(s"unable to get storage for element $reference. Element not found or not attached")
        None
    }
  }
  /** Get marker for loaded model. */
  def getModelMarker(model: Model.Interface[_ <: Model.Stash]): Option[lapi.ModelMarker] =
    markerRegistry.get(model)
  /** Get a model list. */
  def listModels(): Seq[lapi.ModelMarker] = {
    modelMarker(Payload.defaultModel) +: Logic.container.members.flatMap { resource =>
      if (resource.getFileExtension() == extensionModel)
        try {
          val uuid = UUID.fromString(resource.getName().takeWhile(_ != '.'))
          Some(ModelMarker(uuid))
        } catch {
          case e: Throwable =>
            log.warn("Skip model marker with invalid name: " + resource.getName)
            None
        }
      else
        None
    }
  }
  /** Load type schemas. */
  @log
  def loadTypeSchemas(marker: lapi.ModelMarker): immutable.HashSet[api.TypeSchema] = {
    val typeSchemasStorage = new File(marker.path, folderTypeSchemas)
    log.debug(s"load type schemas from $typeSchemasStorage")
    if (!typeSchemasStorage.exists())
      return immutable.HashSet()
    val prefix = marker.path.getCanonicalPath().size + 1
    var schemas = immutable.HashSet[api.TypeSchema]()
    typeSchemasStorage.listFiles(new FileFilter { def accept(file: File) = file.isFile() && file.getName().endsWith(".yaml") }).foreach { file =>
      try {
        log.debug("load ... " + file.getCanonicalPath().substring(prefix))
        val yaml = Files.toString(file, Charsets.UTF_8)
        try {
          TypeSchema.YAML.from(yaml).foreach(schema => schemas = schemas + schema)
        } catch {
          case e: Throwable =>
            log.error("unable to load type schema %s: %s".format(file.getName(), e), e)
        }
      } catch {
        case e: IOException =>
          log.error("unable to load type schema %s: %s".format(file.getName(), e))
      }
    }
    schemas
  }
  /** Get marker for loaded model or throw error. */
  def modelMarker(model: Model.Interface[_ <: Model.Stash]): lapi.ModelMarker =
    getModelMarker(model) getOrElse { throw new IllegalArgumentException("Marker is unavailable for model " + model) }
  /** Save type schemas. */
  @log
  def saveTypeSchemas(marker: lapi.ModelMarker, schemas: immutable.Set[api.TypeSchema]) {
    val typeSchemasStorage = new File(marker.path, folderTypeSchemas)
    log.debug(s"load type schemas to $typeSchemasStorage")
    if (!typeSchemasStorage.exists())
      if (!typeSchemasStorage.mkdirs())
        throw new RuntimeException("Unable to create type schemas storage at " + typeSchemasStorage.getAbsolutePath())
    typeSchemasStorage.listFiles(new FileFilter { def accept(file: File) = file.isFile() && file.getName().endsWith(".yaml") }).foreach(_.delete())
    schemas.foreach(schema =>
      Files.write(TypeSchema.YAML.to(schema), new File(typeSchemasStorage, schema.id.toString() + ".yaml"), Charsets.UTF_8))
  }
  /** Get a model settings container */
  // Element[_ <: Stash] == Element.Generic, avoid 'erroneous or inaccessible type' error
  def settings(): Record.Generic = inject[Record.Interface[_ <: Record.Stash]]("eSettings")

  protected def loadFilter(element: Element.Generic): Option[Element.Generic] = {
    element match {
      case model: Model.Generic =>
      case element =>
    }
    Some(element)
  }
  /** Load the model. */
  @log
  protected def loadModel(marker: lapi.ModelMarker): Option[Model.Interface[_ <: Model.Stash]] = {
    if (!marker.isValid)
      return None
    val treeFilter = new FileFilter() { def accept(file: File): Boolean = file.isDirectory() || file.getName.endsWith("." + extensionElement) }
    def treeFiles(root: File, skipHidden: Boolean, filter: FileFilter): Stream[File] =
      if (!root.exists || (skipHidden && root.isHidden)) Stream.empty
      else root #:: (
        root.listFiles(filter) match {
          case null => Stream.empty
          case files => files.toStream.flatMap(treeFiles(_, skipHidden, filter))
        })
    val elements = treeFiles(marker.path, false, treeFilter).iterator
    val loadF = () => loadModelElements(marker, elements)
    log.info(s"Load model ${marker.id} from ${marker.path.getAbsolutePath()}.")
    val loaded = Payload.serialization.acquire[Model.Interface[Model.Stash], Model.Stash](loadF)
    loaded.foreach { model =>
      markerRegistry(model) = marker
      Model.reset(model)
    }
    loaded
  }
  /** Load model elements */
  protected def loadModelElements(marker: lapi.ModelMarker, iterator: Iterator[File]): Option[Array[Byte]] = {
    val prefix = marker.path.getCanonicalPath().size + 1
    while (iterator.hasNext) {
      val next = iterator.next
      if (next.isFile) {
        log.debug("load ... " + next.getCanonicalPath().substring(prefix))
        val elementStream = new BufferedInputStream(new FileInputStream(next))
        val elementData = new ByteArrayOutputStream()
        val result = try {
          FileUtil.writeToStream(elementStream, elementData)
          Some(elementData.toByteArray())
        } catch {
          case e: Throwable =>
            log.error("unable to acquire element %s: %s".format(next.getName, e))
            None
        } finally {
          elementStream.close()
          elementData.flush()
          elementData.close()
        }
        if (result.nonEmpty)
          return result
      }
    }
    None
  }
  /** Save the model. */
  @log
  protected def saveModel(marker: lapi.ModelMarker, model: Model.Interface[_ <: Model.Stash]) {
    if (marker.id != model.eId)
      throw new IllegalArgumentException(s"Model marker id ${marker.id} and model id ${model.eId} are different.")
    val saveF = saveModelElements(marker.path, _: Element.Generic, _: Array[Byte])
    Payload.serialization.freeze(Model, saveF)
  }
  /** Save model elements */
  protected def saveModelElements(base: File, element: Element.Generic, data: Array[Byte]) {
    val elementDirectory = new File(base, element.eAncestors.map(e =>
      elementNameTemplate.format(e.eId.name, e.eId.name.hashCode())).reverse.mkString(File.separator)).getAbsoluteFile()
    log.debug("save element " + element)
    if (elementDirectory.isDirectory() || elementDirectory.mkdirs()) {
      val elementFile = new File(elementDirectory, elementNameTemplate.format(element.eId.name, element.eId.name.hashCode()) + "." + extensionElement)
      val in = new ByteArrayInputStream(data)
      val out = new BufferedOutputStream(new FileOutputStream(elementFile))
      try {
        FileUtil.writeToStream(in, out)
      } finally {
        out.close()
        in.close()
      }
    } else {
      log.error("Unable to create directory " + elementDirectory)
    }
  }
  protected def saveFilter(element: Element.Generic): Option[Element.Generic] = {
    element match {
      case model: Model.Generic =>
      case element =>
    }
    Some(element)
  }
}

/**
 * Payload contains
 * - Model, binded to current device with unique ID
 * - Records, binded to Model
 */
object Payload extends Loggable {
  implicit def payload2implementation(p: Payload.type): api.Payload = p.inner
  @volatile private var active: Boolean = false
  PayloadModel // initialize

  def defaultModel() = DI.defaultModel
  def inner() = DI.implementation
  def serialization() = DI.serialization

  /**
   * Dependency injection routines
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** Default model identifier. */
    lazy val defaultModel = inject[Model.Interface[Model.Stash]]
    /** File extension for the serialized element. */
    lazy val extensionElement = inject[String]("Payload.Element.Extension")
    /** Payload implementation. */
    lazy val implementation = inject[api.Payload]
    /** Serialization. */
    lazy val serialization = inject[Serialization[Array[Byte]]]("Payload.Serialization")
  }
}
