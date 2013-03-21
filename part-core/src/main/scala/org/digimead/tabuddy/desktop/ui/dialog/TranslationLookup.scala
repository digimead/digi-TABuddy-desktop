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

package org.digimead.tabuddy.desktop.ui.dialog

import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import java.util.regex.Pattern
import scala.Array.canBuildFrom
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.future
import scala.ref.WeakReference
import org.digimead.digi.lib.log.Loggable
import org.digimead.tabuddy.desktop.Main
import org.digimead.tabuddy.desktop.Resources
import org.digimead.tabuddy.desktop.res.Messages
import org.digimead.tabuddy.desktop.ui.Default
import org.eclipse.core.databinding.observable.ChangeEvent
import org.eclipse.core.databinding.observable.IChangeListener
import org.eclipse.core.databinding.observable.value.WritableValue
import org.eclipse.jface.action.Action
import org.eclipse.jface.action.IAction
import org.eclipse.jface.action.IMenuListener
import org.eclipse.jface.action.IMenuManager
import org.eclipse.jface.action.MenuManager
import org.eclipse.jface.dialogs.IDialogConstants
import org.eclipse.jface.viewers.TableViewer
import org.eclipse.jface.viewers.Viewer
import org.eclipse.jface.viewers.ViewerComparator
import org.eclipse.jface.viewers.ViewerFilter
import org.eclipse.swt.events.DisposeEvent
import org.eclipse.swt.events.DisposeListener
import org.eclipse.swt.events.SelectionAdapter
import org.eclipse.swt.events.SelectionEvent
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Control
import org.eclipse.swt.widgets.Shell
import org.digimead.tabuddy.desktop.ui.BaseElement

class TranslationLookup(val parentShell: Shell)
  extends org.digimead.tabuddy.desktop.res.dialog.TranslationLookup(parentShell) with Dialog with Loggable {
  /** The auto resize lock */
  protected val autoResizeLock = new ReentrantLock()
  assert(TranslationLookup.dialog.isEmpty, "TranslationLookup dialog is already active")

  /** Get selected translation */
  def getSelected() = Main.execNGet { Option(getSelectedTranslation.getValue).asInstanceOf[Option[Translation]] }

  /** Auto resize tableviewer columns */
  protected def autoresize() = if (autoResizeLock.tryLock()) try {
    Thread.sleep(50)
    Main.execNGet {
      if (!getTableViewer.getTable.isDisposed()) {
        adjustColumnWidth(getTableViewerColumnKey(), Default.columnPadding)
        getTableViewer.refresh()
      }
    }
  } finally {
    autoResizeLock.unlock()
  }
  /** Create contents of the dialog. */
  override protected def createDialogArea(parent: Composite): Control = {
    val result = super.createDialogArea(parent)
    val viewer = getTableViewer()
    val translations = getTranslations
    Resources.messages.foreach {
      case (key, value) =>
        val t = new Translation
        t.setKey(key)
        t.setValue(value)
        translations.addTranslation(t)
    }
    // Add the context menu
    val menuMgr = new MenuManager()
    val menu = menuMgr.createContextMenu(viewer.getControl)
    menuMgr.addMenuListener(new IMenuListener() {
      override def menuAboutToShow(manager: IMenuManager) {
        manager.add(TranslationLookup.ActionAutoResize)
      }
    })
    menuMgr.setRemoveAllWhenShown(true)
    viewer.getControl.setMenu(menu)
    // Add the dispose listener
    getShell().addDisposeListener(new DisposeListener { def widgetDisposed(e: DisposeEvent) = TranslationLookup.dialog = None })
    // Set the dialog window title
    getShell().setText(Messages.translationDialog_text.format(Locale.getDefault))
    // Add the filter
    val filter = new AtomicReference(".*".r.pattern)
    val filterListener = new BaseElement.RegexFilterListener(filter) {
      override def handleChange(event: ChangeEvent) {
        super.handleChange(event)
        getTableViewer.refresh()
        autoresize()
      }
    }
    getFilter.addChangeListener(filterListener)
    getTableViewer().setFilters(Array(new TranslationLookup.TranslationFilter(filter)))
    getTableViewerColumnKey.getColumn.addSelectionListener(new TranslationLookup.TranslationSelectionAdapter(WeakReference(viewer), 0))
    getTableViewerColumnValue.getColumn.addSelectionListener(new TranslationLookup.TranslationSelectionAdapter(WeakReference(viewer), 1))
    viewer.setComparator(new TranslationLookup.TranslationComparator)

    // Add the result listener
    val resultListener = new IChangeListener() {
      override def handleChange(event: ChangeEvent) = Option(getButton(IDialogConstants.OK_ID)).foreach(button =>
        button.setEnabled(getSelectedTranslation.getValue() != null))
    }
    getSelectedTranslation.addChangeListener(resultListener)
    // Add the dispose listener
    getShell().addDisposeListener(new DisposeListener {
      def widgetDisposed(e: DisposeEvent) {
        getFilter.removeChangeListener(filterListener)
        getSelectedTranslation.removeChangeListener(resultListener)
        TranslationLookup.dialog = None
      }
    })
    TranslationLookup.dialog = Some(this)
    result
  }
  /** On dialog active */
  override protected def onActive = {
    future { autoresize() }
  }
}

