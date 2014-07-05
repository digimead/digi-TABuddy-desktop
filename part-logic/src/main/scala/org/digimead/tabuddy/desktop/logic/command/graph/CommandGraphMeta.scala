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

package org.digimead.tabuddy.desktop.logic.command.graph

import java.util.{ Date, UUID }
import org.digimead.tabuddy.desktop.core.definition.command.Command
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.logic.Messages
import org.digimead.tabuddy.desktop.logic.payload.marker.GraphMarker
import scala.concurrent.Future

/**
 * Show graph metadata.
 */
object CommandGraphMeta {
  import Command.parser._
  /** Akka execution context. */
  implicit lazy val ec = App.system.dispatcher
  /** Command description. */
  implicit lazy val descriptor = Command.Descriptor(UUID.randomUUID())(Messages.graph_meta_text,
    Messages.graph_metaDescriptionShort_text, Messages.graph_metaDescriptionLong_text,
    (activeContext, parserContext, parserResult) ⇒ Future {
      parserResult match {
        case (Some(marker: GraphMarker), _, _) ⇒
          s"""identifier: ${marker.graphModelId}
             |origin: ${marker.graphOrigin}
             |loaded: ${if (marker.graphIsOpen()) "YES" else "NO"}
             |modified: ${if (marker.graphIsDirty()) "YES" else "NO"}
             |creation timestamp: ${new Date(marker.graphCreated.milliseconds)} [${marker.graphCreated}]
             |last save timestamp: ${new Date(marker.markerLastAccessed)}
             |default serialization type: ${marker.defaultSerialization.extension.toUpperCase()}
             |digest on write: ${marker.digest.freeze}
             |digest on read: ${marker.digest.acquire}
             |signature on write: ${marker.signature.freeze}
             |signature on read: ${marker.signature.acquire}
             |containerEncryption: ${marker.containerEncryption}
             |contentEncryption: ${marker.contentEncryption}
             |primary storage: ${marker.graphPath}
             |additional storages: ${marker.graphAdditionalStorages}
             |marker uuid: ${marker.uuid}""".stripMargin
      }
    })
  /** Command parser. */
  lazy val parser = Command.CmdParser(descriptor.name ~ sp ~> graphParser)

  /** Graph argument parser. */
  def graphParser = GraphParser(() ⇒ GraphMarker.list().map(GraphMarker(_)).
    filter(m ⇒ m.markerIsValid).sortBy(_.graphModelId.name).sortBy(_.graphOrigin.name))
}
