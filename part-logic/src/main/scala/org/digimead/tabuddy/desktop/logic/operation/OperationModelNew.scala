/**
 * This file is part of the TABuddy project.
 * Copyright (c) 2012-2013 Alexey Aksenov ezh@ezh.msk.ru
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

package org.digimead.tabuddy.desktop.logic.operation

import java.io.File
import java.util.concurrent.CancellationException

import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.Core
import org.digimead.tabuddy.desktop.core.Wizards
import org.digimead.tabuddy.desktop.core.Wizards.registry2implementation
import org.digimead.tabuddy.desktop.definition.Operation
import org.digimead.tabuddy.desktop.gui.GUI
import org.digimead.tabuddy.desktop.logic
import org.digimead.tabuddy.desktop.logic.api.ModelMarker
import org.digimead.tabuddy.desktop.logic.payload.Payload
import org.digimead.tabuddy.desktop.logic.payload.Payload.payload2implementation
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.App.app2implementation
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.Model.model2implementation
import org.eclipse.core.runtime.IAdaptable
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.swt.widgets.Shell

/** 'New model' operation. */
class OperationModelNew extends api.OperationModelNew with Loggable {
  /**
   * Create new model.
   *
   * @param name initial model name if any
   * @param location initial model location if any
   * @param interactive show model creation wizard
   * @return created model marker
   */
  def apply(name: Option[String], location: Option[File], interactive: Boolean): logic.api.ModelMarker = ModelLock.synchronized {
    log.info(s"Create new model with initial name ${name}.")
    if (Model.eId != Payload.defaultModel.eId)
      throw new IllegalStateException(s"Unable to create new model. Another model ${Model.eId} is loaded.")
    if (!interactive && name.isEmpty)
      throw new IllegalArgumentException("Unable to create non interactive new model without name.")
    if (!interactive && location.isEmpty)
      throw new IllegalArgumentException("Unable to create non interactive new model without location.")
    if (interactive)
      App.getActiveShell match {
        case Some(shell) =>
          App.execNGet {
            Wizards.open("org.digimead.tabuddy.desktop.model.editor.wizard.ModelCreationWizard", shell, Some(name, location)) match {
              case marker: ModelMarker =>
                marker
              case other if other == org.eclipse.jface.window.Window.CANCEL =>
                throw new CancellationException("Unable to create new model. Operation canceled.")
              case other =>
                throw new IllegalStateException(s"Unable to create new model. Result ${other}.")
            }
          }
        case None =>
          throw new IllegalStateException("Unable to create interacive new model without parent shell.")
      }
    else {
      val marker = Payload.createModel(new File(location.get, name.get))
      Payload.acquireModel(marker)
      marker
    }
  }
  /**
   * Create 'New model' operation.
   *
   * @param name initial model name if any
   * @param location initial model location if any
   * @param interactive show model creation wizard
   * @return 'New model' operation
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
    extends OperationModelNew.Abstract(name, location, interactive) with Loggable {
    @volatile protected var allowExecute = true

    override def canExecute() = allowExecute
    override def canRedo() = false
    override def canUndo() = false

    protected def execute(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[logic.api.ModelMarker] = ModelLock.synchronized {
      try {
        Operation.Result.OK(Option(OperationModelNew.this(name, location, interactive)))
      } catch {
        case e: CancellationException =>
          Operation.Result.Cancel()
      }
    }
    protected def redo(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[logic.api.ModelMarker] =
      throw new UnsupportedOperationException
    protected def undo(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[logic.api.ModelMarker] =
      throw new UnsupportedOperationException
  }
}

object OperationModelNew extends Loggable {
  /** Stable identifier with OperationModelNew DI */
  lazy val operation = DI.operation.asInstanceOf[OperationModelNew]

  /** Build a new 'New model' operation */
  @log
  def apply(name: Option[String], location: Option[File], interactive: Boolean): Option[Abstract] =
    Some(operation.operation(name, location, interactive))

  /** Bridge between abstract api.Operation[logic.api.ModelMarker] and concrete Operation[logic.api.ModelMarker] */
  abstract class Abstract(val name: Option[String], val location: Option[File], val interactive: Boolean)
    extends Operation[logic.api.ModelMarker](s"Create new model with initial name ${name}.") {
    this: Loggable =>
  }
  /**
   * Dependency injection routines.
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    lazy val operation = injectOptional[api.OperationModelNew] getOrElse new OperationModelNew
  }
}
