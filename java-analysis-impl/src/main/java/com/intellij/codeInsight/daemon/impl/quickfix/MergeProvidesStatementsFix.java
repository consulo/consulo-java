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
package com.intellij.codeInsight.daemon.impl.quickfix;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiProvidesStatement;
import com.intellij.psi.PsiReferenceList;
import com.intellij.util.containers.ContainerUtil;
import consulo.java.JavaQuickFixBundle;

/**
 * @author Pavel.Dolgov
 */
public class MergeProvidesStatementsFix extends MergeModuleStatementsFix<PsiProvidesStatement>
{
	private final String myInterfaceName;

	MergeProvidesStatementsFix(@NotNull PsiJavaModule javaModule, @NotNull String interfaceName)
	{
		super(javaModule);
		myInterfaceName = interfaceName;
	}

	@NotNull
	@Override
	public String getText()
	{
		return JavaQuickFixBundle.message("java.9.merge.module.statements.fix.name", PsiKeyword.PROVIDES, myInterfaceName);
	}

	@Nls
	@NotNull
	@Override
	public String getFamilyName()
	{
		return JavaQuickFixBundle.message("java.9.merge.module.statements.fix.family.name", PsiKeyword.PROVIDES);
	}

	@NotNull
	@Override
	protected String getReplacementText(@NotNull List<PsiProvidesStatement> statementsToMerge)
	{
		final List<String> implementationNames = getImplementationNames(statementsToMerge);
		LOG.assertTrue(!implementationNames.isEmpty());
		return PsiKeyword.PROVIDES + " " + myInterfaceName + " " + PsiKeyword.WITH + " " + joinUniqueNames(implementationNames) + ";";
	}

	@NotNull
	private static List<String> getImplementationNames(@NotNull List<PsiProvidesStatement> statements)
	{
		List<String> list = new ArrayList<>();
		for(PsiProvidesStatement statement : statements)
		{
			PsiReferenceList implementationList = statement.getImplementationList();
			if(implementationList == null)
			{
				continue;
			}
			for(PsiJavaCodeReferenceElement element : implementationList.getReferenceElements())
			{
				ContainerUtil.addIfNotNull(list, element.getQualifiedName());
			}
		}
		return list;
	}

	@NotNull
	@Override
	protected List<PsiProvidesStatement> getStatementsToMerge(@NotNull PsiJavaModule javaModule)
	{
		return StreamSupport.stream(javaModule.getProvides().spliterator(), false).filter(statement ->
		{
			final PsiJavaCodeReferenceElement reference = statement.getInterfaceReference();
			return reference != null && myInterfaceName.equals(reference.getQualifiedName());
		}).collect(Collectors.toList());
	}

	@Nullable
	public static MergeModuleStatementsFix createFix(@Nullable PsiProvidesStatement statement)
	{
		if(statement != null)
		{
			final PsiElement parent = statement.getParent();
			if(parent instanceof PsiJavaModule)
			{
				final PsiJavaCodeReferenceElement interfaceReference = statement.getInterfaceReference();
				if(interfaceReference != null)
				{
					final String interfaceName = interfaceReference.getQualifiedName();
					if(interfaceName != null)
					{
						return new MergeProvidesStatementsFix((PsiJavaModule) parent, interfaceName);
					}
				}
			}
		}
		return null;
	}
}