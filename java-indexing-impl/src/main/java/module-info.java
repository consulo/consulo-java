/**
 * @author VISTALL
 * @since 03/12/2022
 */
module consulo.java.indexing.impl {
	requires transitive consulo.java.indexing.api;
	requires transitive consulo.java.language.impl;

	exports com.intellij.java.indexing.impl;
	exports com.intellij.java.indexing.impl.search;
	exports com.intellij.java.indexing.impl.stubs.index;
}