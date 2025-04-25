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
package com.intellij.java.impl.ide.hierarchy.method;

import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.java.language.psi.PsiJavaToken;
import com.intellij.java.language.psi.PsiMethod;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.dataContext.DataContext;
import consulo.ide.impl.idea.ide.hierarchy.MethodHierarchyBrowserBase;
import consulo.language.Language;
import consulo.language.editor.hierarchy.HierarchyBrowser;
import consulo.language.editor.hierarchy.MethodHierarchyProvider;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author yole
 */
@ExtensionImpl
public class JavaMethodHierarchyProvider implements MethodHierarchyProvider {
    @Override
    @RequiredReadAction
    public PsiElement getTarget(@Nonnull DataContext dataContext) {
        PsiMethod method = getMethodImpl(dataContext);
        if (method != null && method.getContainingClass() != null && !method.isPrivate() && !method.isStatic()) {
            return method;
        }
        return null;
    }

    @RequiredReadAction
    @Nullable
    private static PsiMethod getMethodImpl(DataContext dataContext) {
        Project project = dataContext.getData(Project.KEY);
        if (project == null) {
            return null;
        }

        PsiElement element = dataContext.getData(PsiElement.KEY);
        PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class, false);

        if (method != null) {
            return method;
        }

        Editor editor = dataContext.getData(Editor.KEY);
        if (editor == null) {
            return null;
        }

        PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
        if (psiFile == null) {
            return null;
        }

        PsiDocumentManager.getInstance(project).commitAllDocuments();

        int offset = editor.getCaretModel().getOffset();
        if (offset < 1) {
            return null;
        }

        element = psiFile.findElementAt(offset);
        if (!(element instanceof PsiWhiteSpace)) {
            return null;
        }

        element = psiFile.findElementAt(offset - 1);
        if (!(element instanceof PsiJavaToken javaToken && javaToken.getTokenType() == JavaTokenType.SEMICOLON)) {
            return null;
        }

        return PsiTreeUtil.getParentOfType(element, PsiMethod.class, false);
    }

    @Nonnull
    @Override
    public HierarchyBrowser createHierarchyBrowser(PsiElement target) {
        return new MethodHierarchyBrowser(target.getProject(), (PsiMethod)target);
    }

    @Override
    @RequiredReadAction
    public void browserActivated(@Nonnull HierarchyBrowser hierarchyBrowser) {
        ((MethodHierarchyBrowser)hierarchyBrowser).changeView(MethodHierarchyBrowserBase.METHOD_TYPE);
    }

    @Nonnull
    @Override
    public Language getLanguage() {
        return JavaLanguage.INSTANCE;
    }
}
