/**
 * @author VISTALL
 * @since 07/12/2022
 */
module consulo.java.intelliLang {
    // TODO drop in future
    requires consulo.ide.impl;

    requires consulo.java.language.impl;
    requires consulo.java.compiler.impl;
    requires consulo.java.analysis.impl;

    requires com.intellij.regexp;

    // TODO drop in future
    requires java.desktop;
}