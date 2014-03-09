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

package org.digimead.tabuddy.desktop.core.ui.inspector.dialog

import javax.inject.Inject
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.ui.UI
import org.digimead.tabuddy.desktop.core.ui.definition.Dialog
import org.digimead.tabuddy.desktop.core.ui.definition.widget.{ SComposite, VComposite, WComposite }
import org.eclipse.e4.core.contexts.IEclipseContext
import org.eclipse.jface.action.{ Action, IMenuListener, IMenuManager, MenuManager }
import org.eclipse.jface.viewers.{ ArrayContentProvider, ColumnLabelProvider, DoubleClickEvent, IDoubleClickListener, ISelectionChangedListener, IStructuredSelection, ITableLabelProvider, ITreeContentProvider, LabelProvider, SelectionChangedEvent, TreeViewer, TreeViewerColumn, Viewer }
import org.eclipse.swt.SWT
import org.eclipse.swt.custom.StackLayout
import org.eclipse.swt.events.{ DisposeEvent, DisposeListener, SelectionEvent, SelectionListener }
import org.eclipse.swt.graphics.{ Color, Image }
import org.eclipse.swt.layout.{ FillLayout, FormLayout, GridLayout, RowLayout }
import org.eclipse.swt.widgets.{ Composite, Control, Event, Listener, Shell, ToolTip, TreeItem, Widget }

/**
 * Inspector dialog implementation.
 */
