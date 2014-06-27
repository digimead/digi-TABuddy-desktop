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

package org.digimead.tabuddy.desktop.core.ui.block

import java.util.UUID
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.ui.Resources
import org.digimead.tabuddy.desktop.core.ui.block.api.XConfiguration.CPlaceHolder
import scala.collection.immutable

/**
 * Stack configuration container. It contains:
 * - elements hierarchy: Stack.Tab/Stack.VSash/Stack.HSash/View
 * - time stamp
 */
case class Configuration(
  /** Stack element configuration. */
  val stack: CPlaceHolder) {
  /** Stack projection that looks like map Id -> (parent Id, configuration). */
  lazy val asMap = toMap(this)
  val timestamp = System.currentTimeMillis()

  /** Dump element hierarchy. */
  def dump(): Seq[String] = this.toString +: stack.dump("")

  /** Create map from configuration. */
  protected def toMap(configuration: Configuration): immutable.HashMap[UUID, (Option[UUID], CPlaceHolder)] = {
    var entry = Seq[(UUID, (Option[UUID], CPlaceHolder))]()
    def visit(stack: CPlaceHolder, parent: Option[UUID]) {
      entry = entry :+ stack.id -> (parent, stack)
      stack match {
        case tab: Configuration.Stack.CTab ⇒
          tab.children.foreach(visit(_, Some(tab.id)))
        case hsash: Configuration.Stack.CHSash ⇒
          visit(hsash.left, Some(hsash.id))
          visit(hsash.right, Some(hsash.id))
        case vsash: Configuration.Stack.CVSash ⇒
          visit(vsash.top, Some(vsash.id))
          visit(vsash.bottom, Some(vsash.id))
        case view: Configuration.CView ⇒
        case empty: Configuration.CEmpty ⇒
      }
    }
    visit(configuration.stack, None)
    immutable.HashMap[UUID, (Option[UUID], CPlaceHolder)](entry: _*)
  }

  override lazy val toString = s"Configuration(timestamp: ${timestamp}, top stack layer: ${stack})"
}

object Configuration extends XLoggable {
  override def toString = "Configuration[Singleton]"

  /** View factory configuration. */
  case class Factory(val bundleSymbolicName: String, val factoryClassName: String) {
    /** Factory singleton instance. */
    @transient
    protected lazy val instance = {
      val fClass = App.bundle(getClass).getBundleContext().getBundles().
        find(_.getSymbolicName() == bundleSymbolicName).map(_.loadClass(factoryClassName)).get
      Resources.factory(factoryClassName).get
    }

    def apply(): View.Factory = instance
    /** Validate if this factory is exists. */
    def validate(): Boolean = try {
      App.bundle(getClass).getBundleContext().getBundles().find(_.getSymbolicName() == bundleSymbolicName).map(_.loadClass(factoryClassName)).nonEmpty
    } catch {
      case e: ClassNotFoundException ⇒ false
      case e: IllegalStateException ⇒ false
      case e: NullPointerException ⇒ false
    }
    override lazy val toString = s"Factory(${factoryClassName})"
  }
  /** Empty view element. */
  case class CEmpty(val id: UUID = UUID.randomUUID()) extends CPlaceHolder {
    /** Dump element hierarchy. */
    def dump(indent: String): Seq[String] = Seq(indent + this.toString)

    override lazy val toString = "CEmpty[%08X]".format(id.hashCode())
  }
  /** View element. */
  case class CView(val factory: Factory, val id: UUID = UUID.randomUUID()) extends CPlaceHolder {
    factory.validate()

    /** Dump element hierarchy. */
    def dump(indent: String): Seq[String] =
      Seq(indent + "CView[%08X#%s]".format(id.hashCode(), id) + " with factory " + factory)
    override lazy val toString = "CView[%08X]".format(id.hashCode())
  }
  /** Stack element of the configuration. */
  sealed trait Stack extends CPlaceHolder
  object Stack {
    /** Tab stack. */
    case class CTab(children: Seq[CView], val id: UUID = UUID.randomUUID()) extends Stack {
      /** Dump element hierarchy. */
      def dump(indent: String): Seq[String] =
        Seq(indent + "CTab[%08X#%s]".format(id.hashCode(), id) + " with views: ") ++ children.map(_.dump("  " + indent)).flatten
      /** Map stack element to the new one. */
      override def map(f: CPlaceHolder ⇒ CPlaceHolder) =
        this.copy(children = this.children.map { child ⇒
          val newElement = child.map(f)
          if (!newElement.isInstanceOf[CView])
            throw new IllegalArgumentException("CTab stack could contains only view elements.")
          newElement.asInstanceOf[CView]
        })
      override lazy val toString = "CTab[%08X]".format(id.hashCode())
    }
    /** Horizontal stack. */
    case class CHSash(left: Stack, right: Stack, val ratio: Double = 0.5, val id: UUID = UUID.randomUUID()) extends Stack {
      /** Dump element hierarchy. */
      def dump(indent: String): Seq[String] =
        Seq(indent + "CHSash[%08X#%s]".format(id.hashCode(), id) + " with parts: ") ++ left.dump("  " + indent) ++ right.dump("  " + indent)
      /** Map stack element to the new one. */
      override def map(f: CPlaceHolder ⇒ CPlaceHolder) =
        this.copy(left = {
          val newElement = left.map(f)
          if (!newElement.isInstanceOf[Stack])
            throw new IllegalArgumentException("CHSash stack could contains only stack elements.")
          newElement.asInstanceOf[Stack]
        }, right = {
          val newElement = right.map(f)
          if (!newElement.isInstanceOf[Stack])
            throw new IllegalArgumentException("CHSash stack could contains only stack elements.")
          newElement.asInstanceOf[Stack]
        })
      override lazy val toString = "CHSash[%08X]".format(id.hashCode())
    }
    /** Vertical stack. */
    case class CVSash(top: Stack, bottom: Stack, val ratio: Double = 0.5, val id: UUID = UUID.randomUUID()) extends Stack {
      /** Dump element hierarchy. */
      def dump(indent: String): Seq[String] =
        Seq(indent + "CVSash[%08X#%s]".format(id.hashCode(), id) + " with parts: ") ++ top.dump("  " + indent) ++ bottom.dump("  " + indent)
      /** Map stack element to the new one. */
      override def map(f: CPlaceHolder ⇒ CPlaceHolder) =
        this.copy(top = {
          val newElement = top.map(f)
          if (!newElement.isInstanceOf[Stack])
            throw new IllegalArgumentException("CVStack stack could contains only stack elements.")
          newElement.asInstanceOf[Stack]
        }, bottom = {
          val newElement = bottom.map(f)
          if (!newElement.isInstanceOf[Stack])
            throw new IllegalArgumentException("CVSash stack could contains only stack elements.")
          newElement.asInstanceOf[Stack]
        })
      override lazy val toString = "CVSash[%08X]".format(id.hashCode())
    }
  }
}
