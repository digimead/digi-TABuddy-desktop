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

import java.io.File
import java.util.UUID
import java.util.concurrent.{ CancellationException, Exchanger }
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.command.PathParser
import org.digimead.tabuddy.desktop.core.definition.Operation
import org.digimead.tabuddy.desktop.core.definition.command.Command
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.logic.command.digest.DigestParser
import org.digimead.tabuddy.desktop.logic.command.encryption.EncryptionParser
import org.digimead.tabuddy.desktop.logic.command.signature.SignatureParser
import org.digimead.tabuddy.desktop.logic.operation.graph.OperationGraphNew
import org.digimead.tabuddy.desktop.logic.payload.Payload
import org.digimead.tabuddy.desktop.logic.payload.marker.GraphMarker
import org.digimead.tabuddy.desktop.logic.payload.marker.api.XGraphMarker
import org.digimead.tabuddy.desktop.logic.{ Logic, Messages }
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.graph.Graph
import org.eclipse.core.runtime.jobs.Job
import scala.concurrent.Future

/**
 * Create new graph.
 * Example: graph new -all graphName /a/b/c/.../graphPath
 */
object CommandGraphNew extends XLoggable {
  import Command.parser._
  /** Akka execution context. */
  implicit lazy val ec = App.system.dispatcher
  /** Command description. */
  implicit lazy val descriptor = Command.Descriptor(UUID.randomUUID())(Messages.graph_new_text,
    Messages.graph_newDescriptionShort_text, Messages.graph_newDescriptionLong_text,
    (activeContext, parserContext, parserResult) ⇒ Future {
      parserResult match {
        case ((options @ List(_*), ~(graphName: String, graphContainer: File))) ⇒
          val exchanger = new Exchanger[Operation.Result[Graph[_ <: Model.Like]]]()
          OperationGraphNew(graphName, graphContainer, Payload.defaultSerialization).foreach { operation ⇒
            operation.getExecuteJob() match {
              case Some(job) ⇒
                job.setPriority(Job.LONG)
                job.onComplete(exchanger.exchange).schedule()
              case None ⇒
                throw new RuntimeException(s"Unable to create job for ${operation}.")
            }
          }
          exchanger.exchange(null) match {
            case Operation.Result.OK(Some(graph), message) ⇒
              val marker = GraphMarker(graph)
              options.foreach(_ match {
                case DigestParser.Argument("digest", Some(parameters)) ⇒
                  marker.digest = XGraphMarker.Digest(Some(true), Some(Map(marker.graphPath.toURI() -> parameters)))
                case DigestParser.Argument("digest", None) ⇒
                  marker.digest = XGraphMarker.Digest(None, None)
                case EncryptionParser.Argument("container", Some(parameters)) ⇒
                  marker.containerEncryption = XGraphMarker.Encryption(Map(marker.graphPath.toURI() -> parameters))
                case EncryptionParser.Argument("container", None) ⇒
                  marker.containerEncryption = XGraphMarker.Encryption(Map())
                case EncryptionParser.Argument("content", Some(parameters)) ⇒
                  marker.contentEncryption = XGraphMarker.Encryption(Map(marker.graphPath.toURI() -> parameters))
                case EncryptionParser.Argument("content", None) ⇒
                  marker.contentEncryption = XGraphMarker.Encryption(Map())
                case SignatureParser.Argument("signature", Some(parameters)) ⇒
                  marker.signature = XGraphMarker.Signature(Some(UUID.randomUUID()), Some(Map(marker.graphPath.toURI() -> parameters)))
                case SignatureParser.Argument("signature", None) ⇒
                  marker.signature = XGraphMarker.Signature(None, None)
                case unknown ⇒
                  throw new IllegalArgumentException("Unknown parameter: " + unknown)
              })
              GraphMarker.bind(marker)
              log.info(s"Operation completed successfully.")
              marker
            case Operation.Result.OK(None, message) ⇒
              throw new CancellationException(s"Operation canceled, reason: ${message}.")
            case Operation.Result.Cancel(message) ⇒
              throw new CancellationException(s"Operation canceled, reason: ${message}.")
            case err: Operation.Result.Error[_] ⇒
              throw err
            case other ⇒
              throw new RuntimeException(s"Unable to complete operation: ${other}.")
          }
      }
    })
  /** Command parser. */
  lazy val parser = Command.CmdParser(descriptor.name ~> sp ~> optionParser(Seq.empty) { nameParser ~ pathParser })

  /** Option parser. */
  def optionParser(alreadyDefinedOptions: Seq[Any])(tail: Command.parser.Parser[Any]): Command.parser.Parser[Any] = {
    var options = Seq[Command.parser.Parser[Any]](tail ^^ { (alreadyDefinedOptions, _) })

    if (!alreadyDefinedOptions.exists(_.isInstanceOf[DigestParser.Argument]))
      options = (DigestParser.digestParser into { result ⇒
        sp ~> (optionParser(alreadyDefinedOptions :+ result) { tail })
      }) +: options
    if (!alreadyDefinedOptions.exists(_.isInstanceOf[SignatureParser.Argument]))
      options = (SignatureParser.signatureParser into { result ⇒
        sp ~> (optionParser(alreadyDefinedOptions :+ result) { tail })
      }) +: options
    if (!alreadyDefinedOptions.exists(_ match {
      case EncryptionParser.Argument("container", _) ⇒ true
      case _ ⇒ false
    }))
      options = (EncryptionParser.containerEncryptionParser into { result ⇒
        sp ~> (optionParser(alreadyDefinedOptions :+ result) { tail })
      }) +: options
    if (!alreadyDefinedOptions.exists(_ match {
      case EncryptionParser.Argument("content", _) ⇒ true
      case _ ⇒ false
    }))
      options = (EncryptionParser.contentEncryptionParser into { result ⇒
        sp ~> (optionParser(alreadyDefinedOptions :+ result) { tail })
      }) +: options

    options.reduce(_ | _)
  }

  /** Create parser for the graph name. */
  protected def nameParser: Command.parser.Parser[Any] =
    commandRegex(App.symbolPattern.pattern().r, Command.Hint.Container(Command.Hint("graph name", Some(s"Value that is correct Scala symbol literal"))))
  /** Path argument parser. */
  protected def pathParser = PathParser(() ⇒ Logic.graphContainer, () ⇒ "graph location",
    () ⇒ Some(s"Path to graph directory")) { _.isDirectory }
}
