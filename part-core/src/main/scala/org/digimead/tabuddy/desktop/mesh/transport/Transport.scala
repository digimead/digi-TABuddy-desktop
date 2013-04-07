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

package org.digimead.tabuddy.desktop.mesh.transport

import java.net.InetSocketAddress
import java.net.SocketAddress
import java.util.concurrent.ExecutorService

import scala.Option.option2Iterable

import org.digimead.configgy.Configgy
import org.digimead.configgy.Configgy.getImplementation
import org.digimead.digi.lib.DependencyInjection
import org.digimead.digi.lib.log.Loggable
import org.digimead.digi.lib.log.logger.RichLogger.rich2slf4j
import org.digimead.digi.lib.rudp.RUDPDataHandler
import org.digimead.digi.lib.rudp.RUDPFrameHandler
import org.digimead.tabuddy.desktop.Main
import org.jboss.netty.bootstrap.ConnectionlessBootstrap
import org.jboss.netty.channel.Channel
import org.jboss.netty.channel.ChannelPipeline
import org.jboss.netty.channel.ChannelPipelineFactory
import org.jboss.netty.channel.Channels
import org.jboss.netty.channel.socket.nio.NioDatagramChannelFactory
import org.jboss.netty.channel.socket.oio.OioDatagramChannelFactory
import org.jboss.netty.handler.execution.ExecutionHandler
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor
import org.jboss.netty.logging.InternalLoggerFactory
import org.jboss.netty.logging.Slf4JLoggerFactory

import com.escalatesoft.subcut.inject.BindingModule
import com.escalatesoft.subcut.inject.Injectable

import akka.actor.Props

import language.implicitConversions

class Transport(implicit val bindingModule: BindingModule) extends Transport.Interface with Injectable {
  val threadPool = inject[ExecutorService]("Transport")
  val factory = inject[Boolean]("Transport.NIO") match {
    case true => new NioDatagramChannelFactory(threadPool)
    case false => new OioDatagramChannelFactory(threadPool)
  }
  val bootstrap = new ConnectionlessBootstrap(factory)
  val address = inject[SocketAddress]("Transport")
  lazy val actor = Main.system.actorOf(Props[Transport.Actor], name = "Transport.Actor")
  @volatile protected var channel: Option[Channel] = None
  protected lazy val executionHandler = new OrderedMemoryAwareThreadPoolExecutor(16, 1048576, 1048576)

  def start(): Unit = synchronized {
    log.info("start RUDP transport for " + address)
    assert(channel.isEmpty, "unable to reuse Transport component")
    active = true
    bootstrap.setOption("reuseAddress", true)
    bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
      def getPipeline(): ChannelPipeline = {
        val pipeline = Channels.pipeline()
        pipeline.addFirst("execution-handler", new ExecutionHandler(executionHandler))
        pipeline.addLast("RUDP frame handler", new RUDPFrameHandler)
        pipeline.addLast("RUDP data handler", new RUDPDataHandler)
        //pipeline.addLast("handler", new ServerChannelHandler)
        pipeline
      }
    })
    channel = Some(bootstrap.bind(address))
  }
  def stop(): Unit = synchronized {
    log.info("stop RUDP transport")
    if (!active)
      return
    channel.foreach { ch =>
      channel = None
      ch.close().awaitUninterruptibly()
    }
    executionHandler.shutdown()
    factory.releaseExternalResources()
    bootstrap.releaseExternalResources()
  }
}

object Transport extends DependencyInjection.PersistentInjectable with Loggable {
  InternalLoggerFactory.setDefaultFactory(new Slf4JLoggerFactory())
  implicit def transport2implementation(m: Transport.type): Interface = m.inner
  implicit def bindingModule = DependencyInjection()
  @volatile private var active: Boolean = false
  lazy val coreList = Configgy.getConfigMap("core").map(table => {
    table.keys.flatMap { key =>
      try {
        table(key).toString().split(""":""") match {
          case Array(address, port) =>
            Some(Core(key, new InetSocketAddress(address, port.toInt)))
          case address =>
            log.warn("unable to register core: %s invalid address %s".format(key, address))
            None
        }
      } catch {
        case e: Throwable =>
          log.warn("unable to register core: " + e.getMessage(), e)
          None
      }
    }
  }).getOrElse(Seq()).toSeq
  @volatile private var activeCore: Option[Core] = None

  def setCore(core: Option[Core]) {
    log.debug("set active core " + core)
    activeCore = core
  }
  def getCore() = activeCore

  /*
   * dependency injection
   */
  def inner() = inject[Interface]
  override def afterInjection(newModule: BindingModule) {
    if (Transport.active)
      inner.start()
  }
  override def beforeInjection(newModule: BindingModule) {
    DependencyInjection.assertLazy[Interface](None, newModule)
  }
  override def onClearInjection(oldModule: BindingModule) {
    Transport.active = inner.active
    if (inner.active)
      inner.stop()
  }

  trait Interface extends Main.Interface
  class Actor extends akka.actor.Actor {
    def receive = {
      case "test" => log.info("received test")
      case _ => log.info("received unknown message")
    }
  }
  case class Core(val name: String, address: SocketAddress) {
    log.info("register core %s at %s".format(name, address))
  }
  sealed trait Message
  object Message {
    case object DiscoverBuddyCore extends Message
  }
}
