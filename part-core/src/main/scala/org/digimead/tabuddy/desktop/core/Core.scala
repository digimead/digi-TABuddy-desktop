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

package org.digimead.tabuddy.desktop.core

import akka.actor.{ ActorRef, OneForOneStrategy, Props, ScalaActorRef, UnhandledMessage }
import akka.actor.SupervisorStrategy.Resume
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import org.digimead.digi.lib.Disposable
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.XDependencyInjection
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.api.XMain
import org.digimead.tabuddy.desktop.core.console.Console
import org.digimead.tabuddy.desktop.core.definition.{ NLS, Operation }
import org.digimead.tabuddy.desktop.core.definition.Context
import org.digimead.tabuddy.desktop.core.definition.api.XOperationApprover
import org.digimead.tabuddy.desktop.core.support.App
import org.eclipse.core.commands.CommandManager
import org.eclipse.core.commands.contexts.ContextManager
import org.eclipse.core.runtime.{ IExtensionRegistry, RegistryFactory }
import org.eclipse.e4.core.commands.{ ECommandService, EHandlerService }
import org.eclipse.e4.core.commands.internal.{ CommandServiceImpl, HandlerServiceCreationFunction }
import org.eclipse.e4.core.contexts.{ ContextInjectionFactory, IEclipseContext }
import org.eclipse.e4.core.services.events.IEventBroker
import org.eclipse.e4.ui.bindings.EBindingService
import org.eclipse.e4.ui.bindings.internal.{ BindingServiceCreationFunction, BindingTableManager }
import org.eclipse.e4.ui.internal.services.ContextContextFunction
import org.eclipse.e4.ui.model.application.MApplication
import org.eclipse.e4.ui.model.application.ui.basic.MWindow
import org.eclipse.e4.ui.services.EContextService
import org.eclipse.e4.ui.workbench.IPresentationEngine
import org.eclipse.jface.bindings.BindingManager
import org.eclipse.swt.widgets.Display
import org.eclipse.ui.PlatformUI
import org.eclipse.ui.application.WorkbenchAdvisor
import org.eclipse.ui.internal.Workbench
import org.eclipse.ui.internal.services.SourceProviderService
import org.eclipse.ui.services.ISourceProviderService
import org.osgi.framework.{ BundleContext, BundleEvent, BundleListener, ServiceRegistration }
import scala.language.implicitConversions

/**
 * Root actor of the Core component.
 */
class Core extends RootActor with XLoggable {
  /** Akka execution context. */
  implicit lazy val ec = App.system.dispatcher
  /** Inconsistent elements. */
  protected var inconsistentSet = Set[AnyRef](Console, Core)
  /** Application entry point registration. */
  @volatile protected var mainRegistration: Option[ServiceRegistration[XMain]] = None
  /** Start/stop initialization lock. */
  private val initializationLock = new Object
  log.debug("Start actor " + self.path)

  /** Console actor. */
  lazy val consoleRef = context.actorOf(console.Console.props, console.Console.id)

