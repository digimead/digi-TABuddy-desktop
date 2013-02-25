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

import org.digimead.digi.lib.log.Loggable
import org.digimead.tabuddy.desktop.Main
import org.digimead.tabuddy.desktop.payload.ElementTemplate
import org.digimead.tabuddy.desktop.payload.TemplateProperty
import org.digimead.tabuddy.desktop.payload.TemplatePropertyGroup
import org.digimead.tabuddy.desktop.payload.PropertyType
import org.digimead.tabuddy.desktop.res.Messages
import org.digimead.tabuddy.desktop.res.SWTResourceManager
import org.digimead.tabuddy.desktop.support.WritableValue
import org.digimead.tabuddy.desktop.support.WritableValue.wrapper2underlying
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.Model.model2implementation
import org.digimead.tabuddy.model.element.Element
import org.eclipse.core.databinding.observable.ChangeEvent
import org.eclipse.core.databinding.observable.IChangeListener
import org.eclipse.jface.databinding.swt.WidgetProperties
import org.eclipse.jface.dialogs.IDialogConstants
import org.eclipse.swt.SWT
import org.eclipse.swt.graphics.Point
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Control
import org.eclipse.swt.widgets.Shell
import org.eclipse.ui.forms.widgets.ExpandableComposite

class ElementEditor(val parentShell: Shell, element: Element.Generic, template: ElementTemplate.Interface)
  extends org.digimead.tabuddy.desktop.res.dialog.ElementEditor(parentShell) with Dialog with Loggable {
  /** The property representing the current element id */
  protected lazy val idField = WritableValue("")
  /** Element properties (Option[initialValue], editor). Available only from the UI thread */
  var properties = Seq[(Option[AnyRef with java.io.Serializable], PropertyType.Editor[_ <: AnyRef with java.io.Serializable])]()
  /** Element properties listener */
  val propertiesListener = new IChangeListener() {
    override def handleChange(event: ChangeEvent) = Option(getButton(IDialogConstants.OK_ID)).foreach(_.
      setEnabled(!properties.forall { case (initial, editor) => initial == Option(editor.data.value) || (initial.isEmpty && editor.isEmpty) }))
  }
  assert(ElementEditor.dialog.isEmpty, "ElementCreate dialog is already active")

  /** Add the group title to creation form */
  def addGroupTitle(group: TemplatePropertyGroup) {
    val title = getToolkit.createSection(getForm.getBody(), ExpandableComposite.TWISTIE | ExpandableComposite.TITLE_BAR)
    val layoutData = new GridData(SWT.FILL, SWT.CENTER, true, false)
    layoutData.horizontalSpan = 3
    title.setText(group.id.name)
    title.setExpanded(true)
    title.setToolTipText(group.description)
    title.setLayoutData(layoutData)
    getToolkit().paintBordersFor(title)
  }
  /** Add the property group marker */
  def addGroupMarker(group: TemplatePropertyGroup, size: Int) {
    val marker = getToolkit.createLabel(getForm.getBody(), "  ", SWT.NONE)
    val markerLayoutData = new GridData()
    markerLayoutData.verticalSpan = size
    marker.setToolTipText(group.id.name + " - " + group.description)
    marker.setLayoutData(markerLayoutData)
    marker.setBackground(SWTResourceManager.getColor(SWT.COLOR_BLUE))
  }
  /** Add the property id label*/
  def addLabel(property: TemplateProperty[_]) {
    val label = getToolkit.createLabel(getForm.getBody(), property.id.name, SWT.NONE)
    val tooltip = if (property.required)
      Messages.acquire_text.format(property.ptype.typeSymbol)
    else
      Messages.activate_text.format(property.ptype.typeSymbol)
    //      property.typeSymbol + " " + Messages.
    label.setToolTipText(tooltip)
    label.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false))
  }
  /** Add the property value field */
  def addCellEditor[T <: AnyRef with java.io.Serializable: Manifest](property: TemplateProperty[T]): (Option[T], PropertyType.Editor[T]) = {
    val initial = element.eGet[T](property.id).map(_.get) orElse property.defaultValue
    val editor = property.ptype.createEditor(initial)
    val control = editor.createControl(getToolkit, getForm.getBody(), SWT.BORDER)
    editor.addValidator(control)
    control.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false))
    control.setBackground(getForm.getBackground())
    (initial, editor)
  }
  /** Create contents of the dialog. */
  override protected def createDialogArea(parent: Composite): Control = {
    val container = super.createDialogArea(parent).asInstanceOf[Composite]
    val shell = getShell
    val form = getForm
    // Handle the element name
    val ancestors = element.eAncestors.map(_.eId.name).reverse
    val formTitle = if (ancestors.size > 3) "%s .../" + ancestors.takeRight(3).mkString("/") + "/%s" else "%s /" + ancestors.mkString("/") + "/%s"
    Main.bindingContext.bindValue(WidgetProperties.text(SWT.Modify).observeDelayed(50, getTxtElementName), idField)
    idField.addChangeListener { event => form.setText(formTitle.format(template.id.name, idField.value)) }
    idField.value = element.eId.name
    // Sort by group priority
    properties = template.properties.toSeq.sortBy(_._1.priority).map {
      case (group, properties) =>
        addGroupTitle(group)
        addGroupMarker(group, properties.size)
        properties.map { property =>
          addLabel(property)
          addCellEditor(property)
        }
    }.flatten
    properties.foreach { case (initial, editor) => editor.data.underlying.addChangeListener(propertiesListener) }
    // set dialog message
    setMessage(Messages.elementEditorDescription_text.format(Model.eId.name))
    // set dialog window title
    getShell().setText(Messages.elementEditorDialog_text.format(element.eId.name))
    ElementEditor.dialog = Some(this)
    container
  }
  /** Return the initial size of the dialog. */
  override protected def getInitialSize(): Point = {
    val default = super.getInitialSize
    val packedShellSize = getShell.computeSize(SWT.DEFAULT, SWT.DEFAULT, false)
    val adjustedHeight = math.min(default.y, packedShellSize.y)
    new Point(default.x, adjustedHeight)
  }
}

object ElementEditor {
  /** There is may be only one dialog instance at time */
  @volatile private var dialog: Option[ElementEditor] = None
}
