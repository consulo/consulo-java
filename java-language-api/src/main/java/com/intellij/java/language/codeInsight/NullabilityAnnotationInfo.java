// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.codeInsight;

import com.intellij.java.language.psi.PsiAnnotation;
import jakarta.annotation.Nonnull;

/**
 * Represents a particular nullability annotation instance
 */
public class NullabilityAnnotationInfo
{
	private final
	@jakarta.annotation.Nonnull
	PsiAnnotation myAnnotation;
	private final
	@jakarta.annotation.Nonnull
	Nullability myNullability;
	private final boolean myContainer;

	public NullabilityAnnotationInfo(@jakarta.annotation.Nonnull PsiAnnotation annotation, @jakarta.annotation.Nonnull Nullability nullability, boolean container)
	{
		myAnnotation = annotation;
		myNullability = nullability;
		myContainer = container;
	}

	/**
	 * @return annotation object (might be synthetic)
	 */
	@jakarta.annotation.Nonnull
	public PsiAnnotation getAnnotation()
	{
		return myAnnotation;
	}

	/**
	 * @return nullability this annotation represents
	 */
	@Nonnull
	public Nullability getNullability()
	{
		return myNullability;
	}

	/**
	 * @return true if this annotation is a container annotation (applied to the whole class/package/etc.)
	 */
	public boolean isContainer()
	{
		return myContainer;
	}

	/**
	 * @return true if this annotation is an external annotation
	 */
	public boolean isExternal()
	{
		return AnnotationUtil.isExternalAnnotation(myAnnotation);
	}

	/**
	 * @return true if this annotation is an inferred annotation
	 */
	public boolean isInferred()
	{
		return AnnotationUtil.isInferredAnnotation(myAnnotation);
	}

	@Override
	public String toString()
	{
		return "NullabilityAnnotationInfo{" +
				myNullability + "(" + myAnnotation.getQualifiedName() + ")" +
				(myContainer ? ", container=" : "") +
				"}";
	}
}
