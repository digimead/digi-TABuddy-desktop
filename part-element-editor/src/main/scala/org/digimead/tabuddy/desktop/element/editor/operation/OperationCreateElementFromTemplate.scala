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

package org.digimead.tabuddy.desktop.element.editor.operation

import java.util.concurrent.{ CancellationException, Exchanger }
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.definition.Operation
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.element.editor.ui.dialog.ElementEditor
import org.digimead.tabuddy.desktop.logic
import org.digimead.tabuddy.desktop.logic.payload.marker.GraphMarker
import org.digimead.tabuddy.desktop.logic.payload.{ ElementTemplate, Payload }
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.element.Element
import org.digimead.tabuddy.model.element.Element.relative2absolute
import org.digimead.tabuddy.model.graph.Graph
import org.eclipse.core.runtime.{ IAdaptable, IProgressMonitor }
import org.eclipse.e4.core.contexts.ContextInjectionFactory
import org.eclipse.swt.widgets.Shell

/**
 * 'Create a new element from template' operation.
 */
class OperationCreateElementFromTemplate extends logic.operation.OperationCreateElementFromTemplate with XLoggable {
  /**
   * Create a new element from template.
   *
   * @param template template for the new element
   * @param container container for the new element
   * @return the modified/the same filter
   */
  def apply(template: ElementTemplate, container: Element): Element = {
    log.info(s"Create new element inside ${container}.")
    dialog(template, container) match {
      case Operation.Result.OK(Some(element), _) ⇒ element
      case _ ⇒ throw new CancellationException()
    }
  }
  /**
   * Create 'Create a new element from template' operation.
   *
   * @param template template for the new element
   * @param container container for the new element
   * @return 'Create a new element from template' operation
   */
  def operation(template: ElementTemplate, container: Element) =
    new Implemetation(template, container)

  protected def dialog(template: ElementTemplate, container: Element): Operation.Result[Element] = {
    val marker = GraphMarker(container.eGraph)
    val exchanger = new Exchanger[Operation.Result[Element]]()
    App.assertEventThread(false)
    // this lock is preparation that prevents freeze of the event loop thread
    marker.safeRead { _ ⇒
      App.exec {
        GraphMarker.shell(marker) match {
          case Some((context, shell)) ⇒
            // actual lock inside event loop thread
            marker.safeRead { state ⇒
              val newElementID = state.payload.generateNew("New" + template.element.eScope, "_", newId ⇒ container.eNode.safeRead(_.exists(_.id.name == newId)))
              val element = template.factory(container, Symbol(newElementID), template.id)
              val dialogContext = context.createChild("ElementEditorDialog")
              dialogContext.set(classOf[Shell], shell)
              dialogContext.set(classOf[Graph[_ <: Model.Like]], element.eGraph)
              dialogContext.set(classOf[GraphMarker], marker)
              dialogContext.set(classOf[Payload], state.payload)
              dialogContext.set(classOf[Element], element)
              dialogContext.set(classOf[ElementTemplate], template)
              dialogContext.set(ElementEditor.newElementFlagId, true: java.lang.Boolean)
              val dialog = ContextInjectionFactory.make(classOf[ElementEditor], dialogContext)
              dialog.openOrFocus { result ⇒
                context.removeChild(dialogContext)
                dialogContext.dispose()
                if (result == org.eclipse.jface.window.Window.OK)
                  exchanger.exchange(Operation.Result.OK(Some(dialog.getModifiedElement())))
                else {
                  element.eNode.detach()
                  exchanger.exchange(Operation.Result.Cancel())
                }
              }
            }
          case None ⇒
            exchanger.exchange(Operation.Result.Error("Unable to find active shell."))
        }
      }(App.LongRunnable)
    }
    exchanger.exchange(null)
  }

  class Implemetation(template: ElementTemplate, container: Element)
      extends logic.operation.OperationCreateElementFromTemplate.Abstract(template, container) with XLoggable {
    @volatile protected var allowExecute = true

    override def canExecute() = allowExecute
    override def canRedo() = false
    override def canUndo() = false

    protected def execute(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[Element] = {
      try {
        Operation.Result.OK(Option(OperationCreateElementFromTemplate.this(template, container)))
      } catch {
        case e: CancellationException ⇒
          Operation.Result.Cancel()
      }
    }
    protected def redo(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[Element] =
      throw new UnsupportedOperationException
    protected def undo(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[Element] =
      throw new UnsupportedOperationException
  }
}
