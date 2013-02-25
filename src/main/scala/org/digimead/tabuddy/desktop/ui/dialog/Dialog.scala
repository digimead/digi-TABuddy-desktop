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

package org.digimead.tabuddy.desktop.ui.dialog

import java.io.Reader
import java.io.Writer
import java.util.concurrent.atomic.AtomicBoolean

import org.digimead.configgy.Configgy
import org.digimead.configgy.Configgy.getImplementation
import org.digimead.digi.lib.log.Loggable
import org.digimead.tabuddy.desktop.Main
import org.eclipse.jface.dialogs.IDialogSettings
import org.eclipse.jface.viewers.TableViewerColumn
import org.eclipse.swt.SWT
import org.eclipse.swt.events.DisposeEvent
import org.eclipse.swt.events.DisposeListener
import org.eclipse.swt.graphics.Point
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Control
import org.eclipse.swt.widgets.Event
import org.eclipse.swt.widgets.Listener
import org.eclipse.swt.widgets.Shell

trait Dialog extends org.eclipse.jface.dialogs.Dialog with Loggable {
  protected val parentShell: Shell
  protected val onActiveFlag = new AtomicBoolean(true)
  protected val onActiveListener = new Dialog.DialogPaintListener(this)

  /** Adjust column width */
  def adjustColumnWidth(viewerColumn: TableViewerColumn, padding: Int) {
    val bounds = viewerColumn.getViewer.getControl.getBounds()
    val column = viewerColumn.getColumn()
    column.pack()
    column.setWidth(math.min(column.getWidth() + padding, bounds.width / 3))
  }
  /** Create contents of the dialog. */
  override protected def createDialogArea(parent: Composite): Control = {
    val result = super.createDialogArea(parent)
    // The onPaintListener solution is not sufficient
    Main.display.addFilter(SWT.Paint, onActiveListener)
    parentShell.addDisposeListener(new DisposeListener {
      def widgetDisposed(e: DisposeEvent) = Main.display.removeFilter(SWT.Paint, onActiveListener)
    })
    result
  }
  /**
   * Gets the dialog settings that should be used for remembering the bounds of
   * of the dialog, according to the dialog bounds strategy.
   *
   * @return settings the dialog settings used to store the dialog's location
   *         and/or size, or <code>null</code> if the dialog's bounds should
   *         never be stored.
   *
   */
  override protected def getDialogBoundsSettings(): IDialogSettings =
    new Dialog.Settings("settings", this.getClass.getName)
  /** Return the initial size of the dialog. */
  override protected def getInitialSize(): Point =
    if (getDialogBoundsSettings().getSection(null) != null)
      // there is already saved settings
      super.getInitialSize()
    else {
      val default = super.getInitialSize
      val parent = parentShell.getBounds()
      val initialWidth = (parent.width * 0.9).toInt
      val initialHeight = (parent.height * 0.8).toInt
      new Point(math.min(math.max(initialWidth, default.x), parent.width), math.min(math.max(initialHeight, default.y), parent.height))
    }
  /** Return the initial location to use for the shell. */
  override protected def getInitialLocation(initialSize: Point): Point =
    if (getDialogBoundsSettings().getSection(null) != null)
      // there is already saved settings
      super.getInitialLocation(initialSize)
    else {
      val parent = parentShell.getBounds()
      val paddingX = parent.x + ((parent.width - initialSize.x) / 2).toInt
      val paddingY = parent.y + ((parent.height - initialSize.y) / 2).toInt
      new Point(paddingX, paddingY)
    }
  protected def onActive() {}
}

object Dialog {
  /** Ascending sort constant */
  val ASCENDING = 0
  /** Descending sort constant */
  val DESCENDING = 1
  /** Auto resize column padding */
  val columnPadding = 10
  /** Dialog persistence prefix */
  val configPrefix = "persistence.dialogs"

