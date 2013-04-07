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

import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import scala.collection.mutable
import scala.collection.mutable.Publisher

import org.digimead.digi.lib.DependencyInjection
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.log.Loggable
import org.digimead.digi.lib.log.logger.RichLogger.rich2slf4j
import org.digimead.tabuddy.desktop.Main
import org.eclipse.core.commands.operations.AbstractOperation
import org.eclipse.core.commands.operations.IOperationHistory
import org.eclipse.core.runtime.IAdaptable
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.Status

import com.escalatesoft.subcut.inject.BindingModule

import language.implicitConversions

abstract class Job[A](
  /** The property representing the job label. */
  protected var label: String) extends AbstractOperation("") with PropertyChangeListener with Loggable {
  /** The job runnable representation */
  @volatile protected var task: Option[JobTask[A]] = None
  /** The property representing the state. */
  @volatile protected var state: Job.State = Job.State.Initialized
  /** The onSchedule event handler is called whenever the Task state transitions to the SCHEDULED state. */
  protected val onScheduled = new InheritableThreadLocal[this.type => Unit]()
  /** The onRunning event handler is called whenever the Task state transitions to the RUNNING state. */
  protected val onRunning = new InheritableThreadLocal[this.type => Unit]()
  /** The onSucceeded event handler is called whenever the Task state transitions to the SUCCEEDED state. */
  protected val onSucceeded = new InheritableThreadLocal[this.type => Unit]()
  /** The onCancelled event handler is called whenever the Task state transitions to the CANCELLED state. */
  protected val onCancelled = new InheritableThreadLocal[this.type => Unit]()
  /** The onFailed event handler is called whenever the Task state transitions to the FAILED state. */
  protected val onFailed = new InheritableThreadLocal[this.type => Unit]()
  /** The property representing the current value. */
  @volatile protected var value: Option[A] = None
  /** The property representing the exception. */
  @volatile protected var exception: Option[Throwable] = None
  /** The property representing the amount of work done. */
  @volatile protected var workDone = null
  /** The property representing the total work to be done. */
  @volatile protected var totalWork = null
  /** The property representing the progress. */
  @volatile protected var progress = null
  /** The support for bound properties */
  protected val pcs = new PropertyChangeSupport(this)
  /** The result concurrent barrier that used in get() methods */
  @volatile protected var valueCountDownLatch = new CountDownLatch(1)
  initialized()

  /**
   * Initiate required operation
   * If state is Job.State.Initialized and direction is Redo - 'execute'
   * If isDone and direction is Redo - 'redo'
   * If isDone and direction is Undo - 'undo'
   *
   * @return true if we able to initiate required operation
   */
  @log
  protected def execute(history: IOperationHistory, monitor: IProgressMonitor, info: IAdaptable, direction: Job.Direction): Boolean = synchronized {
    val result = state match {
      case state: Job.State.Initialized.type =>
        assert(!isRunning, "job already running")
        direction match {
          case Job.Direction.Redo if canExecute =>
            state.next(this, Some(true), Job.State.ScheduledArgument[A](new Job.Callable[Job.Result[A]](direction) {
              def call = {
                history.execute(Job.this, monitor, info) match {
                  case r: Job.Result[A] => r
                  case Status.CANCEL_STATUS =>
                    log.info("execution canceled by an approver")
                    Job.Result.Cancel("operation cancel by an approver")
                  case r =>
                    val msg = "execution return unknown status " + r
                    log.fatal(msg)
                    throw new IllegalArgumentException()
                }
              }
            }))
            true
          case Job.Direction.Redo =>
            log.info("execution disabled")
            false
          case Job.Direction.Undo =>
            log.error("unable to undo while job in " + state)
            false
        }
      case state if Seq(Job.State.Succeeded, Job.State.Cancelled, Job.State.Failed).contains(state) =>
        assert(!isRunning, "job already running")
        direction match {
          case Job.Direction.Redo if canRedo =>
            state.reset(this)
            getState.asInstanceOf[Job.State.Initialized.type].next(this, Some(true), Job.State.ScheduledArgument[A](new Job.Callable[Job.Result[A]](direction) {
              def call = {
                history.redoOperation(Job.this, monitor, info) match {
                  case r: Job.Result[A] => r
                  case Status.CANCEL_STATUS =>
                    log.info("execution canceled by an approver")
                    Job.Result.Cancel("operation cancel by an approver")
                  case r =>
                    val msg = "execution return unknown status " + r
                    log.fatal(msg)
                    throw new IllegalArgumentException()
                }
              }
            }))
            true
          case Job.Direction.Redo =>
            log.info("redo disabled")
            false
          case Job.Direction.Undo =>
            log.error("unable to undo while job in " + state)
            false
        }
        false
      case state =>
        log.warn("unable to execute while job in " + state)
        false
    }
    result
  }

  def getDirection() = task.map(_.direction)
  def isCancelled() = state == Job.State.Cancelled || task.map(_.isCancelled()).getOrElse(false)
  def isDone() = Seq(Job.State.Succeeded, Job.State.Cancelled, Job.State.Failed).contains(getState) || task.map(_.isDone()).getOrElse(true)
  def isRunning() = state == Job.State.Running || !task.map(_.isDone()).getOrElse(true)
  /*
   * workflow control
   */
  @log
  protected def initialized() = synchronized {
    log.debug(s"$this: initialized")
    // title must be initialized in parent class
    // message must be initialized in parent class
    task = None
    value = None
    exception = None
    workDone = null
    totalWork = null
    progress = null
    if (valueCountDownLatch.getCount() == 0)
      valueCountDownLatch = new CountDownLatch(1)
  }
  @log
  protected def scheduled(callable: Job.Callable[Job.Result[A]]) {
    log.debug(s"$this: scheduled")
    Option(onScheduled.get).foreach(_(this))
    val task = new JobTask[A](callable, onScheduled.get(), onRunning.get(), onSucceeded.get(), onCancelled.get(), onFailed.get())
    // provide a suitable unhandled exception support
    // reimplement from scratch ScheduledThreadPoolExecutor.ScheduledFutureTask is not necessary
    // possible a race condition: the job will be finished before we add a new value to futureMap
    // so we have an ugly guard about 100ms
    Job.futureMap(Job.executor.submit(task)) = task
  }
  @log
  protected def running() {
    log.debug(s"$this: running")
    Option(onRunning.get).foreach(_(this))
  }
  @log
  protected def succeeded() {
    log.debug(s"$this: succeeded")
    Option(onSucceeded.get).foreach(_(this))
    Job.Event.publish(Job.Event.Succeeded(this))
  }
  @log
  protected def cancelled() {
    log.debug(s"$this: cancelled")
    Option(onCancelled.get).foreach(_(this))
    Job.Event.publish(Job.Event.Cancelled(this))
  }
  @log
  protected def failed() {
    log.debug(s"$this: failed")
    Option(onFailed.get).foreach(_(this))
    Job.Event.publish(Job.Event.Failed(this))
  }
  /*
   * FutureTask routines
   */
  /** Attempts to cancel execution of this task. */
  def cancel(): Boolean = cancel(true)
  /** Attempts to cancel execution of this task. */
  def cancel(mayInterruptIfRunning: Boolean): Boolean = synchronized {
    task.map(_.cancel(mayInterruptIfRunning)) getOrElse false

  }
  /** Waits if necessary for the computation to complete, and then retrieves its result. */
  def get(): Option[A] = {
    valueCountDownLatch.await()
    value
  }
  /** Waits if necessary for at most the given time for the computation to complete, and then retrieves its result, if available. */
  def get(timeout: Long, unit: TimeUnit): Option[A] = {
    valueCountDownLatch.await(timeout, unit)
    value
  }
  /*
   * UndoableOperation routines
   */
  protected def execute(monitor: IProgressMonitor, info: IAdaptable): Job.Result[A] = redo(monitor, info)
  protected def redo(monitor: IProgressMonitor, info: IAdaptable): Job.Result[A]
  protected def undo(monitor: IProgressMonitor, info: IAdaptable): Job.Result[A]
  /*
   * Accessors
   */
  def getException() = exception
  protected def setException(newValue: Option[Throwable]) = if (exception != newValue) {
    pcs.firePropertyChange("exception", exception, newValue)
    exception = newValue
  }
  override def getLabel() = label
  override protected def setLabel(newValue: String) = if (label != newValue) {
    pcs.firePropertyChange("label", label, newValue)
    label = newValue
  }
  def getOnScheduled() = onScheduled.get
  def setOnScheduled(newValue: Option[this.type => Unit]) = onScheduled.set(newValue.getOrElse(null))
  def getOnRunning() = onRunning.get
  def setOnRunning(newValue: Option[this.type => Unit]) = onRunning.set(newValue.getOrElse(null))
  def getOnSucceeded() = onSucceeded.get
  def setOnSucceeded(newValue: Option[this.type => Unit]) = onSucceeded.set(newValue.getOrElse(null))
  def getOnCancelled() = onCancelled.get
  def setOnCancelled(newValue: Option[this.type => Unit]) = onCancelled.set(newValue.getOrElse(null))
  def getOnFailed() = onFailed.get
  def setOnFailed(newValue: Option[this.type => Unit]) = onFailed.set(newValue.getOrElse(null))
  def getState() = state
  def setState(newValue: Job.State) = if (state != newValue) {
    pcs.firePropertyChange("state", state, newValue)
    state = newValue
  }
  def getValue(): Option[A] = value
  protected def setValue(newValue: Option[A]) = if (value != newValue) {
    pcs.firePropertyChange("value", value, newValue)
    value = newValue
  }
  /*
   * PropertyChangeListener and PropertyChangeSupport stuff
   */
  override def propertyChange(event: PropertyChangeEvent): Unit = {}
  def addPropertyChangeListener(pcl: PropertyChangeListener) =
    pcs.addPropertyChangeListener(pcl)
  def removePropertyChangeListener(pcl: PropertyChangeListener) =
    pcs.removePropertyChangeListener(pcl)

  override def toString() = "job[\"%s\" %s]".format(getLabel, if (getContexts.isEmpty) "without context" else getContexts.mkString(","))

  class JobTask[B <: A](callable: Job.Callable[Job.Result[B]],
    /** The onSchedule event handler is called whenever the Task state transitions to the SCHEDULED state. */
    onScheduledCache: Job.this.type => Unit,
    /** The onRunning event handler is called whenever the Task state transitions to the RUNNING state. */
    onRunningCache: Job.this.type => Unit,
    /** The onSucceeded event handler is called whenever the Task state transitions to the SUCCEEDED state. */
    onSucceededCache: Job.this.type => Unit,
    /** The onCancelled event handler is called whenever the Task state transitions to the CANCELLED state. */
    onCancelledCache: Job.this.type => Unit,
    /** The onFailed event handler is called whenever the Task state transitions to the FAILED state. */
    onFailedCache: Job.this.type => Unit) extends FutureTask[Job.Result[B]](callable) {
    /** Runnable direction */
    val direction = callable.direction

    /** run wrapper that provide job state synchronization */
    override def run = {
      // reinitialize thread local variable
      Job.this.onScheduled.set(onScheduledCache)
      Job.this.onRunning.set(onRunningCache)
      Job.this.onSucceeded.set(onSucceededCache)
      Job.this.onCancelled.set(onCancelledCache)
      Job.this.onFailed.set(onFailedCache)
      // Scheduled -> Running
      getState() match {
        case state: Job.State.Scheduled.type =>
          state.next(Job.this, Some(true))
        case state =>
          val msg = "unexpected state %s, expect Job.State.Scheduled".format(state)
          log.fatal(msg)
          throw new IllegalStateException(msg)
      }
      try {
        super.run
      } finally {
        // Running -> ...
        getState() match {
          case state: Job.State.Running.type =>
            if (isCancelled())
              setValue(None, () => state.next(Job.this, None))
            else try {
              this.get() match {
                case Job.Result.OK(result, _) =>
                  setValue(result, () => state.next(Job.this, Some(true)))
                case Job.Result.Cancel(_) =>
                  setValue(None, () => state.next(Job.this, None))
                case error =>
                  setValue(None, () => state.next(Job.this, Some(false))) // lost result
              }
            } catch {
              case e: Throwable =>
            }
          case state =>
          // we may already set new state in setException
        }
      }
    }
    override def setException(t: Throwable) {
      getState() match {
        case state: Job.State.Running.type =>
          log.error("%s: unable to complete, reason: %s".format(this, t), t)
          setValue(None, () => state.next(Job.this, Some(false)))
        case state: Job.State.Scheduled.type =>
          log.error("%s: unable to complete, reason: %s".format(this, t), t)
          setValue(None, () => state.next(Job.this, Some(false)))
        case state =>
          val msg = "%s: unexpected state %s, expect Job.State.Running".format(this, state)
          log.fatal(msg)
          throw new IllegalStateException(msg)
      }
      super.setException(t)
    }
    protected def setValue[T](newValue: Option[B], f: () => T) {
      val oldValue = value
      value = newValue // silently set the value
      // fire event for property listeners
      pcs.firePropertyChange("value", oldValue, newValue)
      // fire event for get listeners
      valueCountDownLatch.countDown()
      // fire event for transition listeners
      f()
      // fire method for subclass
      Job.this.setValue(value)
    }

    override def toString() = "Future Task of " + Job.this
  }
}

