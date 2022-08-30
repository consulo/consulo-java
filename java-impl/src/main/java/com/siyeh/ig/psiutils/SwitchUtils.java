/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.psiutils;

import com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import consulo.java.module.util.JavaClassNames;
import org.jetbrains.annotations.NonNls;
import javax.annotation.Nonnull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SwitchUtils
{

	private SwitchUtils()
	{
	}

	/**
	 * Calculates the number of branches in the specified switch statement.
	 * When a default case is present the count will be returned as a negative number,
	 * e.g. if a switch statement contains 4 labeled cases and a default case, it will return -5
	 *
	 * @param statement the statement to count the cases of.
	 * @return a negative number if a default case was encountered.
	 */
	public static int calculateBranchCount(@Nonnull PsiSwitchStatement statement)
	{
		// preserved for plugin compatibility
		return calculateBranchCount((PsiSwitchBlock) statement);
	}

	/**
	 * Calculates the number of branches in the specified switch block.
	 * When a default case is present the count will be returned as a negative number,
	 * e.g. if a switch block contains 4 labeled cases and a default case, it will return -5
	 *
	 * @param block the switch block to count the cases of.
	 * @return a negative number if a default case was encountered.
	 */
	public static int calculateBranchCount(@Nonnull PsiSwitchBlock block)
	{
		final PsiCodeBlock body = block.getBody();
		if(body == null)
		{
			return 0;
		}
		int branches = 0;
		boolean defaultFound = false;
		for(final PsiSwitchLabelStatementBase child : PsiTreeUtil.getChildrenOfTypeAsList(body, PsiSwitchLabelStatementBase.class))
		{
			if(child.isDefaultCase())
			{
				defaultFound = true;
			}
			else
			{
				branches++;
			}
		}
		return defaultFound ? -branches - 1 : branches;
	}

	@Nullable
	public static PsiExpression getSwitchExpression(PsiIfStatement statement, int minimumBranches)
	{
		final PsiExpression condition = statement.getCondition();
		final LanguageLevel languageLevel = PsiUtil.getLanguageLevel(statement);
		final PsiExpression possibleSwitchExpression = determinePossibleSwitchExpressions(condition, languageLevel);
		if(!canBeSwitchExpression(possibleSwitchExpression, languageLevel))
		{
			return null;
		}
		int branchCount = 0;
		while(true)
		{
			branchCount++;
			if(!canBeMadeIntoCase(statement.getCondition(), possibleSwitchExpression, languageLevel))
			{
				break;
			}
			final PsiStatement elseBranch = statement.getElseBranch();
			if(!(elseBranch instanceof PsiIfStatement))
			{
				if(elseBranch != null)
				{
					branchCount++;
				}
				if(branchCount < minimumBranches)
				{
					return null;
				}
				return possibleSwitchExpression;
			}
			statement = (PsiIfStatement) elseBranch;
		}
		return null;
	}

	private static boolean canBeMadeIntoCase(PsiExpression expression, PsiExpression switchExpression, LanguageLevel languageLevel)
	{
		expression = ParenthesesUtils.stripParentheses(expression);
		if(languageLevel.isAtLeast(LanguageLevel.JDK_1_7))
		{
			final PsiExpression stringSwitchExpression = determinePossibleStringSwitchExpression(expression);
			if(EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(switchExpression, stringSwitchExpression))
			{
				return true;
			}
		}
		if(!(expression instanceof PsiPolyadicExpression))
		{
			return false;
		}
		final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression) expression;
		final IElementType operation = polyadicExpression.getOperationTokenType();
		final PsiExpression[] operands = polyadicExpression.getOperands();
		if(operation.equals(JavaTokenType.OROR))
		{
			for(PsiExpression operand : operands)
			{
				if(!canBeMadeIntoCase(operand, switchExpression, languageLevel))
				{
					return false;
				}
			}
			return true;
		}
		else if(operation.equals(JavaTokenType.EQEQ) && operands.length == 2)
		{
			return (canBeCaseLabel(operands[0], languageLevel) && EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(switchExpression, operands[1])) ||
					(canBeCaseLabel(operands[1], languageLevel) && EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(switchExpression, operands[0]));
		}
		else
		{
			return false;
		}
	}

	private static boolean canBeSwitchExpression(PsiExpression expression, LanguageLevel languageLevel)
	{
		if(expression == null || SideEffectChecker.mayHaveSideEffects(expression))
		{
			return false;
		}
		final PsiType type = expression.getType();
		if(PsiType.CHAR.equals(type) || PsiType.BYTE.equals(type) || PsiType.SHORT.equals(type) || PsiType.INT.equals(type))
		{
			return true;
		}
		else if(type instanceof PsiClassType)
		{
			if(type.equalsToText(JavaClassNames.JAVA_LANG_CHARACTER) || type.equalsToText(JavaClassNames.JAVA_LANG_BYTE) ||
					type.equalsToText(JavaClassNames.JAVA_LANG_SHORT) || type.equalsToText(JavaClassNames.JAVA_LANG_INTEGER))
			{
				return true;
			}
			if(languageLevel.isAtLeast(LanguageLevel.JDK_1_5))
			{
				final PsiClassType classType = (PsiClassType) type;
				final PsiClass aClass = classType.resolve();
				if(aClass != null && aClass.isEnum())
				{
					return true;
				}
			}
			if(languageLevel.isAtLeast(LanguageLevel.JDK_1_7) && type.equalsToText(JavaClassNames.JAVA_LANG_STRING))
			{
				return true;
			}
		}
		return false;
	}

	private static PsiExpression determinePossibleSwitchExpressions(PsiExpression expression, LanguageLevel languageLevel)
	{
		expression = ParenthesesUtils.stripParentheses(expression);
		if(expression == null)
		{
			return null;
		}
		if(languageLevel.isAtLeast(LanguageLevel.JDK_1_7))
		{
			final PsiExpression jdk17Expression = determinePossibleStringSwitchExpression(expression);
			if(jdk17Expression != null)
			{
				return jdk17Expression;
			}
		}
		if(!(expression instanceof PsiPolyadicExpression))
		{
			return null;
		}
		final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression) expression;
		final IElementType operation = polyadicExpression.getOperationTokenType();
		final PsiExpression[] operands = polyadicExpression.getOperands();
		if(operation.equals(JavaTokenType.OROR) && operands.length > 0)
		{
			return determinePossibleSwitchExpressions(operands[0], languageLevel);
		}
		else if(operation.equals(JavaTokenType.EQEQ) && operands.length == 2)
		{
			final PsiExpression lhs = operands[0];
			final PsiExpression rhs = operands[1];
			if(canBeCaseLabel(lhs, languageLevel))
			{
				return rhs;
			}
			else if(canBeCaseLabel(rhs, languageLevel))
			{
				return lhs;
			}
		}
		return null;
	}

	private static PsiExpression determinePossibleStringSwitchExpression(PsiExpression expression)
	{
		if(!(expression instanceof PsiMethodCallExpression))
		{
			return null;
		}
		final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression) expression;
		final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
		@NonNls final String referenceName = methodExpression.getReferenceName();
		if(!"equals".equals(referenceName))
		{
			return null;
		}
		final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
		if(qualifierExpression == null)
		{
			return null;
		}
		final PsiType type = qualifierExpression.getType();
		if(type == null || !type.equalsToText(JavaClassNames.JAVA_LANG_STRING))
		{
			return null;
		}
		final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
		final PsiExpression[] arguments = argumentList.getExpressions();
		if(arguments.length != 1)
		{
			return null;
		}
		final PsiExpression argument = arguments[0];
		final PsiType argumentType = argument.getType();
		if(argumentType == null || !argumentType.equalsToText(JavaClassNames.JAVA_LANG_STRING))
		{
			return null;
		}
		if(PsiUtil.isConstantExpression(qualifierExpression))
		{
			return argument;
		}
		else if(PsiUtil.isConstantExpression(argument))
		{
			return qualifierExpression;
		}
		return null;
	}

	private static boolean canBeCaseLabel(PsiExpression expression, LanguageLevel languageLevel)
	{
		if(expression == null)
		{
			return false;
		}
		if(languageLevel.isAtLeast(LanguageLevel.JDK_1_5) && expression instanceof PsiReferenceExpression)
		{
			final PsiElement referent = ((PsiReference) expression).resolve();
			if(referent instanceof PsiEnumConstant)
			{
				return true;
			}
		}
		final PsiType type = expression.getType();
		return (PsiType.INT.equals(type) || PsiType.SHORT.equals(type) || PsiType.BYTE.equals(type) || PsiType.CHAR.equals(type)) &&
				PsiUtil.isConstantExpression(expression);
	}

	public static String findUniqueLabelName(PsiStatement statement, @NonNls String baseName)
	{
		final PsiElement ancestor = PsiTreeUtil.getParentOfType(statement, PsiMember.class);
		if(!checkForLabel(baseName, ancestor))
		{
			return baseName;
		}
		int val = 1;
		while(true)
		{
			final String name = baseName + val;
			if(!checkForLabel(name, ancestor))
			{
				return name;
			}
			val++;
		}
	}

	private static boolean checkForLabel(String name, PsiElement ancestor)
	{
		final LabelSearchVisitor visitor = new LabelSearchVisitor(name);
		ancestor.accept(visitor);
		return visitor.isUsed();
	}

	/**
	 * Returns true if given switch block has a rule-based format (like 'case 0 ->')
	 *
	 * @param block block to test
	 * @return true if given switch block has a rule-based format; false if it has conventional label-based format (like 'case 0:')
	 * If switch body has no labels yet and language level permits, rule-based format is assumed.
	 */
	public static boolean isRuleFormatSwitch(@Nonnull PsiSwitchBlock block)
	{
		if(!HighlightUtil.Feature.ENHANCED_SWITCH.isAvailable(block))
		{
			return false;
		}
		final PsiSwitchLabelStatementBase label = PsiTreeUtil.getChildOfType(block.getBody(), PsiSwitchLabelStatementBase.class);
		return label == null || label instanceof PsiSwitchLabeledRuleStatement;
	}


	/**
	 * @param label a switch label statement
	 * @return list of enum constants which are targets of the specified label; empty list if the supplied element is not a switch label,
	 * or it is not an enum switch.
	 */
	@Nonnull
	public static List<PsiEnumConstant> findEnumConstants(PsiSwitchLabelStatementBase label)
	{
		if(label == null)
		{
			return Collections.emptyList();
		}
		final PsiExpressionList list = label.getCaseValues();
		if(list == null)
		{
			return Collections.emptyList();
		}
		List<PsiEnumConstant> constants = new ArrayList<>();
		for(PsiExpression value : list.getExpressions())
		{
			if(value instanceof PsiReferenceExpression)
			{
				final PsiElement target = ((PsiReferenceExpression) value).resolve();
				if(target instanceof PsiEnumConstant)
				{
					constants.add((PsiEnumConstant) target);
					continue;
				}
			}
			return Collections.emptyList();
		}
		return constants;
	}

	private static class LabelSearchVisitor extends JavaRecursiveElementWalkingVisitor
	{

		private final String m_labelName;
		private boolean m_used = false;

		LabelSearchVisitor(String name)
		{
			m_labelName = name;
		}

		@Override
		public void visitElement(PsiElement element)
		{
			if(m_used)
			{
				return;
			}
			super.visitElement(element);
		}

		@Override
		public void visitLabeledStatement(PsiLabeledStatement statement)
		{
			final PsiIdentifier labelIdentifier = statement.getLabelIdentifier();
			final String labelText = labelIdentifier.getText();
			if(labelText.equals(m_labelName))
			{
				m_used = true;
			}
		}

		public boolean isUsed()
		{
			return m_used;
		}
	}
}
