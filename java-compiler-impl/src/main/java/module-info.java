/**
 * @author VISTALL
 * @since 2022-12-06
 */
module consulo.java.compiler.impl {
    requires consulo.java.language.impl;
    requires consulo.java.analysis.impl;
    requires consulo.java.execution.api;
    requires instrumentation.util;
    requires instrumentation.util8;
    requires notnull.compiler;
    requires consulo.java.rt.common;

    requires org.apache.thrift;
    requires org.apache.commons.lang3;
    requires org.slf4j;

    // TODO remove in future
    requires java.desktop;

    exports consulo.java.compiler;
    exports consulo.java.compiler.bytecodeProcessing;
    exports consulo.java.compiler.bytecodeProcessing.impl;
    exports consulo.java.compiler.impl.javaCompiler;
    exports consulo.java.compiler.impl.javaCompiler.old;
    exports com.intellij.java.compiler.impl;
    exports com.intellij.java.compiler.impl.actions;
    exports com.intellij.java.compiler.impl.cache;
    exports com.intellij.java.compiler.impl.classParsing;
    exports com.intellij.java.compiler.impl.javaCompiler;
    exports com.intellij.java.compiler.impl.javaCompiler.annotationProcessing;
    exports com.intellij.java.compiler.impl.javaCompiler.annotationProcessing.impl;
    exports com.intellij.java.compiler.impl.javaCompiler.javac;
    exports com.intellij.java.compiler.impl.options;
    exports com.intellij.java.compiler.impl.util.cls;
    exports consulo.java.compiler.impl.javaCompiler.ui;
}