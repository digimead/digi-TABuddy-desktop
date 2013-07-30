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

package org.digimead.tabuddy.desktop.gui

import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.Core
import org.digimead.tabuddy.desktop.gui
import org.digimead.tabuddy.desktop.gui.stack.VComposite
import org.digimead.tabuddy.desktop.gui.stack.StackBuilder
import org.digimead.tabuddy.desktop.gui.stack.StackBuilder.builder2implementation
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.App.app2implementation
import org.eclipse.e4.core.internal.contexts.EclipseContext
import org.eclipse.swt.custom.ScrolledComposite
import org.eclipse.swt.widgets.Widget
import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props
import java.util.UUID
import org.digimead.tabuddy.desktop.action.View

/** View actor binded to SComposite that contains an actual view from a view factory. */
class ViewLayer(parentContext: EclipseContext) extends Actor with Loggable {
  /** View JFace instance. */
  protected var view: Option[VComposite] = None
  /** View layer id. */
  lazy val stackId = UUID.fromString(self.path.name.split("@").last)
  /** View context. */
  protected val viewContext = parentContext.createChild(self.path.name).asInstanceOf[EclipseContext]
  log.debug("Start actor " + self.path)

  def receive = {
    case message @ App.Message.Create(ViewLayer.CreateArgument(viewConfiguration, parentWidget, parentContext), supervisor) => App.traceMessage(message) {
      create(viewConfiguration, parentWidget, parentContext, supervisor)
    }
    case message @ App.Message.Start(widget: Widget, supervisor) => App.traceMessage(message) {
      onStart(widget)
    }
  }

  /** Create view. */
  protected def create(viewConfiguration: gui.Configuration.View, parentWidget: ScrolledComposite, parentContext: EclipseContext, supervisor: ActorRef) {
    if (view.nonEmpty)
      throw new IllegalStateException("Unable to create view. It is already created.")
    this.view = StackBuilder(viewConfiguration, parentWidget, viewContext, supervisor, self)
    this.view.foreach(stack => App.publish(App.Message.Created(stack, self)))
  }
  /** User start interaction with window/stack supervisor/view. Focus is gained. */
  protected def onStart(widget: Widget) = view match {
    case Some(view) =>
      log.debug("View layer started by focus event on " + widget)
      Core.context.set(GUI.viewContextKey, view)
      viewContext.activateBranch()
      view.contentRef ! App.Message.Start(widget, self)
    case None =>
      log.fatal("Unable to start view without widget.")
  }
}

object ViewLayer {
  /** Singleton identificator. */
  val id = getClass.getSimpleName().dropRight(1)

  /** ViewLayer actor reference configuration object. */
  def props = DI.props

  case class CreateArgument(val viewConfiguration: gui.Configuration.View, val parentWidget: ScrolledComposite, val parentContext: EclipseContext)
  /**
   * User implemented factory, that returns ActorRef, responsible for view creation/destroying.
   */
  trait Factory {
    /** View name. */
    val name: String
    /** View description. */
    val description: String

    /** Returns actor reference that could handle Create/Destroy messages. */
    def viewActor(configuration: Configuration.View, parentContext: EclipseContext): Option[ActorRef]

    override def toString() = s"""ViewFactory("${name}", "${description}")"""
  }
  /**
   * Dependency injection routines.
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** ViewLayer actor reference configuration object. */
    lazy val props = injectOptional[Props]("GUI.ViewLayer") getOrElse Props(classOf[ViewLayer], Core.context)
  }
}
