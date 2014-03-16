/**
 * This file is part of the TA Buddy project.
 * Copyright (c) 2012-2014 Alexey Aksenov ezh@ezh.msk.ru
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
import org.digimead.tabuddy.desktop.core.ui.definition.widget.VComposite
import org.digimead.tabuddy.desktop.logic.Messages
import org.digimead.tabuddy.desktop.logic.operation.graph.OperationGraphSave
import org.digimead.tabuddy.desktop.logic.payload.maker.GraphMarker
import org.digimead.tabuddy.model.graph.{ Event ⇒ GraphEvent }
import org.eclipse.core.runtime.jobs.Job
import org.eclipse.e4.core.contexts.Active
import org.eclipse.e4.core.di.annotations.Optional
import org.eclipse.swt.widgets.Event
import scala.collection.mutable
import scala.collection.mutable.{ Publisher, Subscriber }
import scala.concurrent.Future

/** Save the opened model. */
class ActionGraphSave @Inject() (windowContext: Context) extends Action(Messages.saveFile_text) with Loggable {
  /** Akka execution context. */
  implicit lazy val ec = App.system.dispatcher
  @volatile protected var marker = Option.empty[GraphMarker]

  ActionGraphSave.register(this)

  /** Runs this action, passing the triggering SWT event. */
  @log
  override def runWithEvent(event: Event) = marker.foreach { marker ⇒
    val graph = marker.safeRead(_.graph)
    OperationGraphSave(graph, false).foreach { operation ⇒
      operation.getExecuteJob() match {
        case Some(job) ⇒
          job.setPriority(Job.LONG)
          job.schedule()
        case None ⇒
          throw new RuntimeException(s"Unable to create job for ${operation}.")
      }
    }
  }

  /** Track the state of the current marker. */
  def trackMarker(marker: GraphMarker) = marker.safeRead { state ⇒
    state.graph.subscribe(GraphEventListener)
  }
  /** Stop tracking the state of the marker. */
  def untrackMarker(marker: GraphMarker) = marker.safeRead { state ⇒
    state.graph.removeSubscription(GraphEventListener)
  }
  /** Update action state. */
  @Inject
  protected def update(@Optional @Active vComposite: VComposite, @Optional @Active marker: GraphMarker) = marker match {
    case marker: GraphMarker if !isEnabled && marker.graphIsDirty() ⇒
      this.marker = Option(marker)
      trackMarker(marker)
      App.exec {
        setEnabled(true)
        updateEnabled()
      }
    case marker: GraphMarker if isEnabled && marker.graphIsDirty() ⇒
    case _ if isEnabled ⇒
      this.marker.foreach(untrackMarker)
      this.marker = None
      App.exec {
        setEnabled(false)
        updateEnabled()
      }
    case _ ⇒
  }

  /** Track graphs events. */
  object GraphEventListener extends Subscriber[GraphEvent, Publisher[GraphEvent]] {
    def notify(pub: Publisher[GraphEvent], event: GraphEvent) =
      if (!isEnabled()) Future { marker.foreach(marker ⇒ update(null, marker)) } // enable if dirty
  }
}

object ActionGraphSave {
  /** Akka system. */
  implicit lazy val akka = App.system
  /** Akka execution context. */
  implicit lazy val ec = App.system.dispatcher
  /** List of all actions. */
  private val actions = new mutable.WeakHashMap[ActionGraphSave, Unit]() with mutable.SynchronizedMap[ActionGraphSave, Unit]
  /** Track graph markers events. */
  private val appEventListener = actor(new Act {
    become {
      case App.Message.Save(marker: GraphMarker, _, _) ⇒ Future {
        actions.keys.foreach { action ⇒
          if (action.marker.map(_.uuid) == Some(marker.uuid))
            action.update(null, marker)
        }
      }
      case App.Message.Save(_, _, _) ⇒
    }
    whenStarting { App.system.eventStream.subscribe(self, classOf[App.Message.Save[_]]) }
    whenStopping { App.system.eventStream.unsubscribe(self, classOf[App.Message.Save[_]]) }
  })

  /** Register action in action map. */
  protected def register(action: ActionGraphSave) = actions(action) = ()
}
