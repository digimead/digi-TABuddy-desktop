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

package org.digimead.tabuddy.desktop.model.editor.ui.view.editor

import org.digimead.tabuddy.desktop.core.support.App
import org.eclipse.e4.core.contexts.ContextInjectionFactory
import org.eclipse.e4.core.di.annotations.Optional
import org.eclipse.jface.action.Action
import org.eclipse.jface.action.IAction
import javax.inject.Inject
import javax.inject.Named
import org.digimead.tabuddy.desktop.model.editor.Messages
import org.digimead.tabuddy.desktop.model.editor.ModelEditor

/**
 * TableView actions.
 */
trait ViewActions {
  this: View ⇒

  object ActionAutoResize extends Action(Messages.autoresize_key) {
    setChecked(true)
    /** Auto resize the table view components if ActionAutoResize is checked */
    def apply(immediately: Boolean) = {
      if (tree.ActionAutoResize.isChecked())
        tree.ActionAutoResize(immediately)
      if (table.ActionAutoResize.isChecked())
        table.ActionAutoResize(immediately)
    }
  }
  object ActionCollapseAll extends Action(Messages.collapseAll_text) {
    def apply() = View.withRedrawDelayed(ViewActions.this) {
      Tree.collapseAll(ViewActions.this)
      ActionAutoResize(false)
    }
    override def run() = apply()
  }
  object ActionExpandAll extends Action(Messages.expandAll_text) {
    def apply() = View.withRedrawDelayed(ViewActions.this) {
      Tree.expandAll(ViewActions.this)
      ActionAutoResize(false)
    }
    override def run() = apply()
  }
  object ActionElementNew extends Action(Messages.new_text) {
    override def run() = {} //OperationCreateElement(Data.fieldElement.value).foreach(_.execute)
  }
  object ActionElementEdit extends Action(Messages.edit_text) {
    override def run() = {} //OperationModifyElement(Data.fieldElement.value).foreach(_.execute)
  }
  object ActionElementDelete extends Action(Messages.delete_text) {
    override def run = {}
  }
  /** Toggle visibility of identificators in the view. */
  object ActionToggleIdentificators extends Action(Messages.identificators_text, IAction.AS_CHECK_BOX) {
    setChecked(true)
    ViewActions.this.getParent.getContext.foreach { context ⇒
      context.set(ModelEditor.Id.stateOfToggleIdentificator, isChecked(): java.lang.Boolean)
      ContextInjectionFactory.inject(this, context)
    }

    def apply() = Table.toggleColumnId(isChecked(), ViewActions.this)
    /** Update checked state from context of the current view. */
    @Inject @Optional
    def onStateOfToggleIdentificatorChanged(@Named(ModelEditor.Id.stateOfToggleIdentificator) checked: java.lang.Boolean) =
      Option(checked) foreach (checked ⇒ App.exec {
        if (checked != isChecked()) {
          setChecked(checked)
          apply()
        }
      })
    override def run() = {
      ViewActions.this.getParent.getContext.foreach(_.set(ModelEditor.Id.stateOfToggleIdentificator, isChecked(): java.lang.Boolean))
      apply()
    }
  }
  /** Toggle visibility of empty rows in the view. */
  object ActionToggleEmpty extends Action(Messages.emptyRows_text, IAction.AS_CHECK_BOX) {
    setChecked(true)
    ViewActions.this.getParent.getContext.foreach { context ⇒
      context.set(ModelEditor.Id.stateOfToggleEmpty, isChecked(): java.lang.Boolean)
      ContextInjectionFactory.inject(this, context)
    }

    def apply() = Table.toggleEmptyRows(!isChecked(), ViewActions.this)
    /** Update checked state from context of the current view. */
    @Inject @Optional
    def onStateOfToggleIdentificatorChanged(@Named(ModelEditor.Id.stateOfToggleEmpty) checked: java.lang.Boolean) =
      Option(checked) foreach (checked ⇒ App.exec {
        if (checked != isChecked()) {
          setChecked(checked)
          apply()
        }
      })
    override def run() = apply()
  }
  object ActionToggleExpand extends Action(Messages.expandNew_text, IAction.AS_CHECK_BOX) {
    setChecked(false)

    def apply() = Tree.toggleAutoExpand(isChecked(), ViewActions.this)
    override def run() = apply()
  }
  object ActionHideTree extends Action(Messages.autoresize_key, IAction.AS_CHECK_BOX) {
    setChecked(false)

    def apply() = if (isChecked())
      getSashForm.setMaximizedControl(table.tableViewer.getTable())
    else
      getSashForm.setMaximizedControl(null)
    override def run() = apply()
  }
  /**
   * Hide system elements.
   * unchecked - filter is enabled
   * checked - filter is disabled
   * by default - enabled
   */
  object ActionToggleSystem extends Action(Messages.systemElements_text, IAction.AS_CHECK_BOX) {
    setChecked(false)
    ViewActions.this.getParent.getContext.foreach { context ⇒
      context.set(ModelEditor.Id.stateOfToggleSystem, isChecked(): java.lang.Boolean)
      ContextInjectionFactory.inject(this, context)
    }

    def apply() = View.withRedrawDelayed(ViewActions.this) {
      Tree.toggleSystemElementsFilter(!isChecked(), ViewActions.this)
      ActionAutoResize(false)
    }
    /** Update checked state from context of the current view. */
    @Inject @Optional
    def onStateOfToggleIdentificatorChanged(@Named(ModelEditor.Id.stateOfToggleSystem) checked: java.lang.Boolean) =
      Option(checked) foreach (checked ⇒ App.exec {
        if (checked != isChecked()) {
          setChecked(checked)
          apply()
        }
      })
    override def run() = apply()
  }
}
