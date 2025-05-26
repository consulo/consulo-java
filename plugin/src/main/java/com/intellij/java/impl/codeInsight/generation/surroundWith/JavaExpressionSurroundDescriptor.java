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
package com.intellij.java.impl.codeInsight.generation.surroundWith;

import com.intellij.java.impl.codeInsight.CodeInsightUtil;
import com.intellij.java.impl.refactoring.introduceVariable.IntroduceVariableBase;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.PsiExpression;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.Application;
import consulo.externalService.statistic.FeatureUsageTracker;
import consulo.language.Language;
import consulo.language.editor.surroundWith.SurroundDescriptor;
import consulo.language.editor.surroundWith.Surrounder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import jakarta.annotation.Nonnull;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author ven
 */
@ExtensionImpl
public class JavaExpressionSurroundDescriptor implements SurroundDescriptor {
    private final Application myApplication;
    private Surrounder[] mySurrounders = null;

    private static final Surrounder[] SURROUNDERS = {
        new JavaWithParenthesesSurrounder(),
        new JavaWithCastSurrounder(),
        new JavaWithNotSurrounder(),
        new JavaWithNotInstanceofSurrounder(),
        new JavaWithIfExpressionSurrounder(),
        new JavaWithIfElseExpressionSurrounder(),
        new JavaWithNullCheckSurrounder()
    };

    @Inject
    public JavaExpressionSurroundDescriptor(Application application) {
        myApplication = application;
    }

    @Nonnull
    @Override
    @RequiredReadAction
    public PsiElement[] getElementsToSurround(PsiFile file, int startOffset, int endOffset) {
        PsiExpression expr = CodeInsightUtil.findExpressionInRange(file, startOffset, endOffset);
        if (expr == null) {
            expr = IntroduceVariableBase.getSelectedExpression(file.getProject(), file, startOffset, endOffset);
            if (expr == null) {
                return PsiElement.EMPTY_ARRAY;
            }
        }
        FeatureUsageTracker.getInstance().triggerFeatureUsed("codeassists.surroundwith.expression");
        return new PsiElement[]{expr};
    }

    @Override
    @Nonnull
    public Surrounder[] getSurrounders() {
        if (mySurrounders == null) {
            List<Surrounder> list = new ArrayList<>();
            Collections.addAll(list, SURROUNDERS);
            list.addAll(myApplication.getExtensionList(JavaExpressionSurrounder.class));
            mySurrounders = list.toArray(new Surrounder[list.size()]);
        }
        return mySurrounders;
    }

    @Override
    public boolean isExclusive() {
        return false;
    }

    @Nonnull
    @Override
    public Language getLanguage() {
        return JavaLanguage.INSTANCE;
    }
}
