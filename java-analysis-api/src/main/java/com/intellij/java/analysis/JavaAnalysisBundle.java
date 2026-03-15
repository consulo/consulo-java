// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis;

import consulo.annotation.DeprecationInfo;
import consulo.annotation.internal.MigratedExtensionsTo;
import consulo.component.util.localize.AbstractBundle;
import consulo.java.analysis.localize.JavaAnalysisLocalize;
import org.jetbrains.annotations.PropertyKey;

import java.util.function.Supplier;

@Deprecated
@DeprecationInfo("Use JavaAnalysisLocalize")
@MigratedExtensionsTo(JavaAnalysisLocalize.class)
public final class JavaAnalysisBundle extends AbstractBundle
{
	public static final String BUNDLE = "messages.JavaAnalysisBundle";
	private static final JavaAnalysisBundle INSTANCE = new JavaAnalysisBundle();

	private JavaAnalysisBundle()
	{
		super(BUNDLE);
	}

	public static String message(@PropertyKey(resourceBundle = BUNDLE) String key, Object  ...params)
	{
		return INSTANCE.getMessage(key, params);
	}

	public static Supplier<String> messagePointer(@PropertyKey(resourceBundle = BUNDLE) String key, Object... params)
	{
		return () -> INSTANCE.getMessage(key, params);
	}
}
