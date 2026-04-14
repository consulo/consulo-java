/**
 * @author VISTALL
 * @since 06/12/2022
 */
module consulo.java.guava.impl {
    requires consulo.java.language.impl;
    requires consulo.language.impl;
    requires consulo.code.editor.api;
    requires consulo.language.editor.api;
    requires consulo.language.editor.ui.api;
    // TODO remove in future
    requires java.desktop;

    // need open for CacheValueManager analyze
    opens consulo.java.guava to consulo.application.impl;
}