/**
 * @author VISTALL
 * @since 02/12/2022
 */
module consulo.java.analysis.api {
    requires transitive consulo.java.language.api;

    exports com.intellij.java.analysis;
    exports com.intellij.java.analysis.codeInsight.daemon;
    exports com.intellij.java.analysis.codeInsight.guess;
    exports com.intellij.java.analysis.codeInsight.intention;
    exports com.intellij.java.analysis.codeInspection;
    exports com.intellij.java.analysis.codeInspection.ex;
    exports com.intellij.java.analysis.codeInspection.reference;
    exports com.intellij.java.analysis.property;
    exports com.intellij.java.analysis.refactoring;
    exports consulo.java.analysis.codeInsight;
    exports consulo.java.analysis.codeInspection;

    exports consulo.java.deadCodeNotWorking;
}