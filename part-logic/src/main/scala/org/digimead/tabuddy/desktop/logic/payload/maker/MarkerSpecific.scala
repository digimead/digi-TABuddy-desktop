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

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream, File, FileInputStream, FileOutputStream }
import java.util.Properties
import org.digimead.tabuddy.desktop.core.Report
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
      assertState()
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
    assertState()
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
    assertState()
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
