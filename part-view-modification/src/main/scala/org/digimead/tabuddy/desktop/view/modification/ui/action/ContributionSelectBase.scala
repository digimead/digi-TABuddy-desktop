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
import java.util.concurrent.atomic.AtomicReference
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.definition.Context
import org.digimead.tabuddy.desktop.core.support.{ App, WritableValue }
import org.digimead.tabuddy.desktop.core.ui.definition.widget.{ AppWindow, VComposite }
import org.digimead.tabuddy.desktop.logic.Logic
import org.digimead.tabuddy.desktop.logic.payload.marker.GraphMarker
import org.digimead.tabuddy.desktop.logic.payload.view.{ Filter, Sorting, View }
import org.digimead.tabuddy.desktop.view.modification.Default
import org.eclipse.core.databinding.observable.value.{ IObservableValue, IValueChangeListener, ValueChangeEvent }
import org.eclipse.core.databinding.observable.{ ChangeEvent, IChangeListener, Observables }
import org.eclipse.jface.action.{ ControlContribution, IContributionItem, ToolBarContributionItem }
import org.eclipse.jface.databinding.viewers.{ IViewerObservableValue, ViewersObservables }
import org.eclipse.jface.viewers.{ ArrayContentProvider, ComboViewer, IStructuredSelection }
import org.eclipse.swt.SWT
import org.eclipse.swt.events.{ DisposeEvent, DisposeListener }
import org.eclipse.swt.widgets.{ Composite, Control }
import scala.concurrent.Future
import scala.language.reflectiveCalls
import scala.ref.WeakReference

/**
 * ContributionSelectNN base trait.
 */
trait ContributionSelectBase[T <: { val id: UUID }] {
  this: ControlContribution with XLoggable ⇒
  /** Akka execution context. */
  implicit lazy val ec = App.system.dispatcher
  /** Window context of the action item. */
  val windowContext: Context
  /** The combo box with list of values. */
  @volatile protected var comboViewer = WeakReference[ComboViewer](null)
  /** Combo viewer observable. */
  @volatile protected var comboViewerObservable: Option[IViewerObservableValue] = None
  /** Context value key. */
  val contextValueKey: String
  /** ToolBarContributionItem inside CoolBar. */
  @volatile protected var coolBarContributionItem = WeakReference[ToolBarContributionItem](null)
  /** IContributionItem inside ToolBar. */
  @volatile protected var toolBarContributionItem = WeakReference[IContributionItem](null)
  /** Observable events aggregator. */
  protected val eventsAggregator = WritableValue(Long.box(0L))
  /** Last selection state if any. */
  protected val selectionState = new AtomicReference(Option.empty[ContributionSelectBase.SelectionState])

  App.execNGet { initialize() }

  /** Create contribution control. */
  override protected def createControl(parent: Composite): Control = {
    val comboViewer = new ComboViewer(parent, SWT.BORDER | SWT.H_SCROLL | SWT.READ_ONLY)
    comboViewer.setContentProvider(ArrayContentProvider.getInstance())
    val comboViewerObservable = ViewersObservables.observeDelayedValue(50, ViewersObservables.observeSingleSelection(comboViewer))
    comboViewerObservable.addChangeListener(new IChangeListener {
      override def handleChange(event: ChangeEvent) =
        Option(windowContext.getActive(classOf[VComposite])).foreach { vComposite ⇒
          if (vComposite.factory().features.contains(Logic.Feature.viewDefinition))
            Option(event.getObservable.asInstanceOf[IObservableValue].getValue().asInstanceOf[T]).foreach(newValue ⇒
              vComposite.getContentContext().foreach(context ⇒ context.set(contextValueKey, newValue.id)))
        }
    })
    comboViewer.getControl().addDisposeListener(ComboViewerDisposeListener)
    this.comboViewerObservable = Some(comboViewerObservable)
    this.comboViewer = WeakReference(comboViewer)
    selectionState.get().foreach(ss ⇒ Future { reloadItems(ss) } onFailure { case e: Throwable ⇒ log.error(e.getMessage(), e) })
    comboViewer.getControl()
  }
  /** Get IContributionItem for this ControlContribution. */
  protected def getComboContribution(): Option[IContributionItem] = toolBarContributionItem.get orElse
    getCoolBarContribution.flatMap { cc ⇒
      val result = Option(cc.getToolBarManager().find(getId()))
      result.map(item ⇒ toolBarContributionItem = WeakReference(item))
      result
    }
  /** Returns CoolBar contribution item. */
  protected def getCoolBarContribution(): Option[ToolBarContributionItem] = coolBarContributionItem.get orElse comboViewer.get.flatMap { combo ⇒
    Option(windowContext.getLocal(classOf[AppWindow])).map(_.getCoolBarManager2()).flatMap { coolbarManager ⇒
      coolbarManager.getItems().find {
        case item: ToolBarContributionItem ⇒
          item.getToolBarManager() == this.getParent()
        case item ⇒
          false
      } map { item ⇒
        coolBarContributionItem = WeakReference(item.asInstanceOf[ToolBarContributionItem])
        item.asInstanceOf[ToolBarContributionItem]
      }
    }
  }
  /** Get selection from combo box. */
  def getSelection(): Option[T] = comboViewer.get.flatMap(viewer ⇒ if (viewer.getCombo().isDisposed()) null else viewer.getSelection() match {
    case selection: IStructuredSelection if !selection.isEmpty() ⇒ Option(selection.getFirstElement().asInstanceOf[T])
    case selection ⇒ None
  })

