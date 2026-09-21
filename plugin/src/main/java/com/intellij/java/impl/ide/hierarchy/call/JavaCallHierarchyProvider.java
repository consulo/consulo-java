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
package com.intellij.java.impl.ide.hierarchy.call;

import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.PsiMethod;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.dataContext.DataContext;
import consulo.language.Language;
import consulo.language.editor.hierarchy.HierarchyKind;
import consulo.language.editor.hierarchy.HierarchyModel;
import consulo.language.editor.hierarchy.HierarchyProvider;
import consulo.language.editor.hierarchy.StandardHierarchyKinds;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import org.jspecify.annotations.Nullable;

/**
 * @author yole
 */
@ExtensionImpl
public class JavaCallHierarchyProvider implements HierarchyProvider<PsiMethod> {
    @Override
    public HierarchyKind getKind() {
        return StandardHierarchyKinds.CALL;
    }

    @Override
    @RequiredReadAction
    public @Nullable PsiMethod getTarget(DataContext dataContext) {
        Project project = dataContext.getData(Project.KEY);
        if (project == null) {
            return null;
        }

        PsiElement element = dataContext.getData(PsiElement.KEY);
        return PsiTreeUtil.getParentOfType(element, PsiMethod.class, false);
    }

    @Override
    @RequiredReadAction
    public HierarchyModel<PsiMethod> createModel(Project project, PsiMethod target) {
        return new JavaCallHierarchyModel(project, target);
    }

    @Override
    public Language getLanguage() {
        return JavaLanguage.INSTANCE;
    }
}
