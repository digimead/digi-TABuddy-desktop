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

package org.digimead.tabuddy.desktop.view.modification.ui.action

import javax.inject.Inject
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.definition.{ Context, Operation }
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.ui.definition.widget.{ AppWindow, VComposite }
import org.digimead.tabuddy.desktop.logic.operation.view.OperationModifyFilterList
import org.digimead.tabuddy.desktop.logic.payload.marker.GraphMarker
import org.digimead.tabuddy.desktop.logic.payload.view.Filter
import org.digimead.tabuddy.desktop.view.modification.{ Messages, bundleId }
import org.eclipse.core.runtime.jobs.Job
import org.eclipse.e4.core.contexts.Active
import org.eclipse.e4.core.di.annotations.Optional
import org.eclipse.jface.action.{ Action ⇒ JFaceAction, IAction }
import org.eclipse.swt.widgets.Event

/**
 * Modify filter list.
 */
class ActionModifyFilterList @Inject() (windowContext: Context) extends JFaceAction(Messages.filters_text) with XLoggable {
  setId(ActionModifyFilterList.id)
  /** Flag indicating whether the action is enabled. */
  @volatile protected var vContext = Option.empty[Context]

  if (windowContext.getLocal(classOf[AppWindow]) == null)
    throw new IllegalArgumentException(s"${windowContext} does not contain AppWindow.")

  override def isEnabled(): Boolean = super.isEnabled &&
    vContext.map { context ⇒ context.getActive(classOf[GraphMarker]) != null }.getOrElse(false)

  /** Runs this action, passing the triggering SWT event. */
  @log
  override def runWithEvent(event: Event) = for {
    context ← vContext
    marker ← Option(context.get(classOf[GraphMarker]))
  } marker.safeRead { state ⇒
    OperationModifyFilterList(state.graph, App.execNGet { state.payload.getAvailableViewFilters().toSet }).foreach { operation ⇒
      operation.getExecuteJob() match {
        case Some(job) ⇒
          job.setPriority(Job.LONG)
          job.onComplete(_ match {
            case Operation.Result.OK(result, message) ⇒
              log.info(s"Operation completed successfully: ${result}")
              result.foreach { case (filters) ⇒ Filter.save(marker, filters) }
            case Operation.Result.Cancel(message) ⇒
              log.warn(s"Operation canceled, reason: ${message}.")
            case other ⇒
              log.error(s"Unable to complete operation: ${other}.")
          }).schedule()
        case None ⇒
          throw new RuntimeException(s"Unable to create job for ${operation}.")
      }
    }
  }

  /** Update enabled action state. */
  @log
  protected def updateEnabled() = if (isEnabled)
    firePropertyChange(IAction.ENABLED, java.lang.Boolean.FALSE, java.lang.Boolean.TRUE)
  else
    firePropertyChange(IAction.ENABLED, java.lang.Boolean.TRUE, java.lang.Boolean.FALSE)
  /** Invoked on view activation. */
  @Inject @Optional
  protected def onViewChanged(@Active @Optional vComposite: VComposite, @Active @Optional marker: GraphMarker) =
    ActionModifyFilterList.synchronized {
      val newContext = {
        for {
          composite ← Option(vComposite)
          marker ← Option(marker)
        } yield vComposite.getContext
      } getOrElse None
      if (newContext != vContext) {
        vContext = newContext
        App.exec { updateEnabled() }
      }
    }
}

object ActionModifyFilterList {
  val id = bundleId + "#ModifyFilterList"
}
