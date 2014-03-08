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
import java.util.UUID
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.definition.Context
import org.digimead.tabuddy.desktop.core.definition.command.Command
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.support.Timeout
import org.digimead.tabuddy.desktop.core.ui.UI
import org.digimead.tabuddy.desktop.core.ui.block.{ Configuration, View }
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
class ViewContentBuilder extends VComposite.ContextSetter with Loggable {
  /** Akka communication timeout. */
  implicit val timeout = akka.util.Timeout(UI.communicationTimeout)

  /**
   * Create view container actor.
   * Must not be UI thread.
   *
   * @param configuration view configuration
   * @param pWidget parent widget
   * @param pAppContext parent AppContext
   * @param parentActorContext parent ActorContext
   * @return Option[VComposite]
   */
  def container(configuration: Configuration.CView, pWidget: ScrolledComposite, pAppContext: Context,
    parentActorContext: ActorContext, content: Option[VComposite]): Option[VComposite] = {
    val viewName = View.name(configuration.id)
    log.debug(s"Build ${configuration}.")
    App.assertEventThread(false)
    val viewContext = pAppContext.createChild(viewName): Context.Rich
    val resultOfAddToContext = try { Command.addToContext(viewContext, CommandViewClose.parser); None }
    catch { case e: IllegalArgumentException ⇒ Some(e) }
    // Application may be closed
    resultOfAddToContext.foreach { error ⇒
      log.debug("Cancel view creation: " + error.getMessage())
      pAppContext.removeChild(viewContext)
      viewContext.dispose()
      return None
    }
    val view = parentActorContext.actorOf(View.props.copy(args = immutable.Seq(configuration.id, viewContext: Context.Rich)), viewName)
    // Block until view is created.
    Await.result(view ? App.Message.Create(View.<>(configuration, pWidget, content), parentActorContext.self), timeout.duration) match {
      case App.Message.Create(viewWidget: VComposite, Some(origin), _) ⇒
        Some(viewWidget)
      case App.Message.Error(error, None) ⇒
        log.fatal(s"Unable to create ${configuration}: ${error.getOrElse("unknown")}.")
        None
      case error ⇒
        App.exec {
          if (pWidget.isDisposed())
            log.debug(s"Unable to create ${configuration}: ${error}.")
          else
            log.fatal(s"Unable to create ${configuration}: ${error}.")
        }
        parentActorContext.stop(view)
        None
    }
  }
  /**
   * Create view content actor.
   * Must not be UI thread.
   *
   * @param configuration view configuration
   * @param pWidget parent widget
   * @param context view AppContext
   * @param parentActorContext parent ActorContext
   * @param content exists content that is replaced with the new one
   * @return Option[VComposite]
   */
  def content(configuration: Configuration.CView, pWidget: ScrolledComposite, context: Context,
    parentActorContext: ActorContext, content: Option[VComposite]): Option[VComposite] = {
    log.debug(s"Build content for ${configuration}.")
    val actualViewActorRef = content.map(_.contentRef) orElse configuration.factory().viewActor(parentActorContext.self, configuration)
    App.assertEventThread(false)
    // Create view widget.
    val viewWidget: Option[VComposite] = App.execNGet {
      if (pWidget.isDisposed())
        None // container was disposed immediately
      else if (pWidget.getLayout().isInstanceOf[GridLayout])
        throw new IllegalArgumentException(s"Unexpected parent layout ${pWidget.getLayout().getClass()}.")
      else
        actualViewActorRef match {
          case Some(actualViewActorRef) ⇒
            val content = new VComposite(configuration.id, parentActorContext.self, actualViewActorRef, configuration.factory, pWidget, SWT.NONE)
            setVCompositeContext(content, context)
            // Set the specific widget
            context.set(classOf[VComposite], content)
            // Set the common top level widget
            context.set(classOf[Composite], content)
            //          content.setBackground(App.display.getSystemColor(SWT.COLOR_CYAN))
            pWidget.setContent(content)
            pWidget.setMinSize(content.computeSize(SWT.DEFAULT, SWT.DEFAULT))
            pWidget.setExpandHorizontal(true)
            pWidget.setExpandVertical(true)
            pWidget.layout(Array[Control](content), SWT.ALL)
            Some(content)
          case None ⇒
            // TODO destroy
            log.fatal("Unable to locate actual view actor.")
            None
        }
    }
    // Create widget content
    viewWidget.flatMap { widget ⇒
      content match {
        case Some(existsWidget) ⇒
          // Move exists content from existsWidget to widget.
          assert(widget.contentRef == existsWidget.contentRef)
          assert(widget != existsWidget)
          log.debug(s"Move ${configuration} from the old ${existsWidget} to the new ${widget}.")
          val successful = App.execNGet {
            val successful = !existsWidget.isDisposed() && existsWidget.getChildren().forall(_.setParent(widget))
            if (successful)
              widget.setLayout(existsWidget.getLayout())
            successful
          }
          if (successful) {
            widget.contentRef ! App.Message.Set(widget)
            Some(widget)
          } else {
            App.exec {
              if (existsWidget.isDisposed())
                log.debug(s"Unable to change parent widget for - in " + existsWidget)
              else
                log.fatal(s"Unable to change parent widget for ${existsWidget.getChildren().mkString(", ")}")
            }
            None
          }
        case None ⇒
          // Create new content.
          widget.contentRef ! App.Message.Set(widget)
          // Ask widget.contentRef to create it
          Await.result(widget.contentRef ? App.Message.Create(widget, parentActorContext.self), timeout.duration) match {
            case App.Message.Create(contentContainerWidget, Some(widget.contentRef), _) ⇒
              log.debug(s"${configuration} content is created.")
              if (contentContainerWidget.isInstanceOf[SComposite])
                // SComposite matching is involved in GUI hierarchy creation.
                // This view will overwrite exists hierarchy element like View with the same ID.
                log.fatal(s"View ${configuration} is broken. View container is ${contentContainerWidget}.")
              viewWidget
            case App.Message.Error(error, _) ⇒
              log.fatal(s"Unable to create content for view layer ${configuration}: ${error.getOrElse("unknown")}.")
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
