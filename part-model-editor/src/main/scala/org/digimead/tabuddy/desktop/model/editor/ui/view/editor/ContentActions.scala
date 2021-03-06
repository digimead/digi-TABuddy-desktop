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

package org.digimead.tabuddy.desktop.model.editor.ui.view.editor

import javax.inject.{ Inject, Named }
import org.digimead.tabuddy.desktop.core.{ Messages ⇒ CMessages }
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.model.editor.{ Messages, ModelEditor }
import org.eclipse.e4.core.contexts.ContextInjectionFactory
import org.eclipse.e4.core.di.annotations.Optional
import org.eclipse.jface.action.{ Action, IAction }

/**
 * TableView actions.
 */
trait ContentActions {
  this: Content ⇒

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
    def apply() = Content.withRedrawDelayed(ContentActions.this) {
      Tree.collapseAll(ContentActions.this)
      ActionAutoResize(false)
    }
    override def run() = apply()
  }
  object ActionExpandAll extends Action(Messages.expandAll_text) {
    def apply() = Content.withRedrawDelayed(ContentActions.this) {
      Tree.expandAll(ContentActions.this)
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
  /** Toggle visibility of empty rows in the view. */
  object ActionToggleEmpty extends Action(Messages.emptyRows_text, IAction.AS_CHECK_BOX) {
    ContextInjectionFactory.inject(this, context)
    App.execNGet {
      setChecked(true)
      apply(false)
    }

    def apply(newValue: java.lang.Boolean = !Option(context.get(ModelEditor.Id.stateOfToggleEmpty).
      asInstanceOf[java.lang.Boolean]).getOrElse(isChecked: java.lang.Boolean)) = App.exec {
      if (isChecked != newValue) {
        setChecked(newValue)
        Table.toggleEmptyRows(!newValue, ContentActions.this)
      }
      if (context.get(ModelEditor.Id.stateOfToggleEmpty) != newValue)
        context.set(ModelEditor.Id.stateOfToggleEmpty, newValue)
    }
    override def run() = apply()

    /** Update checked state of this action. */
    @Inject @Optional
    def onStateChanged(@Named(ModelEditor.Id.stateOfToggleEmpty) checked: java.lang.Boolean) =
      Option(checked) foreach (checked ⇒ apply(checked))
  }
  /** Toggle visibility of identificators in the view. */
  object ActionToggleIdentificators extends Action(CMessages.identificators_text, IAction.AS_CHECK_BOX) {
    ContextInjectionFactory.inject(this, context)
    App.execNGet {
      setChecked(true)
      apply(false)
    }

    def apply(newValue: java.lang.Boolean = !Option(context.get(ModelEditor.Id.stateOfToggleIdentificator).
      asInstanceOf[java.lang.Boolean]).getOrElse(isChecked: java.lang.Boolean)) = App.exec {
      if (isChecked != newValue) {
        setChecked(newValue)
        Table.toggleColumnId(newValue, ContentActions.this)
      }
      if (context.get(ModelEditor.Id.stateOfToggleIdentificator) != newValue)
        context.set(ModelEditor.Id.stateOfToggleIdentificator, newValue)
    }
    override def run() = apply()

    /** Update checked state of this action. */
    @Inject @Optional
    protected def onStateChanged(@Named(ModelEditor.Id.stateOfToggleIdentificator) checked: java.lang.Boolean) =
      Option(checked) foreach (checked ⇒ apply(checked))
  }
  object ActionHideTree extends Action(Messages.autoresize_key, IAction.AS_CHECK_BOX) {
    App.execNGet {
      setChecked(false)
    }

    def apply() = if (isChecked())
      getSashForm.setMaximizedControl(table.tableViewer.getTable())
    else
      getSashForm.setMaximizedControl(null)
    override def run() = apply()
  }
}
