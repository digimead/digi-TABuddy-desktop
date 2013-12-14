/**
 * This file is part of the TA Buddy project.
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

package org.digimead.tabuddy.desktop.ui.builder

import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.ui.widget.{ AppWindow, WComposite }
import org.eclipse.swt.SWT
import org.eclipse.swt.custom.StackLayout
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.widgets.Composite
import scala.language.implicitConversions
import scala.ref.WeakReference

/**
 * Create initial window content.
 */
class ContentBuilder extends Loggable {
  /** Creates and returns this window's contents. */
  def apply(window: AppWindow, parent: Composite): (Composite, Composite, WComposite) = {
    log.debug(s"Build content for window ${window.id}.")
    App.assertEventThread()
    val container = new Composite(parent, SWT.NONE)
    val layout = new StackLayout()
    container.setLayout(layout)
    val filler = new Composite(container, SWT.NONE)
    filler.setBackground(App.display.getSystemColor(SWT.COLOR_DARK_GREEN))
    val content = new WComposite(window.id, window.ref, WeakReference(window), container, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL)
    content.setLayout(new GridLayout)
    content.setBackground(App.display.getSystemColor(SWT.COLOR_RED))
    layout.topControl = filler
    (container, filler, content)
  }
}

object ContentBuilder {
  implicit def builder2implementation(c: ContentBuilder.type): ContentBuilder = c.inner

  /** ContentBuilder implementation. */
  def inner = DI.implementation

  /**
   * Dependency injection routines.
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** Window ContentBuilder implementation. */
    lazy val implementation = injectOptional[ContentBuilder] getOrElse new ContentBuilder
  }
}
