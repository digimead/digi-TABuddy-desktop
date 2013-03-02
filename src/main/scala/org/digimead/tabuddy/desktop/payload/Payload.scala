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

package org.digimead.tabuddy.desktop.payload

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
import java.util.UUID

import scala.Array.fallbackCanBuildFrom
import scala.collection.immutable
import scala.collection.immutable.Stream.consWrapper

import org.digimead.digi.lib.DependencyInjection
import org.digimead.digi.lib.log.Loggable
import org.digimead.digi.lib.log.logger.RichLogger.rich2slf4j
import org.digimead.digi.lib.util.FileUtil
import org.digimead.tabuddy.desktop.Data
import org.digimead.tabuddy.desktop.Main
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.Model.Stash
import org.digimead.tabuddy.model.Model.model2implementation
import org.digimead.tabuddy.model.Record
import org.digimead.tabuddy.model.element.Element
import org.digimead.tabuddy.model.serialization.Serialization

import com.escalatesoft.subcut.inject.BindingModule
import com.escalatesoft.subcut.inject.Injectable
import com.google.common.base.Charsets
import com.google.common.io.Files

import language.implicitConversions

class Payload(implicit val bindingModule: BindingModule) extends Payload.Interface with Injectable {
  /** Location of the serialized data/payload */
  lazy val location = inject[File]("Payload")

