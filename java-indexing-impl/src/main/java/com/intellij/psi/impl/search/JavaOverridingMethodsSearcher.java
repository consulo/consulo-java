package com.intellij.psi.impl.search;

import javax.annotation.Nonnull;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;

/**
 * @author max
 */
public class JavaOverridingMethodsSearcher implements QueryExecutor<PsiMethod, OverridingMethodsSearch.SearchParameters>
{
	@Override
	public boolean execute(@Nonnull final OverridingMethodsSearch.SearchParameters p, @Nonnull final Processor<PsiMethod> consumer)
	{
		final PsiMethod method = p.getMethod();
		final SearchScope scope = p.getScope();

		final PsiClass parentClass = ApplicationManager.getApplication().runReadAction(new Computable<PsiClass>()
		{
			@javax.annotation.Nullable
			@Override
			public PsiClass compute()
			{
				return method.getContainingClass();
			}
		});
		assert parentClass != null;
		Processor<PsiClass> inheritorsProcessor = new Processor<PsiClass>()
		{
			@Override
			public boolean process(final PsiClass inheritor)
			{
				PsiMethod found = ApplicationManager.getApplication().runReadAction(new Computable<PsiMethod>()
				{
					@Override
					@javax.annotation.Nullable
					public PsiMethod compute()
					{
						return findOverridingMethod(inheritor, parentClass, method);
					}
				});
				return found == null || consumer.process(found) && p.isCheckDeep();
			}
		};

		return ClassInheritorsSearch.search(parentClass, scope, true).forEach(inheritorsProcessor);
	}

	@javax.annotation.Nullable
	private static PsiMethod findOverridingMethod(PsiClass inheritor, @Nonnull PsiClass parentClass, PsiMethod method)
	{
		String name = method.getName();
		if(inheritor.findMethodsByName(name, false).length > 0)
		{
			PsiMethod found = MethodSignatureUtil.findMethodBySuperSignature(inheritor, getSuperSignature(inheritor, parentClass, method), false);
			if(found != null && isAcceptable(found, method))
			{
				return found;
			}
		}

		if(parentClass.isInterface() && !inheritor.isInterface())
		{  //check for sibling implementation
			final PsiClass superClass = inheritor.getSuperClass();
			if(superClass != null && !superClass.isInheritor(parentClass, true) && superClass.findMethodsByName(name, true).length > 0)
			{
				MethodSignature signature = getSuperSignature(inheritor, parentClass, method);
				PsiMethod derived = MethodSignatureUtil.findMethodInSuperClassBySignatureInDerived(inheritor, superClass, signature, true);
				if(derived != null && isAcceptable(derived, method))
				{
					return derived;
				}
			}
		}
		return null;
	}

	@Nonnull
	private static MethodSignature getSuperSignature(PsiClass inheritor, @Nonnull PsiClass parentClass, PsiMethod method)
	{
		PsiSubstitutor substitutor = TypeConversionUtil.getMaybeSuperClassSubstitutor(parentClass, inheritor, PsiSubstitutor.EMPTY, null);
		// if null, we have EJB custom inheritance here and still check overriding
		return method.getSignature(substitutor != null ? substitutor : PsiSubstitutor.EMPTY);
	}


	private static boolean isAcceptable(final PsiMethod found, final PsiMethod method)
	{
		return !found.hasModifierProperty(PsiModifier.STATIC) && (!method.hasModifierProperty(PsiModifier.PACKAGE_LOCAL) || JavaPsiFacade.getInstance(found.getProject()).arePackagesTheSame(method
				.getContainingClass(), found.getContainingClass()));
	}
}
