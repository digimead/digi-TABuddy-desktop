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

package org.digimead.tabuddy.desktop.logic.payload.maker

import com.google.common.base.Charsets
import com.google.common.io.Files
import java.io.{ ByteArrayInputStream, ByteArrayOutputStream, File, FileFilter, FileOutputStream, IOException }
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.{ Properties, UUID }
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.digi.lib.util.FileUtil
import org.digimead.tabuddy.desktop.core.Core
import org.digimead.tabuddy.desktop.core.Report
import org.digimead.tabuddy.desktop.core.definition.Context
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.logic.Logic
import org.digimead.tabuddy.desktop.logic.operation.graph.OperationGraphClose
import org.digimead.tabuddy.desktop.logic.payload.DSL._
import org.digimead.tabuddy.desktop.logic.payload.view.{ Filter, Sorting, View }
import org.digimead.tabuddy.desktop.logic.payload.{ ElementTemplate, Enumeration, Payload, PredefinedElements, TypeSchema, api ⇒ payloadapi }
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.Record
import org.digimead.tabuddy.model.element.Element
import org.digimead.tabuddy.model.graph.Graph
import org.eclipse.core.internal.utils.Policy
import org.eclipse.core.resources.IFile
import org.eclipse.core.resources.IResource
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
 *     ... and so on
 */
