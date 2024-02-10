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
package com.intellij.java.impl.codeInspection.dataFlow;

import com.intellij.java.analysis.impl.codeInsight.daemon.impl.quickfix.SimplifyBooleanExpressionFix;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.*;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.fix.SurroundWithRequireNonNullFix;
import com.intellij.java.impl.codeInsight.daemon.impl.quickfix.DeleteSideEffectsAwareFix;
import com.intellij.java.impl.codeInsight.daemon.impl.quickfix.UnwrapSwitchLabelFix;
import com.intellij.java.impl.codeInspection.*;
import com.intellij.java.impl.codeInspection.dataFlow.fix.FindDfaProblemCauseFix;
import com.intellij.java.impl.codeInspection.nullable.NullableStuffInspection;
import com.intellij.java.impl.ig.fixes.IntroduceVariableFix;
import com.intellij.java.impl.refactoring.util.RefactoringUtil;
import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiPrecedenceUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.SideEffectChecker;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.IElementType;
import consulo.language.editor.inspection.InspectionToolState;
import consulo.language.editor.inspection.InspectionsBundle;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.LocalQuickFixOnPsiElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.SmartList;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static consulo.util.lang.xml.XmlStringUtil.wrapInHtml;
import static javax.swing.SwingConstants.TOP;

@ExtensionImpl
public class DataFlowInspection extends DataFlowInspectionBase
{
	private static final Logger LOG = Logger.getInstance(DataFlowInspection.class);

	@Nonnull
	@Override
	public InspectionToolState<? extends DataFlowInspectionStateBase> createStateProvider()
	{
		return new DataFlowInspectionState();
	}

	@Override
	protected LocalQuickFix[] createConditionalAssignmentFixes(boolean evaluatesToTrue, PsiAssignmentExpression assignment, final boolean onTheFly)
	{
		IElementType op = assignment.getOperationTokenType();
		boolean toRemove = op == JavaTokenType.ANDEQ && !evaluatesToTrue || op == JavaTokenType.OREQ && evaluatesToTrue;
		if(toRemove && !onTheFly)
		{
			return LocalQuickFix.EMPTY_ARRAY;
		}
		return new LocalQuickFix[]{toRemove ? new RemoveAssignmentFix() : createSimplifyToAssignmentFix()};
	}

	@Nonnull
	@Override
	public String getDisplayName()
	{
		return InspectionsBundle.message("inspection.data.flow.display.name");
	}

	@Override
	public boolean isEnabledByDefault()
	{
		return true;
	}

	@Override
	protected LocalQuickFix createReplaceWithTrivialLambdaFix(Object value)
	{
		return new ReplaceWithTrivialLambdaFix(value);
	}

	@Override
	protected LocalQuickFix createMutabilityViolationFix(PsiElement violation, boolean onTheFly)
	{
		return WrapWithMutableCollectionFix.createFix(violation, onTheFly);
	}

	@Nullable
	@Override
	protected LocalQuickFix createExplainFix(PsiExpression anchor, TrackingRunner.DfaProblemType problemType, DataFlowInspectionStateBase state)
	{
		return new FindDfaProblemCauseFix(state.TREAT_UNKNOWN_MEMBERS_AS_NULLABLE, state.IGNORE_ASSERT_STATEMENTS, anchor, problemType);
	}

	@Nullable
	@Override
	protected LocalQuickFix createUnwrapSwitchLabelFix()
	{
		return new UnwrapSwitchLabelFix();
	}

	@Override
	protected LocalQuickFix createIntroduceVariableFix()
	{
		return new IntroduceVariableFix(true);
	}

	@Override
	protected LocalQuickFixOnPsiElement createSimplifyBooleanFix(PsiElement element, boolean value)
	{
		if(!(element instanceof PsiExpression))
		{
			return null;
		}
		if(PsiTreeUtil.findChildOfType(element, PsiAssignmentExpression.class) != null)
		{
			return null;
		}

		final PsiExpression expression = (PsiExpression) element;
		while(element.getParent() instanceof PsiExpression)
		{
			element = element.getParent();
		}
		final SimplifyBooleanExpressionFix fix = new SimplifyBooleanExpressionFix(expression, value);
		// simplify intention already active
		if(!fix.isAvailable() || SimplifyBooleanExpressionFix.canBeSimplified((PsiExpression) element))
		{
			return null;
		}
		return fix;
	}

	private static boolean isVolatileFieldReference(PsiExpression qualifier)
	{
		PsiElement target = qualifier instanceof PsiReferenceExpression ? ((PsiReferenceExpression) qualifier).resolve() : null;
		return target instanceof PsiField && ((PsiField) target).hasModifierProperty(PsiModifier.VOLATILE);
	}

	@Nonnull
	@Override
	protected List<LocalQuickFix> createMethodReferenceNPEFixes(PsiMethodReferenceExpression methodRef, boolean onTheFly)
	{
		List<LocalQuickFix> fixes = new ArrayList<>();
		ContainerUtil.addIfNotNull(fixes, StreamFilterNotNullFix.makeFix(methodRef));
		if(onTheFly)
		{
			fixes.add(new ReplaceWithTernaryOperatorFix.ReplaceMethodRefWithTernaryOperatorFix());
		}
		return fixes;
	}

