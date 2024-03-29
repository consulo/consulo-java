/*
 * Copyright 2001-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.impl.generate.config;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import consulo.codeEditor.Editor;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import consulo.language.util.IncorrectOperationException;

/**
 * Interface that defines a policy for dealing with conflicts (i.e., the user is
 * trying to tostring a {@link Object#toString} method but one already exists in
 * this class).
 */
public interface ConflictResolutionPolicy
{
	/**
	 * Inject the policy to use when inserting a new method.
	 *
	 * @param strategy the policy to use.
	 * @since 3.18
	 */
	void setNewMethodStrategy(InsertNewMethodStrategy strategy);

	/**
	 * Applies the chosen policy.
	 *
	 * @param clazz          PSIClass.
	 * @param existingMethod existing method if one exists.
	 * @param newMethod      new method.
	 * @param editor
	 * @return if the policy was executed normally (not cancelled)
	 * @throws IncorrectOperationException is thrown if there is an IDEA error.
	 */
	@Nullable
	PsiMethod applyMethod(PsiClass clazz, PsiMethod existingMethod, @Nonnull PsiMethod newMethod, Editor editor);
}
