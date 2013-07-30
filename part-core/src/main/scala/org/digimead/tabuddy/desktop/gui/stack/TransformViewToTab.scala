package org.digimead.tabuddy.desktop.gui.stack

import org.digimead.digi.lib.api.DependencyInjection
import language.implicitConversions
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.gui.StackSupervisor
import org.eclipse.swt.widgets.Shell
import org.digimead.tabuddy.desktop.gui.Configuration
import org.digimead.tabuddy.desktop.support.Timeout
import org.digimead.tabuddy.desktop.gui.StackLayer
import org.eclipse.swt.SWT

/** Wrap view with tab stack. */
class TransformViewToTab extends Loggable {
  def apply(stackSupervisorIternals: StackSupervisor, view: VComposite): Option[SCompositeTab] = {
    log.debug(s"Move ${view} to tab stack container.")
    App.checkThread
    val hierarchy = App.widgetHierarchy(view)
    if (hierarchy.headOption != Some(view) || hierarchy.size < 2)
      throw new IllegalStateException(s"Illegal hierarchy ${hierarchy}.")
    hierarchy(1) match {
      case tab: SCompositeTab =>
        log.debug(s"View ${view} is already wrapped with tab ${tab}.")
        Option(tab)
      case other: Shell =>
        val tabParentWidget = view.getParent
        val viewConfiguration = stackSupervisorIternals.configurationMap(view.id).asInstanceOf[Configuration.View]
        val tabConfiguration = Configuration.Stack.Tab(Seq(viewConfiguration))
        log.debug(s"Reconfigure stack hierarchy. Bind ${tabConfiguration} to ${other}.")
        val stackRef = stackSupervisorIternals.context.actorOf(StackLayer.props, StackLayer.id + "@" + tabConfiguration.id.toString())
        val (tabComposite, containers) = StackTabBuilder(tabConfiguration, tabParentWidget, stackRef)
        val firstTab = containers.head
        if (!view.setParent(firstTab)) {
          log.fatal(s"Unable to change parent for ${view}.")
          tabComposite.dispose()
          None
        } else {
          firstTab.setContent(view)
          firstTab.setMinSize(view.computeSize(SWT.DEFAULT, SWT.DEFAULT))
          tabParentWidget.setContent(tabComposite)
          tabParentWidget.setMinSize(tabComposite.computeSize(SWT.DEFAULT, SWT.DEFAULT))
          tabParentWidget.layout(true)
          App.publish(App.Message.Created(tabComposite, stackRef))
          Option(tabComposite)
        }
      case unexpected =>
        throw new IllegalStateException(s"Unexpected parent element ${unexpected}.")
    }
  }
}

object TransformViewToTab {
  implicit def transform2implementation(t: TransformViewToTab.type): TransformViewToTab = inner

  def inner(): TransformViewToTab = DI.implementation

  /**
   * Dependency injection routines
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** TransformAttachView implementation */
    lazy val implementation = injectOptional[TransformViewToTab] getOrElse new TransformViewToTab
  }
}