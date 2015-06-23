/**
 * This file is part of the TA Buddy project.
 * Copyright (c) 2014-2015 Alexey Aksenov ezh@ezh.msk.ru
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

import javax.inject.{ Inject, Named }
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.definition.Context
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.ui.definition.widget.VComposite
import org.digimead.tabuddy.desktop.logic.Logic
import org.digimead.tabuddy.desktop.view.modification.{ Messages, bundleId }
import org.eclipse.e4.core.contexts.Active
import org.eclipse.e4.core.di.annotations.Optional
import org.eclipse.jface.action.{ Action, IAction }
import org.eclipse.jface.util.{ IPropertyChangeListener, PropertyChangeEvent }

/**
 * Hide system elements.
 * unchecked - filter is enabled
 * checked - filter is disabled
 * by default - enabled
 */
class ActionToggleSystem @Inject extends Action(Messages.systemElements_text, IAction.AS_CHECK_BOX) with XLoggable {
  setId(ActionToggleSystem.id)
  setChecked(false)
  setEnabled(false)
  /** Last active content context. */
  @volatile protected var contentContext = Option.empty[Context]
  /** Default value. */
  val default = java.lang.Boolean.TRUE

  initialize()

  def apply() = contentContext match {
    case Some(contentContext) ⇒
      Option(contentContext.getLocal(Logic.Id.stateOfToggleSystem)) match {
        case Some(java.lang.Boolean.TRUE) ⇒
          if (!isChecked())
            contentContext.set(Logic.Id.stateOfToggleSystem, java.lang.Boolean.FALSE)
        case Some(java.lang.Boolean.FALSE) ⇒
          if (isChecked())
            contentContext.set(Logic.Id.stateOfToggleSystem, java.lang.Boolean.TRUE)
        case _ ⇒
          contentContext.set(Logic.Id.stateOfToggleSystem, isChecked(): java.lang.Boolean)
      }
    case None ⇒
      setChecked(!isChecked())
  }
  override def run() = apply()

  protected def initialize() {
    addPropertyChangeListener(PropertyChangeListener)
  }
  @Inject @Optional @log
  protected def onStateChanged(@Active @Named(Logic.Id.stateOfToggleSystem) state: java.lang.Boolean) = App.exec {
    if (isChecked() != state)
      setChecked(state)
  }
  /** Invoked on view activation. */
  @Inject @Optional @log
  protected def onViewChanged(@Active vComposite: VComposite): Unit = App.exec {
    if (vComposite.factory().features.contains(Logic.Feature.viewToggleSystem)) {
      if (!isEnabled())
        setEnabled(true)
      this.contentContext = for (contentContext ← Option(vComposite).flatMap(_.getContentContext())) yield {
        Option(contentContext.getLocal(Logic.Id.stateOfToggleSystem)) match {
          case Some(java.lang.Boolean.TRUE) ⇒
            if (isChecked())
              setChecked(true)
          case Some(java.lang.Boolean.FALSE) ⇒
            if (!isChecked())
              setChecked(false)
          case _ ⇒
            contentContext.set(Logic.Id.stateOfToggleSystem, default)
        }
        contentContext
      }
    } else {
      this.contentContext = None
      if (isEnabled())
        setEnabled(false)
      if (isChecked())
        setChecked(false)
    }
  }

  object PropertyChangeListener extends IPropertyChangeListener {
    def propertyChange(e: PropertyChangeEvent) = contentContext.foreach { ctx ⇒
      val value = e.getNewValue
      if (ctx.getLocal(Logic.Id.stateOfToggleSystem) != value)
        ctx.set(Logic.Id.stateOfToggleSystem, value)
    }
  }
}

object ActionToggleSystem {
  val id = bundleId + "#ToggleSystemElements"
}
