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

package org.digimead.tabuddy.desktop.logic.ui.view.graph

import java.util.concurrent.atomic.AtomicReference
import javafx.animation.{ FadeTransition, FadeTransitionBuilder }
import javafx.animation.{ PathTransitionBuilder, Transition }
import javafx.animation.PathTransition.OrientationType
import javafx.beans.value.{ ChangeListener, ObservableValue }
import javafx.event.{ ActionEvent, EventHandler }
import javafx.geometry.VPos
import javafx.scene.{ Group, Scene }
import javafx.scene.effect.DropShadow
import javafx.scene.layout.{ HBox, Pane, VBox }
import javafx.scene.paint.Color
import javafx.scene.shape.Rectangle
import javafx.scene.text.{ Font, FontPosture, FontWeight, Text, TextAlignment, TextBuilder }
import javafx.util.Duration
import org.digimead.digi.lib.jfx4swt.FXCanvas
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.Report
import org.digimead.tabuddy.desktop.core.definition.Operation
import org.digimead.tabuddy.desktop.core.operation.OperationInfo
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.ui.{ ResourceManager, UI }
import org.eclipse.core.runtime.jobs.Job
import org.eclipse.swt.SWT
import org.eclipse.swt.graphics.Point
import org.eclipse.swt.layout.{ FormAttachment, FormData, GridData, GridLayout }
import org.eclipse.swt.widgets.{ Composite, Control, Event, Listener }

/**
 * Graph content.
 */
class Content(parent: Composite, style: Int = SWT.NONE) extends ContentSkel(parent, style) with Loggable {
  def initializeJFX() {
  }
  def initializeSWT() {
  }
  def graphChanged() {
    log.___glance("!!!")
  }
}
