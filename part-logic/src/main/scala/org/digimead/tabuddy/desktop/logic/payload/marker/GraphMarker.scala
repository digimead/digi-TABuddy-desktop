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

import com.google.common.base.Charsets
import com.google.common.io.Files
import java.io.{ ByteArrayInputStream, ByteArrayOutputStream, File, FileFilter, IOException }
import java.net.URI
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.{ Properties, UUID }
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.digi.lib.util.FileUtil
import org.digimead.tabuddy.desktop.core.definition.Context
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.{ Core, Report }
import org.digimead.tabuddy.desktop.logic.Logic
import org.digimead.tabuddy.desktop.logic.operation.graph.OperationGraphClose
import org.digimead.tabuddy.desktop.logic.payload.DSL._
import org.digimead.tabuddy.desktop.logic.payload.api.XTypeSchema
import org.digimead.tabuddy.desktop.logic.payload.marker.api.XGraphMarker
import org.digimead.tabuddy.desktop.logic.payload.marker.serialization.SerializationSpecific
import org.digimead.tabuddy.desktop.logic.payload.view.{ Filter, Sorting, View }
import org.digimead.tabuddy.desktop.logic.payload.{ Enumeration, Payload, PredefinedElements, TypeSchema }
import org.digimead.tabuddy.model.element.Element
import org.digimead.tabuddy.model.graph.Graph
import org.digimead.tabuddy.model.serialization.SData
import org.digimead.tabuddy.model.serialization.Serialization
import org.digimead.tabuddy.model.{ Model, Record }
import org.eclipse.core.internal.utils.Policy
import org.eclipse.core.resources.{ IFile, IResource }
import org.eclipse.swt.widgets.{ Composite, Shell }
import scala.collection.{ immutable, mutable }

/**
 * Graph marker is an object that holds an association between real graph at client
 *   and Eclipse IResource within container(project).
 * Marker define application behavior.
 * Graph of the marker contains:
 *   application element templates
 *   application enumerations
 *   application type schemas
 *   application view definitions
 *   application view filters
 *     ... and so on + user data
 */
