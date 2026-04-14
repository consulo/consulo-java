/**
 * @author VISTALL
 * @since 20/12/2022
 */
module consulo.java.debugger.image.impl {
  requires com.intellij.images_image.api;
  requires consulo.java.debugger.impl;
  requires consulo.internal.jdi;
  requires consulo.java.rt.common;

  requires consulo.ide.impl;
  requires consulo.execution.debug.api;
  requires consulo.ui.ex.awt.api;

  // TODO remove it in future
  requires java.desktop;
}