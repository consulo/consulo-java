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
package com.intellij.psi.impl.source;

import java.lang.ref.Reference;
import java.util.Arrays;

import javax.annotation.Nonnull;

import com.intellij.java.language.psi.*;
import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.ItemPresentationProviders;
import consulo.logging.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiParameterStub;
import com.intellij.psi.impl.source.resolve.graphInference.FunctionalInterfaceParameterizationUtil;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.JavaSharedImplUtil;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.reference.SoftReference;
import com.intellij.util.IncorrectOperationException;

public class PsiParameterImpl extends JavaStubPsiElement<PsiParameterStub> implements PsiParameter
{
	private static final Logger LOG = Logger.getInstance(PsiParameterImpl.class);

	private volatile Reference<PsiType> myCachedType = null;

	public PsiParameterImpl(@Nonnull PsiParameterStub stub)
	{
		this(stub, JavaStubElementTypes.PARAMETER);
	}

	protected PsiParameterImpl(@Nonnull PsiParameterStub stub, @Nonnull IStubElementType type)
	{
		super(stub, type);
	}

	public PsiParameterImpl(@Nonnull ASTNode node)
	{
		super(node);
	}

	public static PsiType getLambdaParameterType(PsiParameter param)
	{
		final PsiElement paramParent = param.getParent();
		if(paramParent instanceof PsiParameterList)
		{
			final int parameterIndex = ((PsiParameterList) paramParent).getParameterIndex(param);
			if(parameterIndex > -1)
			{
				final PsiLambdaExpression lambdaExpression = PsiTreeUtil.getParentOfType(param,
						PsiLambdaExpression.class);
				if(lambdaExpression != null)
				{

					PsiType type = FunctionalInterfaceParameterizationUtil.getGroundTargetType(LambdaUtil
							.getFunctionalInterfaceType(lambdaExpression, true), lambdaExpression);
					if(type instanceof PsiIntersectionType)
					{
						final PsiType[] conjuncts = ((PsiIntersectionType) type).getConjuncts();
						for(PsiType conjunct : conjuncts)
						{
							final PsiType lambdaParameterFromType = getLambdaParameterFromType(parameterIndex,
									lambdaExpression, conjunct);
							if(lambdaParameterFromType != null)
							{
								return lambdaParameterFromType;
							}
						}
					}
					else
					{
						final PsiType lambdaParameterFromType = getLambdaParameterFromType(parameterIndex,
								lambdaExpression, type);
						if(lambdaParameterFromType != null)
						{
							return lambdaParameterFromType;
						}
					}
				}
			}
		}
		return new PsiLambdaParameterType(param);
	}

	private static PsiType getLambdaParameterFromType(int parameterIndex,
			PsiLambdaExpression lambdaExpression,
			PsiType conjunct)
	{
		final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(conjunct);
		if(resolveResult != null)
		{
			final PsiMethod method = LambdaUtil.getFunctionalInterfaceMethod(conjunct);
			if(method != null)
			{
				final PsiParameter[] parameters = method.getParameterList().getParameters();
				if(parameterIndex < parameters.length)
				{
					final PsiType psiType = LambdaUtil.getSubstitutor(method,
							resolveResult).substitute(parameters[parameterIndex].getType());
					if(!LambdaUtil.dependsOnTypeParams(psiType, conjunct, lambdaExpression))
					{
						return psiType;
					}
				}
			}
		}
		return null;
	}

	@Override
	public void subtreeChanged()
	{
		super.subtreeChanged();
		myCachedType = null;
	}

	@Override
	protected Object clone()
	{
		PsiParameterImpl clone = (PsiParameterImpl) super.clone();
		clone.myCachedType = null;

		return clone;
	}

	@Override
	@Nonnull
	public final String getName()
	{
		PsiParameterStub stub = getStub();
		if(stub != null)
		{
			return stub.getName();
		}

		return getNameIdentifier().getText();
	}

	@Override
	public final PsiElement setName(@Nonnull String name) throws IncorrectOperationException
	{
		PsiImplUtil.setName(getNameIdentifier(), name);
		return this;
	}

	@Override
	@Nonnull
	public final PsiIdentifier getNameIdentifier()
	{
		return PsiTreeUtil.getRequiredChildOfType(this, PsiIdentifier.class);
	}

	@Override
	@Nonnull
	public CompositeElement getNode()
	{
		return (CompositeElement) super.getNode();
	}

