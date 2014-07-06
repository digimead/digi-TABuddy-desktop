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

package org.digimead.tabuddy.desktop.logic.operation.graph

import java.io.{ File, InputStream }
import java.net.URI
import java.util.UUID
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.XDependencyInjection
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.definition.Operation
import org.digimead.tabuddy.desktop.logic.Logic
import org.digimead.tabuddy.desktop.logic.operation.graph.api.XOperationGraphImport
import org.digimead.tabuddy.desktop.logic.payload.marker.GraphMarker
import org.digimead.tabuddy.desktop.logic.payload.marker.api.XEncryption
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.element.Element
import org.digimead.tabuddy.model.graph.Graph
import org.digimead.tabuddy.model.serialization.digest.Digest
import org.digimead.tabuddy.model.serialization.signature.Signature
import org.digimead.tabuddy.model.serialization.transport.Transport
import org.digimead.tabuddy.model.serialization.{ SData, Serialization }
import org.eclipse.core.runtime.{ IAdaptable, IProgressMonitor }

/** 'Import graph' operation. */
class OperationGraphImport extends XOperationGraphImport with XLoggable {
  /**
   * Import graph.
   *
   * @param source location of the imported graph
   * @param name model name
   * @param target graph location
   * @param containerEncParameters container encription parameters
   * @param contentEncParameters content encription parameters
   * @param dParameters digest parameters
   * @param sParameters signature parameters
   * @return imported graph
   */
  def apply(source: URI, name: String, target: File,
    containerEncParameters: Option[XEncryption.Parameters],
    contentEncParameters: Option[XEncryption.Parameters],
    dParameters: Option[Boolean], sParameters: Option[UUID]): Graph[_ <: Model.Like] = {
    log.info(s"Import graph from ${source}.")
    if (!Logic.container.isOpen())
      throw new IllegalStateException("Workspace is not available.")

    val sourceURI = Serialization.normalizeURI(source)
    val sData = SData(SData.Key.force -> true)
    // Digest
    val sDataNDigest = dParameters match {
      case Some(parameters) ⇒
        sData.updated(Digest.Key.acquire, parameters)
      case None ⇒
        sData
    }
    // Signature
    val sDataNSignature = sParameters match {
      case Some(parameters) ⇒
        // TODO Signature.acceptSigned replace with sParameters: Option[UUID]
        sDataNDigest.updated(Signature.Key.acquire, Signature.acceptSigned)
      case None ⇒
        sDataNDigest
    }
    // Container encryption
    val sDataNContainerEncryption = containerEncParameters match {
      case Some(parameters) ⇒
        sDataNSignature.updated(SData.Key.convertURI,
          // encode
          ((name: String, sData: SData) ⇒
            parameters.encryption.toString(parameters.encryption.encrypt(name.getBytes(io.Codec.UTF8.charSet), parameters)),
            // decode
            (name: String, sData: SData) ⇒
              new String(parameters.encryption.decrypt(parameters.encryption.fromString(name), parameters), io.Codec.UTF8.charSet)))
      case None ⇒
        sDataNSignature
    }
    // Content encryption
    val sDataNContentEncryption = contentEncParameters match {
      case Some(parameters) ⇒
        sDataNContainerEncryption.updated(SData.Key.readFilter, ((is: InputStream, uri: URI, transport: Transport, sData: SData) ⇒
          parameters.encryption.decrypt(is, parameters)))
      case None ⇒
        sDataNContainerEncryption
    }

    Option[Graph[_ <: Model.Like]](Serialization.acquire(sourceURI, sDataNContentEncryption)) match {
      case Some(graph) ⇒
        val graphPath = new File(target, name)
        val uuid = if (GraphMarker.list().contains(graph.node.unique))
          UUID.randomUUID()
        else
          graph.node.unique
        val newMarker = GraphMarker.createInTheWorkspace(uuid, graphPath, Element.timestamp(), graph.origin, graph.model.eBox.serialization)
        newMarker.safeUpdate(_.safeWrite(_.graphObject = Some(graph.copy() { g ⇒
          g.withData { data ⇒
            data(GraphMarker) = newMarker
          }
        })))
        newMarker.graphAcquire()
        val imported = newMarker.safeRead(_.graph)
        OperationGraphSave.operation(imported, true)
        imported
      case None ⇒
        throw new IllegalStateException(s"Unable to import graph from " + sourceURI)
    }
  }
  /**
   * Create 'Import graph' operation.
   *
   * @param source location of the imported graph
   * @param name model name
   * @param target graph location
   * @param containerEncParameters container encription parameters
   * @param contentEncParameters content encription parameters
   * @param dParameters digest parameters
   * @param sParameters signature parameters
   * @return 'Import graph' operation
   */
  def operation(source: URI, name: String, target: File,
    containerEncParameters: Option[XEncryption.Parameters],
    contentEncParameters: Option[XEncryption.Parameters],
    dParameters: Option[Boolean], sParameters: Option[UUID]) =
    new Implemetation(source, name, target, containerEncParameters, contentEncParameters, dParameters, sParameters)