class InspectorDialog @Inject() (
  /** This dialog context. */
  val context: IEclipseContext,
  /** Parent shell. */
  val parentShell: Shell)
  extends InspectorDialogSkel(parentShell) with Dialog with Loggable {
  val colors = Seq(
    App.display.getSystemColor(SWT.COLOR_RED),
    App.display.getSystemColor(SWT.COLOR_BLUE),
    App.display.getSystemColor(SWT.COLOR_YELLOW),
    App.display.getSystemColor(SWT.COLOR_GREEN),
    App.display.getSystemColor(SWT.COLOR_CYAN),
    App.display.getSystemColor(SWT.COLOR_MAGENTA),
    App.display.getSystemColor(SWT.COLOR_DARK_RED),
    App.display.getSystemColor(SWT.COLOR_DARK_BLUE),
    App.display.getSystemColor(SWT.COLOR_DARK_YELLOW),
    App.display.getSystemColor(SWT.COLOR_DARK_GREEN),
    App.display.getSystemColor(SWT.COLOR_DARK_CYAN),
    App.display.getSystemColor(SWT.COLOR_DARK_MAGENTA))

  /**
   * Create contents of the dialog.
   *
   * @param parent parent composite
   * @return dialog content
   */
  override protected def createDialogArea(parent: Composite): Control = {
    val result = super.createDialogArea(parent)
    getShell.setText("Inspector")
    getBtnRefresh().addSelectionListener(new SelectionListener() {
      def widgetSelected(event: SelectionEvent) = InspectorDialog.this.refresh()
      def widgetDefaultSelected(event: SelectionEvent) = widgetSelected(event)
    })
    getBtnReset().addSelectionListener(new SelectionListener() {
      def widgetSelected(event: SelectionEvent) = InspectorDialog.this.reset()
      def widgetDefaultSelected(event: SelectionEvent) = widgetSelected(event)
    })
    /*
     * tree
     */
    val treeViewer = getTreeViewer()
    val column1 = new TreeViewerColumn(treeViewer, SWT.LEFT)
    column1.getColumn().setText("Widget")
    column1.getColumn().pack()
    column1.setLabelProvider(new InspectorDialog.WigdetLabelProvider)
    val column2 = new TreeViewerColumn(treeViewer, SWT.LEFT)
    column2.getColumn().setText("Default size")
    column2.getColumn().pack()
    column2.setLabelProvider(new InspectorDialog.SizeLabelProvider(true))
    val column3 = new TreeViewerColumn(treeViewer, SWT.LEFT)
    column3.getColumn().setText("Minimum size")
    column3.getColumn().pack()
    column3.setLabelProvider(new InspectorDialog.SizeLabelProvider(false))
    val autoResizeListener = new Listener() {
      override def handleEvent(e: Event) = e.item match {
        case treeItem: TreeItem ⇒ App.exec { for (tc ← treeItem.getParent().getColumns()) tc.pack() }
      }
    }
    treeViewer.getTree().addListener(SWT.Collapse, autoResizeListener)
    treeViewer.getTree().addListener(SWT.Expand, autoResizeListener)
    treeViewer.setContentProvider(new InspectorDialog.TreeContentProvider)
    treeViewer.addSelectionChangedListener(new ISelectionChangedListener() {
      def selectionChanged(e: SelectionChangedEvent) = e.getSelection() match {
        case iStructuredSelection: IStructuredSelection ⇒
          val compositePart = iStructuredSelection.getFirstElement() match {
            case composite: Composite ⇒ Array[Tuple2[String, Any]](("layout", composite.getLayout()))
            case _ ⇒ Array[Tuple2[String, Any]]()
          }
          val controlPart = iStructuredSelection.getFirstElement() match {
            case control: Control ⇒ Array[Tuple2[String, Any]](
              ("visible", control.isVisible()),
              ("disposed", control.isDisposed()),
              ("enabled", control.isEnabled()),
              ("layout data", control.getLayoutData()),
              ("tooltip", control.getToolTipText()),
              ("data", control.getData()))
            case _ ⇒ Array()
          }
          InspectorDialog.this.getTableViewer().setInput((compositePart ++ controlPart).sortBy(_._1))
        case _ ⇒
      }
    })
    treeViewer.addDoubleClickListener(new IDoubleClickListener() {
      override def doubleClick(event: DoubleClickEvent) {
        val viewer = event.getViewer().asInstanceOf[TreeViewer]
        val thisSelection = event.getSelection().asInstanceOf[IStructuredSelection]
        val selectedNode = thisSelection.getFirstElement()
        doubleClickTreeElement(selectedNode)
      }
    })
    // tree menu
    val menuMgr = new MenuManager("#PopupMenu")
    menuMgr.setRemoveAllWhenShown(true)
    menuMgr.addMenuListener(new IMenuListener() {
      override def menuAboutToShow(manager: IMenuManager) = treeViewer.getSelection() match {
        case iStructuredSelection: IStructuredSelection ⇒
          iStructuredSelection.getFirstElement() match {
            case composite: Composite ⇒
              manager.add(new Action("Locate") { override def run = doubleClickTreeElement(iStructuredSelection.getFirstElement()) })
              manager.add(new Action("Call computeSize def") { override def run = composite.computeSize(SWT.DEFAULT, SWT.DEFAULT) })
              manager.add(new Action("Call computeSize min") { override def run = composite.computeSize(1, 1) })
              manager.add(new Action("Pack") { override def run = composite.pack() })
              manager.add(new Action("Layout") { override def run = composite.layout() })
              manager.add(new Action("Update") { override def run = composite.update() })
              manager.add(new Action("Redraw") { override def run = composite.redraw() })
            case _ ⇒
          }
        case _ ⇒
      }
    })
    val menu = menuMgr.createContextMenu(treeViewer.getTree())
    treeViewer.getTree().setMenu(menu)
    /*
     * table
     */
    val tableViewer = getTableViewer()
    tableViewer.setContentProvider(ArrayContentProvider.getInstance())
    tableViewer.setLabelProvider(new InspectorDialog.TableLabelProvider())
    getTextMargin().setText(InspectorDialog.margin.toString())
    getShell().addDisposeListener(new DisposeListener {
      def widgetDisposed(e: DisposeEvent) = InspectorDialog.this.reset()
    })
    result
  }
  /** Decorate the specific composite. */
  protected def decorateComposite(composite: Composite, colorIndex: Int = 0) {
    var index = colorIndex
    pad(composite, getMargin())
    decorateControl(composite, index)
    composite.getChildren().foreach { child ⇒
      index += 1
      child match {
        case composite: Composite ⇒ decorateComposite(composite, index)
        case control: Control ⇒ decorateControl(control, index)
        case _ ⇒
      }
    }
  }
  /** Decorate the specific control. */
  protected def decorateControl(control: Control, colorIndex: Int = 0) {
    if (control.getData(InspectorDialog.swtBackgroundId) != null)
      return
    val index = colorIndex % colors.size
    control.setData(InspectorDialog.swtBackgroundId, control.getBackground())
    control.setBackground(colors(index))
    control.setData(InspectorDialog.swtToolTipId, control.getToolTipText())
    control.setToolTipText(getToolTipText(control))
  }
  /** Handle double click on tree element. */
  def doubleClickTreeElement(element: AnyRef) = element match {
    case control: Control ⇒
      val bounds = control.getBounds()
      val tip = new ToolTip(control.getShell(), SWT.BALLOON)
      tip.setMessage(s"Top left corner of ${control} with ${bounds}.")
      tip.setLocation(control.toDisplay(0, 0))
      tip.setAutoHide(true)
      tip.setVisible(true)
    case other ⇒
      log.debug("Skip double click on unknown element.")
  }
  /** Get margin value. */
  def getMargin(): Int = try getTextMargin().getText().toInt catch {
    case e: Throwable ⇒
      log.error(s"Unable to convert margin value '${getTextMargin().getText()} to integer: ${e.getMessage()}.")
      InspectorDialog.margin
  }
  /** Get ToolTip text for control. */
  protected def getToolTipText(control: Control): String = {
    val common: Seq[String] = Seq(
      "Class: " + control.getClass().getName(),
      "Control: " + control.toString(),
      ToolTip.addLayout(control))
    val specific: Seq[String] = control match {
      case wComposite: WComposite ⇒ Seq(
        s"Window Id: ${wComposite.id} / ${"%08X".format(wComposite.id.hashCode())}",
        s"Actor: " + wComposite.ref)
      case vComposite: VComposite ⇒ Seq(
        s"View Id: ${vComposite.id} / ${"%08X".format(vComposite.id.hashCode())}",
        s"Actor: " + vComposite.ref)
      case sComposite: SComposite ⇒ Seq(
        s"Stack Id: ${sComposite.id} / ${"%08X".format(sComposite.id.hashCode())}",
        s"Actor: " + sComposite.ref)
      case control ⇒ Seq()
    }
    (common ++ specific).mkString("\n")
  }
  /** Pad composite with margin. */
  def pad(composite: Composite, margin: Int) = {
    composite.getLayout match {
      case layout: FillLayout ⇒
        composite.getData(InspectorDialog.swtMarginId) match {
          case (marginWidth: Int, marginHeight: Int) ⇒
            if (layout.marginWidth != (marginWidth + margin)) {
              layout.marginWidth = marginWidth + margin
              layout.marginHeight = marginHeight + margin
            }
          case _ ⇒
            composite.setData(InspectorDialog.swtMarginId, (layout.marginWidth, layout.marginHeight))
            layout.marginWidth += margin
            layout.marginHeight += margin
        }
      case layout: FormLayout ⇒
        composite.getData(InspectorDialog.swtMarginId) match {
          case (marginWidth: Int, marginHeight: Int) ⇒
            if (layout.marginWidth != (marginWidth + margin)) {
              layout.marginWidth = marginWidth + margin
              layout.marginHeight = marginHeight + margin
            }
          case _ ⇒
            composite.setData(InspectorDialog.swtMarginId, (layout.marginWidth, layout.marginHeight))
            layout.marginWidth += margin
            layout.marginHeight += margin
        }
      case layout: GridLayout ⇒
        composite.getData(InspectorDialog.swtMarginId) match {
          case (marginWidth: Int, marginHeight: Int) ⇒
            if (layout.marginWidth != (marginWidth + margin)) {
              layout.marginWidth = marginWidth + margin
              layout.marginHeight = marginHeight + margin
            }
          case _ ⇒
            composite.setData(InspectorDialog.swtMarginId, (layout.marginWidth, layout.marginHeight))
            layout.marginWidth += margin
            layout.marginHeight += margin
        }
      case layout: RowLayout ⇒
        composite.getData(InspectorDialog.swtMarginId) match {
          case (marginWidth: Int, marginHeight: Int) ⇒
            if (layout.marginWidth != (marginWidth + margin)) {
              layout.marginWidth = marginWidth + margin
              layout.marginHeight = marginHeight + margin
            }
          case _ ⇒
            composite.setData(InspectorDialog.swtMarginId, (layout.marginWidth, layout.marginHeight))
            layout.marginWidth += margin
            layout.marginHeight += margin
        }
      case layout: StackLayout ⇒
        composite.getData(InspectorDialog.swtMarginId) match {
          case (marginWidth: Int, marginHeight: Int) ⇒
            if (layout.marginWidth != (marginWidth + margin)) {
              layout.marginWidth = marginWidth + margin
              layout.marginHeight = marginHeight + margin
            }
          case _ ⇒
            composite.setData(InspectorDialog.swtMarginId, (layout.marginWidth, layout.marginHeight))
            layout.marginWidth += margin
            layout.marginHeight += margin
        }
      case layout if layout != null ⇒
        log.warn(s"Unable to adjust unknown layout ${layout}.")
      case layout ⇒
    }
  }
  /** Refresh the inspector state. */
  def refresh() {
    log.debug("Refresh inspector.")
    val shell = getShell()
    val shells = App.display.getShells().filterNot(_ == shell)
    shells.foreach(shell ⇒
      shell.getChildren().foreach(
        _ match {
          case composite: Composite ⇒
            decorateComposite(composite)
            composite.layout()
          case control: Control ⇒
            decorateControl(control)
          case _ ⇒
        }))
    getTreeViewer.setInput(shells)
  }
  /** Reset the inspector state. */
  def reset() {
    log.debug("Reset inspector.")
    val shell = getShell()
    val shells = App.display.getShells().filterNot(_ == shell)
    shells.foreach(shell ⇒
      shell.getChildren().foreach(
        _ match {
          case composite: Composite ⇒
            resetComposite(composite)
            composite.layout()
          case control: Control ⇒
            resetControl(control)
          case _ ⇒
        }))
    getTreeViewer.setInput(shells)
  }
  /** Reset decoration of the specific composite. */
  protected def resetComposite(composite: Composite) {
    pad(composite, 0)
    composite.setData(InspectorDialog.swtMarginId, null)
    resetControl(composite)
    composite.getChildren().foreach { child ⇒
      child match {
        case composite: Composite ⇒ resetComposite(composite)
        case control: Control ⇒ resetControl(control)
        case _ ⇒
      }
    }
  }
  /** Reset decoration of the specific control. */
  protected def resetControl(control: Control) {
    if (control.getData(InspectorDialog.swtBackgroundId) == null)
      return
    control.setBackground(control.getData(InspectorDialog.swtBackgroundId).asInstanceOf[Color])
    control.setData(InspectorDialog.swtBackgroundId, null)
    control.setToolTipText(control.getData(InspectorDialog.swtToolTipId).asInstanceOf[String])
    control.setData(InspectorDialog.swtToolTipId, null)
  }

  object ToolTip {
    /** Get information about layout. */
    def addLayout(control: Control) = {
      val common = "Bounds: " + control.getBounds()
      val specific = control match {
        case view: VComposite ⇒ ""
        case control ⇒ ""
      }
      common + specific
    }
  }
}

