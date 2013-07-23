package org.digimead.tabuddy.desktop.menu

class ShowView(id: String) extends org.eclipse.ui.internal.ShowViewMenu(org.eclipse.ui.PlatformUI.getWorkbench().getActiveWorkbenchWindow(), id) {
  def this() = this("org.digimead.tabuddy.desktop.menu.dynamicShowView")
}