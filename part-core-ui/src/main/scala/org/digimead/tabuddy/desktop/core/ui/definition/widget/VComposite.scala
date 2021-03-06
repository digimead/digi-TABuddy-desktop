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

package org.digimead.tabuddy.desktop.core.ui.definition.widget

import akka.actor.{ ActorRef, actorRef2Scala }
import com.google.common.collect.MapMaker
import java.util.UUID
import org.digimead.tabuddy.desktop.core.definition.Context
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.ui.block.{ Configuration, View }
import org.eclipse.e4.core.di.InjectionException
import org.eclipse.swt.custom.ScrolledComposite
import org.eclipse.swt.events.{ DisposeEvent, DisposeListener }
import org.eclipse.swt.widgets.Composite
import scala.collection.JavaConverters.mapAsScalaMapConverter

/**
 * View composite that contains additional reference to content actor.
 * Content view actor is bound to root component because of changing parent by Akka is unsupported.
 */
class VComposite(val id: UUID, val ref: ActorRef, val contentRef: ActorRef, val factory: Configuration.Factory,
  parent: ScrolledComposite, style: Int)
  extends Composite(parent, style) with View.ViewMapDisposer with SComposite {
  initialize

  /** Get view context. */
  // There are a lot of situations when an application try to access context after WComposite is disposed.
  def getContext(): Option[Context] = VComposite.contextMap.get(this)
  /** Get content context. */
  def getContentContext(): Option[Context] = factory().withContexts(_.get(contentRef.path.name)).map(_._2)

  /** Returns the receiver's parent, which must be a ScrolledComposite. */
  override def getParent(): ScrolledComposite = super.getParent.asInstanceOf[ScrolledComposite]

  /** Initialize current view composite. */
  protected def initialize() {
    addDisposeListener(new DisposeListener {
      def widgetDisposed(e: DisposeEvent) {
        VComposite.contextMap.remove(VComposite.this).foreach { context ⇒
          try context.dispose()
          catch { case e: InjectionException ⇒ context.dispose() } // Yes, there is a bug in org.eclipse.e4.core.internal.contexts.EclipseContext.dispose
        }
        viewRemoveFromCommonMap()
        ref ! App.Message.Destroy(None, ref)
      }
    })
  }

  override lazy val toString = s"VComposite{${factory().name.name}}[%08X]".format(id.hashCode())
}

object VComposite {
  protected val contextMap = new MapMaker().weakKeys().makeMap[VComposite, Context]().asScala

  /** Set context for VComposite. */
  trait ContextSetter {
    def setVCompositeContext(vComposite: VComposite, context: Context) = contextMap(vComposite) = context
  }
}
