/**
 * @author VISTALL
 * @since 07/12/2022
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
}