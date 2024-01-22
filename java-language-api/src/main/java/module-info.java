/**
 * @author VISTALL
 * @since 02/12/2022
 */
open module consulo.java.language.api {
  requires transitive consulo.ide.api;

  requires kava.beans;

  exports com.intellij.java.language;
  exports com.intellij.java.language.codeInsight;
  exports com.intellij.java.language.codeInsight.daemon.impl.analysis;
  exports com.intellij.java.language.codeInsight.folding;
  exports com.intellij.java.language.codeInsight.runner;
  exports com.intellij.java.language.jvm;
  exports com.intellij.java.language.jvm.facade;
  exports com.intellij.java.language.jvm.types;
  exports com.intellij.java.language.lexer;
  exports com.intellij.java.language.module;
  exports com.intellij.java.language.patterns;
  exports com.intellij.java.language.projectRoots;
  exports com.intellij.java.language.projectRoots.roots;
  exports com.intellij.java.language.psi;
  exports com.intellij.java.language.psi.augment;
  exports com.intellij.java.language.psi.codeStyle;
  exports com.intellij.java.language.psi.compiled;
  exports com.intellij.java.language.psi.impl.source.resolve;
  exports com.intellij.java.language.psi.impl.source.resolve.graphInference;
  exports com.intellij.java.language.psi.infos;
  exports com.intellij.java.language.psi.javadoc;
  exports com.intellij.java.language.psi.ref;
  exports com.intellij.java.language.psi.scope;
  exports com.intellij.java.language.psi.search;
  exports com.intellij.java.language.psi.search.searches;
  exports com.intellij.java.language.psi.stubs;
  exports com.intellij.java.language.psi.targets;
  exports com.intellij.java.language.psi.tree;
  exports com.intellij.java.language.psi.tree.java;
  exports com.intellij.java.language.psi.util;
  exports com.intellij.java.language.spi;
  exports com.intellij.java.language.testIntegration;
  exports com.intellij.java.language.util;
  exports com.intellij.java.language.util.cls;
  exports com.intellij.java.language.vfs.jrt;
  exports consulo.java.language.fileTypes;
  exports consulo.java.language.module.extension;
  exports consulo.java.language.module.util;
  exports consulo.java.language.psi;
}