  /**
   * Checks that this class can be subclassed.
   * <p>
   * The API class is intended to be subclassed only at specific,
   * controlled point. This method enforces this rule
   * unless it is overridden.
   * </p><p>
   * <em>IMPORTANT:</em> By providing an implementation of this
   * method that allows a subclass of a class which does not
   * normally allow subclassing to be created, the implementer
   * agrees to be fully responsible for the fact that any such
   * subclass will likely fail.
   * </p>
   */
  override protected def checkSubclass() {}

  class Implemetation(source: URI, name: String, target: File,
    containerEncParameters: Option[XEncryption.Parameters],
    contentEncParameters: Option[XEncryption.Parameters],
    dParameters: Option[Boolean], sParameters: Option[UUID])
    extends OperationGraphImport.Abstract(source, name, target,
      containerEncParameters, contentEncParameters, dParameters, sParameters) with XLoggable {
    @volatile protected var allowExecute = true

    override def canExecute() = allowExecute
    override def canRedo() = false
    override def canUndo() = false

    protected def execute(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[Graph[_ <: Model.Like]] = {
      require(canExecute, "Execution is disabled.")
      try {
        val result = Option[Graph[_ <: Model.Like]](OperationGraphImport.this(source, name, target,
          containerEncParameters, contentEncParameters, dParameters, sParameters))
        allowExecute = false
        Operation.Result.OK(result)
      } catch {
        case e: Throwable ⇒
          Operation.Result.Error(s"Unable to import graph: " + e.getMessage(), e)
      }
    }
    protected def redo(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[Graph[_ <: Model.Like]] =
      throw new UnsupportedOperationException
    protected def undo(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[Graph[_ <: Model.Like]] =
      throw new UnsupportedOperationException
  }
}

object OperationGraphImport extends XLoggable {
  /** Stable identifier with OperationGraphImport DI */
  lazy val operation = DI.operation.asInstanceOf[OperationGraphImport]

  /**
   * Build a new 'Import graph' operation.
   *
   * @param source location of the imported graph
   * @param name model name
   * @param target graph location
   * @param containerEncParameters container encription parameters
   * @param contentEncParameters content encription parameters
   * @param dParameters digest parameters
   * @param sParameters signature parameters
   * @return 'Import graph' operation
   */
  @log
  def apply(source: URI, name: String, target: File,
    containerEncParameters: Option[XEncryption.Parameters],
    contentEncParameters: Option[XEncryption.Parameters],
    dParameters: Option[Boolean], sParameters: Option[UUID]): Option[Abstract] =
    Some(operation.operation(source, name, target, containerEncParameters, contentEncParameters, dParameters, sParameters))

  /** Bridge between abstract XOperation[Graph[_ <: Model.Like]] and concrete Operation[Graph[_ <: Model.Like]] */
  abstract class Abstract(val source: URI, val name: String, val target: File,
    val containerEncParameters: Option[XEncryption.Parameters],
    val contentEncParameters: Option[XEncryption.Parameters],
    val dParameters: Option[Boolean], val sParameters: Option[UUID])
    extends Operation[Graph[_ <: Model.Like]](s"Import graph from ${source} as ${name}.") {
    this: XLoggable ⇒
  }
  /**
   * Dependency injection routines.
   */
  private object DI extends XDependencyInjection.PersistentInjectable {
    lazy val operation = injectOptional[XOperationGraphImport] getOrElse new OperationGraphImport
  }
}
