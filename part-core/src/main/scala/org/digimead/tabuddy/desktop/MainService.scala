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

package org.digimead.tabuddy.desktop

import java.util.concurrent.atomic.AtomicReference

import scala.collection.JavaConversions._

import org.digimead.digi.lib.Disposable
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.gui.GUI.gui2implementation
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.App.app2implementation
import org.digimead.tabuddy.desktop.support.Timeout
import org.eclipse.core.databinding.DataBindingContext
import org.eclipse.core.databinding.observable.Realm
import org.eclipse.core.runtime.Platform
import org.eclipse.e4.core.internal.contexts.EclipseContext
import org.eclipse.e4.ui.internal.workbench.E4Workbench
import org.eclipse.equinox.app.IApplication
import org.eclipse.equinox.app.IApplicationContext
import org.eclipse.jface.databinding.swt.SWTObservables
import org.eclipse.ui.PlatformUI
import org.eclipse.ui.internal.services.EvaluationService
import org.osgi.util.tracker.ServiceTracker

import com.typesafe.config.ConfigFactory

import akka.actor.ActorSystem

import language.implicitConversions

/**
 * Application entry point for Digi Launcher
 */
class MainService extends api.Main with Disposable.Default with Loggable {
  /** Application context timeout. */
  protected val applicationContextTimeout = Timeout.long
  /** The UI thread */
  protected lazy val thread = new MainService.UIThread
  /** Weak reference with manager for disposable marker. */
  protected val disposeable = Disposable(this, Activator)
  /** The application-wide actor system */
  val system = {
    log.debug("Start Akka ecosystem.")
    val config = ConfigFactory.parseString("""
      akka {
        actor.debug.unhandled = true
        actor.debug {
          receive = on
          autoreceive = on
          lifecycle = on
        }
        # Event handlers to register at boot time (Logging$DefaultLogger logs to STDOUT)
        loggers = ["org.digimead.tabuddy.desktop.support.AkkaLogBridge"]
        # Options: ERROR, WARNING, INFO, DEBUG
        loglevel = "DEBUG"
      }
    """)
    ActorSystem("DesktopTABuddySystem", config.withFallback(ConfigFactory.load()), getClass.getClassLoader())
  }

  /**
   * Application entry point from launcher.
   * @return the return value of the application. IApplication.EXIT_OK, IApplication.EXIT_RESTART or -1
   */
  def call(): Int = try {
    log.info("Run core.")
    // Waiting for IApplicationContext service.
    // Assume that platform is started when IApplicationContext is available.
    val applicationContextServiceTracker = new ServiceTracker[IApplicationContext, IApplicationContext](App.bundle(getClass).getBundleContext(), classOf[IApplicationContext], null)
    applicationContextServiceTracker.open()
    log.debug("Get global application context.")
    val exitCode = Option(applicationContextServiceTracker.waitForService(applicationContextTimeout.toMillis)) match {
      case Some(appContext) =>
        if (!Platform.isRunning)
          throw new IllegalStateException("Eclipse platform is not running.")
        updateDI()
        try {
          start().asInstanceOf[Int]
        } catch {
          case e: Throwable =>
            log.error(s"Application terminated: " + e.getMessage(), e)
            -1
        }
      case None =>
        // IApplicationContext create by org.eclipse.equinox.app via ServiceTracker that intercepts IExtensionRegistry
        log.error(s"Unable to find service for ${classOf[IApplicationContext].getName}. Is 'org.eclipse.equinox.app' bundle is running and extension point is registered?")
        -1
    }
    try { applicationContextServiceTracker.close() } catch { case e: IllegalStateException => /* reloading, bundle is absent */ }
    log.info(s"Core logic completed with code $exitCode.")
    exitCode
  }
  /**
   * Starts this application with the given context and returns a result.  The content of
   * the context is unchecked and should conform to the expectations of the application being
   * invoked.  This method can return the value {@link IApplicationContext#EXIT_ASYNC_RESULT} if
   * the application will deliver its results asynchronously with the
   * {@link IApplicationContext#setResult(Object, IApplication)} method; otherwise this method must not exit
   * until the application is finished and is ready to exit.
   *
   * @return IApplication.EXIT_OK, IApplication.EXIT_RESTART or -1
   * @see #EXIT_OK
   * @see #EXIT_RESTART
   * @param context the application context to pass to the application
   * @exception Exception if there is a problem running this application.
   */
  def start(): AnyRef = digiStart(): java.lang.Integer
  /**
   * Forces this running application to exit.  This method should wait until the
   * running application is ready to exit.  The {@link #start(IApplicationContext)}
   * should already have exited or should exit very soon after this method exits<p>
   *
   * This method is only called to force an application to exit.
   * This method will not be called if an application exits normally from
   * the {@link #start(IApplicationContext)} method.
   * <p>
   * Note: This method is called by the platform; it is not intended
   * to be called directly by clients.
   * </p>
   */
  def stop() = digiStop()

