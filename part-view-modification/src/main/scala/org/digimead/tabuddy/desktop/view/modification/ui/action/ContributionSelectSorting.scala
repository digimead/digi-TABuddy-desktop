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

import java.util.UUID
import scala.Array.canBuildFrom
import scala.Option.option2Iterable
import scala.collection.mutable
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.Core
import org.digimead.tabuddy.desktop.logic.Data
import org.digimead.tabuddy.desktop.logic.payload
import org.digimead.tabuddy.desktop.logic.payload.view.api.Sorting
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.support.App.app2implementation
import org.digimead.tabuddy.desktop.view.modification.Default
import org.eclipse.e4.core.contexts.Active
import org.eclipse.e4.core.di.annotations.Optional
import org.eclipse.jface.action.ControlContribution
import org.eclipse.jface.action.ICoolBarManager
import org.eclipse.jface.viewers.LabelProvider
import org.eclipse.jface.viewers.StructuredSelection
import org.eclipse.swt.SWT
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Control
import org.eclipse.swt.widgets.ToolBar
import javax.inject.Inject
import javax.inject.Named
import org.digimead.tabuddy.desktop.view.modification.Messages
import org.digimead.tabuddy.desktop.logic.Logic
import scala.ref.WeakReference
import org.digimead.tabuddy.desktop.core.ui.definition.widget.AppWindow

class ContributionSelectSorting(val window: WeakReference[AppWindow]) extends ControlContribution(ContributionSelectSorting.id) with ContributionSelectBase[Sorting] with Loggable {
  /** Context value key. */
  val contextValueKey = Logic.Id.selectedSorting

  /** Invoked on view activation. */
  //  @Inject @Optional
  //  def onViewChanged(@Named(GUI.viewContextKey) vcomposite: VComposite) = Option(vcomposite) foreach (vcomposite => App.exec {
  //    Option(vcomposite.getContext.getLocal(Data.Id.selectedView)) match {
  //      case Some(viewId: UUID) =>
  //        // Take previous value.
  //        updateComboBoxValue(viewId)
  //      case None =>
  //        // There is uninitialized context.
  //        log.debug(s"Initialize ${vcomposite} context.")
  //        updateContextValue(Some(Default.ViewModification.sorting))
  //        updateComboBoxValue(None)
  //      case _ =>
  //    }
  //  })
  /** Invoked at every modification of Data.Id.selectedSorting. */
  @Inject @Optional // @log
  def onSelectedSortingChanged(@Active @Named(Logic.Id.selectedSorting) id: UUID): Unit =
    App.exec { updateComboBoxValue(id) }
  /** Invoked at every modification of Data.Id.selectedView. */
  //    @Inject @Optional // @log
  //    def onSelectedViewChanged(@Active @Named(Logic.Id.selectedView) id: UUID): Unit =
  //      App.exec { reloadItems(Option(id).flatMap(Logic.viewDefinitions.get) getOrElse { Default.ViewModification.view }) }

