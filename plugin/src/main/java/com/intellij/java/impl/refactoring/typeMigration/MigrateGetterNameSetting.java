/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.java.impl.refactoring.typeMigration;

import java.util.concurrent.atomic.AtomicReference;

import consulo.application.ApplicationManager;
import consulo.ui.ex.awt.Messages;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.impl.refactoring.typeMigration.usageInfo.OverriddenUsageInfo;
import consulo.ui.ex.awt.UIUtil;

/**
 * @author Dmitry Batkovich
 */
class MigrateGetterNameSetting
{
	private static final String CODE_ALWAYS_YES = "Always Migrate Method Names";
	private static final String CODE_ALWAYS_NO = "Never Migrate Method Names";
	private static final String[] CODES = new String[]{
			CODE_ALWAYS_YES,
			Messages.YES_BUTTON,
			CODE_ALWAYS_NO,
			Messages.NO_BUTTON
	};

	private final AtomicReference<Boolean> myGlobalValue = new AtomicReference<>();

	void askUserIfNeed(final OverriddenUsageInfo info, final String newMethodName, final PsiType migrationReturnType)
	{
		final Boolean globalValue = myGlobalValue.get();
		if(globalValue == null)
		{
			final String currentName = ((PsiMethod) info.getElement()).getName();
			final String messageText = String.format("Do migrate getter name from '%s' to '%s' since return type is migrated to '%s'?", currentName, newMethodName, migrationReturnType
					.getCanonicalText());
			UIUtil.invokeAndWaitIfNeeded((Runnable) () ->
			{
				final Boolean globalValue1 = myGlobalValue.get();
				if(globalValue1 == null)
				{
					final int code = showChooserDialog(messageText);
					if(code == 0)
					{
						myGlobalValue.set(true);
						info.setMigrateMethodName(newMethodName);
					}
					else if(code == 1)
					{
						info.setMigrateMethodName(newMethodName);
					}
					else if(code == 2)
					{
						myGlobalValue.set(false);
					}
				}
				else if(globalValue1.equals(Boolean.TRUE))
				{
					info.setMigrateMethodName(newMethodName);
				}
			});
		}
		else if(globalValue.equals(Boolean.TRUE))
		{
			info.setMigrateMethodName(newMethodName);
		}
	}

	private static int showChooserDialog(String messageText)
	{
		if(ApplicationManager.getApplication().isUnitTestMode())
		{
			return messageText.contains("dontMigrateName") ? 3 : 1;
		}
		else
		{
			return Messages.showIdeaMessageDialog(null, messageText, "Type Migration", CODES, 0, null, null);
		}
	}
}
