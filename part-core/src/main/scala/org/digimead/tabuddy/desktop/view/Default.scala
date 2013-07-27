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

package org.digimead.tabuddy.desktop.view

import java.util.concurrent.TimeoutException

import scala.concurrent.Await
import scala.concurrent.Future

import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.Core
import org.digimead.tabuddy.desktop.gui.View
import org.digimead.tabuddy.desktop.gui.api
import org.digimead.tabuddy.desktop.gui.stack.SComposite
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.App.app2implementation
import org.digimead.tabuddy.desktop.support.Timeout
import org.eclipse.swt.SWT
import org.eclipse.swt.widgets.Control

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props
import akka.pattern.ask

class Default extends Actor with Loggable {
  /** Container view actor. */
  protected var view: Option[ActorRef] = None
  log.debug("Start actor " + self.path)

  def receive = {
    case message @ App.Message.Create(container: SComposite, viewActor) => App.traceMessage(message) {
      App.exec { create(container, viewActor) }
    }
    case message @ App.Message.Destroy => App.traceMessage(message) {
      App.exec { destroy(sender) }
      this.view = None
    }
  }
  /** Create window. */
  protected def create(parent: SComposite, viewActor: ActorRef) {
    if (view.nonEmpty)
      throw new IllegalStateException("Unable to create view. It is already created.")
    App.checkThread
    val button = new org.eclipse.swt.widgets.Button(parent, SWT.PUSH)
    button.setText("!!!!!!!!!!!!!!!!!!!!!!!!")
    parent.getParent().setMinSize(parent.computeSize(SWT.DEFAULT, SWT.DEFAULT))
    parent.layout(Array[Control](button), SWT.ALL)
  }
  /** Destroy created window. */
  protected def destroy(sender: ActorRef) = this.view.foreach { view =>
    App.checkThread
  }
}

object Default extends api.ViewFactory with Loggable {
  /** Singleton identificator. */
  val id = getClass.getSimpleName().dropRight(1)

  /** Returns actor reference that could handle Create/Destroy messages. */
  def viewActor(configuration: api.Configuration.View): Option[ActorRef] = {
    implicit val ec = App.system.dispatcher
    implicit val timeout = akka.util.Timeout(Timeout.short)
    val future = Core.actor ? App.Message.Attach(props, View.id + id + "@" + configuration.id)
    try {
      Option(Await.result(future.asInstanceOf[Future[ActorRef]], timeout.duration))
    } catch {
      case e: InterruptedException =>
        log.error(e.getMessage, e)
        None
      case e: TimeoutException =>
        log.error(e.getMessage, e)
        None
    }
  }
  /** Window actor reference configuration object. */
  def props = DI.props

  /**
   * Dependency injection routines.
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** Default view actor reference configuration object. */
    lazy val props = injectOptional[Props]("Default") getOrElse Props[Default]
  }
}
