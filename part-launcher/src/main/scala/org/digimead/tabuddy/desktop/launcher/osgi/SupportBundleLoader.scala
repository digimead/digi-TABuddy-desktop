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

package org.digimead.tabuddy.desktop.launcher.osgi

import java.io.File
import java.io.IOException
import java.net.URL
import scala.Array.canBuildFrom
import scala.Option.option2Iterable
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.launcher.ApplicationLauncher
import org.eclipse.core.runtime.adaptor.EclipseStarter
import org.eclipse.core.runtime.adaptor.LocationManager
import org.eclipse.core.runtime.internal.adaptor.EclipseAdaptorMsg
import org.eclipse.core.runtime.internal.adaptor.Semaphore
import org.eclipse.osgi.framework.adaptor.FilePath
import org.eclipse.osgi.framework.adaptor.StatusException
import org.eclipse.osgi.framework.internal.core.{ Constants => EConstants }
import org.eclipse.osgi.framework.internal.core.FrameworkProperties
import org.eclipse.osgi.util.ManifestElement
import org.eclipse.osgi.util.NLS
import org.osgi.framework.Bundle
import org.osgi.framework.BundleEvent
import org.osgi.framework.BundleException
import org.osgi.framework.Constants
import org.osgi.framework.FrameworkEvent
import org.osgi.framework.FrameworkListener
import org.osgi.framework.SynchronousBundleListener
import org.osgi.service.packageadmin.PackageAdmin
import org.osgi.service.startlevel.StartLevel
import org.osgi.util.tracker.ServiceTracker

/**
 * Helper routines that contain OSGi loading logic.
 */