object Job extends DependencyInjection.PersistentInjectable with Loggable {
  implicit def job2implementation(j: Job.type): Interface = inner
  implicit def bindingModule = DependencyInjection()

  /*
   * dependency injection
   */
  def inner(): Interface = inject[Interface]
  override def beforeInjection(newModule: BindingModule) {
    DependencyInjection.assertLazy[Interface](None, newModule)
  }

  abstract case class Callable[T](direction: Direction) extends java.util.concurrent.Callable[T]
  sealed trait Direction
  object Direction {
    case object Undo extends Direction
    case object Redo extends Direction
  }
  sealed trait Event
  object Event extends Publisher[Event] {
    override protected[Job] def publish(event: Event) = try {
      super.publish(event)
    } catch {
      // catch all throwable from subscribers
      case e: Throwable =>
        log.error(e.getMessage(), e)
    }
    case class Succeeded[T](job: Job[T]) extends Event
    case class Cancelled[T](job: Job[T]) extends Event
    case class Failed[T](job: Job[T]) extends Event
  }
  /**
   * Global job handler interface
   */
  trait Interface extends Main.Interface {
    /**
     * The job map representing current state per singleton
     */
    val stateMap = new mutable.HashMap[AnyRef, Job[_]] with mutable.SynchronizedMap[AnyRef, Job[_]]
    /**
     * The job map representing current state per singleton
     */
    val futureMap = new mutable.WeakHashMap[Future[_], FutureTask[_]] with mutable.SynchronizedMap[Future[_], FutureTask[_]]
    /**
     * The job task executor
     */
    val executor: ScheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(16, new ThreadFactory {
      val threadNumber = new AtomicInteger(1)
      val namePrefix = "job-pool-thread-"
      def newThread(runnable: Runnable): Thread = {
        val thread = new Thread(runnable, namePrefix + threadNumber.getAndIncrement())
        if (thread.isDaemon())
          thread.setDaemon(false)
        if (thread.getPriority() != Thread.NORM_PRIORITY)
          thread.setPriority(Thread.NORM_PRIORITY)
        thread
      }
    }) {
      /** Intercept an unhandled exception */
      override protected def afterExecute(r: Runnable, t: Throwable) {
        super.afterExecute(r, t)
        if (t == null)
          r match {
            case jobTaskFuture: Future[_] => try {
              if (jobTaskFuture.isDone())
                jobTaskFuture.get()
            } catch {
              case ce: CancellationException =>
                val job = Job.futureMap.get(jobTaskFuture)
                if (job.isEmpty)
                  Thread.sleep(100)
                job.foreach(_ match {
                  case jobTask: Job[_]#JobTask[_] => jobTask.setException(ce)
                  case task => log.fatal("Unknown task " + task)
                })
              case ee: ExecutionException =>
                val job = Job.futureMap.get(jobTaskFuture)
                if (job.isEmpty)
                  Thread.sleep(100)
                job.foreach(_ match {
                  case jobTask: Job[_]#JobTask[_] => jobTask.setException(ee.getCause())
                  case task => log.fatal("Unknown task " + task)
                })
              case ie: InterruptedException =>
                val job = Job.futureMap.get(jobTaskFuture)
                if (job.isEmpty)
                  Thread.sleep(100)
                job.foreach(_ match {
                  case jobTask: Job[_]#JobTask[_] => jobTask.setException(ie)
                  case task => log.fatal("Unknown task " + task)
                })
                Thread.currentThread().interrupt()
              // ignore rest
            }
            case future =>
              log.fatal("Unknown runnable " + future)
          }
      }
    }
    /**
     * Default empty IAdaptable info
     */
    val emptyInfo = new IAdaptable {
      def getAdapter(adapter: Class[_]) = null
    }
    /**
     * Default empty IProgressMonitor monitor
     */
    val emptyMonitor = new IProgressMonitor {
      def beginTask(name: String, totalWork: Int) {}
      def done() {}
      def internalWorked(work: Double) {}
      def isCanceled(): Boolean = false
      def setCanceled(value: Boolean) {}
      def setTaskName(name: String) {}
      def subTask(name: String) {}
      def worked(work: Int) {}
    }

