/**
 * @author VISTALL
 * @since 02/12/2022
 */
module consulo.java.jam.api {
	requires transitive consulo.java.language.api;
	requires transitive consulo.java.indexing.api;
	requires transitive com.intellij.xml;

	// TODO remove in future
	requires java.desktop;

	exports com.intellij.jam;
	exports com.intellij.jam.annotations;
	exports com.intellij.jam.model.common;
	exports com.intellij.jam.model.util;
	exports com.intellij.jam.reflect;
	exports com.intellij.jam.view;
	exports com.intellij.jam.view.tree;
	exports com.intellij.jam.view.ui;
	exports consulo.java.jam.util;
}