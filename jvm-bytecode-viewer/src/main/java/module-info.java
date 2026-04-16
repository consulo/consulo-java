/**
 * @author VISTALL
 * @since 06/12/2022
 */
open module consulo.java.jvm.bytecode.viewer {
    requires consulo.java.debugger.impl;
    requires consulo.java.language.impl;
    requires consulo.ide.api;
    requires consulo.compiler.api;
    requires consulo.execution.api;

    // TODO remove in future
    requires consulo.ide.impl;
     // TODO remove in future
    requires java.desktop;
}