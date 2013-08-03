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

package org.digimead.tabuddy.desktop.gui.transform

import scala.collection.immutable
import scala.concurrent.Await

import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.gui.Configuration
import org.digimead.tabuddy.desktop.gui.GUI
import org.digimead.tabuddy.desktop.gui.StackSupervisor
import org.digimead.tabuddy.desktop.gui.ViewLayer
import org.digimead.tabuddy.desktop.gui.builder.StackTabBuilder
import org.digimead.tabuddy.desktop.gui.builder.StackTabBuilder.builder2implementation
import org.digimead.tabuddy.desktop.gui.widget.SComposite
import org.digimead.tabuddy.desktop.gui.widget.SCompositeTab
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.App.app2implementation
import org.digimead.tabuddy.desktop.support.Timeout

import akka.pattern.ask
import akka.util.Timeout.durationToTimeout

import language.implicitConversions

class TransformAttachView extends Loggable {
  def apply(ss: StackSupervisor, tabStack: SCompositeTab, newView: ViewLayer.Factory) {
    log.debug(s"Create new view from ${newView} and attach it to ${tabStack}.")
    App.assertUIThread(false)
    log.debug("Update stack supervisor configuration.")
    val viewConfiguration = Configuration.View(newView.configuration)
    // Prepare tab item.
    val parentWidget = App.execNGet {
      StackTabBuilder.addTabItem(tabStack, (tabItem) => {
        tabItem.setData(GUI.swtId, viewConfiguration.id)
        newView.description.foreach(tabItem.setToolTipText)
        newView.image.foreach(tabItem.setImage)
      })
    }
    val viewName = ViewLayer.id + "_%08X".format(viewConfiguration.id.hashCode())
    log.debug(s"Create new view layer ${viewName}.")
    val viewRef = ss.context.actorOf(ViewLayer.props.copy(args = immutable.Seq(viewConfiguration.id, ss.parentContext)), viewName)
    // Block builder until the view is created.
    implicit val sender = tabStack.ref
    Await.result(ask(viewRef, App.Message.Create(Left(ViewLayer.<>(viewConfiguration,
      parentWidget, ss.parentContext))))(Timeout.short), Timeout.short) match {
      case App.Message.Create(Right(viewWidget: SComposite), None) =>
        log.debug(s"View layer ${viewConfiguration} content created.")
        Some(viewWidget)
      case App.Message.Error(error, None) =>
        log.fatal(s"Unable to create content for view layer ${viewConfiguration}: ${error}.")
        None
      case _ =>
        log.fatal(s"Unable to create content for view layer ${viewConfiguration}.")
        None
    }
  }
}

object TransformAttachView {
  implicit def transform2implementation(t: TransformAttachView.type): TransformAttachView = inner

  def inner(): TransformAttachView = DI.implementation

  /**
   * Dependency injection routines
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** TransformAttachView implementation */
    lazy val implementation = injectOptional[TransformAttachView] getOrElse new TransformAttachView
  }
}
