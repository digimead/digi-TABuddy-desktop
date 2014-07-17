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

package org.digimead.tabuddy.desktop.model.editor.ui.view.editor.bar.editor

import javax.inject.{ Inject, Named }
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.{ Messages ⇒ CMessages }
import org.digimead.tabuddy.desktop.core.definition.Context
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.ui.definition.widget.VComposite
import org.digimead.tabuddy.desktop.logic.payload.marker.GraphMarker
import org.digimead.tabuddy.desktop.model.editor.ModelEditor
import org.eclipse.e4.core.di.annotations.Optional
import org.eclipse.jface.action.{ Action, IAction }
import scala.language.implicitConversions

/**
 * 'ToggleEmpty' action for an editor bar.
 */
class ToggleEmpty @Inject() (context: Context) extends Action(CMessages.emptyRows_text, IAction.AS_CHECK_BOX) with XLoggable {
  if (context.get(classOf[VComposite]) == null)
    throw new IllegalArgumentException(s"${context} does not contain VComposite.")

  override def isEnabled(): Boolean = super.isEnabled &&
    context.get(classOf[GraphMarker]) != null

  /** Update checked state from context. */
  @Inject @Optional
  def onStateChanged(@Named(ModelEditor.Id.stateOfToggleEmpty) checked: java.lang.Boolean) =
    Option(checked) foreach { checked ⇒ App.exec { if (checked != isChecked()) setChecked(checked) } }

  /** Runs this action, passing the triggering SWT event. */
  @log
  override def run = for {
    composite ← Option(context.get(classOf[VComposite]))
    marker ← Option(context.get(classOf[GraphMarker]))
  } context.set(ModelEditor.Id.stateOfToggleEmpty, isChecked(): java.lang.Boolean)

  /** Update enabled action state. */
  protected def updateEnabled() = if (isEnabled)
    firePropertyChange(IAction.ENABLED, java.lang.Boolean.FALSE, java.lang.Boolean.TRUE)
  else
    firePropertyChange(IAction.ENABLED, java.lang.Boolean.TRUE, java.lang.Boolean.FALSE)
  /** Invoked on marker modification. */
  @Inject @Optional
  protected def onMarkerChanged(@Optional marker: GraphMarker): Unit = App.exec { updateEnabled() }
}
