/**
 * @author VISTALL
 * @since 2022-12-06
 */
module consulo.java.compiler.artifact.impl {
    requires consulo.ide.api;
    requires consulo.java.language.impl;

    // TODO remove in future
    requires java.desktop;
    requires forms.rt;

    exports com.intellij.java.compiler.artifact.impl;
    exports com.intellij.java.compiler.artifact.impl.artifacts;
    exports com.intellij.java.compiler.artifact.impl.elements;
    exports com.intellij.java.compiler.artifact.impl.ui;
    exports com.intellij.java.compiler.artifact.impl.ui.properties;
}