package com.intellij.psi.impl.search;

import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;

/**
 * @author max
 */
public class ConstructorReferencesSearcher extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>
{
	@Override
	public void processQuery(@NotNull ReferencesSearch.SearchParameters p, @NotNull Processor<PsiReference> consumer)
	{
		final PsiElement element = p.getElementToSearch();
		if(!(element instanceof PsiMethod))
		{
			return;
		}
		final PsiMethod method = (PsiMethod) element;
		final PsiManager[] manager = new PsiManager[1];
		PsiClass aClass = ApplicationManager.getApplication().runReadAction(new Computable<PsiClass>()
		{
			@Override
			public PsiClass compute()
			{
				if(!method.isConstructor())
				{
					return null;
				}
				PsiClass aClass = method.getContainingClass();
				manager[0] = aClass == null ? null : aClass.getManager();
				return aClass;
			}
		});
		if(manager[0] == null)
		{
			return;
		}
		new ConstructorReferencesSearchHelper(manager[0]).processConstructorReferences(consumer, method, aClass, p.getScope(),
				p.isIgnoreAccessScope(), true, p.getOptimizer());
	}
}
