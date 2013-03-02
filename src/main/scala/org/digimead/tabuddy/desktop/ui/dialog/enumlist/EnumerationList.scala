package org.digimead.tabuddy.desktop.ui.dialog.enumlist

import java.util.UUID
import java.util.concurrent.locks.ReentrantLock

import scala.collection.immutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.future
import scala.ref.WeakReference

import org.digimead.digi.lib.log.Loggable
import org.digimead.tabuddy.desktop.Data
import org.digimead.tabuddy.desktop.Main
import org.digimead.tabuddy.desktop.job.Job
import org.digimead.tabuddy.desktop.job.Job.job2implementation
import org.digimead.tabuddy.desktop.job.JobShowEnumerationEditor
import org.digimead.tabuddy.desktop.payload.Enumeration
import org.digimead.tabuddy.desktop.payload.Payload
import org.digimead.tabuddy.desktop.payload.PropertyType
import org.digimead.tabuddy.desktop.res.Messages
import org.digimead.tabuddy.desktop.support.WritableList
import org.digimead.tabuddy.desktop.support.WritableValue
import org.digimead.tabuddy.desktop.ui.dialog.Dialog
import org.digimead.tabuddy.desktop.ui.dialog.enumed.EnumerationEditor
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.Model.model2implementation
import org.digimead.tabuddy.model.element.Element
import org.digimead.tabuddy.model.element.Stash
import org.eclipse.jface.action.Action
import org.eclipse.jface.action.ActionContributionItem
import org.eclipse.jface.action.IMenuListener
import org.eclipse.jface.action.IMenuManager
import org.eclipse.jface.action.MenuManager
import org.eclipse.jface.databinding.viewers.ObservableListContentProvider
import org.eclipse.jface.databinding.viewers.ViewersObservables
import org.eclipse.jface.dialogs.IDialogConstants
import org.eclipse.jface.viewers.CellLabelProvider
import org.eclipse.jface.viewers.ColumnViewerToolTipSupport
import org.eclipse.jface.viewers.ISelectionChangedListener
import org.eclipse.jface.viewers.IStructuredSelection
import org.eclipse.jface.viewers.SelectionChangedEvent
import org.eclipse.jface.viewers.StructuredSelection
import org.eclipse.jface.viewers.TableViewer
import org.eclipse.jface.viewers.TableViewerColumn
import org.eclipse.jface.viewers.Viewer
import org.eclipse.jface.viewers.ViewerCell
import org.eclipse.jface.viewers.ViewerComparator
import org.eclipse.swt.SWT
import org.eclipse.swt.events.DisposeEvent
import org.eclipse.swt.events.DisposeListener
import org.eclipse.swt.events.SelectionAdapter
import org.eclipse.swt.events.SelectionEvent
import org.eclipse.swt.graphics.Point
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Control
import org.eclipse.swt.widgets.Event
import org.eclipse.swt.widgets.Listener
import org.eclipse.swt.widgets.Shell
import org.eclipse.swt.widgets.TableItem

