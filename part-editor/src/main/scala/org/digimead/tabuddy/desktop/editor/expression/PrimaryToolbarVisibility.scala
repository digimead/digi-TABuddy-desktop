package org.digimead.tabuddy.desktop.editor.expression

import org.digimead.digi.lib.log.api.Loggable
import org.eclipse.core.expressions.PropertyTester

class PrimaryToolbarVisibility extends PropertyTester with Loggable {
  def test(receiver: AnyRef, property: String, args: Array[AnyRef], expectedValue: AnyRef): Boolean = {
    log.___gaze("AAAA")
    false
  }
}

object PrimaryToolbarVisibility {

}