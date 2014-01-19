/**
 * This file is part of the TA Buddy project.
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

package org.digimead.tabuddy.desktop.model.definition.operation

import java.util.concurrent.{ CancellationException, Exchanger }
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.definition.Operation
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.logic
import org.digimead.tabuddy.desktop.logic.payload.ElementTemplate
import org.digimead.tabuddy.desktop.logic.payload.maker.GraphMarker
import org.digimead.tabuddy.desktop.logic.payload.{ Payload, api ⇒ papi }
import org.digimead.tabuddy.desktop.model.definition.ui.dialog.eltemed.ElementTemplateEditor
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.graph.Graph
import org.eclipse.core.runtime.{ IAdaptable, IProgressMonitor }
import org.eclipse.e4.core.contexts.ContextInjectionFactory
import org.eclipse.swt.widgets.Shell

/**
 * Modify an element template.
 */
class OperationModifyElementTemplate extends logic.operation.OperationModifyElementTemplate with Loggable {
  /**
   * Modify an element template.
   *
   * @param graph graph that contains an element template
   * @param template the initial element template
   * @param templateList exists element templates
   * @return the modified element template
   */
  def apply(graph: Graph[_ <: Model.Like], template: papi.ElementTemplate, templateList: Set[papi.ElementTemplate]): papi.ElementTemplate = {
    log.info(s"Modify ${template} for ${graph}.")
    dialog(graph, template, templateList) match {
      case Operation.Result.OK(Some(template), _) ⇒ template
      case _ ⇒ template
    }
  }
  /**
   * Create 'Modify filter' operation.
   *
   * @param graph graph that contains a filter
   * @param filter the initial filter
   * @param filterList the list of exists filters
   * @return 'Modify filter' operation
   */
  override def operation(graph: Graph[_ <: Model.Like], template: papi.ElementTemplate, templateList: Set[papi.ElementTemplate]) =
    new Implemetation(graph, template, templateList)

  protected def dialog(graph: Graph[_ <: Model.Like], template: papi.ElementTemplate, templateList: Set[papi.ElementTemplate]): Operation.Result[papi.ElementTemplate] = {
    val marker = GraphMarker(graph)
    val exchanger = new Exchanger[Operation.Result[papi.ElementTemplate]]()
    App.assertEventThread(false)
    // this lock is preparation that prevents freeze of the event loop thread
    marker.safeRead { _ ⇒
      App.exec {
        GraphMarker.shell(marker) match {
          case Some((context, shell)) ⇒
            // actual lock inside event loop thread
            marker.safeRead { state ⇒
              val dialogContext = context.createChild("ElementTemplateEditorDialog")
              dialogContext.set(classOf[Shell], shell)
              dialogContext.set(classOf[Graph[_ <: Model.Like]], graph)
              dialogContext.set(classOf[GraphMarker], marker)
              dialogContext.set(classOf[Payload], state.payload)
              dialogContext.set(classOf[papi.ElementTemplate], template)
              dialogContext.set(classOf[Set[papi.ElementTemplate]], templateList)
              val dialog = ContextInjectionFactory.make(classOf[ElementTemplateEditor], dialogContext)
              dialog.openOrFocus { result ⇒
                context.removeChild(dialogContext)
                dialogContext.dispose()
                if (result == org.eclipse.jface.window.Window.OK)
                  exchanger.exchange(Operation.Result.OK({
                    val after = dialog.getModifiedTemplate
                    // compare templates and deep compare template properties
                    if (templateList.exists(template ⇒ ElementTemplate.compareDeep(template, after)))
                      None // nothing changes, and template is already exists
                    else
                      Some(after)
                  }))
                else
                  exchanger.exchange(Operation.Result.Cancel())
              }
            }
          case None ⇒
            exchanger.exchange(Operation.Result.Error("Unable to find active shell."))
        }
      }(App.LongRunnable)
    }
    exchanger.exchange(null)
  }

  class Implemetation(
    /** Graph container. */
    graph: Graph[_ <: Model.Like],
    /** The initial element template. */
    template: papi.ElementTemplate,
    /** The list of element template. */
    templateList: Set[papi.ElementTemplate])
    extends logic.operation.OperationModifyElementTemplate.Abstract(graph, template, templateList) with Loggable {
    @volatile protected var allowExecute = true

    override def canExecute() = allowExecute
    override def canRedo() = false
    override def canUndo() = false

    protected def execute(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[papi.ElementTemplate] =
      try dialog(graph, template, templateList)
      catch {
        case e: IllegalArgumentException ⇒
          Operation.Result.Error(e.getMessage(), e)
        case e: IllegalStateException ⇒
          Operation.Result.Error(e.getMessage(), e)
        case e: CancellationException ⇒
          Operation.Result.Cancel()
      }
    protected def redo(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[papi.ElementTemplate] =
      throw new UnsupportedOperationException
    protected def undo(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[papi.ElementTemplate] =
      throw new UnsupportedOperationException
  }
}
