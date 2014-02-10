/**
 * This file is part of the TA Buddy project.
 * Copyright (c) 2013 Alexey Aksenov ezh@ezh.msk.ru
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

package org.digimead.tabuddy.desktop.view.modification.ui.action

//import org.digimead.digi.lib.aop.log
//import org.digimead.digi.lib.api.DependencyInjection
//import org.digimead.digi.lib.log.api.Loggable
//import org.digimead.tabuddy.desktop.Messages
//import org.digimead.tabuddy.desktop.core.definition.Operation
//import org.digimead.tabuddy.desktop.logic.Data
//import org.digimead.tabuddy.desktop.logic.operation.view.OperationModifySortingList
//import org.digimead.tabuddy.desktop.logic.payload.Payload
//import org.digimead.tabuddy.desktop.logic.payload.view.Sorting
//import org.digimead.tabuddy.desktop.core.support.App
//import org.digimead.tabuddy.desktop.core.support.App.app2implementation
//import org.digimead.tabuddy.model.Model
//import org.digimead.tabuddy.model.Model.model2implementation
//import org.digimead.tabuddy.model.element.Element
//import org.eclipse.core.runtime.jobs.Job
//import org.eclipse.jface.action.{ Action => JFaceAction }
//import org.eclipse.jface.action.IAction
//import org.eclipse.swt.widgets.Event
//
//import akka.actor.Props
//
///** Modify sorting list. */
//class ActionModifySortingList private () extends JFaceAction(Messages.sortings_text) with Loggable {
//  override def isEnabled(): Boolean = super.isEnabled && (Model.eId != Payload.defaultModel.eId)
//  /** Runs this action, passing the triggering SWT event. */
//  @log
//  override def runWithEvent(event: Event) =
//    OperationModifySortingList(Data.getAvailableViewSortings - Sorting.simpleSorting).foreach { operation =>
//      operation.getExecuteJob() match {
//        case Some(job) =>
//          job.setPriority(Job.SHORT)
//          job.onComplete(_ match {
//            case Operation.Result.OK(result, message) =>
//              log.info(s"Operation completed successfully: ${result}")
//              result.foreach { case (sortings) => Sorting.save(sortings) }
//            case Operation.Result.Cancel(message) =>
//              log.warn(s"Operation canceled, reason: ${message}.")
//            case other =>
//              log.error(s"Unable to complete operation: ${other}.")
//          }).schedule()
//          job.schedule()
//        case None =>
//          log.fatal(s"Unable to create job for ${operation}.")
//      }
//    }
//
//  /** Update enabled action state. */
//  protected def updateEnabled() = if (isEnabled)
//    firePropertyChange(IAction.ENABLED, java.lang.Boolean.FALSE, java.lang.Boolean.TRUE)
//  else
//    firePropertyChange(IAction.ENABLED, java.lang.Boolean.TRUE, java.lang.Boolean.FALSE)
//}
//
//object ActionModifySortingList extends Loggable {
//  /** Singleton identificator. */
//  val id = getClass.getSimpleName().dropRight(1)
//  /** ModifySortingList action. */
//  @volatile protected var action: Option[ActionModifySortingList] = None
//
//  /** Returns ModifySortingList action. */
//  def apply(): ActionModifySortingList = action.getOrElse {
//    val modifySortingListAction = App.execNGet { new ActionModifySortingList }
//    action = Some(modifySortingListAction)
//    modifySortingListAction
//  }
//  /** ModifySortingList action actor reference configuration object. */
//  def props = DI.props
//
//  /** ModifySortingList action actor. */
//  class Actor extends akka.actor.Actor {
//    log.debug("Start actor " + self.path)
//
//    /** Is called asynchronously after 'actor.stop()' is invoked. */
//    override def postStop() = {
//      App.system.eventStream.unsubscribe(self, classOf[Element.Event.ModelReplace[_ <: Model.Interface[_ <: Model.Stash], _ <: Model.Interface[_ <: Model.Stash]]])
//      log.debug(self.path.name + " actor is stopped.")
//    }
//    /** Is called when an Actor is started. */
//    override def preStart() {
//      App.system.eventStream.subscribe(self, classOf[Element.Event.ModelReplace[_ <: Model.Interface[_ <: Model.Stash], _ <: Model.Interface[_ <: Model.Stash]]])
//      log.debug(self.path.name + " actor is started.")
//    }
//    def receive = {
//      case message @ Element.Event.ModelReplace(oldModel, newModel, modified) => App.traceMessage(message) {
//        action.foreach(action => App.exec { action.updateEnabled })
//      }
//    }
//  }
//  /**
//   * Dependency injection routines.
//   */
//  private object DI extends DependencyInjection.PersistentInjectable {
//    /** ModifySortingList actor reference configuration object. */
//    lazy val props = injectOptional[Props]("ViewModification.Action.ModifySortingList") getOrElse Props[ActionModifySortingList.Actor]
//  }
//}