class EnumerationList(val parentShell: Shell, val initial: List[Enumeration.Interface[_ <: AnyRef with java.io.Serializable]])
  extends org.digimead.tabuddy.desktop.res.dialog.EnumerationList(parentShell) with Dialog with Loggable {
  /** The actual enumeration list */
  protected[enumlist] val actual = WritableList[Enumeration.Interface[_ <: AnyRef with java.io.Serializable]](initial.map(enumeration =>
    enumeration.generic.copy(element = enumeration.element.eCopy)))
  /** The auto resize lock */
  protected lazy val autoResizeLock = new ReentrantLock()
  /** The property representing enumeration in current UI field(s) that available for user */
  protected lazy val enumerationField = WritableValue[Enumeration.Interface[_ <: AnyRef with java.io.Serializable]]
  assert(EnumerationList.dialog.isEmpty, "EnumerationList dialog is already active")

  /** Get actual enumerations */
  def getModifiedEnumerations(): Set[Enumeration.Interface[_ <: AnyRef with java.io.Serializable]] = actual.toSet

  /** Auto resize tableviewer columns */
  protected def autoresize() = if (autoResizeLock.tryLock()) try {
    Thread.sleep(50)
    Main.execNGet {
      if (!getTableEnumerations.getTable.isDisposed()) {
        adjustColumnWidth(getTableViewerColumnAvailable, Dialog.columnPadding)
        adjustColumnWidth(getTableViewerColumnIdentificator, Dialog.columnPadding)
        getTableEnumerations.refresh()
      }
    }
  } finally {
    autoResizeLock.unlock()
  }
  /** Create contents of the dialog. */
  override protected def createDialogArea(parent: Composite): Control = {
    val result = super.createDialogArea(parent)
    new ActionContributionItem(EnumerationList.ActionCreate).fill(getCompositeEnumerations())
    new ActionContributionItem(EnumerationList.ActionCreateFrom).fill(getCompositeEnumerations())
    new ActionContributionItem(EnumerationList.ActionEdit).fill(getCompositeEnumerations())
    new ActionContributionItem(EnumerationList.ActionRemove).fill(getCompositeEnumerations())
    EnumerationList.ActionCreate.setEnabled(true)
    EnumerationList.ActionCreateFrom.setEnabled(false)
    EnumerationList.ActionEdit.setEnabled(false)
    EnumerationList.ActionRemove.setEnabled(false)
    initTableEnumerations()
    val actualListener = actual.addChangeListener { event =>
      if (EnumerationEditor.ActionAutoResize.isChecked())
        future { autoresize() }
      updateOK()
    }
    // add dispose listener
    getShell().addDisposeListener(new DisposeListener {
      def widgetDisposed(e: DisposeEvent) {
        actual.removeChangeListener(actualListener)
        EnumerationList.dialog = None
      }
    })
    // set dialog message
    setMessage(Messages.enumerationListDescription_text.format(Model.eId.name))
    // set dialog window title
    getShell().setText(Messages.enumerationListDialog_text.format(Model.eId.name))
    EnumerationList.dialog = Some(this)
    result
  }
  /** Initialize tableEnumerationList */
  protected def initTableEnumerations() {
    val viewer = getTableEnumerations()
    viewer.setContentProvider(new ObservableListContentProvider())
    getTableViewerColumnAvailable.setLabelProvider(new ColumnAvailable.TLabelProvider)
    getTableViewerColumnAvailable.setEditingSupport(new ColumnAvailable.TEditingSupport(viewer, this))
    getTableViewerColumnAvailable.getColumn.addSelectionListener(new EnumerationList.EnumerationSelectionAdapter(WeakReference(viewer), 0))
    getTableViewerColumnIdentificator.setLabelProvider(new ColumnIdentificator.TLabelProvider)
    getTableViewerColumnIdentificator.setEditingSupport(new ColumnIdentificator.TEditingSupport(viewer, this))
    getTableViewerColumnIdentificator.getColumn.addSelectionListener(new EnumerationList.EnumerationSelectionAdapter(WeakReference(viewer), 1))
    getTableViewerColumnDescription.setLabelProvider(new ColumnLabel.TLabelProvider)
    getTableViewerColumnDescription.setEditingSupport(new ColumnLabel.TEditingSupport(viewer, this))
    getTableViewerColumnDescription.getColumn.addSelectionListener(new EnumerationList.EnumerationSelectionAdapter(WeakReference(viewer), 2))
    // Add a SWT.CHECK support
    viewer.getTable.addListener(SWT.Selection, new Listener() {
      def handleEvent(event: Event) = if (event.detail == SWT.CHECK)
        event.item match {
          case tableItem: TableItem =>
            val index = tableItem.getParent().indexOf(tableItem)
            viewer.getElementAt(index) match {
              case before: Enumeration[_] =>
                updateActualEnumeration(before, before.copy(availability = tableItem.getChecked()))
              case element =>
                log.fatal(s"unknown element $element")
            }
          case item =>
            log.fatal(s"unknown item $item")
        }
    })
    // Activate the tooltip support for the viewer
    ColumnViewerToolTipSupport.enableFor(viewer)
    // Add context menu
    val menuMgr = new MenuManager()
    val menu = menuMgr.createContextMenu(viewer.getControl)
    menuMgr.addMenuListener(new IMenuListener() {
      override def menuAboutToShow(manager: IMenuManager) {
        manager.add(EnumerationList.ActionAutoResize)
      }
    })
    menuMgr.setRemoveAllWhenShown(true)
    viewer.getControl.setMenu(menu)
    // Add selection listener
    viewer.addSelectionChangedListener(new ISelectionChangedListener() {
      override def selectionChanged(event: SelectionChangedEvent) = event.getSelection() match {
        case selection: IStructuredSelection if !selection.isEmpty() =>
          EnumerationList.ActionCreateFrom.setEnabled(true)
          EnumerationList.ActionEdit.setEnabled(true)
          EnumerationList.ActionRemove.setEnabled(true)
        case selection =>
          EnumerationList.ActionCreateFrom.setEnabled(false)
          EnumerationList.ActionEdit.setEnabled(false)
          EnumerationList.ActionRemove.setEnabled(false)
      }
    })
    actual.addChangeListener(event => future {
      if (EnumerationList.ActionAutoResize.isChecked())
        Main.exec { if (!viewer.getTable.isDisposed()) autoresize() }
    })
    val comparator = new EnumerationList.EnumerationComparator
    viewer.setComparator(comparator)
    viewer.setInput(actual.underlying)
    Main.bindingContext.bindValue(ViewersObservables.observeSingleSelection(viewer), enumerationField)
  }
  /** On dialog active */
  override protected def onActive = {
    updateOK()
    autoresize()
  }
  /** Updates an actual enumearion */
  protected[enumlist] def updateActualEnumeration(before: Enumeration.Interface[_ <: AnyRef with java.io.Serializable], after: Enumeration.Interface[_ <: AnyRef with java.io.Serializable]) {
    val index = actual.indexOf(before)
    actual.update(index, after)
    if (index == actual.size - 1)
      getTableEnumerations.refresh() // Workaround for the JFace bug. Force the last element modification.
    getTableEnumerations.setSelection(new StructuredSelection(after), true)
  }
  /** Update OK button state */
  protected def updateOK() = Option(getButton(IDialogConstants.OK_ID)).foreach(_.setEnabled(
    initial.size != actual.size ||
      !(initial, actual).zipped.forall { (initial, actual) =>
        (initial eq actual) || (
          initial.availability == actual.availability &&
          initial.label == actual.label &&
          initial.element.eq(actual.element) &&
          initial.id == actual.id &&
          initial.ptype == actual.ptype &&
          initial.constants.size == actual.constants.size &&
          (initial.constants, actual.constants).zipped.forall(Enumeration.compareDeep(_, _)))
      }))
}

