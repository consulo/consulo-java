/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.java.language.psi.javadoc;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

import consulo.language.psi.PsiDocCommentBase;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiJavaDocumentedElement;

/**
 * Represents a JavaDoc comment.
 */
public interface PsiDocComment extends PsiDocCommentBase
{
	/**
	 * Returns the class, method or field described by the comment.
	 */
	@Override
	@Nullable
	PsiJavaDocumentedElement getOwner();

	/**
	 * Returns the PSI elements containing the description of the element being documented
	 * (all significant tokens up to the first doc comment tag).
	 */
	@Nonnull
	PsiElement[] getDescriptionElements();

	/**
	 * Returns the list of JavaDoc tags in the comment.
	 */
	@Nonnull
	PsiDocTag[] getTags();

	/**
	 * Finds the first JavaDoc tag with the specified name.
	 *
	 * @param name The name of the tags to find (not including the leading @ character).
	 * @return the tag with the specified name, or null if not found.
	 */
	@Nullable
	PsiDocTag findTagByName(@NonNls String name);

	/**
	 * Finds all JavaDoc tags with the specified name.
	 *
	 * @param name The name of the tags to find (not including the leading @ character).
	 */
	@Nonnull
	PsiDocTag[] findTagsByName(@NonNls String name);
}