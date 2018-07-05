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
package com.intellij.psi.impl.source;

import java.lang.ref.WeakReference;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jetbrains.annotations.NonNls;
import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.PsiJavaParserFacadeImpl;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.JavaSharedImplUtil;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;

public class PsiTypeElementImpl extends CompositePsiElement implements PsiTypeElement
{
	@SuppressWarnings("UnusedDeclaration")
	public PsiTypeElementImpl()
	{
		this(JavaElementType.TYPE);
	}

	PsiTypeElementImpl(@Nonnull IElementType type)
	{
		super(type);
	}

	@Override
	public void accept(@Nonnull PsiElementVisitor visitor)
	{
		if(visitor instanceof JavaElementVisitor)
		{
			((JavaElementVisitor) visitor).visitTypeElement(this);
		}
		else
		{
			visitor.visitElement(this);
		}
	}

	@Override
	@Nonnull
	public PsiType getType()
	{
		return CachedValuesManager.getCachedValue(this, () -> CachedValueProvider.Result.create(calculateType(), PsiModificationTracker.MODIFICATION_COUNT));
	}

	@Nonnull
	private PsiType calculateType()
	{
		/*PsiType inferredType = PsiAugmentProvider.getInferredType(this);
		if(inferredType != null)
		{
			return inferredType;
		} */

		PsiType type = null;
		List<PsiAnnotation> annotations = new SmartList<>();

		PsiElement parent = getParent();
		for(PsiElement child = getFirstChild(); child != null; child = child.getNextSibling())
		{
			if(child instanceof PsiComment || child instanceof PsiWhiteSpace)
			{
				continue;
			}

			if(child instanceof PsiAnnotation)
			{
				annotations.add((PsiAnnotation) child);
			}
			else if(child instanceof PsiTypeElement)
			{
				assert type == null : this;
				if(child instanceof PsiDiamondTypeElementImpl)
				{
					type = new PsiDiamondTypeImpl(getManager(), this);
					break;
				}
				else
				{
					type = ((PsiTypeElement) child).getType();
				}
			}
			else if(PsiUtil.isJavaToken(child, ElementType.PRIMITIVE_TYPE_BIT_SET))
			{
				assert type == null : this;
				String text = child.getText();
				type = annotations.isEmpty() ? PsiJavaParserFacadeImpl.getPrimitiveType(text) : new PsiPrimitiveType(text, createProvider(annotations));
			}
			else if(PsiUtil.isJavaToken(child, JavaTokenType.VAR_KEYWORD))
			{
				assert type == null : this;
				type = inferVarType(parent);
			}
			else if(child instanceof PsiJavaCodeReferenceElement)
			{
				assert type == null : this;
				type = new PsiClassReferenceType(getReferenceComputable((PsiJavaCodeReferenceElement) child), null, createProvider(annotations));
			}
			else if(PsiUtil.isJavaToken(child, JavaTokenType.LBRACKET))
			{
				assert type != null : this;
				type = new PsiArrayType(type, createProvider(annotations));
			}
			else if(PsiUtil.isJavaToken(child, JavaTokenType.ELLIPSIS))
			{
				assert type != null : this;
				type = new PsiEllipsisType(type, createProvider(annotations));
			}

			if(PsiUtil.isJavaToken(child, JavaTokenType.QUEST) || child instanceof ASTNode && ((ASTNode) child).getElementType() == JavaElementType.DUMMY_ELEMENT && "any".equals(child.getText()))
			{
				assert type == null : this;
				PsiElement boundKind = PsiTreeUtil.skipSiblingsForward(child, PsiComment.class, PsiWhiteSpace.class);
				PsiElement boundType = PsiTreeUtil.skipSiblingsForward(boundKind, PsiComment.class, PsiWhiteSpace.class);
				if(PsiUtil.isJavaToken(boundKind, JavaTokenType.EXTENDS_KEYWORD) && boundType instanceof PsiTypeElement)
				{
					type = PsiWildcardType.createExtends(getManager(), ((PsiTypeElement) boundType).getType());
				}
				else if(PsiUtil.isJavaToken(boundKind, JavaTokenType.SUPER_KEYWORD) && boundType instanceof PsiTypeElement)
				{
					type = PsiWildcardType.createSuper(getManager(), ((PsiTypeElement) boundType).getType());
				}
				else
				{
					type = PsiWildcardType.createUnbounded(getManager());
				}
				type = type.annotate(createProvider(annotations));
				break;
			}

			if(PsiUtil.isJavaToken(child, JavaTokenType.AND))
			{
				List<PsiType> types = collectTypes();
				assert !types.isEmpty() : this;
				type = PsiIntersectionType.createIntersection(false, types.toArray(PsiType.createArray(types.size())));
				break;
			}

			if(PsiUtil.isJavaToken(child, JavaTokenType.OR))
			{
				List<PsiType> types = collectTypes();
				assert !types.isEmpty() : this;
				type = PsiDisjunctionType.createDisjunction(types, getManager());
				break;
			}
		}

		if(type == null)
		{
			return PsiType.NULL;
		}

		if(parent instanceof PsiModifierListOwner)
		{
			type = JavaSharedImplUtil.applyAnnotations(type, ((PsiModifierListOwner) parent).getModifierList());
		}

		return type;
	}