  /** Starts Digi application */
  protected def digiStart(): Int = {
    log.debug("Start application.")
    thread.start()
    thread.waitForResult() match {
      case gui.GUI.Exit.Ok => IApplication.EXIT_OK
      case gui.GUI.Exit.Restart => IApplication.EXIT_RESTART
      case gui.GUI.Exit.Error => -1
    }
  }
  /** Stops Digi application */
  protected def digiStop(code: gui.GUI.Exit = gui.GUI.Exit.Ok) = gui.GUI.stop(code)
  /** Dispose instance. */
  override protected def dispose() = MainService.disposeableLock.synchronized {
    if (Option(disposeable).nonEmpty) {
      log.debug("Shutdown Akka ecosystem.")
      system.shutdown()
      system.awaitTermination(Timeout.longest)
      // reset everything
      super.dispose()
    }
  }
  /** Starts this application and returns a result. */
  @deprecated(message = """
    Platform contains to many bugs and any positive effect is negated:
    - some contributions are broken after plugin bundle is reloaded
    - saved layout are broken is some cases after application is reloaded
    - lack of XMI editor (E4 Tools are broken)
    - partial support from original developers (a lot of incompleted modules)
    - internal platform code is incomplete: a lot of methods with TODO mark
    - copy'n'paste code (they don't ever want to change the code comments after paste)
    - contexts are inconsistent (They looks like monadic entities with broken behaviour)
    - visibleWhen is inconsistent: for example https://bugs.eclipse.org/bugs/show_bug.cgi?id=201589 (since 2006 with 30 votes)
    - reaction to bug reports is very sloooooooooooooooooooow.
    - average product quality: low
    as result: there is no reason to correct errors of Elipse platform team. Hard Work Does Not Pay Off.
    """, since = "the beginning")
  protected def eclipseStart(): Int = {
    log.debug("Start application.")
    //assert(display != null) // Initialize
    //assert(realm != null) // Initialize
    var returnCode = PlatformUI.RETURN_RESTART
    //returnCode = PlatformUI.createAndRunWorkbench(display, new WorkbenchAdvisor())
    /* Workbench is still active sporadically after createAndRunWorkbench is completed. */
    //try { App.workbench.close() } catch { case e: Throwable => }
    // Flush display queue.
    //while (display.readAndDispatch()) {}
    /*
     * Workbench context contains garbage after createAndRunWorkbench is completed.
     * Obviously that developer(s) of E4Application is stupid asshole.
     * This bastard only add stuff to Ta-Ta-Ta-Da! Global! OSGi wide! context and never clean that shit after.
     * Look at E4Application.createDefaultContext that is based on createDefaultHeadlessContext
     *
     * from http://wiki.eclipse.org/Platform_Command_Framework
     *   Note, however, that a plugin that programmatically defines contexts is responsible
     *     for cleaning them up if the plugin is ever unloaded.
     *
     * IMHO Eclipse core developers have some problems with reading their own documentation.
     *   Ezh
     */
    E4Workbench.getServiceContext() match {
      case globalContext: EclipseContext =>
        while (App.contextFindChild("WorkbenchContext", globalContext) match {
          case Some(context: EclipseContext) =>
            log.debug("Cleaning up garbage after Eclipse: WorkbenchContext")
            try { globalContext.removeChild(context) } catch { case e: Throwable => } // who cares?
            try { context.dispose() } catch { case e: Throwable => } // who cares?
            true
          case _ =>
            false
        }) ()
        /*
         * org.eclipse.ui.internal.Workbench.init() create EvaluationService
         * final EvaluationService evaluationService = new EvaluationService(e4Context); <-- Here
         * And an instance of the service register itself in the constructor :-) What is about the destructor?
         * Oh I forgot that Java have no destructors and junkie wrote that shit too.
         * But wait... That developer is used a 'dispose' method! Awesome! But little note - dispose don't clean a shit.
         *   Ezh
         */
        while (App.contextFindChild(classOf[EvaluationService].getName, globalContext) match {
          case Some(context: EclipseContext) =>
            log.debug("Cleaning up garbage after Eclipse: EvaluationService")
            try { globalContext.removeChild(context) } catch { case e: Throwable => } // who cares?
            try { context.dispose() } catch { case e: Throwable => } // who cares?
            true
          case _ =>
            false
        }) ()
        // Remove local garbage from globalContext
        /*
         * EclipseContext implementation contains this note before localData:
         * // This method is for debug only, do not use externally
         * Funny.
         *  1. At the beginning the code monkey add hacks to their own code that is broken by design.
         *  2. At the last the code monkey add comment with pray: "don't touch my shit".
         * In contrast the Akka sources just not contains hacks at all. I tried to find some for my special needs :-) No way ;-)
         *   The Akka producer (framework developer) and the Akka consumer (library user) have absolutely equal pack of instrumentation.
         *   The code without hacks is clear, simple and strait.
         *     Ezh
         */
        globalContext.localData().foreach {
          case (key, _) =>
            if (key != "debugString") globalContext.remove(key)
        }
      // log.trace("Garbage after workbench: \n" + App.contextDumpHierarchy(globalContext, _ => true, false))
      case _ => // skip something unknown
    }
    returnCode match {
      case PlatformUI.RETURN_OK => IApplication.EXIT_OK
      case PlatformUI.RETURN_RESTART => IApplication.EXIT_RESTART // never
      case PlatformUI.RETURN_UNSTARTABLE => Int.box(-1)
      case PlatformUI.RETURN_EMERGENCY_CLOSE => Int.box(-1)
    }
  }
  /** Stops this application. */
  @deprecated(message = """
    Platform contains to many bugs and any positive effect is negated:
    - some contributions are broken after plugin bundle is reloaded
    - saved layout are broken is some cases after application is reloaded
    - lack of XMI editor (E4 Tools are broken)
    - partial support from original developers (a lot of incompleted modules)
    - internal platform code is incomplete: a lot of methods with TODO mark
    - copy'n'paste code (they don't ever want to change the code comments after paste)
    - contexts are inconsistent (They looks like monadic entities with broken behaviour)
      As example 'removeRAT' function that isn't used at all! :-/ we may add RAT and... This is all.
    - visibleWhen is inconsistent: for example https://bugs.eclipse.org/bugs/show_bug.cgi?id=201589 (since 2006 with 30 votes)
    - reaction to bug reports is very sloooooooooooooooooooow.
    - average product quality: low
    as result: there is no reason to correct errors of Elipse platform team. Hard Work Does Not Pay Off.
    """, since = "the beginning")
  protected def eclipseStop() {
    if (!PlatformUI.isWorkbenchRunning())
      return
    log.debug("Stop application.")
    val workbench = PlatformUI.getWorkbench()
    val display = workbench.getDisplay()
    display.syncExec(new Runnable() {
      def run() {
        if (!display.isDisposed())
          workbench.close()
      }
    })
  }
  /** Update dependency injections. */
  protected def updateDI() {
    val context = App.bundle(getClass).getBundleContext
    Option(context.getServiceReference(classOf[org.digimead.digi.lib.api.DependencyInjection])).
      map { currencyServiceRef => (currencyServiceRef, context.getService(currencyServiceRef)) } match {
        case Some((reference, diService)) =>
          // DI is already initialized somewhere.
          // Update DI container and apply DI to newly loaded classes
          log.debug("Update DI.")
          org.digimead.digi.lib.DependencyInjection.reset()
          org.digimead.digi.lib.DependencyInjection(diService.getDependencyInjection)
          diService.getDependencyValidator.foreach { validator =>
            val invalid = org.digimead.digi.lib.DependencyInjection.validate(validator, this)
            if (invalid.nonEmpty)
              throw new IllegalArgumentException("Illegal DI keys found: " + invalid.mkString(","))
          }
          context.ungetService(reference)
        case None =>
          log.warn("DI service not found.")
      }
  }
}

