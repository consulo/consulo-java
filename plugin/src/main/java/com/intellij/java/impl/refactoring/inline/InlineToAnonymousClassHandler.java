/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.java.impl.refactoring.inline;

import com.intellij.java.impl.refactoring.util.RefactoringUtil;
import com.intellij.java.indexing.search.searches.ClassInheritorsSearch;
import com.intellij.java.indexing.search.searches.FunctionalExpressionSearch;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.progress.ProgressManager;
import consulo.application.util.function.Processor;
import consulo.codeEditor.Editor;
import consulo.java.language.module.util.JavaClassNames;
import consulo.language.editor.TargetElementUtil;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.pattern.ElementPattern;
import consulo.language.pattern.PlatformPatterns;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.ref.Ref;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;

/**
 * @author yole
 */
@ExtensionImpl
public class InlineToAnonymousClassHandler extends JavaInlineActionHandler
{
	static final ElementPattern ourCatchClausePattern = PlatformPatterns.psiElement(PsiTypeElement.class).withParent(PlatformPatterns.psiElement(PsiParameter.class).withParent(PlatformPatterns
			.psiElement(PsiCatchSection.class)));
	static final ElementPattern ourThrowsClausePattern = PlatformPatterns.psiElement().withParent(PlatformPatterns.psiElement(PsiReferenceList.class).withFirstChild(PlatformPatterns.psiElement()
			.withText(PsiKeyword.THROWS)));

	@Override
	public boolean isEnabledOnElement(PsiElement element)
	{
		return element instanceof PsiMethod || element instanceof PsiClass;
	}

	@Override
	@RequiredReadAction
	public boolean canInlineElement(final PsiElement element)
	{
		if (element.getLanguage() != JavaLanguage.INSTANCE)
		{
			return false;
		}
		if (element instanceof PsiMethod method && method.isConstructor() && !InlineMethodHandler.isChainingConstructor(method)) {
			final PsiClass containingClass = method.getContainingClass();
			return containingClass != null && findClassInheritors(containingClass);
		}
		return element instanceof PsiClass psiClass && !(element instanceof PsiAnonymousClass) && findClassInheritors(psiClass);
	}

