/**
 * This file is part of the TABuddy project.
 * Copyright (c) 2013 Alexey Aksenov ezh@ezh.msk.ru
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

package org.digimead.tabuddy.desktop.logic

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties
import java.util.UUID

import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.Report
import org.digimead.tabuddy.desktop.Report.report2implementation
import org.digimead.tabuddy.desktop.logic.payload.Payload
import org.digimead.tabuddy.desktop.logic.payload.Payload.payload2implementation
import org.digimead.tabuddy.model.Model
import org.eclipse.core.resources.IFile
import org.eclipse.core.resources.IResource

/** Model marker is a class that hold association between real model at client and IResource within container(project). */
case class ModelMarker(
  /** Container IResource unique id. */
  uuid: UUID,
  /** Autoload property file if suitable information needed. */
  autoload: Boolean = true) extends api.ModelMarker with Loggable {
  /** Get container resource */
  lazy val resource: IFile = {
    val resourceName = uuid.toString + "." + Payload.extensionModel
    Logic.container.getFile(resourceName)
  }
  /** Model descriptor properties. */
  @volatile protected var modelDescriptorProperties: Option[Properties] = None
  /** Container IResource properties. */
  @volatile protected var resourceProperties: Option[Properties] = None
  private val saveLoadLock = new Object

  /** Model creation timestamp. */
  def createdAt: Long = {
    require(true)
    modelDescriptorProperties.get.getProperty(ModelMarker.fieldCreatedAt).toLong
  }
  /** Model owner that launch creation process. */
  def creator: Symbol = {
    require(true)
    Symbol(modelDescriptorProperties.get.getProperty(ModelMarker.fieldOrigin))
  }
  /** Model ID. */
  def id: Symbol = Symbol(path.getName)
  /** Last accessed timestamp. */
  def lastAccessed: Long = {
    require(true)
    resourceProperties.get.getProperty(ModelMarker.fieldLastAccessed).toLong
  }
  /** The validation flag indicating whether the marker is consistent. */
  def isValid: Boolean = try {
    val base = path.getParentFile()
    val id = path.getName
    val descriptor = new File(base, id + "." + Payload.extensionModel)
    path.exists() && descriptor.exists && resource.exists() && {
      if (autoload && modelDescriptorProperties.isEmpty)
        load()
      true
    }
  } catch {
    case e: Throwable =>
      false
  }
  /** Load marker properties. */
  def load(): Unit = saveLoadLock.synchronized {
    log.debug("Load marker " + uuid)

    // load IResource part
    log.debug(s"Load resource: ${resource.getName}.")
    if (!resource.exists())
      throw new IllegalArgumentException(s"Model marker with id ${uuid} not found.")
    val stream = resource.getContents(true)
    val resourceProperties = new Properties
    resourceProperties.load(stream)
    try { stream.close() } catch { case e: Throwable => }
    this.resourceProperties = Option(resourceProperties)

    // load model descriptor part
    val fullPath = new File(resourceProperties.getProperty(ModelMarker.fieldPath))
    val path = fullPath.getParentFile()
    val id = fullPath.getName()
    val descriptor = new File(path, id + "." + Payload.extensionModel)
    log.debug(s"Load model descriptor: ${descriptor.getName}.")
    val properties = new Properties
    properties.load(new FileInputStream(descriptor))
    this.modelDescriptorProperties = Option(properties)
  }
  /** Path to model: base directory and model directory name. */
  def path: File = {
    require(true)
    new File(resourceProperties.get.getProperty(ModelMarker.fieldPath))
  }
  /** Save model descriptor. */
  def save(): Unit = saveLoadLock.synchronized {
    log.debug("Save marker for model " + id)
    require(true, false)

    // update fields
    resourceProperties.get.setProperty(ModelMarker.fieldLastAccessed, System.currentTimeMillis().toString)

    // save model descriptor part
    path.mkdirs()
    log.debug(s"Save model descriptor: ${descriptor.getName}.")
    Report.info match {
      case Some(info) => modelDescriptorProperties.get.store(new FileOutputStream(descriptor), info)
      case None => modelDescriptorProperties.get.store(new FileOutputStream(descriptor), null)
    }

    // save IResource part
    log.debug(s"Save resource: ${resource.getName()}.")
    val output = new ByteArrayOutputStream()
    resourceProperties.get.store(output, null)
    val input = new ByteArrayInputStream(output.toByteArray())
    if (!resource.exists())
      resource.create(input, IResource.NONE, null)
    else
      resource.setContents(input, true, false, null)
  }

  /** Model descriptor location. */
  protected def descriptor = new File(path.getParentFile(), path.getName() + "." + Payload.extensionModel)
  /** Load model descriptor if needed. */
  protected def require(throwError: Boolean, updateViaSave: Boolean = true): Unit =
    if (autoload && (modelDescriptorProperties.isEmpty || resourceProperties.isEmpty)) {
      load()
      // and update last access time via
      if (updateViaSave) save()
    } else {
      if (throwError && modelDescriptorProperties.isEmpty)
        throw new IllegalStateException(s"Model descriptor ${descriptor} not loaded.")
    }
}

