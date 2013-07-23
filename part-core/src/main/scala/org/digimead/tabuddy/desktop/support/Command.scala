package org.digimead.tabuddy.desktop.support

import scala.collection.JavaConversions._
import org.eclipse.ui.internal.WorkbenchWindow
import org.eclipse.ui.commands.ICommandService

abstract class Command(id: String) {
  def enable(window: WorkbenchWindow) =
    Option(window.getService(classOf[ICommandService]).asInstanceOf[ICommandService]).foreach { service =>
      service.getCommand(id).setEnabled(null)
    }
  def refresh(window: WorkbenchWindow, filter: Option[Map[_, _]] = None) =
    Option(window.getService(classOf[ICommandService]).asInstanceOf[ICommandService]).
      foreach(_.refreshElements(id, filter.getOrElse(null)))
  def disable(window: WorkbenchWindow) =
    Option(window.getService(classOf[ICommandService]).asInstanceOf[ICommandService]).foreach { service =>
      service.getCommand(id).setEnabled(null)
    }
}
