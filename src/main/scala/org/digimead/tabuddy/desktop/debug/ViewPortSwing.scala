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

import java.awt.BorderLayout
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.io.PrintStream

import scala.tools.nsc.interpreter.Completion

import org.digimead.digi.lib.log.Loggable
import org.digimead.digi.lib.log.logger.RichLogger.rich2slf4j
import org.digimead.tabuddy.desktop.debug.Console.console2implementation

import javax.swing.JFrame
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.SwingUtilities
import javax.swing.WindowConstants

class ViewPortSwing extends JFrame with Console.View with Loggable {
  protected val textArea = new JTextArea()
  protected val cmdText = new JTextField()
  // system output hack
  // system console -> outputPrintStream -> poOut -> piOut -> ReaderThread
  protected val piFromHackForReaderThread = new PipedInputStream()
  protected val poScalaInterpreterHack = new PipedOutputStream(piFromHackForReaderThread)
  protected val fromHackReaderThread = new ReaderThread("HackInputStream", piFromHackForReaderThread)
  val outputPrintStream = new PrintStream(poScalaInterpreterHack, true)
  // interpreter console output
  // echoNoNL -> poEcho -> piFromEchoForReaderThread -> fromEchoReaderThread
  protected val piFromEchoForReaderThread = new PipedInputStream()
  protected val poEcho = new PipedOutputStream(piFromEchoForReaderThread)
  protected val fromEchoReaderThread = new ReaderThread("EchoInputStream", piFromEchoForReaderThread)
  protected var historyShift = 0
  init()

  def echoNoNL(msg: String) = try {
    SwingUtilities.invokeLater(new Runnable() {
      def run() {
        poEcho.write(msg.getBytes)
        poEcho.flush
      }
    })
  } catch {
    case e: Throwable =>
      log.error("unable to write message to interpreter console output (poConsole):" + e, e)
  }
  def init() {
    log.debug("initialize " + getClass.getName())
    // Add a scrolling text area
    textArea.setEditable(false)
    textArea.setRows(20)
    textArea.setColumns(50)
    getContentPane.add(new JScrollPane(textArea), BorderLayout.CENTER)
    // add command input

    cmdText.setFocusTraversalKeysEnabled(false)
    cmdText.addKeyListener(new KeyAdapter() { override def keyPressed(evt: KeyEvent) = onKeyPressed(evt) })
    getContentPane.add(cmdText, BorderLayout.SOUTH)
    if (Console.font != null) {
      textArea.setFont(Console.font)
      cmdText.setFont(Console.font)
    }
    // register show/hide listener
    addComponentListener(new ComponentAdapter() {
      override def componentHidden(e: ComponentEvent) = onHide()
      override def componentShown(e: ComponentEvent) = onShow()
    })
    pack()
    fromHackReaderThread.start()
    fromEchoReaderThread.start()
    setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE)
    setVisible(true)
  }
  def onHide() = synchronized {
    log.debug(getClass.getName() + " hidden")
  }
  def onKeyPressed(evt: KeyEvent) = Console.reader.inputKey.synchronized {
    evt.getKeyCode match {
      case KeyEvent.VK_ENTER =>
        Console.reader.inputLine.set(cmdText.getText())
        cmdText.setText("")
      case KeyEvent.VK_UP =>
        val hlist = Console.reader.history.asStrings
        val hidx = if (hlist.size - historyShift - 1 > 0) hlist.size - historyShift - 1 else 0
        cmdText.setText(hlist(hidx))
        historyShift += 1
      case KeyEvent.VK_DOWN =>
        if (historyShift > 0) {
          historyShift -= 1
          val hlist = Console.reader.history.asStrings
          val hidx = hlist.size - historyShift - 1
          cmdText.setText(hlist(hidx))
        }
      case KeyEvent.VK_TAB =>
        Console.completion.completer
/*        if (Console.completer != null) {
          var autoComplete = ""
          val buf = cmdText.getText()
          val pos = cmdText.getCaretPosition()
          val Completion.Candidates(newCursor, newCandidates) = Console.completer.complete(buf, pos)
          if (newCandidates.size == 1) {
            autoComplete = newCandidates(0)
          } else if (newCandidates.size > 1) {
            Console.echo("\nauto complete:  ")
            var commonFlag = false
            do {
              val autoCompleteSize = autoComplete.size
              if (newCandidates(0).size > autoCompleteSize) {
                commonFlag = newCandidates.forall(_.startsWith(autoComplete + newCandidates(0)(autoCompleteSize)))
                if (commonFlag)
                  autoComplete += newCandidates(0)(autoCompleteSize)
              } else
                commonFlag = false
            } while (commonFlag)
            newCandidates.foreach(Console.echo(_))
          }
          if (autoComplete != "") {
            def intersect(a: String, b: String): String = {
              if (a.isEmpty || b.isEmpty)
                return ""
              if (a.endsWith(b))
                return b
              else
                intersect(a, b.take(b.size - 1))
            }
            val sharedText = intersect(buf, autoComplete)
            if (sharedText.nonEmpty && autoComplete.startsWith(sharedText))
              cmdText.setText(buf + autoComplete.substring(sharedText.size))
            else
              cmdText.setText(buf + autoComplete)
          }
        }*/
      case _ =>
        //println("key " + inputKey.get)
        historyShift = 0
    }
    Console.reader.inputKey.set(evt.getKeyCode)
    Console.reader.inputKey.notifyAll
  }
  def onShow() = synchronized {
    log.debug(getClass.getName() + " shown")
  }
  def update(line: String) = {
    textArea.append(line + "\n")
    // Make sure the last line is always visible
    textArea.setCaretPosition(textArea.getDocument().getLength());
    // Keep the text area down to a certain character size
    val idealSize = 2000
    val maxExcess = 1000
    val excess = textArea.getDocument().getLength() - idealSize
    if (excess >= maxExcess) {
      textArea.replaceRange("", 0, excess)
    }
  }

  /**
   * ReaderThread class that redirect the data flow from PipedInputStreams to update method
   */
  class ReaderThread(val description: String, val pi: PipedInputStream) extends Thread {
    protected val buf = new Array[Byte](1024)
    protected val reader = new BufferedReader(new InputStreamReader(pi))
    protected var lineNum = 0
    var loop = true
    setDaemon(true)

    override def run() = while (loop) try {
      val line = reader.readLine
      ViewPortSwing.this.synchronized {
        SwingUtilities.invokeLater(new Runnable() { def run() = update(line) })
        Thread.sleep(10)
        while (pi.available() > 0) {
          val line = reader.readLine
          SwingUtilities.invokeLater(new Runnable() { def run() = update(line) })
          Thread.sleep(10)
        }
      }
    } catch {
      // catch all exception in the reader thread
      case e: Throwable =>
        log.error(description + ": " + e.getMessage, e)
    }
    protected def read() {

    }
  }
}
