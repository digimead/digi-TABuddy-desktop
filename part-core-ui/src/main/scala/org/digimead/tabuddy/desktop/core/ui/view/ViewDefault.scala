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

package org.digimead.tabuddy.desktop.core.ui.view

import akka.actor.{ Actor, ActorRef, Props, actorRef2Scala }
import akka.pattern.ask
import java.util.UUID
import java.util.concurrent.TimeoutException
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.Core
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.support.Timeout
import org.digimead.tabuddy.desktop.core.ui.Messages
import org.digimead.tabuddy.desktop.core.ui.block.{ Configuration, View }
import org.digimead.tabuddy.desktop.core.ui.definition.widget.VComposite
import org.eclipse.swt.SWT
import org.eclipse.swt.events.{ DisposeEvent, DisposeListener }
import org.eclipse.swt.graphics.Image
import org.eclipse.swt.layout.FillLayout
import org.eclipse.swt.widgets.{ Composite, Control, Widget }
import scala.collection.immutable
import scala.concurrent.{ Await, Future }

class ViewDefault(val contentId: UUID) extends Actor with Loggable {
  /** View parent widget. */
  @volatile protected var parent: Option[VComposite] = None
  /** View body widget. */
  @volatile protected var body: Option[Composite] = None
  log.debug("Start actor " + self.path)

  def receive = {
    case message @ App.Message.Create(Left(viewLayerWidget: VComposite), None) ⇒ App.traceMessage(message) {
      create(viewLayerWidget) match {
        case Some(body) ⇒
          App.publish(App.Message.Create(Right(body), self))
          App.Message.Create(Right(body))
        case None ⇒
          App.Message.Error(s"Unable to create ${this} for ${viewLayerWidget}.")
      }
    } foreach { sender ! _ }

    case message @ App.Message.Destroy(Left(viewLayerWidget: VComposite), None) ⇒ App.traceMessage(message) {
      destroy() match {
        case Some(body) ⇒
          // App.publish(App.Message.Destroy(Right(body), self)) see body dispose listener
          App.Message.Destroy(Right(body))
        case None ⇒
          App.Message.Error(s"Unable to destroy ${this} for ${viewLayerWidget}.")
      }
    } foreach { sender ! _ }

    case message @ App.Message.Start(Left(widget: Widget), None) ⇒ App.traceMessage(message) {
      App.Message.Start(Right(widget))
    } foreach { sender ! _ }

    case message @ App.Message.Stop(Left(widget: Widget), None) ⇒ App.traceMessage(message) {
      App.Message.Stop(Right(widget))
    } foreach { sender ! _ }
  }

  /**
   * Create view content.
   * @return content container.
   */
  @log
  protected def create(parent: VComposite): Option[Composite] = {
    if (this.parent.nonEmpty)
      throw new IllegalStateException("Unable to create view. It is already created.")
    App.assertEventThread(false)
    App.execNGet {
      parent.setLayout(new FillLayout())
      val body = new Composite(parent, SWT.NONE)
      body.setLayout(new FillLayout())
      val button = new org.eclipse.swt.widgets.Button(parent, SWT.PUSH)
      button.setText("!!!!!!!!!!!!!!!!!!!!!!!!")
      parent.getParent().setMinSize(parent.computeSize(SWT.DEFAULT, SWT.DEFAULT))
      parent.layout(Array[Control](button), SWT.ALL)
      body.addDisposeListener(new DisposeListener {
        def widgetDisposed(e: DisposeEvent) = App.publish(App.Message.Destroy(Right(body), self))
      })
      this.parent = Option(parent)
      this.body = Option(body)
      this.body
    }
  }
  /** Destroy created window. */
  @log
  protected def destroy(): Option[Composite] = {
    App.assertEventThread(false)
    for {
      parent ← parent
      body ← body
    } yield App.execNGet {
      this.parent = None
      this.body = None
      body
    }
  }
}

object ViewDefault extends View.Factory with Loggable {
  /** Singleton identificator. */
  val id = getClass.getSimpleName().dropRight(1)
  /** View name. */
  lazy val name = DI.name
  /** Short view description (one line). */
  lazy val shortDescription = DI.shortDescription
  /** Long view description. */
  lazy val longDescription = DI.longDescription
  /** View image. */
  lazy val image = DI.image

  /** Returns actor reference that could handle Create/Destroy messages. */
  @log
  def viewActor(configuration: Configuration.CView): Option[ActorRef] = viewActorLock.synchronized {
    implicit val timeout = akka.util.Timeout(Timeout.short)
    val viewName = "Content_" + id + "_%08X".format(configuration.id.hashCode())
    val future = Core.actor ? App.Message.Attach(props.copy(args = immutable.Seq(configuration.id)), viewName)
    try {
      val newActorRef = Await.result(future.asInstanceOf[Future[ActorRef]], timeout.duration)
      activeActorRefs.set(activeActorRefs.get() :+ newActorRef)
      titlePerActor.values.foreach(_.update)
      Some(newActorRef)
    } catch {
      case e: InterruptedException ⇒
        log.error(e.getMessage, e)
        None
      case e: TimeoutException ⇒
        log.error(e.getMessage, e)
        None
    }
  }
  /** Default view actor reference configuration object. */
  def props = DI.props

  /**
   * Dependency injection routines.
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** View name. */
    lazy val name = injectOptional[Symbol]("Core.View.Default.Name") getOrElse Symbol({
      val name = Messages.default_text
      if (!App.symbolPattern.matcher(name).matches())
        throw new IllegalArgumentException(s"'${name}' isn't a correct Scala symbol.")
      name
    })
    /** Short view description (one line). */
    lazy val shortDescription = injectOptional[String]("Core.View.Default.ShortDescription") getOrElse Messages.defaultViewShortDescription
    /** Long view description. */
    lazy val longDescription = injectOptional[String]("Core.View.Default.LongDescription") getOrElse Messages.defaultViewLongDescription
    /** View image. */
    lazy val image = injectOptional[Image]("Core.View.Default.Image")
    /** Default view actor reference configuration object. */
    lazy val props = injectOptional[Props]("Core.View.Default") getOrElse Props(classOf[ViewDefault],
      // content id == view layer id
      UUID.fromString("00000000-0000-0000-0000-000000000000"))
  }
}
