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

package org.digimead.tabuddy.desktop.ui.toolbar

import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.log.Loggable
import org.digimead.digi.lib.log.logger.RichLogger.rich2slf4j
import org.digimead.tabuddy.desktop.Resources
import org.digimead.tabuddy.desktop.res.Messages
import org.digimead.tabuddy.desktop.ui.action.ActionCoreStorageLoad
import org.digimead.tabuddy.desktop.ui.action.ActionCoreStorageSave
import org.digimead.tabuddy.desktop.ui.action.ActionCoreStorageSynchronize
import org.eclipse.jface.action.ControlContribution
import org.eclipse.jface.action.ToolBarManager
import org.eclipse.swt.SWT
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.widgets.Combo
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Control
import org.eclipse.swt.widgets.Label

object MainCoreStorage extends ToolBarManager with Loggable {
  @volatile protected var modelLabel: Option[Label] = None
  @volatile protected var modelCombo: Option[Combo] = None
  log.debug("alive")

  // initialize
  add(new ControlContribution(null) {
    protected def createControl(parent: Composite): Control = {
      val container = new Composite(parent, SWT.NONE)
      container.setLayout(new GridLayout(1, false))
      val label = new Label(container, SWT.NONE)
      label.setAlignment(SWT.CENTER);
      label.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, true, 1, 1))
      label.setText(Messages.coreModel_text)
      label.setToolTipText(Messages.coreModel_tooltip_text)
      label.setFont(Resources.fontSmall)
      modelLabel = Some(label)
      container
    }
  })
  add(new ControlContribution(null) {
    protected def createControl(parent: Composite): Control = {
      val combo = new Combo(parent, SWT.READ_ONLY)
      combo.setToolTipText(Messages.coreModel_tooltip_text)
      combo.add("one")
      combo.add("two")
      combo.add("three")
      modelCombo = Some(combo)
      combo
    }
  })
  add(ActionCoreStorageSynchronize)
  add(ActionCoreStorageLoad)
  add(ActionCoreStorageSave)

  @log
  override def dispose() {
    getItems().foreach(_.dispose())
    super.dispose()
  }
}

object CoreStorage {
  lazy val defaultItem = new Item(null) { override def toString() = "None" }

  case class Item(val name: String) {
    override def toString() = name
  }
}