class GraphMarker(
  /** Container IResource unique id. */
  val uuid: UUID,
  /** Autoload property file if suitable information needed. */
  val autoload: Boolean = true) extends XGraphMarker with MarkerSpecific with GraphSpecific with SerializationSpecific with Loggable {
  /** Type schemas folder name. */
  val folderTypeSchemas = "typeSchemas"
  /** Resources index file name. */
  val index = "index"
  /** Get container resource */
  lazy val resource: IFile = {
    val resourceName = uuid.toString + "." + Payload.extensionGraph
    Logic.container.getFile(resourceName)
  }
  /** GraphMarker mutable state. */
  val state: GraphMarker.ThreadSafeState = initializeState()

  /** Assert marker state. */
  def assertState() = safeRead { state ⇒
    if (state.asInstanceOf[GraphMarker.ThreadUnsafeState].payloadObject == null)
      throw new IllegalStateException(s"${this} points to disposed data.")
  }

  /** Load type schemas from local storage. */
  @log
  def loadTypeSchemas(storage: Option[URI] = None): immutable.HashSet[XTypeSchema] = safeRead { state ⇒
    assertState()
    val containerEncryptionMap = containerEncryption.encryption
    val contentEncryptionMap = contentEncryption.encryption
    val storageURI = storage getOrElse graphPath.toURI()
    var schemas = immutable.HashSet[XTypeSchema]()
    Serialization.perScheme.get(storageURI.getScheme()) match {
      case Some(transport) ⇒
        val sData = SData(SData.Key.storageURI -> storageURI)
        val encode = containerEncryptionMap.get(storageURI) match {
          case Some(parameters) ⇒
            ((name: String) ⇒ parameters.encryption.toString(parameters.encryption.encrypt(name.getBytes(io.Codec.UTF8.charSet), parameters)))
          case None ⇒
            ((name: String) ⇒ name)
        }
        val decrypt = contentEncryptionMap.get(storageURI) match {
          case Some(parameters) ⇒
            ((content: Array[Byte]) ⇒ parameters.encryption.decrypt(content, parameters))
          case None ⇒
            ((content: Array[Byte]) ⇒ content)
        }
        val typeSchemasStorageBase = transport.append(storageURI, encode(folderTypeSchemas))
        log.debug(s"Load type schemas from $typeSchemasStorageBase")
        // Acquire.
        val schemaIndexURI = transport.append(typeSchemasStorageBase, encode(index))
        val ids = try new String(decrypt(transport.read(schemaIndexURI, sData)), io.Codec.UTF8.charSet).split("\n").map(UUID.fromString)
        catch {
          case e: IOException ⇒
            log.debug(s"Unable to load type schema index ${schemaIndexURI}: " + e.getMessage()); Array.empty[UUID]
          case e: Throwable ⇒
            log.error(s"Unable to load type schema index ${schemaIndexURI}: " + e.getMessage(), e); Array.empty[UUID]
        }
        for (schemaId ← ids) try {
          val schemaURI = transport.append(typeSchemasStorageBase, encode(schemaId.toString() + ".yaml"))
          val yaml = new String(decrypt(transport.read(schemaURI, sData)), io.Codec.UTF8.charSet)
          TypeSchema.YAML.from(yaml).foreach(schema ⇒ schemas = schemas + schema)
        } catch {
          case e: IOException ⇒
            log.error(s"Unable to load type schema ${schemaId.toString}.yaml:" + e.getMessage(), e)
        }
      case None ⇒
        throw new IllegalArgumentException(s"Unable to load type schemas from URI with unknown scheme ${storageURI.getScheme}.")
    }
    schemas
  }
  /**
   * Lock marker state for reading.
   */
  @inline
  def safeRead[A](f: GraphMarker.ThreadUnsafeStateReadOnly ⇒ A): A = state.safeRead(f)
  /**
   * Lock marker state for updating.
   */
  @inline
  def safeUpdate[A](f: GraphMarker.ThreadUnsafeStateReadOnly ⇒ A): A = state.safeUpdate(f)
  /** Save type schemas to the local storage. */
  @log
  def saveTypeSchemas(schemas: immutable.Set[XTypeSchema], sData: SData) = state.safeWrite { state ⇒
    assertState()
    // Storages.
    val typeSchemasStorages = sData.get(SData.Key.explicitStorages) match {
      case Some(storages) ⇒
        storages.seq.flatMap(_.real).toSet
      case None ⇒
        val additionalStorages = graphAdditionalStorages
        if (additionalStorages.isEmpty) {
          log.debug("Graph haven't any active additional locations.")
          // there is only a local copy that is hidden from anyone
          Set(graphPath.toURI)
        } else {
          // build set with write only locations
          additionalStorages.flatMap(_ match {
            case Left(write) ⇒ Some(write)
            case _ ⇒ None
          }) + graphPath.toURI
        }
    }

    // Freeze schemas.
    for (storageURI ← typeSchemasStorages) Serialization.perScheme.get(storageURI.getScheme()) match {
      case Some(transport) ⇒
        val sDataForStorageURI = sData.updated(SData.Key.storageURI, storageURI)
        val typeSchemasStorageBase = transport.append(storageURI, folderTypeSchemas)
        val typeSchemasStorageBaseEncoded = Serialization.inner.encode(typeSchemasStorageBase, sDataForStorageURI)
        log.debug(s"Save type schemas to $typeSchemasStorageBase")
        // Clear folder.
        transport.delete(typeSchemasStorageBase, sDataForStorageURI)
        // Freeze.
        val schemaIndexURI = Serialization.inner.encode(transport.append(typeSchemasStorageBase, index), sDataForStorageURI)
        transport.write(schemaIndexURI, schemas.map(_.id.toString).mkString("\n").getBytes(io.Codec.UTF8.charSet), sDataForStorageURI)
        schemas.foreach { schema ⇒
          val schemaURI = Serialization.inner.encode(transport.append(typeSchemasStorageBase, schema.id.toString() + ".yaml"), sDataForStorageURI)
          transport.write(schemaURI, TypeSchema.YAML.to(schema).getBytes(io.Codec.UTF8.charSet), sDataForStorageURI)
        }
      case None ⇒
        throw new IllegalArgumentException(s"Unable to save type schemas to URI with unknown scheme ${storageURI.getScheme}.")
    }
  }

  /** Get values from graph properties with double checking. */
  protected def graphProperties[A](f: Properties ⇒ A): A =
    safeRead { state: GraphMarker.ThreadUnsafeStateReadOnly ⇒
      assertState()
      state.graphProperties.map(f)
    } getOrElse {
      safeUpdate { state ⇒
        state.graphProperties.map(f) getOrElse {
          require(state, true)
          state.graphProperties.map(f) getOrElse { throw new IOException("Unable to read graph properties.") }
        }
      }
    }
  /** Update values of graph properties. */
  protected def graphPropertiesUpdate[A](f: Properties ⇒ A): A =
    safeUpdate { state: GraphMarker.ThreadUnsafeStateReadOnly ⇒
      assertState()
      val result = state.graphProperties.map(f) getOrElse {
        require(state, true)
        state.graphProperties.map(f) getOrElse { throw new IOException("Unable to read graph properties.") }
      }
      markerSave()
      result
    }
  protected def initializePayload(): Payload = state.safeWrite { state ⇒
    log.info(s"Initialize payload for marker ${this}.")
    val payload = new Payload(this)
    // The load order is important
    // prevents deadlock while initialization
    App.execNGet {
      payload.elementTemplates
      payload.enumerations
      payload.typeSchemas
      payload.typeSchema
      payload.viewDefinitions
      payload.viewFilters
      payload.viewSortings
    }
    /*
     * create elements if needed
     * add description to elements
     * modify graph values
     */
    updateApplicationElements()
    // Type schemas
    val typeSchemaSet = TypeSchema.load(this)
    // reload type schemas
    log.debug("Update type schemas.")
    App.execNGet {
      payload.typeSchemas.clear
      typeSchemaSet.foreach(schema ⇒ payload.typeSchemas(schema.id) = schema)
      payload.typeSchema.value = TypeSchema.default
    }
    // Enumerations
    val enumerationSet = Enumeration.load(this)
    // reload enumerations
    log.debug("Update enumerations.")
    App.execNGet {
      payload.enumerations.clear
      enumerationSet.foreach(enumeration ⇒ payload.enumerations(enumeration.id) = enumeration)
    }
    // Templates
    // Reload element templates
    log.debug("Update element templates.")
    // Indirectly initialize payload.elementTemplates
    payload.originalElementTemplates
    // Set active type schema
    PredefinedElements.eSettings(state.graph).eGet[String]('activeTypeSchema) match {
      case Some(schemaValue) ⇒
        val schemaUUID = UUID.fromString(schemaValue)
        App.execNGet {
          payload.typeSchemas.get(schemaUUID) match {
            case Some(schema) ⇒ payload.typeSchema.value = schema
            case None ⇒ payload.typeSchema.value = TypeSchema.predefined.head
          }
        }
      case None ⇒
        App.execNGet { payload.typeSchema.value = TypeSchema.predefined.head }
    }
    // View
    log.debug("Update view difinitions.")
    val viewDefinitions = View.load(this)
    App.execNGet {
      payload.viewDefinitions.clear
      viewDefinitions.foreach(view ⇒ payload.viewDefinitions(view.id) = view)
    }
    val viewFilters = Filter.load(this)
    App.execNGet {
      payload.viewFilters.clear
      viewFilters.foreach(filter ⇒ payload.viewFilters(filter.id) = filter)
    }
    val viewSortings = Sorting.load(this)
    App.execNGet {
      payload.viewSortings.clear
      viewSortings.foreach(sorting ⇒ payload.viewSortings(sorting.id) = sorting)
    }
    payload
  }
  /** Initialize marker state. */
  protected def initializeState(): GraphMarker.ThreadSafeState = {
    GraphMarker.globalRWL.readLock().lock()
    try {
      GraphMarker.state.get(uuid) getOrElse GraphMarker.state.synchronized {
        val state = new GraphMarker.ThreadUnsafeState(None)
        GraphMarker.state(uuid) = state
        state
      }
    } finally GraphMarker.globalRWL.readLock().unlock()
  }
  /** Load model descriptor if needed. */
  protected def require(state: GraphMarker.ThreadUnsafeStateReadOnly, throwError: Boolean, updateViaSave: Boolean = true): Unit =
    if (autoload && state.graphProperties.isEmpty) {
      markerLoad()
      // and update last access time via markerSave()
      if (updateViaSave) markerSave()
    } else {
      if (throwError && state.graphProperties.isEmpty)
        throw new IllegalStateException(s"Graph properties ${resource} is not loaded.")
    }
  /** Update application elements. */
  protected def updateApplicationElements() = state.safeWrite { state ⇒
    val eTABuddy = PredefinedElements.eTABuddy(state.graph)
    if (eTABuddy.name.trim.isEmpty())
      eTABuddy.name = "TABuddy Desktop internal treespace"
    eTABuddy.eParent.map(_.rootBox.e) match {
      case Some(eTABaddyContainerAbs: Record.Like) ⇒
        val eTABaddyContainer = eTABaddyContainerAbs.eRelative
        if (eTABaddyContainer.name.trim.isEmpty())
          eTABaddyContainer.name = "TABuddy internal treespace"
      case _ ⇒
    }
    // settings
    val eSettings = PredefinedElements.eSettings(state.graph)
    if (eSettings.name.trim.isEmpty())
      eSettings.name = "TABuddy Desktop settings"
    val eEnumeration = PredefinedElements.eEnumeration(state.graph)
    if (eEnumeration.name.trim.isEmpty())
      eEnumeration.name = "Enumeration definitions"
    val eTemplate = PredefinedElements.eElementTemplate(state.graph)
    if (eTemplate.name.trim.isEmpty())
      eTemplate.name = "Element template definitions"
    // view
    val eView = PredefinedElements.eView(state.graph)
    if (eView.name.trim.isEmpty())
      eView.name = "TABuddy Desktop view modificator elements"
    // view definition
    val eViewDefinition = PredefinedElements.eViewDefinition(state.graph)
    if (eViewDefinition.name.trim.isEmpty())
      eViewDefinition.name = "View definition"
    // view sorting
    val eViewSorting = PredefinedElements.eViewSorting(state.graph)
    if (eViewSorting.name.trim.isEmpty())
      eViewSorting.name = "View sorting"
    // view filter
    val eViewFilter = PredefinedElements.eViewFilter(state.graph)
    if (eViewFilter.name.trim.isEmpty())
      eViewFilter.name = "View filter"
  }

  def canEqual(other: Any) = other.isInstanceOf[GraphMarker]
  override def equals(other: Any) = other match {
    case that: GraphMarker ⇒ (this eq that) || { that.canEqual(this) && uuid == that.uuid }
    case _ ⇒ false
  }
  override def hashCode() = uuid.hashCode()

  override lazy val toString = s"GraphMarker[${uuid}]"
}