  /** Initialize this class */
  protected def initialize() {
    App.assertEventThread()
    Observables.observeDelayedValue(Default.aggregatorDelay, eventsAggregator).addValueChangeListener(new IValueChangeListener {
      def handleValueChange(event: ValueChangeEvent) = {
        log.debug("Reload " + ContributionSelectBase.this)
        Future { selectionState.get().foreach(reloadItems) } onFailure { case e: Throwable ⇒ log.error(e.getMessage(), e) }
      }
    })
    val windowContent = windowContext.get(classOf[AppWindow]).getContent() getOrElse
      { throw new IllegalStateException(s"Content of ${windowContext.get(classOf[AppWindow])} not found") }
    windowContent.addDisposeListener(ContributionDisposeListener())
  }
  /** Reload view definitions combo box */
  protected def reloadItems(ss: ContributionSelectBase.SelectionState)
  /** Update combo box value. */
  protected def updateComboBoxValue(newValue: T, ss: ContributionSelectBase.SelectionState)
  /** Combo control dispose listener. */
  // Combo control maybe recreated if needed.
  object ComboViewerDisposeListener extends DisposeListener {
    def widgetDisposed(e: DisposeEvent) {
      comboViewer = WeakReference(null)
      comboViewerObservable.foreach(_.dispose())
      comboViewerObservable = None
    }
  }
  /** Contribution dispose listener. */
  case class ContributionDisposeListener(toDispose: { def dispose() }*) extends DisposeListener {
    def widgetDisposed(e: DisposeEvent) {
      selectionState.getAndSet(None).foreach(_.dispose())
      eventsAggregator.dispose()
      toDispose.foreach(_.dispose())
    }
  }
}

object ContributionSelectBase extends XLoggable {
  /**
   * Selection state.
   */
  class SelectionState(val marker: GraphMarker, val context: Context, val base: WeakReference[ContributionSelectBase[_]]) {
    /** Observable dispose callbacks. */
    protected val disposeCallbacks = marker.safeRead { state ⇒ Seq(state.payload.viewDefinitions, state.payload.viewSortings, state.payload.viewFilters) }.map { observable ⇒
      App.execNGet {
        val listener = observable.addChangeListener { _ ⇒ base.get.foreach { _.eventsAggregator.value = System.currentTimeMillis() } }
        () ⇒ observable.removeChangeListener(listener)
      }
    }
    /** Default values. */
    val (defaultView, defaultSorting, defaultFilter) = marker.safeRead { state ⇒
      /** Default view definition id. */
      val defaultView = Default.defaultViewId.flatMap(state.payload.viewDefinitions.get) getOrElse View.displayName
      /** Default view sorting id. */
      val defaultSorting = Default.defaultViewId.flatMap(state.payload.viewSortings.get) getOrElse Sorting.simpleSorting
      /** Default view filter id. */
      val defaultFilter = Default.defaultViewId.flatMap(state.payload.viewFilters.get) getOrElse Filter.allowAllFilter
      (defaultView, defaultSorting, defaultFilter)
    }

    /** Dispose state. */
    def dispose(): Unit = App.exec {
      disposeCallbacks.foreach(_())
    }
    /** Get a selected view from the state context. */
    def getSelectedView(alreadySelectedView: Option[View]): View =
      Option(context.getLocal(Logic.Id.selectedView)) match {
        case id @ Some(_) if id == alreadySelectedView.map(_.id) ⇒
          alreadySelectedView getOrElse defaultView
        case Some(id) ⇒
          marker.safeRead(_.payload.getAvailableViewDefinitions().find(_.id == id)) getOrElse defaultView
        case None ⇒
          defaultView
      }
    /** Get a selected sorting from the state context. */
    def getSelectedSorting(alreadySelectedSorting: Option[Sorting]): Sorting =
      Option(context.getLocal(Logic.Id.selectedSorting)) match {
        case id @ Some(_) if id == alreadySelectedSorting.map(_.id) ⇒
          alreadySelectedSorting getOrElse defaultSorting
        case Some(id) ⇒
          marker.safeRead(_.payload.getAvailableViewSortings().find(_.id == id)) getOrElse defaultSorting
        case None ⇒
          defaultSorting
      }
    /** Get a selected filter from the state context. */
    def getSelectedFilter(alreadySelectedFilter: Option[Filter]): Filter =
      Option(context.getLocal(Logic.Id.selectedFilter)) match {
        case id @ Some(_) if id == alreadySelectedFilter.map(_.id) ⇒
          alreadySelectedFilter getOrElse defaultFilter
        case Some(id) ⇒
          marker.safeRead(_.payload.getAvailableViewFilters().find(_.id == id)) getOrElse defaultFilter
        case None ⇒
          defaultFilter
      }
  }
  object SelectionState {
    /**
     * Create new selection state or return exists.
     */
    def apply(stateContainer: AtomicReference[Option[SelectionState]], marker: GraphMarker, context: Context, base: ContributionSelectBase[_ <: { val id: UUID }]): SelectionState =
      stateContainer.synchronized {
        stateContainer.get() match {
          case Some(state) if state.marker == marker && state.context == context ⇒
            state
          case state ⇒
            state.foreach(_.dispose())
            val newState = new SelectionState(marker, context, WeakReference(base))
            stateContainer.set(Some(newState))
            newState
        }
      }
  }
}
