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

package org.digimead.tabuddy.desktop.gui.stack

import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.gui.StackConfiguration
import org.digimead.tabuddy.desktop.gui.api
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.App.app2implementation
import org.eclipse.swt.SWT
import org.eclipse.swt.custom.ScrolledComposite
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.widgets.Composite

import akka.actor.ActorRef

import language.implicitConversions

class StackTabBuilder extends Loggable {
  def apply(configuration: api.Configuration.Stack.Tab, stackActorRef: ActorRef, parent: ScrolledComposite): (SComposite, Seq[ScrolledComposite]) = {
    log.debug("Build content for stack tab.")
    App.checkThread
    if (parent.getLayout().isInstanceOf[GridLayout])
      throw new IllegalArgumentException(s"Unexpected parent layout ${parent.getLayout().getClass()}.")
    val content = new SComposite(configuration.id, stackActorRef, parent, SWT.NONE)
    content.setBackground(App.display.getSystemColor(SWT.COLOR_BLACK))
    val containers = for (i <- 0 until configuration.children.size) yield {
      val container = new ScrolledComposite(content, SWT.NONE)
      container.setBackground(App.display.getSystemColor(SWT.COLOR_YELLOW))
      container
    }
    (content, containers)
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
