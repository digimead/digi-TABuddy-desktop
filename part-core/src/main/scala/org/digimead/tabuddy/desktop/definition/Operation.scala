/**
 * This file is part of the TABuddy project.
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
 * that is created or manipulated using TABuddy.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license. Buying such a license is mandatory as soon as you
 * develop commercial activities involving the TABuddy software without
 * disclosing the source code of your own applications.
 * These activities include: offering paid services to customers,
 * serving files in a web or/and network application,
 * shipping TABuddy with a closed source product.
 *
 * For more information, please contact Digimead Team at this
 * address: ezh@ezh.msk.ru
 */

package org.digimead.tabuddy.desktop.definition

import scala.reflect.runtime.universe

import org.digimead.digi.lib.log.api.Loggable
import org.eclipse.core.commands.operations.AbstractOperation
import org.eclipse.core.commands.operations.OperationHistoryFactory
import org.eclipse.core.runtime.IAdaptable
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.jobs.IJobChangeEvent
import org.eclipse.core.runtime.jobs.JobChangeAdapter

/**
 * Operation base class.
 */
abstract class Operation[A: universe.TypeTag](label: String) extends AbstractOperation(label) with api.Operation[A] {
  this: Loggable =>

  override protected def execute(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[A]
  override protected def redo(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[A]
  override protected def undo(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[A]

  /** Create execute job for this operation. */
  def executeJob(info: Option[IAdaptable] = None): Operation.Job[A] = {
    new Operation.Job[A](getLabel()) {
      protected def run(monitor: IProgressMonitor): IStatus = {
        log.info(s"""Begin "${label}" job [EXECUTE].""")
        val result = Operation.history.execute(Operation.this, monitor, info.getOrElse(null))
        log.info(s"""Job "${label}" is finished: ${result}.""")
        result
      }
    }
  }
  /** Get execute job if possible. */
  def getExecuteJob(info: Option[IAdaptable] = None): Option[Operation.Job[A]] =
    if (canExecute()) Some(executeJob(info)) else None
  /** Get redo job if possible. */
  def getRedoJob(info: Option[IAdaptable] = None): Option[Operation.Job[A]] =
    if (canRedo()) Some(redoJob(info)) else None
  /** Get undo job if possible. */
  def getUndoJob(info: Option[IAdaptable] = None): Option[Operation.Job[A]] =
    if (canUndo()) Some(undoJob(info)) else None
  /** Create redo job for this operation. */
  def redoJob(info: Option[IAdaptable] = None): Operation.Job[A] = {
    new Operation.Job[A](getLabel()) {
      protected def run(monitor: IProgressMonitor): IStatus = {
        log.info(s"""Begin "${label}" job [REDO].""")
        val result = Operation.history.redoOperation(Operation.this, monitor, info.getOrElse(null))
        log.info(s"""Job "${label}" is finished: ${result}.""")
        result
      }
    }
  }
  /** Create undo job for this operation. */
  def undoJob(info: Option[IAdaptable] = None): Operation.Job[A] = {
    new Operation.Job[A](getLabel()) {
      protected def run(monitor: IProgressMonitor): IStatus = {
        log.info(s"""Begin "${label}" job [UNDO].""")
        val result = Operation.history.undoOperation(Operation.this, monitor, info.getOrElse(null))
        log.info(s"""Job "${label}" is finished: ${result}.""")
        result
      }
    }
  }
}

object Operation extends Loggable {
  /** Operation history. */
  lazy val history = OperationHistoryFactory.getOperationHistory()

  /** Operation job wrapper. */
  abstract class Job[A: universe.TypeTag](label: String) extends org.eclipse.core.runtime.jobs.Job(label) {
    def onComplete[B](f: Result[A] => B): this.type = {
      addJobChangeListener(new JobChangeAdapter() {
        override def done(event: IJobChangeEvent) = event.getResult() match {
          case result: Result[A] =>
            f(result)
          case other =>
            val error = s"Unexpected job result: ${other}."
            Option(other.getException()) match {
              case Some(exception) =>
                log.error(error, exception)
              case None =>
                log.fatal(error)
            }
            f(Result.Error[A](error))
        }
      })
      this
    }
  }
  /**
   * Job result
   */
  sealed trait Result[A] extends IStatus {
    val exception: Throwable
    val message: String
    val result: Option[A]
    val severity: Int
    val tt: universe.TypeTag[_ <: Result[A]]

    /** Returns the operation/job result */
    def get(): Option[A] = result
    /**
     * Returns a list of status object immediately contained in this
     * multi-status, or an empty list if this is not a multi-status.
     */
    def getChildren(): Array[IStatus] = Array()
    /** Returns the plug-in-specific status code describing the outcome. */
    def getCode() = 0
    /**
     * Returns the relevant low-level exception, or <code>null</code> if none.
     * For example, when an operation fails because of a network communications
     * failure, this might return the <code>java.io.IOException</code>
     * describing the exact nature of that failure.
     */
    def getException() = exception
    /**
     * Returns the message describing the outcome.
     * The message is localized to the current locale.
     */
    def getMessage() = message
    /**
     * Returns the unique identifier of the plug-in associated with this status
     * (this is the plug-in that defines the meaning of the status code).
     */
    def getPlugin() = "none"
    /** Returns the severity. */
    def getSeverity() = severity
    /**
     * Returns whether this status is a multi-status.
     * A multi-status describes the outcome of an operation
     * involving multiple operands.
     */
    def isMultiStatus() = false
    /**
     * Returns whether this status indicates everything is okay
     * (neither info, warning, nor error).
     */
    def isOK() = getSeverity match {
      case IStatus.OK => true
      case IStatus.CANCEL => true
      case _ => false
    }
    /**
     * Returns whether the severity of this status matches the given
     * severity mask. Note that a status with severity <code>OK</code>
     * will never match; use <code>isOK</code> instead to detect
     * a status with a severity of <code>OK</code>.
     */
    def matches(severityMask: Int) = (severityMask & severity) != 0
  }
  object Result {
    case class AsyncFinish[A](val result: Option[A] = None, val message: String = "operation complete")(implicit val tt: universe.TypeTag[AsyncFinish[A]]) extends Result[A] {
      val severity = IStatus.OK
      val exception = null

      /** Returns the plug-in-specific status code describing the outcome. */
      override def getCode() = 1
      /**
       * Returns the unique identifier of the plug-in associated with this status
       * (this is the plug-in that defines the meaning of the status code).
       */
      override def getPlugin() = org.eclipse.core.internal.jobs.JobManager.PI_JOBS
    }
    case class Cancel[A](val message: String = "operation cancel")(implicit val tt: universe.TypeTag[Cancel[A]]) extends Result[A] {
      val exception = null
      val result = None
      val severity = IStatus.CANCEL
    }
    case class Error[A](val message: String, logAsFatal: Boolean = true)(implicit val tt: universe.TypeTag[Error[A]]) extends Result[A] {
      val exception = null
      val result = None
      val severity = IStatus.ERROR

      if (logAsFatal)
        log.fatal(message)
      else
        log.warn(message)
    }
    case class Exception[A](override val exception: Throwable, logAsFatal: Boolean = true)(implicit val tt: universe.TypeTag[Exception[A]]) extends Result[A] {
      val message = "Error: " + exception
      val severity = IStatus.ERROR
      val result = None

      if (logAsFatal)
        log.error(exception.toString(), exception)
      else
        log.warn(exception.toString())
    }
    case class OK[A](val result: Option[A] = None, val message: String = "operation complete")(implicit val tt: universe.TypeTag[OK[A]]) extends Result[A] {
      val severity = IStatus.OK
      val exception = null
    }
  }
}
