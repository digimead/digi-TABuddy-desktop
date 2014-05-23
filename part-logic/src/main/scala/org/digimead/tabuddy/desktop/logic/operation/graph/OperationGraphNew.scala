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

package org.digimead.tabuddy.desktop.logic.operation.graph

import java.io.File
import java.util.UUID
import java.util.concurrent.{ CancellationException, ExecutionException }
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.definition.Operation
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.ui.{ UI, Wizards }
import org.digimead.tabuddy.desktop.logic.Logic
import org.digimead.tabuddy.desktop.logic.payload.Payload
import org.digimead.tabuddy.desktop.logic.payload.marker.GraphMarker
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.element.Element
import org.digimead.tabuddy.model.graph.Graph
import org.eclipse.core.runtime.{ IAdaptable, IProgressMonitor }

/** 'New graph' operation. */
class OperationGraphNew extends api.OperationGraphNew with Loggable {
  /**
   * Create new graph.
   *
   * @param name initial model name if any
   * @param location initial graph location if any
   * @param interactive show graph creation wizard
   * @return graph marker
   */
  def apply(name: Option[String], location: Option[File], interactive: Boolean): Graph[_ <: Model.Like] = {
    log.info(s"Create new graph with initial name ${name}.")
    if (interactive && !App.isUIAvailable)
      throw new IllegalArgumentException("Unable to create graph interactively without UI.")
    if (!interactive && name.isEmpty)
      throw new IllegalArgumentException("Unable to non interactively create new graph without name.")
    if (!interactive && location.isEmpty)
      throw new IllegalArgumentException("Unable to non interactively create new graph without location.")
    if (!Logic.container.isOpen())
      throw new IllegalStateException("Workspace is not available.")
    val marker = if (interactive)
      UI.getActiveShell match {
        case Some(shell) ⇒
          try App.execNGet {
            Wizards.open("org.digimead.tabuddy.desktop.logic.ui.wizard.WizardGraphNew", shell, Some(name, location)) match {
              case marker: GraphMarker ⇒
                if (!marker.markerIsValid)
                  throw new IllegalStateException(marker + " is not valid.")
                marker
              case other if other == org.eclipse.jface.window.Window.CANCEL ⇒
                throw new CancellationException("Unable to create new graph. Operation canceled.")
              case other ⇒
                throw new IllegalStateException(s"Unable to create new graph. Result ${other}.")
            }
          }(App.LongRunnable)
          catch { case e: ExecutionException if e.getCause() != null ⇒ throw e.getCause() }
        case None ⇒
          throw new IllegalStateException("Unable to create new graph dialog without parent shell.")
      }
    else {
      val marker = GraphMarker.createInTheWorkspace(UUID.randomUUID(), new File(location.get, name.get),
        Element.timestamp(), Payload.origin)
      if (!marker.markerIsValid)
        throw new IllegalStateException(marker + " is not valid.")
      marker
    }
    marker.graphAcquire()
    marker.safeRead(_.graph)
  }
  /**
   * Create 'New graph' operation.
   *
   * @param name initial model name if any
   * @param location initial graph location if any
   * @param interactive show graph creation wizard
   * @return 'New graph' operation
   */
  def operation(name: Option[String], location: Option[File], interactive: Boolean) =
    new Implemetation(name, location, interactive)

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

  class Implemetation(name: Option[String], location: Option[File], interactive: Boolean)
    extends OperationGraphNew.Abstract(name, location, interactive) with Loggable {
    @volatile protected var allowExecute = true

    override def canExecute() = allowExecute
    override def canRedo() = false
    override def canUndo() = false

    protected def execute(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[Graph[_ <: Model.Like]] = {
      require(canExecute, "Execution is disabled.")
      try {
        val result = Option[Graph[_ <: Model.Like]](OperationGraphNew.this(name, location, interactive))
        allowExecute = false
        Operation.Result.OK(result)
      } catch {
        case e: CancellationException ⇒ Operation.Result.Cancel()
        case e: RuntimeException ⇒ Operation.Result.Error(e.getMessage(), e)
        case e: Throwable ⇒ Operation.Result.Error(s"Unable to create new graph.", e)
      }
    }
    protected def redo(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[Graph[_ <: Model.Like]] =
      throw new UnsupportedOperationException
    protected def undo(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[Graph[_ <: Model.Like]] =
      throw new UnsupportedOperationException
  }
}

object OperationGraphNew extends Loggable {
  /** Stable identifier with OperationGraphNew DI */
  lazy val operation = DI.operation.asInstanceOf[OperationGraphNew]

  /**
   * Build a new 'New graph' operation.
   *
   * @param name initial model name if any
   * @param location initial graph location if any
   * @param interactive show graph creation wizard
   * @return 'New graph' operation
   */
  @log
  def apply(name: Option[String], location: Option[File], interactive: Boolean): Option[Abstract] =
    Some(operation.operation(name, location, interactive))

  /** Bridge between abstract api.Operation[Graph[_ <: Model.Like]] and concrete Operation[Graph[_ <: Model.Like]] */
  abstract class Abstract(val name: Option[String], val location: Option[File], val interactive: Boolean)
    extends Operation[Graph[_ <: Model.Like]](s"Create new graph with initial name ${name}.") {
    this: Loggable ⇒
  }
  /**
   * Dependency injection routines.
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    lazy val operation = injectOptional[api.OperationGraphNew] getOrElse new OperationGraphNew
  }
}