	private PsiType inferVarType(PsiElement parent)
	{
		if(parent instanceof PsiParameter)
		{
			PsiElement declarationScope = ((PsiParameter) parent).getDeclarationScope();
			if(declarationScope instanceof PsiForeachStatement)
			{
				PsiExpression iteratedValue = ((PsiForeachStatement) declarationScope).getIteratedValue();
				if(iteratedValue != null)
				{
					return JavaGenericsUtil.getCollectionItemType(iteratedValue);
				}
				return null;
			}

			if(declarationScope instanceof PsiLambdaExpression)
			{
				return ((PsiParameter) parent).getType();
			}
		}
		else
		{
			for(PsiElement e = this; e != null; e = e.getNextSibling())
			{
				if(e instanceof PsiExpression)
				{
					if(!(e instanceof PsiArrayInitializerExpression) && !isSelfReferenced((PsiExpression) e, parent))
					{
						PsiExpression expression = (PsiExpression) e;
						PsiType type = JavaVarTypeUtil.ourVarGuard.doPreventingRecursion(expression, true, () -> expression.getType());
						return type == null ? null : JavaVarTypeUtil.getUpwardProjection(type);
					}
					return null;
				}
			}
		}
		return null;
	}

	private static boolean isSelfReferenced(@Nonnull PsiExpression initializer, PsiElement parent)
	{
		class SelfReferenceVisitor extends JavaRecursiveElementVisitor
		{
			private boolean referenced;

			@Override
			public void visitElement(PsiElement element)
			{
				if(referenced)
				{
					return;
				}
				super.visitElement(element);
			}

			@Override
			public void visitReferenceExpression(PsiReferenceExpression expression)
			{
				super.visitReferenceExpression(expression);
				if(expression.getParent() instanceof PsiMethodCallExpression)
				{
					return;
				}
				if(expression.resolve() == parent)
				{
					referenced = true;
				}
			}
		}

		SelfReferenceVisitor visitor = new SelfReferenceVisitor();
		initializer.accept(visitor);
		return visitor.referenced;
	}

	@Override
	public boolean isInferredType()
	{
		PsiElement firstChild = getFirstChild();
		return PsiUtil.isJavaToken(firstChild, JavaTokenType.VAR_KEYWORD);
	}

	@Nonnull
	private Computable<PsiJavaCodeReferenceElement> getReferenceComputable(@Nonnull PsiJavaCodeReferenceElement ref)
	{
		final PsiElement parent = getParent();
		if(parent instanceof PsiMethod || parent instanceof PsiVariable)
		{
			return computeFromTypeOwner(parent, new WeakReference<>(ref));
		}

		return new Computable.PredefinedValueComputable<>(ref);
	}