object EnumerationList extends Loggable {
  /** There is may be only one dialog instance at time */
  @volatile private var dialog: Option[EnumerationList] = None
  /** Auto resize column padding */
  private val columnPadding = 10
  /** Ascending sort constant */
  private val ASCENDING = 0
  /** Descending sort constant */
  private val DESCENDING = 1
  /** Actual sortBy column index */
  @volatile private var sortColumn = 1
  /** Default sort direction */
  private val defaultDirection = ASCENDING
  /** Actual sort direction */
  @volatile private var sortDirection = defaultDirection

  /** Apply a f(x) to the selected enumeration if any */
  protected def enumeration[T](f: (EnumerationList, Enumeration.Interface[_ <: AnyRef with java.io.Serializable]) => T): Option[T] =
    dialog.flatMap(d => Option(d.enumerationField.value).map(f(d, _)))
  /** Generate new ID: old ID + 'Copy' + N */
  protected def getNewEnumerationCopyID(id: Symbol, enumerations: List[Enumeration.Interface[_ <: AnyRef with java.io.Serializable]]): Symbol = {
    val sameIds = immutable.HashSet(enumerations.filter(_.id.name.startsWith(id.name)).map(_.id.name).toSeq: _*)
    var n = 0
    var newId = id.name + Messages.copy_item_text
    while (sameIds(newId)) {
      n += 1
      newId = id.name + Messages.copy_item_text + n
    }
    Symbol(newId)
  }

  class EnumerationComparator extends ViewerComparator {
    private var _column = EnumerationList.sortColumn
    private var _direction = EnumerationList.sortDirection

