/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.language.psi;

import jakarta.annotation.Nullable;

/**
 * Represents a {@code requires} directive of a Java module declaration.
 */
public interface PsiRequiresStatement extends PsiModifierListOwner, PsiStatement
{
	PsiRequiresStatement[] EMPTY_ARRAY = new PsiRequiresStatement[0];

	@Nullable
	PsiJavaModuleReferenceElement getReferenceElement();

	@Nullable
	String getModuleName();

	@Nullable
	PsiJavaModuleReference getModuleReference();

	default
	@Nullable
	PsiJavaModule resolve()
	{
		PsiJavaModuleReference ref = getModuleReference();
		return ref != null ? ref.resolve() : null;
	}
}