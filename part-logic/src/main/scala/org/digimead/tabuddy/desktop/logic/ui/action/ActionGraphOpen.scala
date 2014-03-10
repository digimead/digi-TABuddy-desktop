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

import java.util.concurrent.Exchanger
import javax.inject.Inject
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.definition.{ Context, Operation }
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.ui.UI
import org.digimead.tabuddy.desktop.core.ui.block.Configuration
import org.digimead.tabuddy.desktop.core.ui.definition.widget.{ AppWindow, VComposite }
import org.digimead.tabuddy.desktop.core.ui.operation.OperationViewCreate
import org.digimead.tabuddy.desktop.logic.Messages
import org.digimead.tabuddy.desktop.logic.payload.maker.GraphMarker
import org.digimead.tabuddy.desktop.logic.ui.dialog.GraphSelectionDialog
import org.digimead.tabuddy.desktop.logic.ui.view
import org.eclipse.core.runtime.jobs.Job
import org.eclipse.e4.core.contexts.ContextInjectionFactory
import org.eclipse.jface.action.{ Action ⇒ JFaceAction }
import org.eclipse.swt.widgets.Shell
import scala.concurrent.Future

class ActionGraphOpen @Inject() (windowContext: Context) extends JFaceAction(Messages.openFile_text) with Loggable {
  /** Akka execution context. */
  implicit lazy val ec = App.system.dispatcher

  @log
  override def run {
    val appWindow = windowContext.get(classOf[AppWindow])
    OperationViewCreate(appWindow.id, Configuration.CView(view.graph.View.factory.configuration)).foreach { operation ⇒
      operation.getExecuteJob() match {
        case Some(job) ⇒
          job.setPriority(Job.LONG)
          job.onComplete(_ match {
            case Operation.Result.OK(Some(viewId), message) ⇒ UI.viewMap.get(viewId).map(view ⇒ Future { onViewCreated(view) })
            case _ ⇒
          }).schedule()
        case None ⇒
          log.fatal(s"Unable to create job for ${operation}.")
      }
    }
  }
  /** Create graph when view is created. */
  def onViewCreated(view: VComposite) {
    val exchanger = new Exchanger[Operation.Result[Unit]]()
    App.assertEventThread(false)
    App.exec {
      UI.getActiveShell match {
        case Some(shell) ⇒
          val markers = GraphMarker.list().map(GraphMarker(_)).
            filter(m ⇒ m.markerIsValid && !m.graphIsOpen()).sortBy(_.graphModelId.name).sortBy(_.graphOrigin.name)
          val dialogContext = windowContext.createChild("GraphSelectionDialog")
          dialogContext.set(classOf[Shell], shell)
          val dialog = ContextInjectionFactory.make(classOf[GraphSelectionDialog], dialogContext)
          dialog.openOrFocus { result ⇒
            windowContext.removeChild(dialogContext)
            dialogContext.dispose()
            if (result == org.eclipse.jface.window.Window.OK)
              exchanger.exchange(Operation.Result.OK(Some()))
            else
              exchanger.exchange(Operation.Result.Cancel())
          }
        case None ⇒
          exchanger.exchange(Operation.Result.Error("Unable to find active shell."))
      }
    }(App.LongRunnable)
    exchanger.exchange(null) match {
      case Operation.Result.OK(result, message) ⇒
      case Operation.Result.Cancel(message) ⇒
        view.ref ! App.Message.Destroy()
      case Operation.Result.Error(message, exception, logAsFatal) ⇒
        if (logAsFatal) log.fatal(message) else log.error(message, exception)
        view.ref ! App.Message.Destroy()
      case unexpected ⇒
        log.fatal(s"Unexpected result: ${unexpected}.")
        view.ref ! App.Message.Destroy()
    }
  }
}
