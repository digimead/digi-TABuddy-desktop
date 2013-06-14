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
import scala.Array.canBuildFrom
import scala.Array.fallbackCanBuildFrom
import scala.Option.option2Iterable
import org.digimead.digi.lib.log.api.Loggable
import org.osgi.framework.wiring.BundleWiring
import com.escalatesoft.subcut.inject.BindingModule
import com.twitter.util.Eval
import language.reflectiveCalls
import org.digimead.tabuddy.desktop.launcher.RootClassLoader

/** OSGi framework DI initializer */
class DI extends Loggable {
  /** Evaluate DI from script with DI class loader */
  def evaluate(script: File, classLoader: DI.ClassLoader): Option[BindingModule] = {
    log.debug(s"Evaluate DI settings with classloader which is included ${classLoader.bundleClassLoaders.length} subloader(s).")
    if (!script.exists() || !script.isFile()) {
      log.warn("Unable to find DI script: " + script.getCanonicalPath())
      return None
    }
    try {
      // get delegationLoader from RootClassLoader via reflection
      val evalClazz = getClass.getClassLoader().asInstanceOf[{ val delegationLoader: ClassLoader }].
        delegationLoader.loadClass("com.twitter.util.Eval")
      val evalCtor = evalClazz.getConstructor(classOf[Option[File]])
      // None -> in memory compilation
      val eval = evalCtor.newInstance(None).asInstanceOf[{ def apply[T](files: File*): T }]
      // Eval script as class Evaluator__... extends (() => Any) { def apply() = { SCRIPT } }
      val di = eval[BindingModule](script)
      log.debug(s"DI file ${script} compiles successful.")
      Some(di)
    } catch {
      // Eval.CompilerException is unavailable for current class loader
      case e if e.getClass.getName().endsWith("Eval$CompilerException") =>
        log.error("Error in DI file ${script}: " + e.getMessage(), e)
        None
    }
  }
  /** Create DI consolidated class loader. */
  def initialize(framework: Framework): Option[DI.ClassLoader] = {
    log.debug("Initialize dependency injection.")
    val bundleContext = framework.getSystemBundleContext().getBundles().flatMap(bundle =>
      Option(try {
        (bundle, bundle.adapt(classOf[BundleWiring]).getClassLoader())
      } catch {
        case e: Throwable =>
          // Is it a BUG in OSGi implementation?
          if (bundle.getBundleId() != 0)
            log.debug(s"Unable to get bundle ${bundle} class loader: " + e.getMessage(), e)
          null
      }))
    if (bundleContext.isEmpty) {
      log.error("Unable to initialize dependency injection: there are no bundle class loaders discovered.")
      None
    } else {
      log.debug("Create DI classloader with subloaders in order: \n\t" + bundleContext.map(_._1).mkString("\n\t"))
      Some(new DI.ClassLoader(getClass.getClassLoader(), bundleContext.map(_._2)))
    }
  }
}

object DI {
  /**
   * Standard parent-first class loader with additional search over bundleClassLoaders
   */
  class ClassLoader(parent: java.lang.ClassLoader,
    val bundleClassLoaders: Seq[java.lang.ClassLoader]) extends java.lang.ClassLoader(parent) {
    // It is never returns null, as the specification defines
    /** Loads the class with the specified binary name. */
    override protected def loadClass(name: String, resolve: Boolean): Class[_] = {
      // Try to load from parent loader.
      try {
        return super.loadClass(name, resolve)
      } catch {
        case _: ClassNotFoundException =>
      }

      bundleClassLoaders.foreach { bundleClassLoader =>
        try {
          return bundleClassLoader.loadClass(name)
        } catch {
          case _: ClassNotFoundException =>
        }
      }

      throw new ClassNotFoundException(name)
    }
  }
}
