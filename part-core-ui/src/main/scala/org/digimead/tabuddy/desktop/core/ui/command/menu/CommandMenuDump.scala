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

package org.digimead.tabuddy.desktop.core.ui.command.menu

import akka.pattern.ask
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import org.digimead.tabuddy.desktop.core.command.PathParser
import org.digimead.tabuddy.desktop.core.definition.command.Command
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.support.Timeout
import org.digimead.tabuddy.desktop.core.ui.{ Messages, SmartMenuManager }
import org.digimead.tabuddy.desktop.core.ui.block.WindowSupervisor
import org.digimead.tabuddy.desktop.core.ui.definition.widget.AppWindow
import org.eclipse.jface.action.{ ActionContributionItem, IContributionItem, MenuManager, Separator }
import org.eclipse.ui.actions.CompoundContributionItem
import org.yaml.snakeyaml.Yaml
import scala.collection.immutable.ListMap
import scala.concurrent.{ Await, Future }

/**
 * Dump menu structure to console or file.
 */
object CommandMenuDump {
  import Command.parser._
  private val windowArg = "-window"
  private val verboseArg = "-verbose"
  /** Akka execution context. */
  implicit lazy val ec = App.system.dispatcher
  /** Actor request timeout. */
  implicit lazy val timeout = akka.util.Timeout(Timeout.short)
  /** Command description. */
  implicit lazy val descriptor = Command.Descriptor(UUID.randomUUID())(Messages.menu_dump_text,
    Messages.menu_dumpDescriptionShort_text, Messages.menu_dumpDescriptionLong_text,
    (activeContext, parserContext, parserResult) ⇒ Future {
      parserResult match {
        case Some(file: File) ⇒
          // dump generic to file
          dumpGenericToFile(file)
        case Some(~(windowArg, ~(verboseArg, window: String))) ⇒
          // dump window
          val pointerMap = Await.result((WindowSupervisor.actor ? App.Message.Get(WindowSupervisor.PointerMap)).
            mapTo[Map[UUID, WindowSupervisor.WindowPointer]], timeout.duration)
          pointerMap.find { case (id, pointer) ⇒ "%08X".format(id.hashCode()) == window }.map {
            case (id, pointer) ⇒ dumpWindowMenu(pointer, true)
          }.getOrElse {
            val message = "Unable to find window " + window
            log.error(message)
            message
          }
        case Some(~(windowArg, window: String)) ⇒
          // dump window verbose
          val pointerMap = Await.result((WindowSupervisor.actor ? App.Message.Get(WindowSupervisor.PointerMap)).
            mapTo[Map[UUID, WindowSupervisor.WindowPointer]], timeout.duration)
          pointerMap.find { case (id, pointer) ⇒ "%08X".format(id.hashCode()) == window }.map {
            case (id, pointer) ⇒ dumpWindowMenu(pointer, false)
          }.getOrElse {
            val message = "Unable to find window " + window
            log.error(message)
            message
          }
        case Some(~(windowArg, ~(window: String, file: File))) ⇒
          // dump window to file
          val pointerMap = Await.result((WindowSupervisor.actor ? App.Message.Get(WindowSupervisor.PointerMap)).
            mapTo[Map[UUID, WindowSupervisor.WindowPointer]], timeout.duration)
          pointerMap.find { case (id, pointer) ⇒ "%08X".format(id.hashCode()) == window }.map {
            case (id, pointer) ⇒ dumpWindowToFile(pointer, file)
          }.getOrElse {
            val message = "Unable to find window " + window
            log.error(message)
            message
          }
        case None ⇒
          // dump generic
          dumpGeneric()
      }
    })
  /** Command parser. */
  lazy val parser = Command.CmdParser(descriptor.name ~>
    opt(sp ~> (((windowArg, Command.Hint(windowArg, Some("show actual configuration of the specific window"))) ~ (windowVerbose | windowFile | window)) |
      pathParser ^^ {
        case file: File if file.exists() && file.isFile() ⇒ file
        case file: File if !file.exists() ⇒ file
        case file ⇒ throw new Command.ParseException(s"Incorrect file '$file'.")
      })))

  /** Path argument parser. */
  def pathParser = PathParser(() ⇒ SmartMenuManager.menuConfiguration, () ⇒ "menu configuration location",
    () ⇒ Some(s"Path to menu file"), "the configuration file") { _ ⇒ true }

