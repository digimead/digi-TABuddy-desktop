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

import java.net.URI
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.definition.Operation
import org.digimead.tabuddy.desktop.logic.operation.graph.api.XOperationGraphInfo
import org.digimead.tabuddy.desktop.logic.payload.marker.api.XEncryption
import org.digimead.tabuddy.model.serialization.Serialization
import org.eclipse.core.runtime.{ IAdaptable, IProgressMonitor }

/**
 * 'Get information about graph' operation.
 */
class OperationGraphInfo extends XOperationGraphInfo with Loggable {
  /**
   * Get information about graph.
   *
   * @param location source with imported graph
   * @param containerEncParameters container encription parameters
   * @param contentEncParameters content encription parameters
   * @return information about graph
   */
  def apply(location: URI,
    containerEncParameters: Option[XEncryption.Parameters],
    contentEncParameters: Option[XEncryption.Parameters]): XOperationGraphInfo.Info = {
    //    log.info(pgpPublicKey match {
    //      case Some(key) ⇒ s"Get information about private graph with ${origin} from ${location}."
    //      case None ⇒ s"Get information about public graph with ${origin} from ${location}."
    //    })
    //    if (!Logic.container.isOpen())
    //      throw new IllegalStateException("Workspace is not available.")
    // получить дескриптор
    // выцепить overview
    // вернуть инфо
    Serialization.perScheme.get(location.getScheme()) match {
      case Some(transport) ⇒
      //        pgpPublicKey match {
      //          case Some(key) ⇒ transport.setPGPKey(key)
      //          case None ⇒ transport.resetPGPKey()
      //        }
      case None ⇒
        throw new IllegalArgumentException(s"Unable to load graph from URI with unknown scheme ${location.getScheme}.")
    }
    val loader = Serialization.acquireLoader(location)
    //loader.info()
    null
  }
  /**
   * Create 'Graph info' operation.
   *
   * @param location source with imported graph
   * @param containerEncParameters container encription parameters
   * @param contentEncParameters content encription parameters
   * @return 'Graph info' operation
   */
  def operation(location: URI,
    containerEncParameters: Option[XEncryption.Parameters],
    contentEncParameters: Option[XEncryption.Parameters]) =
    new Implemetation(location, containerEncParameters, contentEncParameters)

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

  class Implemetation(location: URI,
    containerEncParameters: Option[XEncryption.Parameters],
    contentEncParameters: Option[XEncryption.Parameters])
    extends OperationGraphInfo.Abstract(location, containerEncParameters, contentEncParameters) with Loggable {
    @volatile protected var allowExecute = true

    override def canExecute() = allowExecute
    override def canRedo() = false
    override def canUndo() = false

    protected def execute(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[XOperationGraphInfo.Info] = {
      require(canExecute, "Execution is disabled.")
      try {
        val result = Option[XOperationGraphInfo.Info](OperationGraphInfo.this(location, containerEncParameters, contentEncParameters))
        allowExecute = false
        Operation.Result.OK(result)
      } catch {
        case e: Throwable ⇒
          Operation.Result.Error(s"Unable to import graph.", e)
      }
    }
    protected def redo(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[XOperationGraphInfo.Info] =
      throw new UnsupportedOperationException
    protected def undo(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[XOperationGraphInfo.Info] =
      throw new UnsupportedOperationException
  }
}

object OperationGraphInfo extends Loggable {
  /** Stable identifier with OperationGraphInfo DI */
  lazy val operation = DI.operation.asInstanceOf[OperationGraphInfo]

  /**
   * Build a new 'Graph info' operation.
   *
   * @param location source with imported graph
   * @param containerEncParameters container encription parameters
   * @param contentEncParameters content encription parameters
   * @return 'Graph info' operation
   */
  @log
  def apply(location: URI,
    containerEncParameters: Option[XEncryption.Parameters],
    contentEncParameters: Option[XEncryption.Parameters]): Option[Abstract] =
    Some(operation.operation(location, containerEncParameters, contentEncParameters))

  /** Bridge between abstract XOperation[OperationGraphInfo.Info] and concrete Operation[OperationGraphInfo.Info] */
  abstract class Abstract(val location: URI,
    val containerEncParameters: Option[XEncryption.Parameters],
    val contentEncParameters: Option[XEncryption.Parameters])
    extends Operation[XOperationGraphInfo.Info](s"Get information about graph from ${location}.") {
    this: Loggable ⇒
  }
  /**
   * Dependency injection routines.
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    lazy val operation = injectOptional[XOperationGraphInfo] getOrElse new OperationGraphInfo
  }
}
