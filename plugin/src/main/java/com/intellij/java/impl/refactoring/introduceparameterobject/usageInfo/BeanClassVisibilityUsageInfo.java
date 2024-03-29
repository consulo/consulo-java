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

/*
 * User: anna
 * Date: 03-Nov-2009
 */
package com.intellij.java.impl.refactoring.introduceparameterobject.usageInfo;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.impl.refactoring.util.FixableUsageInfo;
import consulo.usage.UsageInfo;
import consulo.usage.UsageViewUtil;
import consulo.language.util.IncorrectOperationException;
import com.intellij.java.language.util.VisibilityUtil;

public class BeanClassVisibilityUsageInfo extends FixableUsageInfo
{
	private final PsiClass existingClass;
	private final UsageInfo[] usages;
	private final String myNewVisibility;
	private final PsiMethod myExistingClassCompatibleConstructor;

	public BeanClassVisibilityUsageInfo(PsiClass existingClass, UsageInfo[] usages, String newVisibility, PsiMethod existingClassCompatibleConstructor)
	{
		super(existingClass);
		this.existingClass = existingClass;
		this.usages = usages;
		myNewVisibility = newVisibility;
		myExistingClassCompatibleConstructor = existingClassCompatibleConstructor;
	}

	@Override
	public void fixUsage() throws IncorrectOperationException
	{
		VisibilityUtil.fixVisibility(UsageViewUtil.toElements(usages), existingClass, myNewVisibility);
		if(myExistingClassCompatibleConstructor != null)
		{
			VisibilityUtil.fixVisibility(UsageViewUtil.toElements(usages), myExistingClassCompatibleConstructor, myNewVisibility);
		}
	}
}
