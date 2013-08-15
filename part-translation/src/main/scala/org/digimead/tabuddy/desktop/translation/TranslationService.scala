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

package org.digimead.tabuddy.desktop.translation

import java.io.IOException
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.security.AccessController
import java.security.PrivilegedAction
import java.util.Locale
import java.util.Properties

import scala.Array.canBuildFrom
import scala.Option.option2Iterable
import scala.collection.immutable
import scala.collection.mutable
import scala.collection.JavaConversions._

import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.api.Translation
import org.digimead.tabuddy.desktop.definition.Context
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.support.App.app2implementation
import org.eclipse.e4.core.services.translation.TranslationProviderFactory
import org.eclipse.e4.core.services.translation.{ TranslationService => ETranslationService }

import language.implicitConversions

/** Translation service. */
class TranslationService extends Translation with Loggable {
  /** Properties file extension. */
  val EXTENSION = ".properties"
  /** Translation services */
  protected val services = new mutable.HashMap[Locale, ETranslationService]()
  private val translationLock = new Object

  /** Get user translations. */
  @log
  def getUserTranslations(instance: Translation.NLS, locale: Locale): Option[immutable.HashMap[String, String]] =
    loadUserTranslations(instance, locale).map(p => immutable.HashMap(p.toSeq: _*))
  /** Get locale suffixes from the most specific to the most general. Vector(_ru_RU.n, _ru.n, .n). */
  @log
  def nlSuffixes(locale: Locale = Locale.getDefault()) = {
    val parts = locale.toString().split('_')
    val result = for (i <- 0 to parts.size) yield if (i == 0) EXTENSION else "_" + parts.take(i).mkString("_") + EXTENSION
    result.reverse
  }
  /** Set user translations. */
  @log
  def setUserTranslations(instance: Translation.NLS, locale: Locale, translations: Option[immutable.HashMap[String, String]]) =
    saveUserTranslations(instance, locale, translations.map { hashMap =>
      val props = new java.util.Properties
      for ((k, v) <- hashMap) props.setProperty(k, v)
      props
    })
  /** Translate the specific singleton. */
  @log
  def translate(instance: Translation.NLS, locale: Locale, resourceNames: Seq[String]) = Option(System.getSecurityManager()) match {
    case Some(manager) =>
      AccessController.doPrivileged(new PrivilegedAction[AnyRef]() { def run(): AnyRef = load(instance, resourceNames, locale) })
    case None =>
      load(instance, resourceNames, locale)
  }

