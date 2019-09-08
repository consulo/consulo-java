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
package com.intellij.pom.java;

import com.intellij.core.JavaCoreBundle;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import consulo.java.psi.JavaLanguageVersion;
import consulo.util.pointers.Named;
import consulo.util.pointers.NamedPointer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author dsl
 */
public enum LanguageLevel implements Named, NamedPointer<LanguageLevel>
{
	JDK_1_3("1.3", JavaCoreBundle.message("jdk.1.3.language.level.description"), "1.3"),
	JDK_1_4("1.4", JavaCoreBundle.message("jdk.1.4.language.level.description"), "1.4"),
	JDK_1_5("1.5", JavaCoreBundle.message("jdk.1.5.language.level.description"), "1.5", "5"),
	JDK_1_6("1.6", JavaCoreBundle.message("jdk.1.6.language.level.description"), "1.6", "6"),
	JDK_1_7("1.7", JavaCoreBundle.message("jdk.1.7.language.level.description"), "1.7", "7"),
	JDK_1_8("1.8", JavaCoreBundle.message("jdk.1.8.language.level.description"), "1.8", "8", "8"),
	JDK_1_9("1.9", JavaCoreBundle.message("jdk.1.9.language.level.description"), "9", "1.9", "9"),
	JDK_10("10", JavaCoreBundle.message("jdk.10.language.level.description"), "1.10", "10"),
	JDK_11("11", JavaCoreBundle.message("jdk.11.language.level.description"), "1.11", "11"),
	JDK_12("12", JavaCoreBundle.message("jdk.12.language.level.description"), "1.12", "12"),
	JDK_13("13", JavaCoreBundle.message("jdk.13.language.level.description"), "13"),
	JDK_X("X", JavaCoreBundle.message("jdk.X.language.level.description"), "");

	public static final LanguageLevel HIGHEST = JDK_12;
	public static final Key<LanguageLevel> KEY = Key.create("LANGUAGE_LEVEL");

	private final String myShortText;
	private final String myPresentableText;
	private final String[] myCompilerComplianceOptions;

	private JavaLanguageVersion myLangVersion;

	/**
	 * @param compilerComplianceOptions versions supported by Javac '-source' parameter
	 * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/tools/windows/javac.html">Javac Reference</a>
	 */
	LanguageLevel(String shortText, String presentableText, String... compilerComplianceOptions)
	{
		myShortText = shortText;
		myPresentableText = presentableText;
		myCompilerComplianceOptions = compilerComplianceOptions;
		myLangVersion = new JavaLanguageVersion(name(), shortText, this);
	}

	/**
	 * String representation of the level, suitable to pass as a value of compiler's "-source" and "-target" options
	 */
	public String getCompilerComplianceDefaultOption()
	{
		return myCompilerComplianceOptions[0];
	}

	@Nonnull
	public JavaLanguageVersion toLangVersion()
	{
		return myLangVersion;
	}

	public String getDescription()
	{
		return myPresentableText;
	}

	public boolean isAtLeast(final LanguageLevel level)
	{
		return compareTo(level) >= 0;
	}

	@Override
	public LanguageLevel get()
	{
		return this;
	}

	@Nonnull
	@Override
	public String getName()
	{
		return name();
	}

	@Nullable
	public static LanguageLevel parse(final String compilerComplianceOption)
	{
		if(StringUtil.isEmpty(compilerComplianceOption))
		{
			return null;
		}
		return ContainerUtil.find(values(), level -> ArrayUtil.contains(compilerComplianceOption, level.myCompilerComplianceOptions));
	}

	@Nonnull
	public String getShortText()
	{
		return myShortText;
	}

	@Nonnull
	public String getFullText()
	{
		return "Java " + myShortText;
	}
}
