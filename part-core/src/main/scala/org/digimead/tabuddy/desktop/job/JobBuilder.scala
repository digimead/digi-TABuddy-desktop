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

package org.digimead.tabuddy.desktop.job

import org.digimead.tabuddy.desktop.Data
import org.eclipse.core.commands.operations.IOperationHistory
import org.eclipse.core.runtime.IAdaptable
import org.eclipse.core.runtime.IProgressMonitor

/**
 * Job singleton interface
 * Build new job instance
 * Provide user interface and basic state verification
 */
class JobBuilder[A <: Job[_]](protected val anchor: AnyRef, protected val createJob: () => A) extends Job.Builder[A] {
  protected val buildArgs = new InheritableThreadLocal[JobBuilder.Args[A]]()
  buildArgs.set(JobBuilder.Args())

  def setAdaptable(info: IAdaptable, guard: Boolean = true) = {
    if (guard) assert(buildArgs.get.info == null, "IAdaptable is already defined")
    buildArgs.set(buildArgs.get.copy(info = info))
    this
  }
  def setHistory(history: IOperationHistory, guard: Boolean = true) = {
    if (guard) assert(buildArgs.get.history == Data.history, "Custom IOperationHistory is already defined")
    buildArgs.set(buildArgs.get.copy(history = history))
    this
  }
  def setMonitor(monitor: IProgressMonitor, guard: Boolean = true) = {
    if (guard) assert(buildArgs.get.monitor == null, "IProgressMonitor is already defined")
    buildArgs.set(buildArgs.get.copy(monitor = monitor))
    this
  }
  def setOnScheduled(callback: A => Unit, guard: Boolean = true) = {
    if (guard) assert(buildArgs.get.onScheduled == None, "onScheduled is already defined")
    buildArgs.set(buildArgs.get.copy(onScheduled = Some(callback)))
    this
  }
  def setOnRunning(callback: A => Unit, guard: Boolean = true) = {
    if (guard) assert(buildArgs.get.onRunning == None, "onRunning is already defined")
    buildArgs.set(buildArgs.get.copy(onRunning = Some(callback)))
    this
  }
  def setOnSucceeded(callback: A => Unit, guard: Boolean = true) = {
    if (guard) assert(buildArgs.get.onSucceeded == None, "onSucceeded is already defined")
    buildArgs.set(buildArgs.get.copy(onSucceeded = Some(callback)))
    this
  }
  def setOnCancelled(callback: A => Unit, guard: Boolean = true) = {
    if (guard) assert(buildArgs.get.onCancelled == None, "onCancelled is already defined")
    buildArgs.set(buildArgs.get.copy(onCancelled = Some(callback)))
    this
  }
  def setOnFailed(callback: A => Unit, guard: Boolean = true) = {
    if (guard) assert(buildArgs.get.onFailed == None, "onFailed is already defined")
    buildArgs.set(buildArgs.get.copy(onFailed = Some(callback)))
    this
  }
}

object JobBuilder {
  case class Args[A](
    /**
     * IOperationHistory tracks a history of operations that can be undone or
     * redone. Operations are added to the history once they have been initially
     * executed. Clients may choose whether to have the operations history perform
     * the initial execution or to simply add an already-executed operation to the
     * history.
     */
    val history: IOperationHistory = Data.history,
    /** An interface for an adaptable object. */
    val info: IAdaptable = null,
    /**
     * The <code>IProgressMonitor</code> interface is implemented
     * by objects that monitor the progress of an activity; the methods
     * in this interface are invoked by code that performs the activity.
     */
    val monitor: IProgressMonitor = null,
    /** The onSchedule event handler is called whenever the Task state transitions to the SCHEDULED state. */
    val onScheduled: Option[A => Unit] = None,
    /** The onRunning event handler is called whenever the Task state transitions to the RUNNING state. */
    val onRunning: Option[A => Unit] = None,
    /** The onSucceeded event handler is called whenever the Task state transitions to the SUCCEEDED state. */
    val onSucceeded: Option[A => Unit] = None,
    /** The onCancelled event handler is called whenever the Task state transitions to the CANCELLED state. */
    val onCancelled: Option[A => Unit] = None,
    /** The onFailed event handler is called whenever the Task state transitions to the FAILED state. */
    val onFailed: Option[A => Unit] = None)
}
