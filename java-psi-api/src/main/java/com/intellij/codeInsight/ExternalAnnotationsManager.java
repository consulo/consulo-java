/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInsight;

import java.util.List;

import javax.annotation.Nonnull;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyKey;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.util.messages.Topic;

/**
 * @authot anna
 * @since 26-Jun-2007
 */
public abstract class ExternalAnnotationsManager
{
	public static final String ANNOTATIONS_XML = "annotations.xml";

	public static final Topic<ExternalAnnotationsListener> TOPIC = Topic.create("external annotations", ExternalAnnotationsListener.class);

	public enum AnnotationPlace
	{
		IN_CODE,
		EXTERNAL,
		NOWHERE
	}

	private static final NotNullLazyKey<ExternalAnnotationsManager, Project> INSTANCE_KEY = ServiceManager.createLazyKey(ExternalAnnotationsManager.class);

	public static ExternalAnnotationsManager getInstance(@Nonnull Project project)
	{
		return INSTANCE_KEY.getValue(project);
	}

	public abstract boolean isExternalAnnotation(@Nonnull PsiAnnotation annotation);

	@javax.annotation.Nullable
	public abstract PsiAnnotation findExternalAnnotation(@Nonnull PsiModifierListOwner listOwner, @Nonnull String annotationFQN);

	// Method used in Kotlin plugin
	public abstract boolean isExternalAnnotationWritable(@Nonnull PsiModifierListOwner listOwner, @Nonnull String annotationFQN);

	@javax.annotation.Nullable
	public abstract PsiAnnotation[] findExternalAnnotations(@Nonnull PsiModifierListOwner listOwner);

	public abstract void annotateExternally(@Nonnull PsiModifierListOwner listOwner,
			@Nonnull String annotationFQName,
			@Nonnull PsiFile fromFile,
			@javax.annotation.Nullable PsiNameValuePair[] value) throws CanceledConfigurationException;

	public abstract boolean deannotate(@Nonnull PsiModifierListOwner listOwner, @Nonnull String annotationFQN);

	// Method used in Kotlin plugin when it is necessary to leave external annotation, but modify its arguments
	public abstract boolean editExternalAnnotation(@Nonnull PsiModifierListOwner listOwner, @Nonnull String annotationFQN, @javax.annotation.Nullable PsiNameValuePair[] value);

	public abstract AnnotationPlace chooseAnnotationsPlace(@Nonnull PsiElement element);

	@javax.annotation.Nullable
	public abstract List<PsiFile> findExternalAnnotationsFiles(@Nonnull PsiModifierListOwner listOwner);

	public static class CanceledConfigurationException extends RuntimeException
	{
		public static final CanceledConfigurationException INSTANCE = new CanceledConfigurationException();

		private CanceledConfigurationException()
		{
		}
	}
}