  /*
   *
   * Core started if:
   * 	Activator is started
   * 	This actor is started
   * 	And someone starts event loop (from application service or directly)
   *
   */
  if (App.watch(Activator, EventLoop, this).hooks.isEmpty)
    App.watch(Activator, EventLoop, this).always().
      makeAfterStart('core_Core__onAppStarted) { onAppStarted() }.
      makeBeforeStop('core_Core__onAppStopped) { onAppStopped() }.sync()

  /** Is called asynchronously after 'actor.stop()' is invoked. */
  override def postStop() = {
    App.system.eventStream.unsubscribe(self, classOf[App.Message.Consistent[_]])
    App.system.eventStream.unsubscribe(self, classOf[App.Message.Inconsistent[_]])
    App.system.eventStream.unsubscribe(self, classOf[UnhandledMessage])
    // Stop "main" service.
    mainRegistration.foreach { serviceRegistration ⇒
      log.debug("Unregister TA Buddy Desktop application entry point service.")
      serviceRegistration.unregister()
    }
    mainRegistration = None
    App.watch(this) off ()
    log.debug(self.path.name + " actor is stopped.")
  }
  /** Is called when an Actor is started. */
  override def preStart() {
    App.system.eventStream.subscribe(self, classOf[UnhandledMessage])
    App.system.eventStream.subscribe(self, classOf[App.Message.Inconsistent[_]])
    App.system.eventStream.subscribe(self, classOf[App.Message.Consistent[_]])
    App.watch(this) on ()
    log.debug(self.path.name + " actor is started.")
  }
  def receive = {
    case message @ App.Message.Attach(props, name, _) ⇒ App.traceMessage(message) {
      sender ! context.actorOf(props, name)
    }

    case message @ App.Message.Consistent(element, from, _) if from != Some(self) ⇒ App.traceMessage(message) {
      if (inconsistentSet.nonEmpty) {
        inconsistentSet = inconsistentSet - element
        if (inconsistentSet.isEmpty) {
          log.debug("Return integrity.")
          context.system.eventStream.publish(App.Message.Consistent(Core, self))
        }
      } else
        log.debug(s"Skip message ${message}. Core is already consistent.")
    }

    case message @ App.Message.Inconsistent(element, from, _) if from != Some(self) ⇒ App.traceMessage(message) {
      if (inconsistentSet.isEmpty) {
        log.debug("Lost consistency.")
        context.system.eventStream.publish(App.Message.Inconsistent(Core, self))
      }
      inconsistentSet = inconsistentSet + element
    }

    case message @ App.Message.Consistency(set, from, _) if set.isEmpty ⇒ App.traceMessage(message) {
      from.foreach(_ ! App.Message.Consistency(inconsistentSet, Some(self)))
    }

    case message @ App.Message.Consistent(_, _, _) ⇒ // skip
    case message @ App.Message.Inconsistent(_, _, _) ⇒ // skip

    case message: BundleContext ⇒ App.traceMessage(message) { main(message) }

    case UnhandledMessage(message: App.Message, sender, self) if message.source != null ⇒
      log.error(s"Received unexpected message '${sender}' -> '${self}': '${message}'", message.source)
    case UnhandledMessage(message, sender, self) ⇒
      log.fatal(s"Received unexpected message '${sender}' -> '${self}': '${message}'")
  }
  override val supervisorStrategy = OneForOneStrategy(maxNrOfRetries = 2) {
    case e: Exception ⇒
      log.error(Option(e.getMessage).getOrElse("- No message -"), e)
      Resume
  }

  /** Starts main service when OSGi environment will be stable. */
  @log
  protected def main(context: BundleContext) = try {
    val lastEventTS = new AtomicLong(System.currentTimeMillis())
    val listener = new BundleListener {
      def bundleChanged(event: BundleEvent) = lastEventTS.set(System.currentTimeMillis())
    }
    context.addBundleListener(listener)
    // OSGi is infinite black box of bundles
    // waiting for stabilization
    log.debug("Waiting OSGi for stabilization.")
    val frame = 400 // 0.4s for decision
    while ((System.currentTimeMillis - lastEventTS.get()) < frame)
      Thread.sleep(100) // 0.1s for iteration
    log.debug("OSGi stabilization is achieved.")
    context.removeBundleListener(listener)
    // Start "main" service
    mainRegistration = Option(context.registerService(classOf[XMain], AppService, null))
    mainRegistration match {
      case Some(service) ⇒ log.debug("Register TA Buddy Desktop application entry point service as: " + service)
      case None ⇒ log.error("Unable to register TA Buddy Desktop application entry point service.")
    }
    // Initialize lazy actors before application will be started.
    Core.actor
    consoleRef
    self ! App.Message.Consistent(Core, None)
  } finally App.watch(context).on() // Send notice to Activator that initialization is complete.
  /** Invoked when application started. */
  protected def onAppStarted(): Unit = initializationLock.synchronized {
    App.watch(Core) on {
      self ! App.Message.Inconsistent(Core, None)
      App.verifyApplicationEnvironment()
      // Wait for translationService
      NLS.translationService
      // Translate all messages
      Messages
      if (App.isUIAvailable) {
        log.info("Start application with GUI.")
        Core.DI.approvers.foreach(Operation.history.addOperationApprover)
      } else
        log.info("Start application without GUI.")
      try startWorkbench() catch {
        case e: Throwable ⇒
          log.error("Unable to start workbench: " + e.getMessage, e)
          throw e
      }
      App.execNGet { Preferences.start(App.bundle(getClass).getBundleContext()) }(App.LongRunnable)
      command.Commands.configure()
      Console ! Console.Message.Notice("\n" + Console.welcomeMessage())
      Console ! App.Message.Start(Console, None)
      Console ! Console.Message.Notice("Core component is started.")
      self ! App.Message.Consistent(Core, None)
    }
  }
  /** Invoked when application stopped. */
  protected def onAppStopped(): Unit = initializationLock.synchronized {
    App.watch(Core) off {
      self ! App.Message.Inconsistent(Core, None)
      command.Commands.unconfigure()
      App.execNGet { Preferences.stop(App.bundle(getClass).getBundleContext()) }
      if (App.isUIAvailable)
        Core.DI.approvers.foreach(Operation.history.removeOperationApprover)
      val lost = inconsistentSet - Core
      if (lost.nonEmpty)
        log.debug("Inconsistent elements: " + lost)
      try stopWorkbench() catch { case e: Throwable ⇒ log.error("Unable to stop workbench: " + e.getMessage, e) }
      Console ! Console.Message.Notice("Core component is stopped.")
    }
  }
  /** Start platform workbench. */
  protected def startWorkbench() = {
    log.debug("Start workbench.")
    /*
     * BLAME FOR PLATFORM DEVELOPERS. KILL'EM ALL :-/ HATE THOSE MONKEYS
     * So ugly code :-/ Awesome... :-(
     * Reassign a new instance of Workbench after each restart.
     * This is a memory leak, but only in development mode.
     */
    // I tried adjust environment via custom IApplication -
    //   waste of time: hard coded constants... final classes... initialization in static blocks...
    val oldWorkbench = try Option(PlatformUI.getWorkbench())
    catch { case e: IllegalStateException ⇒ None }
    val constructor = classOf[Workbench].getDeclaredConstructor(classOf[Display], classOf[WorkbenchAdvisor], classOf[MApplication], classOf[IEclipseContext])
    if (!constructor.isAccessible())
      constructor.setAccessible(true)
    val instance = classOf[Workbench].getDeclaredField("instance")
    if (!instance.isAccessible())
      instance.setAccessible(true)
    // We may but not use E4Application.createDefaultContext() or E4Application.createDefaultHeadlessContext()
    val workbenchContext = Core.serviceContext.createChild("workbench")
    val mApplication = new Core.ApplicationStub(workbenchContext)
    workbenchContext.set(classOf[BindingTableManager], ContextInjectionFactory.make(classOf[BindingTableManager], workbenchContext))
    workbenchContext.set(classOf[CommandManager], new CommandManager())
    workbenchContext.set(classOf[ContextManager], new ContextManager())
    workbenchContext.set(classOf[EBindingService].getName(), new BindingServiceCreationFunction())
    workbenchContext.set(classOf[ECommandService], ContextInjectionFactory.make(classOf[CommandServiceImpl], workbenchContext))
    workbenchContext.set(classOf[EContextService].getName(), new ContextContextFunction())
    workbenchContext.set(classOf[EHandlerService].getName(), new HandlerServiceCreationFunction())
    // IEventBroker are useless part in this application. Actually, as most of the workbench...
    workbenchContext.set(classOf[IEventBroker], new Core.EventBrokerStub)
    workbenchContext.set(classOf[IExtensionRegistry], RegistryFactory.getRegistry())
    workbenchContext.set(classOf[IPresentationEngine], new Core.PresentationEngineStub())
    workbenchContext.set(classOf[MApplication], mApplication)
    workbenchContext.set(classOf[BindingManager], new BindingManager(workbenchContext.get(classOf[ContextManager]), workbenchContext.get(classOf[CommandManager])))
    val workbench = App.execNGet {
      instance.set(null, null)
      oldWorkbench.foreach(Disposable.clean)
      Core.DI.beforeWorkbenchCreate.foreach(_())
      val workbench = constructor.newInstance(App.display, new WorkbenchAdvisor {
        def getInitialWindowPerspectiveId() = ""
      }, mApplication, workbenchContext)
      instance.set(null, workbench)
      workbench
    }
    // init
    val initMethod = classOf[Workbench].getDeclaredMethod("init")
    if (!initMethod.isAccessible())
      initMethod.setAccessible(true)
    App.execNGet {
      //
      // This block may running more than 1 second
      //
      Core.DI.beforeWorkbenchInit.foreach(_())
      initMethod.invoke(workbench)
      // adjust
      workbench.getService(classOf[ISourceProviderService]) match {
        case sourceProviderService: SourceProviderService ⇒
          val providers = sourceProviderService.getSourceProviders()
          providers.foreach { provider ⇒
            sourceProviderService.unregisterProvider(provider)
            provider.dispose()
          }
        case _ ⇒
      }
      Core.DI.afterWorkbenchInit.foreach(_())
    }(App.LongRunnable)
    assert(PlatformUI.isWorkbenchRunning())
  }
  /** Stop platform workbench. */
  protected def stopWorkbench() = App.execNGet {
    Option(Workbench.getInstance()).foreach { workbench ⇒
      log.debug("Stop workbench.")
      // YUP. Another flag that indicates code quality.
      // Workbench may be closed only once. What is about reopen? Fucking morons... Architects...
      if (!App.isDevelopmentMode)
        workbench.close()
      val instance = classOf[Workbench].getDeclaredField("instance")
      if (!instance.isAccessible())
        instance.setAccessible(true)
      instance.set(null, null)
      Disposable.clean(workbench)
      assert(!PlatformUI.isWorkbenchRunning())
    }
  }

  override def toString = "core.Core"
}

object Core extends XLoggable {
  implicit def core2actorRef(c: Core.type): ActorRef = c.actor
  implicit def core2actorSRef(c: Core.type): ScalaActorRef = c.actor
  /** Core actor reference. */
  lazy val actor = App.system.actorOf(props, id)
  /** Root context. */
  val context = Context("Core"): Context.Rich
  /** Singleton identificator. */
  val id = getClass.getSimpleName().dropRight(1)
  /** Component name. */
  val name = "digi-tabuddy-desktop-core"
  /** Core actor path. */
  lazy val path = actor.path
  /** Service context for internal routines. */
  val serviceContext = Context("Service")

  /** Core actor reference configuration object. */
  def props = DI.props
  /** Application root directory. */
  def root = DI.root

  override def toString = "core.Core[Singleton]"

  /**
   * MApplication stub for workbench.
   */
  class ApplicationStub(context: IEclipseContext) extends MApplication {
    def getAccessibilityPhrase(): String = ""
    def getAddons(): java.util.List[org.eclipse.e4.ui.model.application.MAddon] = new java.util.ArrayList()
    def getBindingContexts(): java.util.List[org.eclipse.e4.ui.model.application.commands.MBindingContext] = new java.util.ArrayList()
    def getBindingTables(): java.util.List[org.eclipse.e4.ui.model.application.commands.MBindingTable] = new java.util.ArrayList()
    def getCategories(): java.util.List[org.eclipse.e4.ui.model.application.commands.MCategory] = new java.util.ArrayList()
    def getChildren(): java.util.List[org.eclipse.e4.ui.model.application.ui.basic.MWindow] = {
      // We are always returns only one window to the framework if any
      val list = new java.util.ArrayList[org.eclipse.e4.ui.model.application.ui.basic.MWindow]()
      Option(serviceContext.get(classOf[org.eclipse.e4.ui.model.application.ui.basic.MWindow])).foreach(list.add)
      list
    }
    def getCommands(): java.util.List[org.eclipse.e4.ui.model.application.commands.MCommand] = new java.util.ArrayList()
    def getContainerData(): String = ""
    def getContext() = context
    def getContributorURI(): String = ""
    def getCurSharedRef(): org.eclipse.e4.ui.model.application.ui.advanced.MPlaceholder = ???
    def getDescriptors(): java.util.List[org.eclipse.e4.ui.model.application.descriptor.basic.MPartDescriptor] = new java.util.ArrayList()
    def getElementId(): String = ""
    def getHandlers(): java.util.List[org.eclipse.e4.ui.model.application.commands.MHandler] = new java.util.ArrayList()
    def getLocalizedAccessibilityPhrase(): String = ""
    def getMenuContributions(): java.util.List[org.eclipse.e4.ui.model.application.ui.menu.MMenuContribution] = new java.util.ArrayList()
    def getParent(): org.eclipse.e4.ui.model.application.ui.MElementContainer[org.eclipse.e4.ui.model.application.ui.MUIElement] = ???
    def getPersistedState(): java.util.Map[String, String] = new java.util.HashMap()
    def getProperties(): java.util.Map[String, String] = new java.util.HashMap()
    def getRenderer(): Object = ???
    def getRootContext(): java.util.List[org.eclipse.e4.ui.model.application.commands.MBindingContext] = new java.util.ArrayList()
    def getSelectedElement(): org.eclipse.e4.ui.model.application.ui.basic.MWindow = null
    def getSnippets(): java.util.List[org.eclipse.e4.ui.model.application.ui.MUIElement] = new java.util.ArrayList()
    def getTags(): java.util.List[String] = new java.util.ArrayList()
    def getToolBarContributions(): java.util.List[org.eclipse.e4.ui.model.application.ui.menu.MToolBarContribution] = new java.util.ArrayList()
    def getTransientData(): java.util.Map[String, Object] = new java.util.HashMap()
    def getTrimContributions(): java.util.List[org.eclipse.e4.ui.model.application.ui.menu.MTrimContribution] = new java.util.ArrayList()
    def getVariables(): java.util.List[String] = new java.util.ArrayList()
    def getVisibleWhen(): org.eclipse.e4.ui.model.application.ui.MExpression = ???
    def getWidget(): Object = ???
    def isOnTop(): Boolean = false
    def isToBeRendered(): Boolean = false
    def isVisible(): Boolean = false
    def setAccessibilityPhrase(x$1: String) {}
    def setContainerData(x$1: String) {}
    def setContext(x$1: org.eclipse.e4.core.contexts.IEclipseContext) {}
    def setContributorURI(x$1: String) {}
    def setCurSharedRef(x$1: org.eclipse.e4.ui.model.application.ui.advanced.MPlaceholder) {}
    def setElementId(x$1: String) {}
    def setOnTop(x$1: Boolean) {}
    def setParent(x$1: org.eclipse.e4.ui.model.application.ui.MElementContainer[org.eclipse.e4.ui.model.application.ui.MUIElement]) {}
    def setRenderer(x$1: Any) {}
    def setSelectedElement(x$1: org.eclipse.e4.ui.model.application.ui.basic.MWindow) {}
    def setToBeRendered(x$1: Boolean) {}
    def setVisible(x$1: Boolean) {}
    def setVisibleWhen(x$1: org.eclipse.e4.ui.model.application.ui.MExpression) {}
    def setWidget(x$1: Any) {}
  }
  /**
   * EventBroker stub for workbench.
   */
  class EventBrokerStub extends IEventBroker {
    def post(x$1: String, x$2: Any): Boolean = true
    def send(x$1: String, x$2: Any): Boolean = true
    def subscribe(x$1: String, x$2: String, x$3: org.osgi.service.event.EventHandler, x$4: Boolean): Boolean = true
    def subscribe(x$1: String, x$2: org.osgi.service.event.EventHandler): Boolean = true
    def unsubscribe(x$1: org.osgi.service.event.EventHandler): Boolean = true
  }
  /**
   * PresentationEngine stub for workbench.
   */
  class PresentationEngineStub extends IPresentationEngine {
    def createGui(x$1: org.eclipse.e4.ui.model.application.ui.MUIElement): Object = null
    def createGui(x$1: org.eclipse.e4.ui.model.application.ui.MUIElement, x$2: Any, x$3: IEclipseContext): Object = null
    def focusGui(x$1: org.eclipse.e4.ui.model.application.ui.MUIElement) {}
    def removeGui(x$1: org.eclipse.e4.ui.model.application.ui.MUIElement) {}
    def run(x$1: org.eclipse.e4.ui.model.application.MApplicationElement, x$2: IEclipseContext): Object = null
    def stop() {}
  }
  /**
   * Dependency injection routines
   */
  private object DI extends XDependencyInjection.PersistentInjectable {
    /** Hook method that is invoked after a workbench is initialized. */
    lazy val afterWorkbenchInit = injectOptional[Function0[Unit]]("AfterWorkbenchInit")
    /**
     * Collection of operation approvers.
     *
     * Each collected approver must be:
     *  1. an instance of definition.api.OperationApprover
     *  2. has name that starts with "Approver."
     */
    lazy val approvers = bindingModule.bindings.filter {
      case (key, value) ⇒ classOf[XOperationApprover].isAssignableFrom(key.m.runtimeClass)
    }.map {
      case (key, value) ⇒
        key.name match {
          case Some(name) if name.startsWith("Approver.") ⇒
            log.debug(s"Operation '${name}' loaded.")
            bindingModule.injectOptional(key).asInstanceOf[Option[org.digimead.tabuddy.desktop.core.definition.OperationApprover]]
          case _ ⇒
            log.debug(s"'${key.name.getOrElse("Unnamed")}' operation approver skipped.")
            None
        }
    }.flatten
    /** Hook method that is invoked before a workbench is created. */
    lazy val beforeWorkbenchCreate = injectOptional[Function0[Unit]]("BeforeWorkbenchCreate")
    /** Hook method that is invoked before a workbench is initialized. */
    lazy val beforeWorkbenchInit = injectOptional[Function0[Unit]]("BeforeWorkbenchInit")
    /** Core Akka factory. */
    lazy val props = injectOptional[Props]("Core") getOrElse Props[Core]
    /** Application root directory. */
    lazy val root = inject[File]("Root")
  }
}
