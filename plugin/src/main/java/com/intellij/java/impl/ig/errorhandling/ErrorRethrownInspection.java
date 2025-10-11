/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.errorhandling;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.intellij.lang.annotations.Pattern;

@ExtensionImpl
public class ErrorRethrownInspection extends BaseInspection {
    @Nonnull
    @Override
    @Pattern(VALID_ID_PATTERN)
    public String getID() {
        return "ErrorNotRethrown";
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.errorRethrownDisplayName();
    }

    @Override
    @Nonnull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.errorRethrownProblemDescriptor().get();
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new ErrorRethrownVisitor();
    }

    private static class ErrorRethrownVisitor extends BaseInspectionVisitor {
        @Override
        public void visitTryStatement(@Nonnull PsiTryStatement statement) {
            super.visitTryStatement(statement);
            final PsiCatchSection[] catchSections = statement.getCatchSections();
            for (PsiCatchSection catchSection : catchSections) {
                final PsiParameter parameter = catchSection.getParameter();
                final PsiCodeBlock catchBlock = catchSection.getCatchBlock();
                if (parameter != null && catchBlock != null) {
                    checkCatchBlock(parameter, catchBlock);
                }
            }
        }

        private void checkCatchBlock(PsiParameter parameter, PsiCodeBlock catchBlock) {
            final PsiType type = parameter.getType();
            final PsiClass aClass = PsiUtil.resolveClassInType(type);
            if (aClass == null) {
                return;
            }
            if (!InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_LANG_ERROR)) {
                return;
            }
            if (TypeUtils.typeEquals("java.lang.ThreadDeath", type)) {
                return;
            }
            final PsiTypeElement typeElement = parameter.getTypeElement();
            final PsiStatement[] statements = catchBlock.getStatements();
            if (statements.length == 0) {
                registerError(typeElement);
                return;
            }
            final PsiStatement lastStatement =
                statements[statements.length - 1];
            if (!(lastStatement instanceof PsiThrowStatement)) {
                registerError(typeElement);
                return;
            }
            final PsiThrowStatement throwStatement = (PsiThrowStatement) lastStatement;
            final PsiExpression exception = throwStatement.getException();
            if (!(exception instanceof PsiReferenceExpression)) {
                registerError(typeElement);
                return;
            }
            final PsiElement element = ((PsiReference) exception).resolve();
            if (parameter.equals(element)) {
                return;
            }
            registerError(typeElement);
        }
    }
}