package org.digimead.tabuddy.desktop.moddef.job

import org.digimead.digi.lib.log.api.Loggable
import org.digimead.tabuddy.desktop.logic.payload.api.ElementTemplate
import org.eclipse.core.runtime.IProgressMonitor
import org.digimead.tabuddy.desktop.definition.Operation
import org.eclipse.core.runtime.IAdaptable
import org.digimead.tabuddy.model.Model
import org.digimead.tabuddy.desktop.support.App
import org.digimead.tabuddy.desktop.moddef.dialog.eltemlist.ElementTemplateList

class OperationModifyElementTemplateList(elementTemplates: Set[ElementTemplate], modelID: Symbol)
  extends org.digimead.tabuddy.desktop.logic.operation.OperationModifyElementTemplateList.Abstract(elementTemplates, modelID) with Loggable {
  @volatile protected var allowExecute = true
  @volatile protected var allowRedo = false
  @volatile protected var allowUndo = false

  override def canExecute() = allowExecute
  override def canRedo() = allowRedo
  override def canUndo() = allowUndo

  protected def execute(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[Set[ElementTemplate]] = redo(monitor, info)
  protected def run(monitor: IProgressMonitor): Operation.Result[Set[ElementTemplate]] = {
    /*monitor.beginTask("My job is working...", 100)
        for (i <- 0 until 100) {
          try {
            Thread.sleep(200)
          }
          monitor.worked(1)
        }
        monitor.done()*/
    log.___gaze("OperationModifyElementTemplateList")
    Operation.Result.OK()
  }
  protected def redo(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[Set[ElementTemplate]] = {
    assert(Model.eId == modelID, "An unexpected model %s, expect %s".format(Model.eId, modelID))
    // process the job
    val result: Operation.Result[Set[ElementTemplate]] = if (canRedo) {
      // TODO replay history, modify ElementTemplate.container: before -> after
      Operation.Result.Error("Unimplemented")
    } else if (canExecute) {
      // TODO save modification history
      App.execNGet {
        val dialog = new ElementTemplateList(null, elementTemplates)
        /*Window.currentShell.withValue(Some(dialog.getShell)) {
          dialog.open() == org.eclipse.jface.window.Window.OK
        } match {
          case true => Operation.Result.OK(Some(dialog.getModifiedTemplates()))
          case false => Operation.Result.Cancel()
        }*/
        Operation.Result.OK()
      }
    } else
      Operation.Result.Error(s"Unable to process $this: redo and execute are prohibited")
    // update the job state
    result match {
      case Operation.Result.OK(_, _) =>
        allowExecute = false
        allowRedo = false
        allowUndo = true
      case Operation.Result.Cancel(_) =>
        allowExecute = true
        allowRedo = false
        allowUndo = false
      case _ =>
        allowExecute = false
        allowRedo = false
        allowUndo = false
    }
    // return the result
    result
  }
  protected def undo(monitor: IProgressMonitor, info: IAdaptable): Operation.Result[Set[ElementTemplate]] = {
    // TODO revert history, modify elementTemplate: after -> before
    Operation.Result.Error("Unimplemented")
  }
}
