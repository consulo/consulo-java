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
package com.intellij.java.impl.codeInsight.daemon.impl.quickfix;

import com.intellij.java.language.impl.codeInsight.BlockUtils;
import consulo.language.editor.intention.LowPriorityAction;
import consulo.language.editor.inspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.java.language.psi.*;
import consulo.codeEditor.Editor;
import consulo.project.Project;
import consulo.language.psi.*;
import consulo.language.psi.util.PsiTreeUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.SideEffectChecker;
import com.siyeh.ig.psiutils.StatementExtractor;
import consulo.java.analysis.impl.JavaQuickFixBundle;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.Nls;
import jakarta.annotation.Nonnull;

import java.util.List;
import java.util.Objects;

public class DeleteSideEffectsAwareFix extends LocalQuickFixAndIntentionActionOnPsiElement implements LowPriorityAction
{
	private final SmartPsiElementPointer<PsiStatement> myStatementPtr;
	private final SmartPsiElementPointer<PsiExpression> myExpressionPtr;
	private final String myMessage;
	private final boolean myIsAvailable;

	public DeleteSideEffectsAwareFix(@Nonnull PsiStatement statement, PsiExpression expression)
	{
		this(statement, expression, false);
	}

	public DeleteSideEffectsAwareFix(@Nonnull PsiStatement statement, PsiExpression expression, boolean alwaysAvailable)
	{
		super(statement);
		SmartPointerManager manager = SmartPointerManager.getInstance(statement.getProject());
		myStatementPtr = manager.createSmartPsiElementPointer(statement);
		myExpressionPtr = manager.createSmartPsiElementPointer(expression);
		List<PsiExpression> sideEffects = SideEffectChecker.extractSideEffectExpressions(expression);
		if(sideEffects.isEmpty())
		{
			myMessage = JavaQuickFixBundle.message("delete.element.fix.text");
		}
		else
		{
			PsiStatement[] statements = StatementExtractor.generateStatements(sideEffects, expression);
			if(statements.length == 1 && statements[0] instanceof PsiIfStatement)
			{
				myMessage = JavaQuickFixBundle.message("extract.side.effects.convert.to.if");
			}
			else
			{
				myMessage = JavaQuickFixBundle.message("extract.side.effects", statements.length);
			}
		}
		myIsAvailable = alwaysAvailable ||
				// "Remove unnecessary parentheses" action is already present which will do the same
				sideEffects.size() != 1 || !(statement instanceof PsiExpressionStatement) ||
				sideEffects.get(0) != PsiUtil.skipParenthesizedExprDown(expression);
	}

	@Nls
	@Nonnull
	@Override
	public String getText()
	{
		return myMessage;
	}

	@Nls
	@Nonnull
	@Override
	public String getFamilyName()
	{
		return JavaQuickFixBundle.message("extract.side.effects.family.name");
	}

	@Override
	public boolean isAvailable(@Nonnull Project project,
							   @Nonnull PsiFile file,
							   @Nonnull PsiElement startElement,
							   @Nonnull PsiElement endElement)
	{
		return myIsAvailable;
	}

	@Override
	public void invoke(@Nonnull Project project,
					   @Nonnull PsiFile file,
					   @Nullable Editor editor,
					   @Nonnull PsiElement startElement,
					   @Nonnull PsiElement endElement)
	{
		PsiStatement statement = myStatementPtr.getElement();
		if(statement == null)
		{
			return;
		}
		PsiExpression expression = myExpressionPtr.getElement();
		if(expression == null)
		{
			return;
		}
		List<PsiExpression> sideEffects = SideEffectChecker.extractSideEffectExpressions(expression);
		CommentTracker ct = new CommentTracker();
		sideEffects.forEach(ct::markUnchanged);
		PsiStatement[] statements = StatementExtractor.generateStatements(sideEffects, expression);
		if(statements.length > 0)
		{
			PsiStatement lastAdded = BlockUtils.addBefore(statement, statements);
			statement = Objects.requireNonNull(PsiTreeUtil.getNextSiblingOfType(lastAdded, PsiStatement.class));
		}
		PsiElement parent = statement.getParent();
		if(parent instanceof PsiStatement &&
				!(parent instanceof PsiIfStatement && ((PsiIfStatement) parent).getElseBranch() == statement) &&
				!(parent instanceof PsiForStatement && ((PsiForStatement) parent).getUpdate() == statement))
		{
			ct.replaceAndRestoreComments(statement, "{}");
		}
		else
		{
			ct.deleteAndRestoreComments(statement);
		}
	}
}
