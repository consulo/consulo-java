// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiModifierListOwner;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class InferredAnnotationsManagerImpl extends InferredAnnotationsManager
{
	private static final Key<Boolean> INFERRED_ANNOTATION = Key.create("INFERRED_ANNOTATION");
	private final Project myProject;

	public InferredAnnotationsManagerImpl(Project project)
	{
		myProject = project;
	}

	@Nullable
	@Override
	public PsiAnnotation findInferredAnnotation(@Nonnull PsiModifierListOwner listOwner, @Nonnull String annotationFQN)
	{
		for(InferredAnnotationProvider provider : InferredAnnotationProvider.EP_NAME.getExtensionList(myProject))
		{
			PsiAnnotation annotation = provider.findInferredAnnotation(listOwner, annotationFQN);
			if(annotation != null)
			{
				markInferred(annotation);
				return annotation;
			}
		}
		return null;
	}

	@Nonnull
	@Override
	public PsiAnnotation[] findInferredAnnotations(@Nonnull PsiModifierListOwner listOwner)
	{
		List<PsiAnnotation> result = new ArrayList<>();
		for(InferredAnnotationProvider provider : InferredAnnotationProvider.EP_NAME.getExtensionList(myProject))
		{
			List<PsiAnnotation> annotations = provider.findInferredAnnotations(listOwner);
			for(PsiAnnotation annotation : annotations)
			{
				markInferred(annotation);
				result.add(annotation);
			}
		}
		return result.toArray(PsiAnnotation.EMPTY_ARRAY);
	}

	@Override
	public boolean isInferredAnnotation(@Nonnull PsiAnnotation annotation)
	{
		return annotation.getUserData(INFERRED_ANNOTATION) != null;
	}

	private static void markInferred(@Nonnull PsiAnnotation annotation)
	{
		annotation.putUserData(INFERRED_ANNOTATION, Boolean.TRUE);
	}

}
