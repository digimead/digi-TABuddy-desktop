/**
 * This file is part of the TA Buddy project.
 * Copyright (c) 2012-2014 Alexey Aksenov ezh@ezh.msk.ru
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

package org.digimead.tabuddy.desktop.core.ui.operation

import akka.pattern.ask
import java.util.UUID
import java.util.concurrent.{ CancellationException, ExecutionException }
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.XDependencyInjection
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.definition.Operation
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.ui.UI
import org.digimead.tabuddy.desktop.core.ui.definition.widget.VComposite
import org.digimead.tabuddy.desktop.core.ui.operation.api.XOperationViewClose
import org.eclipse.core.runtime.{ IAdaptable, IProgressMonitor }
import scala.concurrent.Await

/** 'Close view' operation. */
class OperationViewClose extends XOperationViewClose with XLoggable {
  /** Akka execution context. */
  implicit lazy val ec = App.system.dispatcher
  /** Akka communication timeout. */
  implicit val timeout = akka.util.Timeout(UI.communicationTimeout)

  /**
   * Close view.
   *
   * @param vCompositeId view composite id
   */
  def apply(vCompositeId: UUID) {
    log.info("Close VComposite{...}[%08X].".format(vCompositeId.hashCode()))
    val vComposite = UI.viewMap(vCompositeId)
    val future = vComposite.ref ? App.Message.Destroy()
    try Await.result(future, timeout.duration)
    catch {
      case e: Throwable ⇒
        throw new ExecutionException(s"Unable to close VComposite{...}[%08X]: %s".format(vCompositeId.hashCode(), e.getMessage()), e)
    }
  }
  /**
   * Create 'Close view' operation.
   *
   * @param vCompositeId view composite id
   * @return 'Close view' operation
   */
  def operation(vCompositeId: UUID) = new Implemetation(vCompositeId)

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

  class Implemetation(vCompositeId: UUID) extends OperationViewClose.Abstract(vCompositeId) with XLoggable {
    @volatile protected var allowExecute = true

    override def canExecute() = allowExecute
    override def canRedo() = false
    override def canUndo() = false

    protected def execute(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[Unit] =
      try Operation.Result.OK(Option(OperationViewClose.this(vCompositeId)))
      catch { case e: CancellationException ⇒ Operation.Result.Cancel() }
    protected def redo(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[Unit] =
      throw new UnsupportedOperationException
    protected def undo(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[Unit] =
      throw new UnsupportedOperationException
  }
}

object OperationViewClose extends XLoggable {
  /** Stable identifier with OperationViewClose DI */
  lazy val operation = DI.operation.asInstanceOf[OperationViewClose]

  /**
   *  Build a new 'Close view' operation.
   *
   * @param vCompositeId view composite id
   * @return 'Close view' operation
   */
  @log
  def apply(vCompositeId: UUID): Option[Abstract] = Some(operation.operation(vCompositeId))

  /** Bridge between abstract XOperation[Unit] and concrete Operation[Unit] */
  abstract class Abstract(vCompositeId: UUID)
    extends Operation[Unit]("Close VComposite{...}[%08X].".format(vCompositeId.hashCode())) {
    this: XLoggable ⇒
  }
  /**
   * Dependency injection routines.
   */
  private object DI extends XDependencyInjection.PersistentInjectable {
    lazy val operation = injectOptional[XOperationViewClose] getOrElse new OperationViewClose
  }
}
