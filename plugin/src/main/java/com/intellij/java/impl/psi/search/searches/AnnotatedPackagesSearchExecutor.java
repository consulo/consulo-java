package com.intellij.java.impl.psi.search.searches;

import com.intellij.java.language.psi.PsiJavaPackage;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.application.util.query.QueryExecutor;

/**
 * @author VISTALL
 * @since 14/11/2022
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface AnnotatedPackagesSearchExecutor extends QueryExecutor<PsiJavaPackage, AnnotatedPackagesSearch.Parameters> {
}
