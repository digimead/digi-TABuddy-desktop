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

package org.digimead.tabuddy.desktop.logic.operation.script

import java.io.File
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.XDependencyInjection
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.definition.Operation
import org.digimead.tabuddy.desktop.logic.Logic
import org.digimead.tabuddy.desktop.logic.operation.script.api.XOperationScriptEvaluate
import org.digimead.tabuddy.desktop.logic.script.Script
import org.digimead.tabuddy.desktop.logic.script.{ Cache, Loader }
import org.eclipse.core.runtime.{ IAdaptable, IProgressMonitor }

/**
 * 'Evaluate the script' operation.
 */
class OperationScriptEvaluate extends XOperationScriptEvaluate with XLoggable {
  /**
   * Evaluate the script.
   *
   * @param script script for evaluation
   * @return result of evaluation
   */
  def apply[T](script: Either[File, String], verbose: Boolean): T = {
    val content = script match {
      case Left(file) ⇒ Loader(file)
      case Right(inline) ⇒ inline
    }
    val unique = Script.unique(content)
    log.info(s"Evaluate script with unique id ${unique}.")
    if (!Logic.container.isOpen())
      throw new IllegalStateException("Workspace is not available.")
    val container = Cache.withScript[T](unique) { Script(content, unique, verbose) }
    container.run()
  }
  /**
   * Create 'Evaluate the script' operation.
   *
   * @param script script for evaluation
   * @return 'Evaluate the script' operation
   */
  def operation[T](script: Either[File, String], verbose: Boolean) = new Implemetation[T](script, verbose)

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

  class Implemetation[T](script: Either[File, String], verbose: Boolean)
    extends OperationScriptEvaluate.Abstract[T](script, verbose) with XLoggable {
    @volatile protected var allowExecute = true

    override def canExecute() = allowExecute
    override def canRedo() = false
    override def canUndo() = false

    protected def execute(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[T] = {
      require(canExecute, "Execution is disabled.")
      try {
        val result = Option[T](OperationScriptEvaluate.this(script, verbose))
        allowExecute = false
        Operation.Result.OK(result)
      } catch {
        case e: Throwable ⇒
          Operation.Result.Error(s"Unable to evaluate script: ${e.getMessage()}.", e)
      }
    }
    protected def redo(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[T] =
      throw new UnsupportedOperationException
    protected def undo(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[T] =
      throw new UnsupportedOperationException
  }
}

object OperationScriptEvaluate extends XLoggable {
  /** Stable identifier with OperationScriptEvaluate DI */
  lazy val operation = DI.operation.asInstanceOf[OperationScriptEvaluate]

  /**
   * Build a new 'Evaluate the script' operation.
   *
   * @param script script for evaluation
   * @return 'Evaluate the script' operation
   */
  @log
  def apply[T](script: Either[File, String], verbose: Boolean): Option[Abstract[T]] =
    Some(operation.operation(script, verbose))

  /** Bridge between abstract XOperation[T] and concrete Operation[T] */
  abstract class Abstract[T](val script: Either[File, String], val verbose: Boolean)
    extends Operation[T](script match {
      case Left(file) ⇒ s"Evaluate ${file}."
      case Right(inline) ⇒ s"Evaluate (inline)."
    }) {
    this: XLoggable ⇒
  }
  /**
   * Dependency injection routines.
   */
  private object DI extends XDependencyInjection.PersistentInjectable {
    lazy val operation = injectOptional[XOperationScriptEvaluate] getOrElse new OperationScriptEvaluate
  }
}
