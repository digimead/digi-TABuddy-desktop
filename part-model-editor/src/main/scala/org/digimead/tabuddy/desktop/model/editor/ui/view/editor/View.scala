/**
 * This file is part of the TA Buddy project.
 * Copyright (c) 2013-2015 Alexey Aksenov ezh@ezh.msk.ru
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

package org.digimead.tabuddy.desktop.model.editor.ui.view.editor

import akka.actor.{ Actor, ActorRef, Props }
import java.util.UUID
import org.digimead.digi.lib.api.XDependencyInjection
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.support.{ App, WritableValue }
import org.digimead.tabuddy.desktop.core.ui.block
import org.digimead.tabuddy.desktop.core.ui.definition.IView
import org.digimead.tabuddy.desktop.core.ui.definition.widget.VComposite
import org.digimead.tabuddy.desktop.logic.Logic
import org.digimead.tabuddy.desktop.logic.payload.marker.GraphMarker
import org.digimead.tabuddy.desktop.model.editor.Messages
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.element.Element
import org.digimead.tabuddy.model.graph.Event
import org.digimead.tabuddy.model.graph.Graph
import org.eclipse.e4.core.contexts.ContextInjectionFactory
import org.eclipse.swt.SWT
import org.eclipse.swt.events.{ DisposeEvent, DisposeListener }
import org.eclipse.swt.graphics.Image
import org.eclipse.swt.widgets.Composite

class View(val contentId: UUID, val factory: block.View.Factory) extends Actor with IView with XLoggable {
  /** Aggregation listener delay */
  protected val aggregatorDelay = 250 // msec
  /** Significant changes(schema modification, model reloading,...) aggregator */
  protected lazy val reloadEventsAggregator = WritableValue(Long.box(0L))
  /** Structural changes(e.g. addition or removal of elements) aggregator */
  protected lazy val refreshEventsAggregator = WritableValue(Set[Element]())
  /** Minor changes(element modification) aggregator */
  protected lazy val updateEventsAggregator = WritableValue(Set[Element]())
  /** Parent view widget. */
  @volatile protected var view: Option[VComposite] = None
  /** View content widget. */
  @volatile var content: Option[Content] = None
  log.debug("Start actor " + self.path)

  override def receive: Actor.Receive = super.receive orElse {
    case message @ App.Message.Set(_, marker: GraphMarker) ⇒ App.traceMessage(message) {
      if (terminated)
        App.Message.Error(s"${this} is terminated.", self)
      else
        App.exec { assignGraphMarker(marker) }
    }
  }

  /** Assign graph marker to view. */
  protected def assignGraphMarker(marker: GraphMarker) = for {
    body ← body
    content ← content
    parent ← parent
    parentContext ← parent.getContext()
  } if (marker.graphIsOpen()) {
    body.layout()
    GraphMarker.bind(marker, parentContext)
    GraphMarker.bind(marker, viewContext)
    App.publish(App.Message.Update(marker, self))
  } else
    container ! App.Message.Destroy(None, self)
  /** Creates and returns this window's contents. */
  protected def createContents(parent: VComposite): Composite = {
    viewContext.set(Logic.Feature.viewDefinition, java.lang.Boolean.TRUE)
    viewContext.set("style", SWT.NONE: Integer)
    /* View.DI.contentClass.asInstanceOf[Class[Content]] instead of classOf[Content] is suitable for debugging. */
    val content = ContextInjectionFactory.make(View.DI.contentClass.asInstanceOf[Class[Content]], viewContext)
    this.content = Some(content)
    content.addDisposeListener(new DisposeListener {
      def widgetDisposed(e: DisposeEvent) = {
        View.this.content = None
      }
    })
    content
  }
}

object View extends XLoggable {
  /** Singleton identificator. */
  val id = getClass.getSimpleName().dropRight(1)

  /** Default view factory. */
  def factory = DI.factory

  class Factory extends block.View.Factory {
    /** View name. */
    lazy val name = DI.name
    /** Short view description (one line). */
    lazy val shortDescription = DI.shortDescription
    /** Long view description. */
    lazy val longDescription = DI.longDescription
    /** View image. */
    lazy val image = DI.image
    /** Features. */
    val features: Seq[String] = Seq(Logic.Feature.graph, Logic.Feature.viewDefinition)

    /** Editor view actor reference configuration object. */
    def props = DI.props
  }
  /**
   * Dependency injection routines.
   */
  private object DI extends XDependencyInjection.PersistentInjectable {
    /** View content class. Value may be overwritten while debugging. */
    lazy val contentClass = injectOptional[Class[_]]("Editor.View.Content") getOrElse classOf[Content]
    /** View factory. */
    lazy val factory = injectOptional[Factory] getOrElse new Factory
    /** View name. */
    lazy val name = injectOptional[Symbol]("Editor.View.Editor.Name") getOrElse Symbol({
      val name = Messages.tableView_text
      if (!App.symbolPattern.matcher(name).matches())
        throw new IllegalArgumentException(s"'${name}' isn't a correct Scala symbol.")
      name
    })
    /** Short view description (one line). */
    lazy val shortDescription = injectOptional[String]("Editor.View.Default.ShortDescription") getOrElse Messages.editorViewShortDescription
    /** Long view description. */
    lazy val longDescription = injectOptional[String]("Editor.View.Default.LongDescription") getOrElse Messages.editorViewLongDescription
    /** View image. */
    lazy val image = injectOptional[Image]("Editor.View.Editor.Image")
    /** Editor view actor reference configuration object. */
    lazy val props = injectOptional[Props]("Editor.View.Editor") getOrElse Props(classOf[View],
      // content id == view layer id
      UUID.fromString("00000000-0000-0000-0000-000000000000"),
      // stub factory
      null.asInstanceOf[View.Factory])
  }
}
