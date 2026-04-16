/**
 * @author VISTALL
 * @since 21/12/2022
 */
open module consulo.java.properties.impl {
  requires consulo.java.analysis.impl;
  requires consulo.java.language.impl;
  requires consulo.ide.api;
  requires consulo.language.editor.refactoring.api;
  requires consulo.language.impl;
  requires consulo.ui.ex.api;
  requires consulo.file.template.api;

  requires com.intellij.properties;
  requires com.intellij.xml.api;

  // TODO remove in future
  requires java.desktop;
  // TODO remove in future
  requires consulo.ide.impl;

  exports consulo.java.properties.impl.i18n;
  exports consulo.java.properties.impl.psi;
}