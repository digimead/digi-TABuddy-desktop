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

import org.eclipse.core.runtime.IStatus
import org.digimead.digi.lib.log.api.Loggable
import org.eclipse.core.runtime.IProgressMonitor
import java.lang.ref.WeakReference

import language.implicitConversions

/**
 * Job base class.
 */
abstract class Job[A](
  /** The property representing the job label. */
  label: String) extends api.Job[A] {
  /** Original job instance from Eclipse platform */
  lazy val unsafe = Job.Unsafe[A](label, new WeakReference(this))

  /**
   * Jobs that complete their execution asynchronously must indicate when they
   * are finished by calling this method.  This method must not be called by
   * a job that has not indicated that it is executing asynchronously.
   * <p>
   * This method must not be called from within the scope of a job's <code>run</code>
   * method.  Jobs should normally indicate completion by returning an appropriate
   * status from the <code>run</code> method.  Jobs that return a status of
   * <code>ASYNC_FINISH</code> from their run method must later call
   * <code>done</code> to indicate completion.
   *
   * @param result a status object indicating the result of the job's execution.
   * @see #ASYNC_FINISH
   * @see #run(IProgressMonitor)
   */
  def done(result: Job.Result[A]) = unsafe.done(result)
  /**
   * Executes this job.  Returns the result of the execution.
   * <p>
   * The provided monitor can be used to report progress and respond to
   * cancellation.  If the progress monitor has been canceled, the job
   * should finish its execution at the earliest convenience and return a result
   * status of severity {@link IStatus#CANCEL}.  The singleton
   * cancel status {@link Status#CANCEL_STATUS} can be used for
   * this purpose.  The monitor is only valid for the duration of the invocation
   * of this method.
   * <p>
   * This method must not be called directly by clients.  Clients should call
   * <code>schedule</code>, which will in turn cause this method to be called.
   * <p>
   * Jobs can optionally finish their execution asynchronously (in another thread) by
   * returning a result status of Job.Result.AsyncFinish[A].  Jobs that finish
   * asynchronously <b>must</b> specify the execution thread by calling
   * <code>setThread</code>, and must indicate when they are finished by calling
   * the method <code>done</code>.
   *
   * @param monitor the monitor to be used for reporting progress and
   * responding to cancelation.
   * @return resulting status of the run.
   * @see #Job.Result.AsyncFinish[A]
   * @see #done(IStatus)
   */
  protected def run(monitor: IProgressMonitor): Job.Result[A]
}

object Job extends Loggable {
  implicit def job2unsafe[A](j: Job[A]): Unsafe[A] = j.unsafe

  /**
   * Job result
   */
  sealed trait Result[A] extends IStatus {
    val exception: Throwable
    val message: String
    val result: Option[A]
    val severity: Int

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
    case class AsyncFinish[A](val result: Option[A] = None, val message: String = "operation complete") extends Result[A] {
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
    case class Cancel[A](val message: String = "operation cancel") extends Result[A] {
      val exception = null
      val result = None
      val severity = IStatus.CANCEL
    }
    case class Error[A](val message: String, logAsFatal: Boolean = true) extends Result[A] {
      val exception = null
      val result = None
      val severity = IStatus.ERROR

      if (logAsFatal)
        log.fatal(message)
      else
        log.warn(message)
    }
    case class Exception[A](override val exception: Throwable, logAsFatal: Boolean = true) extends Result[A] {
      val message = "Error: " + exception
      val severity = IStatus.ERROR
      val result = None

      if (logAsFatal)
        log.error(exception.toString(), exception)
      else
        log.warn(exception.toString())
    }
    case class OK[A](val result: Option[A] = None, val message: String = "operation complete") extends Result[A] {
      val severity = IStatus.OK
      val exception = null
    }
  }
  /**
   * Wrapper for unsafe, dirty implementation from framework.
   */
  case class Unsafe[A](label: String, safeWrapper: WeakReference[Job[A]]) extends org.eclipse.core.runtime.jobs.Job(label) {
    override protected def run(monitor: IProgressMonitor): Job.Result[A] = safeWrapper.get.run(monitor)
  }
}
