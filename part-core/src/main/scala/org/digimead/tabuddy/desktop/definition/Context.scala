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

package org.digimead.tabuddy.desktop.definition

import scala.collection.JavaConversions._
import scala.collection.immutable

import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.App.app2implementation
import org.eclipse.e4.core.contexts.IEclipseContext
import org.eclipse.e4.core.internal.contexts.EclipseContext

import language.implicitConversions

/**
 * EclipseContext wrapper.
 */
class Context(parent: EclipseContext) extends EclipseContext(parent) {
  override def createChild(): IEclipseContext = new Context(Context.this)
  override def createChild(name: String): IEclipseContext = {
    val result = createChild()
    result.set(EclipseContext.DEBUG_STRING, name)
    result
  }
  /** Get context parent. */
  override def getParent(): Context = super.getParent.asInstanceOf[Context]
  /** Get context parents. */
  def getParents(): Seq[Context] = App.contextParents(Context.this).asInstanceOf[Seq[Context]]
  override def modify(name: String, value: AnyRef) = {
    super.modify(name, value)
    Context.Event.publish(name, Context.this)
  }
  override def modify[T](clazz: Class[T], value: T) = {
    super.modify(clazz, value)
    Context.Event.publish(clazz.getName, Context.this)
  }
  override def remove(name: String) = {
    super.remove(name)
    Context.Event.publish(name, Context.this)
  }
  override def remove(clazz: Class[_]) = {
    super.remove(clazz)
    Context.Event.publish(clazz.getName, Context.this)
  }
  override def set(name: String, value: AnyRef) = {
    super.set(name, value)
    Context.Event.publish(name, Context.this)
  }
  override def set[T](clazz: Class[T], value: T) = {
    super.set(clazz, value)
    Context.Event.publish(clazz.getName, Context.this)
  }
}

object Context {
  implicit def appContext2rich(a: Context): Rich = new Rich(a)
  implicit def rich2appContext(r: Rich): Context = r.context

  /** Create new context. */
  def apply(name: String): Context = {
    val result = new Context(null)
    result.set(EclipseContext.DEBUG_STRING, name)
    result
  }

  /**
   * Most important is readability. Speed and memory is not significant.
   * Reduce technical debt of original EclipseContext implementation.
   */
  class Rich(val context: Context = new Context(null)) {
    def createChild(): Context = context.createChild().asInstanceOf[Context]
    def createChild(name: String): Context = context.createChild(name).asInstanceOf[Context]
    def getActiveChild(): Option[Context] = Option(context.getActiveChild().asInstanceOf[Context])
    def getActiveLeaf(): Context = context.getActiveLeaf().asInstanceOf[Context]
    def getActive[T](clazz: Class[T]): T = context.getActive(clazz)
    def getActive[T](name: String): T = context.getActive(name).asInstanceOf[T]
    def getChildren(): Set[Context] = (context.getChildren.asInstanceOf[java.util.Set[Context]]).toSet
    def getLocal[T](name: String): Option[T] = Option(context.getLocal(name).asInstanceOf[T])
    def getParent(): Option[Context] = Option(context.getParent()).map(p => p.asInstanceOf[Context])
    def get[T](name: String): Option[T] = Option(context.get(name).asInstanceOf[T])
  }

  /** Everything is based on string. Erasure is out of scope :-( Do we need rewrite EclipseContext completely? */
  class Listener(val name: String, val f: (String, Context) => Any)
  class Event {
    /** Key listeners. */
    @volatile protected var listeners = immutable.HashMap[String, Seq[Listener]]()

    /** Publish context event. */
    def publish(name: String, context: Context) { listeners.get(name).foreach { value => value.foreach(_.f(name, context)) } }
    /** Publish context event. */
    def publish[T](clazz: Class[T], context: Context) { publish(clazz.getName, context) }
    /** Subscribe to name events. */
    def subscribe[T](name: String, f: (String, Context) => T): Listener = synchronized {
      val listener = new Listener(name, f)
      listeners.get(name) match {
        case Some(seq) =>
          listeners = listeners.updated(name, seq :+ listener)
          listener
        case None =>
          listeners = listeners.updated(name, Seq(listener))
          listener
      }
    }
    /** Subscribe to class events. */
    def subscribe[T](clazz: Class[T], f: (String, Context) => T): Listener =
      subscribe(clazz.getName(), f)
    /** Unsubscribe listener. */
    def unsubscribe[T](listener: Listener) = synchronized {
      listeners.get(listener.name).foreach { seq =>
        seq.filterNot(_ == listener) match {
          case Nil =>
            listeners = listeners - listener.name
          case seq =>
            listeners = listeners.updated(listener.name, seq)
        }
      }
    }
  }
  /**
   * Publish modify events.
   */
  object Event {
    implicit def event2implementation(c: Event.type): Event = c.inner

    /** Event implementation. */
    def inner() = DI.implementation

    /**
     * Dependency injection routines.
     */
    private object DI extends DependencyInjection.PersistentInjectable {
      /** Event implementation. */
      lazy val implementation = injectOptional[Event] getOrElse new Event
    }
  }
}
