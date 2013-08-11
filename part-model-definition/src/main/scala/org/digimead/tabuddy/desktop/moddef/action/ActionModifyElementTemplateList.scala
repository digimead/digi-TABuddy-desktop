/**
 * This file is part of the TABuddy project.
 * Copyright (c) 2012-2013 Alexey Aksenov ezh@ezh.msk.ru
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

package org.digimead.tabuddy.desktop.moddef.action

import org.digimead.digi.lib.aop.log
import org.digimead.tabuddy.desktop.Messages
import org.digimead.digi.lib.log.api.Loggable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.future
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.Messages
import org.digimead.tabuddy.desktop.logic.payload.Payload
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.App.app2implementation
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.Model.model2implementation
import org.digimead.tabuddy.model.element.Element
import org.eclipse.jface.action.{ Action => JFaceAction }
import org.eclipse.jface.action.IAction
import org.eclipse.swt.widgets.Event
import akka.actor.Props
import org.digimead.tabuddy.desktop.logic.operation.OperationModifyElementTemplateList
import org.digimead.tabuddy.desktop.logic.Data
import org.eclipse.core.runtime.jobs.Job
import org.eclipse.core.runtime.jobs.JobChangeAdapter
import org.eclipse.core.runtime.jobs.IJobChangeEvent
import org.digimead.tabuddy.desktop.definition.Operation
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.IStatus
/*object ActionModifyElementTemplateList extends Action(Messages.elementTemplates_text) with Loggable {
  Data.modelName.addChangeListener { (name, event) => setEnabled(name != Payload.defaultModelIdentifier.name) }
  setEnabled(Data.modelName.value != Payload.defaultModelIdentifier.name)

  @log
  override def run = OperationModifyElementTemplateList(Data.elementTemplates.values.toSet).foreach(_.setOnSucceeded { job =>
    job.getValue.foreach { case (templates) => ElementTemplate.save(templates) }
  }.execute)
}*/

/** Modify element template list. */
class ActionModifyElementTemplateList private () extends JFaceAction(Messages.elementTemplates_text) with Loggable {
  override def isEnabled(): Boolean = super.isEnabled && (Model.eId != Payload.defaultModel.eId)
  /** Runs this action, passing the triggering SWT event. */
  @log
  override def runWithEvent(event: Event) = OperationModifyElementTemplateList(Data.elementTemplates.values.toSet).foreach { operation =>
    log.___glance("START++")
    val job = if (operation.canRedo())
      new Job(operation.getLabel()) {
        protected def run(monitor: IProgressMonitor): IStatus = Operation.history.redoOperation(operation, monitor, null)
      }
    else if (operation.canExecute())
      new Job(operation.getLabel()) {
        protected def run(monitor: IProgressMonitor): IStatus = Operation.history.execute(operation, monitor, null)
      }
    else
      null
    Option(job).foreach { job =>
      job.setPriority(Job.SHORT)
      job.schedule()
      job.addJobChangeListener(new JobChangeAdapter() {
        override def done(event: IJobChangeEvent) {
          if (event.getResult().isOK())
            log.___glance("Job completed successfully" + event.getResult())
          else
            log.___glance("Job did not complete successfully")
        }
      })
    }
    //job.getValue.foreach { case (templates) => ElementTemplate.save(templates) }
  }

  /** Update enabled action state. */
  protected def updateEnabled() = if (isEnabled)
    firePropertyChange(IAction.ENABLED, java.lang.Boolean.FALSE, java.lang.Boolean.TRUE)
  else
    firePropertyChange(IAction.ENABLED, java.lang.Boolean.TRUE, java.lang.Boolean.FALSE)
}

object ActionModifyElementTemplateList extends Loggable {
  /** Singleton identificator. */
  val id = getClass.getSimpleName().dropRight(1)
  /** Save action. */
  @volatile protected var action: Option[ActionModifyElementTemplateList] = None

  /** Returns close action. */
  def apply(): ActionModifyElementTemplateList = action.getOrElse {
    val modifyElementTemplateListAction = App.execNGet { new ActionModifyElementTemplateList }
    action = Some(modifyElementTemplateListAction)
    modifyElementTemplateListAction
  }
  /** Save action actor reference configuration object. */
  def props = DI.props

  /** Save action actor. */
  class Actor extends akka.actor.Actor {
    log.debug("Start actor " + self.path)

    /** Is called asynchronously after 'actor.stop()' is invoked. */
    override def postStop() = {
      App.system.eventStream.unsubscribe(self, classOf[Element.Event.ModelReplace[_ <: Model.Interface[_ <: Model.Stash], _ <: Model.Interface[_ <: Model.Stash]]])
      log.debug(self.path.name + " actor is stopped.")
    }
    /** Is called when an Actor is started. */
    override def preStart() {
      App.system.eventStream.subscribe(self, classOf[Element.Event.ModelReplace[_ <: Model.Interface[_ <: Model.Stash], _ <: Model.Interface[_ <: Model.Stash]]])
      log.debug(self.path.name + " actor is started.")
    }
    def receive = {
      case message @ Element.Event.ModelReplace(oldModel, newModel, modified) => App.traceMessage(message) {
        action.foreach(action => App.exec { action.updateEnabled })
      }
    }
  }
  /**
   * Dependency injection routines.
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** Save actor reference configuration object. */
    lazy val props = injectOptional[Props]("Logic.Action.ModifyElementTemplateList") getOrElse Props[ActionModifyElementTemplateList.Actor]
  }
}
