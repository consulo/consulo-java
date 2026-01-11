/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInsight.daemon.impl.quickfix;

import com.intellij.java.impl.codeInsight.generation.GetterSetterPrototypeProvider;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.util.PropertyUtil;
import consulo.codeEditor.Editor;
import consulo.java.analysis.impl.localize.JavaQuickFixLocalize;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.intention.LowPriorityAction;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author ven
 */
public class CreateGetterOrSetterFix implements SyntheticIntentionAction, LowPriorityAction {
    private final boolean myCreateGetter;
    private final boolean myCreateSetter;
    private final PsiField myField;
    private final String myPropertyName;

    public CreateGetterOrSetterFix(boolean createGetter, boolean createSetter, @Nonnull PsiField field) {
        myCreateGetter = createGetter;
        myCreateSetter = createSetter;
        myField = field;
        myPropertyName = PropertyUtil.suggestPropertyName(field);
    }

    @Override
    @Nonnull
    public LocalizeValue getText() {
        if (myCreateGetter && myCreateSetter) {
            return JavaQuickFixLocalize.createGetterAndSetterForField(myField.getName());
        }
        else if (myCreateGetter) {
            return JavaQuickFixLocalize.createGetterForField(myField.getName());
        }
        else if (myCreateSetter) {
            return JavaQuickFixLocalize.createSetterForField(myField.getName());
        }
        else {
            return LocalizeValue.empty();
        }
    }

    @Override
    public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
        if (!myField.isValid()) {
            return false;
        }

        PsiClass aClass = myField.getContainingClass();
        if (aClass == null) {
            return false;
        }

        if (myCreateGetter) {
            if (isStaticFinal(myField) || PropertyUtil.findPropertyGetter(aClass, myPropertyName, isStatic(myField), false) != null) {
                return false;
            }
        }

        if (myCreateSetter) {
            if (isFinal(myField) || PropertyUtil.findPropertySetter(aClass, myPropertyName, isStatic(myField), false) != null) {
                return false;
            }
        }

        return true;
    }

    private static boolean isFinal(@Nonnull PsiField field) {
        return field.hasModifierProperty(PsiModifier.FINAL);
    }

    private static boolean isStatic(@Nonnull PsiField field) {
        return field.hasModifierProperty(PsiModifier.STATIC);
    }

    private static boolean isStaticFinal(@Nonnull PsiField field) {
        return isStatic(field) && isFinal(field);
    }

    @Override
    public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        if (!FileModificationService.getInstance().preparePsiElementForWrite(myField)) {
            return;
        }
        PsiClass aClass = myField.getContainingClass();
        List<PsiMethod> methods = new ArrayList<PsiMethod>();
        if (myCreateGetter) {
            Collections.addAll(methods, GetterSetterPrototypeProvider.generateGetterSetters(myField, true));
        }
        if (myCreateSetter) {
            Collections.addAll(methods, GetterSetterPrototypeProvider.generateGetterSetters(myField, false));
        }
        for (PsiMethod method : methods) {
            aClass.add(method);
        }
    }

    @Override
    public boolean startInWriteAction() {
        return true;
    }
}
