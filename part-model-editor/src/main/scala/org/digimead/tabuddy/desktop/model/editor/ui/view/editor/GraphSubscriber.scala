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

package org.digimead.tabuddy.desktop.model.editor.ui.view.editor

import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.graph.{ Event, Graph }
import scala.collection.mutable
import org.digimead.tabuddy.desktop.core.support.WritableValue
import org.digimead.tabuddy.model.element.Element
import org.digimead.tabuddy.model.graph.Node
import org.digimead.tabuddy.desktop.core.support.App
import org.eclipse.core.databinding.observable.Observables
import org.eclipse.core.databinding.observable.value.IValueChangeListener
import org.eclipse.core.databinding.observable.value.ValueChangeEvent

/**
 * TableView graph subscriber.
 */
trait GraphSubscriber {
  this: Content ⇒
  /** Aggregation listener delay */
  private val aggregatorDelay = 250 // msec
  /** Structural changes(e.g. addition or removal of elements) aggregator */
  private val refreshEventsAggregator = WritableValue(Set[Node[_ <: Element]]())
  /** Minor changes(element modification) aggregator */
  private val updateEventsAggregator = WritableValue(Set[Node[_ <: Element]]())

  /**
   * Initialize subscriber aggregators.
   */
  protected def initializeSubscriber() {
    Observables.observeDelayedValue(aggregatorDelay, updateEventsAggregator).addValueChangeListener(new IValueChangeListener {
      def handleValueChange(event: ValueChangeEvent) = onPresentationChanges(event)
    })
    Observables.observeDelayedValue(aggregatorDelay, refreshEventsAggregator).addValueChangeListener(new IValueChangeListener {
      def handleValueChange(event: ValueChangeEvent) = onStructuralChanges(event)
    })
  }
  /**
   * Handle presentation changes of graph.
   */
  protected def onPresentationChanges(event: ValueChangeEvent): Unit = Content.withRedrawDelayed(GraphSubscriber.this) {
    val set = updateEventsAggregator.value
    updateEventsAggregator.value = Set()
    if (set.nonEmpty)
      GraphSubscriber.this.update(set.map(_.rootBox.e).toArray)
  }
  /**
   * Handle structural changes of graph.
   */
  protected def onStructuralChanges(event: ValueChangeEvent): Unit = Content.withRedrawDelayed(GraphSubscriber.this) {
    val set = refreshEventsAggregator.value
    refreshEventsAggregator.value = Set()
    if (set.nonEmpty)
      GraphSubscriber.this.refresh(set.map(_.rootBox.e).toArray)
  }
  object Subscriber extends mutable.Subscriber[Event, Graph[Model.Like]#Pub] {
    def notify(pub: Graph[Model.Like]#Pub, event: Event) = event match {
      case Event.NodeChange(node, oldState, newState) ⇒
        App.exec { updateEventsAggregator.value = updateEventsAggregator.value + node }
      case Event.GraphChange(parent, oldChild, newChild) ⇒
        App.exec { refreshEventsAggregator.value = refreshEventsAggregator.value + parent }
      case Event.GraphReset(graph) ⇒
        App.exec { refreshEventsAggregator.value = refreshEventsAggregator.value + graph.model.eNode }
      case Event.ValueInclude(source, newValue) ⇒
        App.exec { refreshEventsAggregator.value = updateEventsAggregator.value + source.eNode }
      case Event.ValueRemove(source, oldValue) ⇒
        App.exec { refreshEventsAggregator.value = updateEventsAggregator.value + source.eNode }
      case Event.ValueUpdate(source, oldValue, newValue) ⇒
        App.exec { refreshEventsAggregator.value = updateEventsAggregator.value + source.eNode }
    }
  }
}
