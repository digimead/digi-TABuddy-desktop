package org.digimead.tabuddy.desktop.logic.operation.graph

import java.io.File
import java.net.URI
import java.util.UUID
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.core.definition.Operation
import org.digimead.tabuddy.desktop.core.support.App
import org.digimead.tabuddy.desktop.logic.Logic
import org.digimead.tabuddy.desktop.logic.payload.marker.GraphMarker
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.model.element.Element
import org.digimead.tabuddy.model.graph.Graph
import org.digimead.tabuddy.model.serialization.Serialization
import org.eclipse.core.runtime.{ IAdaptable, IProgressMonitor }

/**
 * 'Get information about graph' operation.
 */
class OperationGraphInfo extends api.OperationGraphInfo with Loggable {
  /**
   * Get information about graph.
   *
   * @param origin graph origin
   * @param location source with imported graph
   * @param publicKey key for graph if needed
   * @return information about graph
   */
  def apply(origin: Symbol, location: URI, pgpPublicKey: Option[AnyRef]): api.OperationGraphInfo.Info = {
    log.info(pgpPublicKey match {
      case Some(key) ⇒ s"Get information about private graph with ${origin} from ${location}."
      case None ⇒ s"Get information about public graph with ${origin} from ${location}."
    })
    if (!Logic.container.isOpen())
      throw new IllegalStateException("Workspace is not available.")
    // получить дескриптор
    // выцепить overview
    // вернуть инфо
    Serialization.perScheme.get(location.getScheme()) match {
      case Some(transport) ⇒
//        pgpPublicKey match {
//          case Some(key) ⇒ transport.setPGPKey(key)
//          case None ⇒ transport.resetPGPKey()
//        }
      case None ⇒
        throw new IllegalArgumentException(s"Unable to load graph from URI with unknown scheme ${location.getScheme}.")
    }
    val loader = Serialization.acquireLoader(location)
    //loader.info()
    null
  }
  /**
   * Create 'Graph info' operation.
   *
   * @param origin graph origin
   * @param location source with imported graph
   * @param publicKey key for graph if needed
   * @return 'Graph info' operation
   */
  def operation(origin: Symbol, location: URI, pgpPublicKey: Option[AnyRef]) =
    new Implemetation(origin, location, pgpPublicKey)

  /**
   * Checks that this class can be subclassed.
   * <p>
   * The API class is intended to be subclassed only at specific,
   * controlled point. This method enforces this rule
   * unless it is overridden.
   * </p><p>
   * <em>IMPORTANT:</em> By providing an implementation of this
   * method that allows a subclass of a class which does not
   * normally allow subclassing to be created, the implementer
   * agrees to be fully responsible for the fact that any such
   * subclass will likely fail.
   * </p>
   */
  override protected def checkSubclass() {}

  class Implemetation(origin: Symbol, location: URI, pgpPublicKey: Option[AnyRef])
    extends OperationGraphInfo.Abstract(origin, location, pgpPublicKey) with Loggable {
    @volatile protected var allowExecute = true

    override def canExecute() = allowExecute
    override def canRedo() = false
    override def canUndo() = false

    protected def execute(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[api.OperationGraphInfo.Info] = {
      require(canExecute, "Execution is disabled.")
      try {
        val result = Option[api.OperationGraphInfo.Info](OperationGraphInfo.this(origin, location, null))
        allowExecute = false
        Operation.Result.OK(result)
      } catch {
        case e: Throwable ⇒
          Operation.Result.Error(s"Unable to import graph.", e)
      }
    }
    protected def redo(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[api.OperationGraphInfo.Info] =
      throw new UnsupportedOperationException
    protected def undo(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[api.OperationGraphInfo.Info] =
      throw new UnsupportedOperationException
  }
}

object OperationGraphInfo extends Loggable {
  /** Stable identifier with OperationGraphInfo DI */
  lazy val operation = DI.operation.asInstanceOf[OperationGraphInfo]

  /**
   * Build a new 'Graph info' operation.
   *
   * @param origin graph origin
   * @param location source with imported graph
   * @param publicKey key for graph if needed
   * @return 'Graph info' operation
   */
  @log
  def apply(origin: Symbol, location: URI, pgpPublicKey: Option[AnyRef]): Option[Abstract] =
    Some(operation.operation(origin, location, pgpPublicKey))

  /** Bridge between abstract api.Operation[OperationGraphInfo.Info] and concrete Operation[OperationGraphInfo.Info] */
  abstract class Abstract(val origin: Symbol, val location: URI, val pgpPublicKey: Option[AnyRef])
    extends Operation[api.OperationGraphInfo.Info](pgpPublicKey match {
      case Some(key) ⇒ s"Get information about private graph with ${origin} from ${location}."
      case None ⇒ s"Get information about public graph with ${origin} from ${location}."
    }) {
    this: Loggable ⇒
  }
  /**
   * Dependency injection routines.
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    lazy val operation = injectOptional[api.OperationGraphInfo] getOrElse new OperationGraphInfo
  }
}
