/**
 * This file is part of the TA Buddy project.
 * Copyright (c) 2012-2014 Alexey Aksenov ezh@ezh.msk.ru
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

package org.digimead.tabuddy.desktop.logic.ui.preference

import org.digimead.tabuddy.desktop.core.Preferences
import org.digimead.tabuddy.desktop.core.definition.IPreferencePage
import org.eclipse.jface.preference.{ FieldEditor ⇒ JFieldEditor, FieldEditorPreferencePage, IPreferenceStore, PreferenceManager }
import org.eclipse.jface.viewers.ListViewer
import org.eclipse.swt.SWT
import org.eclipse.swt.layout.{ FillLayout, GridData }
import org.eclipse.swt.widgets.{ Button, Composite }
import org.digimead.tabuddy.desktop.logic.payload.marker.serialization.signature.Validator
import org.eclipse.jface.preference.PreferenceStore
import org.eclipse.jface.viewers.LabelProvider
import org.digimead.tabuddy.desktop.logic.payload.marker.serialization.signature.api.XValidator
import org.eclipse.jface.viewers.TableViewer
import org.eclipse.jface.viewers.TableViewerColumn
import org.eclipse.jface.viewers.ColumnLabelProvider
import org.eclipse.ui.PlatformUI
import org.eclipse.ui.ISharedImages
import org.eclipse.jface.layout.TableColumnLayout
import org.eclipse.jface.viewers.ColumnWeightData
import org.eclipse.jface.viewers.ArrayContentProvider
import scala.collection.mutable.ArrayBuffer
import java.util.ArrayList
import org.digimead.tabuddy.desktop.core.ui.UI
import org.digimead.tabuddy.desktop.logic.Default
import org.eclipse.jface.viewers.DelegatingStyledCellLabelProvider.IStyledLabelProvider
import org.eclipse.jface.viewers.StyledString
import org.eclipse.jface.viewers.DecoratingStyledCellLabelProvider
import org.eclipse.jface.viewers.LabelDecorator
import org.eclipse.swt.graphics.Image
import org.eclipse.jface.viewers.IDecorationContext
import org.eclipse.jface.viewers.DecorationOverlayIcon
import org.eclipse.jface.viewers.IDecoration
import org.eclipse.jface.viewers.ILabelProviderListener
import org.eclipse.ui.internal.WorkbenchImages
import org.eclipse.ui.internal.IWorkbenchGraphicConstants
import org.eclipse.ui.internal.WorkbenchPlugin
import org.eclipse.jface.viewers.ColumnViewerToolTipSupport
import org.eclipse.jface.viewers.ILabelDecorator
import org.eclipse.swt.graphics.Color
import org.eclipse.jface.viewers.CellLabelProvider
import org.eclipse.swt.graphics.Font
import org.eclipse.swt.graphics.Point
import org.eclipse.swt.layout.RowLayout
import org.eclipse.swt.widgets.Text
import org.eclipse.swt.widgets.Event
import org.eclipse.jface.viewers.ViewerCell
import org.eclipse.jface.viewers.ColumnViewer
import org.eclipse.jface.window.ToolTip

/**
 * Signature validator preference page.
 */
class SignatureValidator extends FieldEditorPreferencePage with IPreferencePage {
  protected val pagePreferenceStore = new PreferenceStore

  def createFieldEditors() {
    setPreferenceStore(pagePreferenceStore)
    addField(new SignatureValidator.FieldEditor("SignatureValidator", "Signature validator:", getFieldEditorParent()))
  }
  /** Register this page in a preference manager. */
  def register(pmgr: PreferenceManager) =
    pmgr.addToRoot(Preferences.Node("SignatureValidator", "Signature validator", None)(() ⇒ new SignatureValidator))
}

