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

import akka.actor.ActorRef
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.ui.UI
import org.digimead.tabuddy.desktop.core.ui.block.Configuration
import org.digimead.tabuddy.desktop.core.ui.definition.widget.{ SCompositeTab, VComposite }
import org.eclipse.jface.databinding.swt.SWTObservables
import org.eclipse.swt.SWT
import org.eclipse.swt.custom.ScrolledComposite
import org.eclipse.swt.layout.{ GridData, GridLayout }
import org.eclipse.swt.widgets.TabItem
import scala.language.implicitConversions

/** Build tab layer and allocate N tab items for views. */
class StackTabBuilder extends Loggable {
  def apply(configuration: Configuration.Stack.CTab, parentWidget: ScrolledComposite, stackRef: ActorRef): (SCompositeTab, Seq[ScrolledComposite]) = {
    log.debug(s"Build content for ${configuration}.")
    App.assertEventThread()
    if (parentWidget.getLayout().isInstanceOf[GridLayout])
      throw new IllegalArgumentException(s"Unexpected parent layout ${parentWidget.getLayout().getClass()}.")
    val content = new SCompositeTab(configuration.id, stackRef, parentWidget, SWT.NONE)
    content.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1))
    val containers = for (child ← configuration.children) yield addTabItem(content, (tabItem) ⇒ {
      tabItem.setData(UI.swtId, child.id)
      tabItem.setToolTipText(child.factory().shortDescription)
      child.factory().image.foreach(tabItem.setImage)
    })
    (content, containers)
  }
  /** Add new TabItem with ScrolledComposite to SCompositeTab. */
  def addTabItem[T](tab: SCompositeTab, adjust: TabItem ⇒ T): ScrolledComposite = {
    val container = new TabItem(tab, SWT.NULL)
    adjust(container)
    val scroll = new ScrolledComposite(tab, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL)
    container.setControl(scroll)
    scroll.setLayout(new GridLayout)
    scroll.setExpandHorizontal(true)
    scroll.setExpandVertical(true)
    scroll
  }
  /** Adjust TabItem. */
  def adjustTabItem(tabComposite: SCompositeTab, viewConfiguration: Configuration.CView) {
    App.execNGet {
      // Adjust tab.
      val result = for {
        tabItem ← tabComposite.getItems().find { item ⇒ item.getData(UI.swtId) == viewConfiguration.id }
        container ← tabComposite.getChildren().headOption
      } yield container match {
        case container: ScrolledComposite ⇒
          container.getChildren().headOption match {
            case Some(vComposite: VComposite) ⇒
              container.setContent(vComposite)
              container.setMinSize(vComposite.computeSize(SWT.DEFAULT, SWT.DEFAULT))
              container.layout(true)
            case unexpected ⇒
              throw new IllegalStateException(s"Incorrect ScrolledComposite content ${unexpected}.")
          }
        case unexpected ⇒
          throw new IllegalStateException(s"Incorrect SCompositeTab content ${unexpected}.")
      }
      result getOrElse { log.fatal(s"TabItem for ${viewConfiguration} in ${tabComposite} not found.") }
    }
  }
}

object StackTabBuilder {
  implicit def builder2implementation(c: StackTabBuilder.type): StackTabBuilder = c.inner

  /** StackTabBuilder implementation. */
  def inner = DI.implementation

  /**
   * Dependency injection routines.
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** StackTabBuilder implementation. */
    lazy val implementation = injectOptional[StackTabBuilder] getOrElse new StackTabBuilder
  }
}
