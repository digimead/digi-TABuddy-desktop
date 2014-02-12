/**
 * This file is part of the TA Buddy project.
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

import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.{ Core, definition }
import org.eclipse.e4.core.contexts.{ EclipseContextFactory, IEclipseContext }
import org.eclipse.e4.core.internal.contexts.EclipseContext
import scala.annotation.tailrec
import scala.collection.JavaConversions.{ asJavaCollection, asScalaSet, mapAsScalaMap }

trait Context {
  this: Loggable with Generic ⇒

  /** Dump context hierarchy acquired from Core context. */
  def contextDumpHierarchy(filter: String ⇒ Boolean = (key) ⇒ true, brief: Boolean = true): String =
    contextDumpHierarchy(Core.context, filter, brief)
  /** Dump context hierarchy. */
  def contextDumpHierarchy(ctx: IEclipseContext, filter: String ⇒ Boolean, brief: Boolean): String = {
    var result = ""
    val root = getContextParents(ctx).headOption getOrElse ctx
    val native = root match {
      case ctx: EclipseContext ⇒ ctx
      case other ⇒ throw new IllegalArgumentException("Context root is not EclipseContext instance.")
    }
    var sequence = Seq[EclipseContext]()
    def process(ctx: EclipseContext, prefix: String = ""): String = {
      val head = if (prefix.isEmpty()) "  " + ctx.toString() else prefix + "+-- " + ctx.toString()
      sequence = sequence :+ ctx
      val children = ctx.getChildren().toSeq.sortBy(_.toString())
      (head +: children.map(c ⇒ process(c, prefix + "  "))).mkString("\n")
    }
    val summary = process(native)
    val details = sequence.map { ctx ⇒
      val head = s"Context: $ctx [parrent '${ctx.getParent()}']"
      val staticKeys = ctx.localData().keys
      val dynamicKeys = ctx.cachedCachedContextFunctions().keys
      val entries = if (brief)
        (staticKeys ++ dynamicKeys).filter(filter).toSeq.sorted.map(key ⇒
          if (dynamicKeys.contains(key))
            "* " + key // dynamic value
          else
            key)
      else
        (staticKeys ++ dynamicKeys).filter(filter).toSeq.sorted.map(key ⇒
          if (dynamicKeys.contains(key))
            "* " + key + ": " + ctx.getLocal(key) // dynamic value
          else
            key + ": " + ctx.getLocal(key))
      (head +: entries.map("  " + _)).mkString("\n")
    }
    result += s"summary:\n$summary\n\ndetails:${details.mkString("\n")}\n\n"
    result
  }
  /** Find context in branch with specific name if any */
  def findBranchContextByName(contextLeaf: definition.Context, contextName: String): Option[definition.Context] =
    if (contextLeaf.getLocal(EclipseContext.DEBUG_STRING) == contextName)
      Some(contextLeaf)
    else
      Option(contextLeaf.getParent()) flatMap { parentContext ⇒ findBranchContextByName(parentContext, contextName) }
  /** Find child context by name if any */
  def findChildContextByName(container: definition.Context, childName: String): Option[definition.Context] = {
    val native = container match {
      case ctx: definition.Context ⇒ ctx
      case other ⇒ throw new IllegalArgumentException(s"Context ${container} is not Context instance.")
    }
    native.getChildren().asInstanceOf[java.util.Set[definition.Context]].find(ctx ⇒ ctx.internalGet(ctx, EclipseContext.DEBUG_STRING, true) == childName)
  }
  /** Get context parents. */
  def contextParents(ctx: IEclipseContext): Seq[IEclipseContext] = getContextParents(ctx, Seq())
  /** Get context children. */
  def contextChildren(ctx: EclipseContext): Seq[EclipseContext] = getContextChildren(Seq(ctx))
  /** Get active leaf. */
  def getActiveLeaf(): definition.Context = Core.context.getActiveLeaf
  /** Get root context. */
  def rootContext = EclipseContextFactory.getServiceContext(bundle(getClass).getBundleContext()) match {
    case ctx: EclipseContext ⇒ ctx
    case other ⇒ throw new IllegalArgumentException(s"Root context is not EclipseContext instance.")
  }

  /** Get parent children. */
  @tailrec
  private def getContextParents(ctx: IEclipseContext, acc: Seq[IEclipseContext] = Seq()): Seq[IEclipseContext] = {
    val parent = Option(ctx.getParent())
    if (parent.isEmpty) return acc
    getContextParents(parent.get, parent.get +: acc)
  }
  /** Get context children. */
  @tailrec
  private def getContextChildren(ctx: Seq[EclipseContext], acc: Seq[EclipseContext] = Seq()): Seq[EclipseContext] = {
    val children = ctx.map(_.getChildren().toSeq).flatten
    if (children.isEmpty) return acc.distinct
    getContextChildren(children, children ++ acc)
  }
}
