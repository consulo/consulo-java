package com.intellij.java.indexing.impl.search;

import com.intellij.java.indexing.search.searches.ClassInheritorsSearch;
import com.intellij.java.indexing.search.searches.OverridingMethodsSearch;
import com.intellij.java.indexing.search.searches.OverridingMethodsSearchExecutor;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.MethodSignature;
import com.intellij.java.language.psi.util.MethodSignatureUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.Application;
import consulo.application.ApplicationManager;
import consulo.application.util.function.Computable;
import consulo.application.util.function.Processor;
import consulo.content.scope.SearchScope;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * @author max
 */
@ExtensionImpl
public class JavaOverridingMethodsSearcher implements OverridingMethodsSearchExecutor {
    @Override
    public boolean execute(
        @Nonnull OverridingMethodsSearch.SearchParameters p,
        @Nonnull Predicate<? super PsiMethod> consumer
    ) {
        PsiMethod method = p.getMethod();
        SearchScope scope = p.getScope();

        PsiClass parentClass = Application.get().runReadAction((Supplier<PsiClass>)method::getContainingClass);
        assert parentClass != null;
        Predicate<PsiClass> inheritorsProcessor = inheritor -> {
            PsiMethod found = Application.get().runReadAction((Supplier<PsiMethod>)() -> findOverridingMethod(inheritor, method, parentClass));
            return found == null || consumer.test(found) && p.isCheckDeep();
        };

        return ClassInheritorsSearch.search(parentClass, scope, true).forEach(inheritorsProcessor);
    }

    @Nullable
    public static PsiMethod findOverridingMethod(PsiClass inheritor, PsiMethod method, @Nonnull PsiClass methodContainingClass) {
        String name = method.getName();
        if (inheritor.findMethodsByName(name, false).length > 0) {
            PsiMethod found = MethodSignatureUtil.findMethodBySuperSignature(
                inheritor,
                getSuperSignature(inheritor, methodContainingClass, method),
                false
            );
            if (found != null && isAcceptable(found, method)) {
                return found;
            }
        }

        if (methodContainingClass.isInterface() && !inheritor.isInterface()) {  //check for sibling implementation
            PsiClass superClass = inheritor.getSuperClass();
            if (superClass != null && !superClass.isInheritor(methodContainingClass, true)
                && superClass.findMethodsByName(name, true).length > 0) {
                MethodSignature signature = getSuperSignature(inheritor, methodContainingClass, method);
                PsiMethod derived = MethodSignatureUtil.findMethodInSuperClassBySignatureInDerived(inheritor, superClass, signature, true);
                if (derived != null && isAcceptable(derived, method)) {
                    return derived;
                }
            }
        }
        return null;
    }

    @Nonnull
    private static MethodSignature getSuperSignature(PsiClass inheritor, @Nonnull PsiClass parentClass, PsiMethod method) {
        PsiSubstitutor substitutor = TypeConversionUtil.getMaybeSuperClassSubstitutor(parentClass, inheritor, PsiSubstitutor.EMPTY);
        // if null, we have EJB custom inheritance here and still check overriding
        return method.getSignature(substitutor != null ? substitutor : PsiSubstitutor.EMPTY);
    }


    private static boolean isAcceptable(PsiMethod found, PsiMethod method) {
        return !found.isStatic() && (!method.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)
            || JavaPsiFacade.getInstance(found.getProject()).arePackagesTheSame(method.getContainingClass(), found.getContainingClass()));
    }
}