object GraphMarker extends Loggable {
  /**
   * Field that contains map of graph additional storages except local one.
   * Value is indicating whether the location is available or not.
   */
  val fieldAdditionalStorages = "additionalStorages"
  /** Long field that contains graph marker creation time. */
  val fieldCreatedMillis = "createdMillis"
  /** Long field that contains graph marker creation time. */
  val fieldCreatedNanos = "createdNanos"
  /** String field that contains default serialization extension. */
  val fieldDefaultSerialization = "defaultSerializationExtension"
  /** Composite field that contains digestAcquire parameters. */
  val fieldDigestAcquire = "digestAcquire"
  /** Composite field that contains digestFreeze parameters. */
  val fieldDigestFreeze = "digestFreeze"
  /** Composite field that contains containerEncryption parameters. */
  val fieldContainerEncryption = "containerEncryption"
  /** Composite field that contains contentEncryption parameters. */
  val fieldContentEncryption = "contentEncryption"
  /** Long field that contains graph marker last accessed time. */
  val fieldLastAccessed = "lastAccessed"
  /** Field that contains graph origin. */
  val fieldOrigin = "origin"
  /** Field that contains path to the local copy of graph. */
  val fieldPath = "localStorage"
  /** Field that contains graph marker UUID. */
  val fieldResourceId = "resourceId"
  /** Long field that contains graph store time. */
  val fieldSavedMillis = "savedMillis"
  /** Long field that contains graph store time. */
  val fieldSavedNanos = "savedNanos"
  /** Composite field that contains signatureAcquire parameters. */
  val fieldSignatureAcquire = "signatureAcquire"
  /** Composite field that contains signatureFreeze parameters. */
  val fieldSignatureFreeze = "signatureFreeze"
  /** GraphMarker lock */
  /*
   * Every external operation that created/deleted marker AND locked any marker MUST use this lock at first.
   * like OperationGraphClose, OperationGraphDelete
   */
  val globalRWL = new ReentrantReadWriteLock
  /** Application wide GraphMarker states. */
  protected val state = new mutable.HashMap[UUID, ThreadSafeState]() with mutable.SynchronizedMap[UUID, ThreadSafeState]