  /**
   * Build an array of property files to search.  The returned array contains
   * the property fields in order from most specific to most generic.
   * So, in the FR_fr locale, it will return file_fr_FR.properties, then
   * file_fr.properties, and finally file.properties.
   */
  protected def buildVariants(resourceName: Seq[String]) = nlSuffixes().map(suffix => resourceName.map { resourceName =>
    val root = resourceName.replace('.', '/')
    root + suffix
  }).flatten
  /** Load translation. */
  protected def load(instance: Translation.NLS, resourceNames: Seq[String], locale: Locale): AnyRef = {
    val clazz = instance.getClass
    log.debug(s"Load translation for ${clazz} with resource ${resourceNames}.")
    val fieldArray = clazz.getDeclaredFields().filter(field => field.getType() == classOf[String] &&
      Modifier.isPrivate(field.getModifiers) && Modifier.isFinal(field.getModifiers))
    val loader = clazz.getClassLoader()
    val isAccessible = (clazz.getModifiers() & Modifier.PUBLIC) != 0
    val translations = loadUserTranslations(instance, locale) ++
      loadResourceTranslations(instance, resourceNames, locale)
    // Load fields data
    val lost = {
      for (field <- fieldArray) yield try {
        val translation = translations.view.flatMap(properties => Option(properties.getProperty(field.getName))).find(_.nonEmpty)
        translation match {
          case Some(translation) =>
            if (TranslationService.DI.isUTF8)
              modify(instance, field, new String(translation.getBytes("ISO-8859-1"), "UTF-8").
                replaceAll("\\\\n", "\n").replaceAll("\\\\t", "\t"))
            else
              modify(instance, field, translation)
            None
          case None =>
            Some(field) // not found
        }
      } catch {
        case e: Throwable =>
          log.error("Error setting the missing message value for: " + field.getName(), e)
          None
      }
    }.flatten
    if (lost.isEmpty) {
      // There are fields with lost translation
      /** Translation service. */
      val service = translationService(locale)
      val bundleSymbolicName = App.bundle(clazz).getSymbolicName()
      val contributorURI = "platform:/plugin/" + bundleSymbolicName
      lost.foreach { field =>
        try {
          service.translate(field.getName, contributorURI) match {
            case translation if translation != field.getName =>
              if (TranslationService.DI.isUTF8)
                modify(instance, field, new String(translation.getBytes("ISO-8859-1"), "UTF-8").
                  replaceAll("\\\\n", "\n").replaceAll("\\\\t", "\t"))
              else
                modify(instance, field, translation)
              false
            case key =>
              // Set a value for this empty field. We should never get an exception here because
              // we know we have a public static non-final field. If we do get an exception, silently
              // log it and continue. This means that the field will (most likely) be un-initialized and
              // will fail later in the code and if so then we will see both the NPE and this error.
              val value = "NLS missing message: " + field.getName() + " in: " + bundleSymbolicName
              log.warn(value)
              modify(instance, field, value)
          }
        } catch {
          case e: Throwable =>
            log.error("Error setting the missing message value for: " + field.getName(), e)
        }
      }
    }
    null // for PrivilegedAction
  }
  /** Load translation defined by resourceName in the bundle. */
  protected def loadResourceTranslations(instance: Translation.NLS, resourceNames: Seq[String], locale: Locale): Seq[Properties] = {
    val loader = instance.getClass().getClassLoader()
    // search the variants from most specific to most general, since
    // the MessagesProperties.put method will mark assigned fields
    // to prevent them from being assigned twice
    val properties = for (variant <- buildVariants(resourceNames)) yield {
      // loader==null if we're launched off the Java boot classpath
      val input = if (loader == null) ClassLoader.getSystemResourceAsStream(variant) else loader.getResourceAsStream(variant)
      if (input != null)
        try {
          val properties = new Properties()
          properties.load(input)
          Some(properties)
        } catch {
          case e: IOException =>
            log.error("Error loading " + variant, e)
            None
        } finally {
          if (input != null)
            try { input.close() } catch { case e: IOException => }
        }
      else
        None
    }
    properties.flatten
  }
  /** Load translation defined by user for the specific instance and specific locale. */
  protected def loadUserTranslations(instance: Translation.NLS, locale: Locale): Option[Properties] = translationLock.synchronized {
    log.debug(s"Load user translations for ${instance.getClass} and locale ${locale}.")
    None
  }
  /** Modify final field of the current class. */
  protected def modify(instance: Translation.NLS, field: Field, value: String) {
    if (!field.isAccessible())
      field.setAccessible(true)
    val before = field.get(instance)
    field.set(instance, value)
  }
  /** Save translation defined by user for the specific instance and specific locale. */
  protected def saveUserTranslations(instance: Translation.NLS, locale: Locale, properties: Option[Properties]): Unit = translationLock.synchronized {
    if (properties.nonEmpty) {
      log.debug(s"Save user translations for ${instance.getClass} and locale ${locale}.")
    } else {
      log.debug(s"Clear user translations for ${instance.getClass} and locale ${locale}.")
    }
  }
  /** Get translation service from Eclipse. */
  protected def translationService(locale: Locale) = translationLock.synchronized {
    services.get(locale) getOrElse {
      val translationContext = Context("translation")
      translationContext.set(ETranslationService.LOCALE, locale.toString)
      val translationService = TranslationProviderFactory.bundleTranslationService(translationContext)
      services(locale) = translationService
      translationService
    }
  }
}

object TranslationService extends Loggable {
  implicit def translation2implementation(c: TranslationService.type): TranslationService = c.inner

  /** Translation service implementation. */
  def inner() = DI.implementation

  /**
   * Dependency injection routines.
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** Translation service implementation. */
    lazy val implementation = injectOptional[TranslationService] getOrElse new TranslationService()
    /** Flag indicating whether properties are UTF8 files. */
    lazy val isUTF8 = injectOptional[Boolean]("Translation.UTF8") getOrElse true
  }
}
