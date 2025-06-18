/**
 * @author VISTALL
 * @since 2022-12-07
 */
module consulo.java.coverage.impl {
    requires consulo.java.execution.api;
    requires consulo.java.execution.impl;
    requires consulo.java.language.impl;
    requires consulo.java.analysis.impl;
    requires consulo.java.coverage.rt;
    requires consulo.java.debugger.api;
    requires consulo.java.debugger.impl;

    requires org.jacoco.core;

    // TODO remove in future
    requires java.desktop;
   
    exports com.intellij.java.coverage;
    exports com.intellij.java.coverage.info;
    exports com.intellij.java.coverage.view;
    exports consulo.java.coverage.localize;
}