  /** Get marker for UUID. */
  def apply(uuid: UUID): GraphMarker = state.get(uuid).flatMap(_.graphMarkerSingleton) getOrElse new GraphMarker(uuid)
  /** Get marker for graph. */
  def apply(graph: Graph[_ <: Model.Like]): GraphMarker = graph.withData(_(GraphMarker).asInstanceOf[GraphMarker])
  /** Bind marker to context. */
  def bind(marker: GraphMarker, context: Context = Core.context) = {
    globalRWL.readLock().lock()
    try {
      log.debug(s"Bind ${marker} to ${context}")
      contextToMarker(context).foreach(_ ⇒ unbind(context))
      marker.state.asInstanceOf[ThreadUnsafeState].contextRefs(context) = ()
      context.set(classOf[GraphMarker], marker)
    } finally globalRWL.readLock().unlock()
  }
  /** Get marker binded to context. */
  def contextToMarker(context: Context): Option[GraphMarker] = {
    globalRWL.readLock().lock()
    try Option(context.getLocal(classOf[GraphMarker]))
    finally globalRWL.readLock().unlock()
  }
  /**
   * Create new graph marker in the workspace
   *
   * @param uuid container IResource unique id
   * @param fullPath path to model with model directory name
   * @param created graph creation timestamp
   * @param origin graph owner that launch creation process
   * @param serialization type of the serialization
   * @return graph marker
   */
  def createInTheWorkspace(resourceUUID: UUID, fullPath: File, created: Element.Timestamp, origin: Symbol, serialization: Serialization.Identifier): GraphMarker = {
    globalRWL.writeLock().lock()
    try {
      if (!Logic.container.isOpen())
        throw new IllegalStateException("Workspace is not available.")
      val resourceName = resourceUUID.toString + "." + Payload.extensionGraph
      val resourceFile = Logic.container.getFile(resourceName) // throws IllegalStateException: Workspace is closed.
      if (resourceFile.exists())
        throw new IllegalStateException("Graph marker is already exists at " + resourceFile.getLocationURI())
      if (list.map(apply).find(_.graphPath == fullPath).nonEmpty)
        throw new IllegalStateException("Graph marker is already exists for " + fullPath)
      log.debug(s"Prepare graph ${fullPath.getName}")
      if (!fullPath.exists())
        if (!fullPath.mkdirs())
          throw new IOException("Unable to create graph storage at " + fullPath.getAbsolutePath())
      val resourceContent = new Properties
      resourceContent.setProperty(fieldPath, fullPath.getCanonicalPath())
      resourceContent.setProperty(fieldAdditionalStorages, 0.toString())
      resourceContent.setProperty(fieldLastAccessed, created.milliseconds.toString)
      resourceContent.setProperty(fieldCreatedMillis, created.milliseconds.toString)
      resourceContent.setProperty(fieldCreatedNanos, created.nanoShift.toString)
      resourceContent.setProperty(fieldDefaultSerialization, serialization.extension)
      resourceContent.setProperty(fieldResourceId, resourceUUID.toString)
      resourceContent.setProperty(fieldOrigin, origin.name)
      resourceContent.setProperty(fieldSavedMillis, 0.toString)
      resourceContent.setProperty(fieldSavedNanos, 0.toString)
      val output = new ByteArrayOutputStream()
      Report.info match {
        case Some(info) ⇒ resourceContent.store(output, info.toString)
        case None ⇒ resourceContent.store(output, null)
      }
      val input = new ByteArrayInputStream(output.toByteArray())
      log.debug(s"Create model marker: ${resourceName}.")
      resourceFile.create(input, IResource.NONE, null)
      new GraphMarker(resourceUUID, true)
    } finally globalRWL.writeLock().unlock()
  }
  /** Permanently delete marker from the workspace. */
  def deleteFromWorkspace(marker: GraphMarker): ReadOnlyGraphMarker = {
    globalRWL.writeLock().lock()
    try {
      marker.safeUpdate {
        case state: ThreadUnsafeState ⇒
          val readOnlyMarker = new ReadOnlyGraphMarker(marker.uuid, marker.graphAdditionalStorages, marker.graphCreated,
            marker.graphModelId, marker.graphOrigin, marker.graphPath, marker.graphStored, marker.markerLastAccessed,
            marker.defaultSerialization, marker.digest, marker.containerEncryption, marker.contentEncryption, marker.signature)
          if (!FileUtil.deleteFile(marker.graphPath))
            log.fatal("Unable to delete " + marker)
          marker.resource.delete(true, false, Policy.monitorFor(null))
          GraphMarker.state.remove(marker.uuid)
          state.graphObject = null
          state.graphObject = null
          state.payloadObject = null
          readOnlyMarker
      }
    } finally globalRWL.writeLock().unlock()
  }
  /** Get a graph list. */
  def list(): Seq[UUID] = {
    globalRWL.readLock().lock()
    try Logic.container.members.flatMap { resource ⇒
      if (resource.getFileExtension() == Payload.extensionGraph)
        try Some(UUID.fromString(resource.getName().takeWhile(_ != '.')))
        catch {
          case e: Throwable ⇒
            log.warn("Skip model marker with invalid name: " + resource.getName)
            None
        }
      else
        None
    }
    finally globalRWL.readLock().unlock()
  }
  /** Get list of contexts binded to marker. */
  def markerToContext(marker: GraphMarker): Seq[Context] = {
    globalRWL.readLock().lock()
    try marker.state.asInstanceOf[ThreadUnsafeState].contextRefs.keys.toSeq
    finally globalRWL.readLock().unlock()
  }
  /** Get a shell which is suitable for the graph marker. */
  def shell(graph: Graph[_ <: Model.Like]): Option[(Context, Shell)] =
    shell(GraphMarker(graph))
  /** Get a shell which is suitable for the graph marker. */
  def shell(marker: GraphMarker): Option[(Context, Shell)] = {
    log.debug(s"Search shell for $marker.")
    lazy val contexts = Core.context.getChildren().filter(_.containsKey(classOf[Composite], true))
    val markerContexts = GraphMarker.markerToContext(marker)
    val activeLeaf = Core.context.getActiveLeaf()
    // active branch from leaf to root
    val activeBranch = (activeLeaf.getParents() :+ activeLeaf).reverse
    // find shell within active branch for this marker
    val activeShellForMarker = activeBranch.find(markerContexts.contains).flatMap { mostCommonContext ⇒
      activeBranch.find(_.containsKey(classOf[Composite], true)).map { contextWithComposite ⇒
        log.debug(s"Found shell for ${marker} in active branch ${contextWithComposite}.")
        (contextWithComposite, contextWithComposite.get(classOf[Composite]).getShell())
      }
    }
    // find shell for this marker
    val passiveShellForMarker = activeShellForMarker orElse {
      contexts.find(markerContexts.contains).map { contextWithComposite ⇒
        log.debug(s"Found shell for ${marker} in passive ${contextWithComposite}.")
        (contextWithComposite, contextWithComposite.get(classOf[Composite]).getShell())
      }
    }
    // find shell within active branch
    val activeShell = passiveShellForMarker orElse {
      activeBranch.find(_.containsKey(classOf[Composite], true)).map { contextWithComposite ⇒
        log.debug(s"Found shell in active branch ${contextWithComposite}.")
        (contextWithComposite, contextWithComposite.get(classOf[Composite]).getShell())
      }
    }
    // find shell ...
    activeShell orElse {
      // search for any context with composites
      contexts.find(ctx ⇒ ctx.getParent().getActiveChild() == ctx) match {
        case Some(contextWithComposite) ⇒
          log.debug(s"Found shell that is actived at least once in ${contextWithComposite}.")
          Some((contextWithComposite, contextWithComposite.get(classOf[Composite]).getShell()))
        case None ⇒
          contexts.headOption.map { contextWithComposite ⇒
            log.debug(s"Found shell in some context ${contextWithComposite}.")
            (contextWithComposite, contextWithComposite.get(classOf[Composite]).getShell())
          }
      }
    }
  }
  /** Create temporary graph marker. */
  def temporary(graph: Graph[_ <: Model]): TemporaryGraphMarker = new TemporaryGraphMarker(graph)
  /** Unbind marker from context. */
  def unbind(context: Context) = {
    globalRWL.readLock().lock()
    try {
      val marker = contextToMarker(context).get // throw if empty
      log.debug(s"Unbind ${marker} from ${context}")
      context.remove(classOf[GraphMarker])
      marker.state.asInstanceOf[ThreadUnsafeState].contextRefs.remove(context)
      if (marker.state.asInstanceOf[ThreadUnsafeState].contextRefs.isEmpty && marker.graphIsOpen())
        marker.safeUpdate(e ⇒ OperationGraphClose(e.graph, false))
    } finally globalRWL.readLock().unlock()
  }

