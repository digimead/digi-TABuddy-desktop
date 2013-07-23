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

package org.digimead.tabuddy.desktop.support.app

import java.net.URI

import scala.collection.JavaConversions._

import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.App.app2implementation
import org.eclipse.e4.core.commands.ExpressionContext
import org.eclipse.e4.core.contexts.IEclipseContext
import org.eclipse.e4.ui.internal.workbench.ContributionsAnalyzer
import org.eclipse.e4.ui.model.application.commands.MBindingContext
import org.eclipse.e4.ui.model.application.commands.MHandler
import org.eclipse.e4.ui.model.application.descriptor.basic.MPartDescriptor
import org.eclipse.e4.ui.model.application.descriptor.basic.impl.BasicFactoryImpl
import org.eclipse.e4.ui.model.application.ui.MElementContainer
import org.eclipse.e4.ui.model.application.ui.MUIElement
import org.eclipse.e4.ui.model.application.ui.basic.MBasicFactory
import org.eclipse.e4.ui.model.application.ui.basic.MInputPart
import org.eclipse.e4.ui.model.application.ui.basic.MWindow
import org.eclipse.e4.ui.model.application.ui.menu.MMenu
import org.eclipse.e4.ui.model.application.ui.menu.MMenuContribution
import org.eclipse.e4.ui.model.application.ui.menu.MMenuElement
import org.eclipse.e4.ui.model.application.ui.menu.MToolBar
import org.eclipse.e4.ui.model.application.ui.menu.impl.MenuFactoryImpl
import org.eclipse.e4.ui.workbench.modeling.EPartService
import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.util.EcoreUtil

trait Model {
  this: Loggable with Generic with Workbench =>
  /** Find renderer for MUIElement if any. */
  def findRenderer(element: MUIElement): Option[AnyRef] =
    Option(element.getRenderer()) orElse {
      Option(element.getParent()).flatMap(e => findRenderer(e))
    }
  /** Get renderer for MUIElement. */
  def getRenderer[T <: AnyRef: Manifest](element: MUIElement): T =
    { findRenderer(element).getOrElse { throw new IllegalStateException("Lost renderer for " + element) } }.asInstanceOf[T]
  /** Find MWindow for MUIElement if any. */
  def findWindowFor(element: MUIElement): Option[MWindow] = {
    var parent: MElementContainer[MUIElement] = element.getParent()
    while (parent != null && !(parent.isInstanceOf[MWindow]))
      parent = parent.getParent()
    if (parent != null && parent.isInstanceOf[MWindow])
      Some(parent.asInstanceOf[MWindow])
    else
      None
  }
  /** Create input part. */
  def modelCreateInputPart(id: String, inputURI: String, partService: EPartService): MInputPart = {
    val part = partService.createPart(id)
    val inputPart = MBasicFactory.INSTANCE.createInputPart()
    inputPart.getBindingContexts().addAll(part.getBindingContexts())
    inputPart.setCloseable(part.isCloseable())
    inputPart.setContributionURI(part.getContributionURI())
    inputPart.setContributorURI(part.getContributorURI())
    inputPart.setElementId(part.getElementId())
    inputPart.getHandlers().addAll(EcoreUtil.copyAll(part.getHandlers()))
    inputPart.setIconURI(part.getIconURI())
    inputPart.setInputURI(inputURI)
    inputPart.setLabel(part.getLabel())
    inputPart.getMenus().addAll(EcoreUtil.copyAll(part.getMenus()))
    inputPart.getTags().addAll(part.getTags())
    Option(part.getToolbar()).foreach { case toolbar: EObject => inputPart.setToolbar(EcoreUtil.copy(toolbar)).asInstanceOf[MToolBar] }
    inputPart.setTooltip(part.getTooltip())
    inputPart
  }
  // Part descriptor has huge technical debt :-/ It is broken under most of conditions.
  /** Create part descriptor. */
  def modelCreatePartDescriptor(allowMultiple: Boolean = true,
    bindingContexts: Seq[MBindingContext] = Seq(),
    category: Option[String] = None,
    closeable: Boolean = true,
    /** Contribution URI that points to the class with implementation. */
    contributionURI: URI,
    /** Contributor URI that is allow to use bundle resources. */
    contributorURI: URI,
    description: Option[String] = None,
    dirtyable: Boolean = true,
    handlers: Seq[MHandler] = Seq(),
    iconURI: Option[URI] = None,
    id: String,
    label: String,
    menus: Seq[MMenu] = Seq(),
    tags: Seq[String] = Seq(),
    toolbar: Option[MToolBar] = None,
    tooltip: Option[String] = None): MPartDescriptor = {
    val part = BasicFactoryImpl.eINSTANCE.createPartDescriptor()
    part.setAllowMultiple(allowMultiple)
    part.getBindingContexts().addAll(bindingContexts)
    category.foreach(part.setCategory)
    part.setCloseable(closeable)
    part.setContributionURI(contributionURI.toString)
    part.setContributorURI(contributorURI.toString)
    part.setDirtyable(dirtyable)
    description.foreach(part.setDescription)
    part.setElementId(id)
    part.getHandlers().addAll(handlers)
    iconURI.foreach(iconURI => part.setIconURI(iconURI.toString))
    part.setLabel(label)
    part.getMenus().addAll(menus)
    part.getTags().addAll(tags)
    toolbar.foreach(part.setToolbar)
    tooltip.foreach(part.setTooltip)
    part
  }
  /**
   * Create MMenu from application contribution.
   * @param contributionId id of the contribution ("a.b.c" for contribution with locationURI "menu:a.b.c")
   * @param context expression context
   */
  def modelCreateMenuFromContribution(contributionId: String, context: IEclipseContext, popup: Boolean): MMenu = {
    val menuModel = MenuFactoryImpl.eINSTANCE.createMenu()
    val toContribute = new java.util.ArrayList[MMenuContribution]()
    val menuContributionsToRemove = new java.util.ArrayList[MMenuElement]()
    val eContext = new ExpressionContext(context)
    ContributionsAnalyzer.gatherMenuContributions(menuModel,
      model.getMenuContributions(), contributionId, toContribute, eContext, popup)
    ContributionsAnalyzer.addMenuContributions(menuModel, toContribute,
      menuContributionsToRemove)
    menuModel
  }
}