object ModelMarker extends Loggable {
  val fieldCreatedAt = "createdAt"
  val fieldLastAccessed = "lastAccessed"
  val fieldOrigin = "origin"
  val fieldPath = "path"
  val fieldResourceId = "resourceId"

  /**
   * Create new model marker.
   *
   * @param uuid container IResource unique id.
   * @param fullPath path to model with model directory name.
   * @param createdAt model creation timestamp.
   * @param creator model owner that launch creation process.
   * @return model marker
   */
  def apply(resourceUUID: UUID, fullPath: File, createdAt: Long, creator: Symbol): ModelMarker = {
    val path = fullPath.getParentFile()
    val id = fullPath.getName
    val timestamp = System.currentTimeMillis()
    log.debug(s"Prepare model ${Symbol(id)}")
    if (!fullPath.exists())
      if (!fullPath.mkdirs())
        throw new RuntimeException("Unable to create model storage at " + fullPath.getAbsolutePath())
    val modelDescriptorFile = new File(path, id + "." + Payload.extensionModel)
    if (!modelDescriptorFile.exists())
      if (!modelDescriptorFile.createNewFile())
        throw new RuntimeException("Unable to create model descriptor at " + modelDescriptorFile.getAbsolutePath())
    val modelDescriptor = new Properties()
    modelDescriptor.setProperty(fieldCreatedAt, timestamp.toString)
    modelDescriptor.setProperty(fieldResourceId, resourceUUID.toString)
    modelDescriptor.setProperty(fieldOrigin, Model.origin.name)
    log.debug(s"Create model descriptor: ${modelDescriptorFile.getName}.")
    Report.info match {
      case Some(info) => modelDescriptor.store(new FileOutputStream(modelDescriptorFile), info)
      case None => modelDescriptor.store(new FileOutputStream(modelDescriptorFile), null)
    }
    val resourceName = resourceUUID.toString + "." + Payload.extensionModel
    val resourceFile = Logic.container.getFile(resourceName)
    if (!resourceFile.exists()) {
      val resourceContent = new Properties
      resourceContent.setProperty(fieldPath, fullPath.getCanonicalPath())
      resourceContent.setProperty(ModelMarker.fieldLastAccessed, timestamp.toString)
      val output = new ByteArrayOutputStream()
      resourceContent.store(output, null)
      val input = new ByteArrayInputStream(output.toByteArray())
      log.debug(s"Create model marker: ${resourceName}.")
      resourceFile.create(input, IResource.NONE, null)
    } else
      throw new IllegalStateException("Model marker ${marker} is already exists.")
    ModelMarker(resourceUUID, true)
  }
  /** Marker for default model. */
  class DefaultModelMarker extends ModelMarker(Payload.defaultModel.eUnique) {
    /** Model creation timestamp. */
    override def createdAt: Long = 0
    /** Model owner that launch creation process. */
    override def creator: Symbol = Model.origin
    /** Model ID. */
    override def id: Symbol = Payload.defaultModel.eId
    /** The validation flag indicating whether the marker is consistent. */
    override def isValid: Boolean = true
    /** Last accessed timestamp. */
    override def lastAccessed: Long = 0
    /** Load marker properties. */
    override def load(): Unit = {}
    /** Save model descriptor. */
    override def save(): Unit = {}
  }
}