    /**
     * This function is invoked at application start
     */
    def start() {
      stateMap.clear
    }
    /**
     * This function is invoked at application stop
     */
    def stop() {
      executor.shutdown()
      stateMap.clear
    }
  }
  /**
   * Job builder
   */
  trait Builder[A <: Job[_]] extends Loggable {
    /** A unique singleton that provide the synchronization over a group of the same jobs */
    protected val anchor: AnyRef
    /** Thread local arguments */
    protected val buildArgs: ThreadLocal[JobBuilder.Args[A]]
    /** Create the new job instance */
    protected val createJob: () => A

    /**
     * Initiate execute operation
     * Create a new job if there is no the previous job or the job is complete
     * Use the exists job if the such job is in progress
     *
     * @return true if we able to initiate execute operation
     */
    def execute(): Boolean = {
      val job = Job.stateMap.get(anchor) match {
        case Some(job) if !job.isDone =>
          job
        case _ =>
          val job = createJob()
          Job.stateMap(anchor) = job
          job
      }
      if (!job.canExecute()) {
        log.warn(s"$job: execution is prohibited")
        return false
      }
      if (!job.isDone() || job.getState != Job.State.Initialized) {
        log.warn(s"$job: the job are executing")
        return false
      }
      job.setOnCancelled(buildArgs.get.onCancelled.asInstanceOf[Option[job.type => Unit]])
      job.setOnFailed(buildArgs.get.onFailed.asInstanceOf[Option[job.type => Unit]])
      job.setOnRunning(buildArgs.get.onRunning.asInstanceOf[Option[job.type => Unit]])
      job.setOnScheduled(buildArgs.get.onScheduled.asInstanceOf[Option[job.type => Unit]])
      job.setOnSucceeded(buildArgs.get.onSucceeded.asInstanceOf[Option[job.type => Unit]])
      job.execute(buildArgs.get.history, buildArgs.get.monitor, buildArgs.get.info, Direction.Redo)
      true
    }
    def setAdaptable(info: IAdaptable, guard: Boolean = true): this.type
    def setMonitor(monitor: IProgressMonitor, guard: Boolean = true): this.type
    def setOnScheduled(callback: A => Unit, guard: Boolean = true): this.type
    def setOnRunning(callback: A => Unit, guard: Boolean = true): this.type
    def setOnSucceeded(callback: A => Unit, guard: Boolean = true): this.type
    def setOnCancelled(callback: A => Unit, guard: Boolean = true): this.type
    def setOnFailed(callback: A => Unit, guard: Boolean = true): this.type
    /**
     * Initiate redo operation
     *
     * @return true if we able to initiate execute operation
     */
    def redo(): Boolean = Job.stateMap.get(anchor).map {
      case job =>
        if (!job.canRedo()) {
          log.warn(s"$job: the redo is prohibited")
          return false
        }
        if (!job.isDone()) {
          log.warn(s"$job: the job are executing")
          return false
        }
        val x =
          job.setOnCancelled(buildArgs.get.onCancelled.asInstanceOf[Option[job.type => Unit]])
        job.setOnFailed(buildArgs.get.onFailed.asInstanceOf[Option[job.type => Unit]])
        job.setOnRunning(buildArgs.get.onRunning.asInstanceOf[Option[job.type => Unit]])
        job.setOnScheduled(buildArgs.get.onScheduled.asInstanceOf[Option[job.type => Unit]])
        job.setOnSucceeded(buildArgs.get.onSucceeded.asInstanceOf[Option[job.type => Unit]])
        job.execute(buildArgs.get.history, buildArgs.get.monitor, buildArgs.get.info, Direction.Redo)
    }.getOrElse {
      log.fatal("The job instance is lost")
      false
    }
    /**
     * Initiate undo operation
     *
     * @return true if we able to initiate execute operation
     */
    def undo(): Boolean = Job.stateMap.get(anchor).map { job =>
      if (!job.canUndo()) {
        log.warn(s"$job: the undo is prohibited")
        return false
      }
      if (!job.isDone()) {
        log.warn(s"$job: the job are executing")
        return false
      }
      job.setOnCancelled(buildArgs.get.onCancelled.asInstanceOf[Option[job.type => Unit]])
      job.setOnFailed(buildArgs.get.onFailed.asInstanceOf[Option[job.type => Unit]])
      job.setOnRunning(buildArgs.get.onRunning.asInstanceOf[Option[job.type => Unit]])
      job.setOnScheduled(buildArgs.get.onScheduled.asInstanceOf[Option[job.type => Unit]])
      job.setOnSucceeded(buildArgs.get.onSucceeded.asInstanceOf[Option[job.type => Unit]])
      job.execute(buildArgs.get.history, buildArgs.get.monitor, buildArgs.get.info, Direction.Undo)
    }.getOrElse {
      log.fatal("The job instance is lost")
      false
    }
  }
  /**
   * Job result
   */
  sealed trait Result[T] extends IStatus {
    val exception: Throwable
    val message: String
    val result: Option[T]
    val severity: Int

