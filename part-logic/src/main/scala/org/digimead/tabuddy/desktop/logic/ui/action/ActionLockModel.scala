/**
 * This file is part of the TA Buddy project.
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

package org.digimead.tabuddy.desktop.logic.action
/*
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.Core
import org.digimead.tabuddy.desktop.core.Messages
import org.digimead.tabuddy.desktop.core.definition.Context.rich2appContext
import org.digimead.tabuddy.desktop.logic.Data
import org.digimead.tabuddy.desktop.logic.operation.OperationModelClose
import org.digimead.tabuddy.desktop.logic.operation.OperationModelNew
import org.digimead.tabuddy.desktop.logic.operation.OperationModelOpen
import org.digimead.tabuddy.desktop.logic.payload.Payload
import org.digimead.tabuddy.desktop.logic.payload.Payload.payload2implementation
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.support.App.app2implementation
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.Model.model2implementation
import org.digimead.tabuddy.model.element.Element
import org.eclipse.core.runtime.jobs.Job
import org.eclipse.e4.core.contexts.Active
import org.eclipse.e4.core.contexts.ContextInjectionFactory
import org.eclipse.e4.core.di.annotations.Optional
import org.eclipse.jface.action.{ Action => JFaceAction }
import org.eclipse.jface.action.IAction
import org.eclipse.swt.widgets.Event

import akka.actor.Props
import javax.inject.Inject
import javax.inject.Named

/** Lock/unlock model. */
class ActionLockModel private () extends JFaceAction(Messages.lock_text, IAction.AS_CHECK_BOX) with Loggable {
  @volatile protected var enabled = false
  ContextInjectionFactory.inject(ActionLockModel.this, Core.context)

  override def isEnabled(): Boolean = super.isEnabled() && (enabled || isChecked)
  override def isChecked(): Boolean = Model.eId != Payload.defaultModel.eId
  /** Invoked at every modification of Data.Id.modelIdUserInput. */
  @Inject @Optional // @log
  def onModelIdUserInputChanged(@Active @Named(Data.Id.modelIdUserInput) id: String) =
    if ((id != null && id.trim.nonEmpty) != enabled) {
      enabled = !enabled
      updateEnabled()
    }
  /** Runs this action, passing the triggering SWT event. */
  @log
  override def runWithEvent(event: Event) {
    val context = Core.context.getActiveLeaf()
    if (Model.eId == Payload.defaultModel.eId) {
      // There is the default model. Load different model.
      val id = context.get(Data.Id.modelIdUserInput).asInstanceOf[String]
      if (id.nonEmpty)
        Payload.listModels.find(marker => marker.isValid && marker.id.name == id) match {
          case Some(marker) =>
            OperationModelOpen(Some(Model.eId), Symbol(id), false) foreach { operation =>
              operation.getExecuteJob() match {
                case Some(job) =>
                  job.setPriority(Job.SHORT)
                  job.schedule()
                case None =>
                  log.fatal(s"Unable to create job for ${operation}.")
              }
            }
          case None =>
            OperationModelNew(Some(id), None, true) foreach { operation =>
              operation.getExecuteJob() match {
                case Some(job) =>
                  job.setPriority(Job.SHORT)
                  job.schedule()
                case None =>
                  log.fatal(s"Unable to create job for ${operation}.")
              }
            }
        }
    } else {
      // Something already loaded. Close.
      OperationModelClose(Model.eId, false) foreach { operation =>
        operation.getExecuteJob() match {
          case Some(job) =>
            job.setPriority(Job.SHORT)
            job.schedule()
          case None =>
            log.fatal(s"Unable to create job for ${operation}.")
        }
      }
    }
  }
  override def setChecked(checked: Boolean) {
    val checked = Model.eId != Payload.defaultModel.eId
    super.setChecked(checked)
    updateChecked
  }

  /** Update enabled action state. */
  protected def updateEnabled() = if (isEnabled)
    firePropertyChange(IAction.ENABLED, java.lang.Boolean.FALSE, java.lang.Boolean.TRUE)
  else
    firePropertyChange(IAction.ENABLED, java.lang.Boolean.TRUE, java.lang.Boolean.FALSE)
  /** Update checked action state. */
  protected def updateChecked() = if (isChecked)
    firePropertyChange(IAction.CHECKED, java.lang.Boolean.FALSE, java.lang.Boolean.TRUE)
  else
    firePropertyChange(IAction.CHECKED, java.lang.Boolean.TRUE, java.lang.Boolean.FALSE)

  //Core.context.runAndTrack(new Listener)
  //override def isEnabled(): Boolean = {
  //  val context = Core.context.getActiveLeaf()
  //  val modelIdUserInput = Option(context.get(Data.Id.modelIdUserInput).asInstanceOf[String]).getOrElse("")
  //  super.isEnabled && (modelIdUserInput.nonEmpty || isChecked)
  //}
  /** Track Data.Id.modelIdUserInput. */
  /*class Listener extends RunAndTrack() {
    /** Sequence of active branch contexts. */
    @volatile var activeBranch = Seq(Core.context: AppContext)
    /** Last enabled state. */
    @volatile var lastEnabledState = isEnabled
    AppContext.Event.subscribe(Data.Id.modelIdUserInput, modelIdUserInputChanged _)

    /** Track active branch.*/
    override def changed(context: IEclipseContext): Boolean = {
      val activeContext = Core.context.getActiveLeaf()
      activeBranch = activeContext.getParents() :+ activeContext
      // Update action state when the active branch changes
      modelIdUserInputChanged(Data.Id.modelIdUserInput, activeContext)
      true
    }
    /** Fire event if input changed within active context. */
    def modelIdUserInputChanged(name: String, context: AppContext) = {
      if (activeBranch.contains(context) && lastEnabledState != isEnabled) {
        firePropertyChange(IAction.ENABLED, lastEnabledState, !lastEnabledState)
        lastEnabledState = !lastEnabledState
      }
    }
  }*/
}

object ActionLockModel extends Loggable {
  /** Singleton identificator. */
  val id = getClass.getSimpleName().dropRight(1)
  /** Lock action. */
  @volatile protected var action: Option[ActionLockModel] = None

  /** Returns lock action. */
  def apply(): ActionLockModel = action.getOrElse {
    val lockAction = App.execNGet { new ActionLockModel }
    action = Some(lockAction)
    lockAction
  }
  /** Lock action actor reference configuration object. */
  def props = DI.props

  /** Lock action actor. */
  class Actor extends akka.actor.Actor {
    log.debug("Start actor " + self.path)

    /** Is called asynchronously after 'actor.stop()' is invoked. */
    override def postStop() = {
      App.system.eventStream.unsubscribe(self, classOf[Element.Event.ModelReplace[_ <: Model.Like, _ <: Model.Like]])
      log.debug(self.path.name + " actor is stopped.")
    }
    /** Is called when an Actor is started. */
    override def preStart() {
      App.system.eventStream.subscribe(self, classOf[Element.Event.ModelReplace[_ <: Model.Like, _ <: Model.Like]])
      log.debug(self.path.name + " actor is started.")
    }
    def receive = {
      case message @ Element.Event.ModelReplace(oldModel, newModel, modified) => App.traceMessage(message) {
        action.foreach(action => App.exec { action.updateChecked })
      }
    }
  }
  /**
   * Dependency injection routines.
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** Lock actor reference configuration object. */
    lazy val props = injectOptional[Props]("Logic.Action.Lock") getOrElse Props[ActionLockModel.Actor]
  }
}
*/