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

package org.digimead.tabuddy.desktop.model.editor.ui.view.editor

import java.util.UUID
import org.digimead.digi.lib.api.XDependencyInjection
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.ui.definition.widget.VComposite
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.support.App.app2implementation
import org.digimead.tabuddy.desktop.core.support.WritableValue
import org.digimead.tabuddy.model.element.Element
import org.eclipse.swt.SWT
import org.eclipse.swt.graphics.Image
import org.eclipse.swt.widgets.Composite
import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.actorRef2Scala
import org.digimead.tabuddy.desktop.core.ui.block
import org.digimead.tabuddy.desktop.model.editor.Messages
import org.digimead.tabuddy.desktop.logic.Logic
import org.digimead.tabuddy.desktop.logic.payload.marker.GraphMarker
import org.digimead.tabuddy.desktop.core.ui.definition.IView
import org.eclipse.swt.events.DisposeEvent
import org.eclipse.swt.events.DisposeListener
import org.digimead.tabuddy.model.graph.Event
import org.eclipse.e4.core.contexts.ContextInjectionFactory

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

  /** Is called asynchronously after 'actor.stop()' is invoked. */
  override def postStop() = {
    App.system.eventStream.unsubscribe(self, classOf[Event.ValueUpdate[_ <: Element]])
    App.system.eventStream.unsubscribe(self, classOf[Event.ValueRemove[_ <: Element]])
    App.system.eventStream.unsubscribe(self, classOf[Event.ValueInclude[_ <: Element]])
    //    App.system.eventStream.unsubscribe(self, classOf[Event.StashReplace[_ <: Element]])
    //    App.system.eventStream.unsubscribe(self, classOf[Event.ModelReplace[_ <: Model.Interface[_ <: Model.Stash], _ <: Model.Interface[_ <: Model.Stash]]])
    //    App.system.eventStream.unsubscribe(self, classOf[Event.ChildrenReset[_ <: Element]])
    //    App.system.eventStream.unsubscribe(self, classOf[Event.ChildReplace[_ <: Element]])
    //    App.system.eventStream.unsubscribe(self, classOf[Event.ChildRemove[_ <: Element]])
    //    App.system.eventStream.unsubscribe(self, classOf[Event.ChildInclude[_ <: Element]])
    super.postStop()
  }
  /** Is called when an Actor is started. */
  override def preStart() {
    //    App.system.eventStream.subscribe(self, classOf[Event.ChildInclude[_ <: Element]])
    //    App.system.eventStream.subscribe(self, classOf[Event.ChildRemove[_ <: Element]])
    //    App.system.eventStream.subscribe(self, classOf[Event.ChildReplace[_ <: Element]])
    //    App.system.eventStream.subscribe(self, classOf[Event.ChildrenReset[_ <: Element]])
    //    App.system.eventStream.subscribe(self, classOf[Event.ModelReplace[_ <: Model.Interface[_ <: Model.Stash], _ <: Model.Interface[_ <: Model.Stash]]])
    //    App.system.eventStream.subscribe(self, classOf[Event.StashReplace[_ <: Element]])
    App.system.eventStream.subscribe(self, classOf[Event.ValueInclude[_ <: Element]])
    App.system.eventStream.subscribe(self, classOf[Event.ValueRemove[_ <: Element]])
    App.system.eventStream.subscribe(self, classOf[Event.ValueUpdate[_ <: Element]])
    App.exec {
      // handle presentation changes
      //      Observables.observeDelayedValue(aggregatorDelay, updateEventsAggregator).addValueChangeListener(new IValueChangeListener {
      //        def handleValueChange(event: ValueChangeEvent) = View.views.keys.foreach(view ⇒ View.withRedrawDelayed(view) {
      //          val set = updateEventsAggregator.value
      //          updateEventsAggregator.value = Set()
      //          if (set.nonEmpty)
      //            view.update(set.toArray)
      //        })
      //      })
      // handle data modification changes
      //      Data.modelName.addChangeListener { (_, _) ⇒ reloadEventsAggregator.value = System.currentTimeMillis() }
      //      Data.elementTemplates.addChangeListener { event ⇒ reloadEventsAggregator.value = System.currentTimeMillis() }
      //      Observables.observeDelayedValue(aggregatorDelay, reloadEventsAggregator).addValueChangeListener(new IValueChangeListener {
      //        def handleValueChange(event: ValueChangeEvent) = View.views.keys.foreach(view ⇒ View.withRedrawDelayed(view) {
      //          view.reload()
      //        })
      //      })
      // handle structural changes
      //      Observables.observeDelayedValue(aggregatorDelay, refreshEventsAggregator).addValueChangeListener(new IValueChangeListener {
      //        def handleValueChange(event: ValueChangeEvent) = View.views.keys.foreach(view ⇒ View.withRedrawDelayed(view) {
      //          val set = refreshEventsAggregator.value
      //          refreshEventsAggregator.value = Set()
      //          if (set.nonEmpty)
      //            view.refresh(set.toArray)
      //        })
      //      })
    }
    super.preStart()
  }
  override def receive: Actor.Receive = super.receive orElse {
    case message @ App.Message.Set(_, marker: GraphMarker) ⇒ App.traceMessage(message) {
      if (terminated)
        App.Message.Error(s"${this} is terminated.", self)
      else
        App.exec { assignGraphMarker(marker) }
    }

    //    case message @ Event.ChildInclude(element, newElement, _) => App.traceMessage(message) {
    //      if (element.eStash.model.forall(_ eq Model.inner))
    //        App.exec {
    //          View.views.keys.foreach { view =>
    //            if (view.ActionToggleExpand.isChecked())
    //              if (element.eChildren.size == 1) // if 1st child
    //                view.tree.expandedItems += TreeProxy.Item(element) // expand parent
    //              else
    //                view.tree.expandedItems ++= newElement.eChildren.iteratorRecursive().map(TreeProxy.Item(_)) // expand children
    //          }
    //          refreshEventsAggregator.value = refreshEventsAggregator.value + element
    //        }
    //    }
    //
    //    case message @ Event.ChildRemove(element, _, _) => App.traceMessage(message) {
    //      if (element.eStash.model.forall(_ eq Model.inner))
    //        App.exec { refreshEventsAggregator.value = refreshEventsAggregator.value + element }
    //    }
    //
    //    case message @ Event.ChildrenReset(element, _) => App.traceMessage(message) {
    //      if (element.eStash.model.forall(_ eq Model.inner))
    //        App.exec { refreshEventsAggregator.value = refreshEventsAggregator.value + element }
    //    }
    //
    //    case message @ Event.ChildReplace(element, _, _, _) => App.traceMessage(message) {
    //      if (element.eStash.model.forall(_ eq Model.inner))
    //        App.exec { refreshEventsAggregator.value = refreshEventsAggregator.value + element }
    //    }
    //
    //    case message @ Event.StashReplace(element, _, _, _) => App.traceMessage(message) {
    //      if (element.eStash.model.forall(_ eq Model.inner))
    //        App.exec { updateEventsAggregator.value = updateEventsAggregator.value + element }
    //    }
    //
    //    case message @ Event.ValueInclude(element, _, _) => App.traceMessage(message) {
    //      if (element.eStash.model.forall(_ eq Model.inner))
    //        App.exec { updateEventsAggregator.value = updateEventsAggregator.value + element }
    //    }
    //
    //    case message @ Event.ValueRemove(element, _, _) => App.traceMessage(message) {
    //      if (element.eStash.model.forall(_ eq Model.inner))
    //        App.exec { updateEventsAggregator.value = updateEventsAggregator.value + element }
    //    }
    //
    //    case message @ Event.ValueUpdate(element, _, _, _) => App.traceMessage(message) {
    //      if (element.eStash.model.forall(_ eq Model.inner))
    //        App.exec { updateEventsAggregator.value = updateEventsAggregator.value + element }
    //    }
    //
    //    case message @ Event.ModelReplace(_, _, _) => App.traceMessage(message) {
    //      App.exec { Tree.FilterSystemElement.updateSystemElement }
    //    }
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
  /**
   * Create view content.
   * @return content container.
   */
  //  protected def create(parent: VComposite): Option[Composite] = {
  //    if (view.nonEmpty)
  //      throw new IllegalStateException("Unable to create view. It is already created.")
  //    App.assertEventThread(false)
  //    view = Option(parent)
  //    val content = App.execNGet {
  //      //parent.getContext.set(Data.Id.featureViewDefinition, java.lang.Boolean.TRUE)
  //      new editor.View(parent, SWT.NONE)
  //    }
  //    Some(content)
  //  }
  /** Creates and returns this window's contents. */
  protected def createContents(parent: VComposite): Composite = {
    viewContext.set("style", SWT.NONE: Integer)
    val content = ContextInjectionFactory.make(classOf[Content], viewContext)

    //new Content(viewContext, parent, SWT.NONE)
    //    content.initializeSWT()
    //    JFX.exec { content.initializeJFX() }
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
    val features: Seq[String] = Seq(Logic.Feature.graph)

    /** Editor view actor reference configuration object. */
    def props = DI.props
  }
  /**
   * Dependency injection routines.
   */
  private object DI extends XDependencyInjection.PersistentInjectable {
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
