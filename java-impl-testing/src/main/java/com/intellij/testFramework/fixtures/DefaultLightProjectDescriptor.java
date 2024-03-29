/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.testFramework.fixtures;

import javax.annotation.Nonnull;

import consulo.module.Module;
import consulo.content.bundle.Sdk;
import consulo.module.content.layer.ContentEntry;
import consulo.module.content.layer.ModifiableRootModel;
import com.intellij.java.language.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.TestModuleDescriptor;
import consulo.ide.impl.idea.util.Consumer;
import consulo.java.language.module.extension.JavaMutableModuleExtension;

/**
 * @author peter
 */
public class DefaultLightProjectDescriptor implements TestModuleDescriptor
{
	public Sdk getSdk()
	{
		return IdeaTestUtil.getMockJdk17();
	}

	@Override
	public void configureSdk(@Nonnull Consumer<Sdk> consumer)
	{
		consumer.consume(getSdk());
	}

	@Override
	public void configureModule(Module module, ModifiableRootModel model, ContentEntry contentEntry)
	{
		model.getExtensionWithoutCheck(JavaMutableModuleExtension.class).getInheritableLanguageLevel().set(null, LanguageLevel.HIGHEST);
	}
}