object TranslationLookup extends Loggable {
  /** There is may be only one dialog instance at time */
  @volatile private var dialog: Option[TranslationLookup] = None
  /** Actual sorting direction */
  @volatile private var sortingDirection = Default.sortingDirection
  /** Actual sortBy column index */
  @volatile private var sortingColumn = 1

  class TranslationFilter(filter: AtomicReference[Pattern]) extends ViewerFilter {
    override def select(viewer: Viewer, parentElement: AnyRef, element: AnyRef): Boolean = {
      val pattern = filter.get
      val translation = element.asInstanceOf[TranslationLookup#Translation]
      pattern.matcher(translation.getKey.toLowerCase()).matches() || pattern.matcher(translation.getValue.toLowerCase()).matches()
    }
  }
  class TranslationComparator extends ViewerComparator {
    private var _column = TranslationLookup.sortingColumn
    private var _direction = TranslationLookup.sortingDirection

    /** Active column getter */
    def column = _column
    /** Active column setter */
    def column_=(arg: Int) {
      _column = arg
      TranslationLookup.sortingColumn = _column
      _direction = Default.sortingDirection
      TranslationLookup.sortingDirection = _direction
    }
    /** Sorting direction */
    def direction = _direction
    /**
     * Returns a negative, zero, or positive number depending on whether
     * the first element is less than, equal to, or greater than
     * the second element.
     */
    override def compare(viewer: Viewer, e1: Object, e2: Object): Int = {
      val translation1 = e1.asInstanceOf[TranslationLookup#Translation]
      val translation2 = e2.asInstanceOf[TranslationLookup#Translation]
      val rc = column match {
        case 0 => translation1.getKey().compareTo(translation2.getKey())
        case 1 => translation1.getValue().compareTo(translation2.getValue())
        case index =>
          log.fatal(s"unknown column with index $index"); 0
      }
      if (_direction) -rc else rc
    }
    /** Switch comparator direction */
    def switchDirection() {
      _direction = !_direction
      TranslationLookup.sortingDirection = _direction
    }
  }
  class TranslationSelectionAdapter(tableViewer: WeakReference[TableViewer], column: Int) extends SelectionAdapter {
    override def widgetSelected(e: SelectionEvent) = {
      tableViewer.get.foreach(viewer => viewer.getComparator() match {
        case comparator: TranslationComparator if comparator.column == column =>
          comparator.switchDirection()
          viewer.refresh()
        case comparator: TranslationComparator =>
          comparator.column = column
          viewer.refresh()
        case _ =>
      })
    }
  }
  /*
   * Actions
   */
  object ActionAutoResize extends Action(Messages.autoresize_key, IAction.AS_CHECK_BOX) {
    setChecked(true)
    override def run = if (isChecked()) TranslationLookup.dialog.foreach(dialog => future { dialog.autoresize })
  }
}
