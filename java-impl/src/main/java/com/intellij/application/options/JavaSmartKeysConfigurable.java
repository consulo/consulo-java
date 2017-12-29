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
package com.intellij.application.options;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;
import com.intellij.javadoc.JavadocBundle;
import com.intellij.openapi.options.BeanConfigurable;
import com.intellij.openapi.options.Configurable;
import consulo.java.application.options.JavaSmartKeysSettings;

/**
 * @author Denis Zhdanov
 * @since 2/2/11 12:32 PM
 */
public class JavaSmartKeysConfigurable extends BeanConfigurable<JavaSmartKeysSettings> implements Configurable
{
	public JavaSmartKeysConfigurable()
	{
		super(JavaSmartKeysSettings.getInstance());
		checkBox("JAVADOC_GENERATE_CLOSING_TAG", JavadocBundle.message("javadoc.generate.closing.tag"));
	}

	@Nls
	@Override
	public String getDisplayName()
	{
		return null;
	}

	@Nullable
	@Override
	public String getHelpTopic()
	{
		return null;
	}
}
