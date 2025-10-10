/*
 * Copyright 2006-2011 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig;

import com.intellij.java.analysis.codeInspection.GlobalJavaInspectionTool;
import com.intellij.java.language.JavaLanguage;
import com.siyeh.ig.GroupDisplayNameUtil;
import consulo.java.deadCodeNotWorking.OldStyleInspection;
import consulo.language.Language;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.Nls;

public abstract class BaseGlobalInspection extends GlobalJavaInspectionTool implements OldStyleInspection
{
	@Override
	@Nonnull
	public final LocalizeValue getGroupDisplayName()
	{
		return GroupDisplayNameUtil.getGroupDisplayName(getClass());
	}

	@Nullable
	@Override
	public Language getLanguage()
	{
		return JavaLanguage.INSTANCE;
	}

	@Override
	public boolean isEnabledByDefault()
	{
		return false;
	}
}