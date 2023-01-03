/**
 * @author VISTALL
 * @since 03/01/2023
 */
module consulo.java.regexp.impl {
  requires consulo.java.language.api;
  requires consulo.java.intelliLang;
  requires com.intellij.regexp;

  // TODO remove in future
  requires consulo.ide.impl;
}