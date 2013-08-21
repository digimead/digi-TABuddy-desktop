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

package org.digimead.tabuddy.desktop.logic.action

import scala.collection.mutable

import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.Messages
import org.digimead.tabuddy.desktop.Resources
import org.digimead.tabuddy.desktop.Resources.resources2implementation
import org.eclipse.jface.action.ControlContribution
import org.eclipse.jface.layout.RowLayoutFactory
import org.eclipse.swt.SWT
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Control
import org.eclipse.swt.widgets.Label

class ContributionSelectFilter extends ControlContribution(ContributionSelectModel.id) with Loggable {
  val id = getClass.getName
  //@volatile protected var combo: Option[ComboViewer] = None
  //@volatile protected var label: Option[Label] = None
  ///** Id text value. */
  //protected val idValue = WritableValue("")

  ContributionSelectFilter.instance += (ContributionSelectFilter.this) -> {}

  /** Create contribution control. */
  override protected def createControl(parent: Composite): Control = {
    val container = new Composite(parent, SWT.NONE)
    val layout = RowLayoutFactory.fillDefaults().wrap(false).spacing(0).create()
    layout.marginLeft = 3
    layout.center = true
    container.setLayout(layout)
    val label = createLabel(container)
    container
  }
  protected def createLabel(parent: Composite): Label = {
    val container = new Composite(parent, SWT.NONE)
    container.setLayout(new GridLayout(1, false))
    val label = new Label(container, SWT.NONE)
    label.setAlignment(SWT.CENTER);
    label.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1))
    label.setText(Messages.localModel_text + ":")
    label.setToolTipText(Messages.localModel_tooltip_text)
    label.setFont(Resources.fontSmall)
    label
  }
}

object ContributionSelectFilter {
  /** All SelectFilter instances. */
  private val instance = new mutable.WeakHashMap[ContributionSelectFilter, Unit] with mutable.SynchronizedMap[ContributionSelectFilter, Unit]
  /** Singleton identificator. */
  val id = getClass.getName().dropRight(1)
}
