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

package org.digimead.tabuddy.desktop.core.ui.definition.widget

import akka.actor.{ ActorRef, actorRef2Scala }
import java.util.UUID
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.ui.UI
import org.digimead.tabuddy.desktop.core.ui.block.WindowSupervisor
import org.eclipse.swt.custom.{ CTabFolder, ScrolledComposite }
import org.eclipse.swt.events.{ DisposeEvent, DisposeListener, SelectionAdapter, SelectionEvent }

class SCompositeTab(val id: UUID, val ref: ActorRef, parent: ScrolledComposite, style: Int)
  extends CTabFolder(parent, style) with SComposite with XLoggable {
  initialize

  /** Returns the receiver's parent, which must be a ScrolledComposite. */
  override def getParent(): ScrolledComposite = super.getParent.asInstanceOf[ScrolledComposite]
  /** Initialize current tab composite. */
  protected def initialize() {
    // Add an event listener to pass the selected tab to WindowSupervisor
    addSelectionListener(new SelectionAdapter() {
      override def widgetSelected(event: SelectionEvent) = getSelection().getControl() match {
        case composite: ScrolledComposite if composite.getContent().isInstanceOf[VComposite] ⇒
          val viewLayerComposite = composite.getContent()
          App.exec {
            // After item will be selected, but will not block UI thread.
            UI.widgetHierarchy(viewLayerComposite).lastOption match {
              case Some(wComposite) ⇒
                log.debug(s"Start tab item with ${viewLayerComposite}.")
                WindowSupervisor.actor ! App.Message.Start((wComposite.id, viewLayerComposite), ref)
              case None ⇒
                log.debug("Skip tab item with ${viewLayerComposite}: layer is destroyed.")
            }
          }
        case composite: ScrolledComposite if composite.getContent() == null ⇒
          log.debug("Skip selection event for the empty tab.")
        case null ⇒ // sometimes we try to select the deleted tab
        case unexpected ⇒
          log.fatal(s"Tab item contains unexpected JFace element: ${unexpected}.")
      }
    })
    addDisposeListener(new DisposeListener {
      def widgetDisposed(e: DisposeEvent) {
        ref ! App.Message.Destroy(None, ref)
      }
    })
  }
}
