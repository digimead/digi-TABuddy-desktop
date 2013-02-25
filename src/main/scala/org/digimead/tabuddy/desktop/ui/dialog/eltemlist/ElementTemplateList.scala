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

package org.digimead.tabuddy.desktop.ui.dialog.eltemlist

import java.util.UUID
import java.util.concurrent.locks.ReentrantLock

import scala.collection.immutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.future
import scala.ref.WeakReference

import org.digimead.digi.lib.log.Loggable
import org.digimead.tabuddy.desktop.Main
import org.digimead.tabuddy.desktop.job.JobShowElementTemplateEditor
import org.digimead.tabuddy.desktop.payload.ElementTemplate
import org.digimead.tabuddy.desktop.res.Messages
import org.digimead.tabuddy.desktop.support.WritableList
import org.digimead.tabuddy.desktop.support.WritableList.wrapper2underlying
import org.digimead.tabuddy.desktop.support.WritableValue
import org.digimead.tabuddy.desktop.support.WritableValue.wrapper2underlying
import org.digimead.tabuddy.desktop.ui.dialog.Dialog
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.Model.model2implementation
import org.digimead.tabuddy.model.element.Element
import org.digimead.tabuddy.model.element.Stash
import org.eclipse.jface.action.Action
import org.eclipse.jface.action.ActionContributionItem
import org.eclipse.jface.action.IAction
import org.eclipse.jface.action.IMenuListener
import org.eclipse.jface.action.IMenuManager
import org.eclipse.jface.action.MenuManager
import org.eclipse.jface.databinding.viewers.ObservableListContentProvider
import org.eclipse.jface.databinding.viewers.ViewersObservables
import org.eclipse.jface.dialogs.IDialogConstants
import org.eclipse.jface.viewers.ColumnViewerToolTipSupport
import org.eclipse.jface.viewers.ISelectionChangedListener
import org.eclipse.jface.viewers.IStructuredSelection
import org.eclipse.jface.viewers.SelectionChangedEvent
import org.eclipse.jface.viewers.StructuredSelection
import org.eclipse.jface.viewers.TableViewer
import org.eclipse.jface.viewers.Viewer
import org.eclipse.jface.viewers.ViewerComparator
import org.eclipse.swt.SWT
import org.eclipse.swt.events.DisposeEvent
import org.eclipse.swt.events.DisposeListener
import org.eclipse.swt.events.SelectionAdapter
import org.eclipse.swt.events.SelectionEvent
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Control
import org.eclipse.swt.widgets.Event
import org.eclipse.swt.widgets.Listener
import org.eclipse.swt.widgets.Shell
import org.eclipse.swt.widgets.TableItem

