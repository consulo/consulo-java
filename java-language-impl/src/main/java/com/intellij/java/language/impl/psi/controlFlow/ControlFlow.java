/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.java.language.impl.psi.controlFlow;

import consulo.language.psi.PsiElement;
import jakarta.annotation.Nonnull;

import java.util.List;

public interface ControlFlow
{
	ControlFlow EMPTY = new ControlFlowImpl();

	@jakarta.annotation.Nonnull
	List<Instruction> getInstructions();

	int getSize();

	int getStartOffset(@Nonnull PsiElement element);

	int getEndOffset(@jakarta.annotation.Nonnull PsiElement element);

	PsiElement getElement(int offset);

	// true means there is at least one place where control flow has been short-circuited due to constant condition
	// false means no constant conditions were detected affecting control flow
	boolean isConstantConditionOccurred();
}