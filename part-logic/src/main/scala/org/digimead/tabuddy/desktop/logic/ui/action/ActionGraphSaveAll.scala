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

package org.digimead.tabuddy.desktop.logic.ui.action

import akka.actor.ActorDSL.{ Act, actor }
import javax.inject.Inject
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.definition.Context
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.ui.definition.Action
import org.digimead.tabuddy.desktop.logic.Messages
import org.digimead.tabuddy.desktop.logic.behaviour.TrackActiveGraph
import org.digimead.tabuddy.desktop.logic.operation.graph.OperationGraphSave
import org.digimead.tabuddy.desktop.logic.payload.marker.GraphMarker
import org.digimead.tabuddy.model.graph.{ Event ⇒ GraphEvent }
import org.eclipse.core.runtime.jobs.Job
import org.eclipse.swt.widgets.Event
import scala.collection.mutable
import scala.collection.mutable.{ Publisher, Subscriber }
import scala.concurrent.Future

/**
 * Save all modified graphs.
 */
class ActionGraphSaveAll @Inject() (windowContext: Context) extends Action(Messages.saveAllFiles_text) with Loggable {
  /** Akka execution context. */
  implicit lazy val ec = App.system.dispatcher

  ActionGraphSaveAll.register(this)

  /** Runs this action, passing the triggering SWT event. */
  @log
  override def runWithEvent(event: Event) = Future {
    val unsaved = GraphMarker.list().map(GraphMarker(_)).filter(m ⇒ m.graphIsOpen() && m.graphIsDirty())
    unsaved.foreach { marker ⇒
      val graph = marker.safeRead(_.graph)
      OperationGraphSave(graph, false).foreach { operation ⇒
        operation.getExecuteJob() match {
          case Some(job) ⇒
            job.setPriority(Job.LONG)
            job.schedule()
          case None ⇒
            log.fatal(s"Unable to create job for ${operation}.")
        }
      }
    }
  }
}

object ActionGraphSaveAll extends Loggable {
  /** Akka system. */
  implicit lazy val akka = App.system
  /** Akka execution context. */
  implicit lazy val ec = App.system.dispatcher
  /** List of all actions. */
  private val actions = mutable.WeakHashMap[ActionGraphSaveAll, Unit]()
  /** Track graph markers events. */
  private val appEventListener = actor(new Act {
    become {
      case App.Message.Save(marker: GraphMarker, _, _) ⇒
        if (enabled) Future { update(TrackActiveGraph.active.exists(_.graphIsDirty())) } // disable if clean
      case App.Message.Save(_, _, _) ⇒
    }
    whenStarting { App.system.eventStream.subscribe(self, classOf[App.Message.Save[_]]) }
    whenStopping { App.system.eventStream.unsubscribe(self, classOf[App.Message.Save[_]]) }
  })
  /** Action enabled flag. */
  @volatile private var enabled = false
  /** Synchronization lock. */
  private val lock = new Object

  TrackActiveGraph.addListener(GraphMarkerListener,
    () ⇒ enabled = TrackActiveGraph.active.exists(_.graphIsDirty()))

  /** Update action state. */
  def update(action: ActionGraphSaveAll, state: Boolean): Unit = App.exec {
    if (action.isEnabled() != state) {
      action.setEnabled(state)
      action.updateEnabled()
    }
  }
  /** Update actions state. */
  def update(state: Boolean): Unit = lock.synchronized {
    if (enabled != state) {
      enabled = state
      if (enabled)
        log.debug(s"Enable '${Messages.saveAllFiles_text}' action")
      else
        log.debug(s"Disable '${Messages.saveAllFiles_text}' action")
      actions.foreach { case (action, _) ⇒ update(action, state) }
    }
  }

  /** Register action in action map. */
  protected def register(action: ActionGraphSaveAll) = lock.synchronized {
    actions(action) = ()
    update(action, enabled)
  }

  /** Track active graphs. */
  object GraphMarkerListener extends TrackActiveGraph.Listener {
    /** Remove marker from tracked objects. */
    def close(marker: GraphMarker) {
      /* All subscribers already removed. */
      if (enabled) Future { update(TrackActiveGraph.active.exists(_.graphIsDirty())) } // disable if clean
    }
    /** Add marker to tracked objects. */
    def open(marker: GraphMarker) = marker.safeRead(state ⇒ if (marker.graphIsOpen()) {
      state.graph.subscribe(GraphEventListener)
      if (!enabled) Future { update(TrackActiveGraph.active.exists(_.graphIsDirty())) } // enable if dirty
    })
  }
  /** Track graphs events. */
  object GraphEventListener extends Subscriber[GraphEvent, Publisher[GraphEvent]] {
    def notify(pub: Publisher[GraphEvent], event: GraphEvent) =
      if (!enabled) Future { update(TrackActiveGraph.active.exists(_.graphIsDirty())) } // enable if dirty
  }
}