object MainService extends Loggable {
  implicit def main2implementation(c: MainService.type): MainService = c.inner
  private val disposeableLock = new Object

  /** Main service implementation. */
  def inner() = DI.implementation

  /**
   * I really don't want to provide access to MainService from entire system
   */
  trait Consumer {
    /** The global application data binding context */
    def bindingContext = MainService.thread.bindingContext
    /** The default display, available from ui.Window */
    def display = MainService.thread.display
    /** The realm representing the UI thread for the given display */
    def realm = MainService.thread.realm
    /** The application-wide actor system */
    def system = MainService.system
    /** The UI thread */
    def thread = MainService.thread
  }
  class UIThread extends Thread("GUI Thread") {
    /** The global application data binding context */
    lazy val bindingContext = new DataBindingContext(realm)
    // There is no display dispose because I don't want to read huge pack of exception reports
    // from Eclipse shit (spreading across bundles widely) that completely ignores bundles restart possibility.
    // We save display with all allocated garbage even between bundle restarts. Many thanks to Eclipse developers for this crap.
    //   Ezh
    /** The default display, available from ui.Window */
    lazy val display = PlatformUI.createDisplay()
    /** The realm representing the UI thread for the given display */
    lazy val realm = SWTObservables.getRealm(display)
    /** Thread result. */
    val result = new AtomicReference[Option[gui.GUI.Exit]](None)

    override def run {
      assert(result.get.isEmpty)
      assert(bindingContext != null)
      assert(display != null)
      assert(realm != null)
      log.debug("Mark UI thread with ID " + getId())
      Realm.runWithDefault(realm, new Runnable() { def run() = gui.GUI.run(result) })
    }
    /** Wait for GUI result. */
    def waitForResult() = {
      result.synchronized { while (result.get.isEmpty) result.wait() }
      result.get.get
    }
  }
  /**
   * Dependency injection routines.
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** Main service implementation. */
    lazy val implementation = injectOptional[MainService] getOrElse new MainService()
  }
}
