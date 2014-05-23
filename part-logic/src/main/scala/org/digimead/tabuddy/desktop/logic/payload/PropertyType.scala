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

package org.digimead.tabuddy.desktop.logic.payload

import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.support.WritableValue
import org.digimead.tabuddy.desktop.logic.Default
import org.digimead.tabuddy.desktop.logic.payload.marker.GraphMarker
import org.digimead.tabuddy.desktop.core.ui.support.Validator
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.dsl.DSLType
import org.digimead.tabuddy.model.element.Element
import org.digimead.tabuddy.model.graph.Graph
import org.eclipse.jface.viewers.{ CellEditor, ComboViewer, LabelProvider, ViewerCell }
import org.eclipse.swt.SWT
import org.eclipse.swt.events.VerifyEvent
import org.eclipse.swt.graphics.{ Color, Font, Image, Point }
import org.eclipse.swt.widgets.{ Composite, Control }
import org.eclipse.ui.forms.widgets.FormToolkit
import scala.collection.immutable

/**
 * Base class of the handler for the property of the particular type
 * The equality is based on id: Symbol
 */
trait PropertyType[T <: AnySRef] extends api.PropertyType[T] {
  /** The property that determines that enumeration is supported */
  val enumerationSupported: Boolean
  /** The property type name */
  val id: Symbol
  /** The type class */
  val typeClass: Class[T]
  /** The type symbol */
  lazy val typeSymbol: Symbol = DSLType.classSymbolMap(typeClass)
  /** The property that contains an adapter for the given type */
  def adapter(): PropertyType.Adapter[T]

  /**
   * Result of comparing 'value1' with 'value2'.
   * returns `x' where
   * x < 0 iff value1 < value2
   * x == 0 iff value1 == value2
   * x > 0 iff value1 > value2
   */
  def compare(value1: T, value2: T): Int
  /** Create an editor for the given type */
  def createEditor(initial: Option[T], propertyId: Symbol, element: Element): PropertyType.Editor[T]
  /** Returns the new value */
  def createValue: T
  /** Returns an iterator for the new value generation */
  def createValues: Iterator[T]
  /** Create a viewer for the given type */
  def createViewer(initial: Option[T], propertyId: Symbol, element: Element): PropertyType.Viewer[T]
  /** Get name of the ptype from the type schema */
  def name(graph: Graph[_ <: Model.Like]): String = TypeSchema.getTypeName(GraphMarker(graph), id)
  /** Convert value to string */
  def valueToString(value: T): String
  /** Convert string to value */
  def valueFromString(value: String): T

  def canEqual(other: Any) =
    other.isInstanceOf[org.digimead.tabuddy.desktop.logic.payload.PropertyType[_]]
  override def equals(other: Any) = other match {
    case that: org.digimead.tabuddy.desktop.logic.payload.PropertyType[_] ⇒
      (this eq that) || {
        that.canEqual(this) &&
          id == that.id
      }
    case _ ⇒ false
  }
  override def hashCode() = id.hashCode
  override def toString() = s"PropertyType[$id]"
}

object PropertyType extends Loggable {
  type genericAdapter = Adapter[AnySRef]
  type genericViewer = Viewer[AnySRef]
  type genericEditor = Editor[AnySRef]

