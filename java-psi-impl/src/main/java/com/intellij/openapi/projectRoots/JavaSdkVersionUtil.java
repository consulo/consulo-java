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
package com.intellij.openapi.projectRoots;

import javax.annotation.Nonnull;

import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.psi.PsiElement;
import consulo.annotations.RequiredReadAction;
import consulo.java.module.extension.JavaModuleExtension;

/**
 * User: anna
 * Date: 3/28/12
 */
public class JavaSdkVersionUtil
{
	public static boolean isAtLeast(@Nonnull PsiElement element, @Nonnull JavaSdkVersion minVersion)
	{
		JavaSdkVersion version = getJavaSdkVersion(element);
		return version == null || version.isAtLeast(minVersion);
	}

	@RequiredReadAction
	public static JavaSdkVersion getJavaSdkVersion(@Nonnull PsiElement element)
	{
		final Sdk sdk = ModuleUtilCore.getSdk(element, JavaModuleExtension.class);
		return getJavaSdkVersion(sdk);
	}

	public static JavaSdkVersion getJavaSdkVersion(@javax.annotation.Nullable Sdk sdk)
	{
		return sdk != null && sdk.getSdkType() instanceof JavaSdk ? ((JavaSdk) sdk.getSdkType()).getVersion(sdk) : null;
	}
}
