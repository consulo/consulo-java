/**
 * @author VISTALL
 * @since 06/12/2022
 */
module consulo.java.compiler.artifact.impl {
    requires consulo.ide.api;
    requires consulo.java.language.impl;

    // TODO remove in future
    requires java.desktop;

    exports com.intellij.java.compiler.artifact.impl;
    exports com.intellij.java.compiler.artifact.impl.artifacts;
    exports com.intellij.java.compiler.artifact.impl.elements;
    exports com.intellij.java.compiler.artifact.impl.ui;
    exports com.intellij.java.compiler.artifact.impl.ui.properties;
}