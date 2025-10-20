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
package com.intellij.java.impl.codeInsight.generation.actions;

import com.intellij.java.impl.codeInsight.generation.GenerateEqualsHandler;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ActionImpl;
import consulo.codeEditor.Editor;
import com.intellij.java.language.psi.PsiAnonymousClass;
import com.intellij.java.language.psi.PsiClass;
import consulo.java.localize.JavaLocalize;
import consulo.language.psi.PsiFile;

/**
 * @author dsl
 */
@ActionImpl(id = "GenerateEquals")
public class GenerateEqualsAction extends BaseGenerateAction {
    public GenerateEqualsAction() {
        super(new GenerateEqualsHandler(), JavaLocalize.actionGenerateequalsText());
    }

    @Override
    @RequiredReadAction
    protected PsiClass getTargetClass(Editor editor, PsiFile file) {
        PsiClass targetClass = super.getTargetClass(editor, file);
        if (targetClass == null || targetClass instanceof PsiAnonymousClass || targetClass.isEnum()) {
            return null;
        }
        return targetClass;
    }
}
