/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.java.impl.ig.junit;

import com.intellij.java.language.psi.PsiClass;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TestUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class TestCaseInProductCodeInspectionBase extends BaseInspection
{
	@Override
	@Nonnull
	public String getDisplayName()
	{
		return InspectionGadgetsLocalize.testCaseInProductCodeDisplayName().get();
	}

	@Override
	@Nonnull
	public String getID()
	{
		return "JUnitTestCaseInProductSource";
	}

	@Override
	@Nonnull
	protected String buildErrorString(Object... infos)
	{
		return InspectionGadgetsLocalize.testCaseInProductCodeProblemDescriptor().get();
	}

	@Override
	protected boolean buildQuickFixesOnlyForOnTheFlyErrors()
	{
		return true;
	}

	@Override
	public BaseInspectionVisitor buildVisitor()
	{
		return new TestCaseInProductCodeVisitor();
	}

	private static class TestCaseInProductCodeVisitor extends BaseInspectionVisitor
	{

		@Override
		public void visitClass(@Nonnull PsiClass aClass)
		{
			if(TestUtils.isInTestSourceContent(aClass) || !TestUtils.isJUnitTestClass(aClass))
			{
				return;
			}
			registerClassError(aClass);
		}
	}
}
