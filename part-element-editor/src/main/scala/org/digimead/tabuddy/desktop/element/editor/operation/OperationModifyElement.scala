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

package org.digimead.tabuddy.desktop.element.editor.operation

import java.util.concurrent.{ CancellationException, Exchanger }
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.definition.Operation
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.element.editor.ui.dialog.ElementEditor
import org.digimead.tabuddy.desktop.logic
import org.digimead.tabuddy.desktop.logic.payload.Payload
import org.digimead.tabuddy.desktop.logic.payload.marker.GraphMarker
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.element.Element
import org.digimead.tabuddy.model.graph.Graph
import org.eclipse.core.runtime.{ IAdaptable, IProgressMonitor }
import org.eclipse.e4.core.contexts.ContextInjectionFactory
import org.eclipse.swt.widgets.Shell
import org.digimead.tabuddy.desktop.logic.payload.ElementTemplate

/**
 * 'Modify the element' operation.
 */
class OperationModifyElement extends logic.operation.OperationModifyElement with XLoggable {
  /**
   * Modify the element.
   *
   * @param element modified element
   * @return
   */
  def apply(element: Element): Element = {
    log.info(s"Modify element ${element}.")
    dialog(element) match {
      case Operation.Result.OK(Some(element), _) ⇒ element
      case _ ⇒ throw new CancellationException()
    }
  }
  /**
   * Create 'Modify the element' operation.
   *
   * @param element modified element
   * @return 'Modify the element' operation
   */
  def operation(element: Element) =
    new Implemetation(element)

  protected def dialog(element: Element): Operation.Result[Element] = {
    val marker = GraphMarker(element.eGraph)
    val exchanger = new Exchanger[Operation.Result[Element]]()
    App.assertEventThread(false)
    // this lock is preparation that prevents freeze of the event loop thread
    marker.safeRead { _ ⇒
      App.exec {
        GraphMarker.shell(marker) match {
          case Some((context, shell)) ⇒
            // actual lock inside event loop thread
            marker.safeRead { state ⇒
              state.payload.elementTemplates.get(element.eScope.modificator) match {
                case Some(template) ⇒
                  val dialogContext = context.createChild("ElementEditorDialog")
                  dialogContext.set(classOf[Shell], shell)
                  dialogContext.set(classOf[Graph[_ <: Model.Like]], element.eGraph)
                  dialogContext.set(classOf[GraphMarker], marker)
                  dialogContext.set(classOf[Payload], state.payload)
                  dialogContext.set(classOf[Element], element)
                  dialogContext.set(classOf[ElementTemplate], template)
                  dialogContext.set(ElementEditor.newElementFlagId, false: java.lang.Boolean)
                  val dialog = ContextInjectionFactory.make(classOf[ElementEditor], dialogContext)
                  dialog.openOrFocus { result ⇒
                    context.removeChild(dialogContext)
                    dialogContext.dispose()
                    if (result == org.eclipse.jface.window.Window.OK)
                      exchanger.exchange(Operation.Result.OK(Some(dialog.getModifiedElement())))
                    else
                      exchanger.exchange(Operation.Result.Cancel())
                  }
                case None ⇒
                  exchanger.exchange(Operation.Result.Error(s"Element template for ${element} not found."))
              }
            }
          case None ⇒
            exchanger.exchange(Operation.Result.Error("Unable to find active shell."))
        }
      }(App.LongRunnable)
    }
    exchanger.exchange(null)
  }

  class Implemetation(element: Element)
    extends logic.operation.OperationModifyElement.Abstract(element) with XLoggable {
    @volatile protected var allowExecute = true

    override def canExecute() = allowExecute
    override def canRedo() = false
    override def canUndo() = false

    protected def execute(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[Element] = {
      try {
        Operation.Result.OK(Option(OperationModifyElement.this(element)))
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
