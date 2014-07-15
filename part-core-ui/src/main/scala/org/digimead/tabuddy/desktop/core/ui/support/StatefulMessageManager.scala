/**
 * This file is part of the TA Buddy project.
 * Copyright (c) 2014 Alexey Aksenov ezh@ezh.msk.ru
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

package org.digimead.tabuddy.desktop.core.ui.support

import org.eclipse.swt.widgets.Control
import org.eclipse.ui.forms.{ IMessage, IMessageManager, IMessagePrefixProvider }
import scala.collection.mutable
import scala.ref.WeakReference

/**
 * Stateful wrapper for IMessageManager
 */
class StatefulMessageManager(mmng: WeakReference[IMessageManager]) extends IMessageManager {
  /** State (control system identity hash code, key) => message type */
  protected val state = mutable.HashMap[(Int, AnyRef), Int]()

  /** Get state. */
  def getState() = state.toMap

  /**
   * Adds a general message that is not associated with any decorated field.
   * Note that subsequent calls using the same key will not result in
   * duplicate messages. Instead, the previous message with the same key will
   * be replaced with the new message.
   *
   * @param key
   *            a unique message key that will be used to look the message up
   *            later
   *
   * @param messageText
   *            the message to add
   * @param data
   *            an object for application use (can be <code>null</code>)
   * @param type
   *            the message type as defined in <code>IMessageProvider</code>.
   */
  def addMessage(key: AnyRef, messageText: String, data: AnyRef, `type`: Int) =
    mmng.get.foreach { mmng ⇒
      state((0, key)) = `type`
      mmng.addMessage(key, messageText, data, `type`)
    }
  /**
   * Adds a message that should be associated with the provided control. Note
   * that subsequent calls using the same key will not result in duplicate
   * messages. Instead, the previous message with the same key will be
   * replaced with the new message.
   *
   * @param key
   *            the unique message key
   * @param messageText
   *            the message to add
   * @param data
   *            an object for application use (can be <code>null</code>)
   * @param type
   *            the message type
   * @param control
   *            the control to associate the message with
   */
  def addMessage(key: AnyRef, messageText: String, data: AnyRef, `type`: Int, control: Control) =
    mmng.get.foreach { mmng ⇒
      state((System.identityHashCode(control), key)) = `type`
      mmng.addMessage(key, messageText, data, `type`, control)
    }
  /**
   * Removes the general message with the provided key. Does nothing if
   * message for the key does not exist.
   *
   * @param key
   *            the key of the message to remove
   */
  def removeMessage(key: AnyRef) =
    mmng.get.foreach { mmng ⇒
      state.remove((0, key))
      mmng.removeMessage(key)
    }
  /**
   * Removes all the general messages. If there are local messages associated
   * with controls, the replacement message may show up drawing user's
   * attention to these local messages. Otherwise, the container will clear
   * the message area.
   */
  def removeMessages() =
    mmng.get.foreach { mmng ⇒
      val toRemove = state.flatMap {
        case (key @ (0, _), value) ⇒ Some(key)
        case _ ⇒ None
      }
      toRemove.foreach(state.remove)
      mmng.removeMessages()
    }
  /**
   * Removes a keyed message associated with the provided control. Does
   * nothing if the message for that key does not exist.
   *
   * @param key
   *            the id of the message to remove
   * @param control
   *            the control the message is associated with
   */
  def removeMessage(key: AnyRef, control: Control) =
    mmng.get.foreach { mmng ⇒
      state.remove((System.identityHashCode(control), key))
      mmng.removeMessage(key, control)
    }
  /**
   * Removes all the messages associated with the provided control. Does
   * nothing if there are no messages for this control.
   *
   * @param control
   *            the control the messages are associated with
   */
  def removeMessages(control: Control) =
    mmng.get.foreach { mmng ⇒
      val controlId = System.identityHashCode(control)
      val toRemove = state.flatMap {
        case (key @ (id, _), value) if id == controlId ⇒ Some(key)
        case _ ⇒ None
      }
      toRemove.foreach(state.remove)
      mmng.removeMessages(control)
    }
  /**
   * Removes all the local field messages and all the general container
   * messages.
   */
  def removeAllMessages() =
    mmng.get.foreach { mmng ⇒
      state.clear()
      mmng.removeAllMessages()
    }
  /**
   * Updates the message container with the messages currently in the manager.
   * There are two scenarios in which a client may want to use this method:
   * <ol>
   * <li>When controls previously managed by this manager have been disposed.</li>
   * <li>When automatic update has been turned off.</li>
   * </ol>
   * In all other situations, the manager will keep the form in sync
   * automatically.
   *
   * @see #setAutoUpdate(boolean)
   */
  def update() = mmng.get.foreach { _.update() }
  /**
   * Controls whether the form is automatically updated when messages are
   * added or removed. By default, auto update is on. Clients can turn it off
   * prior to adding or removing a number of messages as a batch. Turning it
   * back on will trigger an update.
   *
   * @param enabled
   *            sets the state of the automatic update
   */
  def setAutoUpdate(enabled: Boolean) = mmng.get.foreach { _.setAutoUpdate(enabled) }
  /**
   * Tests whether the form will be automatically updated when messages are
   * added or removed.
   *
   * @return <code>true</code> if auto update is active, <code>false</code>
   *         otherwise.
   */
  def isAutoUpdate(): Boolean = mmng.get.map { _.isAutoUpdate() } getOrElse false
  /**
   * Sets the alternative message prefix provider. The default prefix provider
   * is set by the manager.
   *
   * @param provider
   *            the new prefix provider or <code>null</code> to turn the
   *            prefix generation off.
   */
  def setMessagePrefixProvider(provider: IMessagePrefixProvider) = mmng.get.foreach { _.setMessagePrefixProvider(provider) }
  /**
   * @return the current prefix provider or <code>null</code> if prefixes
   *         are not generated.
   */
  def getMessagePrefixProvider(): IMessagePrefixProvider = mmng.get.map { _.getMessagePrefixProvider() } getOrElse null
  /**
   * Message manager uses SWT.LEFT|SWT.BOTTOM for the default decoration
   * position. Use this method to change it.
   *
   * @param position
   *            the decoration position
   * @see ControlDecoration
   */
  def setDecorationPosition(position: Int) = mmng.get.foreach { _.setDecorationPosition(position) }
  /**
   * Returns the currently used decoration position for all control messages.
   *
   * @return the current decoration position
   */

  def getDecorationPosition(): Int = mmng.get.map { _.getDecorationPosition() } getOrElse 0
  /**
   * When message manager is used in context of a form, and there are
   * hyperlink listeners for messages in the header, the hyperlink event will
   * carry an object of type <code>IMessage[]</code> as an href. You can use
   * this method to create a summary text from this array consistent with the
   * tool tip used by the form header.
   *
   * @param messages
   *            an array of messages
   * @return a textual representation of the messages with one message per
   *         line.
   * @see Form#addMessageHyperlinkListener(org.eclipse.ui.forms.events.IHyperlinkListener)
   */
  def createSummary(messages: Array[IMessage]): String = mmng.get.map { _.createSummary(messages) } getOrElse ""
}

object StatefulMessageManager {
  def apply(mmng: IMessageManager) = new StatefulMessageManager(WeakReference(mmng))
}
