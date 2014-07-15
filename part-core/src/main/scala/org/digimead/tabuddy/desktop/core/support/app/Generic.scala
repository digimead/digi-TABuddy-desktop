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

package org.digimead.tabuddy.desktop.core.support.app

import com.google.common.io.Files
import java.io.File
import java.io.{ PrintWriter, StringWriter }
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.EventLoop
import org.digimead.tabuddy.desktop.core.support.{ App, Timeout }
import org.eclipse.core.runtime.preferences.InstanceScope
import org.eclipse.core.runtime.{ IStatus, MultiStatus, Status }
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.ui.preferences.ScopedPreferenceStore
import org.osgi.framework.{ Bundle, FrameworkUtil }
import scala.annotation.elidable
import scala.annotation.elidable._
import scala.annotation.tailrec
import scala.language.reflectiveCalls

trait Generic extends EventLoop.Consumer {
  this: XLoggable with Context with Thread with Watch ⇒
  /** Flag indicating whether debug methods is enabled. */
  val debug = debugMode
  /** Application preference store. */
  protected lazy val preferenceStore = new ScopedPreferenceStore(InstanceScope.INSTANCE, bundle(getClass).getSymbolicName())
  /** Flag indicating whether UI available. */
  lazy val isUIAvailable = try {
    watch(UIFlag).waitForStart(App.UIDetectionTimeout)
    isActive(UIFlag)
  } catch {
    case e: TimeoutException ⇒ false
    case e: ClassNotFoundException ⇒ false
    case e: NullPointerException ⇒ false // NPE is thrown while PojoSR initialization
  }

  /** Assert the current thread against the event one. */
  def assertEventThread(EventLoop: Boolean = true) = if (EventLoop) {
    if (!isEventLoop) {
      val throwable = new IllegalAccessException("Only the original thread that created the event loop can touch its widgets and use observables.")
      // sometimes we throw exception in threads that haven't catch block, notify anyway
      log.error("Only the original thread that created the event loop can touch its widgets and use observables.", throwable)
      throw throwable
    }
  } else {
    if (isEventLoop) {
      val throwable = new IllegalAccessException(s"Current thread ${Thread.currentThread()} is the event loop blocker.")
      // sometimes we throw exception in threads that haven't catch block, notify anyway
      log.error(s"Current thread ${Thread.currentThread()} is the event loop blocker.", throwable)
      throw throwable
    }
  }
  /** Get bundle for class. */
  def bundle(clazz: Class[_]) = FrameworkUtil.getBundle(clazz)
  /** Copy files recursively. */
  def copyRecursive(from: File, to: File) = copyRecursiveImpl(Array((from, to)))
  /** Get development mode flag. */
  def isDevelopmentMode: Boolean =
    // Get developmentMode AtomicBoolean from org.digimead.digi.launcher.RootClassLoader
    try bundle(getClass()).getClass().getClassLoader().asInstanceOf[{ val developmentMode: AtomicBoolean }].developmentMode.get
    catch { case e: NoSuchMethodException ⇒ true } // This is not a RootClassLoader

  /** Check the current thread against the event one. */
  def isEventLoop() = thread.eq(Thread.currentThread())
  /** Get application preference store. */
  def getPreferenceStore(): IPreferenceStore = preferenceStore
  /** Get root from the path. */
  def getRoot(file: File): File = Option(file.getParentFile()) match {
    case Some(parent) ⇒ getRoot(parent)
    case None ⇒ file
  }
  /** Process files recursively in parallel from the tail. */
  def processRecursive[T](from: File, maxDepth: Int = Int.MaxValue)(f: File ⇒ T): Iterable[T] = processRecursiveImpl(Array(from), Nil, 0, maxDepth)(f).flatten
  /** Convert throwable to MultiStatus. */
  def throwableToMultiStatus(t: Throwable, bundle: Bundle): MultiStatus = {
    val sw = new StringWriter()
    val pw = new PrintWriter(sw)
    t.printStackTrace(pw)
    val trace = sw.toString() // stack trace as a string
    // Temp holder of child statuses
    // Split output by OS-independend new-line
    val statuses = for (line ← trace.split(System.getProperty("line.separator")))
      yield new Status(IStatus.ERROR, bundle.getSymbolicName(), line)
    new MultiStatus(bundle.getSymbolicName(), IStatus.ERROR, statuses.toArray, t.getLocalizedMessage(), t)
  }
  /** Apply map to tree of maps: type x = Map[A, x]. */
  def treeMap[A, B](actorTree: Map[A, _])(f: A ⇒ B): Map[B, _] =
    actorTree match { case m: Map[A, _] ⇒ m.map { case (key, values) ⇒ f(key) -> treeMap(values.asInstanceOf[Map[A, _]])(f) } }
  /** Verify the current environment */
  def verifyApplicationEnvironment() {
    val eventThread = execNGet { Thread.currentThread() }
    assert(thread != null, "Thread is not available.")
    assert(thread == eventThread, "Incorrect event thread.")
    assert(display != null, "Display is not available.")
    assert(realm != null, "Realm is not available.")
    assert(bindingContext != null, "Binding context is not available.")
  }

  /** Copy files recursively. */
  @tailrec
  final protected def copyRecursiveImpl(toCopy: Iterable[(File, File)]) {
    if (toCopy.isEmpty)
      return
    val toNextCopy = toCopy.par.map {
      case (from: File, to: File) ⇒
        val toCopy: Seq[(File, File)] = from match {
          case directory if directory.isDirectory() ⇒
            Files.createParentDirs(to)
            to.mkdir()
            from.listFiles().map(from ⇒ (from, new File(to, from.getName())))
          case file ⇒
            Files.copy(from, to)
            Seq.empty
        }
        toCopy
    }.seq
    copyRecursiveImpl(toNextCopy.flatten)
  }
  /** Returns debug mode. */
  @elidable(FINE) protected def debugMode: Boolean = true

  /** Process files recursively in parallel from the tail. */
  @tailrec
  final protected def processRecursiveImpl[T](toList: Iterable[File], toProcess: Seq[Iterable[File]], depth: Int, maxDepth: Int)(f: File ⇒ T): Seq[Iterable[T]] = {
    if (toList.isEmpty || depth >= maxDepth)
      return toProcess.map(chunk ⇒ chunk.par.map(f).seq)
    val toNextList = toList.map(file ⇒ (file match {
      case directory if directory.isDirectory() ⇒ (directory.listFiles(), directory)
      case file ⇒ (Seq.empty, file)
    }): (Seq[File], File))
    processRecursiveImpl(toNextList.map(_._1).flatten, (Seq.empty[Iterable[File]] :+ toNextList.map(_._2)) ++ toProcess, depth + 1, maxDepth)(f)
  }
  /** Persistent object for isUIAvailable. */
  object UIFlag {
    override def toString = "UIFlag"
  }
}
