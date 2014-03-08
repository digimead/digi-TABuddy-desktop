/**
 * This file is part of the TA Buddy project.
 * Copyright (c) 2014 Alexey Aksenov ezh@ezh.msk.ru
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

package org.digimead.tabuddy.desktop.core.ui.definition

import akka.actor.{ Actor, ActorRef, actorRef2Scala }
import java.util.UUID
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.definition.Context
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.ui.block
import org.digimead.tabuddy.desktop.core.ui.definition.widget.VComposite
import org.eclipse.swt.events.{ DisposeEvent, DisposeListener }
import org.eclipse.swt.layout.FillLayout
import org.eclipse.swt.widgets.{ Composite, Widget }

/**
 * Base trait for view content.
 */
trait IView {
  this: Actor with Loggable ⇒
  /** Unique content/view/actor id. */
  val contentId: UUID
  /** View factory. */
  val factory: block.View.Factory
  /** View body widget. */
  @volatile var body: Option[Composite] = None
  /** View parent widget. */
  @volatile var parent: Option[VComposite] = None
  /** Flag indicating whether the View is alive. */
  var terminated = false
  /** Container view actor. */
  var containerRef = Option.empty[ActorRef]
  /** View context. */
  lazy val viewContext = Context(self.path.name)

  /** Is called asynchronously after 'actor.stop()' is invoked. */
  override def postStop() = {
    log.debug(this + " is stopped.")
    factory.unregister(self.path.name)
  }
  /** Is called when an Actor is started. */
  override def preStart() = {
    log.debug(this + " is started.")
    factory.register(self.path.name, viewContext)
  }
  /** Get container actor reference. */
  def container = containerRef getOrElse { throw new NoSuchElementException(s"${this} container not found.") }
  def receive: Actor.Receive = {
    case message @ App.Message.Create(viewLayerWidget: VComposite, Some(view), None) if view == container ⇒ App.traceMessage(message) {
      if (terminated) {
        App.Message.Error(s"${this} is terminated.", self)
      } else {
        create(viewLayerWidget) match {
          case Some(body) ⇒
            App.publish(App.Message.Create(body, self))
            App.Message.Create(body, self)
          case None ⇒
            App.Message.Error(s"Unable to create ${this} for ${viewLayerWidget}.", None)
        }
      }
    } foreach { sender ! _ }

    case message @ App.Message.Destroy(viewLayerWidget: VComposite, Some(view), None) if view == container ⇒ App.traceMessage(message) {
      if (terminated) {
        App.Message.Error(s"${this} is terminated.", self)
      } else {
        destroy() match {
          case Some(body) ⇒
            App.Message.Destroy(body, None)
          case None ⇒
            App.Message.Error(s"Unable to destroy ${this} for ${viewLayerWidget}.", None)
        }
      }
    } foreach { sender ! _ }

    case message @ App.Message.Set(_, parentWidget: VComposite) ⇒ App.traceMessage(message) {
      if (parentWidget.ref.path.name != "View_%08X".format(contentId.hashCode()))
        throw new IllegalArgumentException(s"Illegal container ${parentWidget}.")
      log.debug(s"Bind ${this} to ${parentWidget}.")
      containerRef = Some(parentWidget.ref)
      parentWidget.getContext.foreach(viewContext.setParent)
    }

    case message @ App.Message.Start(widget: Widget, _, None) ⇒ App.traceMessage(message) {
      if (terminated) {
        App.Message.Error(s"${this} is terminated.", self)
      } else {
        App.Message.Start(widget, None)
      }
    } foreach { sender ! _ }

    case message @ App.Message.Stop(widget: Widget, _, None) ⇒ App.traceMessage(message) {
      if (terminated) {
        App.Message.Error(s"${this} is terminated.", self)
      } else {
        App.Message.Stop(widget, None)
      }
    } foreach { sender ! _ }

    case App.Message.Error(Some(message), _) ⇒
      log.debug(message)
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
      val body = createContents(parent)
      body.addDisposeListener(new DisposeListener {
        def widgetDisposed(e: DisposeEvent) = container ! App.Message.Destroy(None, self)
      })
      parent.layout()
      this.parent = Option(parent)
      this.body = Option(body)
      this.body
    }
  }
  /** Creates and returns this window's contents. */
  protected def createContents(parent: VComposite): Composite
  /** Destroy created window. */
  @log
  protected def destroy(): Option[Composite] = {
    App.assertEventThread(false)
    terminated = true
    for {
      parent ← parent
      body ← body
    } yield {
      App.execNGet { body.dispose() }
      App.publish(App.Message.Destroy(body, self))
      body
    }
  }
}
