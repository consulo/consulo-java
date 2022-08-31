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
package com.intellij.java.analysis.impl.codeInsight.daemon.impl.quickfix;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.annotation.Nonnull;

import consulo.logging.Logger;
import org.jetbrains.annotations.Nls;
import com.intellij.psi.PsiElement;
import com.intellij.java.language.psi.PsiJavaModule;
import com.intellij.java.language.psi.PsiKeyword;
import com.intellij.java.language.psi.PsiPackageAccessibilityStatement;
import com.intellij.java.language.psi.PsiPackageAccessibilityStatement.Role;
import consulo.java.analysis.impl.JavaQuickFixBundle;

/**
 * @author Pavel.Dolgov
 */
public class MergePackageAccessibilityStatementsFix extends MergeModuleStatementsFix<PsiPackageAccessibilityStatement>
{

	private static final Logger LOG = Logger.getInstance(MergePackageAccessibilityStatementsFix.class);
	private final String myPackageName;
	private final Role myRole;

	protected MergePackageAccessibilityStatementsFix(@Nonnull PsiJavaModule javaModule, @Nonnull String packageName, @Nonnull Role role)
	{
		super(javaModule);
		myPackageName = packageName;
		myRole = role;
	}

	@Nls
	@Nonnull
	@Override
	public String getText()
	{
		return JavaQuickFixBundle.message("java.9.merge.module.statements.fix.name", getKeyword(), myPackageName);
	}

	@Nls
	@Nonnull
	@Override
	public String getFamilyName()
	{
		return JavaQuickFixBundle.message("java.9.merge.module.statements.fix.family.name", getKeyword());
	}

	@Nonnull
	@Override
	protected String getReplacementText(@Nonnull List<PsiPackageAccessibilityStatement> statementsToMerge)
	{
		final List<String> moduleNames = getModuleNames(statementsToMerge);
		if(!moduleNames.isEmpty())
		{
			return getKeyword() + " " + myPackageName + " " + PsiKeyword.TO + " " + joinUniqueNames(moduleNames) + ";";
		}
		return getKeyword() + " " + myPackageName + ";";
	}

	@Nonnull
	private static List<String> getModuleNames(@Nonnull List<PsiPackageAccessibilityStatement> statements)
	{
		final List<String> result = new ArrayList<>();
		for(PsiPackageAccessibilityStatement statement : statements)
		{
			final List<String> moduleNames = statement.getModuleNames();
			if(moduleNames.isEmpty())
			{
				return Collections.emptyList();
			}
			result.addAll(moduleNames);
		}
		return result;
	}

	@Nonnull
	@Override
	protected List<PsiPackageAccessibilityStatement> getStatementsToMerge(@Nonnull PsiJavaModule javaModule)
	{
		return StreamSupport.stream(getStatements(javaModule, myRole).spliterator(), false).filter(statement -> myPackageName.equals(statement.getPackageName())).collect(Collectors.toList());
	}

	@javax.annotation.Nullable
	public static MergeModuleStatementsFix createFix(@javax.annotation.Nullable PsiPackageAccessibilityStatement statement)
	{
		if(statement != null)
		{
			final PsiElement parent = statement.getParent();
			if(parent instanceof PsiJavaModule)
			{
				final String packageName = statement.getPackageName();
				if(packageName != null)
				{
					return new MergePackageAccessibilityStatementsFix((PsiJavaModule) parent, packageName, statement.getRole());
				}
			}
		}
		return null;
	}

	@Nonnull
	private static Iterable<PsiPackageAccessibilityStatement> getStatements(@Nonnull PsiJavaModule javaModule, @Nonnull Role role)
	{
		switch(role)
		{
			case OPENS:
				return javaModule.getOpens();
			case EXPORTS:
				return javaModule.getExports();
		}
		LOG.error("Unexpected role " + role);
		return Collections.emptyList();
	}

	@Nonnull
	private String getKeyword()
	{
		switch(myRole)
		{
			case OPENS:
				return PsiKeyword.OPENS;
			case EXPORTS:
				return PsiKeyword.EXPORTS;
		}
		LOG.error("Unexpected role " + myRole);
		return "";
	}
}
