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

package org.digimead.tabuddy.desktop.logic.handler

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.future

import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.Core
import org.digimead.tabuddy.desktop.logic.Data
import org.digimead.tabuddy.desktop.logic.Logic
import org.digimead.tabuddy.desktop.logic.payload.Payload
import org.digimead.tabuddy.desktop.logic.payload.Payload.payload2implementation
import org.digimead.tabuddy.desktop.logic.toolbar.ModelToolBar
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.App.app2implementation
import org.digimead.tabuddy.desktop.support.Handler
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.Model.model2implementation
import org.digimead.tabuddy.model.element.Element
import org.eclipse.core.commands.ExecutionEvent
import org.eclipse.ui.commands.IElementUpdater
import org.eclipse.ui.menus.UIElement

import akka.actor.Actor
import akka.actor.Props
import akka.actor.actorRef2Scala

class Lock extends Handler(Lock) with IElementUpdater with Loggable {
  @log
  def execute(event: ExecutionEvent): AnyRef = {
    val context = App.model.getContext().getActiveLeaf()

    if (Model.eId == Payload.defaultModel.eId) {
      // There is the default model. Load different model.
      val id = context.get(Data.Id.modelIdUserInput).asInstanceOf[String]
      if (id.nonEmpty)
        future {
          Payload.listModels.find(marker => marker.isValid && marker.id.name == id) match {
            case Some(marker) =>
              Payload.acquireModel(marker)
            case None =>
              try {
                App.exec { App.openWizard("org.digimead.tabuddy.desktop.editor.wizard.NewModelWizard") }
              } catch {
                case e: IllegalArgumentException =>
                  log.error(e.getMessage())
              }
          }
        } onFailure { case e: Throwable => log.error(e.getMessage, e) }
    } else {
      // Something already loaded. Close.
      future {
        Payload.getModelMarker(Model) match {
          case Some(marker) =>
            Payload.close(marker)
          case None =>
            log.fatal(s"Unable to correctly to close ${Model.inner} with unknown marker.")
        }
      } onFailure { case e: Throwable => log.error(e.getMessage, e) }
    }
    null
  }
  override def isEnabled(): Boolean = {
    val context = App.model.getContext().getActiveLeaf()
    super.isEnabled && (context.get(Data.Id.modelIdUserInput).asInstanceOf[String].nonEmpty || Model.eId != Payload.defaultModel.eId)
  }
  def updateElement(element: UIElement, paramters: java.util.Map[_, _]) {
    element.setChecked(Model.eId != Payload.defaultModel.eId)
  }
}

object Lock extends Handler.Singleton with Loggable {
  /** Lock actor path. */
  lazy val actorPath = App.system / Core.id / Logic.id / ModelToolBar.id / id
  /** Command id for the current handler. */
  val commandId = "org.digimead.tabuddy.desktop.logic.Lock"

  /** Handler actor reference configuration object. */
  def props = DI.props

  class Behaviour extends Handler.Behaviour(Lock) with Loggable {
    override def receive: PartialFunction[Any, Unit] = receiveBefore orElse super.receive
    protected def receiveBefore: Actor.Receive = {
      case message @ Element.Event.ModelReplace(oldModel, newModel, modified) =>
        log.debug(s"Process '${message}'.")
        self ! Handler.Message.Refresh
    }
  }
  /**
   * Dependency injection routines
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** Lock Akka factory. */
    lazy val props = injectOptional[Props]("command.Lock") getOrElse Props[Behaviour]
  }
}
