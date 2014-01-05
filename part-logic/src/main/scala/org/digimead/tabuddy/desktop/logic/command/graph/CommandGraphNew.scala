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
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.definition.Operation
import org.digimead.tabuddy.desktop.core.definition.command.Command
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.logic.operation.OperationGraphNew
import org.digimead.tabuddy.desktop.logic.payload.maker.GraphMarker
import org.digimead.tabuddy.desktop.logic.{ Logic, Messages }
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.graph.Graph
import org.eclipse.core.runtime.jobs.Job
import scala.concurrent.Future

/**
 * Create new graph.
 * Example: graph new -all graphName /a/b/c/.../graphPath
 */
object CommandGraphNew extends Loggable {
  import Command.parser._
  private val allArg = "-all"
  /** Akka execution context. */
  implicit lazy val ec = App.system.dispatcher
  /** Command description. */
  implicit lazy val descriptor = Command.Descriptor(UUID.randomUUID())(Messages.graph_new_text,
    Messages.graph_newDescriptionShort_text, Messages.graph_newDescriptionLong_text,
    (activeContext, parserContext, parserResult) ⇒ Future {
      parserResult match {
        case ~(~(graphName: String, _), graphContainer: String) ⇒
          val exchanger = new Exchanger[Operation.Result[Graph[_ <: Model.Like]]]()
          OperationGraphNew(Some(graphName), Some(new File(graphContainer)), false).foreach { operation ⇒
            operation.getExecuteJob() match {
              case Some(job) ⇒
                job.setPriority(Job.LONG)
                job.onComplete(exchanger.exchange).schedule()
              case None ⇒
                log.fatal(s"Unable to create job for ${operation}.")
            }
          }
          exchanger.exchange(null) match {
            case Operation.Result.OK(result, message) ⇒
              log.info(s"Operation completed successfully.")
              result.map(graph ⇒ GraphMarker.bind(GraphMarker(graph)))
              result
            case Operation.Result.Cancel(message) ⇒
              throw new CancellationException(s"Operation canceled, reason: ${message}.")
            case other ⇒
              throw new RuntimeException(s"Unable to complete operation: ${other}.")
          }
      }
    })
  /** Command parser. */
  lazy val parser = Command.CmdParser(descriptor.name ~ opt(sp ~> allArg) ~> sp ~> nameParser ~ sp ~ locationParser)

  /** Create parser for the graph name. */
  protected def nameParser: Command.parser.Parser[Any] =
    commandRegex(App.symbolPattern.pattern().r, Command.Hint.Container(Command.Hint("graph name", Some(s"string that is correct Scala symbol literal"))))

  /** Create parser for the graph location. */
  protected def locationParser: Command.parser.Parser[Any] =
    commandRegex(".*".r, HintContainer)

  object HintContainer extends Command.Hint.Container {
    /** Current FS root, based on application location. */
    lazy val root = getRoot(new File(Logic.container.getLocationURI()))

    /** Get parser hints for user provided path. */
    def apply(arg: String): Seq[Command.Hint] = {
      if (arg.trim.isEmpty)
        return Seq(Command.Hint("graph location", Some(s"path to graph directory"), Seq(root.getName() + File.separator)))
      val hint = new File(arg.trim()) match {
        case path if path.isDirectory() && arg.endsWith(File.separator) ⇒
          val dirs = path.listFiles().sortBy(_.getName).filter(_.isDirectory())
          Command.Hint("graph location", Some(s"path to graph directory"), dirs.map(_.getName()))
        case path ⇒
          val prefix = path.getName()
          val beginIndex = prefix.length()
          val parent = path.getParentFile()
          val dirs = if (parent.isDirectory())
            path.getParentFile().listFiles().filter(f ⇒ f.getName().startsWith(prefix) && f.isDirectory())
          else
            Array[File]()
          Command.Hint("graph location", Some(s"path to graph directory"), dirs.map(_.getName().substring(beginIndex) + File.separator))
      }
      Seq(hint)
    }
    def getRoot(file: File): File = Option(file.getParentFile()) match {
      case Some(parent) ⇒ getRoot(parent)
      case None ⇒ file
    }
  }
}

