/*
 * Copyright 2011 Bas Leijdekkers
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
package com.intellij.java.impl.ig.internationalization;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiReferenceExpression;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

import java.util.Map;

public abstract class AbsoluteAlignmentInUserInterfaceInspection extends BaseInspection {
    private static final Map<String, String> GRID_BAG_CONSTANTS = Map.of(
        "NORTHWEST", "FIRST_LINE_START",
        "NORTHEAST", "FIRST_LINE_END",
        "SOUTHWEST", "LAST_LINE_START",
        "SOUTHEAST", "LAST_LINE_END"
    );

    private static final Map<String, String> BORDER_LAYOUT_CONSTANTS = Map.of(
        "NORTH", "PAGE_START",
        "SOUTH", "PAGE_END",
        "EAST", "LINE_END",
        "WEST", "LINE_START"
    );

    private static final Map<String, String> FLOW_LAYOUT_CONSTANTS = Map.of(
        "LEFT", "LEADING",
        "RIGHT", "TRAILING"
    );

    private static final Map<String, String> SCROLL_PANE_CONSTANTS = Map.of(
        "LOWER_LEFT_CORNER", "LOWER_LEADING_CORNER",
        "LOWER_RIGHT_CORNER", "LOWER_TRAILING_CORNER",
        "UPPER_LEFT_CORNER", "UPPER_LEADING_CORNER",
        "UPPER_RIGHT_CORNER", "UPPER_TRAILING_CORNER"
    );

    private static final Map<String, String> BOX_LAYOUT_CONSTANTS = Map.of(
        "X_AXIS", "LINE_AXIS",
        "Y_AXIS", "PAGE_AXIS"
    );

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.absoluteAlignmentInUserInterfaceDisplayName();
    }

    @Nonnull
    @Override
    protected String buildErrorString(Object... infos) {
        String className = (String) infos[0];
        String shortClassName = className.substring(className.lastIndexOf('.') + 1);
        return InspectionGadgetsLocalize.absoluteAlignmentInUserInterfaceProblemDescriptor(shortClassName).get();
    }

    @Override
    protected InspectionGadgetsFix buildFix(Object... infos) {
        return new AbsoluteAlignmentInUserInterfaceFix((String) infos[0], (String) infos[1]);
    }

    private static class AbsoluteAlignmentInUserInterfaceFix extends InspectionGadgetsFix {
        private final String myClassName;
        private final String myReplacement;

        public AbsoluteAlignmentInUserInterfaceFix(String className, String replacement) {
            myClassName = className;
            myReplacement = replacement;
        }

        @Nonnull
        @Override
        public LocalizeValue getName() {
            String shortClassName = myClassName.substring(myClassName.lastIndexOf('.') + 1);
            return InspectionGadgetsLocalize.absoluteAlignmentInUserInterfaceQuickfix(shortClassName, myReplacement);
        }

        @Override
        @RequiredWriteAction
        protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
            PsiElement element = descriptor.getPsiElement();
            if (!(element.getParent() instanceof PsiReferenceExpression referenceExpression)) {
                return;
            }
            replaceExpression(referenceExpression, myClassName + '.' + myReplacement);
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new AbsoluteAlignmentInUserInterfaceVisitor();
    }

    private static class AbsoluteAlignmentInUserInterfaceVisitor extends BaseInspectionVisitor {
        @Override
        @RequiredReadAction
        public void visitReferenceExpression(PsiReferenceExpression expression) {
            super.visitReferenceExpression(expression);
            PsiElement referenceNameElement = expression.getReferenceNameElement();
            if (referenceNameElement == null) {
                return;
            }
            String referenceName = expression.getReferenceName();
            String className;
            String value;
            if (referenceName == null) {
                return;
            }
            else if ((value = GRID_BAG_CONSTANTS.get(referenceName)) != null) {
                className = checkExpression(expression, "java.awt.GridBagConstraints");
            }
            else if ((value = BORDER_LAYOUT_CONSTANTS.get(referenceName)) != null) {
                className = checkExpression(expression, "java.awt.BorderLayout", "java.awt.GridBagConstraints");
            }
            else if ((value = FLOW_LAYOUT_CONSTANTS.get(referenceName)) != null) {
                className = checkExpression(expression, "java.awt.FlowLayout");
            }
            else if ((value = SCROLL_PANE_CONSTANTS.get(referenceName)) != null) {
                className = checkExpression(expression, "javax.swing.ScrollPaneConstants");
            }
            else if ((value = BOX_LAYOUT_CONSTANTS.get(referenceName)) != null) {
                className = checkExpression(expression, "javax.swing.BoxLayout");
            }
            else {
                return;
            }
            if (className == null) {
                return;
            }
            registerError(referenceNameElement, className, value);
        }

        @RequiredReadAction
        private static String checkExpression(PsiReferenceExpression expression, String... classNames) {
            if (!(expression.resolve() instanceof PsiField field)) {
                return null;
            }
            PsiClass containingClass = field.getContainingClass();
            for (String className : classNames) {
                if (InheritanceUtil.isInheritor(containingClass, className)) {
                    return className;
                }
            }
            return null;
        }
    }
}
