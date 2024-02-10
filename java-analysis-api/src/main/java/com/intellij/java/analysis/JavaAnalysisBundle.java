// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis;

import consulo.component.util.localize.AbstractBundle;
import org.jetbrains.annotations.NonNls;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.PropertyKey;

import java.util.function.Supplier;

public final class JavaAnalysisBundle extends AbstractBundle
{
	@NonNls
	public static final String BUNDLE = "messages.JavaAnalysisBundle";
	private static final JavaAnalysisBundle INSTANCE = new JavaAnalysisBundle();

	private JavaAnalysisBundle()
	{
		super(BUNDLE);
	}

	@Nonnull
	public static String message(@Nonnull @PropertyKey(resourceBundle = BUNDLE) String key, @Nonnull Object  ...params)
	{
		return INSTANCE.getMessage(key, params);
	}

	@Nonnull
	public static Supplier<String> messagePointer(@Nonnull @PropertyKey(resourceBundle = BUNDLE) String key, Object... params)
	{
		return () -> INSTANCE.getMessage(key, params);
	}
}
