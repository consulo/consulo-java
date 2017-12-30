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
package com.intellij.refactoring.rename;

import consulo.java.util.JavaProjectRootsUtil;
import com.intellij.lang.java.JavaRefactoringSupportProvider;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;

public class JavaVetoRenameCondition implements Condition<PsiElement>
{
	@Override
	public boolean value(final PsiElement element)
	{
		return JavaRefactoringSupportProvider.isDisableRefactoringForLightElement(element) ||
				element instanceof PsiJavaFile &&
				//  !JspPsiUtil.isInJspFile(element) &&
				!JavaProjectRootsUtil.isOutsideSourceRoot((PsiFile) element) &&
				((PsiJavaFile) element).getClasses().length > 0;
	}
}