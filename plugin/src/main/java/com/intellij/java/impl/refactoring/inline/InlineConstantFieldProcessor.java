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
package com.intellij.java.impl.refactoring.inline;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import javax.annotation.Nonnull;

import com.intellij.java.language.psi.*;
import consulo.language.findUsage.DescriptiveNameUtil;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.lang.ref.Ref;
import com.intellij.psi.*;
import com.intellij.java.language.impl.psi.impl.source.javadoc.PsiDocMethodOrFieldRef;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.language.editor.refactoring.BaseRefactoringProcessor;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.language.editor.refactoring.rename.NonCodeUsageInfoFactory;
import com.intellij.java.impl.refactoring.util.ConflictsUtil;
import com.intellij.java.impl.refactoring.util.InlineUtil;
import consulo.language.editor.refactoring.util.NonCodeSearchDescriptionLocation;
import consulo.usage.NonCodeUsageInfo;
import consulo.language.editor.refactoring.ui.RefactoringUIUtil;
import consulo.language.editor.refactoring.util.TextOccurrencesUtil;
import consulo.usage.UsageInfo;
import consulo.usage.UsageInfoFactory;
import consulo.usage.UsageViewDescriptor;
import consulo.language.util.IncorrectOperationException;
import consulo.util.collection.MultiMap;

/**
 * @author ven
 */
