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
package com.intellij.java.impl.refactoring.typeMigration;

import com.intellij.java.impl.refactoring.typeMigration.usageInfo.TypeMigrationUsageInfo;
import com.intellij.java.language.impl.psi.impl.PsiDiamondTypeUtil;
import com.intellij.java.language.impl.psi.impl.source.tree.ChildRole;
import com.intellij.java.language.impl.refactoring.util.RefactoringChangeUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import consulo.language.impl.ast.CompositeElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Map;

/**
 * @author anna
 */
public class TypeMigrationReplacementUtil
{
	public static final Logger LOG = Logger.getInstance(TypeMigrationReplacementUtil.class);

	private TypeMigrationReplacementUtil()
	{
	}

	public static PsiElement replaceExpression(PsiExpression expression, Project project, Object conversion, TypeEvaluator typeEvaluator)
	{
		if(conversion instanceof TypeConversionDescriptorBase)
		{
			try
			{
				return ((TypeConversionDescriptorBase) conversion).replace(expression, typeEvaluator);
			}
			catch(IncorrectOperationException e)
			{
				LOG.error(e);
			}
		}
		else if(conversion instanceof String)
		{
			String replacement = (String) conversion;
			try
			{
				return expression.replace(JavaPsiFacade.getInstance(project).getElementFactory().createExpressionFromText(replacement, expression));
			}
			catch(IncorrectOperationException e)
			{
				LOG.error(e);
			}
		}
		else if(expression instanceof PsiReferenceExpression)
		{
			PsiElement resolved = ((PsiReferenceExpression) expression).resolve();
			PsiMember replacer = ((PsiMember) conversion);
			String method = ((PsiMember) resolved).getName();
			String ref = expression.getText();
			String newref = ref.substring(0, ref.lastIndexOf(method)) + replacer.getName();

			if(conversion instanceof PsiMethod)
			{
				if(resolved instanceof PsiMethod)
				{
					try
					{
						return expression.replace(JavaPsiFacade.getInstance(project).getElementFactory().createExpressionFromText(newref, expression));
					}
					catch(IncorrectOperationException e)
					{
						LOG.error(e);
					}
				}
				else
				{
					try
					{
						return expression.replace(JavaPsiFacade.getInstance(project).getElementFactory().createExpressionFromText(newref + "()", expression));
					}
					catch(IncorrectOperationException e)
					{
						LOG.error(e);
					}
				}
			}
			else if(conversion instanceof PsiField)
			{
				if(resolved instanceof PsiField)
				{
					try
					{
						return expression.replace(JavaPsiFacade.getInstance(project).getElementFactory().createExpressionFromText(newref, expression));
					}
					catch(IncorrectOperationException e)
					{
						LOG.error(e);
					}
				}
				else
				{
					PsiElement parent = Util.getEssentialParent(expression);

					if(parent instanceof PsiMethodCallExpression)
					{
						try
						{
							return parent.replace(JavaPsiFacade.getInstance(project).getElementFactory().createExpressionFromText(newref, expression));
						}
						catch(IncorrectOperationException e)
						{
							LOG.error(e);
						}
					}
				}
			}
		}
		return expression;
	}

	static PsiType revalidateType(@Nonnull PsiType migrationType, @Nonnull Project project)
	{
		if(!migrationType.isValid())
		{
			migrationType = JavaPsiFacade.getElementFactory(project).createTypeByFQClassName(migrationType.getCanonicalText());
		}
		return migrationType;
	}

	static void migrateMemberOrVariableType(PsiElement element, Project project, PsiType migratedType)
	{
		try
		{
			migratedType = revalidateType(migratedType, project);
			PsiTypeElement typeElement = JavaPsiFacade.getInstance(project).getElementFactory().createTypeElement(migratedType);
			if(element instanceof PsiMethod)
			{
				PsiTypeElement returnTypeElement = ((PsiMethod) element).getReturnTypeElement();
				if(returnTypeElement != null)
				{
					PsiElement replaced = returnTypeElement.replace(typeElement);
					JavaCodeStyleManager.getInstance(project).shortenClassReferences(replaced);
				}
			}
			else if(element instanceof PsiVariable)
			{
				PsiTypeElement varTypeElement = ((PsiVariable) element).getTypeElement();
				if(varTypeElement != null)
				{
					PsiElement replaced = varTypeElement.replace(typeElement);
					JavaCodeStyleManager.getInstance(project).shortenClassReferences(replaced);
				}
			}
			else
			{
				LOG.error("Must not happen: " + element.getClass().getName());
			}
		}
		catch(IncorrectOperationException e)
		{
			LOG.error(e);
		}
	}

	static PsiNewExpression replaceNewExpressionType(Project project, PsiNewExpression expression, Map.Entry<TypeMigrationUsageInfo, PsiType> info)
	{
		PsiType changeType = info.getValue();
		if(changeType != null)
		{
			try
			{
				PsiJavaCodeReferenceElement classReference = expression.getClassOrAnonymousClassReference();
				PsiType componentType = changeType.getDeepComponentType();
				if(classReference != null)
				{
					PsiElement psiElement = changeType.equals(RefactoringChangeUtil.getTypeByExpression(expression)) ? classReference : replaceTypeWithClassReferenceOrKeyword(project,
							componentType, classReference);
					PsiNewExpression newExpression = PsiTreeUtil.getParentOfType(psiElement, PsiNewExpression.class);
					if(!tryToReplaceWithDiamond(newExpression, changeType))
					{
						return newExpression;
					}
				}
				else
				{
					PsiElement typeKeyword = getTypeKeyword(expression);
					if(typeKeyword != null)
					{
						replaceTypeWithClassReferenceOrKeyword(project, componentType, typeKeyword);
					}
				}
			}
			catch(IncorrectOperationException e)
			{
				LOG.error(e);
			}
		}
		return null;
	}

	static boolean tryToReplaceWithDiamond(PsiNewExpression newExpression, @Nullable PsiType changeType)
	{
		if(newExpression != null && PsiDiamondTypeUtil.canCollapseToDiamond(newExpression, newExpression, changeType))
		{
			PsiJavaCodeReferenceElement anonymousClassReference = newExpression.getClassOrAnonymousClassReference();
			if(anonymousClassReference != null)
			{
				PsiDiamondTypeUtil.replaceExplicitWithDiamond(anonymousClassReference.getParameterList());
			}
			return true;
		}
		return false;
	}

	private static PsiElement replaceTypeWithClassReferenceOrKeyword(Project project, PsiType componentType, PsiElement typePlace)
	{
		PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
		if(componentType instanceof PsiClassType)
		{
			return typePlace.replace(factory.createReferenceElementByType((PsiClassType) componentType));
		}
		else
		{
			return typePlace.replace(getTypeKeyword(((PsiNewExpression) factory.createExpressionFromText("new " + componentType.getPresentableText() + "[0]", typePlace))));
		}
	}

	private static PsiElement getTypeKeyword(PsiNewExpression expression)
	{
		return ((CompositeElement) expression).findChildByRoleAsPsiElement(ChildRole.TYPE_KEYWORD);
	}
}