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

package org.digimead.tabuddy.desktop.core.ui.block.builder

import akka.actor.ActorContext
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.definition.Context
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.ui.UI
import org.digimead.tabuddy.desktop.core.ui.block.Configuration
import org.digimead.tabuddy.desktop.core.ui.block.api.Configuration.CPlaceHolder
import org.digimead.tabuddy.desktop.core.ui.definition.widget.SComposite
import org.eclipse.swt.SWT
import org.eclipse.swt.custom.ScrolledComposite
import scala.language.implicitConversions

class StackBuilder extends Loggable {
  /** Creates stack content. */
  @log
  def apply(stack: CPlaceHolder, parentWidget: ScrolledComposite, parentContext: Context, stackActorContext: ActorContext): Option[SComposite] = {
    stack match {
      case tab: Configuration.Stack.CTab ⇒
        val (tabComposite, containers) = App.execNGet { StackTabBuilder(tab, parentWidget, stackActorContext.self) }
        // Attach list of Configuration.View(from tab.children) to ScrolledComposite(from containers)
        val content = for { (container, viewConfiguration) ← containers zip tab.children }
          yield (container, viewConfiguration, ViewContentBuilder.container(viewConfiguration, container, parentContext, stackActorContext, None))
        App.execNGet {
          content.foreach {
            case (container, viewConfiguration, Some(viewWidget)) ⇒
              // Adjust tab.
              tabComposite.getItems().find { item ⇒ item.getData(UI.swtId) == viewConfiguration.id } match {
                case Some(tabItem) ⇒
                  container.setContent(viewWidget)
                  val minimum = viewWidget.computeSize(SWT.DEFAULT, SWT.DEFAULT)
                  log.debug(s"Set minimum size to ${minimum} for ${viewConfiguration}.")
                  container.setMinSize(minimum)
                  container.layout(true)
                case None ⇒
                  log.fatal(s"CTabItem for ${viewConfiguration} in ${tabComposite} not found.")
              }
            case (container, viewConfiguration, None) ⇒
              log.fatal(s"Drop ${viewConfiguration} for ${container}.")
          }
          parentWidget.setContent(tabComposite)
          val minimum = tabComposite.computeSize(SWT.DEFAULT, SWT.DEFAULT)
          log.debug(s"Set minimum size to ${minimum} for STabComposite.")
          parentWidget.setMinSize(minimum)
          parentWidget.setExpandHorizontal(true)
          parentWidget.setExpandVertical(true)
          parentWidget.layout(true)
        }
        Option(tabComposite)

      case hsash: Configuration.Stack.CHSash ⇒
        val (sashComposite, left, right) = StackHSashBuilder(hsash, parentWidget, stackActorContext.self)
        //buildLevel(hsash.left, left)
        //buildLevel(hsash.right, right)
        Option(sashComposite)

      case vsash: Configuration.Stack.CVSash ⇒
        val (sashComposite, top, bottom) = StackVSashBuilder(vsash, parentWidget, stackActorContext.self)
        //buildLevel(vsash.top, top)
        //buildLevel(vsash.bottom, bottom)
        Option(sashComposite)

      case view: Configuration.CView ⇒
        log.fatal("Unable to process view as stack.")
        None

      case empty: Configuration.CEmpty ⇒
        log.fatal("Unable to process empty element as stack.")
        None
    }
  }
}

object StackBuilder {
  implicit def builder2implementation(c: StackBuilder.type): StackBuilder = c.inner

  /** StackBuilder implementation. */
  def inner = DI.implementation

  /**
   * Dependency injection routines.
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** Window ContentBuilder implementation. */
    lazy val implementation = injectOptional[StackBuilder] getOrElse new StackBuilder
  }
}
