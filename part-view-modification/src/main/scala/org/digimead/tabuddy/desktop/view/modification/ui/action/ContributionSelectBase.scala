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
import java.util.concurrent.atomic.AtomicReference
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.Messages
import org.digimead.tabuddy.desktop.core.definition.Context
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.support.WritableValue
import org.digimead.tabuddy.desktop.core.ui.definition.widget.{ AppWindow, VComposite }
import org.digimead.tabuddy.desktop.logic.Logic
import org.digimead.tabuddy.desktop.logic.payload.Payload
import org.digimead.tabuddy.desktop.logic.payload.marker.GraphMarker
import org.digimead.tabuddy.desktop.logic.payload.view.View
import org.digimead.tabuddy.desktop.view.modification.Default
import org.eclipse.core.databinding.observable.{ ChangeEvent, IChangeListener, Observables }
import org.eclipse.core.databinding.observable.value.{ IObservableValue, IValueChangeListener, ValueChangeEvent }
import org.eclipse.jface.action.{ ControlContribution, IContributionItem, ToolBarContributionItem }
import org.eclipse.jface.databinding.viewers.{ IViewerObservableValue, ViewersObservables }
import org.eclipse.jface.viewers.{ ArrayContentProvider, ComboViewer, IStructuredSelection, StructuredSelection }
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
  /** Context item key. */
  protected val contextItemKey: String
  /** ToolBarContributionItem inside CoolBar. */
  @volatile protected var coolBarContributionItem = WeakReference[ToolBarContributionItem](null)
  /** Observable events aggregator. */
  protected val eventsAggregator = WritableValue(Long.box(0L))
  /** Item name. */
  protected val itemName: String
  /** Last (composite id, marker id) of an active view. */
  protected var previousState = (Option.empty[UUID], Option.empty[UUID])
  /** Last selection state if any. */
  protected val selectionState = new AtomicReference(Option.empty[ContributionSelectBase.SelectionState[T]])
  /** IContributionItem inside ToolBar. */
  @volatile protected var toolBarContributionItem = WeakReference[IContributionItem](null)

  App.execNGet { initialize() }

  /** Get combo viewer. */
  def getComboViewer() = comboViewer.get
  /** Get selection from combo box. */
  def getSelection(): Option[T] = comboViewer.get.flatMap(viewer ⇒ if (viewer.getCombo().isDisposed()) null else viewer.getSelection() match {
    case selection: IStructuredSelection if !selection.isEmpty() ⇒ Option(selection.getFirstElement().asInstanceOf[T])
    case selection ⇒ None
  })

  /** Activate control. */
  protected def activateControl(state: (Option[UUID], Option[UUID]), contentContext: Context, marker: GraphMarker) {
    App.assertEventThread()
    val alreadySelectedItem = getSelection()
    val currentState = {
      for { comboViewer ← comboViewer.get }
        if (state == previousState)
          if (!comboViewer.getControl.isEnabled()) {
            log.debug(s"Enable ${itemName} control.")
            comboViewer.getControl.setEnabled(true)
          }
      previousState
    }
    if (currentState != state)
      return
    val reloadRequired = comboViewer.get.nonEmpty && (selectionState.get match {
      case Some(state) if state.marker != marker ⇒ true
      case Some(state) ⇒ false
      case None ⇒ true
    })
    val ss = ContributionSelectBase.SelectionState[T](selectionState, marker, contentContext,
      marker.safeRead(state ⇒ getDefaultItem(state.payload)), getItem, this)
    val selectedItem = ss.getSelectedItem(alreadySelectedItem, contextItemKey)
    if (reloadRequired)
      reloadItems(ss)
    else
      updateComboBoxValue(selectedItem, ss)
  }
  /** Create contribution control. */
  override protected def createControl(parent: Composite): Control = {
    val comboViewer = new ComboViewer(parent, SWT.BORDER | SWT.H_SCROLL | SWT.READ_ONLY)
    comboViewer.setContentProvider(ArrayContentProvider.getInstance())
    val comboViewerObservable = ViewersObservables.observeDelayedValue(ContributionSelectBase.comboBoxDelay,
      ViewersObservables.observeSingleSelection(comboViewer))
    comboViewerObservable.addChangeListener(new IChangeListener {
      override def handleChange(event: ChangeEvent) =
        Option(windowContext.getActive(classOf[VComposite])).foreach { vComposite ⇒
          event.getObservable.asInstanceOf[IObservableValue].getValue() match {
            case text: String ⇒ // skip
            case value ⇒
              if (vComposite.factory().features.contains(Logic.Feature.viewDefinition))
                Option(value.asInstanceOf[T]).foreach(newValue ⇒
                  vComposite.getContentContext().foreach { context ⇒
                    Future {
                      log.debug(s"New combo box value. Set context value to ${newValue}.")
                      context.set(contextItemKey, newValue.id)
                    } onFailure { case e: Throwable ⇒ log.error(e.getMessage(), e) }
                  })
          }
        }
    })
    comboViewer.getControl().addDisposeListener(ComboViewerDisposeListener)
    this.comboViewerObservable = Some(comboViewerObservable)
    this.comboViewer = WeakReference(comboViewer)
    selectionState.get().foreach(reloadItems)
    comboViewer.getControl()
  }
  /** Deactivate control. */
  protected def deactivateControl(state: (Option[UUID], Option[UUID])) {
    App.exec {
      for { comboViewer ← comboViewer.get }
        if (state == previousState)
          if (comboViewer.getControl.isEnabled()) {
            log.debug(s"Disable ${itemName} control.")
            comboViewer.getControl.setEnabled(false)
          }
    }
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
  /** Get default item. */
  protected def getDefaultItem(payload: Payload): T
  /** Get item. */
  protected def getItem(payload: Payload, id: UUID): Option[T]
  /** Initialize this class */
  protected def initialize() {
    App.assertEventThread()
    Observables.observeDelayedValue(Default.aggregatorDelay, eventsAggregator).addValueChangeListener(new IValueChangeListener {
      def handleValueChange(event: ValueChangeEvent) = {
        log.debug("Reload " + ContributionSelectBase.this)
        selectionState.get().foreach(reloadItems)
      }
    })
    val windowContent = windowContext.get(classOf[AppWindow]).getContent() getOrElse
      { throw new IllegalStateException(s"Content of ${windowContext.get(classOf[AppWindow])} not found") }
    windowContent.addDisposeListener(ContributionDisposeListener())
  }
  /** Invoked at every modification of Logic.Id.selectedView. */
  protected def onSelectedViewChanged(viewId: UUID, contentContext: Context): Unit = ContributionSelectBase.synchronized {
    if (contentContext.getLocal(Logic.Id.selectedView) != null)
      Option(contentContext.getLocal(classOf[GraphMarker])).foreach { marker ⇒
        val ss = ContributionSelectBase.SelectionState(selectionState, marker, contentContext,
          marker.safeRead(state ⇒ getDefaultItem(state.payload)), getItem, this)
        App.exec { reloadItems(ss) }
      }
  }
  /** Invoked at every modification of Logic.Id.selectedNNN . */
  protected def onSelectedItemChanged(itemId: UUID, contentContext: Context): Unit = ContributionSelectBase.synchronized {
    val id = contentContext.getLocal(contextItemKey)
    if (id != null) {
      Future {
        val alreadySelectedItem = App.execNGet { getSelection() }
        if (alreadySelectedItem.map(_.id) != Option(id))
          Option(contentContext.getLocal(classOf[GraphMarker])).foreach { marker ⇒
            val ss = ContributionSelectBase.SelectionState(selectionState, marker, contentContext,
              marker.safeRead(state ⇒ getDefaultItem(state.payload)), getItem, this)
            val selectedItem = ss.getSelectedItem(alreadySelectedItem, contextItemKey)
            if (Some(selectedItem) == alreadySelectedItem) {
              if (id != selectedItem.id) {
                Future {
                  log.debug(s"Set context value to ${selectedItem}.")
                  contentContext.set(contextItemKey, selectedItem.id)
                } onFailure { case e: Throwable ⇒ log.error(e.getMessage(), e) }
              } else {
                log.debug(s"Skip the same ${itemName} value.")
              }
            } else {
              updateComboBoxValue(selectedItem, ss)
            }
          }
      } onFailure { case e: Throwable ⇒ log.error(e.getMessage(), e) }
    }
  }
  /** Invoked on view activation or marker modification. */
  protected def onViewChanged(vComposite: VComposite, marker: GraphMarker): Unit = ContributionSelectBase.synchronized {
    val newState = (Option(vComposite).map(_.id), Option(marker).map(_.uuid))
    if (newState == previousState || (newState == (None, None)))
      return
    previousState = newState
    if (vComposite.factory().features.contains(Logic.Feature.viewDefinition)) {
      val success = for {
        contentContext ← Option(vComposite).flatMap(_.getContentContext())
      } yield Option(contentContext.getLocal(classOf[GraphMarker])) match {
        case Some(marker) ⇒
          App.exec { activateControl(newState, contentContext, marker) }
        case None ⇒
          App.exec {
            for (comboViewer ← comboViewer.get) {
              log.debug(s"Disable ${itemName} control.")
              comboViewer.setInput(Array(Messages.default_text))
              comboViewer.setSelection(new StructuredSelection(Messages.default_text), true)
              comboViewer.getCombo.setEnabled(false)
              contentContext.remove(contextItemKey)
            }
          }
      }
      success getOrElse deactivateControl(newState)
    } else deactivateControl(newState)
  }
  /** Reload view definitions combo box */
  protected def reloadItems(ss: ContributionSelectBase.SelectionState[T])
  /** Update combo box value. */
  protected def updateComboBoxValue(newValue: T, ss: ContributionSelectBase.SelectionState[T])

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
  /** Combo box listener delay(ms). */
  val comboBoxDelay = 50

  /**
   * Selection state.
   */
  class SelectionState[T <: { val id: UUID }](val marker: GraphMarker, val context: Context, val defaultItem: T,
      val getItem: (Payload, UUID) ⇒ Option[T], val base: WeakReference[ContributionSelectBase[_]]) {
    /** Observable dispose callbacks. */
    protected val disposeCallbacks = marker.safeRead { state ⇒ Seq(state.payload.viewDefinitions, state.payload.viewSortings, state.payload.viewFilters) }.map { observable ⇒
      App.execNGet {
        val listener = observable.addChangeListener { _ ⇒ base.get.foreach { _.eventsAggregator.value = System.currentTimeMillis() } }
        () ⇒ observable.removeChangeListener(listener)
      }
    }

    /** Dispose state. */
    def dispose(): Unit = App.exec {
      disposeCallbacks.foreach(_())
    }
    /** Get a selected item from the state context. */
    def getSelectedItem(alreadySelectedItem: Option[T], contextItemKey: String): T =
      Option(context.getLocal(contextItemKey)) match {
        case id @ Some(_) if id == alreadySelectedItem.map(_.id) ⇒
          alreadySelectedItem getOrElse defaultItem
        case Some(id: UUID) ⇒
          marker.safeRead(state ⇒ getItem(state.payload, id)) orElse alreadySelectedItem getOrElse defaultItem
        case None ⇒
          defaultItem
      }
    /** Get a selected view from the state context. */
    def getSelectedView(): View =
      marker.safeRead { state ⇒
        Option(context.getLocal(Logic.Id.selectedView)).flatMap { id ⇒
          state.payload.getAvailableViewDefinitions().find(_.id == id)
        } orElse Default.defaultViewId.flatMap(state.payload.viewDefinitions.get) getOrElse View.displayName
      }
  }
  object SelectionState {
    /**
     * Create new selection state or return exists.
     */
    def apply[T <: { val id: UUID }](stateContainer: AtomicReference[Option[SelectionState[T]]], marker: GraphMarker, context: Context,
      defaultItem: T, getItem: (Payload, UUID) ⇒ Option[T], base: ContributionSelectBase[_ <: { val id: UUID }]): SelectionState[T] =
      stateContainer.synchronized {
        stateContainer.get() match {
          case Some(state) if state.marker == marker && state.context == context ⇒
            state
          case state ⇒
            state.foreach(_.dispose())
            val newState = new SelectionState[T](marker, context, defaultItem, getItem, WeakReference(base))
            stateContainer.set(Some(newState))
            newState
        }
      }
  }
}

