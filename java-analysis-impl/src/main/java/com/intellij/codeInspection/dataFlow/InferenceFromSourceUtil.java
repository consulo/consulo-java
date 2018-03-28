/*
 * Copyright 2013-2017 consulo.io
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
package com.intellij.codeInspection.dataFlow;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.impl.source.PsiMethodImpl;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;

/**
 * @author peter
 */
public class InferenceFromSourceUtil
{
	static boolean shouldInferFromSource(@Nonnull PsiMethodImpl method)
	{
		return CachedValuesManager.getCachedValue(method, () -> CachedValueProvider.Result.create(calcShouldInferFromSource(method), method, PsiModificationTracker
				.JAVA_STRUCTURE_MODIFICATION_COUNT));
	}

	private static boolean calcShouldInferFromSource(@Nonnull PsiMethod method)
	{
		if(isLibraryCode(method) || method.hasModifierProperty(PsiModifier.ABSTRACT) || PsiUtil.canBeOverriden(method))
		{
			return false;
		}

		if(method.hasModifierProperty(PsiModifier.STATIC))
		{
			return true;
		}

		return !isUnusedInAnonymousClass(method);
	}

	private static boolean isUnusedInAnonymousClass(@Nonnull PsiMethod method)
	{
		PsiClass containingClass = method.getContainingClass();
		if(!(containingClass instanceof PsiAnonymousClass))
		{
			return false;
		}

		if(containingClass.getParent() instanceof PsiNewExpression && containingClass.getParent().getParent() instanceof PsiVariable && !method.getHierarchicalMethodSignature().getSuperSignatures()
				.isEmpty())
		{
			// references outside anonymous class can still resolve to this method, see com.intellij.psi.scope.util.PsiScopesUtil.setupAndRunProcessor()
			return false;
		}

		return MethodReferencesSearch.search(method, new LocalSearchScope(containingClass), false).findFirst() == null;
	}

	private static boolean isLibraryCode(@Nonnull PsiMethod method)
	{
		if(method instanceof PsiCompiledElement)
		{
			return true;
		}
		VirtualFile virtualFile = PsiUtilCore.getVirtualFile(method);
		return virtualFile != null && FileIndexFacade.getInstance(method.getProject()).isInLibrarySource(virtualFile);
	}

	static boolean isReturnTypeCompatible(@Nullable PsiType returnType, @Nonnull MethodContract.ValueConstraint returnValue)
	{
		if(returnValue == MethodContract.ValueConstraint.ANY_VALUE || returnValue == MethodContract.ValueConstraint.THROW_EXCEPTION)
		{
			return true;
		}
		if(PsiType.VOID.equals(returnType))
		{
			return false;
		}

		if(PsiType.BOOLEAN.equals(returnType))
		{
			return returnValue == MethodContract.ValueConstraint.TRUE_VALUE || returnValue == MethodContract.ValueConstraint.FALSE_VALUE;
		}

		if(!(returnType instanceof PsiPrimitiveType))
		{
			return returnValue == MethodContract.ValueConstraint.NULL_VALUE || returnValue == MethodContract.ValueConstraint.NOT_NULL_VALUE;
		}

		return false;
	}

	static boolean suppressNullable(PsiMethod method)
	{
		if(method.getParameterList().getParametersCount() == 0)
		{
			return false;
		}

		for(StandardMethodContract contract : ControlFlowAnalyzer.getMethodContracts(method))
		{
			if(contract.returnValue == MethodContract.ValueConstraint.NULL_VALUE)
			{
				return true;
			}
		}
		return false;
	}
}
