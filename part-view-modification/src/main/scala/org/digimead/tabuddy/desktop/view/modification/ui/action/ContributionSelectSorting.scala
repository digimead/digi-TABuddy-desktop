/**
 * This file is part of the TA Buddy project.
 * Copyright (c) 2013-2015 Alexey Aksenov ezh@ezh.msk.ru
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

import java.util.UUID
import javax.inject.{ Inject, Named }
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.definition.Context
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.ui.definition.widget.{ AppWindow, VComposite }
import org.digimead.tabuddy.desktop.logic.Logic
import org.digimead.tabuddy.desktop.logic.payload.Payload
import org.digimead.tabuddy.desktop.logic.payload.marker.GraphMarker
import org.digimead.tabuddy.desktop.logic.payload.view.Sorting
import org.digimead.tabuddy.desktop.view.modification.{ Default, Messages }
import org.eclipse.e4.core.contexts.Active
import org.eclipse.e4.core.di.annotations.Optional
import org.eclipse.jface.action.{ ControlContribution, ICoolBarManager }
import org.eclipse.jface.viewers.{ LabelProvider, StructuredSelection }
import org.eclipse.swt.SWT
import org.eclipse.swt.widgets.{ Composite, Control, ToolBar }
import scala.concurrent.Future

class ContributionSelectSorting @Inject() (val windowContext: Context)
    extends ControlContribution(ContributionSelectSorting.id) with ContributionSelectBase[Sorting] with XLoggable {
  /** Context item key. */
  protected val contextItemKey = Logic.Id.selectedSorting
  /** Item name. */
  protected val itemName = "sorting"

  if (windowContext.getLocal(classOf[AppWindow]) == null)
    throw new IllegalArgumentException(s"${windowContext} does not contain AppWindow.")

  /** Invoked at every modification of Logic.Id.selectedSorting. */
  @Inject @Optional @log
  override protected def onSelectedItemChanged(@Active @Named(Logic.Id.selectedSorting) id: UUID, @Active contentContext: Context) =
    super.onSelectedItemChanged(id, contentContext)
  /** Invoked at every modification of Logic.Id.selectedView. */
  @Inject @Optional @log
  override protected def onSelectedViewChanged(@Active @Named(Logic.Id.selectedView) id: UUID, @Active contentContext: Context) =
    super.onSelectedViewChanged(id, contentContext)
  /** Invoked on view activation or marker modification. */
  @Inject @Optional @log
  override protected def onViewChanged(@Active vComposite: VComposite, @Optional @Active marker: GraphMarker) =
    super.onViewChanged(vComposite, marker)

  /** Create contribution control. */
  override protected def createControl(parent: Composite): Control = {
    log.debug("Create ContributionSelectSorting contribution.")
    val result = super.createControl(parent)
    comboViewer.get.foreach { comboViewer ⇒
      comboViewer.getCombo.setToolTipText(Messages.sortings_text)
      comboViewer.setLabelProvider(new LabelProvider() {
        override def getText(element: Object): String = element match {
          case sorting: Sorting ⇒
            sorting.name
          case text: String ⇒
            text
          case unknown ⇒
            log.fatal("Unknown item " + unknown.getClass())
            unknown.toString
        }
      })
      comboViewer.setInput(Array(Messages.default_text))
      comboViewer.setSelection(new StructuredSelection(Messages.default_text), true)
      comboViewer.getCombo.setEnabled(false)
    }
    result
  }
  /** Get default item. */
  protected def getDefaultItem(payload: Payload): Sorting =
    Default.defaultSortingId.flatMap(payload.viewSortings.get) getOrElse Sorting.simpleSorting
  /** Get item. */
  protected def getItem(payload: Payload, id: UUID): Option[Sorting] =
    payload.getAvailableViewSortings().find(_.id == id)
  /** Reload view definitions of the combo box. */
  @log
  protected def reloadItems(ss: ContributionSelectBase.SelectionState[Sorting]) {
    log.debug("Reload ContributionSelectSorting items.")
    App.assertEventThread()
    for {
      comboViewer ← comboViewer.get
      coolBarContribution ← getCoolBarContribution
      combo = comboViewer.getCombo() if !combo.isDisposed()
      alreadySelectedSorting = getSelection()
    } {
      val selectedView = ss.getSelectedView()
      val viewSortings = selectedView.sortings
      val modelSortings = ss.marker.safeRead(_.payload.getAvailableViewSortings().toArray)
      val userSortings = modelSortings.filter { sorting ⇒ (sorting.availability && viewSortings(sorting.id)) || sorting == ss.defaultItem }.sortBy(_.name)
      val actualInput = if (userSortings.isEmpty) Array(ss.defaultItem) else userSortings
      val selectedSorting = ss.getSelectedItem(alreadySelectedSorting, contextItemKey) match {
        case sorting if actualInput.contains(sorting) ⇒ sorting
        case sorting ⇒ actualInput.headOption getOrElse ss.defaultItem
      }
      val previousInput = comboViewer.getInput() match {
        case data: Array[Sorting] ⇒ data
        case _ ⇒ Array[Sorting]()
      }
      if (previousInput.nonEmpty && previousInput.corresponds(actualInput)(Sorting.compareDeep)) {
        log.debug("Skip reload. Elements are the same.")
        // combo viewer input is the same
      } else {
        log.debug("Reload sorting combo.")
        // a little hack
        // 1. collapse combo
        comboViewer.getCombo.removeAll()
        // 2. expand combo with new values
        actualInput.foreach(view ⇒ comboViewer.getCombo().add(view.name))
        // asynchronous execution is important
        App.execAsync {
          if (!comboViewer.getCombo.getItems.corresponds(actualInput)(_ == _.name))
            return
          // 3. bind real values to combo viewer
          comboViewer.setInput(actualInput)
          updateComboBoxValue(selectedSorting, ss)
          for {
            selectedSortingValue ← getSelection
            actualSortingValue ← actualInput.find(_.id == selectedSortingValue.id)
          } if (!Sorting.compareDeep(selectedSortingValue, actualSortingValue))
            // user modified the current view: id is persisted, but object is changed
            updateComboBoxValue(actualSortingValue, ss)
          for (toolItem ← combo.getParent().asInstanceOf[ToolBar].getItems().find(_.getControl() == combo)) {
            val width = combo.computeSize(SWT.DEFAULT, SWT.DEFAULT, true).x
            if (width > 0)
              toolItem.setWidth(width)
            coolBarContribution.update(ICoolBarManager.SIZE)
          }
        }
      }
    }
  }
  /** Update combo box value. */
  @log
  protected def updateComboBoxValue(newValue: Sorting, ss: ContributionSelectBase.SelectionState[Sorting]) = App.exec {
    val selection = getSelection
    if (selection != Some(newValue)) {
      for (comboViewer ← comboViewer.get if comboViewer.getInput() != null)
        newValue match {
          case sorting if Option(comboViewer.getInput().asInstanceOf[Array[Sorting]]).map(_.contains(sorting)).getOrElse(false) ⇒
            if (ss.context.getLocal(contextItemKey) != sorting.id)
              Future {
                log.debug(s"Set context value to ${sorting}.")
                ss.context.set(contextItemKey, sorting.id)
              } onFailure { case e: Throwable ⇒ log.error(e.getMessage(), e) }
            comboViewer.setSelection(new StructuredSelection(sorting), true)
          case _ ⇒
            if (ss.context.getLocal(contextItemKey) != ss.defaultItem.id)
              Future {
                log.debug(s"Set context value to ${ss.defaultItem}.")
                ss.context.set(contextItemKey, ss.defaultItem.id)
              } onFailure { case e: Throwable ⇒ log.error(e.getMessage(), e) }
            comboViewer.setSelection(new StructuredSelection(ss.defaultItem), true)
        }
    }
  }

  override def toString = "view.modification.ui.action.ContributionSelectSorting"
}

object ContributionSelectSorting {
  /** Singleton identificator. */
  val id = getClass.getName().dropRight(1)

  override def toString = "view.modification.ui.action.ContributionSelectSorting[Singleton]"
}
