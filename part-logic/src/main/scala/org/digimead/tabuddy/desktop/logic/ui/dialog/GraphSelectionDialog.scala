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

package org.digimead.tabuddy.desktop.logic.ui.dialog

import com.cathive.fonts.fontawesome.FontAwesome
import java.lang.reflect.{ Field, Modifier }
import java.util.Date
import java.util.concurrent.locks.ReentrantLock
import java.util.regex.Pattern
import javafx.beans.value.{ ChangeListener, ObservableValue }
import javafx.geometry.VPos
import javafx.scene.Scene
import javafx.scene.effect.DropShadow
import javafx.scene.image.ImageView
import javafx.scene.layout.Pane
import javafx.scene.paint.Color
import javafx.scene.text.{ Font, Text, TextAlignment, TextBuilder }
import javafx.scene.transform.Scale
import javax.inject.Inject
import org.digimead.digi.lib.jfx4swt.{ FXCanvas, JFX }
import org.digimead.digi.lib.jfx4swt.util.JFXUtil
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.Report
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.ui.{ ResourceManager, Resources }
import org.digimead.tabuddy.desktop.core.ui.UI
import org.digimead.tabuddy.desktop.core.ui.definition.Dialog
import org.digimead.tabuddy.desktop.logic.Default
import org.digimead.tabuddy.desktop.logic.payload.maker.GraphMarker
import org.eclipse.core.databinding.observable.{ ChangeEvent, IChangeListener }
import org.eclipse.core.databinding.observable.value.DecoratingObservableValue
import org.eclipse.e4.core.contexts.IEclipseContext
import org.eclipse.jface.action.{ Action, MenuManager }
import org.eclipse.jface.databinding.swt.WidgetProperties
import org.eclipse.jface.dialogs.IDialogConstants
import org.eclipse.jface.viewers.{ ArrayContentProvider, ColumnLabelProvider, ColumnViewerToolTipSupport, ISelectionChangedListener, IStructuredSelection, LabelProvider, SelectionChangedEvent, StructuredSelection, TableViewer, Viewer, ViewerComparator, ViewerFilter }
import org.eclipse.nebula.widgets.gallery.{ DefaultGalleryItemRenderer, GalleryItem, NoGroupRenderer }
import org.eclipse.swt.SWT
import org.eclipse.swt.custom.StackLayout
import org.eclipse.swt.events.{ DisposeEvent, DisposeListener, FocusEvent, FocusListener, PaintEvent, PaintListener, SelectionAdapter, SelectionEvent, ShellAdapter, ShellEvent }
import org.eclipse.swt.graphics.Image
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.widgets.{ Composite, Control, Event, Listener, Sash, Shell }
import scala.concurrent.Future
import scala.ref.WeakReference

/**
 * Graph selection dialog.
 */
