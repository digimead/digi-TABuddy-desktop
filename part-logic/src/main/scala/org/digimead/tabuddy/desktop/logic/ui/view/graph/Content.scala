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

import javax.inject.Inject
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.definition.{ Context, Operation }
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.support.WritableValue
import org.digimead.tabuddy.desktop.core.ui.Resources
import org.digimead.tabuddy.desktop.core.ui.UI
import org.digimead.tabuddy.desktop.core.ui.block.Configuration
import org.digimead.tabuddy.desktop.core.ui.block.View.Factory
import org.digimead.tabuddy.desktop.core.ui.definition.widget.{ AppWindow, VComposite }
import org.digimead.tabuddy.desktop.core.ui.operation.OperationViewCreate
import org.digimead.tabuddy.desktop.logic.{ Logic, Messages }
import org.digimead.tabuddy.desktop.logic.payload.marker.GraphMarker
import org.eclipse.core.databinding.observable.{ ChangeEvent, IChangeListener }
import org.eclipse.core.internal.databinding.observable.DelayedObservableValue
import org.eclipse.core.runtime.jobs.Job
import org.eclipse.e4.core.contexts.ContextInjectionFactory
import org.eclipse.e4.core.di.annotations.Optional
import org.eclipse.jface.viewers.{ ColumnLabelProvider, DoubleClickEvent, IDoubleClickListener, ISelectionChangedListener, IStructuredContentProvider, IStructuredSelection, SelectionChangedEvent, Viewer, ViewerFilter }
import org.eclipse.swt.events.{ SelectionAdapter, SelectionEvent }
import org.eclipse.swt.widgets.Composite

/**
 * Graph content.
 */
class Content(val context: Context, parent: Composite, style: Int) extends ContentSkel(parent, style) with XLoggable {
  ContextInjectionFactory.inject(Content.this, context)
  lazy val delayedMarkerUpdater = WritableValue(System.currentTimeMillis(): java.lang.Long)

