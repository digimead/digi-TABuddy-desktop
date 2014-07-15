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

import java.util.concurrent.{ Callable, CancellationException }
import javax.inject.Inject
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.definition.{ Context, Operation }
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.ui.{ UI, Wizards }
import org.digimead.tabuddy.desktop.core.ui.block.Configuration
import org.digimead.tabuddy.desktop.core.ui.definition.Action
import org.digimead.tabuddy.desktop.core.ui.definition.widget.AppWindow
import org.digimead.tabuddy.desktop.core.ui.operation.OperationViewCreate
import org.digimead.tabuddy.desktop.logic.Messages
import org.digimead.tabuddy.desktop.logic.payload.marker.GraphMarker
import org.digimead.tabuddy.desktop.logic.ui.view
import org.eclipse.core.runtime.jobs.Job
import scala.concurrent.Future

/**
 * Create new graph.
 */
class ActionGraphNew @Inject() (windowContext: Context) extends Action(Messages.newFile_text) with XLoggable {
  /** Akka execution context. */
  implicit lazy val ec = App.system.dispatcher

  @log
  override def run = {
    UI.getActiveShell match {
      case Some(shell) ⇒
        val callable = App.exec {
          Wizards.open("org.digimead.tabuddy.desktop.logic.ui.wizard.NewGraphWizard", shell, None) match {
            case callable: Callable[_] ⇒
              Future {
                // We leave App.exec section...
                // Block UI thread
                val marker = callable.asInstanceOf[Callable[GraphMarker]].call()
                if (!marker.markerIsValid)
                  throw new IllegalStateException(marker + " is not valid.")
                onGraphCreated(marker)
              } onFailure { case e: Throwable ⇒ log.error("Error while ActionGraphNew: " + e.getMessage(), e) }
            case other if other == org.eclipse.jface.window.Window.CANCEL ⇒
              throw new CancellationException("Unable to create new graph. Operation canceled.")
            case other ⇒
              throw new IllegalStateException(s"Unable to create new graph. Result ${other}.")
          }
        }(App.LongRunnable)

      case None ⇒
        throw new IllegalStateException("Unable to create new graph dialog without parent shell.")
    }
  }
  /** Create view when graph is available. */
  def onGraphCreated(marker: GraphMarker) {
    val appWindow = windowContext.get(classOf[AppWindow])
    OperationViewCreate(appWindow.id, Configuration.CView(view.graph.View.factory.configuration)).foreach { operation ⇒
      operation.getExecuteJob() match {
        case Some(job) ⇒
          job.setPriority(Job.LONG)
          job.onComplete(_ match {
            case Operation.Result.OK(Some(viewId), message) ⇒
              // Fine
              UI.viewMap.get(viewId).map { view ⇒ view.contentRef ! App.Message.Set(marker) }
            case _ ⇒
          }).schedule()
        case None ⇒
          log.fatal(s"Unable to create job for ${operation}.")
      }
    }
  }
}
