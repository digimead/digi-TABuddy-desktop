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

package org.digimead.tabuddy.desktop.logic.action

import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.Core
import org.digimead.tabuddy.desktop.b4e.WorkbenchAdvisor
import org.digimead.tabuddy.desktop.logic.Data
import org.digimead.tabuddy.desktop.logic.Logic
import org.digimead.tabuddy.desktop.logic.toolbar.ModelToolBar
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.App.app2implementation
//import org.digimead.tabuddy.desktop.support.Handler
import org.eclipse.core.commands.ExecutionEvent
import org.eclipse.e4.core.contexts.Active
import org.eclipse.e4.core.contexts.ContextInjectionFactory
import org.eclipse.e4.core.di.annotations.Optional
import org.eclipse.jface.action.{ Action => JFaceAction }

import akka.actor.Actor
import akka.actor.Props
import javax.inject.Inject
import javax.inject.Named

class Delete extends JFaceAction("Delete") with Loggable {
  if (App.workbench.isRunning())
    ContextInjectionFactory.inject(this, App.workbench.getContext())

  @log
  def execute(event: ExecutionEvent): AnyRef = {
    null
  }
  /** Invoked at every modification of Data.Id.modelIdUserInput. */
  @Inject @Optional
  def onModelIdUserInputChanged(@Active @Named(Data.Id.modelIdUserInput) id: String) =
    setEnabled(id != null && id.trim.nonEmpty && Data.availableModels.contains(id.trim))
}

/*object Delete extends Handler.Singleton with Loggable {
  /** Delete actor path. */
  lazy val actorPath = App.system / Core.id / Logic.id / ModelToolBar.id / id
  /** Command id for the current handler. */
  val commandId = "org.digimead.tabuddy.desktop.logic.Delete"

  /** Handler actor reference configuration object. */
  def props = DI.props

  class Behaviour extends Handler.Behaviour(Delete) with Loggable {
    override def receive: PartialFunction[Any, Unit] = receiveBefore orElse super.receive
    protected def receiveBefore: Actor.Receive = {
      case message @ WorkbenchAdvisor.Message.PostStartup(configurer) =>
        log.debug(s"Process! '${message}'.")
        Delete.instance.keys.foreach(ContextInjectionFactory.inject(_, App.workbench.getContext()))
    }
  }
  /**
   * Dependency injection routines
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** Delete Akka factory. */
    lazy val props = injectOptional[Props]("command.Delete") getOrElse Props[Behaviour]
  }
}
*/

