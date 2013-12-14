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

package org.digimead.tabuddy.desktop.ui.block

import java.util.UUID
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.support.App

/**
 * Stack configuration container. It contains:
 * - elements hierarchy: Stack.Tab/Stack.VSash/Stack.HSash/View
 * - time stamp
 */
case class Configuration(
  /** Stack element configuration. */
  val stack: Configuration.PlaceHolder) {
  val timestamp = System.currentTimeMillis()

  /** Dump element hierarchy. */
  def dump(): Seq[String] = this.toString +: stack.dump("")
  override def toString = s"Configuration(timestamp: ${timestamp}, top stack layer: ${stack})"
}

object Configuration extends Loggable {
  /** View factory configuration. */
  case class Factory(val bundleSymbolicName: String, val singletonClassName: String) {
    /** Factory singleton instance. */
    @transient
    protected lazy val singleton = {
      val singletonClass = App.bundle(getClass).getBundleContext().getBundles().
        find(_.getSymbolicName() == bundleSymbolicName).map(_.loadClass(singletonClassName)).get
      singletonClass.getField("MODULE$").get(singletonClass).asInstanceOf[ViewLayer.Factory]
    }
    def apply(): ViewLayer.Factory = singleton
    /** Validate if this factory is exists. */
    def validate(): Boolean = try {
      App.bundle(getClass).getBundleContext().getBundles().find(_.getSymbolicName() == bundleSymbolicName).map(_.loadClass(singletonClassName)).nonEmpty
    } catch {
      case e: ClassNotFoundException ⇒ false
      case e: IllegalStateException ⇒ false
    }
    override def toString() = s"Factory(Symbolic-Name: ${bundleSymbolicName}, Singleton: ${singletonClassName})"
  }
  /** Any element of the configuration. */
  sealed trait PlaceHolder {
    val id: UUID
    log.debug(s"Configuration element ${this} with id ${id} is alive.")

    /** Dump element hierarchy. */
    def dump(indent: String): Seq[String]
    /** Map stack element to the new one. */
    def map(f: PlaceHolder ⇒ PlaceHolder): PlaceHolder = f(this)
  }
  /** View element. */
  case class View(val factory: Factory, val id: UUID = UUID.randomUUID()) extends PlaceHolder {
    factory.validate()

    /** Dump element hierarchy. */
    def dump(indent: String): Seq[String] =
      Seq(indent + "Configuration.View[%08X#%s]".format(id.hashCode(), id) + " with factory " + factory)
    override def toString = "Configuration.View[%08X]".format(id.hashCode())
  }
  /** Stack element of the configuration. */
  sealed trait Stack extends PlaceHolder
  object Stack {
    /** Tab stack. */
    case class Tab(children: Seq[View], val id: UUID = UUID.randomUUID()) extends Stack {
      /** Dump element hierarchy. */
      def dump(indent: String): Seq[String] =
        Seq(indent + "Configuration.Tab[%08X#%s]".format(id.hashCode(), id) + " with views: ") ++ children.map(_.dump("  " + indent)).flatten
      /** Map stack element to the new one. */
      override def map(f: PlaceHolder ⇒ PlaceHolder): PlaceHolder =
        this.copy(children = this.children.map { child ⇒
          val newElement = child.map(f)
          if (!newElement.isInstanceOf[View])
            throw new IllegalArgumentException("Tab stack could contains only view elements.")
          newElement.asInstanceOf[View]
        })
      override def toString = "Configuration.Tab[%08X]".format(id.hashCode())
    }
    /** Horizontal stack. */
    case class HSash(left: Stack, right: Stack, val ratio: Double = 0.5, val id: UUID = UUID.randomUUID()) extends Stack {
      /** Dump element hierarchy. */
      def dump(indent: String): Seq[String] =
        Seq(indent + "Configuration.HSash[%08X#%s]".format(id.hashCode(), id) + " with parts: ") ++ left.dump("  " + indent) ++ right.dump("  " + indent)
      /** Map stack element to the new one. */
      override def map(f: PlaceHolder ⇒ PlaceHolder): PlaceHolder =
        this.copy(left = {
          val newElement = left.map(f)
          if (!newElement.isInstanceOf[Stack])
            throw new IllegalArgumentException("HSash stack could contains only stack elements.")
          newElement.asInstanceOf[Stack]
        }, right = {
          val newElement = right.map(f)
          if (!newElement.isInstanceOf[Stack])
            throw new IllegalArgumentException("HSash stack could contains only stack elements.")
          newElement.asInstanceOf[Stack]
        })
      override def toString = "Configuration.HSash[%08X]".format(id.hashCode())
    }
    /** Vertical stack. */
    case class VSash(top: Stack, bottom: Stack, val ratio: Double = 0.5, val id: UUID = UUID.randomUUID()) extends Stack {
      /** Dump element hierarchy. */
      def dump(indent: String): Seq[String] =
        Seq(indent + "Configuration.VSash[%08X#%s]".format(id.hashCode(), id) + " with parts: ") ++ top.dump("  " + indent) ++ bottom.dump("  " + indent)
      /** Map stack element to the new one. */
      override def map(f: PlaceHolder ⇒ PlaceHolder): PlaceHolder =
        this.copy(top = {
          val newElement = top.map(f)
          if (!newElement.isInstanceOf[Stack])
            throw new IllegalArgumentException("VStack stack could contains only stack elements.")
          newElement.asInstanceOf[Stack]
        }, bottom = {
          val newElement = bottom.map(f)
          if (!newElement.isInstanceOf[Stack])
            throw new IllegalArgumentException("VSash stack could contains only stack elements.")
          newElement.asInstanceOf[Stack]
        })
      override def toString = "Configuration.VSash[%08X]".format(id.hashCode())
    }
  }
}