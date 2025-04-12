package com.intellij.java.indexing.search.searches;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.application.util.query.QueryExecutor;
import consulo.language.psi.PsiReference;

/**
 * @author VISTALL
 * @since 2022-09-01
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface MethodReferencesSearchExecutor extends QueryExecutor<PsiReference, MethodReferencesSearch.SearchParameters> {
}
