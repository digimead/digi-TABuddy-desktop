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

package org.digimead.tabuddy.desktop.launcher

import java.io.IOException
import java.net.MalformedURLException
import java.net.URL
import java.util.Properties
import java.util.concurrent.atomic.AtomicLong

import scala.Array.canBuildFrom
import scala.collection.JavaConversions._

import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.log.api.Loggable
import org.eclipse.core.runtime.adaptor.EclipseStarter
import org.eclipse.core.runtime.adaptor.LocationManager
import org.eclipse.osgi.framework.adaptor.FrameworkAdaptor
import org.eclipse.osgi.framework.internal.core.ConsoleManager
import org.eclipse.osgi.framework.internal.core.FrameworkProperties
import org.eclipse.osgi.internal.baseadaptor.BaseStorageHook
import org.eclipse.osgi.internal.profile.Profile
import org.osgi.framework.Bundle
import org.osgi.framework.BundleEvent
import org.osgi.framework.BundleListener

/**
 * Framework launcher used by ApplicationLauncher.
 */
class FrameworkLauncher extends BundleListener with Loggable {
  /** Contains last bundle event. */
  lazy val lastBundleEvent = new AtomicLong()
  /** Helper with bundle location logic */
  lazy val supportLocator = new osgi.SupportBundleLocator()
  /** Helper with framework loading logic */
  lazy val supportLoader = new osgi.SupportBundleLoader(supportLocator)

  /** Check OSGi framework launch. */
  @log
  def check(framework: osgi.Framework): Boolean = {
    val bundles = framework.getSystemBundleContext().getBundles()
    bundles.find(b => b.getState() != Bundle.RESOLVED && b.getState() != Bundle.STARTING && b.getState() != Bundle.ACTIVE).foreach { broken =>
      val state = broken.getState() match {
        case Bundle.UNINSTALLED => "UNINSTALLED"
        case Bundle.INSTALLED => "INSTALLED"
        case Bundle.RESOLVED => "RESOLVED"
        case Bundle.STARTING => "STARTING"
        case Bundle.STOPPING => "STOPPING"
        case Bundle.ACTIVE => "ACTIVE"
        case unknown => "UNKNOWN " + unknown
      }
      log.error(s"Framework launch unsuccessful. Unexpected state $state for bundle $broken")
      false
    }
    true
  }
  /** Finish processes of OSGi framework. */
  @log
  def finish(shutdownListeners: Seq[BundleListener], console: Option[ConsoleManager], framework: osgi.Framework) {
    log.info("Finish processes of OSGi framework.")
    (shutdownListeners :+ this).foreach(l => try { framework.getSystemBundleContext().removeBundleListener(l) } catch { case e: Throwable => })
    console.foreach(console => try { console.stopConsole() } catch { case e: Throwable => log.warn("Unable to stop OSGi console: " + e, e) })
  }
  /** Initialize DI for OSGi framework. */
  @log
  def initializeDI(framework: osgi.Framework) {
    log.debug("Initialize DI for OSGi.")
    val initializator = new osgi.DI
    //initializator.initialize(framework, applicationDI.map(f => () => bindingModule, f) getOrElse Seq(f))
  }
  /** Launch OSGi framework. */
  @log
  def launch(console: Boolean, shutdownHandlers: Seq[Runnable]): (osgi.Framework, Option[ConsoleManager], Seq[BundleListener]) = {
    log.info("Launch OSGi framework.")
    Properties.initialize()
    // after this: system bundle RESOLVED, all bundles INSTALLED
    val framework = create()
    val shutdownListeners = framework.registerShutdownHandlers(shutdownHandlers)
    framework.getSystemBundleContext().addBundleListener(this)
    val consoleMgr = if (console) Some(ConsoleManager.startConsole(framework)) else None
    // after this: system bundle STARTING, all bundles RESOLVED/INSTALLED
    framework.launch()
    (framework, consoleMgr, shutdownListeners)
  }
  /**
   * Ensure all basic bundles are installed, resolved and scheduled to start. Returns a sequence containing
   * all basic bundles that are marked to start.
   * Returns None if the framework has been shutdown as a result of refreshPackages
   */
  @log
  def loadBundles(defaultStartLevel: Int, framework: osgi.Framework): Option[(Array[Bundle], Array[Bundle])] = {
    log.info("Load OSGi bundles.")
    log.debug("Loading OSGi bundles.")
    val initialBundles = supportLoader.getInitialBundles(defaultStartLevel)
    val currentBundles = supportLoader.getCurrentBundles(true, framework)
    log.debug(s"""Initial bundles: (${initialBundles.mkString(", ")})""")
    log.debug(s"""Current bundles: (${currentBundles.mkString(", ")})""")
    val uninstalledBundles = supportLoader.uninstallBundles(currentBundles, initialBundles)
    val (toLazyActivation, toStart, installedBundles) = supportLoader.installBundles(initialBundles, currentBundles, framework)
    log.debug(s"""Uninstalled bundles: (${uninstalledBundles.mkString(", ")})""")
    log.debug(s"""Installed bundles: (${installedBundles.mkString(", ")})""")
    log.debug(s"""Lazy activation bundles: (${toLazyActivation.mkString(", ")})""")
    log.debug(s"""To start bundles: (${toStart.mkString(", ")})""")
    val toRefresh = (uninstalledBundles ++ installedBundles).distinct

    // If we installed/uninstalled something, force a refresh of all installed/uninstalled bundles
    if (!toRefresh.isEmpty && supportLoader.refreshPackages(toRefresh, framework))
      None // refreshPackages shutdown the framework
    else
      Some(toLazyActivation, toStart)
  }
  /** Schedule all bundles to be started */
  @log
  def startBundles(initialStartLevel: Int, lazyActivationBundles: Array[Bundle], toStartBundles: Array[Bundle], framework: osgi.Framework) {
    log.info("Start OSGi bundles")
    supportLoader.startBundles(toStartBundles, lazyActivationBundles)
    // set the framework start level to the ultimate value.  This will actually start things
    // running if they are persistently active.
    supportLoader.setStartLevel(initialStartLevel, framework)
    // they should all be active by this time
    supportLoader.ensureBundlesActive(toStartBundles, framework)
  }
  /** Wait for consistent state of framework (all bundles loaded and resolver). */
  @log
  def waitForConsitentState(timeout: Long, framework: osgi.Framework): Boolean = {
    val frame = 100 // 100ms for decision
    val consistent = Seq(Bundle.INSTALLED, Bundle.RESOLVED, Bundle.ACTIVE)
    val context = framework.getSystemBundleContext()
    val ts = System.currentTimeMillis() + timeout
    def isConsistent = {
      val lastEventTS = System.currentTimeMillis() - lastBundleEvent.get
      val stateIsConsistent = context.getBundles().forall(b => b.getBundleId() == 0 || consistent.contains(b.getState()))
      val stateIsPersistent = lastEventTS > frame // All bundles are stable and there is $frame ms without new BundleEvent
      if (!stateIsConsistent)
        context.getBundles().map(b => (b, b.getState())).filterNot(b => b._1.getBundleId() == 0 || consistent.contains(b._2)).foreach {
          case (bundle, state) => log.trace(s"There is $bundle in inconsistent state $state.")
        }
      if (stateIsPersistent)
        log.trace(s"Last BundleEvent was ${lastEventTS}ms ago")
      stateIsConsistent && stateIsPersistent
    }
    while ((isConsistent match {
      case true => return true
      case false => true
    }) && (ts - System.currentTimeMillis > 0)) {
      val timeout = math.min(ts - System.currentTimeMillis, frame)
      lastBundleEvent.synchronized { lastBundleEvent.wait(timeout) }
      if (lastBundleEvent.get < frame)
        Thread.sleep(frame) // Something happen, wait 100ms
    }
    isConsistent
  }

