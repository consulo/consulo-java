package com.intellij.java.analysis.impl.codeInspection.dataFlow.inference;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.Mutability;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ClassUtils;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * from kotlin
 */
public interface MethodReturnInferenceResult
{
	class Predefined implements MethodReturnInferenceResult
	{
		private final Nullability value;

		public Predefined(Nullability value)
		{
			this.value = value;
		}

		@Nonnull
		public Nullability getValue()
		{
			return value;
		}

		@Nonnull
		@Override
		public Nullability getNullability(PsiMethod method, Supplier<PsiCodeBlock> body)
		{
			return value;
		}

		@Override
		public int hashCode()
		{
			return value.ordinal();
		}

		@Override
		public boolean equals(Object o)
		{
			if(this == o)
			{
				return true;
			}
			if(o == null || getClass() != o.getClass())
			{
				return false;
			}
			Predefined that = (Predefined) o;
			return value == that.value;
		}
	}

	class FromDelegate implements MethodReturnInferenceResult
	{
		private final Nullability value;
		private final List<ExpressionRange> delegateCalls;

		public FromDelegate(Nullability value, List<ExpressionRange> delegateCalls)
		{
			this.value = value;
			this.delegateCalls = delegateCalls;
		}

		public Nullability getValue()
		{
			return value;
		}

		public List<ExpressionRange> getDelegateCalls()
		{
			return delegateCalls;
		}

		@Nonnull
		@Override
		public Nullability getNullability(PsiMethod method, Supplier<PsiCodeBlock> body)
		{
			if(value == Nullability.NULLABLE)
			{
				return Nullability.NULLABLE;
			}
			else if(ContainerUtil.all(delegateCalls, expressionRange -> isNotNullCall(expressionRange, body.get())))
			{
				return Nullability.NOT_NULL;
			}
			return Nullability.UNKNOWN;
		}

		@Nonnull
		@Override
		public Mutability getMutability(PsiMethod method, Supplier<PsiCodeBlock> body)
		{
			if(value == Nullability.NOT_NULL)
			{
				return Mutability.UNKNOWN;
			}

			return delegateCalls.stream().map(it -> getDelegateMutability(it, body.get())).reduce(Mutability::unite).orElse(Mutability.UNKNOWN);
		}

		private Mutability getDelegateMutability(ExpressionRange delegate, PsiCodeBlock body)
		{
			PsiMethodCallExpression call = (PsiMethodCallExpression) delegate.restoreExpression(body);
			assert call != null;
			PsiMethod target = call.resolveMethod();

			if(target == null)
			{
				return Mutability.UNKNOWN;
			}
			else if(ClassUtils.isImmutable(target.getReturnType(), false))
			{
				return Mutability.UNMODIFIABLE;
			}
			else
			{
				return Mutability.getMutability(target);
			}
		}

		private boolean isNotNullCall(ExpressionRange delegate, PsiCodeBlock body)
		{
			PsiMethodCallExpression call = (PsiMethodCallExpression) delegate.restoreExpression(body);
			assert call != null;
			if(call.getType() instanceof PsiPrimitiveType)
			{
				return true;
			}

			PsiMethod target = call.resolveMethod();
			return target != null && NullableNotNullManager.isNotNull(target);
		}

		@Override
		public boolean equals(Object o)
		{
			if(this == o)
			{
				return true;
			}
			if(o == null || getClass() != o.getClass())
			{
				return false;
			}
			FromDelegate that = (FromDelegate) o;
			return value == that.value &&
					Objects.equals(delegateCalls, that.delegateCalls);
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(value, delegateCalls);
		}
	}

	@Nonnull
	Nullability getNullability(PsiMethod method, Supplier<PsiCodeBlock> body);

	@Nonnull
	default Mutability getMutability(PsiMethod method, Supplier<PsiCodeBlock> body)
	{
		return Mutability.UNKNOWN;
	}
}
