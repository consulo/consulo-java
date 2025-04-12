package com.intellij.java.indexing.impl.search;

import com.intellij.java.language.psi.PsiAnnotation;
import com.intellij.java.language.psi.PsiJavaCodeReferenceElement;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiNameValuePair;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.ReadActionProcessor;
import consulo.application.util.function.Processor;
import consulo.application.util.query.Query;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.search.ReferencesSearchQueryExecutor;

import jakarta.annotation.Nonnull;

/**
 * @author max
 */
@ExtensionImpl
public class PsiAnnotationMethodReferencesSearcher implements ReferencesSearchQueryExecutor {
    @Override
    public boolean execute(@Nonnull final ReferencesSearch.SearchParameters p, @Nonnull final Processor<? super PsiReference> consumer) {
        final PsiElement refElement = p.getElementToSearch();
        if (PsiUtil.isAnnotationMethod(refElement)) {
            PsiMethod method = (PsiMethod)refElement;
            if (PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME.equals(method.getName())
                && method.getParameterList().getParametersCount() == 0) {
                final Query<PsiReference> query =
                    ReferencesSearch.search(method.getContainingClass(), p.getScope(), p.isIgnoreAccessScope());
                return query.forEach(createImplicitDefaultAnnotationMethodConsumer(consumer));
            }
        }

        return true;
    }

    public static ReadActionProcessor<PsiReference> createImplicitDefaultAnnotationMethodConsumer(
        final Processor<? super PsiReference> consumer
    ) {
        return new ReadActionProcessor<PsiReference>() {
            @Override
            public boolean processInReadAction(final PsiReference reference) {
                if (reference instanceof PsiJavaCodeReferenceElement) {
                    PsiJavaCodeReferenceElement javaReference = (PsiJavaCodeReferenceElement)reference;
                    if (javaReference.getParent() instanceof PsiAnnotation) {
                        PsiNameValuePair[] members = ((PsiAnnotation)javaReference.getParent()).getParameterList().getAttributes();
                        if (members.length == 1 && members[0].getNameIdentifier() == null) {
                            PsiReference t = members[0].getReference();
                            if (t != null && !consumer.process(t)) {
                                return false;
                            }
                        }
                    }
                }
                return true;
            }
        };
    }
}
