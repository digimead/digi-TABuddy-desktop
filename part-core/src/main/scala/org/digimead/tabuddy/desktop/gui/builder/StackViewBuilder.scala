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

package org.digimead.tabuddy.desktop.gui.builder

import scala.collection.immutable
import scala.concurrent.Await

import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.gui.Configuration
import org.digimead.tabuddy.desktop.gui.ViewLayer
import org.digimead.tabuddy.desktop.gui.widget.VComposite
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.App.app2implementation
import org.digimead.tabuddy.desktop.support.AppContext
import org.digimead.tabuddy.desktop.support.Timeout
import org.eclipse.swt.SWT
import org.eclipse.swt.custom.ScrolledComposite
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.widgets.Control

import akka.actor.ActorContext
import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout.durationToTimeout

import language.implicitConversions

class StackViewBuilder extends Loggable {
  /**
   * Build view actor and get actor content.
   * Must not be UI thread.
   *
   * @param configuration view configuration
   * @param pWidget parent widget
   * @param pEContext parent AppContext
   * @param pAContext parent ActorContext
   * @return Option[VComposite]
   */
  def apply(configuration: Configuration.View, pWidget: ScrolledComposite, pEContext: AppContext.Rich, pAContext: ActorContext): Option[VComposite] = {
    val viewName = ViewLayer.id + "_%08X".format(configuration.id.hashCode())
    log.debug(s"Build view layer ${viewName}.")
    App.assertUIThread(false)
    val viewContext = pEContext.createChild("Context_" + viewName): AppContext.Rich
    val view = pAContext.actorOf(ViewLayer.props.copy(args = immutable.Seq(configuration.id, viewContext)), viewName)
    // Block until view is created.
    implicit val sender = pAContext.self
    Await.result(ask(view, App.Message.Create(Left(ViewLayer.<>(configuration, pWidget))))(Timeout.short), Timeout.short) match {
      case App.Message.Create(Right(viewWidget: VComposite), None) =>
        log.debug(s"View layer ${configuration} content created.")
        Some(viewWidget)
      case App.Message.Error(error, None) =>
        log.fatal(s"Unable to create content for view layer ${configuration}: ${error}.")
        None
      case _ =>
        log.fatal(s"Unable to create content for view layer ${configuration}.")
        None
    }
  }
  /**
   * Build actor content.
   * Must not be UI thread.
   *
   * @param configuration view configuration
   * @param ref view actor reference
   * @param context view AppContext
   * @param pWidget parent widget
   * @return Option[VComposite]
   */
  def apply(configuration: Configuration.View, ref: ActorRef, context: AppContext, pWidget: ScrolledComposite): Option[VComposite] = {
    log.debug(s"Build content for ${configuration}.")
    App.assertUIThread(false)
    // Create view widget.
    val viewWidget = App.execNGet {
      if (pWidget.getLayout().isInstanceOf[GridLayout])
        throw new IllegalArgumentException(s"Unexpected parent layout ${pWidget.getLayout().getClass()}.")
      configuration.factory().viewActor(configuration) match {
        case Some(actualViewActorRef) =>
          val layout = new GridLayout
          layout.marginHeight = 0
          layout.marginWidth = 0
          val content = new VComposite(configuration.id, ref, actualViewActorRef, configuration.factory, pWidget, SWT.NONE)
          content.setData(App.widgetContextKey, context)
          content.setLayout(layout)
          content.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1))
          content.setBackground(App.display.getSystemColor(SWT.COLOR_CYAN))
          content.pack(true)
          pWidget.setContent(content)
          pWidget.setMinSize(content.computeSize(SWT.DEFAULT, SWT.DEFAULT))
          pWidget.setExpandHorizontal(true)
          pWidget.setExpandVertical(true)
          pWidget.layout(Array[Control](content), SWT.ALL)
          Some(content)
        case None =>
          // TODO destroy
          log.fatal("Unable to locate actual view actor.")
          None
      }
    }
    // Create widget content
    viewWidget.flatMap { widget =>
      Await.result(ask(widget.contentRef, App.Message.Create(Left(widget)))(Timeout.short), Timeout.short) match {
        case App.Message.Create(Right(contentContainerWidget), None) =>
          log.debug(s"View layer ${configuration} content created.")
          viewWidget
        case App.Message.Error(error, None) =>
          log.fatal(s"Unable to create content for view layer ${configuration}: ${error}.")
          viewWidget.foreach(widget => App.execNGet { widget.dispose })
          None
        case _ =>
          log.fatal(s"Unable to create content for view layer ${configuration}.")
          viewWidget.foreach(widget => App.execNGet { widget.dispose })
          None
      }
    }
  }
}

object StackViewBuilder {
  implicit def builder2implementation(c: StackViewBuilder.type): StackViewBuilder = c.inner

  /** StackViewBuilder implementation. */
  def inner = DI.implementation

  /**
   * Dependency injection routines.
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** StackViewBuilder implementation. */
    lazy val implementation = injectOptional[StackViewBuilder] getOrElse new StackViewBuilder
  }
}
