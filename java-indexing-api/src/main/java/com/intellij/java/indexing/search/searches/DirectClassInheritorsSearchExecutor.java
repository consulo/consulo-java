package com.intellij.java.indexing.search.searches;

import com.intellij.java.language.psi.PsiClass;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.application.util.query.QueryExecutor;

/**
 * @author VISTALL
 * @since 01-Sep-22
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface DirectClassInheritorsSearchExecutor extends QueryExecutor<PsiClass, DirectClassInheritorsSearch.SearchParameters> {
}
