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

package org.digimead.tabuddy.desktop.common

import java.io.File
import java.net.URL
import java.util.jar.JarFile

import scala.Array.canBuildFrom
import scala.Option.option2Iterable
import scala.collection.JavaConversions._

import org.digimead.digi.lib.util.FileUtil

/**
 * This class MUST not depends on logging engine. It is used before any initialization.
 */
object Common {
  /** Get bundles list from the specific location. */
  def getBundles(pathToBundles: File, recursive: Boolean = true): Array[String] = {
    val root = pathToBundles.toString()
    System.err.println("R " + root)
    val rootLength = root.length()
    val jars = FileUtil.recursiveListFiles(pathToBundles, """.*\.jar""".r)
    jars.map { jar =>
      val relative = jar.toString.substring(rootLength) match {
        case str if str.startsWith(File.separator) => str.substring(1)
        case str => str
      }
      var archive: JarFile = null
      try {
        archive = new JarFile(jar)
        val manifest = archive.getManifest()
        val attributes = manifest.getMainAttributes()
        if (attributes.keySet().exists(_.toString() == "Bundle-SymbolicName"))
          Some("reference:file:" + relative)
        else
          None
      } catch {
        case e: Throwable => // Skip
          None
      } finally {
        if (archive != null)
          try { archive.close } catch { case e: Throwable => }
      }
    }.flatten
  }
  /** Get path to the directory with application data. */
  def getPath(env: String, clazz: Class[_]): File = {
    val path = Option(System.getProperty(env)).map(new URL(_)).orElse(jarLocation(clazz))
    // try to get jar location or get current directory
    val result = (path match {
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
    if (!result.isAbsolute())
      throw new IllegalStateException(s"Unable to get path for '$env', invalid relative path '$result'")
    result
  }
  /** Returns the jar location as URL if any. */
  def jarLocation(clazz: Class[_]): Option[URL] = try {
    val source = clazz.getProtectionDomain.getCodeSource
    if (source != null)
      Option(source.getLocation)
    else
      None
  } catch {
    // catch all possible throwables
    case e: Throwable =>
      None
  }
}