    /** Returns the operation/job result */
    def get(): Option[T] = result
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
    case class Cancel[T](val message: String = "operation cancel") extends Result[T] {
      val exception = null
      val result = None
      val severity = IStatus.CANCEL
    }
    case class Error[T](val message: String, logAsFatal: Boolean = true) extends Result[T] {
      val exception = null
      val result = None
      val severity = IStatus.ERROR

      if (logAsFatal)
        log.fatal(message)
      else
        log.warn(message)
    }
    case class Exception[T](override val exception: Throwable, logAsFatal: Boolean = true) extends Result[T] {
      val message = "Error: " + exception
      val severity = IStatus.ERROR
      val result = None

      if (logAsFatal)
        log.error(exception.toString(), exception)
      else
        log.warn(exception.toString())
    }
    case class OK[T](val result: Option[T] = None, val message: String = "operation complete") extends Result[T] {
      val severity = IStatus.OK
      val exception = null
    }
  }
  /**
   * FSA job life cycle state trait
   */
  sealed trait State {
    def reset(job: Job[_]) = job.synchronized {
      job.setState(State.Initialized)
      job.initialized()
    }
  }
  object State {
    trait TransitionArgument
    object Initialized extends State {
      def next[B](job: Job[B], previousStateCompleted: Option[Boolean], nextPrimaryStateArgument: ScheduledArgument[B]) = job.synchronized {
        previousStateCompleted match {
          case Some(true) =>
            job.setState(Scheduled)
            job.scheduled(nextPrimaryStateArgument.callable)
          case Some(false) =>
            job.setState(Failed)
            job.failed()
          case None =>
            job.setState(Cancelled)
            job.cancelled()
        }
      }
      override def toString = "State.Initialized"
    }
    case class ScheduledArgument[T](callable: Callable[Result[T]]) extends State.TransitionArgument
    object Scheduled extends State {
      def next(job: Job[_], previousStateCompleted: Option[Boolean]) = job.synchronized {
        previousStateCompleted match {
          case Some(true) =>
            job.setState(Running)
            job.running()
          case Some(false) =>
            job.setState(Failed)
            job.failed()
          case None =>
            job.setState(Cancelled)
            job.cancelled()
        }
      }
      override def toString = "State.Scheduled"
    }
    object Running extends State {
      def next(job: Job[_], previousStateCompleted: Option[Boolean]) = job.synchronized {
        previousStateCompleted match {
          case Some(true) =>
            job.setState(Succeeded)
            job.succeeded()
          case Some(false) =>
            job.setState(Failed)
            job.failed()
          case None =>
            job.setState(Cancelled)
            job.cancelled()
        }
      }
      override def toString = "State.Running"
    }
    object Succeeded extends State {
      override def toString = "State.Succeeded"
    }
    object Cancelled extends State {
      override def toString = "State.Cancelled"
    }
    object Failed extends State {
      override def toString = "State.Failed"
    }
  }
}
