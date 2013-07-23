/**
 * This file is part of the TABuddy project.
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

package org.digimead.tabuddy.desktop.b4e

import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.perspective.DefaultPerspective
import org.digimead.tabuddy.desktop.support.App
import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.Status
import org.eclipse.ui.application.IWorkbenchConfigurer
import org.eclipse.ui.application.IWorkbenchWindowConfigurer
import org.eclipse.ui.application.WorkbenchWindowAdvisor
import org.eclipse.ui.application.{ WorkbenchAdvisor => EWorkbenchAdvisor }
import org.eclipse.ui.internal.WorkbenchPlugin
import org.eclipse.ui.statushandlers.StatusManager

/**
 * The workbench advisor object is used to configure the workbench and pass events to Akka.
 */
class WorkbenchAdvisor extends EWorkbenchAdvisor with Loggable {
  /**
   * Creates a new workbench window advisor for configuring a new workbench
   * window via the given workbench window configurer. Clients should override
   * to provide their own window configurer. This method replaces all the
   * other window and action bar lifecycle methods on the workbench advisor.
   * <p>
   * The default implementation creates a window advisor that calls back to
   * the legacy window and action bar lifecycle methods on the workbench
   * advisor, for backwards compatibility with 3.0.
   * </p>
   *
   * @param configurer
   *            the workbench window configurer
   * @return a new workbench window advisor
   * @since 3.1
   */
  override def createWorkbenchWindowAdvisor(configurer: IWorkbenchWindowConfigurer): WorkbenchWindowAdvisor =
    new WindowAdvisor(configurer)
  /**
   * Performs arbitrary actions when the event loop crashes (the code that
   * handles a UI event throws an exception that is not caught).
   * <p>
   * This method is called when the code handling a UI event throws an
   * exception. In a perfectly functioning application, this method would
   * never be called. In practice, it comes into play when there are bugs in
   * the code that trigger unchecked runtime exceptions. It is also activated
   * when the system runs short of memory, etc. Fatal errors (ThreadDeath) are
   * not passed on to this method, as there is nothing that could be done.
   * </p>
   * <p>
   * Clients must not call this method directly (although super calls are
   * okay). The default implementation logs the problem so that it does not go
   * unnoticed. Subclasses may override or extend this method. It is generally
   * a bad idea to override with an empty method, and you should be especially
   * careful when handling Errors.
   * </p>
   *
   * @param exception
   *            the uncaught exception that was thrown inside the UI event
   *            loop
   */
  override def eventLoopException(exception: Throwable) {
    // Protection from client doing super(null) call
    if (exception == null)
      return

    try {
      StatusManager.getManager().handle(
        new Status(IStatus.ERROR, WorkbenchPlugin.PI_WORKBENCH,
          "Unhandled event loop exception", exception)) //$NON-NLS-1$
      // log.error(exception.getMessage, exception) -=> already caught by default handler
    } catch {
      case e: Throwable =>
        log.error(exception.getMessage, exception)
    }
  }
  /**
   * Returns the id of the perspective to use for the initial workbench
   * window, or <code>null</code> if no initial perspective should be shown
   * in the initial workbench window.
   * <p>
   * This method is called during startup when the workbench is creating the
   * first new window. Subclasses must implement.
   * </p>
   * <p>
   * If the {@link IWorkbenchPreferenceConstants#DEFAULT_PERSPECTIVE_ID}
   * preference is specified, it supercedes the perspective specified here.
   * </p>
   *
   * @return the id of the perspective for the initial window, or
   *         <code>null</code> if no initial perspective should be shown
   */
  def getInitialWindowPerspectiveId(): String =
    classOf[DefaultPerspective].getName

  /**
   * Performs arbitrary initialization before the workbench starts running.
   * <p>
   * This method is called during workbench initialization prior to any
   * windows being opened. Clients must not call this method directly
   * (although super calls are okay). The default implementation does nothing.
   * Subclasses may override. Typical clients will use the configurer passed
   * in to tweak the workbench. If further tweaking is required in the future,
   * the configurer may be obtained using <code>getWorkbenchConfigurer</code>.
   * </p>
   *
   * @param configurer
   *            an object for configuring the workbench
   */
  @log
  override def initialize(configurer: IWorkbenchConfigurer) {
    log.debug(s"Initialization.")
  }
  /**
   * Performs arbitrary actions just before the first workbench window is
   * opened (or restored).
   * <p>
   * This method is called after the workbench has been initialized and just
   * before the first window is about to be opened. Clients must not call this
   * method directly (although super calls are okay). The default
   * implementation does nothing. Subclasses may override.
   * </p>
   */
  @log
  override def preStartup() {
    App.publish(WorkbenchAdvisor.Message.PreStartup(getWorkbenchConfigurer()))
    super.preStartup()
  }
  /**
   * Performs arbitrary actions after the workbench windows have been opened
   * (or restored), but before the main event loop is run.
   * <p>
   * This method is called just after the windows have been opened. Clients
   * must not call this method directly (although super calls are okay). The
   * default implementation does nothing. Subclasses may override. It is okay
   * to call <code>IWorkbench.close()</code> from this method.
   * </p>
   */
  @log
  override def postStartup() {
    App.publish(WorkbenchAdvisor.Message.PostStartup(getWorkbenchConfigurer()))
    super.postStartup()
  }
  /**
   * Performs arbitrary finalization before the workbench is about to shut
   * down.
   * <p>
   * This method is called immediately prior to workbench shutdown before any
   * windows have been closed. Clients must not call this method directly
   * (although super calls are okay). The default implementation returns
   * <code>true</code>. Subclasses may override.
   * </p>
   * <p>
   * The advisor may veto a regular shutdown by returning <code>false</code>,
   * although this will be ignored if the workbench is being forced to shut
   * down.
   * </p>
   *
   * @return <code>true</code> to allow the workbench to proceed with
   *         shutdown, <code>false</code> to veto a non-forced shutdown
   */
  @log
  override def preShutdown() = {
    App.publish(WorkbenchAdvisor.Message.PreShutdown(getWorkbenchConfigurer()))
    super.preShutdown
  }
  /**
   * Performs arbitrary finalization after the workbench stops running.
   * <p>
   * This method is called during workbench shutdown after all windows have
   * been closed. Clients must not call this method directly (although super
   * calls are okay). The default implementation does nothing. Subclasses may
   * override.
   * </p>
   */
  @log
  override def postShutdown() {
    App.publish(WorkbenchAdvisor.Message.PostShutdown(getWorkbenchConfigurer()))
    super.postShutdown()
  }
}

object WorkbenchAdvisor {
  sealed trait Message extends App.Message {
    /**
     * Workbench configurer object that is provide special access for configuring the workbench.
     */
    val configurer: IWorkbenchConfigurer
  }
  object Message {
    /** Performs arbitrary initialization before the workbench starts running. */
    case class Initialize(val configurer: IWorkbenchConfigurer) extends Message
    /** Performs arbitrary actions just before the first workbench window is opened (or restored). */
    case class PreStartup(val configurer: IWorkbenchConfigurer) extends Message
    /**
     * Performs arbitrary actions after the workbench windows have been opened
     * (or restored), but before the main event loop is run.
     */
    case class PostStartup(val configurer: IWorkbenchConfigurer) extends Message
    // Throw ExecutionException to prohibit
    /** Performs arbitrary finalization before the workbench is about to shut down. */
    case class PreShutdown(val configurer: IWorkbenchConfigurer) extends Message
    /** Performs arbitrary finalization after the workbench stops running. */
    case class PostShutdown(val configurer: IWorkbenchConfigurer) extends Message
  }
}
