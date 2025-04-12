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
package com.intellij.java.impl.analysis;

import com.intellij.java.language.psi.PsiJavaFile;
import com.intellij.java.language.psi.PsiJavaPackage;
import consulo.annotation.component.ExtensionImpl;
import consulo.dataContext.DataProvider;
import consulo.dataContext.GetDataRule;
import consulo.language.editor.scope.AnalysisScope;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.module.Module;
import consulo.util.dataholder.Key;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class AnalysisScopeRule implements GetDataRule<AnalysisScope> {
    @Nonnull
    @Override
    public Key<AnalysisScope> getKey() {
        return AnalysisScope.KEY;
    }

    @Override
    public AnalysisScope getData(@Nonnull DataProvider dataProvider) {
        Object psiFile = dataProvider.getDataUnchecked(PsiFile.KEY);
        if (psiFile instanceof PsiJavaFile javaFile) {
            return new JavaAnalysisScope(javaFile);
        }
        Object psiTarget = dataProvider.getDataUnchecked(PsiElement.KEY);
        if (psiTarget instanceof PsiJavaPackage pack) {
            PsiManager manager = pack.getManager();
            if (!manager.isInProject(pack)) {
                return null;
            }
            PsiDirectory[] dirs = pack.getDirectories(GlobalSearchScope.projectScope(manager.getProject()));
            if (dirs.length == 0) {
                return null;
            }
            return new JavaAnalysisScope(pack, dataProvider.getDataUnchecked(Module.KEY));
        }
        return null;
    }
}