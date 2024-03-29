/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.siyeh.ig;

import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.Nls;
import consulo.language.editor.inspection.GlobalInspectionTool;

/**
 * @author Bas Leijdekkers
 */
public abstract class BaseSharedLocalInspection<T extends GlobalInspectionTool> extends BaseInspection
{

	protected final T mySettingsDelegate;

	public BaseSharedLocalInspection(T settingsDelegate)
	{
		mySettingsDelegate = settingsDelegate;
	}

	@Nonnull
	@Override
	public final String getShortName()
	{
		return mySettingsDelegate.getShortName();
	}

	@Nls
	@Nonnull
	@Override
	public final String getDisplayName()
	{
		return mySettingsDelegate.getDisplayName();
	}
}
