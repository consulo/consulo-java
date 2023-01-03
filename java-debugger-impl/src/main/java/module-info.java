/**
 * @author VISTALL
 * @since 05/12/2022
 */
open module consulo.java.debugger.impl {
  requires transitive consulo.java.debugger.api;
  requires consulo.java.language.impl;
  requires consulo.java.indexing.impl;
  requires consulo.java.analysis.impl;
  requires consulo.java.compiler.api;
  requires consulo.java.rt.common;

  // TODO remove in future
  requires java.desktop;
  // TODO remove in future
  requires consulo.ide.impl;

  requires one.util.streamex;

  requires consulo.internal.jdi;
  requires asm;
  requires asm.analysis;
  requires asm.commons;
  requires asm.tree;
  requires asm.util;

  exports com.intellij.java.debugger.impl;
  exports com.intellij.java.debugger.impl.actions;
  exports com.intellij.java.debugger.impl.apiAdapters;
  exports com.intellij.java.debugger.impl.breakpoints;
  exports com.intellij.java.debugger.impl.breakpoints.properties;
  exports com.intellij.java.debugger.impl.classFilter;
  exports com.intellij.java.debugger.impl.codeinsight;
  exports com.intellij.java.debugger.impl.descriptors.data;
  exports com.intellij.java.debugger.impl.engine;
  exports com.intellij.java.debugger.impl.engine.evaluation;
  exports com.intellij.java.debugger.impl.engine.evaluation.expression;
  exports com.intellij.java.debugger.impl.engine.events;
  exports com.intellij.java.debugger.impl.engine.requests;
  exports com.intellij.java.debugger.impl.externalSystem;
  exports com.intellij.java.debugger.impl.jdi;
  exports com.intellij.java.debugger.impl.memory.action;
  exports com.intellij.java.debugger.impl.memory.action.tracking;
  exports com.intellij.java.debugger.impl.memory.component;
  exports com.intellij.java.debugger.impl.memory.event;
  exports com.intellij.java.debugger.impl.memory.filtering;
  exports com.intellij.java.debugger.impl.memory.tracking;
  exports com.intellij.java.debugger.impl.memory.ui;
  exports com.intellij.java.debugger.impl.memory.utils;
  exports com.intellij.java.debugger.impl.settings;
  exports com.intellij.java.debugger.impl.ui;
  exports com.intellij.java.debugger.impl.ui.breakpoints;
  exports com.intellij.java.debugger.impl.ui.impl;
  exports com.intellij.java.debugger.impl.ui.impl.nodes;
  exports com.intellij.java.debugger.impl.ui.impl.tree;
  exports com.intellij.java.debugger.impl.ui.impl.watch;
  exports com.intellij.java.debugger.impl.ui.tree;
  exports com.intellij.java.debugger.impl.ui.tree.actions;
  exports com.intellij.java.debugger.impl.ui.tree.render;
  exports consulo.java.debugger.impl;
  exports consulo.java.debugger.impl.apiAdapters;
  exports consulo.java.debugger.impl.settings;
}