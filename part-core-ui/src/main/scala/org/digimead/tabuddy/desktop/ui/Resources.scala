/**
 * This file is part of the TA Buddy project.
 * Copyright (c) 2012-2014 Alexey Aksenov ezh@ezh.msk.ru
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

package org.digimead.tabuddy.desktop.ui

import java.util.{ Locale, UUID }
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.Core
import org.digimead.tabuddy.desktop.core.definition.command.Command
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.ui.block.ViewLayer
import org.digimead.tabuddy.desktop.ui.definition.IWizard
import org.eclipse.jface.fieldassist.FieldDecorationRegistry
import org.eclipse.jface.resource.{ ImageDescriptor, JFaceResources }
import org.eclipse.swt.graphics.{ Font, Image }
import org.osgi.framework.{ BundleActivator, BundleContext }
import scala.collection.mutable
import scala.language.implicitConversions

/**
 * Handle application resources.
 */
class Resources extends BundleActivator with Loggable {
  val small = 0.315
  val medium = 0.7
  val large = 1
  /** the large font */
  lazy val fontLarge = {
    val fD = App.display.getSystemFont().getFontData()
    fD.head.setHeight(fD.head.getHeight + 1)
    new Font(App.display, fD.head)
  }
  /** The small font */
  lazy val fontSmall = {
    val fD = App.display.getSystemFont().getFontData()
    fD.head.setHeight(fD.head.getHeight - 1)
    new Font(App.display, fD.head)
  }
  /** List of all application view factories. */
  protected val viewFactoriesMap = new Resources.ViewFactoryMap with mutable.SynchronizedMap[ViewLayer.Factory, Boolean]
  /** Application wizards set. */
  protected val wizardsSet = new mutable.HashSet[Class[_ <: IWizard]]() with mutable.SynchronizedSet[Class[_ <: IWizard]]
  private val lock = new Object

  /** Get factory by singleton class name. */
  def factory(singletonClassName: String): Option[ViewLayer.Factory] =
    viewFactoriesMap.find(_._1.getClass().getName() == singletonClassName).map(_._1)
  /** Get map of factories. */
  def factories() = viewFactoriesMap.toMap
  /** Get image at the specific path and scale to k. */
  def getImage(path: String, k: Double) = {
    val image = ResourceManager.getImage(getClass, path)
    scale(image, k)
  }
  /** Register view factory in the map of the application views. */
  def registerViewFactory(factory: ViewLayer.Factory, enabled: Boolean) = lock.synchronized {
    log.debug(s"Register view factory ${factory}.")
    viewFactoriesMap += factory -> enabled
  }
  /** Register wizard. */
  def registerWizard(clazz: Class[_ <: IWizard]) = lock.synchronized {
    log.debug(s"Register wizard ${clazz.getName}.")
    wizardsSet += clazz
  }
  def scale(image: Image, k: Double): Image = {
    val width = image.getBounds().width
    val height = image.getBounds().height
    new Image(App.display, image.getImageData().scaledTo((width * k).toInt, (height * k).toInt))
  }
  /** Recreate font with specific style */
  def setFontStyle(font: Font, style: Int): Font = {
    val fD = font.getFontData()
    fD.head.setStyle(style)
    new Font(App.display, fD.head)
  }
  @log
  def start(context: BundleContext) {
    log.debug("Initialize application SWT resources.")
    // Reinitialize decoration registry for compatibility with OSGi reloads.
    FieldDecorationRegistry.setDefault(new FieldDecorationRegistry())
    val imageRegistry = JFaceResources.getImageRegistry()
    // Define the images used in the standard decorations.
    imageRegistry.remove("org.eclipse.jface.fieldassist.IMG_DEC_FIELD_CONTENT_PROPOSAL")
    imageRegistry.put("org.eclipse.jface.fieldassist.IMG_DEC_FIELD_CONTENT_PROPOSAL",
      ImageDescriptor.createFromFile(classOf[FieldDecorationRegistry], "images/contassist_ovr.gif"))
    imageRegistry.remove("org.eclipse.jface.fieldassist.IMG_DEC_FIELD_ERROR")
    imageRegistry.put("org.eclipse.jface.fieldassist.IMG_DEC_FIELD_ERROR",
      ImageDescriptor.createFromFile(classOf[FieldDecorationRegistry], "images/error_ovr.gif"))
    imageRegistry.remove("org.eclipse.jface.fieldassist.IMG_DEC_FIELD_WARNING")
    imageRegistry.put("org.eclipse.jface.fieldassist.IMG_DEC_FIELD_WARNING",
      ImageDescriptor.createFromFile(classOf[FieldDecorationRegistry], "images/warn_ovr.gif"))
    imageRegistry.remove("org.eclipse.jface.fieldassist.IMG_DEC_FIELD_REQUIRED")
    imageRegistry.put("org.eclipse.jface.fieldassist.IMG_DEC_FIELD_REQUIRED",
      ImageDescriptor.createFromFile(classOf[FieldDecorationRegistry], "images/required_field_cue.gif"))
    imageRegistry.remove("org.eclipse.jface.fieldassist.IMG_DEC_FIELD_ERROR_QUICKFIX")
    imageRegistry.put("org.eclipse.jface.fieldassist.IMG_DEC_FIELD_ERROR_QUICKFIX",
      ImageDescriptor.createFromFile(classOf[FieldDecorationRegistry], "images/errorqf_ovr.gif"))
    imageRegistry.remove("org.eclipse.jface.fieldassist.IMG_DEC_FIELD_INFO")
    imageRegistry.put("org.eclipse.jface.fieldassist.IMG_DEC_FIELD_INFO",
      ImageDescriptor.createFromFile(classOf[FieldDecorationRegistry], "images/info_ovr.gif"))
    FieldDecorationRegistry.getDefault().registerFieldDecoration("DEC_CONTENT_PROPOSAL",
      JFaceResources.getString("FieldDecorationRegistry.contentAssistMessage"),
      "org.eclipse.jface.fieldassist.IMG_DEC_FIELD_CONTENT_PROPOSAL", imageRegistry)
    FieldDecorationRegistry.getDefault().registerFieldDecoration("DEC_ERROR",
      JFaceResources.getString("FieldDecorationRegistry.errorMessage"),
      "org.eclipse.jface.fieldassist.IMG_DEC_FIELD_ERROR", imageRegistry)
    FieldDecorationRegistry.getDefault().registerFieldDecoration("DEC_ERRORQUICKFIX",
      JFaceResources.getString("FieldDecorationRegistry.errorQuickFixMessage"),
      "org.eclipse.jface.fieldassist.IMG_DEC_FIELD_ERROR_QUICKFIX", imageRegistry)
    FieldDecorationRegistry.getDefault().registerFieldDecoration("DEC_WARNING",
      null, "org.eclipse.jface.fieldassist.IMG_DEC_FIELD_WARNING", imageRegistry)
    FieldDecorationRegistry.getDefault().registerFieldDecoration("DEC_INFORMATION",
      null, "org.eclipse.jface.fieldassist.IMG_DEC_FIELD_INFO", imageRegistry)
    FieldDecorationRegistry.getDefault().registerFieldDecoration("DEC_REQUIRED",
      JFaceResources.getString("FieldDecorationRegistry.requiredFieldMessage"),
      "org.eclipse.jface.fieldassist.IMG_DEC_FIELD_REQUIRED", imageRegistry)
    assert(!Resources.Image.error.isDisposed(), "FieldDecoration resources is already disposed.")
    fontLarge
    fontSmall
    Locale.setDefault(Resources.initialLocale)
  }
  @log
  def stop(context: BundleContext) = {
    log.debug("Dispose application SWT resources.")
    ResourceManager.dispose()
  }
  /** Validate resource leaks on shutdown. */
  def validateOnShutdown() {
    if (viewFactoriesMap.nonEmpty) log.fatal("View Factories leaks: " + viewFactoriesMap)
    if (wizardsSet.nonEmpty) log.fatal("Wizards leaks: " + wizardsSet)
  }
  /** Get set of wizards. */
  def wizards() = lock.synchronized { wizardsSet.toSet }
  /** Remove view factory from the map of the application known views. */
  def unregisterViewFactory(factory: ViewLayer.Factory) = lock.synchronized {
    log.debug("Remove " + factory)
    viewFactoriesMap -= factory
  }
  /** Unregister wizard. */
  def unregisterWizard(clazz: Class[_ <: IWizard]) = lock.synchronized {
    log.debug(s"Unregister wizard ${clazz.getName}.")
    wizardsSet -= clazz
  }

