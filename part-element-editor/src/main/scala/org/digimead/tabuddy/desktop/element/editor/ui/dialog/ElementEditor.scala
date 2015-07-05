/**
 * This file is part of the TA Buddy project.
 * Copyright (c) 2012-2015 Alexey Aksenov ezh@ezh.msk.ru
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

package org.digimead.tabuddy.desktop.element.editor.ui.dialog

import javax.inject.{ Inject, Named }
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.Messages
import org.digimead.tabuddy.desktop.core.definition.Context
import org.digimead.tabuddy.desktop.core.support.{ App, WritableList, WritableValue }
import org.digimead.tabuddy.desktop.core.ui.ResourceManager
import org.digimead.tabuddy.desktop.core.ui.definition.Dialog
import org.digimead.tabuddy.desktop.core.ui.support.SymbolValidator
import org.digimead.tabuddy.desktop.logic.payload
import org.digimead.tabuddy.desktop.logic.payload.marker.GraphMarker
import org.digimead.tabuddy.desktop.logic.payload.{ ElementTemplate, Enumeration, Payload, PropertyType, TemplateProperty, TemplatePropertyGroup }
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.element.Element
import org.digimead.tabuddy.model.element.Value
import org.digimead.tabuddy.model.graph.Graph
import org.eclipse.core.databinding.observable.{ ChangeEvent, IChangeListener }
import org.eclipse.jface.databinding.swt.WidgetProperties
import org.eclipse.jface.databinding.viewers.ObservableListContentProvider
import org.eclipse.jface.dialogs.IDialogConstants
import org.eclipse.jface.viewers.StructuredSelection
import org.eclipse.swt.SWT
import org.eclipse.swt.events.{ DisposeEvent, DisposeListener, FocusEvent, FocusListener, PaintEvent, PaintListener, ShellAdapter, ShellEvent }
import org.eclipse.swt.graphics.{ GC, Point }
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.widgets.{ Composite, Control, Label, Shell }
import org.eclipse.ui.forms.events.{ ExpansionAdapter, ExpansionEvent }
import org.eclipse.ui.forms.widgets.{ ExpandableComposite, Section }

class ElementEditor @Inject() (
  /** This dialog context. */
  val context: Context,
  /** Parent shell. */
  val parentShell: Shell,
  /** Graph container. */
  val graph: Graph[_ <: Model.Like],
  /** Graph marker. */
  val marker: GraphMarker,
  /** Graph payload. */
  val payload: Payload,
  /** Exists element. */
  val element: Element,
  /** Element template. */
  val template: ElementTemplate,
  /** New element flag. */
  @Named(ElementEditor.newElementFlagId) val newElement: Boolean)
    extends ElementEditorSkel(parentShell) with Dialog with XLoggable {
  /** Activate context on focus. */
  val focusListener = new FocusListener() {
    def focusGained(e: FocusEvent) = context.activateBranch()
    def focusLost(e: FocusEvent) {}
  }
  /** The property representing the current element id. */
  val idField = WritableValue[String]
  /** Activate context on shell events. */
  val shellListener = new ShellAdapter() {
    override def shellActivated(e: ShellEvent) = context.activateBranch()
  }
  /** Element properties (property, control, editor). Available only from the UI thread. */
  // Sort by group priority
  lazy val properties: Seq[ElementEditor.PropertyItem[_ <: AnyRef with java.io.Serializable]] =
    template.properties.toSeq.sortBy(_._1.priority).map {
      case (group, properties) ⇒
        // Add group title to this form.
        val section = addGroupTitle(group)
        // Add group marker to this form.
        val marker = addGroupMarker(group, properties.size)
        // Transform api.TemplateProperty -> ElementEditor.PropertyItem.
        val propertySeq = properties.map { property ⇒
          // Add label for property to this form.
          val label = addLabel(property)
          // Add editor for property to this form.
          val propertyItem = addCellEditor(property)(Manifest.classType(property.ptype.typeClass))
          (label, propertyItem)
        }
        // Add Expand/Collapse logic
        section.addExpansionListener(new ExpansionAdapter() {
          val expanded = true
          override def expansionStateChanged(e: ExpansionEvent) = {
            if (e.getState() == expanded) {
              marker.setVisible(true)
              marker.getLayoutData().asInstanceOf[GridData].exclude = false
              propertySeq.foreach {
                case (label, ElementEditor.PropertyItem(initial, property, control, editor)) ⇒
                  label.setVisible(true)
                  label.getLayoutData().asInstanceOf[GridData].exclude = false
                  control.setVisible(true)
                  control.getLayoutData().asInstanceOf[GridData].exclude = false
              }
            } else {
              marker.setVisible(false)
              marker.getLayoutData().asInstanceOf[GridData].exclude = true
              propertySeq.foreach {
                case ((label, ElementEditor.PropertyItem(initial, property, control, editor))) ⇒
                  label.setVisible(false)
                  label.getLayoutData().asInstanceOf[GridData].exclude = true
                  control.setVisible(false)
                  control.getLayoutData().asInstanceOf[GridData].exclude = true
              }
            }
            getForm.reflow(true)
            adjustFormHeight()
          }
        })
        propertySeq.map(_._2)
    }.flatten
  /** Element properties listener. */
  val propertiesListener = new IChangeListener() { override def handleChange(event: ChangeEvent) = updateOK() }
  /** The final element. */
  protected var modifiedElement: Element = element

  /** Get modified element. */
  def getModifiedElement() = modifiedElement

  /** Add the group title to creation form */
  protected def addGroupTitle(group: TemplatePropertyGroup): Section = {
    val title = getToolkit.createSection(getForm.getBody(), ExpandableComposite.TWISTIE | ExpandableComposite.TITLE_BAR)
    val layoutData = new GridData(SWT.FILL, SWT.CENTER, true, false)
    layoutData.horizontalSpan = 3
    title.setText(group.id.name)
    title.setExpanded(true)
    title.setToolTipText(group.label)
    title.setLayoutData(layoutData)
    getToolkit().paintBordersFor(title)
    title
  }
  /** Add the property group marker */
  protected def addGroupMarker(group: TemplatePropertyGroup, size: Int): Composite = {
    val marker = getToolkit.createComposite(getForm.getBody(), SWT.NONE)
    val gc = new GC(marker)
    val fm = gc.getFontMetrics()
    val charWidth = fm.getAverageCharWidth()
    val markerLayoutData = new GridData(SWT.FILL, SWT.FILL, false, false)
    markerLayoutData.verticalSpan = size
    markerLayoutData.widthHint = charWidth * 2
    marker.setToolTipText(group.id.name + " - " + group.label)
    marker.setLayoutData(markerLayoutData)
    marker.addPaintListener(new PaintListener() {
      def paintControl(e: PaintEvent) {
        val clientArea = marker.getClientArea()
        e.gc.setForeground(ResourceManager.getColor(SWT.COLOR_BLUE))
        e.gc.fillGradientRectangle(0, 0, clientArea.width, clientArea.height, false)
      }
    })
    marker
  }
  /** Add the property id label*/
  protected def addLabel(property: TemplateProperty[_]): Label = {
    val label = getToolkit.createLabel(getForm.getBody(), property.id.name, SWT.NONE)
    val tooltip = if (property.required)
      Messages.acquire_text.format(property.ptype.typeSymbol)
    else
      Messages.activate_text.format(property.ptype.typeSymbol)
    label.setToolTipText(tooltip)
    label.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false))
    label
  }
  /** Add the property value field */
  protected def addCellEditor[T <: AnyRef with java.io.Serializable: Manifest](property: TemplateProperty[T]): ElementEditor.PropertyItem[_ <: AnyRef with java.io.Serializable] = {
    Model.assertNonGeneric[T]
    val initial = element.eGet[T](property.id).map(_.get) orElse property.defaultValue
    val editor = property.ptype.createEditor(initial, property.id, element)
    val control = property.enumeration.flatMap(id ⇒ payload.enumerations.get(id).find(enum ⇒ (if (enum.ptype == property.ptype) true else {
      log.error(s"Enumeration ${id} has incompatible type ${enum.ptype} vs ${property.ptype}.")
      false
    })).asInstanceOf[Option[Enumeration[T]]]) match {
      case Some(enumeration) ⇒
        val comboViewer = editor.asEditor[PropertyType.genericEditor].createCControl(getToolkit, getForm.getBody(), SWT.READ_ONLY)
        comboViewer.getCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false))
        comboViewer.setLabelProvider(property.ptype.adapter.asAdapter[PropertyType.genericAdapter].createEnumerationLabelProvider)
        comboViewer.setContentProvider(new ObservableListContentProvider())
        comboViewer.setInput(WritableList(enumeration.constants.toList.sortBy(_.view)).underlying)
        comboViewer.setSelection(new StructuredSelection(enumeration.getConstantSafe(property)), true)
        comboViewer.getCombo()
      case None ⇒
        val control = editor.asEditor[PropertyType.genericEditor].createControl(getToolkit, getForm.getBody(), SWT.BORDER)
        control.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false))
        control.setBackground(getForm.getBackground())
        editor.asEditor[PropertyType.genericEditor].addValidator(control)
        control
    }
    // manually convert all fields to the common one
    ElementEditor.PropertyItem[AnyRef with java.io.Serializable](
      initial,
      property.asInstanceOf[TemplateProperty[AnyRef with java.io.Serializable]],
      control,
      editor.asEditor[PropertyType.genericEditor])
  }
  /** Adjust the form height to content if possible */
  protected def adjustFormHeight() {
    val formPredderedSize = getForm.computeSize(SWT.DEFAULT, SWT.DEFAULT, true)
    val formActualSize = getForm.getSize()
    if (formPredderedSize.y != formActualSize.y) {
      val currentShellSize = getShell.getSize
      val parentShellSize = parentShell.getSize()
      val newHeight = math.min(currentShellSize.y + (formPredderedSize.y - formActualSize.y), parentShellSize.y)
      if (currentShellSize.y <= parentShellSize.y && newHeight != currentShellSize.y)
        getShell.setSize(currentShellSize.x, newHeight)
    }
  }
  /** Create contents of the dialog. */
  @log(result = false)
  override protected def createDialogArea(parent: Composite): Control = {
    val container = super.createDialogArea(parent).asInstanceOf[Composite]
    context.set(classOf[Composite], parent)
    context.set(classOf[org.eclipse.jface.dialogs.Dialog], this)
    val shell = getShell
    shell.addShellListener(shellListener)
    shell.addFocusListener(focusListener)
    // Add the dispose listener
    shell.addDisposeListener(new DisposeListener {
      def widgetDisposed(e: DisposeEvent) {
        getShell().removeFocusListener(focusListener)
        getShell().removeShellListener(shellListener)
      }
    })
    val form = getForm
    // Handle the element name
    val ancestors = element.eAncestors.map(_.id.name).reverse
    val formTitle = if (ancestors.size > 3) "%s .../" + ancestors.takeRight(3).mkString("/") + "/%s" else "%s /" + ancestors.mkString("/") + "/%s"
    App.bindingContext.bindValue(WidgetProperties.text(SWT.Modify).observeDelayed(50, getTxtElementId), idField)
    val idFieldValidator = SymbolValidator(getTxtElementId, true) { (validator, event) ⇒
      if (!event.doit)
        validator.withDecoration(validator.showDecorationError(_))
      else
        validator.withDecoration(_.hide)
    }
    idField.addChangeListener { (id, event) ⇒
      val newId = id.trim
      if (newId.isEmpty())
        idFieldValidator.withDecoration(idFieldValidator.showDecorationRequired(_))
      else if (element.eParent.map(_.safeRead(_.exists(_.id.name == newId))).getOrElse(false) && newId != element.eId.name)
        idFieldValidator.withDecoration(idFieldValidator.showDecorationError(_, Messages.identificatorIsAlreadyInUse_text.format(newId)))
      else
        idFieldValidator.withDecoration(_.hide)
      form.setText(formTitle.format(template.id.name, idField.value))
      updateOK()
    }
    idField.value = element.eId.name
    // Invoke updateOk on every 'editor.data' modification of any property.
    properties.foreach {
      case ElementEditor.PropertyItem(initial, property, control, editor) ⇒
        editor.data.underlying.addChangeListener(propertiesListener)
    }
    // Add the dispose listener
    getShell().addDisposeListener(new DisposeListener {
      def widgetDisposed(e: DisposeEvent) {
        properties.foreach {
          case ElementEditor.PropertyItem(initial, property, control, editor) ⇒
            editor.data.underlying.removeChangeListener(propertiesListener)
        }
      }
    })
    // set dialog message
    setMessage(Messages.elementEditorDescription_text.format(element.eModel.eId.name))
    // set dialog window title
    getShell().setText(Messages.elementEditorDialog_text.format(element.eId.name))
    container
  }
  /** Return the initial size of the dialog. */
  override protected def getInitialSize(): Point = {
    val default = super.getInitialSize
    val packedShellSize = getShell.computeSize(SWT.DEFAULT, SWT.DEFAULT, true)
    val adjustedHeight = math.min(default.y, packedShellSize.y)
    new Point(default.x, adjustedHeight)
  }
  /** Notifies that the OK button of this dialog has been pressed.	 */
  override protected def okPressed() {
    val newId = idField.value.trim
    if (newId != this.element.eId.name) {
      val newNode = this.element.eNode.copy(id = Symbol(newId))
      modifiedElement = newNode.projection(this.element.eCoordinate).e
    }
    properties.foreach {
      case ElementEditor.PropertyItem(initialValue, property, control, editor) ⇒
        if (editor.isEmpty) {
          if (modifiedElement.eGet(property.id, property.ptype.typeSymbol).nonEmpty)
            modifiedElement = modifiedElement.eRemove(property.id, property.ptype.typeSymbol)
        } else {
          val previousValue = modifiedElement.eGet(property.id, property.ptype.typeSymbol)
          if (previousValue.map(_.get) != Option(editor.data.value)) {
            val value = Value.static(editor.data.value)(Manifest.classType(property.ptype.typeClass))
            modifiedElement = modifiedElement.eSet(property.id, property.ptype.typeSymbol, Some(value))
          }
        }
    }
    super.okPressed()
  }
  /** On dialog active */
  override protected def onActive = {
    // persist the 1st column width
    getLblElementId.getLayoutData.asInstanceOf[GridData].widthHint = getLblElementId.getSize.x
    getForm.getBody.layout()
    // adjust height
    adjustFormHeight()
    updateOK
  }
  /** Update OK button */
  protected def updateOK() = Option(getButton(IDialogConstants.OK_ID)).foreach(_.setEnabled({
    val newId = idField.value.trim
    newId.nonEmpty && {
      // Not exists and id is the same
      (newElement && newId == element.eId.name) ||
        // Id is modified
        (!element.eParent.map(_.safeRead(_.exists(_.id.name == newId))).getOrElse(false)) ||
        // The content is modified
        !properties.forall {
          case ElementEditor.PropertyItem(initial, property, control, editor) ⇒
            initial == Option(editor.data.value) || (initial.isEmpty && editor.isEmpty)
        }
    }
  }))
}

object ElementEditor {
  /** Flag for dialog context . */
  final val newElementFlagId = "ElementEditor.newElementFlag"

  case class PropertyItem[T <: AnyRef with java.io.Serializable](
    val initial: Option[T],
    val property: TemplateProperty[T],
    val control: Control,
    val editor: payload.PropertyType.Editor[T])
}
