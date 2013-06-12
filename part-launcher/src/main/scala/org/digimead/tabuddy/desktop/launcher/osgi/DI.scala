package org.digimead.tabuddy.desktop.launcher.osgi

import com.escalatesoft.subcut.inject.BindingModule

/** OSGi framework DI initializer */
class DI {
  /** Initialize DI with DI class loader. */
  def initialize(diLoader: ClassLoader, framework: Framework, di: Seq[() => BindingModule]) {

  }
}
