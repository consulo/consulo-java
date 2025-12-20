/*
 * Copyright 2006-2007 Bas Leijdekkers
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
package com.intellij.java.impl.ig.dataflow;

import com.intellij.java.impl.ig.psiutils.LibraryUtil;
import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.util.dataholder.Key;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;

@ExtensionImpl
public class LawOfDemeterInspection extends BaseInspection {
    @SuppressWarnings({"PublicField"})
    public boolean ignoreLibraryCalls = true;

    private static final Key<Integer> key = Key.create("LawOfDemeterInspection");

    @Nonnull
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.lawOfDemeterDisplayName();
    }

    @Nonnull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.lawOfDemeterProblemDescriptor().get();
    }

    @Nullable
    public JComponent createOptionsPanel() {
        LocalizeValue message = InspectionGadgetsLocalize.lawOfDemeterIgnoreLibraryCallsOption();
        return new SingleCheckboxOptionsPanel(message.get(), this, "ignoreLibraryCalls");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new LawOfDemeterVisitor();
    }

    private class LawOfDemeterVisitor extends BaseInspectionVisitor {

        private static final int threshold = 2;

        @Override
        public void visitMethodCallExpression(
            PsiMethodCallExpression expression
        ) {
            super.visitMethodCallExpression(expression);
            if (ignoreLibraryCalls &&
                LibraryUtil.callOnLibraryMethod(expression)) {
                return;
            }
            expression.putUserData(key, Integer.valueOf(1));
            checkParents(expression, Integer.valueOf(1));
        }

        public void checkParents(PsiExpression expression, Integer count) {
            PsiElement parent = expression.getParent();
            if (parent instanceof PsiLocalVariable) {
                Integer localCount = expression.getUserData(key);
                parent.putUserData(key, localCount);
            }
            else if (parent instanceof PsiAssignmentExpression) {
                PsiAssignmentExpression assignmentExpression =
                    (PsiAssignmentExpression) parent;
                PsiExpression lhs = assignmentExpression.getLExpression();
                if (!(lhs instanceof PsiReferenceExpression)) {
                    return;
                }
                PsiReferenceExpression referenceExpression =
                    (PsiReferenceExpression) lhs;
                PsiElement element = referenceExpression.resolve();
                if (!(element instanceof PsiLocalVariable)) {
                    return;
                }
                Integer localCount = expression.getUserData(key);
                element.putUserData(key, localCount);
            }
            else if (parent instanceof PsiReferenceExpression) {
                PsiElement grandParent = parent.getParent();
                if (!(grandParent instanceof PsiMethodCallExpression)) {
                    return;
                }
                PsiMethodCallExpression methodCallExpression =
                    (PsiMethodCallExpression) grandParent;
                Integer userData = grandParent.getUserData(key);
                if (userData == null) {
                    return;
                }
                int localCount = userData.intValue();
                int newCount = localCount + count.intValue();
                if (newCount == threshold) {
                    registerMethodCallError(methodCallExpression);
                }
                grandParent.putUserData(key, Integer.valueOf(newCount));
                checkParents(methodCallExpression, count);
            }
        }

        @Override
        public void visitReferenceExpression(
            PsiReferenceExpression expression
        ) {
            super.visitReferenceExpression(expression);
            PsiElement parent = expression.getParent();
            if (!(parent instanceof PsiReferenceExpression)) {
                return;
            }
            PsiElement element = expression.resolve();
            if (!(element instanceof PsiLocalVariable)) {
                return;
            }
            Integer count = element.getUserData(key);
            if (count != null) {
                checkParents(expression, count);
            }
        }
    }
}