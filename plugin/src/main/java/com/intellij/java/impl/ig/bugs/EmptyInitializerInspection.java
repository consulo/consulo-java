/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.bugs;

import com.intellij.java.language.psi.PsiClassInitializer;
import com.intellij.java.language.psi.PsiCodeBlock;
import com.intellij.java.language.psi.PsiStatement;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.intellij.lang.annotations.Pattern;

@ExtensionImpl
public class EmptyInitializerInspection extends BaseInspection {
    @Nonnull
    @Override
    @Pattern("[a-zA-Z_0-9.]+")
    public String getID() {
        return "EmptyClassInitializer";
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.emptyClassInitializerDisplayName();
    }

    @Nonnull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.emptyClassInitializerProblemDescriptor().get();
    }

    protected InspectionGadgetsFix buildFix(Object... infos) {
        return new EmptyInitializerFix();
    }

    private static class EmptyInitializerFix extends InspectionGadgetsFix {
        @Nonnull
        @Override
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.emptyClassInitializerDeleteQuickfix();
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
            throws IncorrectOperationException {
            PsiElement element = descriptor.getPsiElement();
            PsiElement codeBlock = element.getParent();
            assert codeBlock != null;
            PsiElement classInitializer = codeBlock.getParent();
            assert classInitializer != null;
            deleteElement(classInitializer);
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new EmptyInitializerVisitor();
    }

    private static class EmptyInitializerVisitor extends BaseInspectionVisitor {

        @Override
        public void visitClassInitializer(
            @Nonnull PsiClassInitializer initializer
        ) {
            super.visitClassInitializer(initializer);
            PsiCodeBlock body = initializer.getBody();
            if (!codeBlockIsEmpty(body)) {
                return;
            }
            registerClassInitializerError(initializer);
        }

        private static boolean codeBlockIsEmpty(PsiCodeBlock codeBlock) {
            PsiStatement[] statements = codeBlock.getStatements();
            return statements.length == 0;
        }
    }
}