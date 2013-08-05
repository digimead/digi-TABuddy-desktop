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

package org.digimead.tabuddy.desktop.editor.action

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.future

import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.Core
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.App.app2implementation
import org.digimead.tabuddy.desktop.support.AppContext.rich2appContext
import org.eclipse.e4.core.contexts.ContextInjectionFactory
import org.eclipse.jface.action.{ Action => JFaceAction }
import org.eclipse.jface.action.IAction
import org.eclipse.swt.widgets.Event

import akka.actor.Props

/** Delete model element. */
class ElementDelete extends JFaceAction("ElementDelete") with Loggable {
  @volatile protected var enabled = false
  ContextInjectionFactory.inject(this, Core.context)

  override def isEnabled(): Boolean = super.isEnabled && enabled
  /** Runs this action, passing the triggering SWT event. */
  @log
  override def runWithEvent(event: Event) = future {
    //Payload.close(Payload.modelMarker(Model))
    //Payload.delete(Payload.modelMarker(Model))
  } onFailure { case e: Throwable => log.error(e.getMessage, e) }

  /** Update enabled action state. */
  protected def updateEnabled() = if (isEnabled)
    firePropertyChange(IAction.ENABLED, java.lang.Boolean.FALSE, java.lang.Boolean.TRUE)
  else
    firePropertyChange(IAction.ENABLED, java.lang.Boolean.TRUE, java.lang.Boolean.FALSE)
}

/*
class ElementDelete extends Handler(ElementDelete) with Loggable {
  @log
  def execute(event: ExecutionEvent): AnyRef = {
    null
  }
}

object ElementDelete extends Handler.Singleton with Loggable {
  /** ElementDelete actor path. */
  lazy val actorPath = App.system / Core.id / Editor.id / ElementToolBar.id / id
  /** Command id for the current handler. */
  val commandId = "org.digimead.tabuddy.desktop.editor.ElementDelete"
  /** Handler actor reference configuration object. */
  val props = DI.props

  class Behavoiur extends Handler.Behaviour(ElementDelete) with Loggable
  /**
   * Dependency injection routines
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** ElementDelete actor reference configuration object. */
    lazy val props = injectOptional[Props]("command.ElementDelete") getOrElse Props[Behavoiur]
  }
}
*/

object ElementDelete extends Loggable {
  /** Singleton identificator. */
  val id = getClass.getSimpleName().dropRight(1)
  /** ElementDelete action. */
  @volatile protected var action: Option[ElementDelete] = None

  /** Returns ElementDelete action. */
  def apply(): ElementDelete = action.getOrElse {
    val ElementDeleteAction = App.execNGet { new ElementDelete }
    action = Some(ElementDeleteAction)
    ElementDeleteAction
  }
  /** ElementDelete actor reference configuration object. */
  def props = DI.props

  /** ElementDelete actor. */
  class Actor extends akka.actor.Actor {
    log.debug("Start actor " + self.path)

    /** Is called asynchronously after 'actor.stop()' is invoked. */
    override def postStop() = {
      log.debug(self.path.name + " actor is stopped.")
    }
    /** Is called when an Actor is started. */
    override def preStart() {
      log.debug(self.path.name + " actor is started.")
    }
    def receive = {
      case message if message == null =>
    }
  }
  /**
   * Dependency injection routines.
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** ElementDelete actor reference configuration object. */
    lazy val props = injectOptional[Props]("Editor.Action.ElementDelete") getOrElse Props[ElementDelete.Actor]
  }
}