  def initializeJFX() {
  }
  def initializeSWT() {
    /*
     * Update marker records after delay 100ms.
     */
    new DelayedObservableValue(100, delayedMarkerUpdater).addChangeListener(new IChangeListener() {
      override def handleChange(event: ChangeEvent) = updateGraphMarker()
    })
    /*
     * Table with active elements.
     */
    val tableViewerActive = getTableViewerActive()
    tableViewerActive.setContentProvider(Content.ContentProvider)
    val tableViewerColumnActiveNames = getTableViewerColumnActiveNames()
    tableViewerActive.addFilter(Content.ActiveFilter)
    tableViewerColumnActiveNames.setLabelProvider(new Content.NameLabelProvider())
    val tableViewerColumnActiveCount = getTableViewerColumnActiveCount()
    tableViewerColumnActiveCount.setLabelProvider(new Content.NestedLabelProvider(this))
    tableViewerActive.addSelectionChangedListener(new ISelectionChangedListener() {
      def selectionChanged(e: SelectionChangedEvent) = e.getSelection() match {
        case iStructuredSelection: IStructuredSelection ⇒
          iStructuredSelection.getFirstElement() match {
            case factory: Factory ⇒ getBtnCloseGroup().setEnabled(true)
            case _ ⇒ getBtnCloseGroup().setEnabled(false)
          }
        case _ ⇒ getBtnCloseGroup().setEnabled(false)
      }
    })
    /*
     * Table with available elements.
     */
    val tableViewerAvailable = getTableViewerAvailable()
    tableViewerAvailable.setContentProvider(Content.ContentProvider)
    val tableViewerColumnAvailableName = getTableViewerColumnAvailableName()
    tableViewerColumnAvailableName.setLabelProvider(new Content.NameLabelProvider())
    val tableViewerColumnAvailableCount = getTableViewerColumnAvailableCount()
    tableViewerColumnAvailableCount.setLabelProvider(new Content.TotalLabelProvider())
    tableViewerAvailable.addSelectionChangedListener(new ISelectionChangedListener() {
      def selectionChanged(e: SelectionChangedEvent) = e.getSelection() match {
        case iStructuredSelection: IStructuredSelection ⇒
          iStructuredSelection.getFirstElement() match {
            case factory: Factory ⇒ getBtnOpenNew().setEnabled(true)
            case _ ⇒ getBtnOpenNew().setEnabled(false)
          }
        case _ ⇒ getBtnOpenNew().setEnabled(false)
      }
    })
    tableViewerAvailable.addDoubleClickListener(new IDoubleClickListener() {
      override def doubleClick(event: DoubleClickEvent) =
        event.getSelection match {
          case selection: IStructuredSelection if !selection.isEmpty() ⇒
            selection.getFirstElement() match {
              case factory: Factory ⇒ doCreateView(factory)
              case unknown ⇒ log.fatal(s"Unknown selection ${unknown}.")
            }
          case selection ⇒
        }
    })
    /*
     * Configure buttons
     */
    val btnCloseAll = getBtnCloseAll()
    btnCloseAll.addSelectionListener(new SelectionAdapter() {
      override def widgetSelected(event: SelectionEvent) = {
        val marker = Some(context.get(classOf[GraphMarker]))
        val widget = event.widget
        val toCloseRefs = UI.viewMap.filter { case (uuid, vComposite) ⇒ vComposite.getContext().map(_.getActive(classOf[GraphMarker])) == marker }.map(_._2.ref)
        toCloseRefs.foreach(_ ! App.Message.Destroy())
      }
    })
    val btnCloseGroup = getBtnCloseGroup()
    btnCloseGroup.setEnabled(false)
    btnCloseGroup.addSelectionListener(new SelectionAdapter() {
      override def widgetSelected(event: SelectionEvent) = {
        val marker = Some(context.get(classOf[GraphMarker]))
        val widget = event.widget
        tableViewerActive.getSelection() match {
          case iStructuredSelection: IStructuredSelection ⇒
            iStructuredSelection.getFirstElement() match {
              case factory: Factory ⇒
                val toCloseRefs = UI.viewMap.filter {
                  case (uuid, vComposite) ⇒
                    vComposite.factory.factoryClassName == factory.getClass().getName() &&
                      vComposite.getContext().map(_.getActive(classOf[GraphMarker])) == marker
                }.map(_._2.ref)
                toCloseRefs.foreach(_ ! App.Message.Destroy())
              case unknown ⇒ log.fatal(s"Unknown selection ${unknown}.")
            }
          case unknown ⇒ log.fatal(s"Unknown selection ${unknown}.")
        }
      }
    })
    val btnOpenNew = getBtnOpenNew()
    btnOpenNew.setEnabled(false)
    btnOpenNew.addSelectionListener(new SelectionAdapter() {
      override def widgetSelected(event: SelectionEvent) = {
        tableViewerAvailable.getSelection() match {
          case iStructuredSelection: IStructuredSelection ⇒
            iStructuredSelection.getFirstElement() match {
              case factory: Factory ⇒ doCreateView(factory)
              case unknown ⇒ log.fatal(s"Unknown selection ${unknown}.")
            }
          case unknown ⇒ log.fatal(s"Unknown selection ${unknown}.")
        }
      }
    })
  }
  /** Update records that are binded to markers. */
  def updateGraphMarker() = if (getTableViewerActive().getContentProvider() != null && getTableViewerAvailable().getContentProvider() != null) {
    getTableViewerActive().refresh()
    getTableViewerAvailable().refresh()
  }

  /** Create new view. */
  protected def doCreateView(factory: Factory) {
    val marker = Some(context.get(classOf[GraphMarker]))
    val appWindow = context.get(classOf[AppWindow])
    OperationViewCreate(appWindow.id, Configuration.CView(factory.configuration)).foreach { operation ⇒
      operation.getExecuteJob() match {
        case Some(job) ⇒
          job.setPriority(Job.LONG)
          job.onComplete(_ match {
            case Operation.Result.OK(Some(viewId), message) ⇒ UI.viewMap.get(viewId).map(onViewCreated)
            case _ ⇒
          }).schedule()
        case None ⇒
          log.fatal(s"Unable to create job for ${operation}.")
      }
    }
  }
  /** Assign graph to the new view. */
  protected def onViewCreated(view: VComposite) {
    val marker = context.get(classOf[GraphMarker])
    view.contentRef ! App.Message.Set(marker)
  }

