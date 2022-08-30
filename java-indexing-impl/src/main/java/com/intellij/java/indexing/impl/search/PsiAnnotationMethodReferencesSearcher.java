package com.intellij.java.indexing.impl.search;

import javax.annotation.Nonnull;

import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Processor;
import com.intellij.util.Query;
import com.intellij.util.QueryExecutor;
import com.intellij.openapi.application.ReadActionProcessor;

/**
 * @author max
 */
public class PsiAnnotationMethodReferencesSearcher implements QueryExecutor<PsiReference, ReferencesSearch.SearchParameters> {
  @Override
  public boolean execute(@Nonnull final ReferencesSearch.SearchParameters p, @Nonnull final Processor<? super PsiReference> consumer) {
    final PsiElement refElement = p.getElementToSearch();
    if (PsiUtil.isAnnotationMethod(refElement)) {
      PsiMethod method = (PsiMethod)refElement;
      if (PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME.equals(method.getName()) && method.getParameterList().getParametersCount() == 0) {
        final Query<PsiReference> query = ReferencesSearch.search(method.getContainingClass(), p.getScope(), p.isIgnoreAccessScope());
        return query.forEach(createImplicitDefaultAnnotationMethodConsumer(consumer));
      }
    }

    return true;
  }

  public static ReadActionProcessor<PsiReference> createImplicitDefaultAnnotationMethodConsumer(final Processor<? super PsiReference> consumer) {
    return new ReadActionProcessor<PsiReference>() {
      @Override
      public boolean processInReadAction(final PsiReference reference) {
        if (reference instanceof PsiJavaCodeReferenceElement) {
          PsiJavaCodeReferenceElement javaReference = (PsiJavaCodeReferenceElement)reference;
          if (javaReference.getParent() instanceof PsiAnnotation) {
            PsiNameValuePair[] members = ((PsiAnnotation)javaReference.getParent()).getParameterList().getAttributes();
            if (members.length == 1 && members[0].getNameIdentifier() == null) {
              PsiReference t = members[0].getReference();
              if (t != null && !consumer.process(t)) return false;
            }
          }
        }
        return true;
      }
    };
  }
}