  /** Get the default type class (for new element property, for example) */
  def defaultType(): api.PropertyType[_ <: AnySRef] = DI.types.get('String).getOrElse(DI.types.head._2)
  /** Get type wrapper map */
  def container = DI.types
  /** Get type wrapper of the specific type */
  def get[T <: AnySRef](id: Symbol) = DI.types(id).asInstanceOf[PropertyType[T]]

  /**
   * Element property adapter
   */
  abstract class Adapter[A <: AnySRef] extends api.PropertyType.Adapter[A] {
    /** Cell label provider singleton with limited API for proxy use case */
    val cellLabelProvider: CellLabelProviderAdapter[A]
    /** Label provider singleton with limited API for proxy use case */
    val labelProvider: LabelProviderAdapter[A]

    /** Get a cell editor */
    def createCellEditor(parent: Composite): CellEditor = createCellEditor(parent, SWT.NONE)
    /** Get a cell editor */
    def createCellEditor(parent: Composite, style: Int): CellEditor
    /** Get a LabelProveder */
    def createLabelProvider(): LabelProvider
    /** Get an enumeration LabelProveder */
    def createEnumerationLabelProvider(): LabelProvider = new EnumerationLabelProvider
  }
  /**
   * Element property trait that provides an editor widget
   */
  trait Editor[T <: AnySRef] extends Viewer[T] with api.PropertyType.Editor[T] {
    /** An actual value */
    val data: WritableValue[T]
    /** An actual value container */
    val element: Element

    /** Add the validator */
    def addValidator(control: Control, showOnlyOnFocus: Boolean = true): Option[Validator]
    /** Get a combo viewer UI control */
    def createCControl(parent: Composite): ComboViewer = createCControl(parent, SWT.NONE)
    /** Get a combo viewer UI control */
    def createCControl(parent: Composite, style: Int): ComboViewer = createCControl(parent, style, 50)
    /** Get a combo viewer UI control */
    def createCControl(parent: Composite, style: Int, updateDelay: Int): ComboViewer
    /** Get a combo viewer UI control */
    def createCControl(toolkit: FormToolkit, parent: Composite): ComboViewer = createCControl(toolkit, parent, SWT.NONE)
    /** Get a combo viewer UI control */
    def createCControl(toolkit: FormToolkit, parent: Composite, style: Int): ComboViewer = createCControl(toolkit, parent, style, 50)
    /** Get a combo viewer UI control */
    def createCControl(toolkit: FormToolkit, parent: Composite, style: Int, updateDelay: Int): ComboViewer
    /** The validator function */
    def validate(validator: Validator, event: VerifyEvent): Unit
  }
  /**
   * Element property trait that provides a viewer widget
   */
  trait Viewer[T <: AnySRef] extends api.PropertyType.Viewer[T] {
    /** The property representing the UI control value */
    val data: WritableValue[T]
    /** An actual value container */
    val element: Element

    /** Get an UI control */
    def createControl(parent: Composite): Control = createControl(parent, SWT.NONE)
    /** Get an UI control */
    def createControl(parent: Composite, style: Int): Control = createControl(parent, style, 50)
    /** Get an UI control */
    def createControl(parent: Composite, style: Int, updateDelay: Int): Control
    /** Get an UI control */
    def createControl(toolkit: FormToolkit, parent: Composite): Control = createControl(toolkit, parent, SWT.NONE)
    /** Get an UI control */
    def createControl(toolkit: FormToolkit, parent: Composite, style: Int): Control = createControl(toolkit, parent, style, 50)
    /** Get an UI control */
    def createControl(toolkit: FormToolkit, parent: Composite, style: Int, updateDelay: Int): Control
    /** Returns true if the data is empty, false otherwise. */
    def isEmpty: Boolean
  }
  /*
   * Support classes
   */
  /** The base interface of CellLabelProvider adapter */
  trait CellLabelProviderAdapter[T] {
    /** Return the background color used for the tool tip */
    def getToolTipBackgroundColor(element: AnyRef): Color = null
    /** The time in milliseconds until the tool tip is displayed. */
    def getToolTipDisplayDelayTime(element: AnyRef): Int = Default.toolTipDisplayDelayTime
    /** Get the {@link Font} used to display the tool tip */
    def getToolTipFont(element: AnyRef): Font = null
    /** The foreground color used to display the the text in the tool tip */
    def getToolTipForegroundColor(element: AnyRef): Color = null
    /** Get the image displayed in the tool tip for object. */
    def getToolTipImage(element: AnyRef): Image = null
    /**
     * Return the amount of pixels in x and y direction you want the tool tip to
     * pop up from the mouse pointer. The default shift is 10px right and 0px
     * below your mouse cursor. Be aware of the fact that you should at least
     * position the tool tip 1px right to your mouse cursor else click events
     * may not get propagated properly.
     */
    def getToolTipShift(element: AnyRef): Point = Default.toolTipShift
    /** Get the text displayed in the tool tip for object. */
    def getToolTipText(element: AnyRef): String = null
    /** The time in milliseconds the tool tip is shown for. */
    def getToolTipTimeDisplayed(element: AnyRef): Int = Default.toolTipTimeDisplayed
    /**
     * The {@link SWT} style used to create the {@link CLabel} (see there for
     * supported styles). By default {@link SWT#SHADOW_NONE} is used.
     */
    def getToolTipStyle(element: AnyRef): Int = SWT.SHADOW_NONE
    /** Update the label for cell. */
    def update(cell: ViewerCell, value: Option[T]): Unit
    /**
     * Return whether or not to use the native tool tip. If you switch to native
     * tool tips only the value from {@link #getToolTipText(Object)} is used all
     * other features from custom tool tips are not supported.
     */
    def useNativeToolTip(element: AnyRef): Boolean = false
  }
  /** The default enumeration label provider */
  class EnumerationLabelProvider extends LabelProvider {
    override def getText(element: AnyRef): String = element match {
      case constant: Enumeration.Constant[_] ⇒
        constant.view
      case unknown ⇒
        log.fatal("Unknown item " + unknown.getClass())
        unknown.toString()
    }
  }
  /** The base interface of LabelProvider adapter */
  trait LabelProviderAdapter[T] {
    /**
     * The <code>LabelProvider</code> implementation of this
     * <code>ILabelProvider</code> method returns <code>null</code>.
     */
    def getImage(value: Option[T]): Image = null
    /**
     * The <code>LabelProvider</code> implementation of this
     * <code>ILabelProvider</code> method returns the element's
     * <code>toString</code> string.
     */
    def getText(value: Option[T]): String = value match {
      case Some(value) if value != null ⇒ value.toString()
      case _ ⇒ ""
    }
  }
  /**
   * Dependency injection routines
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** Predefined element property types that are available for this application */
    lazy val types = injectTypes
    /**
     * Collection of type properties.
     *
     * Each collected type must be:
     *  1. an instance of api.PropertyType
     *  2. has name that starts with "PropertyType."
     */
    private def injectTypes(): immutable.HashMap[Symbol, api.PropertyType[_ <: AnySRef]] = {
      val types = bindingModule.bindings.filter {
        case (key, value) ⇒ classOf[api.PropertyType[_ <: AnySRef]].isAssignableFrom(key.m.runtimeClass)
      }.map {
        case (key, value) ⇒
          key.name match {
            case Some(name) if name.startsWith("PropertyType.") ⇒
              log.debug(s"'${name}' loaded.")
              bindingModule.injectOptional(key).asInstanceOf[Option[api.PropertyType[_ <: AnySRef]]]
            case _ ⇒
              log.debug(s"'${key.name.getOrElse("Unnamed")}' property type skipped.")
              None
          }
      }.flatten.toSeq
      val result = immutable.HashMap[Symbol, api.PropertyType[_ <: AnySRef]](types.map(n ⇒ (n.id, n)): _*)
      assert(result.nonEmpty, "Unable to start application with empty properyTypes map.")

      result.values.foreach(ptype ⇒ try {
        log.debug("Register property handler [type symbol:%s -> id:%s].".format(ptype.typeSymbol, ptype.id.name))
      } catch {
        case e: NoSuchElementException ⇒
          log.error(s"Unable to register $ptype, DSL type not found.", e)
          throw e
      })
      result
    }
  }
}
