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

package org.digimead.tabuddy.desktop.viewmod.action

import java.util.UUID

import scala.Array.canBuildFrom
import scala.Option.option2Iterable
import scala.collection.mutable

import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.Core
import org.digimead.tabuddy.desktop.Messages
import org.digimead.tabuddy.desktop.gui.GUI
import org.digimead.tabuddy.desktop.gui.widget.VComposite
import org.digimead.tabuddy.desktop.logic.Data
import org.digimead.tabuddy.desktop.logic.payload
import org.digimead.tabuddy.desktop.logic.payload.view.api.Filter
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.App.app2implementation
import org.digimead.tabuddy.desktop.viewmod.Default
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

class ContributionSelectFilter extends ControlContribution(ContributionSelectFilter.id) with ContributionSelectBase[Filter] with Loggable {
  /** Context value key. */
  val contextValueKey = Data.Id.selectedFilter

  initialize()

  /** Invoked on view activation. */
  @Inject @Optional
  def onViewChanged(@Named(GUI.viewContextKey) vcomposite: VComposite) = Option(vcomposite) foreach (vcomposite => App.exec {
    Option(vcomposite.getContext.getLocal(Data.Id.selectedView)) match {
      case Some(viewId: UUID) =>
        // Take previous value.
        updateComboBoxValue(viewId)
      case None =>
        // There is uninitialized context.
        log.debug(s"Initialize ${vcomposite} context.")
        updateContextValue(Some(Default.ViewMod.filter))
        updateComboBoxValue(None)
      case _ =>
    }
  })
  /** Invoked at every modification of Data.Id.selectedFilter. */
  @Inject @Optional // @log
  def onSelectedFilterChanged(@Active @Named(Data.Id.selectedFilter) id: UUID): Unit =
    App.exec { updateComboBoxValue(id) }
  /** Invoked at every modification of Data.Id.selectedView. */
  @Inject @Optional // @log
  def onSelectedViewChanged(@Active @Named(Data.Id.selectedView) id: UUID): Unit =
    App.exec { reloadItems(Option(id).flatMap(Data.viewDefinitions.get) getOrElse { Default.ViewMod.view }) }

  /** Create contribution control. */
  override protected def createControl(parent: Composite): Control = {
    log.debug("Create ContributionSelectView contribution.")
    val result = super.createControl(parent)
    comboViewer.get.foreach { comboViewer =>
      comboViewer.getCombo.setToolTipText(Messages.filters_text)
      comboViewer.setLabelProvider(new LabelProvider() {
        override def getText(element: Object): String = element match {
          case filter: Filter =>
            filter.name
          case unknown =>
            log.fatal("Unknown item " + unknown.getClass())
            unknown.toString
        }
      })
    }
    result
  }
  /** Reload filters combo box. */
  protected def reloadItems() = {
    App.findBranchContextByName(Core.context.getActiveLeaf, VComposite.contextName).foreach(context =>
      Option(context.getLocal(contextValueKey).asInstanceOf[UUID]))
    reloadItems(Default.ViewMod.view)
  }
  /** Reload filters combo box. */
  protected def reloadItems(view: payload.view.api.View): Unit = for {
    comboViewer <- comboViewer.get
    combo = comboViewer.getCombo()
    coolBarContribution <- getCoolBarContribution
  } {
    log.debug("Reload filter combo.")
    App.assertUIThread()
    val available = Data.getAvailableViewFilters
    val actialInput = if (view.filters.isEmpty)
      available.toArray
    else
      Default.ViewMod.filter +: view.filters.flatMap(id =>
        available.find(_.id == id && id != Default.ViewMod.filter.id)).toArray
    val previousInput = comboViewer.getInput().asInstanceOf[Array[payload.view.api.Filter]]
    if (previousInput != null && previousInput.nonEmpty && previousInput.corresponds(actialInput)(payload.view.Filter.compareDeep)) {
      log.debug("Skip reload. Elements are the same.")
      return // combo viewer input is the same
    }
    // a little hack
    // 1. collapse combo
    combo.removeAll()
    // 2. expand combo with new values
    actialInput.foreach(view => combo.add(view.name))
    // asynchronous execution is important
    App.execAsync {
      if (!combo.isDisposed()) {
        // 3. bind real values to combo viewer
        comboViewer.setInput(actialInput)
        Option(Core.context.getActiveLeaf.get(contextValueKey).asInstanceOf[UUID]) match {
          case Some(id) => updateComboBoxValue(id)
          case None => updateComboBoxValue(None)
        }
        for {
          selectedFilterValue <- getSelection
          actualFilterValue <- Data.viewFilters.get(selectedFilterValue.id)
        } if (!payload.view.Filter.compareDeep(selectedFilterValue, actualFilterValue))
          // user modified the current view: id is persisted, but object is changed
          updateComboBoxValue(Some(actualFilterValue))
        for (toolItem <- combo.getParent().asInstanceOf[ToolBar].getItems().find(_.getControl() == combo)) {
          val width = combo.computeSize(SWT.DEFAULT, SWT.DEFAULT, true).x
          if (width > 0)
            toolItem.setWidth(width)
          coolBarContribution.update(ICoolBarManager.SIZE)
        }
      }
    }
  }
  /** Update combo box value by ID. */
  protected def updateComboBoxValue(newValueId: UUID) =
    updateComboBoxValue(Data.getAvailableViewFilters.find(_.id == newValueId))
  /** Update combo box value. */
  protected def updateComboBoxValue(value: Option[Filter]) {
    val selection = getSelection
    if (selection == value && value.nonEmpty)
      return
    if (selection == Some(Default.ViewMod.filter) && value.isEmpty)
      return
    for (comboViewer <- comboViewer.get)
      value match {
        case Some(filter) if Option(comboViewer.getInput().asInstanceOf[Array[payload.view.api.Filter]]).map(_.contains(filter)).getOrElse(false) =>
          log.debug(s"Set UI value to ${filter.id}.")
          comboViewer.setSelection(new StructuredSelection(filter), true)
        case _ =>
          log.debug(s"Set UI value to ${Default.ViewMod.filter.id}.")
          comboViewer.setSelection(new StructuredSelection(Default.ViewMod.filter), true)
      }
  }
}

object ContributionSelectFilter {
  /** All SelectFilter instances. */
  private val instance = new mutable.WeakHashMap[ContributionSelectFilter, Unit] with mutable.SynchronizedMap[ContributionSelectFilter, Unit]
  /** Singleton identificator. */
  val id = getClass.getName().dropRight(1)
}