  /** Create contribution control. */
  override protected def createControl(parent: Composite): Control = {
    log.debug("Create ContributionSelectView contribution.")
    val result = super.createControl(parent)
    comboViewer.get.foreach { comboViewer ⇒
      comboViewer.getCombo.setToolTipText(Messages.sortings_text)
      comboViewer.setLabelProvider(new LabelProvider() {
        override def getText(element: Object): String = element match {
          case sorting: Sorting ⇒
            sorting.name
          case unknown ⇒
            log.fatal("Unknown item " + unknown.getClass())
            unknown.toString
        }
      })
    }
    result
  }
  /** Reload sortings combo box. */
  protected def reloadItems() = {
    //    App.findBranchContextByName(Core.context.getActiveLeaf, VComposite.contextName).foreach(context =>
    //      Option(context.getLocal(contextValueKey).asInstanceOf[UUID]))
    //    reloadItems(Default.ViewModification.view)
  }
  /** Reload sortings combo box. */
  protected def reloadItems(view: payload.view.api.View): Unit = for {
    comboViewer ← comboViewer.get
    combo = comboViewer.getCombo()
    coolBarContribution ← getCoolBarContribution
  } {
    //    log.debug("Reload sorting combo.")
    //    App.assertUIThread()
    //    val available = Data.getAvailableViewSortings
    //    val actialInput = if (view.sortings.isEmpty)
    //      available.toArray
    //    else
    //      Default.ViewModification.sorting +: view.sortings.flatMap(id =>
    //        available.find(_.id == id && id != Default.ViewModification.sorting.id)).toArray
    //    val previousInput = comboViewer.getInput().asInstanceOf[Array[payload.view.api.Sorting]]
    //    if (previousInput != null && previousInput.nonEmpty && previousInput.corresponds(actialInput)(payload.view.Sorting.compareDeep)) {
    //      log.debug("Skip reload. Elements are the same.")
    //      return // combo viewer input is the same
    //    }
    //    // a little hack
    //    // 1. collapse combo
    //    combo.removeAll()
    //    // 2. expand combo with new values
    //    actialInput.foreach(view => combo.add(view.name))
    //    // asynchronous execution is important
    //    App.execAsync {
    //      if (!combo.isDisposed()) {
    //        // 3. bind real values to combo viewer
    //        comboViewer.setInput(actialInput)
    //        Option(Core.context.getActiveLeaf.get(contextValueKey).asInstanceOf[UUID]) match {
    //          case Some(id) => updateComboBoxValue(id)
    //          case None => updateComboBoxValue(None)
    //        }
    //        for {
    //          selectedSortingValue <- getSelection
    //          actualSortingValue <- Data.viewSortings.get(selectedSortingValue.id)
    //        } if (!payload.view.Sorting.compareDeep(selectedSortingValue, actualSortingValue))
    //          // user modified the current view: id is persisted, but object is changed
    //          updateComboBoxValue(Some(actualSortingValue))
    //        for (toolItem <- combo.getParent().asInstanceOf[ToolBar].getItems().find(_.getControl() == combo)) {
    //          val width = combo.computeSize(SWT.DEFAULT, SWT.DEFAULT, true).x
    //          if (width > 0)
    //            toolItem.setWidth(width)
    //          coolBarContribution.update(ICoolBarManager.SIZE)
    //        }
    //      }
    //    }
  }
  /** Update combo box value by ID. */
  protected def updateComboBoxValue(newValueId: UUID) {}
  //    updateComboBoxValue(Data.getAvailableViewSortings.find(_.id == newValueId))
  /** Update combo box value. */
  protected def updateComboBoxValue(value: Option[Sorting]) {
    //    val selection = getSelection
    //    if (selection == value && value.nonEmpty)
    //      return
    //    if (selection == Some(Default.ViewModification.sorting) && value.isEmpty)
    //      return
    //    for (comboViewer <- comboViewer.get)
    //      value match {
    //        case Some(sorting) if Option(comboViewer.getInput().asInstanceOf[Array[payload.view.api.Sorting]]).map(_.contains(sorting)).getOrElse(false) =>
    //          log.debug(s"Set UI value to ${sorting.id}.")
    //          comboViewer.setSelection(new StructuredSelection(sorting), true)
    //        case _ =>
    //          log.debug(s"Set UI value to ${Default.ViewModification.sorting.id}.")
    //          comboViewer.setSelection(new StructuredSelection(Default.ViewModification.sorting), true)
    //      }
  }
}

object ContributionSelectSorting {
  /** All SelectSorting instances. */
  private val instance = new mutable.WeakHashMap[ContributionSelectSorting, Unit] with mutable.SynchronizedMap[ContributionSelectSorting, Unit]
  /** Singleton identificator. */
  val id = getClass.getName().dropRight(1)
}
