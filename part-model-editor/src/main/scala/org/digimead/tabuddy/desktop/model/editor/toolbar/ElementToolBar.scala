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

package org.digimead.tabuddy.desktop.model.editor.toolbar

import scala.collection.mutable

import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.Core
import org.digimead.tabuddy.desktop.Resources
import org.digimead.tabuddy.desktop.Resources.resources2implementation
import org.digimead.tabuddy.desktop.model.editor.Editor
import org.digimead.tabuddy.desktop.model.editor.part.MainPart
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.App.app2implementation
import org.digimead.tabuddy.desktop.support.ConditionMap
import org.eclipse.e4.core.contexts.ContextInjectionFactory
import org.eclipse.e4.core.di.annotations.Optional
import org.eclipse.e4.ui.di.UIEventTopic
import org.eclipse.e4.ui.model.application.ui.MUIElement
import org.eclipse.e4.ui.model.application.ui.basic.MPart
import org.eclipse.e4.ui.model.application.ui.basic.MTrimBar
import org.eclipse.e4.ui.model.application.ui.menu.MToolBar
import org.eclipse.e4.ui.workbench.UIEvents
import org.eclipse.jface.action.ToolBarManager
import org.eclipse.swt.widgets.Widget

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.ScalaActorRef
import akka.actor.actorRef2Scala
import javax.inject.Inject

import language.implicitConversions

/**
 * ElementToolBar support class
 */
class ElementToolBar extends Actor with Loggable {
  /** visibleWhen expression. */
  lazy val visibleWhen = new ConditionMap[Boolean] {
    protected def test(map: Map[String, Boolean]) {
   /*   Resources.toolbar(classOf[ElementToolBar].getName()).foreach {
        case (toolbar) =>
          // Yes. We are really have this toolbar in Resources
          val trimBar = Option(toolbar.getParent().asInstanceOf[MTrimBar]) orElse trimBarCache.get(toolbar) match {
            case Some(trimBar) =>
              // Yes. And we have a trimbar.
              trimBar.getWidget() match {
                case widget: Widget if !widget.isDisposed() =>
                  // Yes. The trimbar is valid.
                  App.findShell(widget) match {
                    case Some(toolBarShell) if map.get(toolBarShell.hashCode().toString) == Some(true) =>
                      // Yes. This shell marked as suitable.
                      Option(toolbar.getParent()) match {
                        case Some(parent) if !toolbar.isVisible() =>
                          App.showToolBar(toolbar)
                        case _ => // Skip. Already shown.
                      }
                    case _ =>
                      // No. Toolbar shell marked as disabled or absent.
                      Option(toolbar.getParent()) match {
                        case Some(parent) if toolbar.isVisible() =>
                          App.hideToolBar(toolbar)
                        case _ => // Skip. Already hidden.
                      }
                  }
                case other =>
                  log.debug(s"Skip ${toolbar} with unaccessable trimbar ${trimBar}.")
              }
            case None =>
              log.fatal(s"Unable to find trimbar for ${toolbar}.")
          }
      }*/
    }
  }
  /** Map toolbar -> trimbar */
  protected val trimBarCache = mutable.WeakHashMap[MToolBar, MTrimBar]()
  log.debug("Start actor " + self.path)

  // ACTORS
  /*/** ElementNew handler actor. */
  val elementNewActor = this.context.actorOf(handler.ElementNew.props, handler.ElementNew.id)
  /** ElementEdit handler actor. */
  val elementEditActor = this.context.actorOf(handler.ElementEdit.props, handler.ElementEdit.id)
  /** ElementDelete handler actor. */
  val elementDeleteActor = this.context.actorOf(handler.ElementDelete.props, handler.ElementDelete.id)*/

  /** Is called when an Actor is started. Actors are automatically started asynchronously when created. */
  @log
  override def preStart() {
    super.preStart()
    //Resources.Message.subscribe(this)
  }
  /** Is called asynchronously after 'actor.stop()' is invoked. */
  @log
  override def postStop() {
    super.postStop()
    //Resources.Message.removeSubscription(this)

  }
  /** Process application messages. */
  def receive = {
    case message =>
    //case message @ Resources.Message.ToolbarCreated(toolbar) if (toolbar.getElementId == classOf[EditorToolBar].getName()) =>
      log.debug(s"Process '${message}'.")
      App.exec { visibleWhen.test() }

    //case message @ WorkbenchAdvisor.Message.PostStartup(configurer) =>
      //log.debug(s"Process '${message}'.")
      //ContextInjectionFactory.inject(this, App.workbench.getContext())

    //case message @ Resources.Message.ToolbarCreated(toolbar) => // skip
  }

  /** Hide toolbar at the beginning. */
  protected def onToolBarCreating(element: MToolBar, manager: ToolBarManager) = manager.getControl().setVisible(false)
  /** Add/remove 'shell.hashCode = true' for active parts. */
  @log @Inject @Optional
  protected def trackActivePart(@UIEventTopic(UIEvents.UILifeCycle.ACTIVATE) event: org.osgi.service.event.Event) {
    event.getProperty(UIEvents.EventTags.ELEMENT) match {
      case part: MPart if part.getElementId() == MainPart.id =>
        part.getWidget() match {
          case widget: Widget =>
            // Shell with hashcode N set to true
            App.findShell(widget).foreach(shell => visibleWhen.set(shell.hashCode.toString, Some(true)))
          case unknown =>
        }
      case other: MUIElement =>
        other.getWidget() match {
          case widget: Widget =>
            // Remove shell with hashcode N
            App.findShell(widget).foreach(shell => visibleWhen.set(shell.hashCode.toString, None))
          case unknown =>
        }
      case other =>
    }
  }
}

object ElementToolBar {
  implicit def toolbar2actorRef(t: ElementToolBar.type): ActorRef = t.actor
  implicit def toolbar2actorSRef(t: ElementToolBar.type): ScalaActorRef = t.actor
  /** ElementToolBar actor reference. */
  lazy val actor = App.getActorRef(App.system.actorSelection(actorPath)) getOrElse {
    throw new IllegalStateException("Unable to locate actor with path " + actorPath)
  }
  /** ElementToolBar actor path. */
  lazy val actorPath = App.system / Core.id / Editor.id / id
  /** Singleton identificator. */
  val id = getClass.getSimpleName().dropRight(1)
  /** ElementToolBar actor reference configuration object. */
  lazy val props = DI.props
  // Initialize descendant actor singletons
  //handler.ElementNew
  //handler.ElementEdit
  //handler.ElementDelete

  /**
   * Dependency injection routines.
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** ElementToolBar actor reference configuration object. */
    lazy val props = injectOptional[Props]("Editor.ToolBar.ElementToolBar") getOrElse Props[ElementToolBar]
  }
}