class SupportBundleLoader(val supportLocator: SupportBundleLocator) extends Loggable {
  @log
  def ensureBundlesActive(bundles: Array[Bundle], framework: Framework) {
    val context = framework.getSystemBundleContext()
    var tracker: ServiceTracker[StartLevel, StartLevel] = null
    try {
      for (bundle <- bundles) {
        if (bundle.getState() != Bundle.ACTIVE) {
          if (bundle.getState() == Bundle.INSTALLED) {
            log.error(NLS.bind(EclipseAdaptorMsg.ECLIPSE_STARTUP_ERROR_BUNDLE_NOT_RESOLVED, bundle.getLocation()))
          } else {
            // check that the startlevel allows the bundle to be active (111550)
            if (tracker == null) {
              tracker = new ServiceTracker[StartLevel, StartLevel](context, classOf[StartLevel].getName(), null)
              tracker.open()
            }
            val sl = tracker.getService()
            if (sl != null && (sl.getBundleStartLevel(bundle) <= sl.getStartLevel()))
              log.error(NLS.bind(EclipseAdaptorMsg.ECLIPSE_STARTUP_ERROR_BUNDLE_NOT_ACTIVE, bundle))
          }
        }
      }
    } finally {
      if (tracker != null)
        tracker.close()
    }
  }
  /** Get the list of currently installed initial bundles from the framework */
  @log
  def getCurrentBundles(includeInitial: Boolean, framework: Framework): Array[Bundle] = {
    log.debug("Collect current bundles.")
    val installed = framework.getSystemBundleContext().getBundles()
    var initial = Array[Bundle]()
    for (i <- 0 until installed.length) {
      val bundle = installed(i)
      if (bundle.getLocation().startsWith(Framework.INITIAL_LOCATION)) {
        if (includeInitial)
          initial = initial :+ bundle
      } else if (!includeInitial && bundle.getBundleId() != 0)
        initial = initial :+ bundle
    }
    return initial
  }
  /** Get the initial bundle list from the PROP_EXTENSIONS and PROP_BUNDLES */
  @log
  def getInitialBundles(defaultStartLevel: Int): Array[ApplicationLauncher.InitialBundle] = {
    log.debug("Collect initial bundles.")
    val osgiExtensions = FrameworkProperties.getProperty(EclipseStarter.PROP_EXTENSIONS)
    val osgiBundles = FrameworkProperties.getProperty(EclipseStarter.PROP_BUNDLES)
    val allBundles = if (osgiExtensions != null && osgiExtensions.length() > 0)
      osgiExtensions + "," + osgiBundles
    else
      osgiBundles
    if (osgiBundles != allBundles)
      FrameworkProperties.setProperty(EclipseStarter.PROP_BUNDLES, allBundles)
    log.trace("Complete initial bundle list: " + allBundles)
    val initialBundles: Array[String] = allBundles.split(",").map(_.trim)
    // should canonicalize the syspath.
    val syspath = try {
      new File(supportLocator.getSysPath()).getCanonicalPath()
    } catch {
      case e: IOException =>
        supportLocator.getSysPath()
    }
    log.trace("Loading...")
    for (initialBundle <- initialBundles) yield {
      var level = defaultStartLevel
      var start = false
      var index = initialBundle.lastIndexOf('@')
      val name = if (index >= 0) {
        for (attribute <- initialBundle.substring(index + 1, initialBundle.length()).split(":").map(_.trim)) {
          if (attribute.equals("start"))
            start = true
          else {
            try {
              level = Integer.parseInt(attribute);
            } catch { // bug 188089 - can't launch an OSGi bundle if the path of its plugin project contains the character "@"
              case e: NumberFormatException =>
                index = initialBundle.length()
            }
          }
        }
        initialBundle.substring(0, index)
      } else initialBundle
      try {
        Option(supportLocator.searchForBundle(name, syspath)) match {
          case Some(location) =>
            val relative = makeRelative(LocationManager.getInstallLocation().getURL(), location)
            val locationString = Framework.INITIAL_LOCATION + relative.toExternalForm()
            Some(ApplicationLauncher.InitialBundle(locationString, location, level, start))
          case None =>
            log.error(NLS.bind(EclipseAdaptorMsg.ECLIPSE_STARTUP_BUNDLE_NOT_FOUND, initialBundle))
            None
        }
      } catch {
        case e: IOException =>
          log.error(e.getMessage(), e)
          None
      }
    }
  }.flatten
  /**
   * Install the initialBundles that are not already installed.
   *
   * @return list of bundles to lazy activation, to start, to refresh
   */
  @log
  def installBundles(initial: Array[ApplicationLauncher.InitialBundle], current: Array[Bundle],
    framework: Framework): (Array[Bundle], Array[Bundle], Array[Bundle]) = {
    var lazyActivationBundles = Array[Bundle]()
    var startBundles = Array[Bundle]()
    var toRefresh = Array[Bundle]()
    val context = framework.getSystemBundleContext()
    val reference = context.getServiceReference(classOf[StartLevel].getName())
    var startService: StartLevel = null
    if (reference != null)
      startService = context.getService(reference).asInstanceOf[StartLevel]
    try {
      for (initialBundle <- initial) {
        try {
          // don't need to install if it is already installed
          val osgiBundle = current.find(bundle => initialBundle.locationString.equalsIgnoreCase(bundle.getLocation())) getOrElse {
            val in = initialBundle.location.openStream()
            val bundle = try {
              context.installBundle(initialBundle.locationString, in)
            } catch {
              case e: BundleException =>
                e match {
                  case status: StatusException if status.getStatusCode() == StatusException.CODE_OK && status.getStatus().isInstanceOf[Bundle] =>
                    status.getStatus().asInstanceOf[Bundle]
                  case err =>
                    throw err
                }
            }
            // only check for lazy activation header if this is a newly installed bundle and is not marked for persistent start
            if (!initialBundle.start && hasLazyActivationPolicy(bundle))
              lazyActivationBundles = lazyActivationBundles :+ bundle
            bundle
          }
          // always set the startlevel incase it has changed (bug 111549)
          // this is a no-op if the level is the same as previous launch.
          if ((osgiBundle.getState() & Bundle.UNINSTALLED) == 0 && initialBundle.level >= 0 && startService != null)
            startService.setBundleStartLevel(osgiBundle, initialBundle.level);
          // if this bundle is supposed to be started then add it to the start list
          if (initialBundle.start)
            startBundles = startBundles :+ osgiBundle
          // include basic bundles in case they were not resolved before
          if ((osgiBundle.getState() & Bundle.INSTALLED) != 0)
            toRefresh = toRefresh :+ osgiBundle
        } catch {
          case e: BundleException =>
            log.error(NLS.bind(EclipseAdaptorMsg.ECLIPSE_STARTUP_FAILED_INSTALL, initialBundle.location), e)
          case e: IOException =>
            log.error(NLS.bind(EclipseAdaptorMsg.ECLIPSE_STARTUP_FAILED_INSTALL, initialBundle.location), e)
        }
      }
    } finally {
      if (reference != null)
        context.ungetService(reference)
    }
    (lazyActivationBundles, startBundles, toRefresh)
  }
  // returns true if the refreshPackages operation caused the framework to shutdown
  @log
  def refreshPackages(bundles: Array[Bundle], framework: Framework): Boolean = {
    val context = framework.getSystemBundleContext()
    val packageAdminRef = context.getServiceReference(classOf[PackageAdmin].getName())
    var packageAdmin: PackageAdmin = null
    if (packageAdminRef != null)
      packageAdmin = context.getService(packageAdminRef).asInstanceOf[PackageAdmin]
    if (packageAdmin == null)
      return false
    // TODO this is such a hack it is silly.  There are still cases for race conditions etc
    // but this should allow for some progress...
    val semaphore = new Semaphore(0)
    val listener = new SupportBundleLoader.StartupEventListener(semaphore, FrameworkEvent.PACKAGES_REFRESHED)
    context.addFrameworkListener(listener)
    context.addBundleListener(listener)
    packageAdmin.refreshPackages(bundles)
    context.ungetService(packageAdminRef)
    //updateSplash(semaphore, listener)
    Framework.isForcedRestart()
  }
  @log
  def setStartLevel(frameworkStartLevel: Int, framework: Framework) {
    val context = framework.getSystemBundleContext()
    val reference = context.getServiceReference(classOf[StartLevel].getName())
    val startLevel = if (reference != null) context.getService(reference).asInstanceOf[StartLevel] else null
    if (startLevel == null)
      return
    val semaphore = new Semaphore(0)
    val listener = new SupportBundleLoader.StartupEventListener(semaphore, FrameworkEvent.STARTLEVEL_CHANGED)
    context.addFrameworkListener(listener)
    context.addBundleListener(listener)
    startLevel.setStartLevel(frameworkStartLevel)
    context.ungetService(reference)
    //updateSplash(semaphore, listener)
  }
  @log
  def startBundles(startBundles: Array[Bundle], lazyBundles: Array[Bundle]) {
    for (i <- 0 until startBundles.length)
      startBundle(startBundles(i), 0)
    for (i <- 0 until lazyBundles.length)
      startBundle(lazyBundles(i), Bundle.START_ACTIVATION_POLICY)
  }
  /**
   * Uninstall any of the currently installed bundles that do not exist in the
   * initial bundle list from installEntries.
   *
   * @return list of uninstalled bundles to refresh
   */
  @log
  def uninstallBundles(current: Array[Bundle], initial: Array[ApplicationLauncher.InitialBundle]): Array[Bundle] = {
    for (currentBundle <- current) yield {
      if (!initial.exists(initialBundle => currentBundle.getLocation().equalsIgnoreCase(initialBundle.locationString)))
        try {
          currentBundle.uninstall()
          Some(currentBundle)
        } catch {
          case e: BundleException =>
            log.error(NLS.bind(EclipseAdaptorMsg.ECLIPSE_STARTUP_FAILED_UNINSTALL, currentBundle.getLocation()), e)
            None
        }
      else
        None
    }
  }.flatten

