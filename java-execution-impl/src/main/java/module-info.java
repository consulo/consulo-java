/**
 * @author VISTALL
 * @since 06/12/2022
 */
open module consulo.java.execution.impl {
    requires consulo.util.nodep;
    requires consulo.java.language.impl;
    requires consulo.java.execution.api;
    requires consulo.java.debugger.impl;
    requires consulo.java.analysis.impl;
    requires consulo.execution.test.api;
    requires consulo.java.rt.common;

    // TODO remove this dep in future
    requires java.desktop;
    requires consulo.ide.impl;
 
    exports com.intellij.java.execution.impl;
    exports com.intellij.java.execution.impl.actions;
    exports com.intellij.java.execution.impl.application;
    exports com.intellij.java.execution.impl.filters;
    exports com.intellij.java.execution.impl.jar;
    exports com.intellij.java.execution.impl.junit;
    exports com.intellij.java.execution.impl.junit2;
    exports com.intellij.java.execution.impl.junit2.info;
    exports com.intellij.java.execution.impl.remote;
    exports com.intellij.java.execution.impl.runners;
    exports com.intellij.java.execution.impl.stacktrace;
    exports com.intellij.java.execution.impl.testDiscovery;
    exports com.intellij.java.execution.impl.testframework;
    exports com.intellij.java.execution.impl.ui;
    exports com.intellij.java.execution.impl.util;
    exports consulo.java.execution.impl.testframework;
    exports consulo.java.execution.impl.util;
}