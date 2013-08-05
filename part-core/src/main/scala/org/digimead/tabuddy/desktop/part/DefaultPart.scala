/**
 * This file is part of the TABuddy project.
 * Copyright (c) 2013 Alexey Aksenov ezh@ezh.msk.ru
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

package org.digimead.tabuddy.desktop.part

import java.net.URI

import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.Messages
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.App.app2implementation
import org.eclipse.e4.core.contexts.IEclipseContext
import org.eclipse.e4.core.di.annotations.CanExecute
import org.eclipse.e4.core.di.annotations.Execute
import org.eclipse.e4.ui.model.application.ui.basic.MPart
import org.eclipse.e4.ui.model.application.ui.menu.MMenu
import org.eclipse.e4.ui.model.application.ui.menu.MMenuFactory
import org.eclipse.e4.ui.model.application.ui.menu.impl.MenuFactoryImpl
import org.eclipse.e4.ui.workbench.renderers.swt.StackRenderer
import org.eclipse.swt.SWT
import org.eclipse.swt.events.SelectionAdapter
import org.eclipse.swt.events.SelectionEvent
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.widgets.Button
import org.eclipse.swt.widgets.Composite
import org.eclipse.ui.internal.util.BundleUtility

import javax.annotation.PostConstruct
import javax.inject.Inject

class DefaultPart extends Loggable {
  @Inject
  private val context: IEclipseContext = null
  private lazy val viewMenu = {
    //val menu = App.modelCreateMenuFromContribution("org.digimead.tabuddy.desktop.menuX", context, false)
    //menu.getTags().add(StackRenderer.TAG_VIEW_MENU)
    //menu
    null
  }

  @Inject
  private val parent: Composite = null
  private val factory: MMenuFactory = MMenuFactory.INSTANCE
  private var mMenu: MMenu = null
  class DirectProxy {
    @CanExecute
    def canExecute() = true

    @Execute
    def execute() {
      log.___gaze("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!")
    }
  }
  @PostConstruct
  def buildUI(mPart: MPart) {
    val composite = new Composite(parent, SWT.BORDER)
    composite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, false))
    composite.setLayout(new GridLayout(3, false))

    mMenu = factory.createMenu()
    //mMenu.getTags().add(StackRenderer.TAG_VIEW_MENU)
    //mPart.getMenus().add(mMenu)

    /*val menuModel = MenuFactoryImpl.eINSTANCE.createMenu()
    val toContribute = new java.util.ArrayList[MMenuContribution]()
    val menuContributionsToRemove = new java.util.ArrayList[MMenuElement]()
    val eContext = new ExpressionContext(App.application.getContext())
    ContributionsAnalyzer.gatherMenuContributions(menuModel,
      App.application.getMenuContributions(), "org.digimead.tabuddy.desktop.menuX", toContribute, eContext, false)
    //ContributionsAnalyzer.gatherMenuContributions(menuModel,
    //  App.application.getMenuContributions(), "org.digimead.tabuddy.desktop.menuY", toContribute, eContext, true)
    ContributionsAnalyzer.addMenuContributions(menuModel, toContribute,
      menuContributionsToRemove)
    menuModel.getTags().add(StackRenderer.TAG_VIEW_MENU)*/
    mPart.getMenus().add(viewMenu)

    //addMenuOptions()

    // Button 1
    val toolItem1 = factory.createDirectToolItem();
    toolItem1.setLabel("Button 1");
    toolItem1.setTooltip("Button 1");
    toolItem1.setObject(new DirectProxy)
    val toolbar = Option(mPart.getToolbar()) match {
      case Some(toolbar) => toolbar
      case None =>
        val toolbar = MenuFactoryImpl.eINSTANCE.createToolBar()
        mPart.setToolbar(toolbar)
        toolbar
    }
    toolbar.getChildren().add(toolItem1)

    val button = new Button(composite, SWT.PUSH);
    button.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
    button.setText("Remove Menu Options");
    button.addSelectionListener(new SelectionAdapter() {
      override def widgetSelected(e: SelectionEvent) {
        if (mMenu.getChildren().size() == 0) {
          addMenuOptions();
          button.setText("Remove Menu Options");
        } else {
          mMenu.getChildren().clear();
          button.setText("Add Menu Options");
        }
      }
    })
    parent.layout();
  }
  def addMenuOptions() {

    val children = mMenu.getChildren();

    var menuItem = factory.createDirectMenuItem();
    menuItem.setLabel("Menu Item 1");
    try {
      menuItem.setIconURI(classOf[DefaultPart].getClassLoader().getResource("/icons/sample.gif").toURI().toString());
    } catch {
      case e: Throwable =>
        e.printStackTrace();
    }
    children.add(menuItem);

    children.add(factory.createMenuSeparator());

    menuItem = factory.createDirectMenuItem();
    menuItem.setLabel("Menu Item 2");
    children.add(menuItem);

    val newMenu = factory.createMenu();
    newMenu.setLabel("Submenu");

    menuItem = factory.createDirectMenuItem();
    menuItem.setLabel("Menu Item 3");
    try {
      menuItem.setIconURI(classOf[DefaultPart].getClassLoader().getResource("/icons/sample.gif").toURI().toString());
    } catch {
      case e: Throwable =>
        e.printStackTrace();
    }
    newMenu.getChildren().add(menuItem);
    children.add(newMenu);
  }
  /*
  private var viewer: TableViewer = null;

  @PostConstruct
  def postConstruct(parent: Composite) {
    log.___glance("@PostConstruct" + parent)
    viewer = new TableViewer(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);
    viewer.setContentProvider(new ViewContentProvider());
    viewer.setLabelProvider(new ViewLabelProvider());
    // Provide the input to the ContentProvider
    viewer.setInput(Array[String]("OneZZZAAA", "TwoZZZ", "ThreeABC"))
  }

  @PreDestroy
  def preDestroy() {
    log.___glance("@PreDestroy")
  }

  @Focus
  def onFocus() {
    log.___glance("@Focus")
    viewer.getControl().setFocus()
  }

  /**
   * The content provider class is responsible for providing objects to the
   * view. It can wrap existing objects in adapters or simply return objects
   * as-is. These objects may be sensitive to the current input of the view,
   * or ignore it and always show the same content (like Task List, for
   * example).
   */
  class ViewContentProvider extends IStructuredContentProvider {
    def inputChanged(v: Viewer, oldInput: Object, newInput: Object) {
      /*
 * 	if (menu != null && menu.getChildren() != null) {
ArrayList<MMenuElement> list = new ArrayList<MMenuElement>();
for (MMenuElement element : menu.getChildren()) {
if (element.getLabel() != null
&& element.getLabel().contains("Exit")) {
list.add(element);
}
}
menu.getChildren().removeAll(list);

MDirectMenuItem menuItem = MMenuFactory.INSTANCE
.createDirectMenuItem();
menuItem.setLabel("My Exit");
menuItem.setContributionURI("bundleclass://com.example.e4.rcp.todo.contribute/com.example.e4.rcp.todo.contribute.handlers.ExitHandlerWithCheck");
menu.getChildren().add(menuItem);
}
 */
      //ContextInjectionFactory.inject(man,iEclipseContext);
    }

    def dispose() {
    }

    def getElements(parent: Object): Array[AnyRef] = {
      if (parent.isInstanceOf[Array[_]]) {
        return parent.asInstanceOf[Array[AnyRef]]
      }
      return Array()
    }
  }

  class ViewLabelProvider extends LabelProvider with ITableLabelProvider {
    def getColumnText(obj: Object, index: Int): String = {
      return getText(obj)
    }

    def getColumnImage(obj: Object, index: Int): Image = {
      return getImage(obj)
    }

    override def getImage(obj: Object): Image = {
      return PlatformUI.getWorkbench().getSharedImages().getImage(
        ISharedImages.IMG_OBJ_ELEMENT);
    }
  }*/
}

object DefaultPart {
/*  private lazy val descriptor = App.modelCreatePartDescriptor(
    contributionURI = new URI(s"bundleclass://${App.bundle(getClass).getSymbolicName()}/${classOf[DefaultPart].getName()}"),
    contributorURI = new URI(s"platform:/plugin/${App.bundle(getClass).getSymbolicName()}"),
    iconURI = Some(BundleUtility.find(App.bundle(getClass), "icons/16x16/view-process-all.png").toURI()),
    id = classOf[DefaultPart].getName(),
    label = Messages.overViewPanelTitle_text)*/

  //def apply() {
  //  if (!App.model.getDescriptors().contains(descriptor))
  //    App.model.getDescriptors().add(descriptor)
 // }
}