  /**
   * Receives notification that a bundle has had a lifecycle change.
   *
   * @param event The {@code BundleEvent}.
   */
  protected def bundleChanged(event: BundleEvent) = event.getType() match {
    case BundleEvent.INSTALLED =>
      log.debug("bundle %s is installed from context %s".
        format(event.getBundle().getSymbolicName(), event.getOrigin().getSymbolicName()))
      lastBundleEvent.synchronized {
        lastBundleEvent.set(System.currentTimeMillis())
        lastBundleEvent.notifyAll()
      }
    case BundleEvent.STARTED =>
      log.debug("bundle %s is started".format(event.getBundle().getSymbolicName()))
      lastBundleEvent.synchronized {
        lastBundleEvent.set(System.currentTimeMillis())
        lastBundleEvent.notifyAll()
      }
    case BundleEvent.STOPPED =>
      log.debug("bundle %s is stopped".format(event.getBundle().getSymbolicName()))
      lastBundleEvent.synchronized {
        lastBundleEvent.set(System.currentTimeMillis())
        lastBundleEvent.notifyAll()
      }
    case BundleEvent.UPDATED =>
      log.debug("bundle %s is updated".format(event.getBundle().getSymbolicName()))
      lastBundleEvent.synchronized {
        lastBundleEvent.set(System.currentTimeMillis())
        lastBundleEvent.notifyAll()
      }
    case BundleEvent.UNINSTALLED =>
      log.debug("bundle %s is uninstalled".format(event.getBundle().getSymbolicName()))
      lastBundleEvent.synchronized {
        lastBundleEvent.set(System.currentTimeMillis())
        lastBundleEvent.notifyAll()
      }
    case BundleEvent.RESOLVED =>
      log.debug("bundle %s is resolved".format(event.getBundle().getSymbolicName()))
      lastBundleEvent.synchronized {
        lastBundleEvent.set(System.currentTimeMillis())
        lastBundleEvent.notifyAll()
      }
    case BundleEvent.UNRESOLVED =>
      log.warn("bundle %s is unresolved".format(event.getBundle().getSymbolicName()))
      lastBundleEvent.synchronized {
        lastBundleEvent.set(System.currentTimeMillis())
        lastBundleEvent.notifyAll()
      }
    case BundleEvent.STARTING =>
      log.debug("bundle %s is starting".format(event.getBundle().getSymbolicName()))
      lastBundleEvent.synchronized {
        lastBundleEvent.set(System.currentTimeMillis())
        lastBundleEvent.notifyAll()
      }
    case BundleEvent.STOPPING =>
      log.debug("bundle %s is stopping".format(event.getBundle().getSymbolicName()))
      lastBundleEvent.synchronized {
        lastBundleEvent.set(System.currentTimeMillis())
        lastBundleEvent.notifyAll()
      }
    case BundleEvent.LAZY_ACTIVATION =>
      log.debug("bundle %s lazy activation".format(event.getBundle().getSymbolicName()))
      lastBundleEvent.synchronized {
        lastBundleEvent.set(System.currentTimeMillis())
        lastBundleEvent.notifyAll()
      }
  }
  /**  Creates and returns OSGi framework   */
  @log
  protected def create(): osgi.Framework = {
    // the osgi.adaptor (org.eclipse.osgi.baseadaptor.BaseAdaptor by default)
    val adaptorClassName = FrameworkProperties.getProperty(EclipseStarter.PROP_ADAPTOR, EclipseStarter.DEFAULT_ADAPTOR_CLASS)
    val adaptorClass = Class.forName(adaptorClassName)
    val constructor = adaptorClass.getConstructor(classOf[Array[String]])
    val adapter = constructor.newInstance(Array[String]()).asInstanceOf[FrameworkAdaptor]
    new osgi.Framework(adapter)
  }
  // WTF? So shity code from Eclipse. Disappointed.
  protected def waitForShutdown(framework: osgi.Framework) {
    if (!osgi.Framework.isForcedRestart())
      return
    // wait for the system bundle to stop
    val systemBundle = framework.getBundle(0)
    var i = 0
    while (i < 5000 && (systemBundle.getState() & (Bundle.STARTING | Bundle.ACTIVE | Bundle.STOPPING)) != 0) {
      i += 200
      try {
        Thread.sleep(200)
      } catch {
        case e: InterruptedException =>
          i == 5000
      }
    }
  }
  object Properties {
    def initialize() {
      FrameworkProperties.initializeProperties()
      LocationManager.initializeLocations()
      loadConfigurationInfo()
      finalizeInitialization()
      if (Profile.PROFILE)
        Profile.initProps() // catch any Profile properties set in eclipse.properties...
    }
    /**
     * Sets the initial properties for the platform.
     * This method must be called before calling the {@link  #run(String[], Runnable)} or
     * {@link #startup(String[], Runnable)} methods for the properties to be used in
     * a launched instance of the platform.
     * <p>
     * If the specified properties contains a null value then the key for that value
     * will be cleared from the properties of the platform.
     * </p>
     * @param initialProperties the initial properties to set for the platform.
     */
    def setInitial(initialProperties: Map[String, String]) {
      if (initialProperties.isEmpty) {
        log.warn("Initial properties is empty.")
      } else {
        for ((key, value) <- initialProperties) if (value != null)
          FrameworkProperties.setProperty(key, value)
      }
    }
    protected def finalizeInitialization() {
      // if check config is unknown and we are in dev mode,
      if (FrameworkProperties.getProperty(EclipseStarter.PROP_DEV) != null && FrameworkProperties.getProperty(EclipseStarter.PROP_CHECK_CONFIG) == null)
        FrameworkProperties.setProperty(EclipseStarter.PROP_CHECK_CONFIG, "true") //$NON-NLS-1$
    }
    protected def load(location: URL): Properties = {
      val result = new Properties()
      if (location == null)
        return result
      try {
        val in = location.openStream()
        try {
          result.load(in)
        } finally {
          in.close()
        }
      } catch {
        case e: IOException =>
        // its ok if there is no file.  We'll just use the defaults for everything
        // TODO but it might be nice to log something with gentle wording (i.e., it is not an error)
      }
      substituteVars(result)
    }
    protected def loadConfigurationInfo() {
      Option(LocationManager.getConfigurationLocation()) foreach { configArea =>
        var location: URL = null
        try {
          location = new URL(configArea.getURL().toExternalForm() + LocationManager.CONFIG_FILE)
        } catch {
          case e: MalformedURLException =>
          // its ok.  This should never happen
        }
        merge(FrameworkProperties.getProperties(), load(location))
      }
    }
    protected def merge(destination: Properties, source: Properties) =
      for {
        entry <- source.entrySet().iterator()
        key <- Option(entry.getKey()).map(_.asInstanceOf[String])
        value <- Option(entry.getValue()).map(_.asInstanceOf[String])
      } if (destination.getProperty(key) == null) destination.setProperty(key, value)
    protected def substituteVars(result: Properties): Properties =
      if (result == null) {
        //nothing todo.
        null
      } else {
        for (key <- result.keys()) key match {
          case key: String =>
            val value = result.getProperty(key)
            if (value != null)
              result.put(key, BaseStorageHook.substituteVars(value))
          case other =>
        }
        result
      }
  }
}