object InspectorDialog {
  /** SWT Data ID key for background color. */
  val swtBackgroundId = getClass.getName() + "#Background"
  /** SWT Data ID key for margin. */
  val swtMarginId = getClass.getName() + "#Margin"
  /** SWT Data ID key for tooltip. */
  val swtToolTipId = getClass.getName() + "#Tooltip"

  /**Get inspector margin. */
  def margin = DI.margin

  /**
   * InspectorDialog table label provider.
   */
  class TableLabelProvider extends LabelProvider with ITableLabelProvider {
    def getColumnImage(element: AnyRef, columnIndex: Int): Image = null
    def getColumnText(element: AnyRef, columnIndex: Int): String = element match {
      case (name, value) if (columnIndex == 0) ⇒
        String.valueOf(name)
      case (name, value) if (columnIndex == 1) ⇒
        String.valueOf(value)
      case element if (columnIndex == 0) ⇒
        "???"
      case element if (columnIndex == 1) ⇒
        String.valueOf(element)
    }
  }
  /**
   * InspectorDialog widget column label provider.
   */
  class SizeLabelProvider(val default: Boolean) extends ColumnLabelProvider {
    override def getText(element: AnyRef) = element match {
      case element: Control ⇒
        if (default)
          element.computeSize(SWT.DEFAULT, SWT.DEFAULT).toString()
        else
          element.computeSize(1, 1).toString()
      case element ⇒ "unknown"
    }
  }
  /**
   * InspectorDialog widget column label provider.
   */
  class WigdetLabelProvider extends ColumnLabelProvider
  /**
   * InspectorDialog tree content provider.
   */
  class TreeContentProvider extends ITreeContentProvider {
    def inputChanged(viewer: Viewer, oldInput: AnyRef, newInput: AnyRef) {}
    def dispose() {}
    def getElements(inputElement: AnyRef): Array[AnyRef] = getChildren(inputElement)
    def getChildren(parentElement: AnyRef): Array[AnyRef] = parentElement match {
      case elements: Array[_] ⇒ elements.asInstanceOf[Array[AnyRef]].sortBy(_.asInstanceOf[Shell].getText())
      case element: Composite ⇒ element.getChildren().asInstanceOf[Array[AnyRef]].sortBy(_.toString()).sortBy(!hasChildren(_))
      case other ⇒ Array()
    }
    def getParent(element: AnyRef): AnyRef = element match {
      case elements: Shell ⇒ null
      case widget: Widget ⇒ UI.findParent(widget) getOrElse null
      case other ⇒ null
    }
    def hasChildren(element: AnyRef): Boolean = element match {
      case element: Composite ⇒ element.getChildren().length > 0
      case other ⇒ false
    }
  }
  /**
   * Dependency injection routines.
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** Inspector margin. */
    lazy val margin = injectOptional[Int]("Core.UI.Inspector.Margin") getOrElse 3
  }
}
