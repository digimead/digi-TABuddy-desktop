/**
 * This file is part of the TA Buddy project.
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

package org.digimead.tabuddy.desktop.core.ui.operation

import java.util.concurrent.CancellationException
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.definition.{ Context, Operation }
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.ui.UI
import org.digimead.tabuddy.desktop.core.ui.block.ViewLayer
import org.digimead.tabuddy.desktop.core.ui.definition.widget.AppWindow
import org.eclipse.core.runtime.{ IAdaptable, IProgressMonitor }

/** 'Create a new view' operation. */
class OperationView extends api.OperationView with Loggable {
  /**
   * Create a new view.
   */
  def apply(activeContext: Context, viewFactory: ViewLayer.Factory) {
    log.info("Create new view from " + viewFactory)
    activeContext.get(UI.windowContextKey) match {
      case window: AppWindow ⇒
        window.ref ! App.Message.Create(Left(viewFactory))
      case unknwon ⇒
        log.fatal(s"Unable to find active window for ${this}: '${activeContext}', '${viewFactory}'.")
    }
  }
  /**
   * Create 'Create a new view' operation.
   */
  def operation(activeContext: Context, viewFactory: ViewLayer.Factory) =
    new Implemetation(activeContext, viewFactory)

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

  class Implemetation(val activeContext: Context, val viewFactory: ViewLayer.Factory) extends OperationView.Abstract() with Loggable {
    @volatile protected var allowExecute = true

    override def canExecute() = allowExecute
    override def canRedo() = false
    override def canUndo() = false

    protected def execute(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[Unit] =
      try Operation.Result.OK(Option(OperationView.this(activeContext, viewFactory)))
      catch { case e: CancellationException ⇒ Operation.Result.Cancel() }
    protected def redo(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[Unit] =
      throw new UnsupportedOperationException
    protected def undo(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[Unit] =
      throw new UnsupportedOperationException
  }
}

object OperationView extends Loggable {
  /** Stable identifier with OperationView DI */
  lazy val operation = DI.operation.asInstanceOf[OperationView]

  /** Build a new 'Create a new view' operation */
  @log
  def apply(activeContext: Context, viewFactory: ViewLayer.Factory): Option[Abstract] =
    Some(operation.operation(activeContext, viewFactory))

  /** Bridge between abstract api.Operation[Unit] and concrete Operation[Unit] */
  abstract class Abstract() extends Operation[Unit](s"Create a new view.") {
    this: Loggable ⇒
  }
  /**
   * Dependency injection routines.
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    lazy val operation = injectOptional[api.OperationView] getOrElse new OperationView
  }
}