public class InlineConstantFieldProcessor extends BaseRefactoringProcessor
{
	private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.inline" +
			".InlineConstantFieldProcessor");
	private PsiField myField;
	private final PsiReferenceExpression myRefExpr;
	private final boolean myInlineThisOnly;
	private final boolean mySearchInCommentsAndStrings;
	private final boolean mySearchForTextOccurrences;

	public InlineConstantFieldProcessor(PsiField field,
			Project project,
			PsiReferenceExpression ref,
			boolean isInlineThisOnly)
	{
		this(field, project, ref, isInlineThisOnly, false, false);
	}

	public InlineConstantFieldProcessor(PsiField field,
			Project project,
			PsiReferenceExpression ref,
			boolean isInlineThisOnly,
			boolean searchInCommentsAndStrings,
			boolean searchForTextOccurrences)
	{
		super(project);
		myField = field;
		myRefExpr = ref;
		myInlineThisOnly = isInlineThisOnly;
		mySearchInCommentsAndStrings = searchInCommentsAndStrings;
		mySearchForTextOccurrences = searchForTextOccurrences;
	}

	@Override
	@Nonnull
	protected UsageViewDescriptor createUsageViewDescriptor(@Nonnull UsageInfo[] usages)
	{
		return new InlineViewDescriptor(myField);
	}

	@Override
	protected boolean isPreviewUsages(@Nonnull UsageInfo[] usages)
	{
		if(super.isPreviewUsages(usages))
		{
			return true;
		}
		for(UsageInfo info : usages)
		{
			if(info instanceof NonCodeUsageInfo)
			{
				return true;
			}
		}
		return false;
	}

	private static class UsageFromJavaDoc extends UsageInfo
	{
		private UsageFromJavaDoc(@Nonnull PsiElement element)
		{
			super(element, true);
		}
	}

	@Override
	@Nonnull
	protected UsageInfo[] findUsages()
	{
		if(myInlineThisOnly)
		{
			return new UsageInfo[]{new UsageInfo(myRefExpr)};
		}

		List<UsageInfo> usages = new ArrayList<UsageInfo>();
		for(PsiReference ref : ReferencesSearch.search(myField, GlobalSearchScope.projectScope(myProject), false))
		{
			PsiElement element = ref.getElement();
			UsageInfo info = new UsageInfo(element);

			if(!(element instanceof PsiExpression) && PsiTreeUtil.getParentOfType(element,
					PsiImportStaticStatement.class) == null)
			{
				info = new UsageFromJavaDoc(element);
			}

			usages.add(info);
		}
		if(mySearchInCommentsAndStrings || mySearchForTextOccurrences)
		{
			UsageInfoFactory nonCodeUsageFactory = new NonCodeUsageInfoFactory(myField, myField.getName())
			{
				@Override
				public UsageInfo createUsageInfo(@Nonnull PsiElement usage, int startOffset, int endOffset)
				{
					if(PsiTreeUtil.isAncestor(myField, usage, false))
					{
						return null;
					}
					return super.createUsageInfo(usage, startOffset, endOffset);
				}
			};
			if(mySearchInCommentsAndStrings)
			{
				String stringToSearch = ElementDescriptionUtil.getElementDescription(myField,
						NonCodeSearchDescriptionLocation.STRINGS_AND_COMMENTS);
				TextOccurrencesUtil.addUsagesInStringsAndComments(myField, stringToSearch, usages,
						nonCodeUsageFactory);
			}

			if(mySearchForTextOccurrences)
			{
				String stringToSearch = ElementDescriptionUtil.getElementDescription(myField,
						NonCodeSearchDescriptionLocation.NON_JAVA);
				TextOccurrencesUtil.addTextOccurences(myField, stringToSearch, GlobalSearchScope.projectScope
						(myProject), usages, nonCodeUsageFactory);
			}
		}
		return usages.toArray(new UsageInfo[usages.size()]);
	}

	@Override
	protected void refreshElements(@Nonnull PsiElement[] elements)
	{
		LOG.assertTrue(elements.length == 1 && elements[0] instanceof PsiField);
		myField = (PsiField) elements[0];
	}

	@Override
	protected void performRefactoring(@Nonnull UsageInfo[] usages)
	{
		PsiExpression initializer = myField.getInitializer();
		LOG.assertTrue(initializer != null);

		initializer = normalize((PsiExpression) initializer.copy());
		for(UsageInfo info : usages)
		{
			if(info instanceof UsageFromJavaDoc)
			{
				continue;
			}
			if(info instanceof NonCodeUsageInfo)
			{
				continue;
			}
			final PsiElement element = info.getElement();
			if(element == null)
			{
				continue;
			}
			try
			{
				if(element instanceof PsiExpression)
				{
					inlineExpressionUsage((PsiExpression) element, initializer);
				}
				else
				{
					PsiImportStaticStatement importStaticStatement = PsiTreeUtil.getParentOfType(element,
							PsiImportStaticStatement.class);
					LOG.assertTrue(importStaticStatement != null, element.getText());
					importStaticStatement.delete();
				}
			}
			catch(IncorrectOperationException e)
			{
				LOG.error(e);
			}
		}

		if(!myInlineThisOnly)
		{
			try
			{
				myField.delete();
			}
			catch(IncorrectOperationException e)
			{
				LOG.error(e);
			}
		}
	}

	/*@Nullable
	@Override
	protected RefactoringEventData getBeforeData()
	{
		RefactoringEventData data = new RefactoringEventData();
		data.addElement(myField);
		return data;
	}

	@Nullable
	@Override
	protected String getRefactoringId()
	{
		return "refactoring.inline.field";
	}     */

	private void inlineExpressionUsage(PsiExpression expr,
			PsiExpression initializer1) throws IncorrectOperationException
	{
		if(myField.isWritable())
		{
			myField.normalizeDeclaration();
		}
		if(expr instanceof PsiReferenceExpression)
		{
			PsiExpression qExpression = ((PsiReferenceExpression) expr).getQualifierExpression();
			if(qExpression != null)
			{
				PsiReferenceExpression referenceExpression = null;
				if(initializer1 instanceof PsiReferenceExpression)
				{
					referenceExpression = (PsiReferenceExpression) initializer1;
				}
				else if(initializer1 instanceof PsiMethodCallExpression)
				{
					referenceExpression = ((PsiMethodCallExpression) initializer1).getMethodExpression();
				}
				if(referenceExpression != null &&
						referenceExpression.getQualifierExpression() == null &&
						!(referenceExpression.advancedResolve(false).getCurrentFileResolveScope() instanceof
								PsiImportStaticStatement))
				{
					referenceExpression.setQualifierExpression(qExpression);
				}
			}
		}

		InlineUtil.inlineVariable(myField, initializer1, (PsiJavaCodeReferenceElement) expr);
	}

	private static PsiExpression normalize(PsiExpression expression)
	{
		if(expression instanceof PsiArrayInitializerExpression)
		{
			PsiElementFactory factory = JavaPsiFacade.getInstance(expression.getProject()).getElementFactory();
			try
			{
				final PsiType type = expression.getType();
				if(type != null)
				{
					String typeString = type.getCanonicalText();
					PsiNewExpression result = (PsiNewExpression) factory.createExpressionFromText("new " + typeString
							+ "{}", expression);
					result.getArrayInitializer().replace(expression);
					return result;
				}
			}
			catch(IncorrectOperationException e)
			{
				LOG.error(e);
				return expression;
			}
		}

		return expression;
	}

	@Override
	protected String getCommandName()
	{
		return RefactoringBundle.message("inline.field.command", DescriptiveNameUtil.getDescriptiveName(myField));
	}

	@Override
	protected boolean preprocessUsages(@Nonnull Ref<UsageInfo[]> refUsages)
	{
		UsageInfo[] usagesIn = refUsages.get();
		MultiMap<PsiElement, String> conflicts = new MultiMap<PsiElement, String>();

		ReferencedElementsCollector collector = new ReferencedElementsCollector();
		PsiExpression initializer = myField.getInitializer();
		LOG.assertTrue(initializer != null);
		initializer.accept(collector);
		HashSet<PsiMember> referencedWithVisibility = collector.myReferencedMembers;

		PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(myField.getProject()).getResolveHelper();
		for(UsageInfo info : usagesIn)
		{
			PsiElement element = info.getElement();
			if(element instanceof PsiExpression && isAccessedForWriting((PsiExpression) element))
			{
				String message = RefactoringBundle.message("0.is.used.for.writing.in.1",
						RefactoringUIUtil.getDescription(myField, true), RefactoringUIUtil.getDescription
						(ConflictsUtil.getContainer(element), true));
				conflicts.putValue(element, message);
			}

			for(PsiMember member : referencedWithVisibility)
			{
				if(!resolveHelper.isAccessible(member, element, null))
				{
					String message = RefactoringBundle.message("0.will.not.be.accessible.from.1.after.inlining",
							RefactoringUIUtil.getDescription(member, true), RefactoringUIUtil.getDescription
							(ConflictsUtil.getContainer(element), true));
					conflicts.putValue(member, message);
				}
			}
		}

		if(!myInlineThisOnly)
		{
			for(UsageInfo info : usagesIn)
			{
				if(info instanceof UsageFromJavaDoc)
				{
					final PsiElement element = info.getElement();
					if(element instanceof PsiDocMethodOrFieldRef && !PsiTreeUtil.isAncestor(myField, element, false))
					{
						conflicts.putValue(element, "Inlined method is used in javadoc");
					}
				}
			}
		}

		return showConflicts(conflicts, usagesIn);
	}

	private static boolean isAccessedForWriting(PsiExpression expr)
	{
		while(expr.getParent() instanceof PsiArrayAccessExpression)
		{
			expr = (PsiExpression) expr.getParent();
		}

		return PsiUtil.isAccessedForWriting(expr);
	}

	@Override
	@Nonnull
	protected Collection<? extends PsiElement> getElementsToWrite(@Nonnull final UsageViewDescriptor descriptor)
	{
		if(myInlineThisOnly)
		{
			return Collections.singletonList(myRefExpr);
		}
		else
		{
			return super.getElementsToWrite(descriptor);
		}
	}
}