object SignatureValidator {
  /**
   * Signature validator field editor.
   */
  class FieldEditor(name: String, labelText: String, parent: Composite) extends {
    /** Add button control. */
    protected var addButton = Option.empty[Button]
    /** Table viewer control. */
    protected var tableViewer = Option.empty[TableViewer]
    /** Remove button control. */
    protected var removeButton = Option.empty[Button]
  } with JFieldEditor(name, labelText, parent) {
    def adjustForNumColumns(numColumns: Int) = for {
      tableViewer ← tableViewer
    } {
      val gd = tableViewer.getControl().getLayoutData().asInstanceOf[GridData]
      if (numColumns > 2)
        gd.horizontalSpan = numColumns - 2
      else
        gd.horizontalSpan = 1
      // We only grab excess space if we have to
      // If another field editor has more columns then
      // we assume it is setting the width.
      gd.grabExcessHorizontalSpace = gd.horizontalSpan == 1
    }
    def doFillIntoGrid(parent: Composite, numColumns: Int) {
      val table = getTableControl(parent)
      val add = getButtons(parent)
      adjustForNumColumns(numColumns)
    }
    def doLoad() = for {
      tableViewer ← tableViewer
      column ← tableViewer.getTable().getColumns().headOption
      model = tableViewer.getInput().asInstanceOf[ArrayList[XValidator]]
    } {
      Validator.validators().toSeq.sortBy(_.name.name).foreach(model.add)
      tableViewer.refresh()
      if (tableViewer.getTable().getBounds().width > 0)
        UI.adjustViewerColumnWidth(tableViewer.getTable(), column, Default.columnPadding)
      else
        column.pack()
    }
    def doLoadDefault() = for {
      tableViewer ← tableViewer
      model = tableViewer.getInput().asInstanceOf[ArrayList[XValidator]]
    } Validator.validators().toSeq.sortBy(_.name.name).foreach(model.add)
    def doStore() {

    }
    def getNumberOfControls() = 2

    protected def getTableControl(parent: Composite): TableViewer = tableViewer getOrElse {
      val viewer = new TableViewer(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL)
      val viewerColumn = new TableViewerColumn(viewer, SWT.NONE)
      viewerColumn.setLabelProvider(new ViewDecoratingStyledCellLabelProvider(new ViewLabelProvider(), new ViewLabelDecorator(), null))
      viewer.setContentProvider(ArrayContentProvider.getInstance())
      viewer.setInput(new ArrayList[XValidator]())
      ViewColumnViewerToolTipSupport.enableFor(viewer)
      viewer.getControl().setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, false, 1, 1))
      this.tableViewer = Option(viewer)
      viewer
    }
    protected def getButtons(parent: Composite): (Button, Button) = {
      for {
        addButton ← addButton
        removeButton ← removeButton
      } yield (addButton, removeButton)
    } getOrElse {
      val container = new Composite(parent, SWT.NONE)
      container.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false, 1, 1))
      val fillLayout = new FillLayout()
      fillLayout.`type` = SWT.VERTICAL
      container.setLayout(fillLayout)
      val addButton = new Button(container, SWT.NONE)
      addButton.setText("Add")
      val removeButton = new Button(container, SWT.NONE)
      removeButton.setText("Remove")
      this.addButton = Option(addButton)
      this.removeButton = Option(removeButton)
      (addButton, removeButton)
    }
  }
  class ViewLabelProvider extends ColumnLabelProvider with IStyledLabelProvider {
    override def getStyledText(element: AnyRef): StyledString = new StyledString(getText(element))
    override def getText(element: AnyRef) = element match {
      case validator: XValidator ⇒ s"${validator.name.name.capitalize} - ${validator.description.capitalize}"
      case unknown ⇒ super.getText(unknown)
    }
    override def getImage(obj: Object) = PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_OBJ_ELEMENT)
    override def getToolTipText(element: AnyRef): String = getText(element) + ", shown in a tooltip"
  }
  class ViewLabelDecorator extends LabelDecorator {
    private val warningImageDescriptor = WorkbenchPlugin.getDefault().getSharedImages().getImageDescriptor(ISharedImages.IMG_TOOL_BACK_DISABLED)
    //WorkbenchImages.getImageDescriptor(IWorkbenchGraphicConstants.IMG_LCL_PIN_VIEW)
    private var decoratedImage: Image = null

    override def decorateImage(image: Image, element: AnyRef, context: IDecorationContext): Image = element match {
      case element: XValidator ⇒
        if (decoratedImage == null) {
          decoratedImage = (new DecorationOverlayIcon(image, warningImageDescriptor, IDecoration.BOTTOM_RIGHT)).createImage()
        }
        return decoratedImage;
      case other ⇒ null
    }
    override def dispose() {
      decoratedImage.dispose()
      decoratedImage = null
    }
    override def decorateText(text: String, element: AnyRef, context: IDecorationContext): String = null
    override def prepareDecoration(element: AnyRef, originalText: String, context: IDecorationContext) = false
    override def decorateImage(image: Image, element: AnyRef): Image = null
    override def decorateText(text: String, element: AnyRef): String = null
    override def addListener(listener: ILabelProviderListener) {}
    override def isLabelProperty(element: AnyRef, property: String): Boolean = false
    override def removeListener(listener: ILabelProviderListener) {}
  }
  class ViewDecoratingStyledCellLabelProvider(labelProvider: IStyledLabelProvider, decorator: ILabelDecorator, decorationContext: IDecorationContext)
    extends DecoratingStyledCellLabelProvider(labelProvider, decorator, decorationContext) {

    override def getToolTipBackgroundColor(obj: AnyRef): Color = labelProvider match {
      case labelProvider: CellLabelProvider ⇒ labelProvider.getToolTipBackgroundColor(obj)
      case _ ⇒ super.getToolTipBackgroundColor(obj)
    }
    override def getToolTipDisplayDelayTime(obj: AnyRef): Int = labelProvider match {
      case labelProvider: CellLabelProvider ⇒ labelProvider.getToolTipDisplayDelayTime(obj)
      case _ ⇒ super.getToolTipDisplayDelayTime(obj)
    }
    override def getToolTipFont(obj: AnyRef): Font = labelProvider match {
      case labelProvider: CellLabelProvider ⇒ labelProvider.getToolTipFont(obj)
      case _ ⇒ super.getToolTipFont(obj)
    }
    override def getToolTipForegroundColor(obj: AnyRef): Color = labelProvider match {
      case labelProvider: CellLabelProvider ⇒ labelProvider.getToolTipForegroundColor(obj)
      case _ ⇒ super.getToolTipForegroundColor(obj)
    }
    override def getToolTipImage(obj: AnyRef): Image = labelProvider match {
      case labelProvider: CellLabelProvider ⇒ labelProvider.getToolTipImage(obj)
      case _ ⇒ super.getToolTipImage(obj)
    }
    override def getToolTipShift(obj: AnyRef): Point = labelProvider match {
      case labelProvider: CellLabelProvider ⇒ labelProvider.getToolTipShift(obj)
      case _ ⇒ super.getToolTipShift(obj)
    }
    override def getToolTipStyle(obj: AnyRef): Int = labelProvider match {
      case labelProvider: CellLabelProvider ⇒ labelProvider.getToolTipStyle(obj)
      case _ ⇒ super.getToolTipStyle(obj)
    }
    override def getToolTipText(obj: AnyRef): String = labelProvider match {
      case labelProvider: CellLabelProvider ⇒ labelProvider.getToolTipText(obj)
      case _ ⇒ super.getToolTipText(obj)
    }
    override def getToolTipTimeDisplayed(obj: AnyRef): Int = labelProvider match {
      case labelProvider: CellLabelProvider ⇒ labelProvider.getToolTipTimeDisplayed(obj)
      case _ ⇒ super.getToolTipTimeDisplayed(obj)
    }
    override def useNativeToolTip(obj: AnyRef): Boolean = labelProvider match {
      case labelProvider: CellLabelProvider ⇒ labelProvider.useNativeToolTip(obj)
      case _ ⇒ super.useNativeToolTip(obj)
    }
  }
  class ViewColumnViewerToolTipSupport(viewer: ColumnViewer, style: Int, manualActivation: Boolean) extends ColumnViewerToolTipSupport(viewer, style, manualActivation) {

    override protected def createViewerToolTipContentArea(event: Event, cell: ViewerCell, parent: Composite): Composite = {
      val composite = new Composite(parent, SWT.NONE);
      composite.setLayout(new RowLayout(SWT.VERTICAL));
      val text = new Text(composite, SWT.SINGLE);
      text.setText(getText(event));
      text.setSize(100, 60);
      val calendar = new Button(composite, SWT.CALENDAR);
      calendar.setText("111")
      calendar.setEnabled(false);
      calendar.setSize(100, 100);
      composite.pack();
      return composite;
    }

  }
  object ViewColumnViewerToolTipSupport {
    def enableFor(viewer: ColumnViewer) {
      new ViewColumnViewerToolTipSupport(viewer, ToolTip.NO_RECREATE, false);
    }
  }
}
