// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.

package com.intellij.java.impl.refactoring.extractclass;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.intellij.java.impl.codeInsight.generation.GenerateMembersUtil;
import com.intellij.java.language.psi.*;
import consulo.project.Project;
import consulo.util.lang.ref.Ref;
import consulo.util.lang.StringUtil;
import consulo.language.psi.*;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.psi.PsiUtilCore;
import com.intellij.java.impl.refactoring.extractclass.usageInfo.ReplaceStaticVariableAccess;
import com.intellij.java.impl.refactoring.psi.MutationUtils;
import com.intellij.java.impl.refactoring.typeMigration.TypeMigrationProcessor;
import com.intellij.java.impl.refactoring.typeMigration.TypeMigrationRules;
import com.intellij.java.impl.refactoring.util.EnumConstantsUtil;
import com.intellij.java.impl.refactoring.util.FixableUsageInfo;
import consulo.language.editor.refactoring.ui.RefactoringUIUtil;
import consulo.usage.UsageInfo;
import consulo.util.lang.function.Functions;
import consulo.language.util.IncorrectOperationException;

public class ExtractEnumProcessor
{
	private final Project myProject;
	private final List<PsiField> myEnumConstants;
	private final PsiClass myClass;

	private TypeMigrationProcessor myTypeMigrationProcessor;

	public ExtractEnumProcessor(Project project, List<PsiField> enumConstants, PsiClass aClass)
	{
		myProject = project;
		myEnumConstants = enumConstants;
		myClass = aClass;
	}


	public void findEnumConstantConflicts(final Ref<UsageInfo[]> refUsages)
	{
		if(hasUsages2Migrate())
		{
			final List<UsageInfo> resolvableConflicts = new ArrayList<>();
			for(UsageInfo failedUsage : myTypeMigrationProcessor.getLabeler().getFailedUsages())
			{
				final PsiElement element = failedUsage.getElement();
				if(element instanceof PsiReferenceExpression)
				{
					resolvableConflicts.add(new FixableUsageInfo(element)
					{
						@Override
						public void fixUsage() throws IncorrectOperationException
						{
							final PsiReferenceExpression expression = (PsiReferenceExpression) element;
							final String link = GenerateMembersUtil.suggestGetterName("value", expression.getType(), myProject) + "()";
							MutationUtils.replaceExpression(expression.getReferenceName() + "." + link, expression);
						}
					});
				}
				else if(element != null)
				{
					resolvableConflicts.add(new ConflictUsageInfo(element, null));
				}
			}
			if(!resolvableConflicts.isEmpty())
			{
				final List<UsageInfo> usageInfos = new ArrayList<>(Arrays.asList(refUsages.get()));
				for(Iterator<UsageInfo> iterator = resolvableConflicts.iterator(); iterator.hasNext(); )
				{
					final UsageInfo conflict = iterator.next();
					for(UsageInfo usageInfo : usageInfos)
					{
						if(conflict.getElement() == usageInfo.getElement())
						{
							iterator.remove();
							break;
						}
					}
				}
				resolvableConflicts.addAll(0, usageInfos);
				refUsages.set(resolvableConflicts.toArray(UsageInfo.EMPTY_ARRAY));
			}
		}
	}

	private boolean hasUsages2Migrate()
	{
		return myTypeMigrationProcessor != null;
	}

