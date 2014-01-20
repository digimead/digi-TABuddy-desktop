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

import akka.actor.{ ActorContext, ActorRef }
import akka.pattern.ask
import akka.util.Timeout.durationToTimeout
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.definition.Context
import org.digimead.tabuddy.desktop.core.definition.command.Command
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.support.Timeout
import org.digimead.tabuddy.desktop.core.ui.block.{ Configuration, ViewLayer }
import org.digimead.tabuddy.desktop.core.ui.command.view.CommandViewClose
import org.digimead.tabuddy.desktop.core.ui.definition.widget.{ SComposite, VComposite }
import org.eclipse.swt.SWT
import org.eclipse.swt.custom.ScrolledComposite
import org.eclipse.swt.events.{ DisposeEvent, DisposeListener }
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.widgets.{ Composite, Control }
import scala.collection.immutable
import scala.concurrent.Await
import scala.language.implicitConversions

/**
 * Create initial view content.
 */
class ViewContentBuilder extends Loggable {
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
  def apply(configuration: Configuration.View, pWidget: ScrolledComposite, pEContext: Context.Rich, pAContext: ActorContext): Option[VComposite] = {
    val viewName = ViewLayer.id + "_%08X".format(configuration.id.hashCode())
    log.debug(s"Build view layer ${viewName}.")
    App.assertEventThread(false)
    val viewContext = pEContext.createChild(VComposite.contextName): Context.Rich
    Command.addToContext(viewContext, CommandViewClose.parser)
    val view = pAContext.actorOf(ViewLayer.props.copy(args = immutable.Seq(configuration.id, viewContext)), viewName)
    // Block until view is created.
    implicit val sender = pAContext.self
    Await.result(ask(view, App.Message.Create(Left(ViewLayer.<>(configuration, pWidget))))(Timeout.short), Timeout.short) match {
      case App.Message.Create(Right(viewWidget: VComposite), None) ⇒
        log.debug(s"View layer ${configuration} content created.")
        Some(viewWidget)
      case App.Message.Error(error, None) ⇒
        log.fatal(s"Unable to create content for view layer ${configuration}: ${error}.")
        None
      case _ ⇒
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
  def apply(configuration: Configuration.View, ref: ActorRef, context: Context, pWidget: ScrolledComposite): Option[VComposite] = {
    log.debug(s"Build content for ${configuration}.")
    App.assertEventThread(false)
    // Create view widget.
    val viewWidget: Option[VComposite] = App.execNGet {
      if (pWidget.getLayout().isInstanceOf[GridLayout])
        throw new IllegalArgumentException(s"Unexpected parent layout ${pWidget.getLayout().getClass()}.")
      configuration.factory().viewActor(configuration) match {
        case Some(actualViewActorRef) ⇒
          val content = new VComposite(configuration.id, ref, actualViewActorRef, configuration.factory, pWidget, SWT.NONE)
          context.set(classOf[Composite], content)
          content.setData(App.widgetContextKey, context)
          //          content.setBackground(App.display.getSystemColor(SWT.COLOR_CYAN))
          pWidget.setContent(content)
          pWidget.setMinSize(content.computeSize(SWT.DEFAULT, SWT.DEFAULT))
          pWidget.setExpandHorizontal(true)
          pWidget.setExpandVertical(true)
          pWidget.layout(Array[Control](content), SWT.ALL)
          content.addDisposeListener(new DisposeListener {
            def widgetDisposed(event: DisposeEvent) = Option(content.getData(App.widgetContextKey)).foreach {
              case context: Context ⇒
                context.remove(classOf[Composite])
                content.setData(App.widgetContextKey, null)
            }
          })
          Some(content)
        case None ⇒
          // TODO destroy
          log.fatal("Unable to locate actual view actor.")
          None
      }
    }
    // Create widget content
    viewWidget.flatMap { widget ⇒
      // Ask widget.contentRef to create it
      Await.result(ask(widget.contentRef, App.Message.Create(Left(widget)))(Timeout.short), Timeout.short) match {
        case App.Message.Create(Right(contentContainerWidget), None) ⇒
          log.debug(s"View layer ${configuration} content created.")
          if (contentContainerWidget.isInstanceOf[SComposite])
            // SComposite matching is involved in GUI hierarchy creation.
            // This view will overwrite exists hierarchy element like ViewLayer with the same ID.
            log.fatal(s"View ${configuration} is broken. View container is ${contentContainerWidget}.")
          viewWidget
        case App.Message.Error(error, None) ⇒
          log.fatal(s"Unable to create content for view layer ${configuration}: ${error}.")
          viewWidget.foreach(widget ⇒ App.execNGet { widget.dispose })
          None
        case _ ⇒
          log.fatal(s"Unable to create content for view layer ${configuration}.")
          viewWidget.foreach(widget ⇒ App.execNGet { widget.dispose })
          None
      }
    }
  }
}

object ViewContentBuilder {
  implicit def builder2implementation(c: ViewContentBuilder.type): ViewContentBuilder = c.inner

  /** ViewContentBuilder implementation. */
  def inner = DI.implementation

  /**
   * Dependency injection routines.
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** ViewContentBuilder implementation. */
    lazy val implementation = injectOptional[ViewContentBuilder] getOrElse new ViewContentBuilder
  }
}
