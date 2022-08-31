/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.java.language.codeInsight;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyKey;
import com.intellij.java.language.psi.PsiAnnotation;
import com.intellij.java.language.psi.PsiModifierListOwner;
import org.jetbrains.annotations.Contract;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Returns annotations inferred by bytecode or source code, for example contracts and nullity.
 *
 * @see NullableNotNullManager
 * @see Contract
 * @see Nullable
 * @see Nonnull
 * @see AnnotationUtil
 */
public abstract class InferredAnnotationsManager
{
	private static final NotNullLazyKey<InferredAnnotationsManager, Project> INSTANCE_KEY = ServiceManager.createLazyKey(InferredAnnotationsManager.class);

	public static InferredAnnotationsManager getInstance(@Nonnull Project project)
	{
		return INSTANCE_KEY.getValue(project);
	}

	/**
	 * @return if exists, an inferred annotation by given qualified name on a given PSI element. Several invocations may return several
	 * different instances of {@link PsiAnnotation}, which are not guaranteed to be equal.
	 */
	@Nullable
	public abstract PsiAnnotation findInferredAnnotation(@Nonnull PsiModifierListOwner listOwner, @Nonnull String annotationFQN);

	/**
	 * When annotation name is known, prefer {@link #findInferredAnnotation(PsiModifierListOwner, String)} as
	 * potentially faster.
	 *
	 * @return all inferred annotations for the given element
	 */
	@Nonnull
	public abstract PsiAnnotation[] findInferredAnnotations(@Nonnull PsiModifierListOwner listOwner);

	/**
	 * @return whether the given annotation was inferred by this service.
	 * @see AnnotationUtil#isInferredAnnotation(PsiAnnotation)
	 */
	public abstract boolean isInferredAnnotation(@Nonnull PsiAnnotation annotation);
}
