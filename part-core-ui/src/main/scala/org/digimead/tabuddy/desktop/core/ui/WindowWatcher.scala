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

package org.digimead.tabuddy.desktop.core.ui

import akka.actor.{ Actor, ActorRef, Props }
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.XDependencyInjection
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.support.Timeout
import org.digimead.tabuddy.desktop.core.ui.block.{ Configuration, SmartMenuManager, SmartToolbarManager }
import org.digimead.tabuddy.desktop.core.ui.definition.widget.AppWindow
import org.digimead.tabuddy.desktop.core.ui.operation.OperationViewCreate
import org.eclipse.core.runtime.jobs.Job
import org.eclipse.e4.core.contexts.ContextInjectionFactory
import org.eclipse.jface.action.{ Action, ActionContributionItem, IContributionItem, Separator }
import org.eclipse.ui.actions.CompoundContributionItem

/**
 * Register action in new windows.
 * There is no need subscribe to App.Message.Destroyed because SWT dispose will do all job.
 */
class WindowWatcher extends Actor with XLoggable {
  log.debug("Start actor " + self.path)

  /** Is called asynchronously after 'actor.stop()' is invoked. */
  override def postStop() = {
    App.system.eventStream.unsubscribe(self, classOf[App.Message.Create[_]])
    log.debug(self.path.name + " actor is stopped.")
  }
  /** Is called when an Actor is started. */
  override def preStart() {
    App.system.eventStream.subscribe(self, classOf[App.Message.Create[_]])
    log.debug(self.path.name + " actor is started.")
  }
  def receive = {
    case message @ App.Message.Create(window: AppWindow, Some(publisher), _) ⇒ App.traceMessage(message) {
      onCreated(window, publisher)
    }

    case message @ App.Message.Create(_, _, _) ⇒
  }

  /** Register actions in new window. */
  protected def onCreated(window: AppWindow, sender: ActorRef) = {
    // block actor
    App.execNGet {
      log.debug(s"Update window ${window} composite.")
      adjustMenu(window)
      adjustToolbar(window)
    }
    // publish that window menu and toolbar are ready
    App.publish(App.Message.Create((WindowWatcher, window), self))
  }
  /** Adjust window menu. */
  @log
  protected def adjustMenu(window: AppWindow) {
    val file = SmartMenuManager(window, WindowWatcher.fileMenu)
    SmartMenuManager.add(file, action.ActionNewWindow)
    SmartMenuManager.add(file, action.ActionExit)
    SmartMenuManager.add(file, ContextInjectionFactory.make(classOf[action.ActionPreferences], window.windowContext))
    val showView = SmartMenuManager(file, WindowWatcher.showViewMenu)
    SmartMenuManager.add(showView, new WindowWatcher.ListView)
    window.getMenuBarManager().update(true)
  }
  /** Adjust window toolbar. */
  @log
  protected def adjustToolbar(window: AppWindow) {
    val commonToolBar = SmartToolbarManager(window, WindowWatcher.commonToolbar)
    SmartToolbarManager.add(commonToolBar, action.ActionExit)
    SmartToolbarManager.add(commonToolBar, action.ActionTest)
    window.getCoolBarManager2().update(true)
  }
}

object WindowWatcher extends XLoggable {
  /** Singleton identificator. */
  val id = getClass.getSimpleName().dropRight(1)
  /** Common toolbar descriptor. */
  val commonToolbar = SmartToolbarManager.Descriptor(getClass.getName() + "#common")
  /** File menu descriptor. */
  val fileMenu = SmartMenuManager.Descriptor("&File", None, getClass.getName() + "#file")
  /** Show View menu descriptor. */
  val showViewMenu = SmartMenuManager.Descriptor("Show &View", None, getClass.getName() + "#showView")

  /** Core actor reference configuration object. */
  def props = DI.props

  override def toString = "WindowWatcher[Singleton]"

  class ListView extends CompoundContributionItem {
    override protected def getContributionItems(): Array[IContributionItem] = {
      Resources.factories.toSeq.sortBy(_._1.name.name).flatMap {
        case (factory, true) ⇒
          Some(new ActionContributionItem(new Action(factory.name.name) {
            override def run = {
              UI.getActiveWindow() match {
                case Some(window) ⇒
                  OperationViewCreate(window.id, Configuration.CView(factory.configuration)).foreach { operation ⇒
                    operation.getExecuteJob() match {
                      case Some(job) ⇒
                        job.setPriority(Job.LONG)
                        job.schedule(Timeout.shortest.toMillis)
                      case None ⇒
                        log.fatal(s"Unable to create job for ${operation}.")
                    }
                  }
                case None ⇒
                  log.fatal("Unable to find active window.")
              }
            }
          }))
        case _ ⇒
          None
      }.toArray
    }
  }
  /**
   * Dependency injection routines.
   */
  private object DI extends XDependencyInjection.PersistentInjectable {
    /** WindowWatcher actor reference configuration object. */
    lazy val props = injectOptional[Props]("Core.UI.WindowWatcher") getOrElse Props[WindowWatcher]
  }
}
