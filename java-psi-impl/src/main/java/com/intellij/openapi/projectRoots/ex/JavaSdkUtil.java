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
package com.intellij.openapi.projectRoots.ex;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JavaSdkVersionUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.PathUtil;
import com.intellij.util.PathsList;
import consulo.annotations.DeprecationInfo;
import consulo.java.module.extension.JavaModuleExtension;

public class JavaSdkUtil
{
	@NonNls
	public static final String IDEA_PREPEND_RTJAR = "idea.prepend.rtjar";

	public static void addRtJar(PathsList pathsList)
	{
		final String javaRtJarPath = getJavaRtJarPath();
		if(Boolean.getBoolean(IDEA_PREPEND_RTJAR))
		{
			pathsList.addFirst(javaRtJarPath);
		}
		else
		{
			pathsList.addTail(javaRtJarPath);
		}
	}

	public static String getJunit4JarPath()
	{
		try
		{
			return PathUtil.getJarPathForClass(Class.forName("org.junit.Test"));
		}
		catch(ClassNotFoundException e)
		{
			throw new RuntimeException(e);
		}
	}

	public static String getJunit3JarPath()
	{
		try
		{
			return PathUtil.getJarPathForClass(Class.forName("junit.runner.TestSuiteLoader")); //junit3 specific class
		}
		catch(ClassNotFoundException e)
		{
			throw new RuntimeException(e);
		}
	}

	@NotNull
	@Deprecated
	@DeprecationInfo("Use #getJavaRtJarPath()")
	public static String getIdeaRtJarPath()
	{
		return getJavaRtJarPath();
	}

	@NotNull
	public static String getJavaRtJarPath()
	{
		try
		{
			return PathUtil.getJarPathForClass(Class.forName("com.intellij.rt.compiler.JavacRunner"));
		}
		catch(ClassNotFoundException e)
		{
			throw new RuntimeException(e);
		}
	}

	public static boolean isLanguageLevelAcceptable(@NotNull Project project, @NotNull Module module, @NotNull LanguageLevel level)
	{
		return isJdkSupportsLevel(getRelevantJdk(project, module), level);
	}

	private static boolean isJdkSupportsLevel(@Nullable final Sdk jdk, @NotNull LanguageLevel level)
	{
		if(jdk == null)
		{
			return true;
		}
		JavaSdkVersion version = JavaSdkVersionUtil.getJavaSdkVersion(jdk);
		return version != null && version.getMaxLanguageLevel().isAtLeast(level);
	}

	@Nullable
	private static Sdk getRelevantJdk(@NotNull Project project, @NotNull Module module)
	{
		Sdk moduleJdk = ModuleUtilCore.getSdk(module, JavaModuleExtension.class);
		return moduleJdk == null ? null : moduleJdk;
	}

	@Contract("null, _ -> false")
	public static boolean isJdkAtLeast(@Nullable Sdk jdk, @NotNull JavaSdkVersion expected)
	{
		if(jdk != null)
		{
			SdkTypeId type = jdk.getSdkType();
			if(type instanceof JavaSdk)
			{
				JavaSdkVersion actual = ((JavaSdk) type).getVersion(jdk);
				if(actual != null)
				{
					return actual.isAtLeast(expected);
				}
			}
		}

		return false;
	}
}
