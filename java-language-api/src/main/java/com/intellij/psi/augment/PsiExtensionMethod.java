// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.augment;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * An extension instance method that delegates to another method.
 * Does not exist normally in Java but may be supported by annotation processors or other language extensions.
 */
public interface PsiExtensionMethod extends PsiMethod
{
	/**
	 * @return a target method
	 */
	@Nonnull
	PsiMethod getTargetMethod();

	/**
	 * @return a target method parameter that corresponds to the receiver of this extension method;
	 * null if the receiver does not correspond to any parameter of the target method.
	 */
	@Nullable
	PsiParameter getTargetReceiverParameter();

	/**
	 * @param index index of this method parameter
	 * @return a target method parameter that corresponds to the parameter of this extension method having the specified index;
	 * null if the specified parameter does not correspond to any parameter of the target method.
	 */
	@Nullable
	PsiParameter getTargetParameter(int index);
}
