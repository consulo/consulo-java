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
package com.intellij.java.impl.ig.fixes;

import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.refactoring.RefactoringQuickFix;
import consulo.project.Project;
import com.siyeh.ig.InspectionGadgetsFix;

/**
 * @author Bas Leijdekkers
 */
public abstract class RefactoringInspectionGadgetsFix extends InspectionGadgetsFix implements RefactoringQuickFix
{

	@Override
	protected void doFix(Project project, ProblemDescriptor descriptor)
	{
		doFix(descriptor.getPsiElement());
	}
}
