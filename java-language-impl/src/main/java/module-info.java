/**
 * @author VISTALL
 * @since 03/12/2022
 */
open module consulo.java.language.impl {
  requires transitive consulo.java.language.api;
  requires transitive consulo.java.indexing.api;

  requires consulo.language.impl;

  requires one.util.streamex;
  requires transitive asm.tree;
  requires transitive asm.commons;
  requires transitive asm.util;
  requires transitive asm;

  // TODO remove in future
  requires java.desktop;

  exports com.intellij.java.impl.externalSystem;
  exports com.intellij.java.language.impl;
  exports com.intellij.java.language.impl.codeInsight;
  exports com.intellij.java.language.impl.codeInsight.completion.proc;
  exports com.intellij.java.language.impl.codeInsight.completion.scope;
  exports com.intellij.java.language.impl.codeInsight.daemon;
  exports com.intellij.java.language.impl.codeInsight.daemon.impl.analysis;
  exports com.intellij.java.language.impl.codeInsight.folding.impl;
  exports com.intellij.java.language.impl.codeInsight.generation;
  exports com.intellij.java.language.impl.codeInsight.highlighting;
  exports com.intellij.java.language.impl.codeInsight.javadoc;
  exports com.intellij.java.language.impl.core;
  exports com.intellij.java.language.impl.lexer;
  exports com.intellij.java.language.impl.parser;
  exports com.intellij.java.language.impl.projectRoots;
  exports com.intellij.java.language.impl.projectRoots.ex;
  exports com.intellij.java.language.impl.psi;
  exports com.intellij.java.language.impl.psi.controlFlow;
  exports com.intellij.java.language.impl.psi.filters;
  exports com.intellij.java.language.impl.psi.filters.classes;
  exports com.intellij.java.language.impl.psi.filters.element;
  exports com.intellij.java.language.impl.psi.impl;
  exports com.intellij.java.language.impl.psi.impl.cache;
  exports com.intellij.java.language.impl.psi.impl.compiled;
  exports com.intellij.java.language.impl.psi.impl.file;
  exports com.intellij.java.language.impl.psi.impl.file.impl;
  exports com.intellij.java.language.impl.psi.impl.java.stubs;
  exports com.intellij.java.language.impl.psi.impl.java.stubs.impl;
  exports com.intellij.java.language.impl.psi.impl.java.stubs.index;
  exports com.intellij.java.language.impl.psi.impl.light;
  exports com.intellij.java.language.impl.psi.impl.smartPointers;
  exports com.intellij.java.language.impl.psi.impl.source;
  exports com.intellij.java.language.impl.psi.impl.source.javadoc;
  exports com.intellij.java.language.impl.psi.impl.source.resolve;
  exports com.intellij.java.language.impl.psi.impl.source.resolve.graphInference;
  exports com.intellij.java.language.impl.psi.impl.source.resolve.graphInference.constraints;
  exports com.intellij.java.language.impl.psi.impl.source.resolve.reference.impl;
  exports com.intellij.java.language.impl.psi.impl.source.resolve.reference.impl.providers;
  exports com.intellij.java.language.impl.psi.impl.source.tree;
  exports com.intellij.java.language.impl.psi.impl.source.tree.injected;
  exports com.intellij.java.language.impl.psi.impl.source.tree.java;
  exports com.intellij.java.language.impl.psi.presentation.java;
  exports com.intellij.java.language.impl.psi.scope;
  exports com.intellij.java.language.impl.psi.scope.conflictResolvers;
  exports com.intellij.java.language.impl.psi.scope.processor;
  exports com.intellij.java.language.impl.psi.scope.util;
  exports com.intellij.java.language.impl.psi.util;
  exports com.intellij.java.language.impl.refactoring.util;
  exports com.intellij.java.language.impl.spi;
  exports com.intellij.java.language.impl.spi.parsing;
  exports com.intellij.java.language.impl.spi.psi;
  exports com.intellij.java.language.impl.ui;
  exports com.intellij.java.language.impl.util.text;
  exports consulo.java.language.impl;
  exports consulo.java.language.impl.codeInsight;
  exports consulo.java.language.impl.icon;
  exports consulo.java.language.impl.psi;
  exports consulo.java.language.impl.psi.augment;
  exports consulo.java.language.impl.psi.stub;
  exports consulo.java.language.impl.spi;
  exports consulo.java.language.impl.util;
}