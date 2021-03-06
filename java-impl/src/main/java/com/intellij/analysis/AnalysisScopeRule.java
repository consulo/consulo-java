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
 * Date: 15-Jan-2008
 */
package com.intellij.analysis;

import javax.annotation.Nonnull;
import com.intellij.ide.impl.dataRules.GetDataRule;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.LangDataKeys;
import consulo.util.dataholder.Key;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiJavaPackage;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;

public class AnalysisScopeRule implements GetDataRule<AnalysisScope>
{
	@Nonnull
	@Override
	public Key<AnalysisScope> getKey()
	{
		return AnalysisScopeUtil.KEY;
	}

	@Override
	public AnalysisScope getData(@Nonnull final DataProvider dataProvider)
	{
		final Object psiFile = dataProvider.getDataUnchecked(LangDataKeys.PSI_FILE);
		if(psiFile instanceof PsiJavaFile)
		{
			return new JavaAnalysisScope((PsiJavaFile) psiFile);
		}
		Object psiTarget = dataProvider.getDataUnchecked(LangDataKeys.PSI_ELEMENT);
		if(psiTarget instanceof PsiJavaPackage)
		{
			PsiJavaPackage pack = (PsiJavaPackage) psiTarget;
			PsiManager manager = pack.getManager();
			if(!manager.isInProject(pack))
			{
				return null;
			}
			PsiDirectory[] dirs = pack.getDirectories(GlobalSearchScope.projectScope(manager.getProject()));
			if(dirs.length == 0)
			{
				return null;
			}
			return new JavaAnalysisScope(pack, dataProvider.getDataUnchecked(LangDataKeys.MODULE));
		}
		return null;
	}
}