  /**
   * Graph marker thread safe object.
   */
  trait ThreadSafeState {
    this: ThreadUnsafeState ⇒
    /** State read/write lock. */
    protected val rwl = new ReentrantReadWriteLock
    /** Contains the specific singleton instance. */
    val graphMarkerSingleton: Option[GraphMarker]

    /** Lock this state for reading. */
    def safeRead[A](f: ThreadUnsafeStateReadOnly ⇒ A): A = {
      rwl.readLock().lock()
      try f(this) finally rwl.readLock().unlock()
    }
    /** Lock this state for updating field content. */
    def safeUpdate[A](f: ThreadUnsafeStateReadOnly ⇒ A): A = safeWrite(f)
    /** Lock this state for writing. */
    def safeWrite[A](f: ThreadUnsafeState ⇒ A): A = {
      rwl.writeLock().lock()
      try f(this) finally rwl.writeLock().unlock()
    }
  }
  /**
   * Graph marker thread unsafe read only object.
   */
  trait ThreadUnsafeStateReadOnly extends ThreadSafeState {
    this: ThreadUnsafeState ⇒
    /** Get graph. */
    def graph: Graph[_ <: Model.Like]
    /** Get payload. */
    def payload: Payload
    /** Get graph properties. */
    def graphProperties: Option[Properties]
  }
  /**
   * Graph marker thread unsafe object.
   *
   * @param graphMarkerSingleton returns the specific singleton instance instead of generic GraphMarker
   */
  class ThreadUnsafeState(val graphMarkerSingleton: Option[GraphMarker]) extends ThreadUnsafeStateReadOnly {
    /** Map of marker contexts binded with this graph. */
    val contextRefs = new mutable.WeakHashMap[Context, Unit] with mutable.SynchronizedMap[Context, Unit]
    /** Graph. */
    private var graphObjectContainer = Option.empty[Graph[_ <: Model.Like]]
    /** Graph properties. */
    private var graphPropertiesContainer = Option.empty[Properties]
    /** Payload. */
    private var payloadObjectContainer = Option.empty[Payload]

