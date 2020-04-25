/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 14-Feb-2008
 */
package com.intellij.profile.codeInspection.ui;

import javax.annotation.Nullable;
import javax.swing.*;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.openapi.application.ApplicationBundle;
import consulo.awt.TargetAWT;
import consulo.ui.CheckBox;
import consulo.ui.Component;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.layout.HorizontalLayout;

public class JavaErrorOptionsProvider implements ErrorOptionsProvider
{
	private CheckBox mySuppressWay;

	@RequiredUIAccess
	@Nullable
	@Override
	public Component createUIComponent()
	{
		mySuppressWay = CheckBox.create(ApplicationBundle.message("checkbox.suppress.with.suppresswarnings"));
		return HorizontalLayout.create().add(mySuppressWay);
	}

	@RequiredUIAccess
	@Override
	public JComponent createComponent()
	{
		return (JComponent) TargetAWT.to(createUIComponent());
	}

	@RequiredUIAccess
	@Override
	public void reset()
	{
		mySuppressWay.setValue(DaemonCodeAnalyzerSettings.getInstance().isSuppressWarnings());
	}

	@RequiredUIAccess
	@Override
	public void disposeUIResources()
	{
		mySuppressWay = null;
	}

	@RequiredUIAccess
	@Override
	public void apply()
	{
		DaemonCodeAnalyzerSettings.getInstance().setSuppressWarnings(mySuppressWay.getValueOrError());
	}

	@RequiredUIAccess
	@Override
	public boolean isModified()
	{
		DaemonCodeAnalyzerSettings settings = DaemonCodeAnalyzerSettings.getInstance();
		return mySuppressWay.getValueOrError() != settings.isSuppressWarnings();
	}

}