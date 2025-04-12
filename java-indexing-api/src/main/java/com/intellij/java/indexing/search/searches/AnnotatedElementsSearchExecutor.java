package com.intellij.java.indexing.search.searches;

import com.intellij.java.language.psi.PsiModifierListOwner;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.application.util.query.QueryExecutor;

/**
 * @author VISTALL
 * @since 2022-09-01
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface AnnotatedElementsSearchExecutor extends QueryExecutor<PsiModifierListOwner, AnnotatedElementsSearch.Parameters> {
}
