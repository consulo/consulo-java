/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.java.impl.refactoring.util.duplicates;

import java.util.List;

import consulo.localize.LocalizeValue;
import jakarta.annotation.Nullable;

import com.intellij.java.analysis.impl.refactoring.util.duplicates.Match;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;

/**
 * @author dsl
 */
public interface MatchProvider
{
	PsiElement processMatch(Match match) throws IncorrectOperationException;

	List<Match> getDuplicates();

	/**
	 * @return null if no confirmation prompt is expected
	 */
	@Nullable
	Boolean hasDuplicates();

	@Nullable
	String getConfirmDuplicatePrompt(Match match);

	LocalizeValue getReplaceDuplicatesTitle(int idx, int size);
}
