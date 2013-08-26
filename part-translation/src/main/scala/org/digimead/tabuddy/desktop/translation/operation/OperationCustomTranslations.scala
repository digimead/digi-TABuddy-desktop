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

package org.digimead.tabuddy.desktop.translation.operation

import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.api.Translation.NLS
import org.digimead.tabuddy.desktop.definition.Operation
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.App.app2implementation
import org.digimead.tabuddy.desktop.translation.dialog.TranslationLookup
import org.eclipse.core.runtime.IAdaptable
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.swt.widgets.Shell

class OperationCustomTranslations()
  extends org.digimead.tabuddy.desktop.operation.OperationCustomTranslations.Abstract() with Loggable {
  @volatile protected var allowExecute = true
  @volatile protected var allowRedo = false
  @volatile protected var allowUndo = false
  @volatile protected var jobResult: Option[Operation.Result[(String, String, NLS)]] = None

  override def canExecute() = allowExecute
  override def canRedo() = allowRedo
  override def canUndo() = allowUndo

  protected def dialog(shell: Shell): Operation.Result[(String, String, NLS)] = {
    val dialog = new TranslationLookup(shell)
    if (dialog.openOrFocus() == org.eclipse.jface.window.Window.OK)
      Operation.Result.OK(dialog.getSelected.flatMap(translation =>
        org.digimead.tabuddy.desktop.definition.NLS.list.find(_.getClass.getName == translation.getSingleton()) match {
          case Some(singleton) =>
            Some(translation.getKey(), translation.getValue(), singleton)
          case None =>
            log.warn(s"Unable to return selected translation '${translation.getKey()}'. Singleton ${translation.getSingleton()} not found.")
            None
        }))
    else
      Operation.Result.Cancel[(String, String, NLS)]()
  }
  protected def execute(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[(String, String, NLS)] = redo(monitor, info)
  protected def redo(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[(String, String, NLS)] = {
    // process the job
    val result: Operation.Result[(String, String, NLS)] = if (canRedo) {
      jobResult.get
    } else if (canExecute) {
      App.execNGet {
        App.getActiveShell.map(dialog) getOrElse
          { Operation.Result.Error("Unable to find active shell.") }
      }
    } else
      Operation.Result.Error(s"Unable to process $this: redo and execute are prohibited")
    // update the job state
    result match {
      case r @ Operation.Result.OK(_, _) =>
        jobResult = Some(r)
        allowExecute = false
        allowRedo = false
        allowUndo = true
      case Operation.Result.Cancel(_) =>
      case _ =>
        allowExecute = false
        allowRedo = false
        allowUndo = false
    }
    // update the job state
    result
  }
  protected def undo(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[(String, String, NLS)] = {
    if (canUndo) {
      allowExecute = false
      allowRedo = true
      Operation.Result.OK()
    } else
      Operation.Result.Error(s"Unable to process $this: redo and execute are prohibited")
  }
}
