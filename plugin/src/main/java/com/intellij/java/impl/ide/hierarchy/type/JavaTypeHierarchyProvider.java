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
package com.intellij.java.impl.ide.hierarchy.type;

import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.PsiAnonymousClass;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiClassOwner;
import com.intellij.java.language.psi.PsiSyntheticClass;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorKeys;
import consulo.dataContext.DataContext;
import consulo.language.Language;
import consulo.language.editor.TargetElementUtil;
import consulo.language.editor.TargetElementUtilExtender;
import consulo.language.editor.hierarchy.HierarchyKind;
import consulo.language.editor.hierarchy.HierarchyModel;
import consulo.language.editor.hierarchy.HierarchyProvider;
import consulo.language.editor.hierarchy.StandardHierarchyKinds;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.project.Project;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * @author yole
 */
@ExtensionImpl
public class JavaTypeHierarchyProvider implements HierarchyProvider<PsiClass> {
    @Override
    public HierarchyKind getKind() {
        return StandardHierarchyKinds.TYPE;
    }

    @Override
    @RequiredReadAction
    public @Nullable PsiClass getTarget(DataContext dataContext) {
        Project project = dataContext.getData(Project.KEY);
        if (project == null) {
            return null;
        }

        Editor editor = dataContext.getData(EditorKeys.EDITOR_SNAPSHOT);
        if (editor != null) {
            PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
            if (file == null) {
                return null;
            }

            PsiElement targetElement = TargetElementUtil.findTargetElement(
                editor,
                Set.of(
                    TargetElementUtilExtender.ELEMENT_NAME_ACCEPTED,
                    TargetElementUtilExtender.REFERENCED_ELEMENT_ACCEPTED,
                    TargetElementUtilExtender.LOOKUP_ITEM_ACCEPTED
                )
            );
            if (targetElement instanceof PsiClass psiClass) {
                return psiClass;
            }

            int offset = editor.getCaretModel().getOffset();
            PsiElement element = file.findElementAt(offset);
            while (element != null) {
                if (element instanceof PsiFile) {
                    if (!(element instanceof PsiClassOwner classOwner)) {
                        return null;
                    }
                    PsiClass[] classes = classOwner.getClasses();
                    return classes.length == 1 ? classes[0] : null;
                }
                if (element instanceof PsiClass psiClass
                    && !(element instanceof PsiAnonymousClass)
                    && !(element instanceof PsiSyntheticClass)) {
                    return psiClass;
                }
                element = element.getParent();
            }

            return null;
        }
        else {
            PsiElement element = dataContext.getData(PsiElement.KEY);
            return element instanceof PsiClass psiClass ? psiClass : null;
        }
    }

    @Override
    @RequiredReadAction
    public HierarchyModel<PsiClass> createModel(Project project, PsiClass target) {
        return new JavaTypeHierarchyModel(project, target);
    }

    @Override
    public Language getLanguage() {
        return JavaLanguage.INSTANCE;
    }
}
