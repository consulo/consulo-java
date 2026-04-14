/**
 * @author VISTALL
 * @since 02/12/2022
 */
module consulo.java.execution.api {
	requires transitive consulo.java.language.api;

	requires consulo.util.nodep;
	requires consulo.execution.api;
	requires consulo.code.editor.api;
	requires consulo.color.scheme.api;
	requires consulo.datacontext.api;
	requires consulo.ui.ex.awt.api;

	exports com.intellij.java.execution;
	exports com.intellij.java.execution.configurations;
	exports com.intellij.java.execution.filters;
	exports com.intellij.java.execution.runners;
	exports com.intellij.java.execution.unscramble;
	exports consulo.java.execution;
	exports consulo.java.execution.configurations;
	exports consulo.java.execution.projectRoots;
	exports consulo.java.execution.localize;
}