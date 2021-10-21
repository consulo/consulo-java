// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.core;

import com.intellij.AbstractBundle;
import com.intellij.psi.PsiModifier;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import javax.annotation.Nonnull;
import org.jetbrains.annotations.PropertyKey;

import java.util.function.Supplier;

public final class JavaPsiBundle extends AbstractBundle
{
	@NonNls
	public static final String BUNDLE = "messages.JavaPsiBundle";
	public static final JavaPsiBundle INSTANCE = new JavaPsiBundle();

	private JavaPsiBundle()
	{
		super(BUNDLE);
	}

	@Nonnull
	public static
	@Nls
	String message(@Nonnull @PropertyKey(resourceBundle = BUNDLE) String key, Object... params)
	{
		return INSTANCE.getMessage(key, params);
	}

	@Nonnull
	public static Supplier<String> messagePointer(@Nonnull @PropertyKey(resourceBundle = BUNDLE) String key, Object... params)
	{
		return () -> INSTANCE.getMessage(key, params);
	}

	/**
	 * @param modifier modifier string constant
	 * @return modifier to display to the user.
	 * Note that it's not localized in the usual sense: modifiers returned from this method are kept in English,
	 * regardless of the active language pack. It's believed that this way it's more clear.
	 */
	@Nonnull
	public static String visibilityPresentation(@Nonnull @PsiModifier.ModifierConstant String modifier)
	{
		return modifier.equals(PsiModifier.PACKAGE_LOCAL) ? "package-private" : modifier;
	}
}