	@Nonnull
	private static Computable<PsiJavaCodeReferenceElement> computeFromTypeOwner(final PsiElement parent, @Nonnull WeakReference<PsiJavaCodeReferenceElement> ref)
	{
		return new Computable<PsiJavaCodeReferenceElement>()
		{
			volatile WeakReference<PsiJavaCodeReferenceElement> myCache = ref;

			@Override
			public PsiJavaCodeReferenceElement compute()
			{
				PsiJavaCodeReferenceElement result = myCache.get();
				if(result == null)
				{
					myCache = new WeakReference<>(result = getParentTypeElement().getReferenceElement());
				}
				return result;
			}

			@Nonnull
			private PsiTypeElementImpl getParentTypeElement()
			{
				PsiTypeElement typeElement = parent instanceof PsiMethod ? ((PsiMethod) parent).getReturnTypeElement() : ((PsiVariable) parent).getTypeElement();
				return (PsiTypeElementImpl) ObjectUtils.assertNotNull(typeElement);
			}
		};
	}

	@Nonnull
	private static TypeAnnotationProvider createProvider(@Nonnull List<PsiAnnotation> annotations)
	{
		return TypeAnnotationProvider.Static.create(ContainerUtil.copyAndClear(annotations, PsiAnnotation.ARRAY_FACTORY, true));
	}

	@Nonnull
	private List<PsiType> collectTypes()
	{
		List<PsiTypeElement> typeElements = PsiTreeUtil.getChildrenOfTypeAsList(this, PsiTypeElement.class);
		return ContainerUtil.map(typeElements, typeElement -> typeElement.getType());
	}

	@Override
	public PsiJavaCodeReferenceElement getInnermostComponentReferenceElement()
	{
		TreeElement firstChildNode = getFirstChildNode();
		if(firstChildNode == null)
		{
			return null;
		}
		if(firstChildNode.getElementType() == JavaElementType.TYPE)
		{
			return SourceTreeToPsiMap.<PsiTypeElement>treeToPsiNotNull(firstChildNode).getInnermostComponentReferenceElement();
		}
		return getReferenceElement();
	}

	@Nullable
	private PsiJavaCodeReferenceElement getReferenceElement()
	{
		ASTNode ref = findChildByType(JavaElementType.JAVA_CODE_REFERENCE);
		if(ref == null)
		{
			return null;
		}
		return (PsiJavaCodeReferenceElement) SourceTreeToPsiMap.treeElementToPsi(ref);
	}

	@Override
	public boolean processDeclarations(@Nonnull PsiScopeProcessor processor, @Nonnull ResolveState state, PsiElement lastParent, @Nonnull PsiElement place)
	{
		processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, this);
		return true;
	}

	@Override
	@Nonnull
	public PsiAnnotation[] getAnnotations()
	{
		PsiAnnotation[] annotations = PsiTreeUtil.getChildrenOfType(this, PsiAnnotation.class);
		return annotations != null ? annotations : PsiAnnotation.EMPTY_ARRAY;
	}

	@Override
	@Nonnull
	public PsiAnnotation[] getApplicableAnnotations()
	{
		return getType().getAnnotations();
	}

	@Override
	public PsiAnnotation findAnnotation(@Nonnull @NonNls String qualifiedName)
	{
		return PsiImplUtil.findAnnotation(this, qualifiedName);
	}

	@Override
	@Nonnull
	public PsiAnnotation addAnnotation(@Nonnull @NonNls String qualifiedName)
	{
		throw new UnsupportedOperationException();//todo
	}

	@Override
	public PsiElement replace(@Nonnull PsiElement newElement) throws IncorrectOperationException
	{
		// neighbouring type annotations are logical part of this type element and should be dropped
		//if replacement is `var`, annotations should be left as they are not inferred from the right side of the assignment
		if(!(newElement instanceof PsiTypeElement) || !((PsiTypeElement) newElement).isInferredType())
		{
			PsiImplUtil.markTypeAnnotations(this);
		}
		PsiElement result = super.replace(newElement);
		if(result instanceof PsiTypeElement)
		{
			PsiImplUtil.deleteTypeAnnotations((PsiTypeElement) result);
		}
		return result;
	}

	@Override
	public String toString()
	{
		return "PsiTypeElement:" + getText();
	}
}