	@Override
	protected LocalQuickFix createRemoveAssignmentFix(PsiAssignmentExpression assignment)
	{
		if(assignment == null || assignment.getRExpression() == null || !(assignment.getParent() instanceof PsiExpressionStatement))
		{
			return null;
		}
		return new DeleteSideEffectsAwareFix((PsiStatement) assignment.getParent(), assignment.getRExpression(), true);
	}

	@Override
	@Nonnull
	protected List<LocalQuickFix> createCastFixes(PsiTypeCastExpression castExpression,
												  PsiType realType,
												  boolean onTheFly,
												  boolean alwaysFails)
	{
		List<LocalQuickFix> fixes = new ArrayList<>();
		PsiExpression operand = castExpression.getOperand();
		PsiTypeElement typeElement = castExpression.getCastType();
		if(typeElement != null && operand != null)
		{
			if(!alwaysFails && !SideEffectChecker.mayHaveSideEffects(operand))
			{
				String suffix = " instanceof " + typeElement.getText();
				fixes.add(new AddAssertStatementFix(ParenthesesUtils.getText(operand, PsiPrecedenceUtil.RELATIONAL_PRECEDENCE) + suffix));
				if(onTheFly && SurroundWithIfFix.isAvailable(operand))
				{
					fixes.add(new SurroundWithIfFix(operand, suffix));
				}
			}
			if(realType != null)
			{
				PsiType operandType = operand.getType();
				if(operandType != null)
				{
					PsiType type = typeElement.getType();
					PsiType[] types = {realType};
					if(realType instanceof PsiIntersectionType)
					{
						types = ((PsiIntersectionType) realType).getConjuncts();
					}
					for(PsiType psiType : types)
					{
						if(!psiType.isAssignableFrom(operandType))
						{
							psiType = DfaPsiUtil.tryGenerify(operand, psiType);
							fixes.add(new ReplaceTypeInCastFix(type, psiType));
						}
					}
				}
			}
		}
		return fixes;
	}

	@Override
	@Nonnull
	protected List<LocalQuickFix> createNPEFixes(PsiExpression qualifier, PsiExpression expression, boolean onTheFly, DataFlowInspectionStateBase state)
	{
		qualifier = PsiUtil.deparenthesizeExpression(qualifier);

		final List<LocalQuickFix> fixes = new SmartList<>();
		if(qualifier == null || expression == null)
		{
			return fixes;
		}

		try
		{
			ContainerUtil.addIfNotNull(fixes, StreamFilterNotNullFix.makeFix(qualifier));
			ContainerUtil.addIfNotNull(fixes, ReplaceComputeWithComputeIfPresentFix.makeFix(qualifier));
			if(isVolatileFieldReference(qualifier))
			{
				ContainerUtil.addIfNotNull(fixes, createIntroduceVariableFix());
			}
			else if(!ExpressionUtils.isNullLiteral(qualifier) && !SideEffectChecker.mayHaveSideEffects(qualifier))
			{
				String suffix = " != null";
				if(PsiUtil.getLanguageLevel(qualifier).isAtLeast(LanguageLevel.JDK_1_4) &&
						RefactoringUtil.getParentStatement(expression, false) != null)
				{
					String replacement = ParenthesesUtils.getText(qualifier, ParenthesesUtils.EQUALITY_PRECEDENCE) + suffix;
					fixes.add(new AddAssertStatementFix(replacement));
				}

				if(onTheFly && SurroundWithIfFix.isAvailable(qualifier))
				{
					fixes.add(new SurroundWithIfFix(qualifier, suffix));
				}

				if(onTheFly && ReplaceWithTernaryOperatorFix.isAvailable(qualifier, expression))
				{
					fixes.add(new ReplaceWithTernaryOperatorFix(qualifier));
				}
			}

			if(!ExpressionUtils.isNullLiteral(qualifier) && PsiUtil.isLanguageLevel7OrHigher(qualifier))
			{
				fixes.add(new SurroundWithRequireNonNullFix(qualifier));
			}

			if(onTheFly && !ExpressionUtils.isNullLiteral(qualifier))
			{
				ContainerUtil.addIfNotNull(fixes, createExplainFix(qualifier, new TrackingRunner.NullableDfaProblemType(), state));
			}

			ContainerUtil.addIfNotNull(fixes, DfaOptionalSupport.registerReplaceOptionalOfWithOfNullableFix(qualifier));
		}
		catch(IncorrectOperationException e)
		{
			LOG.error(e);
		}
		return fixes;
	}

	@Override
	protected LocalQuickFix createNavigateToNullParameterUsagesFix(PsiParameter parameter)
	{
		return new NullableStuffInspection.NavigateToNullLiteralArguments(parameter);
	}

	private static JCheckBox createCheckBoxWithHTML(String text, boolean selected, Consumer<? super JCheckBox> consumer)
	{
		JCheckBox box = new JCheckBox(wrapInHtml(text));
		box.setVerticalTextPosition(TOP);
		box.setSelected(selected);
		box.getModel().addItemListener(event -> consumer.accept(box));
		return box;
	}
}