class GraphSelectionDialog @Inject() (
  /** This dialog context. */
  val context: IEclipseContext,
  /** Graph markers. */
  val markers: Array[GraphMarker],
  /** Parent shell. */
  val parentShell: Shell) extends GraphSelectionDialogSkel(parentShell) with Dialog with Loggable {
  /** Akka execution context. */
  implicit lazy val ec = App.system.dispatcher
  /** The auto resize lock. */
  protected val autoResizeLock = new ReentrantLock()
  /** Graph image preview. */
  lazy val canvasPreview = new FXCanvas(getCompositePreviewContent, SWT.BORDER)
  /** Graph image grid data. */
  val canvasData = new GridData(SWT.CENTER, SWT.TOP, true, false, 2, 1)
  /** Filter pattern value. */
  var filterPattern = Pattern.compile(".*")
  /** Filter type value. */
  var filterType = 0
  /** Gallery group renderer. */
  lazy val galleryGroupRenderer = new NoGroupRenderer()
  /** Awesome font for this dialog. */
  lazy val font = Font.loadFont(classOf[FontAwesome].getResource("FontAwesome.ttf").toExternalForm(), 100)
  /** Selected marker. */
  @volatile protected var marker = Option.empty[GraphMarker]
  /** Actual sort direction */
  @volatile protected var sortDirection = Default.sortingDirection
  /** Actual sortBy column index */
  @volatile protected var sortColumn = 1
  /** Default graph icon. */
  lazy val text = UI.<>[TextBuilder[_], Text](TextBuilder.create()) { b ⇒
    b.text(FontAwesome.ICON_FILE.toString())
    b.fill(Color.GRAY)
    b.font(font)
    b.textAlignment(TextAlignment.CENTER)
    b.textOrigin(VPos.TOP)
    b.build()
  }
  /** Default graph icon bounds. */
  lazy val textBounds = JFXUtil.getCroppedBounds(textImage, 0.01)
  /** Default graph icon image. */
  lazy val textImage = JFXUtil.takeSnapshot(text)

  /** Get selected marker. */
  def getMarker(): Option[GraphMarker] = marker

  /** Auto resize tableviewer columns */
  protected def autoresize() = if (autoResizeLock.tryLock()) try {
    Thread.sleep(50)
    App.execNGet {
      if (!getTableViewer.getTable.isDisposed()) {
        UI.adjustTableViewerColumnWidth(getTableViewerColumnName, Default.columnPadding)
        UI.adjustTableViewerColumnWidth(getTableViewerColumnOrigin, Default.columnPadding)
        UI.adjustTableViewerColumnWidth(getTableViewerColumnCreated, Default.columnPadding)
        getTableViewer.refresh()
      }
    }
  } finally {
    autoResizeLock.unlock()
  }
  /** Configure buttons. */
  protected def configureButtons() {
    // btnResetFilter
    val btnResetFilter = getBtnResetFilter()
    val btnResetFilterSize = btnResetFilter.computeSize(SWT.DEFAULT, SWT.DEFAULT)
    val btnResetFilterDimension = math.max(btnResetFilterSize.x, btnResetFilterSize.y)
    val btnResetFilterLayoutData = new GridData()
    btnResetFilterLayoutData.minimumHeight = btnResetFilterDimension
    btnResetFilterLayoutData.minimumWidth = btnResetFilterDimension
    btnResetFilterLayoutData.heightHint = btnResetFilterDimension
    btnResetFilterLayoutData.widthHint = btnResetFilterDimension
    btnResetFilter.setLayoutData(btnResetFilterLayoutData)
    btnResetFilter.addSelectionListener(new SelectionAdapter() {
      override def widgetSelected(event: SelectionEvent) {
        getTextFilter().setText("")
        filterPattern = Pattern.compile(".*")
        getTableViewer().refresh()
        if (getTableContainer().getLayout().asInstanceOf[StackLayout].topControl == getTableGallery())
          updateGallery()
      }
    })
    // btnToggleMode
    val btnToggleMode = getBtnToggleMode()
    val btnToggleModeSize = btnToggleMode.computeSize(SWT.DEFAULT, SWT.DEFAULT)
    val btnToggleModeDimension = math.max(btnToggleModeSize.x, btnToggleModeSize.y)
    val btnToggleModeLayoutData = new GridData()
    btnToggleModeLayoutData.minimumHeight = btnToggleModeDimension
    btnToggleModeLayoutData.minimumWidth = btnToggleModeDimension
    btnToggleModeLayoutData.heightHint = btnToggleModeDimension
    btnToggleModeLayoutData.widthHint = btnToggleModeDimension
    btnToggleMode.setLayoutData(btnToggleModeLayoutData)
    btnToggleMode.addSelectionListener(new SelectionAdapter() {
      override def widgetSelected(event: SelectionEvent) {
        val table = getTableViewer().getTable()
        if (getTableContainer().getLayout().asInstanceOf[StackLayout].topControl == table) {
          getTableContainer().getLayout().asInstanceOf[StackLayout].topControl = getTableGallery()
          updateGallery()
        } else {
          getTableContainer().getLayout().asInstanceOf[StackLayout].topControl = table
        }
        getTableContainer().layout()
      }
    })
    // ok
    val ok = getButton(IDialogConstants.OK_ID)
    // cancel
    val cancel = getButton(IDialogConstants.CANCEL_ID)

    getCompositeSelect.layout(Array[Control](btnResetFilter, btnToggleMode))
    getCompositeSelect.layout(true, true)
  }
  /** Configure filter. */
  protected def configureFilter() {
    val textFilter = getTextFilter()
    WidgetProperties.text(SWT.Modify).observeDelayed(50, textFilter).addChangeListener(new IChangeListener() {
      override def handleChange(event: ChangeEvent) = {
        val parts = event.getObservable.asInstanceOf[DecoratingObservableValue].getValue().asInstanceOf[String].split("""\*""")
        filterPattern = Pattern.compile(("" +: parts.map(Pattern.quote) :+ "").mkString(".*"))
        getTableViewer().refresh()
        if (getTableContainer().getLayout().asInstanceOf[StackLayout].topControl == getTableGallery())
          updateGallery()
      }
    })
    val comboViewerFilter = getComboViewerFilter()
    comboViewerFilter.setContentProvider(ArrayContentProvider.getInstance())
    comboViewerFilter.setLabelProvider(new LabelProvider)
    comboViewerFilter.setInput(GraphSelectionDialog.comboFilterArray)
    comboViewerFilter.addSelectionChangedListener(new ISelectionChangedListener() {
      override def selectionChanged(event: SelectionChangedEvent) = event.getSelection() match {
        case selection: IStructuredSelection if !selection.isEmpty() ⇒
          filterType = GraphSelectionDialog.comboFilterArray.indexOf(selection.getFirstElement())
          getTableViewer().refresh()
          if (getTableContainer().getLayout().asInstanceOf[StackLayout].topControl == getTableGallery())
            updateGallery()
        case selection ⇒
      }
    })
    comboViewerFilter.setSelection(new StructuredSelection(GraphSelectionDialog.comboFilterArray.head))
  }
  /** Configure gallery table. */
  protected def configureGallery() {
    val gallery = getTableGallery()
    try {
      // Fix outdated shit(private final field for TEMPORARY workaround) in Nebula Gallery :-(
      val fixedIn2007 = gallery.getClass().getDeclaredField("BUG_PLATFORM_LINUX_GTK_174932")
      if (!fixedIn2007.isAccessible())
        fixedIn2007.setAccessible(true)
      val modifiersField = classOf[Field].getDeclaredField("modifiers")
      modifiersField.setAccessible(true)
      modifiersField.setInt(fixedIn2007, fixedIn2007.getModifiers() & ~Modifier.FINAL)
      fixedIn2007.set(null, "")
    } catch { case e: Throwable ⇒ }
    gallery.addSelectionListener(new SelectionAdapter() {
      override def widgetSelected(event: SelectionEvent) = event.item match {
        case item: GalleryItem ⇒
          val marker = item.getData().asInstanceOf[GraphMarker]
          GraphSelectionDialog.this.marker = Some(marker)
          updateCompositePreviewContent()
          getCompositePreview().setMinSize(getCompositePreviewContent().computeSize(SWT.DEFAULT, SWT.DEFAULT))
          getCompositePreview().setContent(getCompositePreviewContent())
          Option(getButton(IDialogConstants.OK_ID)).foreach(_.setEnabled(true))
        case _ ⇒
          GraphSelectionDialog.this.marker = None
          getCompositePreview().setMinSize(getCompositePreviewNone.computeSize(SWT.DEFAULT, SWT.DEFAULT))
          getCompositePreview().setContent(getCompositePreviewNone())
          Option(getButton(IDialogConstants.OK_ID)).foreach(_.setEnabled(false))
      }
    })
    galleryGroupRenderer.setMinMargin(2)
    galleryGroupRenderer.setItemHeight(100)
    galleryGroupRenderer.setItemWidth(100)
    galleryGroupRenderer.setAutoMargin(true)
    gallery.setGroupRenderer(galleryGroupRenderer)
    gallery.setItemRenderer(new DefaultGalleryItemRenderer())
    val manager = new MenuManager("#PopupMenu")
    manager.add(GalleryZoomInAction)
    manager.add(GalleryZoomOutAction)
    gallery.setMenu(manager.createContextMenu(gallery))
    new GalleryItem(getTableGallery(), SWT.NONE)
  }
  /** Configure sash form. */
  protected def configureSashForm() {
    val sashForm = getSashForm()
    // Make size different from zero for layout.
    sashForm.setSize(UI.DEFAULT_WIDTH, UI.DEFAULT_HEIGHT)
    // Create sash.
    sashForm.layout()
    // Get sash(s) from sashform and adjust.
    sashForm.getChildren().filter(_.isInstanceOf[Sash]).foreach { sash ⇒
      sash.addPaintListener(SashPaintListener)
      sash.redraw()
    }
    val sashBackground = JFXUtil.fromRGB(sashForm.getBackground().getRGB())
    if (JFXUtil.isDark(sashBackground))
      SashPaintListener.sashColor = ResourceManager.getColor(JFXUtil.toRGB(sashBackground.brighter()))
    else
      SashPaintListener.sashColor = ResourceManager.getColor(JFXUtil.toRGB(sashBackground.darker()))
  }
  /** Configure table viewer. */
  protected def configureTableViewer() {
    val tableViewer = getTableViewer()
    val tableViewerColumnName = getTableViewerColumnName()
    val tableViewerColumnOrigin = getTableViewerColumnOrigin()
    val tableViewerColumnCreated = getTableViewerColumnCreated()
    val tableViewerColumnModified = getTableViewerColumnModified()

    tableViewerColumnName.setLabelProvider(GraphSelectionDialog.NameLabelProvider)
    tableViewerColumnName.getColumn.addSelectionListener(new GraphSelectionDialog.ViewSelectionAdapter(WeakReference(tableViewer), 0))
    tableViewerColumnOrigin.setLabelProvider(GraphSelectionDialog.OriginLabelProvider)
    tableViewerColumnOrigin.getColumn.addSelectionListener(new GraphSelectionDialog.ViewSelectionAdapter(WeakReference(tableViewer), 1))
    tableViewerColumnCreated.setLabelProvider(GraphSelectionDialog.CreatedAtLabelProvider)
    tableViewerColumnCreated.getColumn.addSelectionListener(new GraphSelectionDialog.ViewSelectionAdapter(WeakReference(tableViewer), 2))
    tableViewerColumnModified.setLabelProvider(GraphSelectionDialog.ModifiedLabelProvider)
    tableViewerColumnModified.getColumn.addSelectionListener(new GraphSelectionDialog.ViewSelectionAdapter(WeakReference(tableViewer), 3))

    tableViewer.setComparator(new GraphSelectionDialog.ViewComparator(WeakReference(this)))
    tableViewer.addFilter(TableViewerFilter)
    tableViewer.setContentProvider(ArrayContentProvider.getInstance())

    // Activate the tooltip support for the viewer
    ColumnViewerToolTipSupport.enableFor(tableViewer)

    // Add the selection listener
    tableViewer.addSelectionChangedListener(new ISelectionChangedListener() {
      override def selectionChanged(event: SelectionChangedEvent) = event.getSelection() match {
        case selection: IStructuredSelection if !selection.isEmpty() ⇒
          val marker = selection.getFirstElement().asInstanceOf[GraphMarker]
          GraphSelectionDialog.this.marker = Some(marker)
          updateCompositePreviewContent()
          getCompositePreview().setMinSize(getCompositePreviewContent().computeSize(SWT.DEFAULT, SWT.DEFAULT))
          getCompositePreview().setContent(getCompositePreviewContent())
          Option(getButton(IDialogConstants.OK_ID)).foreach(_.setEnabled(true))
        case selection ⇒
          GraphSelectionDialog.this.marker = None
          getCompositePreview().setMinSize(getCompositePreviewNone.computeSize(SWT.DEFAULT, SWT.DEFAULT))
          getCompositePreview().setContent(getCompositePreviewNone())
          Option(getButton(IDialogConstants.OK_ID)).foreach(_.setEnabled(false))
      }
    })

    tableViewer.setInput(markers)
    tableViewer.getTable().setFocus()
  }
  /** Create contents of the button bar. */
  override protected def createButtonsForButtonBar(parent: Composite) {
    super.createButtonsForButtonBar(parent)

    configureButtons()

    JFX.exec { initializeJFX() }
  }
  /** Create contents of the dialog. */
  override protected def createDialogArea(parent: Composite): Control = {
    val result = super.createDialogArea(parent)
    context.set(classOf[Composite], parent)

    configureFilter()
    configureGallery()
    configureSashForm()
    configureTableViewer()

    getTableContainer().getLayout().asInstanceOf[StackLayout].topControl = getTableViewer().getTable()

    canvasData.minimumHeight = UI.DEFAULT_HEIGHT
    canvasData.heightHint = UI.DEFAULT_HEIGHT
    canvasData.minimumWidth = UI.DEFAULT_WIDTH
    canvasData.widthHint = UI.DEFAULT_WIDTH
    canvasPreview.setLayoutData(canvasData)
    canvasPreview.moveAbove(null)
    canvasPreview.setBackground(ResourceManager.getColor(SWT.COLOR_WHITE))
    getCompositePreviewContent.addListener(SWT.Resize, new Listener() {
      def handleEvent(e: Event) = e.widget match {
        case composite: Composite ⇒
          val currentSize = composite.getSize()
          canvasData.minimumHeight = UI.DEFAULT_HEIGHT
          canvasData.heightHint = UI.DEFAULT_HEIGHT
          canvasData.minimumWidth = UI.DEFAULT_WIDTH
          canvasData.widthHint = UI.DEFAULT_WIDTH
          val prefferedSize = composite.computeSize(SWT.DEFAULT, SWT.DEFAULT)
          // Total height - (prefferedSize.y - UI.DEFAULT_HEIGHT)
          val availableHeight = math.max((currentSize.y + UI.DEFAULT_HEIGHT - prefferedSize.y) / 5 * 3, UI.DEFAULT_HEIGHT)
          val availableWidth = math.max(currentSize.x / 5 * 3, UI.DEFAULT_WIDTH)
          val size = math.min(availableHeight, availableWidth)
          canvasData.minimumHeight = size
          canvasData.heightHint = size
          canvasData.minimumWidth = size
          canvasData.widthHint = size
          composite.layout(true, true)
      }
    })

    getShell().addShellListener(ShellContextActivator)
    getShell().addFocusListener(FocusContextActivator)
    // Add the dispose listener
    getShell().addDisposeListener(new DisposeListener {
      def widgetDisposed(e: DisposeEvent) {
        getSashForm().removePaintListener(SashPaintListener)
        getShell().removeFocusListener(FocusContextActivator)
        getShell().removeShellListener(ShellContextActivator)
      }
    })
    result
  }
  /** Create preview scene. */
  protected def createPreview(): Scene = {
    val ds = new DropShadow()
    ds.setOffsetY(3.0f)
    ds.setColor(Color.color(0.4f, 0.4f, 0.4f))
    text.setEffect(ds)

    val scaleTransform = new Scale()
    text.getTransforms().addAll(scaleTransform)

    val content = new Pane()
    content.getChildren().addAll(text)
    val scene = new Scene(content)

    scene.widthProperty().addListener(new ChangeListener[Number]() {
      override def changed(observable: ObservableValue[_ <: Number], oldSceneWidth: Number, sceneWidth: Number) {
        val k = sceneWidth.doubleValue() * 0.7 / textBounds.getWidth()
        val width = textBounds.getMaxX() * k
        val height = textBounds.getMaxY() * k
        val hShift = textBounds.getMinX() * k
        val vShift = textBounds.getMinY() * k
        scaleTransform.setX(k)
        scaleTransform.setY(k)
        text.setLayoutX((sceneWidth.doubleValue() - width - hShift) / 2)
        text.setLayoutY((sceneWidth.doubleValue() - height - vShift) / 2) // sceneWidth == sceneHeight
      }
    })

    scene
  }

  /** Initialize Java FX content. */
  protected def initializeJFX() {
    textBounds // initialize textBounds
    val previewScene = createPreview()
    canvasPreview.setScene(previewScene)
  }
  /** On dialog active */
  override protected def onActive() = {
    //   updateOK()
    Future { autoresize() } onFailure {
      case e: Exception ⇒ log.error(e.getMessage(), e)
      case e ⇒ log.error(e.toString())
    }
  }
  /** Update composite content. */
  protected def updateCompositePreviewContent() = marker.foreach { marker ⇒
    getLblNameValue.setText(marker.graphModelId.name)
    getLblOriginValue().setText(marker.graphOrigin.name)
  }
  /** Update gallery table. */
  protected def updateGallery() {
    getTableGallery().removeAll()
    val defaultImageView = new ImageView()
    defaultImageView.setImage(textImage)
    defaultImageView.setFitWidth(galleryGroupRenderer.getItemWidth())
    defaultImageView.setPreserveRatio(true)
    val defaultImage = JFX.execNGet(JFXUtil.toImageData(JFXUtil.takeSnapshot(defaultImageView))).map(new Image(App.display, _))
    val galleryGroup = new GalleryItem(getTableGallery(), SWT.NONE)
    for {
      marker ← getTableViewer().getSortedChildren(getTableViewer().getInput())
      defaultImage ← defaultImage
    } {
      val item = new GalleryItem(galleryGroup, SWT.NONE)
      item.setData(marker)
      item.setImage(defaultImage)
      item.setText(marker.asInstanceOf[GraphMarker].graphModelId.name)
    }
    getTableGallery().redraw()
  }

  /** Activate context on focus. */
  object FocusContextActivator extends FocusListener() {
    def focusGained(e: FocusEvent) = context.activateBranch()
    def focusLost(e: FocusEvent) {}
  }
  /** Activate context on shell events. */
  object ShellContextActivator extends ShellAdapter() {
    override def shellActivated(e: ShellEvent) = context.activateBranch()
  }
  /** Sash delimiter painter. */
  object SashPaintListener extends PaintListener {
    val delimeterSize = Resources.convertHeightInCharsToPixels(1)
    var sashColor = ResourceManager.getColor(SWT.COLOR_WIDGET_BACKGROUND)
    def paintControl(e: PaintEvent) = {
      e.widget match {
        case sash: Sash ⇒
          val vertical = (sash.getStyle() & SWT.VERTICAL) != 0
          val background = e.gc.getBackground()
          val size = sash.getSize()
          e.gc.setBackground(sashColor)
          if (vertical) {
            val from = (size.y - delimeterSize) / 2
            e.gc.fillRectangle(0, from, size.x, delimeterSize)
          } else {
            val from = (size.x - delimeterSize) / 2
            e.gc.fillRectangle(from, 0, delimeterSize, size.y)
          }
      }
    }
  }
  /** Table viewer filter. */
  object TableViewerFilter extends ViewerFilter {
    def select(viewer: Viewer, parentElement: AnyRef, element: AnyRef): Boolean = filterType match {
      case 0 ⇒ // All
        val marker = element.asInstanceOf[GraphMarker]
        filterPattern.matcher(marker.graphModelId.name).matches() ||
          filterPattern.matcher(marker.graphOrigin.name).matches() ||
          filterPattern.matcher(Report.dateString(new Date(marker.graphCreated.milliseconds))).matches() ||
          filterPattern.matcher(Report.dateString(new Date(marker.graphStored.milliseconds))).matches()
      case 1 ⇒ // Name
        val marker = element.asInstanceOf[GraphMarker]
        filterPattern.matcher(marker.graphModelId.name).matches()
      case 2 ⇒ // Owner
        val marker = element.asInstanceOf[GraphMarker]
        filterPattern.matcher(marker.graphOrigin.name).matches()
      case 3 ⇒ // CreatedAt
        val marker = element.asInstanceOf[GraphMarker]
        filterPattern.matcher(Report.dateString(new Date(marker.graphCreated.milliseconds))).matches()
      case 4 ⇒ // Modified
        val marker = element.asInstanceOf[GraphMarker]
        filterPattern.matcher(Report.dateString(new Date(marker.graphStored.milliseconds))).matches()
      case _ ⇒ true
    }
  }
  /** Gallery ZoomIn action. */
  object GalleryZoomInAction extends Action("Zoom In") {
    override def run() {
      val width = galleryGroupRenderer.getItemWidth()
      if (width < 300) {
        galleryGroupRenderer.setItemWidth(width + 50)
        galleryGroupRenderer.setItemHeight(width + 50)
        if (galleryGroupRenderer.getItemWidth() >= 300)
          setEnabled(false)
        GalleryZoomOutAction.setEnabled(true)
        updateGallery()
      }
    }
  }
  /** Gallery ZoomOut action. */
  object GalleryZoomOutAction extends Action("Zoom Out") {
    override def run() {
      val width = galleryGroupRenderer.getItemWidth()
      if (width > 50) {
        galleryGroupRenderer.setItemWidth(width - 50)
        galleryGroupRenderer.setItemHeight(width - 50)
        if (galleryGroupRenderer.getItemWidth() <= 50)
          setEnabled(false)
        GalleryZoomInAction.setEnabled(true)
        updateGallery()
      }
    }
  }
}