  object Image {
    // We are unable to dispose this resources:
    // This resources initialized only once in static block of org.eclipse.jface.fieldassist.FieldDecorationRegistry
    // If we dispose it at shutdown then after a bundle restart we would have a pack of disposed garbage.
    // Someone may want to restart org.eclipse.jface bundle and all dependencies ;-) lol.
    // So org.eclipse.jface must control it life cycle itself in better world.

    /*
     * From org.eclipse.jface.fieldassist.FieldDecorationRegistry:
     *
     * Registers a field decoration using the specified id. The lifecyle of the
	 * supplied image should be managed by the client. That is, it will never be
	 * disposed by this registry and the decoration should be removed from the
	 * registry if the image is ever disposed elsewhere.
	 *
	 * fucking Eclipse code monkey...
     */
    lazy val error = FieldDecorationRegistry.getDefault().getFieldDecoration(FieldDecorationRegistry.DEC_ERROR).getImage()
    lazy val required = FieldDecorationRegistry.getDefault().getFieldDecoration(FieldDecorationRegistry.DEC_REQUIRED).getImage()
  }
}

/**
 * Application UI resources.
 */
object Resources extends Loggable {
  implicit def resources2implementation(c: Resources.type): Resources = c.inner

  /** Resources implementation. */
  def inner() = DI.implementation
  /** Application initial locale that is used for Locale.setDefault. */
  def initialLocale() = DI.locale

  sealed trait IconTheme {
    val name: String
  }
  object IconTheme {
    case object Light extends Resources.IconTheme { val name = "light" }
    case object Dark extends Resources.IconTheme { val name = "dark" }
  }
  /** Application view map. This class is responsible for action.View command update. */
  class ViewFactoryMap extends mutable.WeakHashMap[ViewLayer.Factory, Boolean] {
    override def +=(kv: (ViewLayer.Factory, Boolean)): this.type = {
      val (key, value) = kv
      if (keys.exists(_.name == key.name))
        throw new IllegalArgumentException(s"View with name '${key.name.name}' is already exists.")
      super.+=(kv)
    }
  }
  /**
   * Dependency injection routines
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** Resources implementation. */
    lazy val implementation = injectOptional[Resources] getOrElse new Resources()
    /** Application locale. */
    lazy val locale = injectOptional[Locale] getOrElse Locale.getDefault()
    /** Icon theme. */
    lazy val theme = injectOptional[Resources.IconTheme] getOrElse IconTheme.Light
  }
}