	@Override
	@Nonnull
	public PsiType getType()
	{
		PsiParameterStub stub = getStub();
		if(stub != null)
		{
			PsiType type = SoftReference.dereference(myCachedType);
			if(type == null)
			{
				type = JavaSharedImplUtil.createTypeFromStub(this, stub.getType());
				myCachedType = new SoftReference<>(type);
			}
			return type;
		}

		myCachedType = null;

		PsiTypeElement typeElement = getTypeElement();
		if(typeElement == null || isLambdaParameter() && typeElement.isInferredType())
		{
			assert isLambdaParameter() : this;
			return getLambdaParameterType(this);
		}
		else
		{
			return JavaSharedImplUtil.getType(typeElement, getNameIdentifier());
		}
	}

	private boolean isLambdaParameter()
	{
		final PsiElement parent = getParent();
		return parent instanceof PsiParameterList && parent.getParent() instanceof PsiLambdaExpression;
	}

	@Override
	public PsiTypeElement getTypeElement()
	{
		for(PsiElement child = getFirstChild(); child != null; child = child.getNextSibling())
		{
			if(child instanceof PsiTypeElement)
			{
				//noinspection unchecked
				return (PsiTypeElement) child;
			}
		}
		return null;
	}

	@Override
	@Nonnull
	public PsiModifierList getModifierList()
	{
		PsiModifierList modifierList = getStubOrPsiChild(JavaStubElementTypes.MODIFIER_LIST);
		assert modifierList != null : this;
		return modifierList;
	}

	@Override
	public boolean hasModifierProperty(@Nonnull String name)
	{
		return getModifierList().hasModifierProperty(name);
	}

	@Override
	public PsiExpression getInitializer()
	{
		return null;
	}

	@Override
	public boolean hasInitializer()
	{
		return false;
	}

	@Override
	public Object computeConstantValue()
	{
		return null;
	}

	@Override
	public void normalizeDeclaration() throws IncorrectOperationException
	{
		CheckUtil.checkWritable(this);
		JavaSharedImplUtil.normalizeBrackets(this);
	}

	@Override
	public void accept(@Nonnull PsiElementVisitor visitor)
	{
		if(visitor instanceof JavaElementVisitor)
		{
			((JavaElementVisitor) visitor).visitParameter(this);
		}
		else
		{
			visitor.visitElement(this);
		}
	}

	public String toString()
	{
		return "PsiParameter:" + getName();
	}

	@Override
	@Nonnull
	public PsiElement getDeclarationScope()
	{
		final PsiElement parent = getParent();
		if(parent == null)
		{
			return this;
		}

		if(parent instanceof PsiParameterList)
		{
			return parent.getParent();
		}
		if(parent instanceof PsiForeachStatement)
		{
			return parent;
		}
		if(parent instanceof PsiCatchSection)
		{
			return parent;
		}

		PsiElement[] children = parent.getChildren();
		//noinspection ConstantConditions
		if(children != null)
		{
			ext:
			for(int i = 0; i < children.length; i++)
			{
				if(children[i].equals(this))
				{
					for(int j = i + 1; j < children.length; j++)
					{
						if(children[j] instanceof PsiCodeBlock)
						{
							return children[j];
						}
					}
					break ext;
				}
			}
		}

		LOG.error("Code block not found among parameter' (" + this + ") parent' (" + parent + ") children: " + Arrays
				.asList(children));
		return null;
	}

	@Override
	public boolean isVarArgs()
	{
		final PsiParameterStub stub = getStub();
		if(stub != null)
		{
			return stub.isParameterTypeEllipsis();
		}

		myCachedType = null;
		final PsiTypeElement typeElement = getTypeElement();
		return typeElement != null && SourceTreeToPsiMap.psiToTreeNotNull(typeElement).findChildByType(JavaTokenType
				.ELLIPSIS) != null;
	}

	@Override
	public ItemPresentation getPresentation()
	{
		return ItemPresentationProviders.getItemPresentation(this);
	}

	@Override
	@Nonnull
	public SearchScope getUseScope()
	{
		final PsiElement declarationScope = getDeclarationScope();
		return new LocalSearchScope(declarationScope);
	}

	@Override
	public PsiElement getOriginalElement()
	{
		PsiElement parent = getParent();
		if(parent instanceof PsiParameterList)
		{
			PsiElement gParent = parent.getParent();
			if(gParent instanceof PsiMethod)
			{
				PsiElement originalMethod = gParent.getOriginalElement();
				if(originalMethod instanceof PsiMethod)
				{
					int index = ((PsiParameterList) parent).getParameterIndex(this);
					PsiParameter[] originalParameters = ((PsiMethod) originalMethod).getParameterList().getParameters();
					if(index < originalParameters.length)
					{
						return originalParameters[index];
					}
				}
			}
		}
		return this;
	}

}
