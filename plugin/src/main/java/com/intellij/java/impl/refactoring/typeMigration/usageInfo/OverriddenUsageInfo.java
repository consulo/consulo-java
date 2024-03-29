/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.java.impl.refactoring.typeMigration.usageInfo;

import jakarta.annotation.Nonnull;
import consulo.language.psi.PsiElement;

/**
 * @author anna
 */
public class OverriddenUsageInfo extends TypeMigrationUsageInfo
{
	private volatile String myMigrateMethodName;

	public OverriddenUsageInfo(@Nonnull PsiElement element)
	{
		super(element);
	}

	public String getMigrateMethodName()
	{
		return myMigrateMethodName;
	}

	public void setMigrateMethodName(String migrateMethodName)
	{
		myMigrateMethodName = migrateMethodName;
	}
}