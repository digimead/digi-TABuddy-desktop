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

package org.digimead.tabuddy.desktop.view.modification.ui.action

import javax.inject.Inject
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.definition.Context
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.ui.definition.ToolBarManager
import org.digimead.tabuddy.desktop.core.ui.definition.widget.{ AppWindow, VComposite }
import org.digimead.tabuddy.desktop.logic.Logic
import org.eclipse.e4.core.contexts.{ Active, ContextInjectionFactory }
import org.eclipse.e4.core.di.annotations.Optional
import org.eclipse.jface.action.CoolBarManager
import org.eclipse.swt.widgets.{ Composite, ToolBar }
import scala.ref.WeakReference

/**
 * ToolBar manager that contains ContributionSelectView, ContributionSelectFilter, ContributionSelectSorting
 */
class ViewToolBarManager @Inject() (windowContext: Context) extends ToolBarManager with Loggable {
  /** CoolBar */
  @volatile protected var coolBarManager = WeakReference[CoolBarManager](null)
  /** Flag indicating whether the toolbar manager is visible. */
  @volatile protected var visible = false

  if (windowContext.getLocal(classOf[AppWindow]) == null)
    throw new IllegalArgumentException(s"${windowContext} does not contain AppWindow.")

  /** Set ToolBarManager contribution after toolbar is created and hide it if needed. */
  override def setToolBarManagerContribution(arg: org.eclipse.jface.action.ToolBarContributionItem) {
    super.setToolBarManagerContribution(arg)
    // Hide toolbar if there is no active VComposite
    arg.setVisible(Option(windowContext.getActive(classOf[VComposite])).nonEmpty)
    getCoolBarManager.foreach(_.update(true))
  }

  /** Get coolbar manager for this toolbar manager. */
  protected def getCoolBarManager(): Option[CoolBarManager] = coolBarManager.get orElse {
    Option(windowContext.get(classOf[AppWindow])).map { window ⇒
      val coolbar = window.getCoolBarManager()
      coolBarManager = WeakReference(coolbar)
      coolbar
    }
  }
  /** Invoked on view activation. */
  @Inject @Optional
  protected def onViewChanged(@Active vComposite: VComposite): Unit = App.exec {
    visible = vComposite.factory().features.contains(Logic.Feature.viewDefinition)
    contribution.get.foreach(_.setVisible(visible))
    getCoolBarManager.foreach(_.update(true))
  }
}