    /** Get graph. */
    def graph: Graph[_ <: Model.Like] = graphObject getOrElse { throw new IllegalStateException("Graph not loaded.") }
    /** Thread unsafe graph getter. */
    def graphObject = graphObjectContainer
    /** Thread unsafe Graph setter. */
    def graphObject_=(arg: Option[Graph[_ <: Model.Like]]) = graphObjectContainer = arg
    /** Thread unsafe Graph properties getter. */
    def graphProperties: Option[Properties] = graphPropertiesContainer
    /** Thread unsafe Graph properties setter. */
    def graphProperties_=(arg: Option[Properties]) = graphPropertiesContainer = arg
    /** Thread unsafe get payload. */
    def payload: Payload = payloadObject getOrElse { throw new IllegalStateException("Payload not initialized.") }
    /** Thread unsafe Payload getter. */
    def payloadObject: Option[Payload] = payloadObjectContainer
    /** Thread unsafe Payload setter. */
    def payloadObject_=(arg: Option[Payload]) = payloadObjectContainer = arg
    override def toString() = s"GraphMarker.State($graphObject, $graphProperties, $payloadObject)"
  }
  /**
   * Nil timestamp
   */
  object TimestampNil extends Element.Timestamp(0, 0)
  /** Read only marker. */
  class ReadOnlyGraphMarker(val uuid: UUID, val graphAdditionalStorages: Set[Either[URI, URI]],
    val graphCreated: Element.Timestamp, val graphModelId: Symbol,
    val graphOrigin: Symbol, val graphPath: File, val graphStored: Element.Timestamp, val markerLastAccessed: Long,
    val defaultSerialization: Serialization.Identifier, val digest: XGraphMarker.Digest, val containerEncryption: XGraphMarker.Encryption,
    val contentEncryption: XGraphMarker.Encryption, val signature: XGraphMarker.Signature)
    extends XGraphMarker {
    /** Autoload property file if suitable information needed. */
    val autoload = false

    /** Assert marker state. */
    def assertState() {}
    /** Load the specific graph from the predefined directory ${location}/id/ */
    def graphAcquire(modified: Option[Element.Timestamp] = None, reload: Boolean = false, takeItEasy: Boolean = false) =
      throw new UnsupportedOperationException()
    /** Acquire graph loader. */
    def graphAcquireLoader(modified: Option[Element.Timestamp] = None): Serialization.Loader =
      throw new UnsupportedOperationException()
    /** Close the loaded graph. */
    def graphClose() = throw new UnsupportedOperationException()
    /** Store the graph to the predefined directory ${location}/id/ */
    def graphFreeze(storages: Option[Serialization.Storages] = None): Unit = throw new UnsupportedOperationException()
    /** Check whether the graph is modified. */
    def graphIsDirty(): Boolean = false
    /** Check whether the graph is loaded. */
    def graphIsOpen(): Boolean = false
    /** Load type schemas from local storage. */
    def loadTypeSchemas(storage: Option[URI] = None): immutable.HashSet[XTypeSchema] =
      throw new UnsupportedOperationException()
    /**
     * Lock this marker for reading.
     */
    def safeRead[A](f: GraphMarker.ThreadUnsafeStateReadOnly ⇒ A): A = throw new UnsupportedOperationException()
    /**
     * Lock marker state for updating.
     */
    def safeUpdate[A](f: GraphMarker.ThreadUnsafeStateReadOnly ⇒ A): A = throw new UnsupportedOperationException()
    /** The validation flag indicating whether the marker is consistent. */
    def markerIsValid: Boolean = false
    /** Load marker properties. */
    def markerLoad() = throw new UnsupportedOperationException()
    /** Save marker properties. */
    def markerSave() = throw new UnsupportedOperationException()
    /** Save type schemas to the local storage. */
    def saveTypeSchemas(schemas: immutable.Set[XTypeSchema], sData: SData) =
      throw new UnsupportedOperationException()

    def canEqual(other: Any) = other.isInstanceOf[ReadOnlyGraphMarker]
    override def equals(other: Any) = other match {
      case that: ReadOnlyGraphMarker ⇒ (this eq that) || {
        that.canEqual(this) && uuid == that.uuid && graphCreated == that.graphCreated &&
          graphModelId == that.graphModelId && graphOrigin == that.graphOrigin && graphPath == that.graphPath &&
          graphStored == that.graphStored && markerLastAccessed == that.markerLastAccessed
      }
      case _ ⇒ false
    }
    override def hashCode() = lazyHashCode
    protected lazy val lazyHashCode = java.util.Arrays.hashCode(Array[AnyRef](uuid, graphCreated,
      graphModelId, graphOrigin, graphPath, graphStored, Long.box(markerLastAccessed)))

    override lazy val toString = s"ROGraphMarker[${uuid}]"
  }
  /** Temporary marker for in memory graph. */
  class TemporaryGraphMarker(graph: Graph[_ <: Model]) extends GraphMarker(UUID.randomUUID(), false) {
    /** Assert marker state. */
    override def assertState() {}
    /** Get default serialization identifier. */
    override def defaultSerialization: Serialization.Identifier = graph.model.eBox.serialization
    /** Get digest settings. */
    override def digest: XGraphMarker.Digest = XGraphMarker.Digest(None, None)
    /** Set digest settings. */
    override def digest_=(settings: XGraphMarker.Digest) = throw new UnsupportedOperationException()
    /** Get container encryption settings. */
    override def containerEncryption: XGraphMarker.Encryption = XGraphMarker.Encryption(Map())
    /** Set container encryption settings. */
    override def containerEncryption_=(settings: XGraphMarker.Encryption) = throw new UnsupportedOperationException()
    /** Get content encryption settings. */
    override def contentEncryption: XGraphMarker.Encryption = XGraphMarker.Encryption(Map())
    /** Set content encryption settings. */
    override def contentEncryption_=(settings: XGraphMarker.Encryption) = throw new UnsupportedOperationException()
    /** Load the specific graph from the predefined directory ${location}/id/ */
    override def graphAcquire(modified: Option[Element.Timestamp] = None, reload: Boolean = false, takeItEasy: Boolean = false) =
      throw new UnsupportedOperationException()
    /** Acquire graph loader. */
    override def graphAcquireLoader(modified: Option[Element.Timestamp] = None): Serialization.Loader =
      throw new UnsupportedOperationException()
    /** Graph creation timestamp. */
    override def graphCreated: Element.Timestamp = graph.created
    /** Close the loaded graph. */
    override def graphClose() = throw new UnsupportedOperationException()
    /** Store the graph to the predefined directory ${location}/id/ */
    override def graphFreeze(storages: Option[Serialization.Storages] = None): Unit = throw new UnsupportedOperationException()
    /** Check whether the graph is modified. */
    override def graphIsDirty(): Boolean = true
    /** Check whether the graph is loaded. */
    override def graphIsOpen(): Boolean = true
    /** Model ID. */
    override def graphModelId: Symbol = graph.model.eId
    /** Origin of the graph. */
    override def graphOrigin: Symbol = graph.origin
    /** Path to the graph: base directory and graph directory name. */
    override def graphPath: File = throw new UnsupportedOperationException()
    /** The latest graph modified timestamp that was saved to storages. */
    override def graphStored: Element.Timestamp = graph.created
    /** Load type schemas from local storage. */
    override def loadTypeSchemas(storage: Option[URI] = None): immutable.HashSet[XTypeSchema] =
      throw new UnsupportedOperationException()
    /** Register marker state. */
    def register() = GraphMarker.state.get(uuid) match {
      case Some(marker) ⇒
        throw new IllegalStateException(s"${this} is already registered.")
      case None ⇒
        GraphMarker.state(uuid) = state
    }
    /** The validation flag indicating whether the marker is consistent. */
    override def markerIsValid: Boolean = false
    /** Marker last access timestamp. */
    override def markerLastAccessed: Long = System.currentTimeMillis()
    /** Load marker properties. */
    override def markerLoad() = throw new UnsupportedOperationException()
    /** Save marker properties. */
    override def markerSave() = throw new UnsupportedOperationException()
    /** Save type schemas to the local storage. */
    override def saveTypeSchemas(schemas: immutable.Set[XTypeSchema], sData: SData) =
      throw new UnsupportedOperationException()
    /** Get signature settings. */
    override def signature: XGraphMarker.Signature = XGraphMarker.Signature(None, None)
    /** Set signature settings. */
    override def signature_=(settings: XGraphMarker.Signature) = throw new UnsupportedOperationException()
    /** Unregister marker state. */
    def unregister() = GraphMarker.state.get(uuid) match {
      case Some(marker) ⇒
        GraphMarker.state.remove(uuid)
      case None ⇒
        throw new IllegalStateException(s"${this} is not registered.")
    }

