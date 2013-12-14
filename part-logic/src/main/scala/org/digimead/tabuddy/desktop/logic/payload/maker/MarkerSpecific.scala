package org.digimead.tabuddy.desktop.logic.payload.maker

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream, File, FileInputStream, FileOutputStream }
import java.util.Properties
import org.digimead.tabuddy.desktop.core.Report
import org.digimead.tabuddy.desktop.core.Report.report2implementation
import org.digimead.tabuddy.desktop.logic.payload.Payload
import org.eclipse.core.resources.IResource

/**
 * Part of the graph marker that contains marker specific logic.
 */
trait MarkerSpecific {
  this: GraphMarker ⇒
  /** The validation flag indicating whether the marker is consistent. */
  def markerIsValid: Boolean = state.lockRead { state ⇒
    try {
      val base = graphPath.getParentFile()
      val id = graphPath.getName
      val descriptor = new File(base, id + "." + Payload.extensionGraph)
      graphPath.exists() && descriptor.exists && resource.exists() && {
        if (autoload && state.graphDescriptorProperties.isEmpty)
          markerLoad()
        true
      }
    } catch {
      case e: Throwable ⇒
        false
    }
  }
  /** Marker last access timestamp. */
  def markerLastAccessed: Long = getValueFromIResourceProperties { p ⇒ p.getProperty(GraphMarker.fieldLastAccessed).toLong }
  /** Load marker properties. */
  def markerLoad() = state.lockWrite { state ⇒
    log.debug(s"Load marker with UUID ${uuid}.")

    // load IResource part
    log.debug(s"Load resource: ${resource.getName}.")
    if (!resource.exists())
      throw new IllegalArgumentException(s"Graph marker with id ${uuid} not found.")
    val stream = resource.getContents(true)
    val resourceProperties = new Properties
    resourceProperties.load(stream)
    try { stream.close() } catch { case e: Throwable ⇒ }
    state.resourceProperties = Option(resourceProperties)

    // load model descriptor part
    val fullPath = new File(resourceProperties.getProperty(GraphMarker.fieldPath))
    val path = fullPath.getParentFile()
    val id = fullPath.getName()
    val descriptor = new File(path, id + "." + Payload.extensionGraph)
    log.debug(s"Load model descriptor: ${descriptor.getName}.")
    val properties = new Properties
    properties.load(new FileInputStream(descriptor))
    state.graphDescriptorProperties = Option(properties)
  }
  /** Save marker properties. */
  def markerSave() = state.lockWrite { state ⇒
    log.debug(s"Save marker ${this}.")
    require(state, true, false)

    // update fields
    state.resourceProperties.get.setProperty(GraphMarker.fieldLastAccessed, System.currentTimeMillis().toString)

    // save model descriptor part
    graphPath.mkdirs()
    log.debug(s"Save graph descriptor: ${graphDescriptor.getName}.")
    Report.info match {
      case Some(info) ⇒ state.graphDescriptorProperties.get.store(new FileOutputStream(graphDescriptor), info.toString)
      case None ⇒ state.graphDescriptorProperties.get.store(new FileOutputStream(graphDescriptor), null)
    }

    // save IResource part
    log.debug(s"Save resource: ${resource.getName()}.")
    val output = new ByteArrayOutputStream()
    state.resourceProperties.get.store(output, null)
    val input = new ByteArrayInputStream(output.toByteArray())
    if (!resource.exists())
      resource.create(input, IResource.NONE, null)
    else
      resource.setContents(input, true, false, null)
  }
}