class GraphMarker(
  /** Container IResource unique id. */
  val uuid: UUID,
  /** Autoload property file if suitable information needed. */
  val autoload: Boolean = true) extends api.GraphMarker with MarkerSpecific with GraphSpecific with Loggable {
  /** Type schemas folder name. */
  val folderTypeSchemas = "typeSchemas"

  /** Get container resource */
  lazy val resource: IFile = {
    val resourceName = uuid.toString + "." + Payload.extensionGraph
    Logic.container.getFile(resourceName)
  }
  /** GraphMarker mutable state. */
  val state: GraphMarker.ThreadSafeState = GraphMarker.lock.synchronized {
    GraphMarker.state.get(uuid) getOrElse GraphMarker.state.synchronized {
      val state = new GraphMarker.ThreadUnsafeState()
      GraphMarker.state(uuid) = state
      state
    }
  }

  /** Assert marker state. */
  def assertState() = lockRead { state ⇒
    if (state.asInstanceOf[GraphMarker.ThreadUnsafeState].payloadObject == null)
      throw new IllegalStateException(s"${this} points to disposed data.")
  }
  /** Load type schemas from local storage. */
  @log
  def loadTypeSchemas(): immutable.HashSet[payloadapi.TypeSchema] = lockRead { state ⇒
    assertState()
    val typeSchemasStorage = new File(graphPath, folderTypeSchemas)
    log.debug(s"Load type schemas from $typeSchemasStorage")
    if (!typeSchemasStorage.exists())
      return immutable.HashSet()
    val prefix = graphPath.getCanonicalPath().size + 1
    var schemas = immutable.HashSet[payloadapi.TypeSchema]()
    typeSchemasStorage.listFiles(new FileFilter { def accept(file: File) = file.isFile() && file.getName().endsWith(".yaml") }).foreach { file ⇒
      try {
        log.debug("Load ... " + file.getCanonicalPath().substring(prefix))
        val yaml = Files.toString(file, Charsets.UTF_8)
        try {
          TypeSchema.YAML.from(yaml).foreach(schema ⇒ schemas = schemas + schema)
        } catch {
          case e: Throwable ⇒
            log.error("Unable to load type schema %s: %s".format(file.getName(), e), e)
        }
      } catch {
        case e: IOException ⇒
          log.error("Unable to load type schema %s: %s".format(file.getName(), e))
      }
    }
    schemas
  }
  /**
   * Lock marker state for reading.
   */
  @inline
  def lockRead[A](f: GraphMarker.ThreadUnsafeStateReadOnly ⇒ A): A = state.lockRead(f)
  /**
   * Lock marker state for updating.
   */
  @inline
  def lockUpdate[A](f: GraphMarker.ThreadUnsafeStateReadOnly ⇒ A): A = state.lockUpdate(f)
  /** Save type schemas to the local storage. */
  @log
  def saveTypeSchemas(schemas: immutable.Set[payloadapi.TypeSchema]) = state.lockWrite { state ⇒
    assertState()
    val typeSchemasStorage = new File(graphPath, folderTypeSchemas)
    log.debug(s"Save type schemas to $typeSchemasStorage")
    if (!typeSchemasStorage.exists())
      if (!typeSchemasStorage.mkdirs())
        throw new RuntimeException("Unable to create type schemas storage at " + typeSchemasStorage.getAbsolutePath())
    typeSchemasStorage.listFiles(new FileFilter { def accept(file: File) = file.isFile() && file.getName().endsWith(".yaml") }).foreach(_.delete())
    schemas.foreach(schema ⇒
      Files.write(TypeSchema.YAML.to(schema), new File(typeSchemasStorage, schema.id.toString() + ".yaml"), Charsets.UTF_8))
  }

  /** Get value from graph descriptor with double checking. */
  protected def getValueFromGraphDescriptor[A](f: Properties ⇒ A): A =
    lockRead { state: GraphMarker.ThreadUnsafeStateReadOnly ⇒
      assertState()
      state.graphDescriptorProperties.map(f)
    } getOrElse {
      lockUpdate { state ⇒
        state.graphDescriptorProperties.map(f) getOrElse {
          require(state, true)
          state.graphDescriptorProperties.map(f) getOrElse { throw new IOException("Unable to read graph descriptor properties.") }
        }
      }
    }
  /** Get value from IResource properties with double checking. */
  protected def getValueFromIResourceProperties[A](f: Properties ⇒ A): A =
    lockRead { state: GraphMarker.ThreadUnsafeStateReadOnly ⇒
      assertState()
      state.resourceProperties.map(f)
    } getOrElse {
      lockUpdate { state ⇒
        state.resourceProperties.map(f) getOrElse {
          require(state, true)
          state.resourceProperties.map(f) getOrElse { throw new IOException("Unable to read IResource properties.") }
        }
      }
    }
  protected def initializePayload(): Payload = state.lockWrite { state ⇒
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
  /** Load model descriptor if needed. */
  protected def require(state: GraphMarker.ThreadUnsafeStateReadOnly, throwError: Boolean, updateViaSave: Boolean = true): Unit =
    if (autoload && (state.graphDescriptorProperties.isEmpty || state.resourceProperties.isEmpty)) {
      markerLoad()
      // and update last access time via
      if (updateViaSave) markerSave()
    } else {
      if (throwError && state.graphDescriptorProperties.isEmpty)
        throw new IllegalStateException(s"Model descriptor ${graphDescriptor} not loaded.")
    }
  /** Update application elements. */
  protected def updateApplicationElements() = state.lockWrite { state ⇒
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

  override def toString() = s"GraphMarker[${uuid}]"
}

object GraphMarker extends Loggable {
  val fieldCreatedMillis = "createdMillis"
  val fieldCreatedNanos = "createdNanos"
  val fieldLastAccessed = "lastAccessed"
  val fieldOrigin = "origin"
  val fieldPath = "path"
  val fieldResourceId = "resourceId"
  val fieldSavedMillis = "savedMillis"
  val fieldSavedNanos = "savedNanos"
  /** GraphMarker lock */
  val lock = new Object
  /** Application wide GraphMarker states. */
  protected val state = new mutable.HashMap[UUID, ThreadSafeState]() with mutable.SynchronizedMap[UUID, ThreadSafeState]

  /** Get marker for UUID. */
  def apply(uuid: UUID): GraphMarker = new GraphMarker(uuid)
  /** Get marker for graph. */
  def apply(graph: Graph[_ <: Model.Like]): GraphMarker = graph.withData(_(GraphMarker).asInstanceOf[GraphMarker])
  /** Bind marker to context. */
  def bind(marker: GraphMarker, context: Context = Core.context) = lock.synchronized {
    log.debug(s"Bind ${marker} to ${context}")
    contextToMarker(context).foreach(_ ⇒ unbind(context))
    marker.state.asInstanceOf[ThreadUnsafeState].contextRefs(context) = ()
    context.set(classOf[GraphMarker], marker)
  }
  /** Get marker binded to context. */
  def contextToMarker(context: Context): Option[GraphMarker] =
    lock.synchronized { Option(context.getLocal(classOf[GraphMarker])) }
  /**
   * Create new model marker in the workspace.
   *
   * @param uuid container IResource unique id.
   * @param fullPath path to model with model directory name.
   * @param created model creation timestamp.
   * @param origin model owner that launch creation process.
   * @return model marker
   */
  def createInTheWorkspace(resourceUUID: UUID, fullPath: File, created: Element.Timestamp, origin: Symbol): GraphMarker = lock.synchronized {
    if (!Logic.container.isOpen())
      throw new IllegalStateException("Workspace is not available.")
    val resourceName = resourceUUID.toString + "." + Payload.extensionGraph
    val resourceFile = Logic.container.getFile(resourceName) // throws IllegalStateException: Workspace is closed.
    val path = fullPath.getParentFile()
    val id = fullPath.getName
    val graphDescriptorFile = new File(path, id + "." + Payload.extensionGraph)
    if (resourceFile.exists())
      throw new IllegalStateException("Model marker is already exists at " + resourceFile.getLocationURI())
    if (graphDescriptorFile.exists())
      throw new IllegalStateException("Graph marker descriptor is already exists at " + graphDescriptorFile.getAbsolutePath())
    log.debug(s"Prepare model ${Symbol(id)}")
    if (!fullPath.exists())
      if (!fullPath.mkdirs())
        throw new IOException("Unable to create model storage at " + fullPath.getAbsolutePath())
    if (!graphDescriptorFile.createNewFile())
      throw new IOException("Unable to create model descriptor at " + graphDescriptorFile.getAbsolutePath())
    val graphDescriptor = new Properties()
    graphDescriptor.setProperty(fieldCreatedMillis, created.milliseconds.toString)
    graphDescriptor.setProperty(fieldCreatedNanos, created.nanoShift.toString)
    graphDescriptor.setProperty(fieldResourceId, resourceUUID.toString)
    graphDescriptor.setProperty(fieldOrigin, origin.name)
    graphDescriptor.setProperty(fieldSavedMillis, created.milliseconds.toString)
    graphDescriptor.setProperty(fieldSavedNanos, created.nanoShift.toString)
    log.debug(s"Create model descriptor: ${graphDescriptorFile.getName}.")
    Report.info match {
      case Some(info) ⇒ graphDescriptor.store(new FileOutputStream(graphDescriptorFile), info.toString)
      case None ⇒ graphDescriptor.store(new FileOutputStream(graphDescriptorFile), null)
    }
    val resourceContent = new Properties
    resourceContent.setProperty(fieldPath, fullPath.getCanonicalPath())
    resourceContent.setProperty(GraphMarker.fieldLastAccessed, created.milliseconds.toString)
    val output = new ByteArrayOutputStream()
    resourceContent.store(output, null)
    val input = new ByteArrayInputStream(output.toByteArray())
    log.debug(s"Create model marker: ${resourceName}.")
    resourceFile.create(input, IResource.NONE, null)
    new GraphMarker(resourceUUID, true)
  }
  /** Permanently delete marker from the workspace. */
  def deleteFromWorkspace(marker: GraphMarker): ReadOnlyGraphMarker = marker.lockUpdate {
    lock.synchronized {
      case state: ThreadUnsafeState ⇒
        val readOnlyMarker = new ReadOnlyGraphMarker(marker.uuid, marker.graphCreated, marker.graphModelId,
          marker.graphOrigin, marker.graphPath, marker.graphStored, marker.markerLastAccessed)
        val path = readOnlyMarker.graphPath.getParentFile()
        val id = readOnlyMarker.graphPath.getName
        val graphDescriptorFile = new File(path, id + "." + Payload.extensionGraph)
        if (!FileUtil.deleteFile(marker.graphPath))
          log.fatal("Unable to delete " + marker)
        graphDescriptorFile.delete()
        marker.resource.delete(true, false, Policy.monitorFor(null))
        state.graphObject = null
        state.graphDescriptorProperties = null
        state.graphObject = null
        state.payloadObject = null
        GraphMarker.state.remove(marker.uuid)
        readOnlyMarker
    }
  }
  /** Get a graph list. */
  def list(): Seq[UUID] = lock.synchronized {
    Logic.container.members.flatMap { resource ⇒
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
  }
  /** Get list of contexts binded to marker. */
  def markerToContext(marker: GraphMarker): Seq[Context] =
    lock.synchronized { marker.state.asInstanceOf[ThreadUnsafeState].contextRefs.keys.toSeq }
  /** Unbind marker from context. */
  def unbind(context: Context) = lock.synchronized {
    val marker = contextToMarker(context).get // throw if empty
    log.debug(s"Unbind ${marker} from ${context}")
    context.remove(classOf[GraphMarker])
    marker.state.asInstanceOf[ThreadUnsafeState].contextRefs.remove(context)
    if (marker.state.asInstanceOf[ThreadUnsafeState].contextRefs.isEmpty && marker.graphIsOpen())
      marker.lockUpdate(e ⇒ OperationGraphClose(e.graph, false))
  }

  /**
   * Graph marker thread safe object.
   */
  trait ThreadSafeState {
    this: ThreadUnsafeState ⇒
    /** State read/write lock. */
    protected val rwl = new ReentrantReadWriteLock()

    /** Lock this state for reading. */
    def lockRead[A](f: ThreadUnsafeStateReadOnly ⇒ A): A = {
      rwl.readLock().lock()
      try f(this) finally rwl.readLock().unlock()
    }
    /** Lock this state for writing. */
    def lockWrite[A](f: ThreadUnsafeState ⇒ A): A = {
      rwl.writeLock().lock()
      try f(this) finally rwl.writeLock().unlock()
    }
    /** Lock this state for updating field content. */
    def lockUpdate[A](f: ThreadUnsafeStateReadOnly ⇒ A): A = lockWrite(f)
  }
  /**
   * Graph marker thread unsafe read only object.
   */
  trait ThreadUnsafeStateReadOnly extends ThreadSafeState {
    this: ThreadUnsafeState ⇒
    /** Get graph. */
    def graph: Graph[_ <: Model.Like]
    /** Get graph descriptor properties. */
    def graphDescriptorProperties: Option[Properties]
    /** Get payload. */
    def payload: Payload
    /** Get container IResource properties. */
    def resourceProperties: Option[Properties]
  }
  /**
   * Graph marker thread unsafe object.
   */
  class ThreadUnsafeState extends ThreadUnsafeStateReadOnly {
    /** Map of marker contexts binded with this graph. */
    val contextRefs = new mutable.WeakHashMap[Context, Unit] with mutable.SynchronizedMap[Context, Unit]
    /** Graph. */
    var graphObject = Option.empty[Graph[_ <: Model.Like]]
    /** Graph descriptor properties. */
    var graphDescriptorProperties = Option.empty[Properties]
    /** Container IResource properties. */
    var resourceProperties = Option.empty[Properties]
    /** Payload. */
    var payloadObject = Option.empty[Payload]

    /** Get graph. */
    def graph: Graph[_ <: Model.Like] = graphObject getOrElse { throw new IllegalStateException("Graph not loaded.") }
    /** Get payload. */
    def payload: Payload = payloadObject getOrElse { throw new IllegalStateException("Payload not initialized.") }
    override def toString() = s"GraphMarker.State($graphObject, $graphDescriptorProperties, $resourceProperties, $payloadObject)"
  }
  /** Read only marker. */
  class ReadOnlyGraphMarker(val uuid: UUID, val graphCreated: Element.Timestamp, val graphModelId: Symbol,
    val graphOrigin: Symbol, val graphPath: File, val graphStored: Element.Timestamp, val markerLastAccessed: Long)
    extends api.GraphMarker {
    /** Autoload property file if suitable information needed. */
    val autoload = false
    /** The validation flag indicating whether the marker is consistent. */
    val markerIsValid: Boolean = false

    /** Assert marker state. */
    def assertState() {}
    /** Load the specific graph from the predefined directory ${location}/id/ */
    def graphAcquire(reload: Boolean = false): Graph[_ <: Model.Like] = throw new UnsupportedOperationException()
    /** Close the loaded graph. */
    def graphClose() = throw new UnsupportedOperationException()
    /** Path to the graph descriptor. */
    def graphDescriptor: File = new File(graphPath.getParentFile(), graphModelId.name + "." + Payload.extensionGraph)
    /** Store the graph to the predefined directory ${location}/id/ */
    def graphFreeze(): Unit = throw new UnsupportedOperationException()
    /** Check whether the graph is modified. */
    def graphIsDirty(): Boolean = false
    /** Check whether the graph is loaded. */
    def graphIsOpen(): Boolean = false
    /** Load type schemas from local storage. */
    def loadTypeSchemas(): immutable.HashSet[payloadapi.TypeSchema] = throw new UnsupportedOperationException()
    /**
     * Lock this marker for reading.
     */
    def lockRead[A](f: GraphMarker.ThreadUnsafeStateReadOnly ⇒ A): A = throw new UnsupportedOperationException()
    /**
     * Lock marker state for updating.
     */
    def lockUpdate[A](f: GraphMarker.ThreadUnsafeStateReadOnly ⇒ A): A = throw new UnsupportedOperationException()
    /** Load marker properties. */
    def markerLoad() = throw new UnsupportedOperationException()
    /** Save marker properties. */
    def markerSave() = throw new UnsupportedOperationException()
    /** Save type schemas to the local storage. */
    def saveTypeSchemas(schemas: immutable.Set[payloadapi.TypeSchema]) = throw new UnsupportedOperationException()

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

    override def toString() = s"ROGraphMarker[${uuid}]"
  }
}
