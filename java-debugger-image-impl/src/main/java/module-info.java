/**
 * @author VISTALL
 * @since 20/12/2022
 */
module consulo.java.debugger.image.impl {
  requires com.intellij.images_image.api;
  requires consulo.java.debugger.impl;
  requires consulo.internal.jdi;
  requires consulo.java.rt.common;

  // TODO remove it in future
  requires java.desktop;
}