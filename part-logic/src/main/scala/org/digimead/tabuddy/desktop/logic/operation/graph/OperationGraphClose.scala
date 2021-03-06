/**
 * This file is part of the TA Buddy project.
 * Copyright (c) 2013-2015 Alexey Aksenov ezh@ezh.msk.ru
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

import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.XDependencyInjection
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.definition.Operation
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.ui.UI
import org.digimead.tabuddy.desktop.logic.operation.graph.api.XOperationGraphClose
import org.digimead.tabuddy.desktop.logic.payload.marker.GraphMarker
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.graph.Graph
import org.eclipse.core.runtime.{ IAdaptable, IProgressMonitor }
import org.eclipse.swt.SWT
import org.eclipse.swt.widgets.MessageBox

/**
 * 'Close graph' operation.
 */
class OperationGraphClose extends XOperationGraphClose[GraphMarker.Generic] with XLoggable {
  /**
   * Close graph.
   *
   * @param graph graph to close
   * @param force close graph without saving
   * @return the same marker or read only marker if current one is deleted
   */
  def apply(graph: Graph[_ <: Model.Like], force: Boolean): GraphMarker.Generic = {
    @volatile var closeApproved = force
    val marker = GraphMarker(graph)
    while (true) {
      GraphMarker.globalRWL.writeLock().lock()
      val shell = try {
        marker.safeUpdate { state ⇒
          log.info(s"Close $graph, force is $force")
          if (!marker.graphIsOpen())
            throw new IllegalStateException(s"$graph is already closed")
          val shell = if (marker.graphIsDirty() && !closeApproved && App.isUIAvailable) {
            UI.getActiveShell match {
              case Some(shell) ⇒
                Some(shell)
              case None ⇒
                log.warn("There is no active shell")
                log.info(s"Close modified $graph without saving")
                None
            }
          } else {
            None
          }
          if (shell.isEmpty) {
            // Close graph
            if (marker.graphStored == GraphMarker.TimestampNil) {
              // There is no a local copy.
              // Newly created graph is unsaved
              // Remove current marker.
              marker.graphClose()
              log.info(s"$graph is closed. Delete unused marker")
              return GraphMarker.deleteFromWorkspace(marker)
            } else {
              // Returns the same marker
              marker.graphClose()
              log.info(s"$graph is closed")
              return marker
            }
          }
          shell
        }
      } finally GraphMarker.globalRWL.writeLock().unlock()
      shell.foreach { shell ⇒
        closeApproved = App.execNGet {
          val style = SWT.ICON_QUESTION | SWT.YES | SWT.NO
          val dialog = new MessageBox(shell, style)
          dialog.setText(s"Graph '${marker.graphModelId.name}' is modified")
          dialog.setMessage("Do you want to save modifications?")
          if (dialog.open() == SWT.YES) {
            // save modified model if user permits
            OperationGraphSave.operation(graph, false)
            false // marker.graphIsDirty() should be false
          } else {
            log.info(s"Close modified $graph without saving")
            true
          }
        }(App.LongRunnable)
      }
    }
    throw new IllegalStateException("This code is unreachable")
  }
  /**
   * Create 'Close graph' operation.
   *
   * @param graph graph to close
   * @param force close graph without saving
   * @return 'Close graph' operation
   */
  def operation(graph: Graph[_ <: Model.Like], force: Boolean) =
    new Implemetation(graph, force: Boolean)

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

  class Implemetation(graph: Graph[_ <: Model.Like], force: Boolean)
    extends OperationGraphClose.Abstract(graph, force) with XLoggable {
    @volatile protected var allowExecute = true

    override def canExecute() = allowExecute
    override def canRedo() = false
    override def canUndo() = false

    protected def execute(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[GraphMarker.Generic] = {
      require(canExecute, "Execution is disabled")
      try {
        val result = Option(OperationGraphClose.this(graph, force))
        allowExecute = false
        Operation.Result.OK(result)
      } catch {
        case e: Throwable ⇒
          Operation.Result.Error(s"Unable to close $graph: " + e.getMessage(), e)
      }
    }
    protected def redo(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[GraphMarker.Generic] =
      throw new UnsupportedOperationException
    protected def undo(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[GraphMarker.Generic] =
      throw new UnsupportedOperationException
  }
}

object OperationGraphClose extends XLoggable {
  /** Stable identifier with OperationGraphClose DI */
  lazy val operation = DI.operation.asInstanceOf[OperationGraphClose]

  /**
   * Build a new 'Close graph' operation.
   *
   * @param graph graph to close
   * @param force close graph without saving
   * @return 'Close graph' operation
   */
  @log
  def apply(graph: Graph[_ <: Model.Like], force: Boolean): Option[Abstract] =
    Some(operation.operation(graph, force))

  /** Bridge between abstract XOperation[Unit] and concrete Operation[Unit] */
  abstract class Abstract(val graph: Graph[_ <: Model.Like], val force: Boolean)
    extends Operation[GraphMarker.Generic](s"Close $graph") {
    this: XLoggable ⇒
  }
  /**
   * Dependency injection routines.
   */
  private object DI extends XDependencyInjection.PersistentInjectable {
    lazy val operation = injectOptional[XOperationGraphClose[_]] getOrElse new OperationGraphClose
  }
}
