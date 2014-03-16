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

package org.digimead.tabuddy.desktop.logic.ui.view.graph

import akka.actor.{ Actor, Props }
import java.util.UUID
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.jfx4swt.JFX
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.ui.block
import org.digimead.tabuddy.desktop.core.ui.definition.IView
import org.digimead.tabuddy.desktop.core.ui.definition.widget.VComposite
import org.digimead.tabuddy.desktop.core.ui.view.{ Loading, defaultv }
import org.digimead.tabuddy.desktop.logic.Logic
import org.digimead.tabuddy.desktop.logic.payload.maker.GraphMarker
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.graph.Graph
import org.eclipse.swt.SWT
import org.eclipse.swt.custom.StackLayout
import org.eclipse.swt.events.{ DisposeEvent, DisposeListener }
import org.eclipse.swt.widgets.Composite

/**
 * Graph view.
 */
class View(val contentId: UUID, val factory: block.View.Factory) extends Actor with IView with Loggable {
  /** View content widget. */
  @volatile var content: Option[Content] = None
  /** View loading widget. */
  @volatile var loading: Option[Loading] = None
  log.debug("Start actor " + self.path)

  /** Is called asynchronously after 'actor.stop()' is invoked. */
  override def postStop() = {
    App.system.eventStream.unsubscribe(self, classOf[App.Message.Update[_]])
    App.system.eventStream.unsubscribe(self, classOf[App.Message.Destroy[_]])
    super.postStop()
  }
  /** Is called when an Actor is started. */
  override def preStart() = {
    super.preStart()
    App.system.eventStream.subscribe(self, classOf[App.Message.Destroy[_]])
    App.system.eventStream.subscribe(self, classOf[App.Message.Update[_]])
  }

  override def receive: Actor.Receive = super.receive orElse {
    case message @ App.Message.Set(_, marker: GraphMarker) ⇒ App.traceMessage(message) {
      if (terminated)
        App.Message.Error(s"${this} is terminated.", self)
      else
        App.exec { assignGraphMarker(marker) }
    }

    case message @ App.Message.Destroy(vComposite: VComposite, _, _) ⇒ App.traceMessage(message) {
      // Update marker records.
      if (!terminated) App.exec { this.content.foreach(_.delayedMarkerUpdater.value = System.currentTimeMillis()) }
    }

    case message @ App.Message.Update(marker: GraphMarker, _, _) ⇒ App.traceMessage(message) {
      // Update marker records.
      if (!terminated) App.exec { this.content.foreach(_.delayedMarkerUpdater.value = System.currentTimeMillis()) }
    }

    case message @ App.Message.Destroy(_, _, _) ⇒ // skip
    case message @ App.Message.Update(_, _, _) ⇒ // skip
  }

  /** Assign graph marker to view. */
  protected def assignGraphMarker(marker: GraphMarker) = for {
    body ← body
    content ← content
    layout = body.getLayout().asInstanceOf[StackLayout]
  } if (marker.graphIsOpen()) {
    layout.topControl = content
    body.layout()
    loading.foreach { loading ⇒
      View.this.loading = None
      loading.dispose()
    }
    viewContext.set(classOf[GraphMarker], marker)
    App.publish(App.Message.Update(marker, self))
  } else
    container ! App.Message.Destroy(None, self)
  /** Creates and returns this window's contents. */
  protected def createContents(parent: VComposite): Composite = {
    val body = new Composite(parent, SWT.NONE)
    val layout = new StackLayout()
    body.setLayout(layout)
    val loading = new Loading(body, SWT.NONE)
    loading.initializeSWT()
    JFX.exec { loading.initializeJFX() }
    val content = new Content(viewContext, body, SWT.NONE)
    content.initializeSWT()
    JFX.exec { content.initializeJFX() }
    layout.topControl = loading
    body.layout()
    this.content = Some(content)
    this.loading = Some(loading)
    body.addDisposeListener(new DisposeListener {
      def widgetDisposed(e: DisposeEvent) = {
        View.this.content = None
        View.this.loading = None
      }
    })
    body
  }
}

object View extends Loggable {
  /** Singleton identificator. */
  val id = getClass.getSimpleName().dropRight(1)

  /** Default view factory. */
  def factory = DI.factory

  class Factory extends block.View.Factory {
    /** View name. */
    lazy val name = 'graph
    /** Short view description (one line). */
    lazy val shortDescription = "View.DI.shortDescription"
    /** Long view description. */
    lazy val longDescription = "View.DI.longDescription"
    /** View image. */
    lazy val image = defaultv.View.factory.image
    /** Features. */
    val features: Seq[String] = Seq(Logic.Feature.graph)

    /** Graph view actor reference configuration object. */
    def props = View.DI.props
  }
  /**
   * Dependency injection routines.
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** Default view factory. */
    lazy val factory = injectOptional[Factory] getOrElse new Factory
    /** Default view actor reference configuration object. */
    lazy val props = injectOptional[Props]("Core.View.Default") getOrElse Props(classOf[View],
      // content id == view layer id
      UUID.fromString("00000000-0000-0000-0000-000000000000"),
      // stub factory
      null.asInstanceOf[View.Factory])
  }
}
