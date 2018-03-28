/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.debugger.engine;

import javax.annotation.Nonnull;

import com.intellij.debugger.EvaluatingComputable;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.LambdaUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.Range;
import consulo.internal.com.sun.jdi.Location;
import consulo.internal.com.sun.jdi.Method;
import consulo.internal.com.sun.jdi.ObjectReference;

/**
 * @author Eugene Zhuravlev
 *         Date: 10/26/13
 */
public class BasicStepMethodFilter implements NamedMethodFilter
{
	private static final Logger LOG = Logger.getInstance(BasicStepMethodFilter.class);

	@Nonnull
	protected final JVMName myDeclaringClassName;
	@Nonnull
	private final String myTargetMethodName;
	@javax.annotation.Nullable
	protected final JVMName myTargetMethodSignature;
	private final Range<Integer> myCallingExpressionLines;

	public BasicStepMethodFilter(@Nonnull PsiMethod psiMethod, Range<Integer> callingExpressionLines)
	{
		this(JVMNameUtil.getJVMQualifiedName(psiMethod.getContainingClass()), JVMNameUtil.getJVMMethodName(psiMethod), JVMNameUtil.getJVMSignature(psiMethod), callingExpressionLines);
	}

	protected BasicStepMethodFilter(@Nonnull JVMName declaringClassName, @Nonnull String targetMethodName, @javax.annotation.Nullable JVMName targetMethodSignature, Range<Integer> callingExpressionLines)
	{
		myDeclaringClassName = declaringClassName;
		myTargetMethodName = targetMethodName;
		myTargetMethodSignature = targetMethodSignature;
		myCallingExpressionLines = callingExpressionLines;
	}

	@Override
	@Nonnull
	public String getMethodName()
	{
		return myTargetMethodName;
	}

	@Override
	public boolean locationMatches(DebugProcessImpl process, Location location) throws EvaluateException
	{
		return locationMatches(process, location, () -> null);
	}

	public boolean locationMatches(DebugProcessImpl process, Location location, @Nonnull EvaluatingComputable<ObjectReference> thisProvider) throws EvaluateException
	{
		Method method = location.method();
		String name = method.name();
		if(!myTargetMethodName.equals(name))
		{
			if(DebuggerUtilsEx.isLambdaName(name))
			{
				SourcePosition position = process.getPositionManager().getSourcePosition(location);
				return ReadAction.compute(() ->
				{
					PsiElement psiMethod = DebuggerUtilsEx.getContainingMethod(position);
					if(psiMethod instanceof PsiLambdaExpression)
					{
						PsiType type = ((PsiLambdaExpression) psiMethod).getFunctionalInterfaceType();
						PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(type);
						if(type != null && interfaceMethod != null && myTargetMethodName.equals(interfaceMethod.getName()))
						{
							try
							{
								return InheritanceUtil.isInheritor(type, myDeclaringClassName.getName(process).replace('$', '.'));
							}
							catch(EvaluateException e)
							{
								LOG.info(e);
							}
						}
					}
					return false;
				});
			}
			return false;
		}
		if(myTargetMethodSignature != null && !signatureMatches(method, myTargetMethodSignature.getName(process)))
		{
			return false;
		}
		if(method.isBridge())
		{ // skip bridge methods
			return false;
		}
		String declaringClassNameName = myDeclaringClassName.getName(process);
		boolean res = DebuggerUtilsEx.isAssignableFrom(declaringClassNameName, location.declaringType());
		if(!res && !method.isStatic())
		{
			ObjectReference thisObject = thisProvider.compute();
			if(thisObject != null)
			{
				res = DebuggerUtilsEx.isAssignableFrom(declaringClassNameName, thisObject.referenceType());
			}
		}
		return res;
	}

	private static boolean signatureMatches(Method method, final String expectedSignature) throws EvaluateException
	{
		if(expectedSignature.equals(method.signature()))
		{
			return true;
		}
		// check if there are any bridge methods that match
		for(Method candidate : method.declaringType().methodsByName(method.name()))
		{
			if(candidate != method && candidate.isBridge() && expectedSignature.equals(candidate.signature()))
			{
				return true;
			}
		}
		return false;
	}

	@javax.annotation.Nullable
	@Override
	public Range<Integer> getCallingExpressionLines()
	{
		return myCallingExpressionLines;
	}
}