  /**
   * Load the specific model from the predefined directory ${location}/id/
   */
  def acquireModel(modelId: Symbol): Option[Model.Interface[_ <: Model.Stash]] = {
    log.debug(s"acquire model modelId")
    try {
      loadModel(modelId) orElse {
        // try to create model if we are unable to load it
        val stash = new Model.Stash(modelId, UUID.randomUUID())
        /**
         * TABuddy - global TA Buddy space
         *  +-Settings - global TA Buddy settings
         *     +-Templates - global TA Buddy element templates
         *  +-Temp - temporary TA Buddy elements
         *     +-Templates - predefined TA Buddy element templates
         */
        val model = new org.digimead.tabuddy.desktop.payload.PayloadModel(stash)
        Model.reset(model)
        Some(model)
      }
    } catch {
      // catch all throwables from serialization process
      case e: Throwable =>
        log.error("Unable to load model %s: %s".format(modelId, e))
        Model.reset()
        None
    }
  }
  /**
   * Store the specific model to the predefined directory ${location}/id/
   */
  def freezeModel(model: Model.Interface[_ <: Model.Stash]): Option[URI] = {
    log.debug(s"freeze model $model")
    try {
      val storage = saveModel(model)
      val typeSchemas = Main.execNGet { Data.typeSchemas.toSet }
      saveTypeSchemas(model.eId, typeSchemas)
      Some(storage.toURI)
    } catch {
      // catch all throwables from serialization process
      case e: Throwable =>
        log.error("Unable to save model %s: %s".format(model, e))
        None
    }
  }
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
  /**
   * Get a model list
   */
  def listModels(): Seq[Symbol] = location.listFiles().filter { file =>
    val marker = new File(file, modelMarker)
    marker.exists()
  } map { file =>
    val name = file.getName
    Symbol(name.substring(0, name.size - extensionModel.size - 1))
  }
  /** Load type schemas */
  def loadTypeSchemas(modelId: Symbol): immutable.HashSet[TypeSchema.Interface] = {
    val typeSchemasStorage = new File(getModelStorage(modelId), folderTypeSchemas)
    log.debug(s"load type schemas from $typeSchemasStorage")
    if (!typeSchemasStorage.exists())
      return immutable.HashSet()
    val prefix = location.getAbsolutePath().size + 1
    var schemas = immutable.HashSet[TypeSchema.Interface]()
    typeSchemasStorage.listFiles(new FileFilter { def accept(file: File) = file.isFile() && file.getName().endsWith(".yaml") }).foreach { file =>
      try {
        log.debug("load ... " + file.getAbsolutePath().substring(prefix))
        val yaml = Files.toString(file, Charsets.UTF_8)
        try {
          schemas = schemas + TypeSchema.YAML.from(yaml)
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
  /** Save type schemas */
  def saveTypeSchemas(modelId: Symbol, schemas: immutable.Set[TypeSchema.Interface]) {
    val typeSchemasStorage = new File(getModelStorage(modelId), folderTypeSchemas)
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

  /**
   * Acquire model storage
   */
  protected def acquireModelStorage(modelId: Symbol): File = {
    log.debug(s"prepare model $modelId")
    val modelStorage = getModelStorage(modelId)
    if (!modelStorage.exists())
      if (!modelStorage.mkdirs())
        throw new RuntimeException("Unable to create model storage at " + modelStorage.getAbsolutePath())
    val modelStorageMarker = new File(modelStorage, modelMarker)
    if (!modelStorageMarker.exists())
      if (!modelStorageMarker.createNewFile())
        throw new RuntimeException("Unable to create model storage marker at " + modelStorageMarker.getAbsolutePath())
    modelStorage
  }
  /** Check if the model is exists */
  protected def checkIfModelExists(modelId: Symbol): Boolean = {
    val modelStorageMarker = new File(getModelStorage(modelId), modelMarker)
    modelStorageMarker.exists
  }
  protected def loadFilter(element: Element.Generic): Option[Element.Generic] = {
    element match {
      case model: Model.Generic =>
      case element =>
    }
    Some(element)
  }
  /** Get the model storage location */
  protected def getModelStorage(modelId: Symbol): File =
    new File(location, modelId.name + "." + extensionModel)
  /** Load the model */
  protected def loadModel(modelId: Symbol): Option[Model.Interface[_ <: Model.Stash]] = {
    val modelStorage = getModelStorage(modelId)
    val modelStorageMarker = new File(modelStorage, modelMarker)
    if (!modelStorageMarker.exists())
      return None
    val treeFilter = new FileFilter() { def accept(file: File): Boolean = file.isDirectory() || file.getName.endsWith("." + extensionElement) }
    def treeFiles(root: File, skipHidden: Boolean, filter: FileFilter): Stream[File] =
      if (!root.exists || (skipHidden && root.isHidden)) Stream.empty
      else root #:: (
        root.listFiles(filter) match {
          case null => Stream.empty
          case files => files.toStream.flatMap(treeFiles(_, skipHidden, filter))
        })
    val elements = treeFiles(modelStorage, false, treeFilter).iterator
    val loadF = () => loadModelElements(elements)
    log.info("load model %s from %s".format(modelId, modelStorage.getAbsolutePath()))
    val loaded = Payload.serialization.acquire[Model.Interface[Model.Stash], Model.Stash](loadF)
    loaded.foreach(Model.reset)
    loaded
  }
  /** Load model elements */
  protected def loadModelElements(iterator: Iterator[File]): Option[Array[Byte]] = {
    val prefix = location.getAbsolutePath().size + 1
    while (iterator.hasNext) {
      val next = iterator.next
      if (next.isFile) {
        log.debug("load ... " + next.getAbsolutePath().substring(prefix))
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
  /** Save the model */
  protected def saveModel(model: Model.Interface[_ <: Model.Stash]): File = {
    val modelStorage = acquireModelStorage(model.eId)
    val saveF = saveModelElements(modelStorage, _: Element.Generic, _: Array[Byte])
    Payload.serialization.freeze(Model, saveF)
    modelStorage
  }
  /** Save model elements */
  protected def saveModelElements(base: File, element: Element.Generic, data: Array[Byte]) {
    val elementDirectory = new File(base, element.eAncestors.map("-" + _.eId.name).reverse.mkString(File.separator)).getAbsoluteFile()
    log.debug("save element " + element)
    if (elementDirectory.isDirectory() || elementDirectory.mkdirs()) {
      val elementFile = new File(elementDirectory, element.eId.name + "." + extensionElement)
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
  //
  // Main.Interface stuff
  //
  /**
   * This function is invoked at application start
   */
  def start() {
    log.info("initialize payload data from " + location)
    active = true
    try {
      if (!location.exists) {
        log.info("create payload storage")
        location.mkdirs
      }
    } catch {
      // catch all possible throwables
      case e: Throwable =>
        log.warn("unable to initialize payload: " + e.getMessage())
    }
  }
  /**
   * This function is invoked at application stop
   */
  def stop() {
    log.info("payload data is prepared for shutdown")
  }
}

/**
 * Payload contains
 * - Model, binded to current device with unique ID
 * - Records, binded to Model
 */
object Payload extends DependencyInjection.PersistentInjectable with Loggable {
  implicit def payload2implementation(p: Payload.type): Interface = p.implementation
  implicit def bindingModule = DependencyInjection()
  @volatile private var implementation = inject[Interface]
  @volatile private var defaultModelIdentifier = inject[Symbol]("Payload.DefaultModel")
  @volatile private var serialization = inject[Serialization[Array[Byte]]]("Payload.Serialization")
  PayloadModel // initialize

  def defaultModel() = defaultModelIdentifier
  def inner() = implementation

  def commitInjection() {}
  def updateInjection() {
    if (implementation.active) {
      implementation.stop()
      implementation = inject[Interface]
      implementation.start()
    } else
      implementation = inject[Interface]
    defaultModelIdentifier = inject[Symbol]("Payload.DefaultModel")
    serialization = inject[Serialization[Array[Byte]]]("Payload.Serialization")
  }

  trait Interface extends Main.Interface {
    /** empty file/marker that mark directory as buddy model */
    val modelMarker = ".Model"
    /** Location of the serialized data/payload */
    val location: File
    val extensionElement = inject[String]("Payload.Element.Extension")
    val extensionModel = "model"
    val folderTypeSchemas = "typeSchemas"

    /** Load the specific model from the predefined directory ${location}/id/ */
    def acquireModel(id: Symbol): Option[Model.Interface[_ <: Model.Stash]]
    /** Store the specific model to the predefined directory ${location}/id/ */
    def freezeModel(model: Model.Interface[_ <: Model.Stash]): Option[URI]
    /** Generate new name/id/... */
    def generateNew(base: String, suffix: String, exists: (String) => Boolean): String
    /** Get a model list */
    def listModels(): Seq[Symbol]
    /** Load type schemas */
    def loadTypeSchemas(modelId: Symbol): immutable.HashSet[TypeSchema.Interface]
    /** Save type schemas */
    def saveTypeSchemas(modelId: Symbol, schemas: immutable.Set[TypeSchema.Interface])
    /** Get a model settings container */
    def settings(): Element.Generic
  }
  trait YAMLProcessor[T] {
    /** Convert object to YAML */
    def to(obj: T): String
    /** Convert YAML to object */
    def from(yaml: String): T
  }
}
