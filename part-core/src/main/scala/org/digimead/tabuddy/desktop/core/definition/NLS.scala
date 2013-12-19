/**
 * This file is part of the TA Buddy project.
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

package org.digimead.tabuddy.desktop.core.definition

import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.security.AccessController
import java.security.PrivilegedAction
import java.util.Locale

import scala.Array.canBuildFrom
import scala.collection.immutable
import scala.collection.mutable
import scala.concurrent.Future

import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.api.Translation
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.core.support.App.app2implementation
import org.digimead.tabuddy.desktop.core.support.Timeout
import org.osgi.util.tracker.ServiceTracker

/**
 * Apply translation to singleton.
 */
abstract class NLS extends Translation.NLS {
  this: Loggable ⇒
  val T = new TranslationImplementation {}
  log.debug(this + " NLS singleton is alive")

  trait TranslationImplementation extends Translation {
    @volatile protected var messageMap = immutable.ListMap[String, String]()

    /** Message map accessor. */
    def messages() = messageMap
    /** Translate the current singleton. */
    def ranslate(resourceNames: Seq[String], locale: Locale): Unit = {
      val context = App.bundle(NLS.this.getClass).getBundleContext()
      NLS.translationService match {
        case Some(service) ⇒
          service.translate(NLS.this, locale, resourceNames)
        case None ⇒
          log.error("Translation service not found.")
          try {
            Option(System.getSecurityManager()) match {
              case Some(manager) ⇒
                AccessController.doPrivileged(new PrivilegedAction[AnyRef]() {
                  def run(): AnyRef = NLS.this.getClass.getDeclaredFields().filter(field ⇒ field.getType() == classOf[String] &&
                    Modifier.isPrivate(field.getModifiers) && Modifier.isFinal(field.getModifiers)).map(modify)
                })
              case None ⇒
                NLS.this.getClass.getDeclaredFields().filter(field ⇒ field.getType() == classOf[String] &&
                  Modifier.isPrivate(field.getModifiers) && Modifier.isFinal(field.getModifiers)).map(modify)
            }
          } catch {
            case e: Throwable ⇒
              log.error(s"Unable to apply translation to ${NLS.this.getClass}: " + e.getMessage, e)
          }
      }
      updateMessages()
      NLS.register(NLS.this, resourceNames)
    }

    /** Generate message map. */
    protected def buildMessageMap(): immutable.ListMap[String, String] = immutable.ListMap(
      NLS.this.getClass.getDeclaredFields().filter(field ⇒ field.getType() == classOf[String] &&
        Modifier.isPrivate(field.getModifiers) && Modifier.isFinal(field.getModifiers)).sortBy(_.getName).
        map { field ⇒
          if (!field.isAccessible())
            field.setAccessible(true)
          (field.getName(), field.get(NLS.this).asInstanceOf[String])
        }: _*)
    /** Modify final field of the current class. */
    protected def modify(field: Field): AnyRef = {
      if (!field.isAccessible())
        field.setAccessible(true)
      val before = field.get(NLS.this)
      if (before == null || before == "") {
        // Set a value for this empty field. We should never get an exception here because
        // we know we have a public static non-final field. If we do get an exception, silently
        // log it and continue. This means that the field will (most likely) be un-initialized and
        // will fail later in the code and if so then we will see both the NPE and this error.
        log.error(s"NLS missing message: ${field.getName()} in: ${NLS.this.getClass}. Translation service not found.")
        field.getName() match {
          case message if message.endsWith("_text") ⇒
            field.set(NLS.this, field.getName().dropRight(5))
          case message ⇒
            field.set(NLS.this, field.getName())
        }

      }
      null // for PrivilegedAction
    }
    /** Update map with messages. */
    protected def updateMessages() {
      try {
        messageMap = Option(System.getSecurityManager()) match {
          case Some(manager) ⇒
            AccessController.doPrivileged(new PrivilegedAction[immutable.ListMap[String, String]]() {
              def run() = buildMessageMap
            })
          case None ⇒
            buildMessageMap
        }
      } catch {
        case e: Throwable ⇒
          log.error(s"Unable to get translation values for ${NLS.this.getClass}: " + e.getMessage, e)
      }
    }
  }
}

object NLS {
  /** Akka execution context. */
  implicit lazy val ec = App.system.dispatcher
  /** NLS consolidated messages cache. */
  @volatile private var cache: Option[immutable.ListMap[String, String]] = None
  /** Translation service tracker. */
  @volatile private var translationServiceTracker: Option[ServiceTracker[Translation, Translation]] = None
  /** All registered NLS singletons. */
  private val registry = mutable.WeakHashMap[NLS, Seq[String]]()
  /** Translation service. */
  lazy val translationService = translationServiceTracker.flatMap(tracker ⇒ Option(tracker.waitForService(Timeout.short.toMillis)))

  /** Get hash map with all messages (key -> translated value). */
  def messages(): immutable.ListMap[String, String] = registry.synchronized {
    cache getOrElse {
      cache = Some(immutable.ListMap(NLS.list.map(_.T.messages).reduce((a, b) ⇒ a ++ b).toList.sortBy(_._1): _*)) // Intersections is out of scope
      cache.get
    }
  }
  /** Get hash map with messages from the specific NLS singleton. */
  def messages(singleton: NLS): immutable.ListMap[String, String] = singleton.T.messages
  /** List all registered singletons with translation. */
  def list = registry.keys
  /** Add singleton to registry. */
  def register(singleton: NLS, resourceNames: Seq[String]) = registry.synchronized {
    registry(singleton) = resourceNames
    cache = None
  }
  /** Reload translations. */
  def reload(locale: Locale = Locale.getDefault()) = App.exec { // Translation must be visible for UI
    registry.synchronized {
      registry.foreach { case (nls, resourceName) ⇒ nls.T.ranslate(resourceName, locale) }
      cache = None
    }
  }

  /** Trait that provides access to translationServiceTracker. */
  trait Initializer {
    def setTranslationServiceTracker(arg: Option[ServiceTracker[Translation, Translation]]) = {
      translationServiceTracker = arg
      // Get translation service in the separate thread.
      Future { translationService }
    }
  }
}
