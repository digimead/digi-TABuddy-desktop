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

package org.digimead.tabuddy.desktop.editor.view

import java.util.UUID
import java.util.concurrent.TimeoutException

import scala.collection.immutable
import scala.concurrent.Await
import scala.concurrent.Future

import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.gui
import org.digimead.tabuddy.desktop.gui.widget.VComposite
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.App.app2implementation
import org.digimead.tabuddy.desktop.support.Timeout
import org.eclipse.swt.SWT
import org.eclipse.swt.graphics.Image
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Control
import org.eclipse.swt.widgets.Widget

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.pattern.ask

class Editor(val contentId: UUID) extends Actor with Loggable {
  /** Parent view widget. */
  @volatile protected var view: Option[VComposite] = None
  log.debug("Start actor " + self.path)

  def receive = {
    case message @ App.Message.Create(Left(parentWidget: VComposite), None) => sender ! App.traceMessage(message) {
      create(parentWidget) match {
        case Some(contentWidget) =>
          App.publish(App.Message.Create(Right(contentWidget), self))
          App.Message.Create(Right(contentWidget))
        case None =>
          App.Message.Error(s"Unable to create ${this} for ${parentWidget}.")
      }
    }
    case message @ App.Message.Destroy => App.traceMessage(message) {
      App.execNGet { destroy(sender) }
      this.view = None
    }
    case message @ App.Message.Start(Left(widget: Widget), None) => sender ! App.traceMessage(message) {
      onStart(widget)
      App.Message.Start(Right(widget))
    }
    case message @ App.Message.Stop(Left(widget: Widget), None) => sender ! App.traceMessage(message) {
      App.Message.Stop(Right(widget))
    }
  }

  /**
   * Create view content.
   * @return content container.
   */
  protected def create(parent: VComposite): Option[Composite] = {
    if (view.nonEmpty)
      throw new IllegalStateException("Unable to create view. It is already created.")
    App.assertUIThread(false)
    view = Option(parent)
    val content = App.execNGet { editor.View(parent, SWT.NONE) }
    Some(content)
  }
  /** Destroy created window. */
  protected def destroy(sender: ActorRef) = this.view.foreach { view =>
    App.assertUIThread()
  }
  /** User start interaction with window/stack supervisor/view/this content. Focus is gained. */
  protected def onStart(widget: Widget) = view match {
    case Some(view) =>
      log.debug("View started by focus event on " + widget)
      App.exec { view.getChildren().head.asInstanceOf[editor.View].onStart() }
    case None =>
      log.fatal("Unable to start view without widget.")
  }
}

object Editor extends gui.ViewLayer.Factory with Loggable {
  /** Singleton identificator. */
  val id = getClass.getSimpleName().dropRight(1)
  /** View name. */
  lazy val name = DI.name
  /** View description. */
  lazy val description = DI.description
  /** View image. */
  lazy val image = DI.image

  /** Returns actor reference that could handle Create/Destroy messages. */
  @log
  def viewActor(configuration: gui.Configuration.View): Option[ActorRef] = viewActorLock.synchronized {
    implicit val ec = App.system.dispatcher
    implicit val timeout = akka.util.Timeout(Timeout.short)
    val viewName = "Content_" + id + "_%08X".format(configuration.id.hashCode())
    val future = org.digimead.tabuddy.desktop.editor.Editor.actor ?
      App.Message.Attach(props.copy(args = immutable.Seq(configuration.id)), viewName)
    try {
      val newActorRef = Await.result(future.asInstanceOf[Future[ActorRef]], timeout.duration)
      activeActorRefs.set(activeActorRefs.get() :+ newActorRef)
      titlePerActor.values.foreach(_.update)
      Some(newActorRef)
    } catch {
      case e: InterruptedException =>
        log.error(e.getMessage, e)
        None
      case e: TimeoutException =>
        log.error(e.getMessage, e)
        None
    }
  }
  /** Editor view actor reference configuration object. */
  def props = DI.props

  /**
   * Dependency injection routines.
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** View name. */
    lazy val name = injectOptional[String]("Editor.View.Editor.Name") getOrElse "Editor"
    /** View description. */
    lazy val description = injectOptional[String]("Editor.View.Editor.Description") orElse Some("Editor view description")
    /** View image. */
    lazy val image = injectOptional[Image]("Editor.View.Editor.Image")
    /** Editor view actor reference configuration object. */
    lazy val props = injectOptional[Props]("Editor.View.Editor") getOrElse Props(classOf[Editor],
      // content id == view layer id
      UUID.fromString("00000000-0000-0000-0000-000000000000"))
  }
}
