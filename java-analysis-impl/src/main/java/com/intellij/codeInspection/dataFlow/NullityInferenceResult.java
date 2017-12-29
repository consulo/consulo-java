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

import java.util.List;
import java.util.function.Supplier;

import org.jetbrains.annotations.NotNull;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiPrimitiveType;

/**
 * from kotlin
 */
interface NullityInferenceResult
{
	class Predefined implements NullityInferenceResult
	{
		private Nullness value;

		public Predefined(Nullness value)
		{
			this.value = value;
		}

		public Nullness getValue()
		{
			return value;
		}

		@NotNull
		@Override
		public Nullness getNullness(PsiMethod method, Supplier<PsiCodeBlock> body)
		{
			if(value == Nullness.NULLABLE && InferenceFromSourceUtil.suppressNullable(method))
			{
				return Nullness.UNKNOWN;
			}
			return value;
		}
	}

	class FromDelegate implements NullityInferenceResult
	{
		private List<ExpressionRange> delegateCalls;

		public FromDelegate(List<ExpressionRange> delegateCalls)
		{
			this.delegateCalls = delegateCalls;
		}

		public List<ExpressionRange> getDelegateCalls()
		{
			return delegateCalls;
		}

		@NotNull
		@Override
		public Nullness getNullness(PsiMethod method, Supplier<PsiCodeBlock> func)
		{
			PsiCodeBlock body = func.get();
			for(ExpressionRange delegateCall : delegateCalls)
			{
				if(!isNotNullCall(delegateCall, body))
				{
					return Nullness.UNKNOWN;
				}
			}
			return Nullness.NOT_NULL;
		}

		private boolean isNotNullCall(ExpressionRange delegate, PsiCodeBlock body)
		{
			PsiMethodCallExpression call = (PsiMethodCallExpression) delegate.restoreExpression(body);
			if(call.getType() instanceof PsiPrimitiveType)
			{
				return true;
			}
			PsiMethod target = call.resolveMethod();
			return target != null && NullableNotNullManager.isNotNull(target);
		}
	}

	@NotNull
	Nullness getNullness(PsiMethod method, Supplier<PsiCodeBlock> body);
}
