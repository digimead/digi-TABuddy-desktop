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

package org.digimead.tabuddy

import java.io.File
import java.lang.management.ManagementFactory
import java.net.URL
import java.util.Date

import org.digimead.digi.lib.util.Util
import org.digimead.tabuddy.desktop.Config
import org.digimead.tabuddy.desktop.Main

import com.escalatesoft.subcut.inject.NewBindingModule

/**
 * TABuddy - global TA Buddy space
 *  +-Settings - global TA Buddy settings
 *     +-Templates - global TA Buddy element templates
 *     |  +- TemplateA[Record, Task, Note, ....]
 *     |      +- __template_field_TYPE_ID
 *     |            +- required[Boolean]
 *     |            +- default[TYPE]
 *     |            +- group[String]
 */

package object desktop {
  lazy val default =
    new NewBindingModule(module => {
      module.bind[File] identifiedBy "Config" toSingle {
        /* Config */
        val configName = "tabuddy.conf"
        // get 'data' path from System.getProperty("data") or Main.findJarPath
        val dataPath = Option(System.getProperty("data")).map(new URL(_)).orElse(Option(Main.findJarPath))
        // try to get jar location or get current directory
        val configDirectory = (dataPath match {
          case Some(url) =>
            val jar = new File(url.toURI())
            if (jar.isDirectory() && jar.exists() && jar.canWrite())
              Some(jar) // return exists
            else {
              val jarDirectory = if (jar.isFile()) jar.getParentFile() else jar
              if (jarDirectory.exists() && jarDirectory.canWrite())
                Some(jarDirectory) // return exists
              else {
                if (jarDirectory.mkdirs()) // create
                  Some(jarDirectory)
                else
                  None
              }
            }
          case None =>
            None
        }) getOrElse {
          new File(".")
        }
        new File(configDirectory, configName)
      }
      module.bind[Config.Interface] toModuleSingle { implicit module => new Config }
      module.bind[File] identifiedBy "Log" toModuleSingle { module =>
        new File(module.inject[File](Some("Config")).getParentFile(), "log")
      }
      module.bind[String] identifiedBy "LogReportPrefix" toProvider { module =>
        val id = ManagementFactory.getRuntimeMXBean().getName()
        Util.dateFile(new Date()) + "-P" + id
      }
      module.bind[String] identifiedBy "LogFilePrefix" toSingle { "log" }
      module.bind[String] identifiedBy "TraceFilePrefix" toSingle { "trc" }
      module.bind[Boolean] identifiedBy "TraceFileEnabled" toSingle { true }
    }) ~
      mesh.transport.default ~
      payload.default ~
      payload.view.default ~
      payload.view.comparator.default ~
      payload.view.filter.default ~
      job.default ~
      approver.default ~
      debug.default ~
      org.digimead.tabuddy.model.default ~
      org.digimead.digi.lib.default

  /** Starts application via scala interactive console */
  def mainInteractive(args: Array[String]) {
    val thread = new Thread(new Runnable { def run = Main.main(args) })
    thread.start
  }
}
