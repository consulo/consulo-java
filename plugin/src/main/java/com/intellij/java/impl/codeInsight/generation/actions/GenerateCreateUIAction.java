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
package com.intellij.java.impl.codeInsight.generation.actions;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiTypesUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ActionImpl;
import consulo.java.localize.JavaLocalize;

/**
 * @author Konstantin Bulenkov
 */
@ActionImpl(id = "GenerateCreateUI")
public class GenerateCreateUIAction extends BaseGenerateAction {
    public GenerateCreateUIAction() {
        super(new GenerateCreateUIHandler(), JavaLocalize.actionGeneratecreateuiText());
    }

    @Override
    @RequiredReadAction
    protected boolean isValidForClass(PsiClass targetClass) {
        return !targetClass.isAbstract()
            && !hasCreateUIMethod(targetClass)
            && isComponentUI(targetClass);
    }

    private static boolean hasCreateUIMethod(PsiClass aClass) {
        for (PsiMethod method : aClass.findMethodsByName("createUI", false)) {
            if (method.isStatic()) {
                PsiParameter[] parameters = method.getParameterList().getParameters();
                if (parameters.length == 1) {
                    PsiType type = parameters[0].getType();
                    PsiClass typeClass = PsiTypesUtil.getPsiClass(type);
                    return typeClass != null && "javax.swing.JComponent".equals(typeClass.getQualifiedName());
                }
            }
        }
        return false;
    }

    private static boolean isComponentUI(PsiClass aClass) {
        while (aClass != null) {
            if ("javax.swing.plaf.ComponentUI".equals(aClass.getQualifiedName())) {
                return true;
            }
            aClass = aClass.getSuperClass();
        }
        return false;
    }
}
