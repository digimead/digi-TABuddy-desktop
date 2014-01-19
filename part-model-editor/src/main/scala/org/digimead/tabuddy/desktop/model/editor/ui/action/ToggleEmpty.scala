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

package org.digimead.tabuddy.desktop.model.editor.ui.action
//
//import org.digimead.digi.lib.aop.log
//import org.digimead.digi.lib.api.DependencyInjection
//import org.digimead.digi.lib.log.api.Loggable
//import org.digimead.tabuddy.desktop.Core
//import org.digimead.tabuddy.desktop.Messages
//import org.digimead.tabuddy.desktop.definition.Context.rich2appContext
//import org.digimead.tabuddy.desktop.model.editor.Editor
//import org.digimead.tabuddy.desktop.support.App
//import org.digimead.tabuddy.desktop.support.App.app2implementation
//import org.eclipse.e4.core.contexts.Active
//import org.eclipse.e4.core.contexts.ContextInjectionFactory
//import org.eclipse.e4.core.di.annotations.Optional
//import org.eclipse.jface.action.{ Action => JFaceAction }
//import org.eclipse.jface.action.IAction
//
//import akka.actor.Props
//import javax.inject.Inject
//import javax.inject.Named
//
///** Toggle visibility of empty rows in the active view. */
//class ToggleEmpty extends JFaceAction(Messages.emptyRows_text, IAction.AS_CHECK_BOX) with Loggable {
//  @volatile protected var enabled = true
//
//  ContextInjectionFactory.inject(this, Core.context)
//
//  override def isEnabled(): Boolean = super.isEnabled && enabled
//  /** Update checked state from context. */
//  @Inject @Optional
//  def onStateOfToggleIdentificatorChanged(@Active @Named(Editor.Id.stateOfToggleEmpty) checked: java.lang.Boolean) =
//    Option(checked) foreach { checked => App.exec { if (checked != isChecked()) setChecked(checked) } }
//  /** Runs this action. */
//  @log
//  override def run() = for {
//    v <- App.getActiveView if v.getChildren().headOption.map(_.isInstanceOf[org.digimead.tabuddy.desktop.model.editor.ui.view.editor.View]).getOrElse(false)
//  } v.getContext.set(Editor.Id.stateOfToggleEmpty, isChecked(): java.lang.Boolean)
//
//  /** Update enabled action state. */
//  protected def updateEnabled() = if (isEnabled)
//    firePropertyChange(IAction.ENABLED, java.lang.Boolean.FALSE, java.lang.Boolean.TRUE)
//  else
//    firePropertyChange(IAction.ENABLED, java.lang.Boolean.TRUE, java.lang.Boolean.FALSE)
//}
//
//object ToggleEmpty extends Loggable {
//  /** Singleton identificator. */
//  val id = getClass.getSimpleName().dropRight(1)
//  /** ToggleEmpty action. */
//  @volatile protected var action: Option[ToggleEmpty] = None
//
//  /** Returns ToggleEmpty action. */
//  def apply(): ToggleEmpty = action.getOrElse {
//    val ToggleEmptyAction = App.execNGet { new ToggleEmpty }
//    action = Some(ToggleEmptyAction)
//    ToggleEmptyAction
//  }
//  /** ToggleEmpty actor reference configuration object. */
//  def props = DI.props
//
//  /** ToggleEmpty actor. */
//  class Actor extends akka.actor.Actor {
//    log.debug("Start actor " + self.path)
//
//    /** Is called asynchronously after 'actor.stop()' is invoked. */
//    override def postStop() = {
//      log.debug(self.path.name + " actor is stopped.")
//    }
//    /** Is called when an Actor is started. */
//    override def preStart() {
//      log.debug(self.path.name + " actor is started.")
//    }
//    def receive = {
//      case message if message == null =>
//    }
//  }
//  /**
//   * Dependency injection routines.
//   */
//  private object DI extends DependencyInjection.PersistentInjectable {
//    /** ToggleEmpty actor reference configuration object. */
//    lazy val props = injectOptional[Props]("Editor.Action.ToggleEmpty") getOrElse Props[ToggleEmpty.Actor]
//  }
//}
