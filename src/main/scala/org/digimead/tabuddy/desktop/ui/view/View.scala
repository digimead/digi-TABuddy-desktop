/**
 * This file is part of the TABuddy project.
 * Copyright (c) 2012-2013 Alexey Aksenov ezh@ezh.msk.ru
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

package org.digimead.tabuddy.desktop.ui.view

import scala.collection.mutable.Publisher
import org.digimead.configgy.Configgy
import org.digimead.configgy.Configgy.getImplementation
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.log.Loggable
import org.digimead.digi.lib.log.logger.RichLogger.rich2slf4j
import org.digimead.tabuddy.desktop.Main
import org.digimead.tabuddy.desktop.support.WritableValue
import org.digimead.tabuddy.desktop.ui.Window
import org.digimead.tabuddy.desktop.ui.Window.instance2object
import org.eclipse.swt.SWT
import org.eclipse.swt.custom.StackLayout
import org.eclipse.swt.widgets.Composite
import language.implicitConversions
import org.digimead.tabuddy.desktop.ui.view.tree.Tree

/**
 * View trait from the perspective of singletons
 */
trait View extends Loggable {
  this: Composite =>
  val title: String
  val description: String

  /** hide current view */
  def hide(): Boolean = View.hide(this)
  /** onLoad event */
  def onLoad(): Boolean = {
    log.debug("load view \"%s\", \"%s\"".format(title, description))
    true
  }
  /** onSave event */
  def onSave(): Boolean = {
    log.debug("save view \"%s\", \"%s\"".format(title, description))
    true
  }
  /** global show method */
  def show(): Boolean = View.show(this)
}

object View extends Loggable {
  implicit def view2list(v: View.type): Seq[View] = viewList
  @volatile private var viewList = Seq[View]()
  /** previous application active view */
  @volatile private var previousView: Option[View] = None
  /** current application active view */
  val currentView = WritableValue[Option[View]](None)
  /** stub view */
  lazy val stub = new Stub()

  def apply() = stub
  @log
  def hide(): Boolean = currentView.value match {
    case Some(view) =>
      hide(view)
    case None =>
      log.warn("there is no active view")
      false
  }
  @log
  def hide(view: View): Boolean = synchronized {
    log.debug("hide view \"%s\", \"%s\"".format(view.title, view.description))
    if (Some(view) == currentView.value) {
      view match {
        case view: Composite =>
          view.getParent().getLayout() match {
            case layout: StackLayout =>
              previousView match {
                case Some(view) =>
                  log.debug("hide view \"%s\", \"%s\"".format(view.title, view.description))
                  show(view) // flip flop
                case None =>
                  log.debug("hide view \"%s\", \"%s\"".format(stub.title, stub.description))
                  show(stub)
              }
            case layout =>
              log.error("unexpected view container layout " + layout)
              false
          }
        case view =>
          log.error("unknown view type " + view)
          false
      }
    } else {
      log.warn("view \"%s\", \"%s\" already hidden".format(view.title, view.description))
      false
    }
  }
  @log
  def load() = synchronized {
    log.info("loading application views...")
    val view = availableViews
    val saved = Configgy.getString("view.selected", classOf[Default].getName())
    viewList = view.filter(_.onLoad())
    val selected = if (saved.nonEmpty) viewList.find(_.getClass().getName() == saved) else None
    selected.orElse(viewList.headOption).foreach(v => show(v))
    Event.publish(Event.ViewListChanged)
  }
  def previous() = previousView
  @log
  def save = synchronized {
    log.info("saving application views...")
    viewList.filter(_.onSave())
  }
  @log
  def show(view: View): Boolean = {
    val result = synchronized {
      view match {
        case view: Composite if Some(view) != currentView =>
          view.getParent().getLayout() match {
            case layout: StackLayout =>
              log.debug("show view \"%s\", \"%s\"".format(view.title, view.description))
              Main.exec {
                layout.topControl = view
                view.getParent().layout()
              }
              previousView = currentView.value
              currentView.value = if (view != stub) {
                Configgy("view.selected") = view.getClass().getName()
                Some(view)
              } else {
                Configgy("view.selected") = ""
                None
              }
              true
            case layout =>
              log.error("unexpected view container layout " + layout)
              false
          }
        case view: Composite =>
          log.warn("view \"%s\", \"%s\" already active".format(view.title, view.description))
          true
        case view =>
          log.error("unknown view type " + view)
          false
      }
    }
    if (result)
      Event.publish(Event.ViewChanged(previousView, currentView.value))
    result
  }
  /** list of available views */
  private def availableViews = Seq[View](new Default(Window.getContainer, SWT.NONE), Tree(Window.getContainer, SWT.NONE))

  sealed trait Event
  object Event extends Publisher[Event] {
    /** Event fired when the viewList modified */
    case object ViewListChanged extends Event
    /** Event fired when the currentView modified */
    case class ViewChanged(before: Option[View], after: Option[View]) extends Event

    override protected[View] def publish(event: Event) = try {
      super.publish(event)
    } catch {
      // catch all subscriber exceptions
      case e: Throwable =>
        log.error(e.getMessage(), e)
    }
  }
  class Stub extends Composite(Window.getContainer, SWT.NONE) with View {
    val title: String = "empty view"
    val description: String = "empty view"
  }
}