  /** Update the content when a graph changed. */
  @Inject
  protected def graphChanged(@Optional marker: GraphMarker) = App.exec {
    if (!getForm.isDisposed())
      Option(marker) match {
        case Some(marker) ⇒
          log.debug(s"Set content marker to ${marker}.")
          getForm().setText(Messages.graphContentTitle.format(marker.graphModelId.name, marker.uuid, marker.graphOrigin.name))
          context.set(UI.Id.contentTitle, s"${marker.graphModelId.name} by ${marker.graphOrigin.name}")
          if (getTableViewerActive().getContentProvider() != null && getTableViewerAvailable().getContentProvider() != null) {
            val graphViews = Resources.factories.keys.filter(_.features.contains(Logic.Feature.graph)).toArray.sortBy(_.name.name)
            getTableViewerActive().setInput(graphViews)
            getTableViewerAvailable().setInput(graphViews)
          }
        case None ⇒
          log.debug("Reset content marker.")
          getForm().setText(Messages.graphContentTitleEmpty)
          context.remove(UI.Id.contentTitle)
          if (getTableViewerActive().getContentProvider() != null && getTableViewerAvailable().getContentProvider() != null) {
            getTableViewerActive().setInput(null)
            getTableViewerAvailable().setInput(null)
          }
      }
    App.execAsync { if (!getForm.isDisposed()) getForm.getBody().layout(true, true) }
  }
}

object Content extends XLoggable {
  /** Pass only active elements. */
  object ActiveFilter extends ViewerFilter {
    /** Filters the given elements for the given viewer. The input array is not modified. */
    def select(viewer: Viewer, parentElement: AnyRef, element: AnyRef): Boolean = element match {
      case factory: Factory ⇒
        UI.viewMap.exists { case (uuid, vComposite) ⇒ vComposite.factory.factoryClassName == factory.getClass().getName() }
      case unknown ⇒
        log.fatal(s"Unknown element: ${unknown}.")
        false
    }
  }
  /** Content provider for active and available tables. */
  object ContentProvider extends IStructuredContentProvider {
    /** Disposes of this content provider.     */
    def dispose() {}
    /** Returns the elements to display in the viewer when its input is set to the given element. */
    def getElements(inputElement: AnyRef): Array[AnyRef] = inputElement match {
      case inputElement: Array[AnyRef] ⇒ inputElement
      case _ ⇒ Array()
    }
    /** Notifies this content provider that the given viewer's input has been switched to a different element. */
    def inputChanged(viewer: Viewer, oldInput: AnyRef, newInput: AnyRef) {}
  }
  /** Name label provider. */
  class NameLabelProvider extends ColumnLabelProvider {
    /** Returns the text for the label of the given element. */
    override def getText(element: AnyRef): String = element match {
      case factory: Factory ⇒
        factory.name.name
      case unknown ⇒
        log.fatal(s"Unknown element: ${unknown}.")
        super.getText(element)
    }
  }
  /** Nested label provider. */
  class NestedLabelProvider(content: Content) extends ColumnLabelProvider {
    /** Returns the text for the label of the given element. */
    override def getText(element: AnyRef): String = element match {
      case factory: Factory ⇒
        val marker = Some(content.context.get(classOf[GraphMarker]))
        UI.viewMap.filter {
          case (uuid, vComposite) ⇒
            vComposite.factory.factoryClassName == factory.getClass().getName() &&
              vComposite.getContext().map(_.getActive(classOf[GraphMarker])) == marker
        }.size.toString()
      case unknown ⇒
        log.fatal(s"Unknown element: ${unknown}.")
        super.getText(element)
    }
  }
  /** Total label provider. */
  class TotalLabelProvider extends ColumnLabelProvider {
    /** Returns the text for the label of the given element. */
    override def getText(element: AnyRef): String = element match {
      case factory: Factory ⇒
        UI.viewMap.filter { case (uuid, vComposite) ⇒ vComposite.factory.factoryClassName == factory.getClass().getName() }.size.toString()
      case unknown ⇒
        log.fatal(s"Unknown element: ${unknown}.")
        super.getText(element)
    }
  }
}