	private static boolean findClassInheritors(final PsiClass element)
	{
		final Collection<PsiElement> inheritors = new ArrayList<>();
		if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(
			() -> element.getApplication().runReadAction(() -> {
				final PsiClass inheritor = ClassInheritorsSearch.search(element).findFirst();
				if (inheritor != null)
				{
					inheritors.add(inheritor);
				}
				else
				{
					final PsiFunctionalExpression functionalExpression = FunctionalExpressionSearch.search(element).findFirst();
					if (functionalExpression != null)
					{
						inheritors.add(functionalExpression);
					}
				}
			}),
			"Searching for class \"" + element.getQualifiedName() + "\" inheritors ...",
			true,
			element.getProject()
		))
		{
			return false;
		}
		return inheritors.isEmpty();
	}

	@Override
	@RequiredReadAction
	public boolean canInlineElementInEditor(PsiElement element, Editor editor)
	{
		if (canInlineElement(element))
		{
			PsiReference reference = editor != null ? TargetElementUtil.findReference(editor, editor.getCaretModel().getOffset()) : null;
			if (!InlineMethodHandler.isThisReference(reference))
			{
				if (element instanceof PsiMethod && reference != null)
				{
					final PsiElement referenceElement = reference.getElement();
					return referenceElement != null && !PsiTreeUtil.isAncestor(((PsiMethod) element).getContainingClass(), referenceElement, false);
				}
				return true;
			}
		}
		return false;
	}

	@Override
	public void inlineElement(final Project project, final Editor editor, final PsiElement psiElement)
	{
		final PsiClass psiClass = psiElement instanceof PsiMethod method ? method.getContainingClass() : (PsiClass) psiElement;
		PsiCall callToInline = findCallToInline(editor);

		final PsiClassType superType = InlineToAnonymousClassProcessor.getSuperType(psiClass);
		LocalizeValue title = RefactoringLocalize.inlineToAnonymousRefactoring();
		if (superType == null)
		{
			CommonRefactoringUtil.showErrorHint(project, editor, "java.lang.Object is not found", title.get(), null);
			return;
		}

		final Ref<String> errorMessage = new Ref<>();
		if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(
			() -> project.getApplication().runReadAction(() -> errorMessage.set(getCannotInlineMessage(psiClass))),
			"Check if inline is possible...",
			true,
			project
		))
		{
			return;
		}
		if (errorMessage.get() != null)
		{
			CommonRefactoringUtil.showErrorHint(project, editor, errorMessage.get(), title.get(), null);
			return;
		}

		new InlineToAnonymousClassDialog(project, psiClass, callToInline, canBeInvokedOnReference(callToInline, superType)).show();
	}

	public static boolean canBeInvokedOnReference(PsiCall callToInline, PsiType superType)
	{
		if (callToInline == null)
		{
			return false;
		}
		final PsiElement parent = callToInline.getParent();
		if (parent instanceof PsiExpressionStatement || parent instanceof PsiSynchronizedStatement || parent instanceof PsiReferenceExpression)
		{
			return true;
		}
		else if (parent instanceof PsiExpressionList)
		{
			final PsiMethodCallExpression methodCallExpression = PsiTreeUtil.getParentOfType(parent, PsiMethodCallExpression.class);
			if (methodCallExpression != null)
			{
				int paramIdx = ArrayUtil.find(methodCallExpression.getArgumentList().getExpressions(), callToInline);
				if (paramIdx != -1)
				{
					final JavaResolveResult resolveResult = methodCallExpression.resolveMethodGenerics();
					final PsiElement resolvedMethod = resolveResult.getElement();
					if (resolvedMethod instanceof PsiMethod method)
					{
						PsiType paramType;
						final PsiParameter[] parameters = method.getParameterList().getParameters();
						if (paramIdx >= parameters.length)
						{
							final PsiParameter varargParameter = parameters[parameters.length - 1];
							paramType = varargParameter.getType();
						}
						else
						{
							paramType = parameters[paramIdx].getType();
						}
						if (paramType instanceof PsiEllipsisType ellipsisType)
						{
							paramType = ellipsisType.getComponentType();
						}
						paramType = resolveResult.getSubstitutor().substitute(paramType);

						final PsiJavaCodeReferenceElement classReference = ((PsiNewExpression) callToInline).getClassOrAnonymousClassReference();
						if (classReference != null)
						{
							superType = classReference.advancedResolve(false).getSubstitutor().substitute(superType);
							if (TypeConversionUtil.isAssignable(paramType, superType))
							{
								return true;
							}
						}
					}
				}
			}
		}
		return false;
	}


	@Nullable
	@RequiredReadAction
	public static PsiCall findCallToInline(final Editor editor)
	{
		PsiCall callToInline = null;
		PsiReference reference = editor != null ? TargetElementUtil.findReference(editor) : null;
		if (reference != null)
		{
			final PsiElement element = reference.getElement();
			if (element instanceof PsiJavaCodeReferenceElement javaCodeReferenceElement)
			{
				callToInline = RefactoringUtil.getEnclosingConstructorCall(javaCodeReferenceElement);
			}
		}
		return callToInline;
	}

	@Nullable
	@RequiredReadAction
	public static String getCannotInlineMessage(final PsiClass psiClass)
	{
		if (psiClass instanceof PsiTypeParameter)
		{
			return "Type parameters cannot be inlined";
		}
		if (psiClass.isAnnotationType())
		{
			return "Annotation types cannot be inlined";
		}
		if (psiClass.isInterface())
		{
			return "Interfaces cannot be inlined";
		}
		if (psiClass.isEnum())
		{
			return "Enums cannot be inlined";
		}
		if (psiClass.hasModifierProperty(PsiModifier.ABSTRACT))
		{
			return RefactoringLocalize.inlineToAnonymousNoAbstract().get();
		}
		if (!psiClass.getManager().isInProject(psiClass))
		{
			return "Library classes cannot be inlined";
		}

		PsiClassType[] classTypes = psiClass.getExtendsListTypes();
		for (PsiClassType classType : classTypes)
		{
			PsiClass superClass = classType.resolve();
			if (superClass == null)
			{
				return "Class cannot be inlined because its superclass cannot be resolved";
			}
		}

		final PsiClassType[] interfaces = psiClass.getImplementsListTypes();
		if (interfaces.length > 1)
		{
			return RefactoringLocalize.inlineToAnonymousNoMultipleInterfaces().get();
		}
		if (interfaces.length == 1)
		{
			if (interfaces[0].resolve() == null)
			{
				return "Class cannot be inlined because an interface implemented by it cannot be resolved";
			}
			final PsiClass superClass = psiClass.getSuperClass();
			if (superClass != null && !JavaClassNames.JAVA_LANG_OBJECT.equals(superClass.getQualifiedName()))
			{
				PsiClassType interfaceType = interfaces[0];
				if (!isRedundantImplements(superClass, interfaceType))
				{
					return RefactoringLocalize.inlineToAnonymousNoSuperclassAndInterface().get();
				}
			}
		}

		final PsiMethod[] methods = psiClass.getMethods();
		for (PsiMethod method : methods)
		{
			if (method.isConstructor())
			{
				if (PsiUtil.findReturnStatements(method).length > 0)
				{
					return "Class cannot be inlined because its constructor contains 'return' statements";
				}
			}
			else if (method.findSuperMethods().length == 0)
			{
				if (!ReferencesSearch.search(method).forEach(new AllowedUsagesProcessor(psiClass)))
				{
					return "Class cannot be inlined because it has usages of methods not inherited from its superclass or interface";
				}
			}
			if (method.hasModifierProperty(PsiModifier.STATIC))
			{
				return "Class cannot be inlined because it has static methods";
			}
		}

		final PsiClass[] innerClasses = psiClass.getInnerClasses();
		for (PsiClass innerClass : innerClasses)
		{
			PsiModifierList classModifiers = innerClass.getModifierList();
			if (classModifiers.hasModifierProperty(PsiModifier.STATIC))
			{
				return "Class cannot be inlined because it has static inner classes";
			}
			if (!ReferencesSearch.search(innerClass).forEach(new AllowedUsagesProcessor(psiClass)))
			{
				return "Class cannot be inlined because it has usages of its inner classes";
			}
		}

		final PsiField[] fields = psiClass.getFields();
		for (PsiField field : fields)
		{
			final PsiModifierList fieldModifiers = field.getModifierList();
			if (fieldModifiers != null && fieldModifiers.hasModifierProperty(PsiModifier.STATIC))
			{
				if (!fieldModifiers.hasModifierProperty(PsiModifier.FINAL))
				{
					return "Class cannot be inlined because it has static non-final fields";
				}
				Object initValue = null;
				final PsiExpression initializer = field.getInitializer();
				if (initializer != null)
				{
					initValue = JavaPsiFacade.getInstance(psiClass.getProject()).getConstantEvaluationHelper().computeConstantExpression(initializer);
				}
				if (initValue == null)
				{
					return "Class cannot be inlined because it has static fields with non-constant initializers";
				}
			}
			if (!ReferencesSearch.search(field).forEach(new AllowedUsagesProcessor(psiClass)))
			{
				return "Class cannot be inlined because it has usages of fields not inherited from its superclass";
			}
		}

		final PsiClassInitializer[] initializers = psiClass.getInitializers();
		for (PsiClassInitializer initializer : initializers)
		{
			final PsiModifierList modifiers = initializer.getModifierList();
			if (modifiers != null && modifiers.hasModifierProperty(PsiModifier.STATIC))
			{
				return "Class cannot be inlined because it has static initializers";
			}
		}

		return getCannotInlineDueToUsagesMessage(psiClass);
	}

	static boolean isRedundantImplements(final PsiClass superClass, final PsiClassType interfaceType)
	{
		boolean redundantImplements = false;
		PsiClassType[] superClassInterfaces = superClass.getImplementsListTypes();
		for (PsiClassType superClassInterface : superClassInterfaces)
		{
			if (superClassInterface.equals(interfaceType))
			{
				redundantImplements = true;
				break;
			}
		}
		return redundantImplements;
	}

	@RequiredReadAction
	@Nullable
	private static String getCannotInlineDueToUsagesMessage(final PsiClass aClass)
	{
		boolean hasUsages = false;
		for (PsiReference reference : ReferencesSearch.search(aClass))
		{
			final PsiElement element = reference.getElement();
			if (element == null)
			{
				continue;
			}
			if (!PsiTreeUtil.isAncestor(aClass, element, false))
			{
				hasUsages = true;
			}
			final PsiElement parentElement = element.getParent();
			if (parentElement != null)
			{
				final PsiElement grandPa = parentElement.getParent();
				if (grandPa instanceof PsiClassObjectAccessExpression)
				{
					return "Class cannot be inlined because it has usages of its class literal";
				}
				if (ourCatchClausePattern.accepts(parentElement))
				{
					return "Class cannot be inlined because it is used in a 'catch' clause";
				}
			}
			if (ourThrowsClausePattern.accepts(element))
			{
				return "Class cannot be inlined because it is used in a 'throws' clause";
			}
			if (parentElement instanceof PsiThisExpression)
			{
				return "Class cannot be inlined because it is used as a 'this' qualifier";
			}
			if (parentElement instanceof PsiNewExpression newExpression)
			{
				final PsiMethod[] constructors = aClass.getConstructors();
				if (constructors.length == 0)
				{
					PsiExpressionList newArgumentList = newExpression.getArgumentList();
					if (newArgumentList != null && newArgumentList.getExpressions().length > 0)
					{
						return "Class cannot be inlined because a call to its constructor is unresolved";
					}
				}
				else
				{
					final JavaResolveResult resolveResult = newExpression.resolveMethodGenerics();
					if (!resolveResult.isValidResult())
					{
						return "Class cannot be inlined because a call to its constructor is unresolved";
					}
				}
			}
		}
		if (!hasUsages)
		{
			return RefactoringLocalize.classIsNeverUsed().get();
		}
		return null;
	}

	private static class AllowedUsagesProcessor implements Processor<PsiReference>
	{
		private final PsiElement myPsiElement;

		public AllowedUsagesProcessor(final PsiElement psiElement)
		{
			myPsiElement = psiElement;
		}

		@Override
		@RequiredReadAction
		public boolean process(final PsiReference psiReference)
		{
			if (PsiTreeUtil.isAncestor(myPsiElement, psiReference.getElement(), false))
			{
				return true;
			}
			PsiElement element = psiReference.getElement();
			if (element instanceof PsiReferenceExpression referenceExpression)
			{
				PsiExpression qualifier = referenceExpression.getQualifierExpression();
				while (qualifier instanceof PsiParenthesizedExpression parenthesizedExpression)
				{
					qualifier = parenthesizedExpression.getExpression();
				}
				if (qualifier instanceof PsiNewExpression newExpr)
				{
					PsiJavaCodeReferenceElement classRef = newExpr.getClassReference();
					if (classRef != null && myPsiElement.equals(classRef.resolve()))
					{
						return true;
					}
				}
			}
			return false;
		}
	}
}