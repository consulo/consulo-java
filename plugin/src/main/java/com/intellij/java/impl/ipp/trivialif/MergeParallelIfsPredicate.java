/*
 * Copyright 2003-2014 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ipp.trivialif;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import com.intellij.java.language.psi.*;
import consulo.language.psi.*;
import consulo.language.psi.util.PsiTreeUtil;
import com.intellij.java.analysis.impl.codeInspection.ControlFlowUtils;
import com.intellij.java.analysis.impl.codeInspection.EquivalenceChecker;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.impl.ipp.psiutils.ErrorUtil;

class MergeParallelIfsPredicate implements PsiElementPredicate
{

	public boolean satisfiedBy(PsiElement element)
	{
		if(!(element instanceof PsiJavaToken))
		{
			return false;
		}
		PsiJavaToken token = (PsiJavaToken) element;
		PsiElement parent = token.getParent();
		if(!(parent instanceof PsiIfStatement))
		{
			return false;
		}
		PsiIfStatement ifStatement = (PsiIfStatement) parent;
		PsiElement nextStatement = PsiTreeUtil.skipSiblingsForward(ifStatement, PsiWhiteSpace.class);
		if(!(nextStatement instanceof PsiIfStatement))
		{
			return false;
		}
		PsiIfStatement nextIfStatement = (PsiIfStatement) nextStatement;
		if(ErrorUtil.containsError(ifStatement))
		{
			return false;
		}
		if(ErrorUtil.containsError(nextIfStatement))
		{
			return false;
		}
		if(!ifStatementsCanBeMerged(ifStatement, nextIfStatement))
		{
			return false;
		}
		PsiExpression condition = ifStatement.getCondition();
		Set<PsiVariable> variables = VariableAccessUtils.collectUsedVariables(condition);
		PsiStatement thenBranch = ifStatement.getThenBranch();
		if(VariableAccessUtils.isAnyVariableAssigned(variables, thenBranch))
		{
			return false;
		}
		PsiStatement elseBranch = ifStatement.getElseBranch();
		return !VariableAccessUtils.isAnyVariableAssigned(variables, elseBranch);
	}

	public static boolean ifStatementsCanBeMerged(PsiIfStatement statement1, PsiIfStatement statement2)
	{
		PsiStatement thenBranch = statement1.getThenBranch();
		PsiStatement elseBranch = statement1.getElseBranch();
		if(thenBranch == null)
		{
			return false;
		}
		PsiExpression firstCondition = statement1.getCondition();
		PsiExpression secondCondition = statement2.getCondition();
		if(!EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(firstCondition, secondCondition))
		{
			return false;
		}
		PsiStatement nextThenBranch = statement2.getThenBranch();
		if(!canBeMerged(thenBranch, nextThenBranch))
		{
			return false;
		}
		PsiStatement nextElseBranch = statement2.getElseBranch();
		return elseBranch == null || nextElseBranch == null ||
				canBeMerged(elseBranch, nextElseBranch);
	}

	private static boolean canBeMerged(PsiStatement statement1, PsiStatement statement2)
	{
		if(!ControlFlowUtils.statementMayCompleteNormally(statement1))
		{
			return false;
		}
		Set<String> statement1Declarations = calculateTopLevelDeclarations(statement1);
		if(containsConflictingDeclarations(statement1Declarations, statement2))
		{
			return false;
		}
		Set<String> statement2Declarations = calculateTopLevelDeclarations(statement2);
		return !containsConflictingDeclarations(statement2Declarations, statement1);
	}

	private static boolean containsConflictingDeclarations(Set<String> declarations, PsiElement context)
	{
		DeclarationVisitor visitor = new DeclarationVisitor(declarations);
		context.accept(visitor);
		return visitor.hasConflict();
	}

	private static Set<String> calculateTopLevelDeclarations(PsiStatement statement)
	{
		Set<String> out = new HashSet<String>();
		if(statement instanceof PsiDeclarationStatement)
		{
			addDeclarations((PsiDeclarationStatement) statement, out);
		}
		else if(statement instanceof PsiBlockStatement)
		{
			PsiBlockStatement blockStatement = (PsiBlockStatement) statement;
			PsiCodeBlock block = blockStatement.getCodeBlock();
			PsiStatement[] statements = block.getStatements();
			for(PsiStatement statement1 : statements)
			{
				if(statement1 instanceof PsiDeclarationStatement)
				{
					addDeclarations((PsiDeclarationStatement) statement1, out);
				}
			}
		}
		return out;
	}

	private static void addDeclarations(PsiDeclarationStatement statement, Collection<String> declaredVariables)
	{
		PsiElement[] elements = statement.getDeclaredElements();
		for(PsiElement element : elements)
		{
			if(element instanceof PsiVariable)
			{
				PsiVariable variable = (PsiVariable) element;
				String name = variable.getName();
				declaredVariables.add(name);
			}
		}
	}

	private static class DeclarationVisitor extends JavaRecursiveElementWalkingVisitor
	{

		private final Set<String> declarations;
		private boolean hasConflict = false;

		private DeclarationVisitor(Set<String> declarations)
		{
			super();
			this.declarations = new HashSet<String>(declarations);
		}

		@Override
		public void visitVariable(PsiVariable variable)
		{
			super.visitVariable(variable);
			String name = variable.getName();
			for(Object declaration : declarations)
			{
				String testName = (String) declaration;
				if(testName.equals(name))
				{
					hasConflict = true;
				}
			}
		}

		public boolean hasConflict()
		{
			return hasConflict;
		}
	}
}