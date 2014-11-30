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

package org.digimead.tabuddy.desktop.logic.ui.action

import javax.inject.Inject
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.definition.{ Context, Operation }
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.ui.UI
import org.digimead.tabuddy.desktop.core.ui.block.Configuration
import org.digimead.tabuddy.desktop.core.ui.definition.Action
import org.digimead.tabuddy.desktop.core.ui.definition.widget.AppWindow
import org.digimead.tabuddy.desktop.core.ui.operation.OperationViewCreate
import org.digimead.tabuddy.desktop.logic.{ Messages, bundleId }
import org.digimead.tabuddy.desktop.logic.operation.graph.OperationGraphOpen
import org.digimead.tabuddy.desktop.logic.payload.marker.GraphMarker
import org.digimead.tabuddy.desktop.logic.ui.dialog.GraphSelectionDialog
import org.digimead.tabuddy.desktop.logic.ui.view
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.graph.Graph
import org.eclipse.core.runtime.jobs.Job
import org.eclipse.e4.core.contexts.ContextInjectionFactory
import org.eclipse.swt.widgets.Shell

class ActionGraphOpen @Inject() (windowContext: Context) extends Action(Messages.openFile_text) with XLoggable {
  setId(bundleId + "#GraphOpen")

  @log
  override def run = App.execAsync {
    UI.getActiveShell match {
      case Some(shell) ⇒
        val markers = GraphMarker.list().map(GraphMarker(_)).
          filter(m ⇒ m.markerIsValid && !m.graphIsOpen()).sortBy(_.graphModelId.name).sortBy(_.graphOrigin.name)
        val dialogContext = windowContext.createChild("GraphSelectionDialog")
        dialogContext.set(classOf[Shell], shell)
        dialogContext.set(classOf[Array[GraphMarker]], markers.toArray)
        val dialog = ContextInjectionFactory.make(classOf[GraphSelectionDialog], dialogContext)
        dialog.openOrFocus { result ⇒
          windowContext.removeChild(dialogContext)
          dialogContext.dispose()
          if (result == org.eclipse.jface.window.Window.OK)
            dialog.getMarker() match {
              case Some(marker) ⇒
                onGraphSelected(marker)
              case None ⇒
                log.debug("There is no graph selected.")
            }
          else
            log.debug("Cancelled.")
        }
      case None ⇒
        log.error("Unable to find active shell.")
    }
  }(App.LongRunnable)
  /** Load graph when view is created. */
  def onGraphSelected(marker: GraphMarker) {
    val appWindow = windowContext.get(classOf[AppWindow])
    OperationViewCreate(appWindow.id, Configuration.CView(view.graph.View.factory.configuration)).foreach { operation ⇒
      operation.getExecuteJob() match {
        case Some(job) ⇒
          job.setPriority(Job.LONG)
          job.onComplete(_ match {
            case Operation.Result.OK(Some(viewId), message) ⇒
              // We should display progress here.
              OperationGraphOpen(marker.uuid).foreach { operation ⇒
                operation.getExecuteJob() match {
                  case Some(job) ⇒
                    job.setPriority(Job.LONG)
                    job.onComplete(_ match {
                      case Operation.Result.OK(Some(graph: Graph[Model.Like]), message) ⇒
                        // Fine
                        UI.viewMap.get(viewId).map { view ⇒ view.contentRef ! App.Message.Set(marker) }
                      case _ ⇒
                    }).schedule()
                  case None ⇒
                    throw new RuntimeException(s"Unable to create job for ${operation}.")
                }
              }
            case _ ⇒
          }).schedule()
        case None ⇒
          log.fatal(s"Unable to create job for ${operation}.")
      }
    }
  }
}
