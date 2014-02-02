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

package org.digimead.tabuddy.desktop.core.ui.inspector.operation

import java.util.concurrent.CancellationException
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.Core
import org.digimead.tabuddy.desktop.core.definition.Context
import org.digimead.tabuddy.desktop.core.definition.Operation
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.ui.inspector.dialog.InspectorDialog
import org.eclipse.core.runtime.{ IAdaptable, IProgressMonitor }
import org.eclipse.e4.core.contexts.ContextInjectionFactory
import org.eclipse.swt.widgets.Shell

/** 'Show inspector' operation. */
class OperationInspector extends api.OperationInspector with Loggable {
  /**
   * Show inspector.
   */
  override def apply() = {
    log.info(s"Show inspector.")
    dialog()
  }
  /**
   * Create 'Show inspector' operation.
   */
  def operation() = new Implemetation().asInstanceOf[Operation[Unit]]

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

  protected def dialog(): Operation.Result[Unit] = {
    App.assertEventThread(false)
    Core.context.getChildren().find(context ⇒ Context.getName(context) == Some("InspectorDialog")) match {
      case Some(dialogContext) ⇒
        App.exec {
          Option(dialogContext.get(classOf[InspectorDialog])).foreach { dialog ⇒
            log.debug("Bring inspector window to top.")
            dialog.getShell().forceFocus()
            dialog.getShell().setActive()
            dialog.getShell().forceActive()
          }
        }
      case None ⇒
        App.exec {
          val dialogContext = Core.context.createChild("InspectorDialog")
          dialogContext.set(classOf[Shell], null)
          val dialog = ContextInjectionFactory.make(classOf[InspectorDialog], dialogContext)
          dialog.openOrFocus { result ⇒
            dialogContext.set(classOf[InspectorDialog], dialog)
            Core.context.removeChild(dialogContext)
            dialogContext.dispose()
          }
        }(App.LongRunnable)
    }
    Operation.Result.OK()
  }

  class Implemetation() extends OperationInspector.Abstract() with Loggable {
    @volatile protected var allowExecute = true

    override def canExecute() = allowExecute
    override def canRedo() = false
    override def canUndo() = false

    protected def execute(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[Unit] =
      try dialog()
      catch {
        case e: IllegalArgumentException ⇒
          Operation.Result.Error(e.getMessage(), e)
        case e: IllegalStateException ⇒
          Operation.Result.Error(e.getMessage(), e)
        case e: CancellationException ⇒
          Operation.Result.Cancel()
      }
    protected def redo(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[Unit] =
      throw new UnsupportedOperationException
    protected def undo(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[Unit] =
      throw new UnsupportedOperationException
  }
}

object OperationInspector {
  /** Stable identifier with OperationInspector DI */
  lazy val operation = DI.operation.asInstanceOf[OperationInspector]

  /** Build a new 'Show inspector' operation */
  @log
  def apply(): Option[Abstract] = Some(operation.operation().asInstanceOf[Abstract])

  /** Bridge between abstract api.Operation[Unit] and concrete Operation[Unit] */
  abstract class Abstract() extends Operation[Unit](s"Show inspector.") {
    this: Loggable ⇒
  }
  /**
   * Dependency injection routines.
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    lazy val operation = injectOptional[api.OperationInspector] getOrElse new OperationInspector
  }
}
