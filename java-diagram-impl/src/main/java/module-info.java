import org.jspecify.annotations.NullMarked;

/**
 * @author VISTALL
 * @since 2026-10-01
 */
@NullMarked
module consulo.java.diagram.impl {
    requires consulo.diagram.api;
    requires consulo.language.editor.api;
    requires consulo.java.language.api;
    requires consulo.java.indexing.api;

    exports consulo.java.diagram.impl;
    exports consulo.java.diagram.impl.providers;
}