	public List<FixableUsageInfo> findEnumConstantUsages(List<FixableUsageInfo> fieldUsages)
	{
		final List<FixableUsageInfo> result = new ArrayList<>();
		if(!myEnumConstants.isEmpty())
		{
			final Set<PsiSwitchStatement> switchStatements = new HashSet<>();
			for(UsageInfo usage : fieldUsages)
			{
				if(usage instanceof ReplaceStaticVariableAccess)
				{
					final PsiElement element = usage.getElement();
					final PsiSwitchStatement switchStatement = PsiTreeUtil.getParentOfType(element, PsiSwitchStatement.class);
					if(switchStatement != null)
					{
						switchStatements.add(switchStatement);
					}
				}
			}

			final PsiConstantEvaluationHelper evaluationHelper = JavaPsiFacade.getInstance(myProject).getConstantEvaluationHelper();
			final Set<Object> enumValues = new HashSet<>();
			for(PsiField enumConstant : myEnumConstants)
			{
				enumValues.add(evaluationHelper.computeConstantExpression(enumConstant.getInitializer()));
			}
			final PsiType enumValueType = myEnumConstants.get(0).getType();

			for(PsiSwitchStatement switchStatement : switchStatements)
			{
				final PsiStatement errStatement = EnumConstantsUtil.isEnumSwitch(switchStatement, enumValueType, enumValues);
				if(errStatement != null)
				{
					String description = null;
					if(errStatement instanceof PsiSwitchLabelStatement)
					{
						final PsiExpression caseValue = ((PsiSwitchLabelStatement) errStatement).getCaseValue();
						if(caseValue != null)
						{
							description = caseValue.getText() + " can not be replaced with enum";
						}
					}
					result.add(new ConflictUsageInfo(errStatement, description));

				}
				else
				{
					final PsiExpression expression = switchStatement.getExpression();
					if(expression instanceof PsiReferenceExpression)
					{
						final PsiElement element = ((PsiReferenceExpression) expression).resolve();
						if(element != null)
						{
							if(!element.getManager().isInProject(element))
							{
								result.add(new ConflictUsageInfo(expression, StringUtil.capitalize(RefactoringUIUtil.getDescription(element, false)) + " is out of project"));
							}
						}
					}
					else
					{
						result.add(new ConflictUsageInfo(expression, null));
					}
				}
			}

			final TypeMigrationRules rules = new TypeMigrationRules(myProject);
			rules.addConversionDescriptor(new EnumTypeConversionRule(myEnumConstants));
			rules.setBoundScope(GlobalSearchScope.projectScope(myProject));
			myTypeMigrationProcessor = new TypeMigrationProcessor(myProject, PsiUtilCore.toPsiElementArray(myEnumConstants), Functions.constant(JavaPsiFacade.getElementFactory(myProject).createType
					(myClass)), rules, true);
			for(UsageInfo usageInfo : myTypeMigrationProcessor.findUsages())
			{
				final PsiElement migrateElement = usageInfo.getElement();
				if(migrateElement instanceof PsiField)
				{
					final PsiField enumConstantField = (PsiField) migrateElement;
					if(enumConstantField.hasModifierProperty(PsiModifier.STATIC) && enumConstantField.hasModifierProperty(PsiModifier.FINAL) && enumConstantField.hasInitializer() && !myEnumConstants
							.contains(enumConstantField))
					{
						continue;
					}
				}
				result.add(new EnumTypeMigrationUsageInfo(usageInfo));
			}
		}
		return result;
	}

	public void performEnumConstantTypeMigration(UsageInfo[] usageInfos)
	{
		if(hasUsages2Migrate())
		{
			final List<UsageInfo> migrationInfos = new ArrayList<>();
			for(UsageInfo usageInfo : usageInfos)
			{
				if(usageInfo instanceof EnumTypeMigrationUsageInfo)
				{
					migrationInfos.add(((EnumTypeMigrationUsageInfo) usageInfo).getUsageInfo());
				}
			}
			myTypeMigrationProcessor.performRefactoring(migrationInfos.toArray(UsageInfo.EMPTY_ARRAY));
		}
	}

	private static class EnumTypeMigrationUsageInfo extends FixableUsageInfo
	{
		private final UsageInfo myUsageInfo;

		public EnumTypeMigrationUsageInfo(UsageInfo usageInfo)
		{
			super(usageInfo.getElement());
			myUsageInfo = usageInfo;
		}

		@Override
		public void fixUsage() throws IncorrectOperationException
		{
		}

		public UsageInfo getUsageInfo()
		{
			return myUsageInfo;
		}
	}

	private static class ConflictUsageInfo extends FixableUsageInfo
	{
		private final String myDescription;

		public ConflictUsageInfo(PsiElement expression, String description)
		{
			super(expression);
			myDescription = description;
		}

		@Override
		public void fixUsage() throws IncorrectOperationException
		{
		}

		@Override
		public String getConflictMessage()
		{
			return "Unable to migrate statement to enum constant." + (myDescription != null ? " " + myDescription : "");
		}
	}
}