  class DialogPaintListener(dialog: Dialog) extends Listener() {
    def handleEvent(event: Event) = event.widget match {
      case control: Control if control.getShell.eq(dialog.getShell) =>
        if (dialog.onActiveFlag.compareAndSet(true, false)) {
          Main.display.removeFilter(SWT.Paint, this)
          dialog.onActive()
        }
      case other =>
    }
  }
  class Settings(sectionName: String, prefix: String) extends IDialogSettings {
    /**
     * Create a new section in the receiver and return it.
     *
     * @param name
     *            the name of the new section
     * @return the new section
     *
     * @see DialogSettings#getOrCreateSection(IDialogSettings, String)
     */
    def addNewSection(name: String): IDialogSettings = new Settings(name, prefix + "." + sectionName)
    /**
     * Add a section in the receiver.
     *
     * @param section
     *            the section to be added
     */
    def addSection(section: IDialogSettings) =
      throw new UnsupportedOperationException
    /**
     * Returns the value of the given key in this dialog settings.
     *
     * @param key
     *            the key
     * @return the value, or <code>null</code> if none
     */
    def get(key: String): String = Configgy.getString(this.key(key)).getOrElse("")
    /**
     * Returns the value, an array of strings, of the given key in this dialog
     * settings.
     *
     * @param key
     *            the key
     * @return the array of string, or <code>null</code> if none
     */
    def getArray(key: String): Array[String] = Configgy.getList(this.key(key)).toArray
    /**
     * Convenience API. Convert the value of the given key in this dialog
     * settings to a boolean and return it.
     *
     * @param key
     *            the key
     * @return the boolean value, or <code>false</code> if none
     */
    def getBoolean(key: String): Boolean = Configgy.getBool(this.key(key)).getOrElse(false)
    /**
     * Convenience API. Convert the value of the given key in this dialog
     * settings to a double and return it.
     *
     * @param key
     *            the key
     * @return the value coverted to double, or throws
     *         <code>NumberFormatException</code> if none
     *
     * @exception NumberFormatException
     *                if the string value does not contain a parsable number.
     * @see java.lang.Double#valueOf(java.lang.String)
     */
    def getDouble(key: String): Double = Configgy.getDouble(this.key(key)).getOrElse(0.0)
    /**
     * Convenience API. Convert the value of the given key in this dialog
     * settings to a float and return it.
     *
     * @param key
     *            the key
     * @return the value coverted to float, or throws
     *         <code>NumberFormatException</code> if none
     *
     * @exception NumberFormatException
     *                if the string value does not contain a parsable number.
     * @see java.lang.Float#valueOf(java.lang.String)
     */
    def getFloat(key: String): Float = Configgy.getDouble(this.key(key)).map(_.toFloat).getOrElse(0.toFloat)
    /**
     * Convenience API. Convert the value of the given key in this dialog
     * settings to a int and return it.
     *
     * @param key
     *            the key
     * @return the value coverted to int, or throws
     *         <code>NumberFormatException</code> if none
     *
     * @exception NumberFormatException
     *                if the string value does not contain a parsable number.
     * @see java.lang.Integer#valueOf(java.lang.String)
     */
    def getInt(key: String): Int = Configgy.getInt(this.key(key)).getOrElse(0)
    /**
     * Convenience API. Convert the value of the given key in this dialog
     * settings to a long and return it.
     *
     * @param key
     *            the key
     * @return the value coverted to long, or throws
     *         <code>NumberFormatException</code> if none
     *
     * @exception NumberFormatException
     *                if the string value does not contain a parsable number.
     * @see java.lang.Long#valueOf(java.lang.String)
     */
    def getLong(key: String): Long = Configgy.getLong(this.key(key)).getOrElse(0L)
    /**
     * Returns the IDialogSettings name.
     *
     * @return the name
     */
    def getName(): String = sectionName
    /**
     * Returns the section with the given name in this dialog settings.
     *
     * @param sectionName
     *            the key
     * @return IDialogSettings (the section), or <code>null</code> if none
     *
     * @see DialogSettings#getOrCreateSection(IDialogSettings, String)
     */
    def getSection(name: String): IDialogSettings =
      if (name == null || name.trim.isEmpty())
        // like exists()
        if (Configgy.getConfigMap(key).nonEmpty) this else null
      else {
        val sectionKey = key + "." + name
        Configgy.getConfigMap(sectionKey).map(_ => new Settings(name, key)).getOrElse(null)
      }
    /**
     * Returns all the sections in this dialog settings.
     *
     * @return the section, or <code>null</code> if none
     */
    def getSections(): Array[IDialogSettings] =
      Configgy.getConfigMap(key) match {
        case Some(map) =>
          map.keys.filter(key => map.getConfigMap(key).nonEmpty).map(new Settings(_, key)).toArray
        case None =>
          Array()
      }
    /**
     * Load a dialog settings from a stream and fill the receiver with its
     * content.
     *
     * @param reader
     *            a Reader specifying the stream where the settings are read
     *            from.
     * @throws IOException
     */
    def load(reader: Reader) = throw new UnsupportedOperationException
    /**
     * Load a dialog settings from a file and fill the receiver with its
     * content.
     *
     * @param fileName
     *            the name of the file the settings are read from.
     * @throws IOException
     */
    def load(fileName: String) = throw new UnsupportedOperationException
    /**
     * Adds the pair <code>key/value</code> to this dialog settings.
     *
     * @param key
     *            the key.
     * @param value
     *            the value to be associated with the <code>key</code>
     */
    def put(key: String, value: Array[String]): Unit = Configgy.setList(this.key(key), value)
    /**
     * Convenience API. Converts the double <code>value</code> to a string and
     * adds the pair <code>key/value</code> to this dialog settings.
     *
     * @param key
     *            the key.
     * @param value
     *            the value to be associated with the <code>key</code>
     */
    def put(key: String, value: Double): Unit = Configgy.setDouble(this.key(key), value)
    /**
     * Convenience API. Converts the float <code>value</code> to a string and
     * adds the pair <code>key/value</code> to this dialog settings.
     *
     * @param key
     *            the key.
     * @param value
     *            the value to be associated with the <code>key</code>
     */
    def put(key: String, value: Float): Unit = Configgy.setDouble(this.key(key), value.toDouble)
    /**
     * Convenience API. Converts the int <code>value</code> to a string and
     * adds the pair <code>key/value</code> to this dialog settings.
     *
     * @param key
     *            the key.
     * @param value
     *            the value to be associated with the <code>key</code>
     */
    def put(key: String, value: Int): Unit = Configgy.setInt(this.key(key), value)
    /**
     * Convenience API. Converts the long <code>value</code> to a string and
     * adds the pair <code>key/value</code> to this dialog settings.
     *
     * @param key
     *            the key.
     * @param value
     *            the value to be associated with the <code>key</code>
     */
    def put(key: String, value: Long): Unit = Configgy.setLong(this.key(key), value)
    /**
     * Adds the pair <code>key/value</code> to this dialog settings.
     *
     * @param key
     *            the key.
     * @param value
     *            the value to be associated with the <code>key</code>
     */
    def put(key: String, value: String): Unit = Configgy.setString(this.key(key), value)
    /**
     * Convenience API. Converts the boolean <code>value</code> to a string
     * and adds the pair <code>key/value</code> to this dialog settings.
     *
     * @param key
     *            the key.
     * @param value
     *            the value to be associated with the <code>key</code>
     */
    def put(key: String, value: Boolean): Unit = Configgy.setBool(this.key(key), value)
    /**
     * Save a dialog settings to a stream
     *
     * @param writer
     *            a Writer specifying the stream the settings are written in.
     * @throws IOException
     */
    def save(writer: Writer) = throw new UnsupportedOperationException
    /**
     * Save a dialog settings to a file.
     *
     * @param fileName
     *            the name of the file the settings are written in.
     * @throws IOException
     */
    def save(fileName: String) = throw new UnsupportedOperationException

    /** Get the current configuration key */
    protected def key = configPrefix + "." + prefix + "." + sectionName
    /** Get the current key */
    protected def key(name: String): String = key + "." + name
  }
}