class ElementTemplateList(val parentShell: Shell, val initial: Set[ElementTemplate.Interface])
  extends org.digimead.tabuddy.desktop.res.dialog.ElementTemplateList(parentShell) with Dialog with Loggable {
  /** The actual content */
  // replace initial elements with copies that will be modified in the progress
  protected[eltemlist] val actual = WritableList(initial.map(template => template.copy(element = template.element.eCopy)).toList.sortBy(_.id.name))
  /** The auto resize lock */
  protected val autoResizeLock = new ReentrantLock()
  /** The property representing a selected element template */
  protected[eltemlist] val selected = WritableValue[ElementTemplate.Interface]
  assert(ElementTemplateList.dialog.isEmpty, "ElementTemplateList dialog is already active")

  /** Get modified type templates */
  def getTemplateSet(): Set[ElementTemplate.Interface] = actual.toSet

  /** Auto resize tableviewer columns */
  protected def autoresize() = if (autoResizeLock.tryLock()) try {
    Thread.sleep(50)
    Main.execNGet {
      if (!getTableElementTemplates.getTable.isDisposed()) {
        adjustColumnWidth(getTableViewerColumnAvailability, Dialog.columnPadding)
        adjustColumnWidth(getTableViewerColumnId, Dialog.columnPadding)
        getTableElementTemplates.refresh()
      }
    }
  } finally {
    autoResizeLock.unlock()
  }
  /** Create contents of the dialog. */
  override protected def createDialogArea(parent: Composite): Control = {
    val result = super.createDialogArea(parent)
    new ActionContributionItem(ElementTemplateList.ActionCreateFrom).fill(getCompositeTemplates())
    new ActionContributionItem(ElementTemplateList.ActionEdit).fill(getCompositeTemplates())
    new ActionContributionItem(ElementTemplateList.ActionRemove).fill(getCompositeTemplates())
    initTableElementTemplates()
    val actualListener = actual.addChangeListener { event =>
      if (ElementTemplateList.ActionAutoResize.isChecked())
        future { autoresize() }
      updateOK()
    }
    // Add the dispose listener
    getShell().addDisposeListener(new DisposeListener {
      def widgetDisposed(e: DisposeEvent) {
        actual.removeChangeListener(actualListener)
        ElementTemplateList.dialog = None
      }
    })
    // Set the dialog message
    setMessage(Messages.elementTemplateListDescription_text.format(Model.eId.name))
    // Set the dialog window title
    getShell().setText(Messages.elementTemplateListDialog_text.format(Model.eId.name))
    ElementTemplateList.dialog = Some(this)
    result
  }
  /** Generate new ID: old ID + 'Copy' + N */
  protected def getNewTemplateCopyID(id: Symbol): Symbol = {
    val sameIds = immutable.HashSet(actual.filter(_.id.name.startsWith(id.name)).map(_.id.name).toSeq: _*)
    var n = 0
    var newId = id.name + Messages.copy_item_text
    while (sameIds(newId)) {
      n += 1
      newId = id.name + Messages.copy_item_text + n
    }
    Symbol(newId)
  }
  /** Initialize tableTypeList */
  protected def initTableElementTemplates() {
    val viewer = getTableElementTemplates()
    viewer.setContentProvider(new ObservableListContentProvider())
    getTableViewerColumnAvailability.setLabelProvider(new ColumnAvailability.TLabelProvider)
    getTableViewerColumnAvailability.setEditingSupport(new ColumnAvailability.TEditingSupport(viewer, this))
    getTableViewerColumnAvailability.getColumn.addSelectionListener(new ElementTemplateList.TemplateSelectionAdapter(WeakReference(viewer), 0))
    getTableViewerColumnId.setLabelProvider(new ColumnId.TLabelProvider)
    getTableViewerColumnId.setEditingSupport(new ColumnId.TEditingSupport(viewer, this))
    getTableViewerColumnId.getColumn.addSelectionListener(new ElementTemplateList.TemplateSelectionAdapter(WeakReference(viewer), 1))
    getTableViewerColumnDescription.setLabelProvider(new ColumnDescription.TLabelProvider)
    getTableViewerColumnDescription.setEditingSupport(new ColumnDescription.TEditingSupport(viewer, this))
    getTableViewerColumnDescription.getColumn.addSelectionListener(new ElementTemplateList.TemplateSelectionAdapter(WeakReference(viewer), 2))
    // Add a SWT.CHECK support
    viewer.getTable.addListener(SWT.Selection, new Listener() {
      def handleEvent(event: Event) = if (event.detail == SWT.CHECK)
        event.item match {
          case tableItem: TableItem =>
            val index = tableItem.getParent().indexOf(tableItem)
            viewer.getElementAt(index) match {
              case before: ElementTemplate.Interface =>
                if (before.availability != tableItem.getChecked()) {
                  val after = before.updated(tableItem.getChecked())
                  updateActualTemplate(before, after)
                }
              case item =>
                log.fatal(s"unknown item $item")
            }
          case item =>
            log.fatal(s"unknown item $item")
        }
    })
    // Activate the tooltip support for the viewer
    ColumnViewerToolTipSupport.enableFor(viewer)
    // Add the context menu
    val menuMgr = new MenuManager()
    val menu = menuMgr.createContextMenu(viewer.getControl)
    menuMgr.addMenuListener(new IMenuListener() {
      override def menuAboutToShow(manager: IMenuManager) {
        manager.add(ElementTemplateList.ActionAutoResize)
      }
    })
    menuMgr.setRemoveAllWhenShown(true)
    viewer.getControl.setMenu(menu)
    // Add the selection listener
    viewer.addSelectionChangedListener(new ISelectionChangedListener() {
      override def selectionChanged(event: SelectionChangedEvent) = event.getSelection() match {
        case selection: IStructuredSelection if !selection.isEmpty() =>
          val template = selection.getFirstElement().asInstanceOf[ElementTemplate.Interface]
          ElementTemplateList.ActionCreateFrom.setEnabled(true)
          ElementTemplateList.ActionEdit.setEnabled(true)
          ElementTemplateList.ActionRemove.setEnabled(selected.value != null &&
            !ElementTemplate.predefined.exists(_.id == selected.value.id)) // exclude predefined
        case selection =>
          ElementTemplateList.ActionCreateFrom.setEnabled(false)
          ElementTemplateList.ActionEdit.setEnabled(false)
          ElementTemplateList.ActionRemove.setEnabled(false)
      }
    })
    viewer.setComparator(new ElementTemplateList.TemplateComparator)
    viewer.setInput(actual.underlying)
    Main.bindingContext.bindValue(ViewersObservables.observeSingleSelection(viewer), selected)
  }
  /** On dialog active */
  override protected def onActive = {
    updateOK()
    autoresize()
  }
  /** Updates an actual element template */
  protected[eltemlist] def updateActualTemplate(before: ElementTemplate.Interface, after: ElementTemplate.Interface) {
    val index = actual.indexOf(before)
    actual.update(index, after)
    if (index == actual.size - 1)
      getTableElementTemplates.refresh() // Workaround for the JFace bug. Force the last element modification.
    getTableElementTemplates.setSelection(new StructuredSelection(after), true)
  }
  /** Update OK button state */
  protected def updateOK() = Option(getButton(IDialogConstants.OK_ID)).foreach(_.setEnabled(initial != actual))
}

