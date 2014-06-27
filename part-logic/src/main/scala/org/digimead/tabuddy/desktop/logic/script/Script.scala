/**
 * This file is part of the TA Buddy project.
 * Copyright (c) 2014 Alexey Aksenov ezh@ezh.msk.ru
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

package org.digimead.tabuddy.desktop.logic.script

import java.io.File
import java.math.BigInteger
import java.net.URI
import java.security.MessageDigest
import org.digimead.digi.lib.api.XDependencyInjection
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.support.App
import org.eclipse.core.runtime.FileLocator
import org.osgi.framework.Bundle
import scala.language.implicitConversions
import scala.reflect.internal.util.AbstractFileClassLoader
import scala.reflect.io.VirtualDirectory
import scala.tools.nsc.Settings

/**
 * Code evaluator.
 */
class Script extends XLoggable {
  /** Evaluation class path. */
  lazy val classPath = buildClassPath(App.bundle(getClass))
  /** Evaluation settings. */
  lazy val settings = {
    val settings = new Settings
    settings.nowarnings.value = true // warnings are exceptions, so disable
    settings.bootclasspath.value = classPath.mkString(File.pathSeparator)
    settings.classpath.value = classPath.mkString(File.pathSeparator)
    settings
  }
  /** Evaluation compiler. */
  lazy val compiler = new Compiler(settings)

  /** Get compilation result. */
  def apply[T](script: String): Script.Container[T] =
    apply(script, Script.unique(script))
  /** Get compilation result. */
  def apply[T](script: String, verbose: Boolean): Script.Container[T] =
    apply(script, Script.unique(script), verbose)
  /** Get compilation result. */
  def apply[T](script: String, verbose: Boolean, lineOffset: Int): Script.Container[T] =
    apply(script, Script.unique(script), verbose, lineOffset)
  /** Get compilation result. */
  def apply[T](script: String, unique: String, verbose: Boolean = false, lineOffset: Int = 2): Script.Container[T] = {
    val className = "Evaluator__" + unique
    val container = new Script.Container[T](className, Script.getClass.getClassLoader())
    try compile(wrapCodeInClass(className, script), container, lineOffset, verbose)
    catch {
      case e: Throwable ⇒
        container.target.clear()
        throw e
    }
    container
  }
  /** Compile script. */
  def compile[T](script: String, classLoader: Script.Container[T], lineOffset: Int, verbose: Boolean) {
    settings.outputDirs.setSingleOutput(classLoader.target)
    compiler(script, classLoader, lineOffset, verbose) // throws an exception if something wrong
  }

  /** Build array of files for all known bundles. */
  protected def buildClassPath(currentBundle: Bundle): Array[File] = {
    log.debug("Build evaluation class path.")
    if (currentBundle.getEntry("/").getProtocol() == "bundleentry") {
      // We are inside Equinox
      currentBundle.getBundleContext().getBundles().flatMap { bundle ⇒
        FileLocator.getBundleFile(bundle) match {
          case null ⇒
            log.debug(s"Skip empty class path entry for bundle ${bundle}.")
            None
          case jar if jar.isFile() ⇒
            log.debug(s"Add jar ${jar.getCanonicalFile()} to evaluation class path.")
            Some(jar.getCanonicalFile())
          case folder if folder.isDirectory() ⇒
            log.debug(s"Add folder ${folder.getCanonicalFile()} to evaluation class path.")
            Some(folder.getCanonicalFile())
          case unknown ⇒
            log.error(s"Unable to process bundle location ${unknown}.")
            None
        }
      }
    } else {
      // We are inside generic OSGi
      App.bundle(getClass).getBundleContext().getBundles().flatMap { bundle ⇒
        bundle.getEntry("/") match {
          case null ⇒
            log.debug(s"Skip empty class path entry for bundle ${bundle}.")
            None
          case entry if entry.getPath().endsWith("!/") ⇒
            val jar = new File(new URI(entry.getPath().dropRight(2)))
            log.debug(s"Add jar ${jar.getCanonicalFile()} to evaluation class path.")
            Some(jar.getCanonicalFile())
          case entry if new File(entry.getPath()).isDirectory() ⇒
            val folder = new File(entry.getPath())
            log.debug(s"Add folder ${folder.getCanonicalFile()} to evaluation class path.")
            Some(folder.getCanonicalFile())
          case unknown ⇒
            log.error(s"Unable to process bundle location ${unknown}.")
            None
        }
      }
    }
  }
  /** Wrap source code in a new class with an apply method.  */
  protected def wrapCodeInClass(className: String, code: String) =
    "class " + className + " extends (() => Any) {\n" +
      "  def apply() = {\n" + code + "\n" + "  }\n" +
      "}\n"
}

object Script {
  implicit def eval2implementation(s: Script.type): Script = s.inner
  /** Digest implementation for unique script value generation. */
  lazy val digest = MessageDigest.getInstance(digestAlgorithm)

  /** Get digest algorithm for generation unique value. */
  def digestAlgorithm = DI.digestAlgorithm
  /** Get Script implementation. */
  def inner = DI.implementation
  /** Get unique identificator for script. */
  def unique(script: String) = new BigInteger(1, digest.digest(script.getBytes(io.Codec.UTF8.charSet))).toString(36)

  /**
   * Class loader for finding classes compiled by Compiler.
   * After target.clear, this class loader will not be able to find old compiled classes.
   */
  class Container[T](val className: String, parent: ClassLoader)
    extends AbstractFileClassLoader(new VirtualDirectory("(memory)", None), parent) {
    /** Clear compilation results. */
    def clear() = {
      target.clear()
      clearAssertionStatus()
    }
    /** Run compiled code. */
    def run() = asContext(this.loadClass(className).getConstructor().newInstance().asInstanceOf[() ⇒ Any].apply().asInstanceOf[T])
    /** Virtual directory with result of compilation. */
    def target = root.asInstanceOf[VirtualDirectory]
  }
  /** Compiler settings. See trait StandardScalaSettings for example. */
  case class Exception(val messages: List[List[String]])
    extends scala.Exception("Compiler exception " + messages.map(_.mkString("\n")).mkString("\n"))
  /**
   * Dependency injection routines
   */
  private object DI extends XDependencyInjection.PersistentInjectable {
    /** Script implementation. */
    lazy val implementation = injectOptional[Script] getOrElse new Script
    /** Digest algorithm. */
    lazy val digestAlgorithm = injectOptional[String]("Script.Digest") getOrElse "SHA-1"
  }
}