  /** Check for lazy activation header. */
  protected def hasLazyActivationPolicy(target: Bundle): Boolean = {
    // check the bundle manifest to see if it defines a lazy activation policy
    val headers = target.getHeaders(""); //$NON-NLS-1$
    // first check to see if this is a fragment bundle
    val fragmentHost = headers.get(Constants.FRAGMENT_HOST)
    if (fragmentHost != null)
      return false // do not activate fragment bundles
    // look for the OSGi defined Bundle-ActivationPolicy header
    val activationPolicy = headers.get(Constants.BUNDLE_ACTIVATIONPOLICY)
    try {
      if (activationPolicy != null) {
        val elements = ManifestElement.parseHeader(Constants.BUNDLE_ACTIVATIONPOLICY, activationPolicy);
        if (elements != null && elements.length > 0) {
          // if the value is "lazy" then it has a lazy activation poliyc
          if (Constants.ACTIVATION_LAZY.equals(elements(0).getValue()))
            return true
        }
      } else {
        // check for Eclipse specific lazy start headers "Eclipse-LazyStart" and "Eclipse-AutoStart"
        val eclipseLazyStart = headers.get(EConstants.ECLIPSE_LAZYSTART)
        val elements = ManifestElement.parseHeader(EConstants.ECLIPSE_LAZYSTART, eclipseLazyStart)
        if (elements != null && elements.length > 0) {
          // if the value is true then it is lazy activated
          if ("true".equals(elements(0).getValue())) //$NON-NLS-1$
            return true;
          // otherwise it is only lazy activated if it defines an exceptions directive.
          else if (elements(0).getDirective("exceptions") != null) //$NON-NLS-1$
            return true;
        }
      }
    } catch {
      case e: BundleException =>
      // ignore this
    }
    return false;
  }
  /**
   * Returns a URL which is equivalent to the given URL relative to the
   * specified base URL. Works only for file: URLs
   * @throws MalformedURLException
   */
  protected def makeRelative(base: URL, location: URL): URL = {
    if (base == null)
      return location
    if (!"file".equals(base.getProtocol())) //$NON-NLS-1$
      return location
    if (!location.getProtocol().equals(Framework.REFERENCE_PROTOCOL))
      return location // we can only make reference urls relative
    val nonReferenceLocation = new URL(location.getPath())
    // if some URL component does not match, return the original location
    if (!base.getProtocol().equals(nonReferenceLocation.getProtocol()))
      return location
    val locationPath = new File(nonReferenceLocation.getPath())
    // if location is not absolute, return original location
    if (!locationPath.isAbsolute())
      return location
    val relativePath = makeRelative(new File(base.getPath()), locationPath)
    var urlPath = relativePath.getPath()
    if (File.separatorChar != '/')
      urlPath = urlPath.replace(File.separatorChar, '/')
    if (nonReferenceLocation.getPath().endsWith("/")) //$NON-NLS-1$
      // restore original trailing slash
      urlPath += '/'
    // couldn't use File to create URL here because it prepends the path with user.dir
    var relativeURL = new URL(base.getProtocol(), base.getHost(), base.getPort(), urlPath)
    // now make it back to a reference URL
    relativeURL = new URL(Framework.REFERENCE_SCHEME + relativeURL.toExternalForm())
    relativeURL
  }
  protected def makeRelative(base: File, location: File): File =
    if (!location.isAbsolute())
      location
    else
      new File(new FilePath(base).makeRelative(new FilePath(location)))
  @log
  protected def startBundle(bundle: Bundle, options: Int) {
    try {
      options match {
        case Bundle.START_TRANSIENT =>
          log.debug("Start TRANSIENT bundle " + bundle)
          bundle.start(options)
        case Bundle.START_ACTIVATION_POLICY =>
          log.debug("Start LAZY bundle " + bundle)
          bundle.start(options)
        case _ =>
          log.debug("Start bundle " + bundle)
          bundle.start(options)
      }
    } catch {
      case e: BundleException =>
        if ((bundle.getState() & Bundle.RESOLVED) != 0) {
          // only log errors if the bundle is resolved
          log.error(NLS.bind(EclipseAdaptorMsg.ECLIPSE_STARTUP_FAILED_START, bundle.getLocation()), e)
        }
    }
  }
}

object SupportBundleLoader {
  class StartupEventListener(semaphore: Semaphore, frameworkEventType: Int) extends SynchronousBundleListener with FrameworkListener {
    def bundleChanged(event: BundleEvent) {
      if (event.getBundle().getBundleId() == 0 && event.getType() == BundleEvent.STOPPING)
        semaphore.release();
    }
    def frameworkEvent(event: FrameworkEvent) {
      if (event.getType() == frameworkEventType)
        semaphore.release();
    }
  }
}