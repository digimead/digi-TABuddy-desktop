/**
 * This file is part of the TABuddy project.
 * Copyright (c) 2012-2013 Alexey Aksenov ezh@ezh.msk.ru
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

package org.digimead.tabuddy.desktop.debug

import java.awt.Font
import java.io.File
import java.io.PrintStream
import java.io.ByteArrayOutputStream
import java.net.URLClassLoader
import java.net.URLDecoder

import scala.tools.nsc.Settings
import scala.tools.nsc.interpreter.Completion
import scala.tools.nsc.interpreter.ILoop
import scala.tools.nsc.interpreter.ILoop.loopToInterpreter
import scala.tools.nsc.interpreter.JLineCompletion
import scala.tools.nsc.interpreter.NamedParam
import scala.tools.nsc.interpreter.session.History
import scala.tools.nsc.interpreter.session.JLineHistory

import org.digimead.digi.lib.DependencyInjection
import org.digimead.digi.lib.log.Loggable
import org.digimead.digi.lib.log.logger.RichLogger.rich2slf4j
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.desktop.Config

import com.escalatesoft.subcut.inject.BindingModule

import language.implicitConversions

class Console extends Console.Interface with Loggable {
  lazy val repl = createRepl()
  lazy val history = JLineHistory()
  lazy val completion = new JLineCompletion(repl)
  lazy val reader = createReader(true, history, completion)

  def echo(msg: String) = echoNoNL(msg + "\r\n")
  def echoNoNL(msg: String) = Console.viewPort.foreach(_.echoNoNL(msg))
  def show = synchronized {
    Console.viewPort match {
      case Some(viewPort) =>
        log.debug("show exists viewport")
        viewPort.show()
      case None =>
        log.debug("create new viewport")
        Console.viewPort = Some(Console.viewPortClass.newInstance())
    }
    Console.viewPort match {
      case Some(viewPort) =>
        log.debug("open TABuddy interactive console")
        val args: List[NamedParam] = List(NamedParam("model", Model))
        val msg = if (args.isEmpty) "" else "  Binding " + args.size + " value%s.".format(
          if (args.size == 1) "" else "s")
        echo("\r\nDebug repl starting." + msg)
        /*
         * Fuck you! Scala interpreter developers. This shit is laying there for years.
         * Interpreter code is full of shit like standard actors library :-(
         * At least there is the Akka as a replacement for actors.
         */
        val savedOut = System.out
        val savedErr = System.err
        System.setOut(viewPort.outputPrintStream)
        System.setErr(viewPort.outputPrintStream)
        repl.createInterpreter()
        System.setOut(savedOut)
        System.setErr(savedErr)
        repl.in = reader

        // rebind exit so people don't accidentally call sys.exit by way of predef
        repl.quietRun("""def exit = println("Type :quit to resume program execution.")""")
        args foreach (p => repl.bind(p.name, p.tpe, p.value))
        repl.loop()

        echo("\nDebug repl exiting.")
        repl.closeInterpreter()
        viewPort.hide

        log.debug("close TABuddy interactive console")
      case None =>
        log.fatal("lost Console view port")
    }
  }

  /** Create reader instance */
  protected[Console] def createReader(interactive: Boolean, history: JLineHistory, completion: Completion) =
    new Reader(interactive, history, completion)
  /** Create ILoop instance */
  protected[Console] def createRepl() = {
    log.debug("create the TABaddy console repl (Read-eval-print loop)")
    val repl = new ILoop {
      override def prompt = "\ndebug> "
      override def echo(msg: String) = Console.echo(msg)
    }
    repl.settings = new Settings(Console.echo)
    repl.settings.usejavacp.value = false
    val cl = this.getClass.getClassLoader
    val paths = new scala.collection.mutable.ListBuffer[Any]
    var libPath: File = null
    if (cl.isInstanceOf[URLClassLoader]) {
      val ucl = cl.asInstanceOf[URLClassLoader]
      ucl.getURLs.foreach { url =>
        if (url.getProtocol.equals("file")) {
          val path = new File(URLDecoder.decode(url.getPath, "UTF-8")).getCanonicalFile.getAbsolutePath
          log.debug("console: add " + path)
          paths.append(path)
        }
      }
      if (libPath != null) {
        libPath.listFiles.foreach(f => {
          log.debug("console: add " + f.getAbsolutePath)
          paths.append(f.getAbsolutePath)
        })
      }
    }
    repl.settings.classpath.value = (List(repl.settings.classpath.value) ::: paths.toList).mkString(File.pathSeparator)
    repl.settings.Yreplsync.value = true // !!! Do not use asynchronous code for repl startup or 2.9.1-RC4 lost in space :-(
    repl.settings.embeddedDefaults[Model.type]
    repl
  }
}

object Console extends DependencyInjection.PersistentInjectable with Loggable {
  implicit def console2implementation(p: Console.type): Interface = p.inner
  implicit def bindingModule = DependencyInjection()
  @volatile private var viewPort: Option[View] = None

  /*
   * dependency injection
   */
  def font() = inject[Font]("Debug.Console")
  def inner() = inject[Interface]
  private def viewPortClass = inject[Class[_ <: View]]
  override def beforeInjection(newModule: BindingModule) {
    DependencyInjection.assertLazy[Interface](None, newModule)
    DependencyInjection.assertLazy[Class[_ <: View]](None, newModule)
  }
  override def onClearInjection(oldModule: BindingModule) {
    viewPort.map { view =>
      viewPort = None
      view.dispose
    }
  }

  trait Interface {
    val history: History
    val completion: Completion
    val reader: Reader
    val repl: ILoop

    def echo(msg: String)
    def echoNoNL(msg: String)
    def show()
  }
  trait View {
    /** The output print stream that handle a visible user data */
    val outputPrintStream: PrintStream

    def echoNoNL(msg: String)
    def hide()
    def show()
    def dispose()
  }
}
