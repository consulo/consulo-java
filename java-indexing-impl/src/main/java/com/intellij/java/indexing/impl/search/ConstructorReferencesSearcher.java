package com.intellij.java.indexing.impl.search;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import consulo.application.ApplicationManager;
import consulo.application.util.function.Computable;
import consulo.application.util.function.Processor;
import consulo.content.scope.SearchScope;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.PsiReference;
import consulo.language.psi.search.ReferencesSearch;
import consulo.project.util.query.QueryExecutorBase;

import javax.annotation.Nonnull;

/**
 * @author max
 */
public class ConstructorReferencesSearcher extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters> {
  @Override
  public void processQuery(@Nonnull final ReferencesSearch.SearchParameters p, @Nonnull Processor<? super PsiReference> consumer) {
    final PsiElement element = p.getElementToSearch();
    if (!(element instanceof PsiMethod)) {
      return;
    }
    final PsiMethod method = (PsiMethod) element;
    final PsiManager[] manager = new PsiManager[1];
    PsiClass aClass = ApplicationManager.getApplication().runReadAction(new Computable<PsiClass>() {
      @Override
      public PsiClass compute() {
        if (!method.isConstructor()) {
          return null;
        }
        PsiClass aClass = method.getContainingClass();
        manager[0] = aClass == null ? null : aClass.getManager();
        return aClass;
      }
    });
    if (manager[0] == null) {
      return;
    }
    SearchScope scope = ApplicationManager.getApplication().runReadAction(new Computable<SearchScope>() {
      @Override
      public SearchScope compute() {
        return p.getEffectiveSearchScope();
      }
    });
    new ConstructorReferencesSearchHelper(manager[0]).processConstructorReferences(consumer, method, aClass, scope, p.getProject(), p.isIgnoreAccessScope(), true, p.getOptimizer());
  }
}