object ElementTemplateList extends Loggable {
  /** There is may be only one dialog instance at time */
  @volatile private var dialog: Option[ElementTemplateList] = None
  /** Default sort direction */
  private val defaultDirection = Dialog.ASCENDING
  /** Actual sortBy column index */
  @volatile private var sortColumn = 1 // by an id
  /** Actual sort direction */
  @volatile private var sortDirection = defaultDirection

  /** Apply a f(x) to the selected template if any */
  def template[T](f: (ElementTemplateList, ElementTemplate.Interface) => T): Option[T] =
    dialog.flatMap(d => Option(d.selected.value).map(f(d, _)))

  //case class Template(val id: String, val description: String, val availability: Boolean)
  class TemplateComparator extends ViewerComparator {
    private var _column = ElementTemplateList.sortColumn
    private var _direction = ElementTemplateList.sortDirection

    /** Active column getter */
    def column = _column
    /** Active column setter */
    def column_=(arg: Int) {
      _column = arg
      ElementTemplateList.sortColumn = _column
      _direction = ElementTemplateList.defaultDirection
      ElementTemplateList.sortDirection = _direction
    }
    /** Sorting direction */
    def direction = _direction
    /**
     * Returns a negative, zero, or positive number depending on whether
     * the first element is less than, equal to, or greater than
     * the second element.
     */
    override def compare(viewer: Viewer, e1: Object, e2: Object): Int = {
      val entity1 = e1.asInstanceOf[ElementTemplate.Interface]
      val entity2 = e2.asInstanceOf[ElementTemplate.Interface]
      val rc = column match {
        case 0 => entity1.availability.compareTo(entity2.availability)
        case 1 => entity1.id.name.compareTo(entity2.id.name)
        case 2 => entity1.description.compareTo(entity2.description)
        case index =>
          log.fatal(s"unknown column with index $index"); 0
      }
      if (_direction == Dialog.DESCENDING) -rc else rc
    }
    /** Switch comparator direction */
    def switchDirection() {
      _direction = 1 - _direction
      ElementTemplateList.sortDirection = _direction
    }
  }
  class TemplateSelectionAdapter(tableViewer: WeakReference[TableViewer], column: Int) extends SelectionAdapter {
    override def widgetSelected(e: SelectionEvent) = {
      tableViewer.get.foreach(viewer => viewer.getComparator() match {
        case comparator: TemplateComparator if comparator.column == column =>
          comparator.switchDirection()
          viewer.refresh()
        case comparator: TemplateComparator =>
          comparator.column = column
          viewer.refresh()
        case _ =>
      })
    }
  }
  /*
  object ActionCreateFrom extends Action(Messages.createFrom_text) with Loggable {
    override def run = TypeList.schema { (dialog, selected) =>
      val from = selected
      // create new ID
      val toName = getNewTypeSchemaCopyName(from.name, dialog)
      assert(!dialog.actualContent.exists(_.name == toName),
        s"Unable to create the type schema copy. The schema $toName is already exists")
      val to = TypeSchema(UUID.randomUUID(), toName, from.description, from.entities.map(_.copy()))
      JobShowTypeEditor(to, dialog.actualContent.toList, Some(to) == dialog.actualActiveSchema.value).
        foreach(_.setOnSucceeded { job =>
          job.getValue.foreach {
            case (schema, active) => Main.exec {
              dialog.actualContent += schema
              if (active)
                dialog.actualActiveSchema.value = Some(schema)
            }
          }
        }.execute)
    }
  }
*/
  /*
   * Actions
   */
  object ActionAutoResize extends Action(Messages.autoresize_key, IAction.AS_CHECK_BOX) {
    setChecked(true)
    override def run = if (isChecked()) ElementTemplateList.dialog.foreach(_.autoresize)
  }
  object ActionCreateFrom extends Action(Messages.createFrom_text) with Loggable {
    override def run = ElementTemplateList.template { (dialog, before) =>
      val from = before.element
      // create new ID
      val toID = dialog.getNewTemplateCopyID(from.eId)
      // create an element for a new template
      val to = from.asInstanceOf[Element[Stash]].eCopy(from.eStash.copy(id = toID, unique = UUID.randomUUID))
      // create a template for a 'to' element
      val newTemplate = new ElementTemplate(to, before.factory)
      // start job
      JobShowElementTemplateEditor(newTemplate, dialog.actual.toSet).foreach(_.setOnSucceeded { job =>
        job.getValue.foreach { case (after) => Main.exec { dialog.updateActualTemplate(before, after) } }
      }.execute())
    }
  }
  object ActionEdit extends Action(Messages.edit_text) {
    override def run = ElementTemplateList.template { (dialog, before) =>
      JobShowElementTemplateEditor(before, dialog.actual.toSet).
        foreach(_.setOnSucceeded { job =>
          job.getValue.foreach { case (after) => Main.exec { dialog.updateActualTemplate(before, after) } }
        }.execute)
    }
  }
  object ActionRemove extends Action(Messages.remove_text) {
    override def run = ElementTemplateList.template { (dialog, selected) =>
      if (!ElementTemplate.predefined.exists(_.id == selected.id))
        dialog.actual -= selected
    }
  }
}