  /** Convert IContributionItem to nested array of (String, Boolean) */
  protected def convertToNestedArray(items: ListMap[IContributionItem, Option[_]]): Array[_] =
    items.toArray.map {
      case (item: IContributionItem, Some(map)) ⇒
        Array(item.getId, new AtomicBoolean(item.isVisible()), convertToNestedArray(map.asInstanceOf[ListMap[IContributionItem, Option[_]]]))
      case (item: IContributionItem, None) if item.getId == null ⇒
        Array()
      case (item: IContributionItem, None) ⇒
        Array(item.getId, new AtomicBoolean(item.isVisible()))
    }
  /** Convert IContributionItem to map of (String, String, Boolean) */
  protected def convertToListMap(items: ListMap[IContributionItem, Option[_]]): ListMap[(String, String, Boolean), Option[_]] =
    items.map {
      case (mm: MenuManager, map) ⇒
        (mm.getId, mm.getMenuText, mm.isVisible()) -> map.map(i ⇒ convertToListMap(i.asInstanceOf[ListMap[IContributionItem, Option[_]]]))
      case (item: ActionContributionItem, map) ⇒
        (item.getId, item.getAction.getText, item.isVisible()) -> map.map(i ⇒ convertToListMap(i.asInstanceOf[ListMap[IContributionItem, Option[_]]]))
      case (item: CompoundContributionItem, map) ⇒
        (item.getId, "-", item.isVisible()) -> map.map(i ⇒ convertToListMap(i.asInstanceOf[ListMap[IContributionItem, Option[_]]]))
      case (item, map) if item.getId == null && item.getClass == classOf[Separator] ⇒
        (System.identityHashCode(item).toString(), null, item.isVisible()) -> map.map(i ⇒ convertToListMap(i.asInstanceOf[ListMap[IContributionItem, Option[_]]]))
      case (item, map) ⇒
        log.warn(s"Unknown menu item type for (${item.getId}, ${item.getId}): " + item.getClass)
        (item.getId, item.getId, item.isVisible()) -> map.map(i ⇒ convertToListMap(i.asInstanceOf[ListMap[IContributionItem, Option[_]]]))
    }
  /** Dump generic configuration. */
  protected def dumpGeneric(): String =
    dumpMenuItems(SmartMenuManager.generic, 0)
  /** Dump generic configuration to file. */
  protected def dumpGenericToFile(file: File): String = {
    val items = App.execNGet { SmartMenuManager.generic }
    if (file.exists())
      file.delete()
    printToFile(file) { _.print(dumpMenuItems(items, 0)) }
    "Dump menu structure to " + file
  }
  /** Dump menu items. */
  protected def dumpMenuItems(items: Array[_], padding: Int): String =
    synchronized { (new Yaml).dump(SmartMenuManager.convertArrayToList(items)) }
  /** Dump menu items verbose. */
  protected def dumpMenuItemsVerbose(items: ListMap[(String, String, Boolean), Option[_]], padding: Int): Seq[String] = {
    val strings = items.toSeq.map {
      case ((id, text, visible), Some(mm)) ⇒
        val content = dumpMenuItemsVerbose(mm.asInstanceOf[ListMap[(String, String, Boolean), Option[_]]], padding + 2)
        val head = Seq(" " * padding + s"""${id} "${text}" - ${if (visible) "visible" else "hidden"} """)
        head ++ content
      case ((id, null, visible), None) ⇒
        Seq(" " * padding + s"""- separator -""")
      case ((id, text, visible), None) ⇒
        Seq(" " * padding + s"""${id} "${text}" - ${if (visible) "visible" else "hidden"} """)
    }
    strings.flatten
  }
  /** Dump window menu to file. */
  protected def dumpWindowToFile(pointer: WindowSupervisor.WindowPointer, file: File): String = {
    Await.result((pointer.windowActor ? App.Message.Get(AppWindow)).mapTo[Option[AppWindow]], timeout.duration) match {
      case Some(window) ⇒
        val items = App.execNGet { SmartMenuManager.collectForMenuManager(window.getMenuBarManager) }
        if (file.exists())
          file.delete()
        printToFile(file) { _.print(dumpMenuItems(convertToNestedArray(items), 0)) }
        "Dump menu structure to " + file
      case None ⇒
        val message = "Unable to get window from " + pointer
        log.error(message)
        message
    }
  }
  /** Dump window menu. */
  protected def dumpWindowMenu(pointer: WindowSupervisor.WindowPointer, verbose: Boolean): String = {
    Await.result((pointer.windowActor ? App.Message.Get(AppWindow)).mapTo[Option[AppWindow]], timeout.duration) match {
      case Some(window) ⇒
        val items = App.execNGet { SmartMenuManager.collectForMenuManager(window.getMenuBarManager) }
        if (verbose)
          dumpMenuItemsVerbose(convertToListMap(items), 0).mkString("\n")
        else
          dumpMenuItems(convertToNestedArray(items), 0)
      case None ⇒
        val message = "Unable to get window from " + pointer
        log.error(message)
        message
    }
  }
  /** Print content to file. */
  protected def printToFile(f: java.io.File)(op: java.io.PrintWriter ⇒ Unit) {
    val p = new java.io.PrintWriter(f)
    try { op(p) } finally { p.close() }
  }
  /** Create parser with list of windows. */
  protected def window: Command.parser.Parser[Any] = sp ~> commandRegex("\\w+".r, WindowHintContainer)
  /** Create parser with list of windows and verbose option. */
  protected def windowFile = window ~ (sp ~> (pathParser ^^ {
    case file: File if file.exists() && file.isFile() ⇒ file
    case file: File if !file.exists() ⇒ file
    case file ⇒ throw new Command.ParseException(s"Incorrect file '$file'.")
  }))
  /** Create parser with list of windows and verbose option. */
  protected def windowVerbose = sp ~> (verboseArg, Command.Hint(verboseArg, Some("show configuration with menu names"))) ~ window

  /** Hint container for list of windows. */
  object WindowHintContainer extends Command.Hint.Container {
    /** Get parser hints for user provided argument. */
    def apply(arg: String): Seq[Command.Hint] = {
      val pointerMap = Await.result((WindowSupervisor.actor ? App.Message.Get(WindowSupervisor.PointerMap)).
        mapTo[Map[UUID, WindowSupervisor.WindowPointer]], timeout.duration)
      val windows = pointerMap.map { case (id, pointer) ⇒ "%08X".format(id.hashCode()) }.toSeq
      windows.sorted.filter(_.startsWith(arg)).map(proposal ⇒
        Command.Hint(proposal, None, Seq(proposal.drop(arg.length)))).filter(_.completions.head.nonEmpty)
    }
  }
}
