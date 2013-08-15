/**
 * This file is part of the TABuddy project.
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

package org.digimead.tabuddy.desktop.menu

import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.App.app2implementation
import org.eclipse.jface.action.ContributionItem
import org.eclipse.swt.SWT
import org.eclipse.swt.events.DisposeEvent
import org.eclipse.swt.events.DisposeListener
import org.eclipse.swt.events.SelectionAdapter
import org.eclipse.swt.events.SelectionEvent
import org.eclipse.swt.widgets.Menu
import org.eclipse.swt.widgets.MenuItem
import org.eclipse.ui.IPerspectiveDescriptor
import org.eclipse.ui.PlatformUI

/**
 * Dynamic menu with all available perspectives
 * Many thanks to Elias Volanakis for idea
 */
class SimplePerspectiveChooser extends ContributionItem {
  protected val menuListener = new SimplePerspectiveChooser.MenuListener

  override def fill(menu: Menu, index: Int) {
    /*    val activePerspective = App.getActivePerspectiveId()
    val perspectives = PlatformUI.getWorkbench().getPerspectiveRegistry().getPerspectives();
    for {
      i <- 0 until perspectives.length
      descriptor = perspectives(i)
    } {
      // i is used as an item index; 0-n will add items to the top of the menu
      val item = new MenuItem(menu, SWT.RADIO, i)
      item.setData(SimplePerspectiveChooser.KEY_PERSPECTIVE_DESCRIPTION, descriptor)
      item.setText(descriptor.getLabel())
      val image = descriptor.getImageDescriptor().createImage()
      item.setImage(image)
      item.addDisposeListener(new DisposeListener() {
        def widgetDisposed(e: DisposeEvent) {
          image.dispose()
        }
      })
      item.addSelectionListener(menuListener)
      if (Option(descriptor.getId()) == activePerspective)
        item.setSelection(true)
    }*/
  }
  override def isDynamic(): Boolean = true
}

object SimplePerspectiveChooser {
  private val KEY_PERSPECTIVE_DESCRIPTION = getClass.getName + ".PerspectiveDescriptor"
  /**
   * Switch perspective in the active page
   */
  class MenuListener extends SelectionAdapter {
    override def widgetSelected(e: SelectionEvent) {
      val item = e.widget.asInstanceOf[MenuItem]
      /*      if (item.getSelection()) {
        App.getActivePage().foreach { page =>
          val descriptor = item.getData(KEY_PERSPECTIVE_DESCRIPTION).asInstanceOf[IPerspectiveDescriptor]
          page.setPerspective(descriptor)
        }
      }*/
    }
  }
}
