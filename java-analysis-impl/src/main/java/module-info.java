/**
 * @author VISTALL
 * @since 03/12/2022
 */
open module consulo.java.analysis.impl {
  requires transitive consulo.ide.api;
  requires transitive consulo.java.analysis.api;
  requires transitive consulo.java.language.impl;
  requires transitive consulo.java.indexing.impl;

  requires com.intellij.xml;

  requires consulo.language.editor.impl;

  requires one.util.streamex;

  // TODO remove in future
  requires java.desktop;

  requires java.management;

  requires asm;
  requires asm.analysis;
  requires asm.commons;
  requires asm.tree;
  requires asm.util;

  exports com.intellij.java.analysis.impl;
  exports com.intellij.java.analysis.impl.codeInsight;
  exports com.intellij.java.analysis.impl.codeInsight.daemon.impl;
  exports com.intellij.java.analysis.impl.codeInsight.daemon.impl.actions;
  exports com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis;
  exports com.intellij.java.analysis.impl.codeInsight.daemon.impl.quickfix;
  exports com.intellij.java.analysis.impl.codeInsight.guess.impl;
  exports com.intellij.java.analysis.impl.codeInsight.intention;
  exports com.intellij.java.analysis.impl.codeInsight.intention.impl;
  exports com.intellij.java.analysis.impl.codeInsight.quickfix;
  exports com.intellij.java.analysis.impl.codeInspection;
  exports com.intellij.java.analysis.impl.codeInspection.booleanIsAlwaysInverted;
  exports com.intellij.java.analysis.impl.codeInspection.bytecodeAnalysis;
  exports com.intellij.java.analysis.impl.codeInspection.bytecodeAnalysis.asm;
  exports com.intellij.java.analysis.impl.codeInspection.canBeFinal;
  exports com.intellij.java.analysis.impl.codeInspection.concurrencyAnnotations;
  exports com.intellij.java.analysis.impl.codeInspection.dataFlow;
  exports com.intellij.java.analysis.impl.codeInspection.dataFlow.fix;
  exports com.intellij.java.analysis.impl.codeInspection.dataFlow.inference;
  exports com.intellij.java.analysis.impl.codeInspection.dataFlow.inliner;
  exports com.intellij.java.analysis.impl.codeInspection.dataFlow.instructions;
  exports com.intellij.java.analysis.impl.codeInspection.dataFlow.rangeSet;
  exports com.intellij.java.analysis.impl.codeInspection.dataFlow.types;
  exports com.intellij.java.analysis.impl.codeInspection.dataFlow.value;
  exports com.intellij.java.analysis.impl.codeInspection.deadCode;
  exports com.intellij.java.analysis.impl.codeInspection.deprecation;
  exports com.intellij.java.analysis.impl.codeInspection.equalsAndHashcode;
  exports com.intellij.java.analysis.impl.codeInspection.ex;
  exports com.intellij.java.analysis.impl.codeInspection.java15api;
  exports com.intellij.java.analysis.impl.codeInspection.localCanBeFinal;
  exports com.intellij.java.analysis.impl.codeInspection.miscGenerics;
  exports com.intellij.java.analysis.impl.codeInspection.nullable;
  exports com.intellij.java.analysis.impl.codeInspection.redundantCast;
  exports com.intellij.java.analysis.impl.codeInspection.reference;
  exports com.intellij.java.analysis.impl.codeInspection.unusedImport;
  exports com.intellij.java.analysis.impl.codeInspection.unusedSymbol;
  exports com.intellij.java.analysis.impl.codeInspection.util;
  exports com.intellij.java.analysis.impl.find.findUsages;
  exports com.intellij.java.analysis.impl.generate.config;
  exports com.intellij.java.analysis.impl.ide.highlighter;
  exports com.intellij.java.analysis.impl.psi.controlFlow;
  exports com.intellij.java.analysis.impl.psi.impl.search;
  exports com.intellij.java.analysis.impl.psi.impl.source.resolve.reference.impl;
  exports com.intellij.java.analysis.impl.psi.util;
  exports com.intellij.java.analysis.impl.refactoring.extractMethod;
  exports com.intellij.java.analysis.impl.refactoring.util;
  exports com.intellij.java.analysis.impl.refactoring.util.duplicates;
  exports com.siyeh;
  exports com.siyeh.ig;
  exports com.siyeh.ig.bugs;
  exports com.siyeh.ig.callMatcher;
  exports com.siyeh.ig.fixes;
  exports com.siyeh.ig.inheritance;
  exports com.siyeh.ig.junit;
  exports com.siyeh.ig.memory;
  exports com.siyeh.ig.numeric;
  exports com.siyeh.ig.psiutils;
  exports com.siyeh.ig.ui;
  exports consulo.java.analysis.impl;
  exports consulo.java.analysis.impl.codeInsight;
  exports consulo.java.analysis.impl.codeInsight.completion;
  exports consulo.java.analysis.impl.util;
  exports org.jetbrains.java.generate;
  exports org.jetbrains.java.generate.inspection;
  exports org.jetbrains.java.generate.psi;
}