object GraphSelectionDialog extends Loggable {
  lazy val comboFilterArray = Array("All", "Name", "Owner", "CreatedAt", "Modified")

  object NameLabelProvider extends ColumnLabelProvider {
    override def getText(element: AnyRef): String = element match {
      case marker: GraphMarker ⇒ marker.graphModelId.name
    }
  }
  object OriginLabelProvider extends ColumnLabelProvider {
    override def getText(element: AnyRef): String = element match {
      case marker: GraphMarker ⇒ marker.graphOrigin.name
    }
  }
  object CreatedAtLabelProvider extends ColumnLabelProvider {
    override def getText(element: AnyRef): String = element match {
      case marker: GraphMarker ⇒ Report.dateString(new Date(marker.graphCreated.milliseconds))
    }
  }
  object ModifiedLabelProvider extends ColumnLabelProvider {
    override def getText(element: AnyRef): String = element match {
      case marker: GraphMarker ⇒ Report.dateString(new Date(marker.graphStored.milliseconds))
    }
  }
  /** Table viewer comparator. */
  class ViewComparator(dialog: WeakReference[GraphSelectionDialog]) extends ViewerComparator {
    private var _column = dialog.get.map(_.sortColumn) getOrElse
      { throw new IllegalStateException("Dialog not found.") }
    private var _direction = dialog.get.map(_.sortDirection) getOrElse
      { throw new IllegalStateException("Dialog not found.") }

