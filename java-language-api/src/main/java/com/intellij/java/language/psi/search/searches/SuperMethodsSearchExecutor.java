package com.intellij.java.language.psi.search.searches;

import com.intellij.java.language.psi.util.MethodSignatureBackedByPsiMethod;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.application.util.query.QueryExecutor;

/**
 * @author VISTALL
 * @since 2022-09-01
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface SuperMethodsSearchExecutor extends QueryExecutor<MethodSignatureBackedByPsiMethod, SuperMethodsSearch.SearchParameters> {
}
