/**
 * @author VISTALL
 * @since 06/12/2022
 */
module consulo.java.guava.impl {
    requires consulo.java.language.impl;
    // TODO remove in future
    requires java.desktop;

    // need open for CacheValueManager analyze
    opens consulo.java.guava to consulo.application.impl;
}