/**
 * @author VISTALL
 * @since 02/12/2022
 */
module consulo.java.execution.api {
	requires transitive consulo.java.language.api;

	requires consulo.util.nodep;

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