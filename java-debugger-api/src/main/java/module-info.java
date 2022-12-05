/**
 * @author VISTALL
 * @since 03/12/2022
 */
module consulo.java.debugger.api {
	requires transitive consulo.ide.api;
	requires transitive consulo.java.execution.api;
	requires transitive consulo.java.language.api;

	requires consulo.internal.jdi;

	exports com.intellij.java.debugger;
	exports com.intellij.java.debugger.engine;
	exports com.intellij.java.debugger.engine.evaluation;
	exports com.intellij.java.debugger.engine.evaluation.expression;
	exports com.intellij.java.debugger.engine.jdi;
	exports com.intellij.java.debugger.engine.managerThread;
	exports com.intellij.java.debugger.requests;
	exports com.intellij.java.debugger.ui.classFilter;
	exports com.intellij.java.debugger.ui.tree;
}