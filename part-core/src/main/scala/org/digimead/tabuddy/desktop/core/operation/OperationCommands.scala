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

package org.digimead.tabuddy.desktop.core.operation

import java.util.concurrent.CancellationException
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.definition.Operation
import org.digimead.tabuddy.desktop.core.definition.command.Command
import org.digimead.tabuddy.desktop.core.definition.command.api.Command.Descriptor
import org.eclipse.core.runtime.{ IAdaptable, IProgressMonitor }
import org.digimead.tabuddy.desktop.core.Core

/** 'Get commands' operation. */
class OperationCommands extends api.OperationCommands with Loggable {
  /**
   * Get commands.
   */
  override def apply(onlyAvailable: Boolean): Seq[Command.Descriptor] = {
    log.info(s"Get commands.")
    if (onlyAvailable)
      Command.binded.filter(_.context == Core.context.getActiveLeaf()).map(info ⇒ Command(info.parserId)).toSeq
    else
      Command.registered.toSeq
  }
  /**
   * Create 'Get commands' operation.
   */
  def operation(onlyAvailable: Boolean) = new Implemetation(onlyAvailable).asInstanceOf[Operation[Seq[Descriptor]]]

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

  class Implemetation(onlyAvailable: Boolean) extends OperationCommands.Abstract() with Loggable {
    @volatile protected var allowExecute = true

    override def canExecute() = allowExecute
    override def canRedo() = false
    override def canUndo() = false

    protected def execute(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[Seq[Command.Descriptor]] =
      try Operation.Result.OK(Option(OperationCommands.this(onlyAvailable)))
      catch {
        case e: CancellationException ⇒ Operation.Result.Cancel()
        case e: RuntimeException ⇒ Operation.Result.Error(e.getMessage(), e)
      }
    protected def redo(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[Seq[Command.Descriptor]] =
      throw new UnsupportedOperationException
    protected def undo(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[Seq[Command.Descriptor]] =
      throw new UnsupportedOperationException
  }
}

object OperationCommands {
  /** Stable identifier with OperationCommands DI */
  lazy val operation = DI.operation.asInstanceOf[OperationCommands]

  /** Build a new 'Get information' operation */
  @log
  def apply(onlyAvailable: Boolean): Option[Abstract] = Some(operation.operation(onlyAvailable).asInstanceOf[Abstract])

  /** Bridge between abstract api.Operation[Seq[api.Descriptor]] and concrete Operation[Seq[Command.Descriptor]] */
  abstract class Abstract() extends Operation[Seq[Command.Descriptor]](s"Get commands.") {
    this: Loggable ⇒
  }
  /**
   * Dependency injection routines.
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    lazy val operation = injectOptional[api.OperationCommands] getOrElse new OperationCommands
  }
}
