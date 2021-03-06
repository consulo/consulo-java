/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.impl.search;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.AnnotatedElementsSearch;
import com.intellij.psi.search.searches.ClassesWithAnnotatedMembersSearch;
import com.intellij.psi.search.searches.ScopedQueryExecutor;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;

import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.Set;

/**
 * @author yole
 */
public class ClassesWithAnnotatedMembersSearcher extends QueryExecutorBase<PsiClass, ClassesWithAnnotatedMembersSearch.Parameters>
{
	@Override
	public void processQuery(@Nonnull ClassesWithAnnotatedMembersSearch.Parameters queryParameters, @Nonnull final Processor<? super PsiClass> consumer)
	{
		SearchScope scope = queryParameters.getScope();
		for(QueryExecutor executor : ClassesWithAnnotatedMembersSearch.EP_NAME.getExtensionList())
		{
			if(executor instanceof ScopedQueryExecutor)
			{
				scope = scope.intersectWith(GlobalSearchScope.notScope(((ScopedQueryExecutor) executor).getScope(queryParameters)));
			}
		}

		final Set<PsiClass> processed = new HashSet<>();
		AnnotatedElementsSearch.searchPsiMembers(queryParameters.getAnnotationClass(), scope).forEach(member ->
		{
			PsiClass psiClass;
			AccessToken token = ReadAction.start();
			try
			{
				psiClass = member instanceof PsiClass ? (PsiClass) member : member.getContainingClass();
			}
			finally
			{
				token.finish();
			}

			if(psiClass != null && processed.add(psiClass))
			{
				consumer.process(psiClass);
			}

			return true;
		});
	}
}
