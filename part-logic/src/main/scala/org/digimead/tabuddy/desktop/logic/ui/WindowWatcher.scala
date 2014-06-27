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

package org.digimead.tabuddy.desktop.logic.ui

import akka.actor.{ Actor, ActorRef, Props }
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.XDependencyInjection
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.ui
import org.digimead.tabuddy.desktop.core.ui.block.{ WindowMenu, WindowToolbar }
import org.digimead.tabuddy.desktop.core.ui.definition.widget.AppWindow
import org.eclipse.e4.core.contexts.ContextInjectionFactory
import org.eclipse.jface.action.Separator

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
    // Adjust menu and toolbar after Core component.
    case message @ App.Message.Create((ui.WindowWatcher, window: AppWindow), Some(publisher), _) ⇒ App.traceMessage(message) {
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
    val file = WindowMenu(Left(window), ui.WindowWatcher.fileMenu)
    //    file.add(action.ActionSaveModel())
    //    file.add(action.ActionDeleteModel())
    //file.add(action.ActionCloseGraph())
    file.add(ContextInjectionFactory.make(classOf[action.ActionGraphNew], window.windowContext))
    file.add(ContextInjectionFactory.make(classOf[action.ActionGraphOpen], window.windowContext))
    file.add(new Separator())
    file.add(ContextInjectionFactory.make(classOf[action.ActionGraphClose], window.windowContext))
    file.add(ContextInjectionFactory.make(classOf[action.ActionGraphCloseAll], window.windowContext))
    file.add(new Separator())
    file.add(ContextInjectionFactory.make(classOf[action.ActionGraphSave], window.windowContext))
    file.add(ContextInjectionFactory.make(classOf[action.ActionGraphSaveAll], window.windowContext))
    file.add(new Separator())
    file.add(ContextInjectionFactory.make(classOf[action.ActionGraphImport], window.windowContext))
    file.add(ContextInjectionFactory.make(classOf[action.ActionGraphExport], window.windowContext))
    file.add(new Separator())
    file.add(ContextInjectionFactory.make(classOf[action.ActionGraphProperties], window.windowContext))
    file.add(new Separator())
    window.getMenuBarManager().update(true)
  }
  /** Adjust window toolbar. */
  @log
  protected def adjustToolbar(window: AppWindow) {
    val modelToolBar = WindowToolbar(window, WindowWatcher.modelToolbar)
    //    modelToolBar.getToolBarManager().add(new ContributionSelectModel)
    //    modelToolBar.getToolBarManager().add(WindowWatcherLockModel())
    //    modelToolBar.getToolBarManager().add(WindowWatcherSaveModel())
    //    modelToolBar.getToolBarManager().add(WindowWatcherDeleteModel())
    window.getCoolBarManager2().update(true)
  }
}

object WindowWatcher {
  /** Singleton identificator. */
  val id = getClass.getSimpleName().dropRight(1)
  /** Model menu descriptor. */
  lazy val modelMenu = App.execNGet { WindowMenu.Descriptor("&Model", None, getClass.getName() + "#model") }
  /** Model toolbar descriptor. */
  lazy val modelToolbar = App.execNGet { WindowToolbar.Descriptor(getClass.getName() + "#model") }
  /** View menu descriptor. */
  lazy val viewMenu = App.execNGet { WindowMenu.Descriptor("&View", None, getClass.getName() + "#view") }
  // Initialize descendant actor singletons
  //  WindowWatcherCloseModel
  //  WindowWatcherDeleteModel
  //  WindowWatcherLockModel
  //  WindowWatcherSaveModel

  /** WindowWatcher actor reference configuration object. */
  def props = DI.props

  /**
   * Dependency injection routines.
   */
  private object DI extends XDependencyInjection.PersistentInjectable {
    /** WindowWatcher actor reference configuration object. */
    lazy val props = injectOptional[Props]("Logic.WindowWatcher") getOrElse Props[WindowWatcher]
  }
}