    /** Initialize marker state. */
    override protected def initializeState() = {
      val state = new TemporaryGraphMarker.ImmutableState(Some(this))
      graph.withData { data ⇒
        data.get(GraphMarker) match {
          case Some(marker) ⇒
            throw new IllegalArgumentException(s"$graph already have marker.")
          case None ⇒
            data(GraphMarker) = this
        }
      }
      state
    }

    override def canEqual(other: Any) = other.isInstanceOf[TemporaryGraphMarker]
    override def equals(other: Any) = other match {
      case that: TemporaryGraphMarker ⇒ (this eq that) || { that.canEqual(this) && uuid == that.uuid }
      case _ ⇒ false
    }

    override lazy val toString = s"TemporaryGraphMarker[${uuid}]"
  }
  object TemporaryGraphMarker {
    /**
     * Immutable state container for TemporaryGraphMarker
     */
    class ImmutableState(singleton: Option[TemporaryGraphMarker])
      extends GraphMarker.ThreadUnsafeState(singleton) {
      /** Graph getter. */
      override def graphObject = singleton.map(_.graphAcquire()): Option[Graph[_ <: Model.Like]]
      /** Graph setter. */
      override def graphObject_=(arg: Option[Graph[_ <: Model.Like]]) = ???
      /** Graph properties getter. */
      override def graphProperties: Option[Properties] = ???
      /** Graph properties setter. */
      override def graphProperties_=(arg: Option[Properties]) = ???
      /** Payload getter. */
      override def payloadObject: Option[Payload] = ???
      /** Payload setter. */
      override def payloadObject_=(arg: Option[Payload]) = ???
      /** Lock this state for reading. */
      override def safeRead[A](f: ThreadUnsafeStateReadOnly ⇒ A): A = f(this)
      /** Lock this state for updating field content. */
      override def safeUpdate[A](f: ThreadUnsafeStateReadOnly ⇒ A): A = safeWrite(f)
      /** Lock this state for writing. */
      override def safeWrite[A](f: ThreadUnsafeState ⇒ A): A = f(this)
    }
  }
}
