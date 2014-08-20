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

package org.digimead.tabuddy.desktop.core.ui

import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.Core
import org.digimead.tabuddy.desktop.core.definition.Context
import org.eclipse.e4.core.contexts.IEclipseContext
import org.eclipse.e4.ui.model.application.MApplicationElement
import org.eclipse.e4.ui.model.application.ui.{ MElementContainer, MUIElement }
import org.eclipse.e4.ui.model.application.ui.advanced.{ MPerspective, MPlaceholder }
import org.eclipse.e4.ui.model.application.ui.basic.{ MPartSashContainerElement, MWindow }
import org.eclipse.e4.ui.workbench.modeling.EModelService
import org.eclipse.jface.fieldassist.FieldDecorationRegistry
import org.eclipse.ui.internal.forms.MessageManager
import scala.collection.JavaConverters.asScalaSetConverter

/**
 * Fix underlaying infrastructure features... bugs?
 */
class InfrastructureFixes extends XLoggable {
  /** Fix underlaying infrastructure. */
  // Yo, wassup dawg? There were code monkeys so we will need to cleat all these shit.
  def fix() {
    try fix_org_eclipse_ui_internal_forms_MessageManager
    catch { case e: Throwable ⇒ log.warn("Unable to fix_org_eclipse_ui_internal_forms_MessageManager: " + e.getMessage()) }
    try addModelService
    catch { case e: Throwable ⇒ log.warn("Unable to addModelService: " + e.getMessage()) }
  }

  /** Add ModelService to workbench context. */
  protected def addModelService =
    Core.serviceContext.getChildren().asScala.find(ctx ⇒ Context.getName(ctx) == Some("workbench")).foreach {
      workbenchContext ⇒ workbenchContext.set(classOf[EModelService].getName, InfrastructureFixes.ModelService)
    }
  /** Fix static fields in org.eclipse.ui.internal.forms.MessageManager */
  // FieldDecorationRegistry reloaded. Old resources are disposed... and all new MessageManager instances are broken
  protected def fix_org_eclipse_ui_internal_forms_MessageManager {
    val mmClass = classOf[MessageManager]

    val standardErrorField = mmClass.getDeclaredField("standardError")
    if (!standardErrorField.isAccessible())
      standardErrorField.setAccessible(true)
    standardErrorField.set(null, FieldDecorationRegistry
      .getDefault().getFieldDecoration(FieldDecorationRegistry.DEC_ERROR))

    val standardWarningField = mmClass.getDeclaredField("standardWarning")
    if (!standardWarningField.isAccessible())
      standardWarningField.setAccessible(true)
    standardWarningField.set(null, FieldDecorationRegistry
      .getDefault().getFieldDecoration(FieldDecorationRegistry.DEC_WARNING))

    val standardInformationField = mmClass.getDeclaredField("standardInformation")
    if (!standardInformationField.isAccessible())
      standardInformationField.setAccessible(true)
    standardInformationField.set(null, FieldDecorationRegistry
      .getDefault().getFieldDecoration(FieldDecorationRegistry.DEC_INFORMATION))
  }
}

object InfrastructureFixes {
  /**
   * ModelService stub.
   */
  object ModelService extends EModelService {
    def bringToTop(element: MUIElement) {}
    def cloneElement(x$1: MUIElement, x$2: org.eclipse.e4.ui.model.application.ui.MSnippetContainer): MUIElement = ???
    def cloneSnippet(x$1: org.eclipse.e4.ui.model.application.ui.MSnippetContainer, x$2: String, x$3: org.eclipse.e4.ui.model.application.ui.basic.MWindow): MUIElement = ???
    def countRenderableChildren(element: MUIElement): Int = 0
    def createModelElement[T <: MApplicationElement](elementType: Class[T]): T = null.asInstanceOf[T]
    def detach(mPartSashContainerElement: MPartSashContainerElement, x: Int, y: Int, width: Int, height: Int) {}
    def find(id: String, searchRoot: MUIElement): MUIElement = null
    def findElements[T](searchRoot: MUIElement, id: String, clazz: Class[T], tagsToMatch: java.util.List[String]) = new java.util.ArrayList[T]()
    def findElements[T](searchRoot: MUIElement, id: String, clazz: Class[T], tagsToMatch: java.util.List[String], searchFlags: Int) = new java.util.ArrayList[T]()
    def findPlaceholderFor(window: MWindow, element: MUIElement): MPlaceholder = null
    def findSnippet(x$1: org.eclipse.e4.ui.model.application.ui.MSnippetContainer, x$2: String): MUIElement = ???
    def getActivePerspective(x$1: org.eclipse.e4.ui.model.application.ui.basic.MWindow): org.eclipse.e4.ui.model.application.ui.advanced.MPerspective = ???
    def getContainer(x$1: MUIElement): MUIElement = ???
    def getContainingContext(x$1: MUIElement): org.eclipse.e4.core.contexts.IEclipseContext = ???
    def getElementLocation(element: MUIElement): Int = EModelService.NOT_IN_UI
    def getPartDescriptor(x$1: String): org.eclipse.e4.ui.model.application.descriptor.basic.MPartDescriptor = ???
    def getPerspectiveFor(x$1: MUIElement): org.eclipse.e4.ui.model.application.ui.advanced.MPerspective = ???
    def getTopLevelWindowFor(x$1: MUIElement): org.eclipse.e4.ui.model.application.ui.basic.MWindow = ???
    def getTrim(x$1: org.eclipse.e4.ui.model.application.ui.basic.MTrimmedWindow, x$2: org.eclipse.e4.ui.model.application.ui.SideValue): org.eclipse.e4.ui.model.application.ui.basic.MTrimBar = ???
    def hideLocalPlaceholders(window: MWindow, perspective: MPerspective) {}
    def hostElement(element: MUIElement, hostWindow: MWindow, uiContainer: Any, hostContext: IEclipseContext) {}
    def insert(toInsert: MPartSashContainerElement, relTo: MPartSashContainerElement, where: Int, ratio: Float) {}
    def isHostedElement(element: MUIElement, window: MWindow): Boolean = true
    def isLastEditorStack(stack: MUIElement): Boolean = true
    def move(element: MUIElement, newParent: MElementContainer[MUIElement], index: Int, leavePlaceholder: Boolean) {}
    def move(element: MUIElement, newParent: MElementContainer[MUIElement], index: Int) {}
    def move(element: MUIElement, newParent: MElementContainer[MUIElement], leavePlaceholder: Boolean) {}
    def move(element: MUIElement, newParent: MElementContainer[MUIElement]) {}
    def removePerspectiveModel(persp: MPerspective, window: MWindow) {}
    def resetPerspectiveModel(persp: MPerspective, window: MWindow) {}
    def toBeRenderedCount(container: MElementContainer[_]): Int = 0
  }
}
