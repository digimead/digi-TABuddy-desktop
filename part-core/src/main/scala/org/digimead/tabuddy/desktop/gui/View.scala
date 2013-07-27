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
import org.digimead.tabuddy.desktop.gui.stack.SComposite
import org.digimead.tabuddy.desktop.gui.stack.StackBuilder
import org.digimead.tabuddy.desktop.gui.stack.StackBuilder.builder2implementation
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.App.app2implementation
import org.eclipse.swt.custom.ScrolledComposite

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props

class View extends Actor with Loggable {
  protected var view: Option[SComposite] = None
  log.debug("Start actor " + self.path)

  def receive = {
    case message @ App.Message.Create(View.CreateArgument(viewConfiguration, parent), supervisor) => App.traceMessage(message) {
      create(viewConfiguration, parent, supervisor)
    }
  }

  /** Create view. */
  protected def create(viewConfiguration: api.Configuration.PlaceHolder, parent: ScrolledComposite, supervisor: ActorRef) {
    if (view.nonEmpty)
      throw new IllegalStateException("Unable to create view. It is already created.")
    this.view = Option(StackBuilder(viewConfiguration, parent, supervisor, self))
    this.view.foreach(stack => App.publish(App.Message.Created(stack, self)))
  }
}

object View {
  /** Singleton identificator. */
  val id = getClass.getSimpleName().dropRight(1)

  /** View actor reference configuration object. */
  def props = DI.props

  case class CreateArgument(val viewConfiguration: api.Configuration.View, val parent: ScrolledComposite)
  /**
   * Dependency injection routines.
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** View actor reference configuration object. */
    lazy val props = injectOptional[Props]("View") getOrElse Props[View]
  }
}
