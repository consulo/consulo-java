/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.cloneable;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.CloneUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.intellij.lang.annotations.Pattern;

import javax.swing.*;

@ExtensionImpl
public class CloneableImplementsCloneInspection extends BaseInspection {
    /**
     * @noinspection PublicField
     */
    public boolean m_ignoreCloneableDueToInheritance = false;

    @Nonnull
    @Override
    @Pattern(VALID_ID_PATTERN)
    public String getID() {
        return "CloneableClassWithoutClone";
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.cloneableClassWithoutCloneDisplayName();
    }

    @Override
    @Nonnull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.cloneableClassWithoutCloneProblemDescriptor().get();
    }

    @Override
    public JComponent createOptionsPanel() {
        LocalizeValue message = InspectionGadgetsLocalize.cloneableClassWithoutCloneIgnoreOption();
        return new SingleCheckboxOptionsPanel(message.get(), this, "m_ignoreCloneableDueToInheritance");
    }

    @Override
    protected InspectionGadgetsFix buildFix(Object... infos) {
        return new CreateCloneMethodFix();
    }

    private static class CreateCloneMethodFix extends InspectionGadgetsFix {
        @Nonnull
        @Override
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.cloneableClassWithoutCloneQuickfix();
        }

        @Override
        protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
            final PsiElement element = descriptor.getPsiElement();
            final PsiElement parent = element.getParent();
            if (!(parent instanceof PsiClass)) {
                return;
            }
            final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
            final StringBuilder cloneMethod = new StringBuilder("public ");
            cloneMethod.append(element.getText());
            cloneMethod.append(" clone() throws java.lang.CloneNotSupportedException {\nreturn (");
            cloneMethod.append(element.getText());
            cloneMethod.append(") super.clone();\n}");
            final PsiMethod method = factory.createMethodFromText(cloneMethod.toString(), element);
            parent.add(method);
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new CloneableImplementsCloneVisitor();
    }

    private class CloneableImplementsCloneVisitor extends BaseInspectionVisitor {

        @Override
        public void visitClass(@Nonnull PsiClass aClass) {
            // no call to super, so it doesn't drill down
            if (aClass.isInterface() || aClass.isAnnotationType() || aClass.isEnum()) {
                return;
            }
            if (aClass instanceof PsiTypeParameter) {
                return;
            }
            if (m_ignoreCloneableDueToInheritance) {
                if (!CloneUtils.isDirectlyCloneable(aClass)) {
                    return;
                }
            }
            else if (!CloneUtils.isCloneable(aClass)) {
                return;
            }
            final PsiMethod[] methods = aClass.getMethods();
            for (final PsiMethod method : methods) {
                if (CloneUtils.isClone(method)) {
                    return;
                }
            }
            registerClassError(aClass);
        }
    }
}