    /** Active column getter */
    def column = _column
    /** Active column setter */
    def column_=(arg: Int) {
      _column = arg
      EnumerationList.sortColumn = _column
      _direction = EnumerationList.defaultDirection
      EnumerationList.sortDirection = _direction
    }
    /** Sorting direction */
    def direction = _direction
    /**
     * Returns a negative, zero, or positive number depending on whether
     * the first element is less than, equal to, or greater than
     * the second element.
     */
    override def compare(viewer: Viewer, e1: Object, e2: Object): Int = {
      val enum1 = e1.asInstanceOf[Enumeration[_]]
      val enum2 = e2.asInstanceOf[Enumeration[_]]
      val rc = column match {
        case 0 => enum1.availability.compareTo(enum2.availability)
        case 1 => enum1.id.name.compareTo(enum2.id.name)
        case 2 => enum1.label.compareTo(enum2.label)
        case index =>
          log.fatal(s"unknown column with index $index"); 0
      }
      if (_direction == EnumerationList.DESCENDING) -rc else rc
    }
    /** Switch comparator direction */
    def switchDirection() {
      _direction = 1 - _direction
      EnumerationList.sortDirection = _direction
    }
  }
  class EnumerationSelectionAdapter(tableViewer: WeakReference[TableViewer], column: Int) extends SelectionAdapter {
    override def widgetSelected(e: SelectionEvent) = {
      tableViewer.get.foreach(viewer => viewer.getComparator() match {
        case comparator: EnumerationComparator if comparator.column == column =>
          comparator.switchDirection()
          viewer.refresh()
        case comparator: EnumerationComparator =>
          comparator.column = column
          viewer.refresh()
        case _ =>
      })
    }
  }
  /*
   * Actions
   */
  object ActionAutoResize extends Action(Messages.autoresize_key) {
    setChecked(true)
    override def run = EnumerationList.dialog.foreach(_.autoresize)
  }
  object ActionCreate extends Action(Messages.create_text) with Loggable {
    override def run = EnumerationList.dialog.foreach { dialog =>
      val newEnumerationID = Payload.generateNew("NewEnumeration", "", newId => dialog.actual.exists(_.id.name == newId))
      val newEnumerationElement = Enumeration.factory(Symbol(newEnumerationID))
      val newEnumeration = new Enumeration(newEnumerationElement, PropertyType.defaultType)(Manifest.classType(PropertyType.defaultType.typeClass))
      JobShowEnumerationEditor(newEnumeration, dialog.actual.toList).foreach(_.setOnSucceeded { job =>
        job.getValue.foreach { case (enumeration) => Main.exec { dialog.actual += enumeration } }
      }.execute)
    }
  }
  object ActionCreateFrom extends Action(Messages.createFrom_text) with Loggable {
    override def run = EnumerationList.enumeration { (dialog, selected) =>
      val from = selected.element
      // create new ID
      val toID = getNewEnumerationCopyID(from.eId, dialog.actual.toList)
      assert(!dialog.actual.exists(_.id == toID),
        s"Unable to create the enumeration copy. The element $toID is already exists")
      // create an element for the new template
      val to = from.asInstanceOf[Element[Stash]].eCopy(from.eStash.copy(id = toID, unique = UUID.randomUUID))
      // create an enumeration for the 'to' element
      val newEnumeration = new Enumeration(to, selected.ptype)(Manifest.classType(selected.ptype.typeClass)).
        asInstanceOf[Enumeration[_ <: AnyRef with java.io.Serializable]]
      // start job
      JobShowEnumerationEditor(newEnumeration, dialog.actual.toList).foreach(_.setOnSucceeded { job =>
        job.getValue.foreach { case (enumeration) => Main.exec { dialog.actual += enumeration } }
      }.execute)
    }
  }
  object ActionEdit extends Action(Messages.edit_text) {
    override def run = EnumerationList.enumeration { (dialog, before) =>
      JobShowEnumerationEditor(before, dialog.actual.toList).foreach(_.setOnSucceeded { job =>
        job.getValue.foreach { case (after) => Main.exec { dialog.updateActualEnumeration(before, after) } }
      }.execute)
    }
  }
  object ActionRemove extends Action(Messages.remove_text) {
    override def run = EnumerationList.enumeration { (dialog, selected) =>
      Main.exec { dialog.actual -= selected }
    }
  }
}
