/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.testFramework.fixtures;

import java.util.Set;

import javax.annotation.Nonnull;

import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.refactoring.inline.InlineConstantFieldProcessor;
import com.intellij.refactoring.inline.InlineLocalHandler;
import com.intellij.refactoring.inline.InlineMethodProcessor;
import com.intellij.refactoring.inline.InlineParameterHandler;
import com.intellij.refactoring.util.InlineUtil;
import com.intellij.util.containers.ContainerUtil;
import consulo.codeInsight.TargetElementUtil;
import consulo.codeInsight.TargetElementUtilEx;


public class JavaCodeInsightTestUtil
{
	private static final Set<String> TARGET_FOR_INLINE_FLAGS = ContainerUtil.newHashSet(TargetElementUtilEx.ELEMENT_NAME_ACCEPTED, TargetElementUtilEx.REFERENCED_ELEMENT_ACCEPTED);

	private JavaCodeInsightTestUtil()
	{
	}

	public static void doInlineLocalTest(@Nonnull final CodeInsightTestFixture fixture, @Nonnull final String before, @Nonnull final String after)
	{
		fixture.configureByFile(before);
		new WriteCommandAction(fixture.getProject())
		{
			@Override
			protected void run(final Result result) throws Throwable
			{
				final Editor editor = fixture.getEditor();
				final PsiElement element = TargetElementUtil.findTargetElement(editor, TARGET_FOR_INLINE_FLAGS);
				assert element instanceof PsiLocalVariable : element;
				InlineLocalHandler.invoke(fixture.getProject(), editor, (PsiLocalVariable) element, null);
			}
		}.execute();
		fixture.checkResultByFile(after, false);
	}

	public static void doInlineParameterTest(@Nonnull final CodeInsightTestFixture fixture, @Nonnull final String before, @Nonnull final String after)
	{
		fixture.configureByFile(before);
		new WriteCommandAction(fixture.getProject())
		{
			@Override
			protected void run(final Result result) throws Throwable
			{
				final Editor editor = fixture.getEditor();
				final PsiElement element = TargetElementUtil.findTargetElement(editor, TARGET_FOR_INLINE_FLAGS);
				assert element instanceof PsiParameter : element;
				new InlineParameterHandler().inlineElement(getProject(), editor, element);
			}
		}.execute();
		fixture.checkResultByFile(after, false);
	}

	public static void doInlineMethodTest(@Nonnull final CodeInsightTestFixture fixture, @Nonnull final String before, @Nonnull final String after)
	{
		fixture.configureByFile(before);
		new WriteCommandAction(fixture.getProject())
		{
			@Override
			protected void run(final Result result) throws Throwable
			{
				final Editor editor = fixture.getEditor();
				final PsiElement element = TargetElementUtil.findTargetElement(editor, TARGET_FOR_INLINE_FLAGS);
				assert element instanceof PsiMethod : element;

				final PsiReference ref = fixture.getFile().findReferenceAt(editor.getCaretModel().getOffset());
				final PsiReferenceExpression refExpr = ref instanceof PsiReferenceExpression ? (PsiReferenceExpression) ref : null;

				final PsiMethod method = (PsiMethod) element;
				assert !(InlineMethodProcessor.checkBadReturns(method) && !InlineUtil.allUsagesAreTailCalls(method)) : "Bad returns found";
				new InlineMethodProcessor(getProject(), method, refExpr, editor, false).run();
			}
		}.execute();
		fixture.checkResultByFile(after, false);
	}

	public static void doInlineConstantTest(@Nonnull final CodeInsightTestFixture fixture, @Nonnull final String before, @Nonnull final String after)
	{
		fixture.configureByFile(before);
		new WriteCommandAction(fixture.getProject())
		{
			@Override
			protected void run(final Result result) throws Throwable
			{
				final Editor editor = fixture.getEditor();
				final PsiElement element = TargetElementUtil.findTargetElement(editor, TARGET_FOR_INLINE_FLAGS);
				assert element instanceof PsiField : element;

				final PsiReference ref = fixture.getFile().findReferenceAt(editor.getCaretModel().getOffset());
				final PsiReferenceExpression refExpr = ref instanceof PsiReferenceExpression ? (PsiReferenceExpression) ref : null;

				new InlineConstantFieldProcessor((PsiField) element, getProject(), refExpr, false).run();
			}
		}.execute();
		fixture.checkResultByFile(after, false);
	}
}