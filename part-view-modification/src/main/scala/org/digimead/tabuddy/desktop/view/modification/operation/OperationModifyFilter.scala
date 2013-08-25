/**
 * This file is part of the TABuddy project.
 * Copyright (c) 2013 Alexey Aksenov ezh@ezh.msk.ru
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

package org.digimead.tabuddy.desktop.view.modification.operation

import java.util.concurrent.CancellationException
import java.util.concurrent.Exchanger

import scala.reflect.runtime.universe

import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.Core
import org.digimead.tabuddy.desktop.definition.Operation
import org.digimead.tabuddy.desktop.gui.GUI
import org.digimead.tabuddy.desktop.logic
import org.digimead.tabuddy.desktop.logic.operation.view.api
import org.digimead.tabuddy.desktop.logic.payload.view.api.Filter
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.App.app2implementation
import org.digimead.tabuddy.desktop.view.modification.dialog.filtered.FilterEditor
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.Model.model2implementation
import org.eclipse.core.runtime.IAdaptable
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.swt.widgets.Shell

/** 'Modify filter' operation. */
class OperationModifyFilter extends logic.operation.view.OperationModifyFilter with Loggable {
  /**
   * Modify filter.
   *
   * @param filter the initial filter
   * @param filterList the list of exists filters
   * @param modelId current model Id
   * @return the modified/the same filter
   */
  def apply(filter: Filter, filterList: Set[Filter], modelId: Symbol): Filter = {
    log.info(s"Modify filter ${filter} for  model ${modelId}.")
    App.assertUIThread(false)
    if (Model.eId != modelId)
      throw new IllegalStateException(s"Unable to modify filter ${filter}. Unexpected model ${Model.eId} is loaded.")
    val exchanger = new Exchanger[Filter]()
    App.getActiveShell() match {
      case Some(shell) =>
        App.exec {
          val dialog = new FilterEditor(shell, filter, filterList.toList)
          dialog.openOrFocus {
            case result if result == org.eclipse.jface.window.Window.OK =>
              exchanger.exchange(dialog.getModifiedFilter())
            case result =>
              exchanger.exchange(null)
          }
        }
      case None =>
        throw new IllegalStateException("Unable to create 'modify filter' dialog without parent shell.")
    }
    Option(exchanger.exchange(null)) getOrElse { throw new CancellationException }
  }
  /**
   * Create 'Modify filter' operation.
   *
   * @param filter the initial filter
   * @param filterList the list of exists filters
   * @param modelId current model Id
   * @return 'Modify filter' operation
   */
  def operation(filter: Filter, filterList: Set[Filter], modelId: Symbol) =
    new Implemetation(filter, filterList, modelId)

  class Implemetation(filter: Filter, filterList: Set[Filter], modelId: Symbol)
    extends logic.operation.view.OperationModifyFilter.Abstract(filter, filterList, modelId) with Loggable {
    @volatile protected var allowExecute = true

    override def canExecute() = allowExecute
    override def canRedo() = false
    override def canUndo() = false

    protected def execute(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[Filter] = {
      try {
        Operation.Result.OK(Option(OperationModifyFilter.this(filter, filterList, modelId)))
      } catch {
        case e: CancellationException =>
          Operation.Result.Cancel()
      }
    }
    protected def redo(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[Filter] =
      throw new UnsupportedOperationException
    protected def undo(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[Filter] =
      throw new UnsupportedOperationException
  }
}
