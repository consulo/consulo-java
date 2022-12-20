/**
 * @author VISTALL
 * @since 21/12/2022
 */
open module consulo.java.properties.impl {
  requires consulo.java.analysis.impl;
  requires consulo.java.language.impl;

  requires com.intellij.properties;
  requires com.intellij.xml;

  // TODO remove in future
  requires java.desktop;
  // TODO remove in future
  requires consulo.ide.impl;
}