    /** Active column getter */
    def column = _column
    /** Active column setter */
    def column_=(arg: Int) {
      _column = arg
      dialog.get.foreach(_.sortColumn = _column)
      _direction = Default.sortingDirection
      dialog.get.foreach(_.sortDirection = _direction)
    }
    /** Sorting direction */
    def direction = _direction
    /**
     * Returns a negative, zero, or positive number depending on whether
     * the first element is less than, equal to, or greater than
     * the second element.
     */
    override def compare(viewer: Viewer, e1: Object, e2: Object): Int = {
      val entity1 = e1.asInstanceOf[GraphMarker]
      val entity2 = e2.asInstanceOf[GraphMarker]
      val rc = column match {
        case 0 ⇒ entity1.graphModelId.name.compareTo(entity2.graphModelId.name)
        case 1 ⇒ entity1.graphOrigin.name.compareTo(entity2.graphOrigin.name)
        case 2 ⇒ entity1.graphCreated.compareTo(entity2.graphCreated)
        case 3 ⇒ entity1.graphStored.compareTo(entity2.graphStored)
        case index ⇒
          log.fatal(s"unknown column with index $index"); 0
      }
      if (_direction) -rc else rc
    }
    /** Switch comparator direction */
    def switchDirection() {
      _direction = !_direction
      dialog.get.foreach(_.sortDirection = _direction)
    }
  }
  /** Table viewer selection adapter. */
  class ViewSelectionAdapter(tableViewer: WeakReference[TableViewer], column: Int) extends SelectionAdapter {
    override def widgetSelected(e: SelectionEvent) = {
      tableViewer.get.foreach(viewer ⇒ viewer.getComparator() match {
        case comparator: ViewComparator if comparator.column == column ⇒
          comparator.switchDirection()
          viewer.refresh()
        case comparator: ViewComparator ⇒
          comparator.column = column
          viewer.refresh()
        case _ ⇒
      })
    }
  }
}
