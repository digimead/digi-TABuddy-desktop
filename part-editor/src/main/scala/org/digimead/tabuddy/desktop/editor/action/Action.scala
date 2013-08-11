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

package org.digimead.tabuddy.desktop.editor.action

import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop
import org.digimead.tabuddy.desktop.logic
import org.digimead.tabuddy.desktop.gui.WindowMenu
import org.digimead.tabuddy.desktop.gui.WindowToolbar
import org.digimead.tabuddy.desktop.gui.widget.AppWindow
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.App.app2implementation

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props

/**
 * Register action in new windows.
 * There is no need subscribe to App.Message.Destroyed because SWT dispose will do all job.
 */
class Action extends Actor with Loggable {
  log.debug("Start actor " + self.path)

  /*
   * Editor component action actors.
   */
  val collapseAllActionRef = context.actorOf(CollapseAll.props, CollapseAll.id)
  val elementDeleteActionRef = context.actorOf(ElementDelete.props, ElementDelete.id)
  val elementEditActionRef = context.actorOf(ElementEdit.props, ElementEdit.id)
  val elementNewActionRef = context.actorOf(ElementNew.props, ElementNew.id)
  val expandAllActionRef = context.actorOf(ExpandAll.props, ExpandAll.id)
  val toggleEmptyActionRef = context.actorOf(ToggleEmpty.props, ToggleEmpty.id)
  val toggleIdentificatorsActionRef = context.actorOf(ToggleIdentificators.props, ToggleIdentificators.id)

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
    // Adjust menu and toolbar after Logic component.
    case message @ App.Message.Create(Right((action: logic.action.Action.type, window: AppWindow)), Some(publisher)) => App.traceMessage(message) {
      onCreated(window, publisher)
    }

    case message @ App.Message.Create(_, _) =>
  }

  /** Register actions in new window. */
  protected def onCreated(window: AppWindow, sender: ActorRef) = {
    // block actor
    App.execNGet {
      log.debug(s"Update window ${window} composite.")
      adjustMenu(window)
      adjustToolbar(window)
    }
    // publish global that window menu and toolbar are ready
    App.publish(App.Message.Create(Right(Action, window), self))
  }
  /** Adjust window menu. */
  @log
  protected def adjustMenu(window: AppWindow) {
    //val file = WindowMenu(window, desktop.action.Action.fileMenu)
    //    file.add(Save())
    //    file.add(Delete())
    //    file.add(Close())
  }
  /** Adjust window toolbar. */
  @log
  protected def adjustToolbar(window: AppWindow) {
    val editorToolbar = WindowToolbar(window, Action.editorToolbar)
    editorToolbar.getToolBarManager().add(ToggleIdentificators())
    editorToolbar.getToolBarManager().add(ToggleEmpty())
    editorToolbar.getToolBarManager().add(ExpandAll())
    editorToolbar.getToolBarManager().add(CollapseAll())
    val elementToolbar = WindowToolbar(window, Action.elementToolbar)
    elementToolbar.getToolBarManager().add(ElementNew())
    elementToolbar.getToolBarManager().add(ElementEdit())
    elementToolbar.getToolBarManager().add(ElementDelete())
    window.getCoolBarManager2().update(true)
  }
}

object Action {
  /** Singleton identificator. */
  val id = getClass.getSimpleName().dropRight(1)
  /** Editor toolbar descriptor. */
  val editorToolbar = WindowToolbar.Descriptor(getClass.getName() + "#editor")
  /** Element toolbar descriptor. */
  val elementToolbar = WindowToolbar.Descriptor(getClass.getName() + "#element")
  // Initialize descendant actor singletons
  CollapseAll
  ElementDelete
  ElementEdit
  ElementNew
  ExpandAll
  ToggleEmpty
  ToggleIdentificators

  /** Action actor reference configuration object. */
  def props = DI.props

  /**
   * Dependency injection routines.
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** Action actor reference configuration object. */
    lazy val props = injectOptional[Props]("Editor.Action") getOrElse Props[Action]
  }
}