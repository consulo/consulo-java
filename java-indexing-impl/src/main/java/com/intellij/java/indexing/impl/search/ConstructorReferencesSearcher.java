package com.intellij.java.indexing.impl.search;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.Application;
import consulo.content.scope.SearchScope;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.PsiReference;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.search.ReferencesSearchQueryExecutor;
import consulo.project.util.query.QueryExecutorBase;
import jakarta.annotation.Nonnull;

import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * @author max
 */
@ExtensionImpl
public class ConstructorReferencesSearcher extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters> implements ReferencesSearchQueryExecutor {
    @Override
    public void processQuery(@Nonnull ReferencesSearch.SearchParameters p, @Nonnull Predicate<? super PsiReference> consumer) {
        PsiElement element = p.getElementToSearch();
        if (!(element instanceof PsiMethod method)) {
            return;
        }
        PsiManager[] manager = new PsiManager[1];
        PsiClass aClass = Application.get().runReadAction((Supplier<PsiClass>)() -> {
            if (!method.isConstructor()) {
                return null;
            }
            PsiClass aClass1 = method.getContainingClass();
            manager[0] = aClass1 == null ? null : aClass1.getManager();
            return aClass1;
        });
        if (manager[0] == null) {
            return;
        }
        SearchScope scope = Application.get().runReadAction((Supplier<SearchScope>)p::getEffectiveSearchScope);
        new ConstructorReferencesSearchHelper(manager[0]).processConstructorReferences(
            consumer,
            method,
            aClass,
            scope,
            p.getProject(),
            p.isIgnoreAccessScope(),
            true,
            p.getOptimizer()
        );
    }
}
