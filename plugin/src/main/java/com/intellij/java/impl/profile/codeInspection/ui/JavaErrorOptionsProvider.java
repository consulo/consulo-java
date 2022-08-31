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
package com.intellij.java.impl.profile.codeInspection.ui;

import java.util.function.Consumer;

import javax.annotation.Nonnull;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.openapi.application.ApplicationBundle;
import consulo.codeInspection.ui.ErrorPropertiesProvider;
import consulo.options.SimpleConfigurableByProperties;
import consulo.ui.CheckBox;
import consulo.ui.Component;
import consulo.ui.annotation.RequiredUIAccess;

public class JavaErrorOptionsProvider implements ErrorPropertiesProvider
{
	@Override
	@RequiredUIAccess
	public void fillProperties(@Nonnull Consumer<Component> consumer, @Nonnull SimpleConfigurableByProperties.PropertyBuilder propertyBuilder)
	{
		CheckBox suppressWay = CheckBox.create(ApplicationBundle.message("checkbox.suppress.with.suppresswarnings"));
		consumer.accept(suppressWay);
		
		DaemonCodeAnalyzerSettings settings = DaemonCodeAnalyzerSettings.getInstance();

		propertyBuilder.add(suppressWay, settings::isSuppressWarnings, settings::setSuppressWarnings);
	}
}