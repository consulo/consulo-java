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
package com.intellij.java.impl.ig.encapsulation;

import com.intellij.java.impl.ig.psiutils.HighlightUtils;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.CollectionUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

@ExtensionImpl
public class ReturnOfCollectionFieldInspection extends BaseInspection {
    /**
     * @noinspection PublicField
     */
    public boolean ignorePrivateMethods = true;

    @Nonnull
    @Override
    @Pattern(VALID_ID_PATTERN)
    public String getID() {
        return "ReturnOfCollectionOrArrayField";
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.returnOfCollectionArrayFieldDisplayName();
    }

    @Override
    @Nullable
    public JComponent createOptionsPanel() {
        LocalizeValue message = InspectionGadgetsLocalize.returnOfCollectionArrayFieldOption();
        return new SingleCheckboxOptionsPanel(message.get(), this, "ignorePrivateMethods");
    }

    @Override
    @Nonnull
    public String buildErrorString(Object... infos) {
        PsiField field = (PsiField) infos[0];
        PsiType type = field.getType();
        return type instanceof PsiArrayType
            ? InspectionGadgetsLocalize.returnOfCollectionArrayFieldProblemDescriptorArray().get()
            : InspectionGadgetsLocalize.returnOfCollectionArrayFieldProblemDescriptorCollection().get();
    }

    @Override
    @Nullable
    protected InspectionGadgetsFix buildFix(Object... infos) {
        PsiReferenceExpression referenceExpression = (PsiReferenceExpression) infos[1];
        String text = referenceExpression.getText();
        if (TypeUtils.expressionHasTypeOrSubtype(referenceExpression, CommonClassNames.JAVA_UTIL_MAP)) {
            if (TypeUtils.expressionHasTypeOrSubtype(referenceExpression, CommonClassNames.JAVA_UTIL_SORTED_MAP)) {
                return new ReturnOfCollectionFieldFix(
                    CommonClassNames.JAVA_UTIL_COLLECTIONS + ".unmodifiableSortedMap(" + text + ')',
                    CommonClassNames.JAVA_UTIL_SORTED_MAP
                );
            }
            return new ReturnOfCollectionFieldFix(
                CommonClassNames.JAVA_UTIL_COLLECTIONS + ".unmodifiableMap(" + text + ')',
                CommonClassNames.JAVA_UTIL_MAP
            );
        }
        else if (TypeUtils.expressionHasTypeOrSubtype(referenceExpression, CommonClassNames.JAVA_UTIL_COLLECTION)) {
            if (TypeUtils.expressionHasTypeOrSubtype(referenceExpression, CommonClassNames.JAVA_UTIL_SET)) {
                if (TypeUtils.expressionHasTypeOrSubtype(referenceExpression, CommonClassNames.JAVA_UTIL_SORTED_SET)) {
                    return new ReturnOfCollectionFieldFix(
                        CommonClassNames.JAVA_UTIL_COLLECTIONS + ".unmodifiableSortedSet(" + text + ')',
                        CommonClassNames.JAVA_UTIL_SORTED_SET
                    );
                }
                return new ReturnOfCollectionFieldFix(
                    CommonClassNames.JAVA_UTIL_COLLECTIONS + ".unmodifiableSet(" + text + ')',
                    CommonClassNames.JAVA_UTIL_SET
                );
            }
            else if (TypeUtils.expressionHasTypeOrSubtype(referenceExpression, CommonClassNames.JAVA_UTIL_LIST)) {
                return new ReturnOfCollectionFieldFix(
                    CommonClassNames.JAVA_UTIL_COLLECTIONS + ".unmodifiableList(" + text + ')',
                    CommonClassNames.JAVA_UTIL_LIST
                );
            }
            return new ReturnOfCollectionFieldFix(
                CommonClassNames.JAVA_UTIL_COLLECTIONS + ".unmodifiableCollection(" + text + ')',
                CommonClassNames.JAVA_UTIL_COLLECTION
            );
        }
        return null;
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new ReturnOfCollectionFieldVisitor();
    }

    private static class ReturnOfCollectionFieldFix extends InspectionGadgetsFix {

        private final String myReplacementText;
        private final String myQualifiedClassName;

        ReturnOfCollectionFieldFix(@NonNls String replacementText, String qualifiedClassName) {
            myReplacementText = replacementText;
            myQualifiedClassName = qualifiedClassName;
        }

        @Nonnull
        @Override
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.returnOfCollectionFieldQuickfix(myReplacementText);
        }

        @Override
        protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
            PsiElement element = descriptor.getPsiElement();
            if (!(element instanceof PsiReferenceExpression)) {
                return;
            }
            PsiReferenceExpression referenceExpression = (PsiReferenceExpression) element;
            fixContainingMethodReturnType(referenceExpression);
            replaceExpressionAndShorten(referenceExpression, myReplacementText);
        }

        private void fixContainingMethodReturnType(PsiReferenceExpression referenceExpression) {
            PsiMethod method = PsiTreeUtil.getParentOfType(referenceExpression, PsiMethod.class, true);
            if (method == null) {
                return;
            }
            PsiTypeElement returnTypeElement = method.getReturnTypeElement();
            if (returnTypeElement == null) {
                return;
            }
            PsiType type = returnTypeElement.getType();
            if (!InheritanceUtil.isInheritor(type, myQualifiedClassName)) {
                return;
            }
            if (!(type instanceof PsiClassType)) {
                return;
            }
            Project project = referenceExpression.getProject();
            PsiClassType classType = (PsiClassType) type;
            PsiClass aClass = classType.resolve();
            if (aClass == null || myQualifiedClassName.equals(aClass.getQualifiedName())) {
                return;
            }
            PsiType[] parameters = classType.getParameters();
            PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
            String typeText;
            if (parameters.length > 0) {
                StringBuilder builder = new StringBuilder(myQualifiedClassName);
                builder.append('<');
                boolean comma = false;
                for (PsiType parameter : parameters) {
                    if (comma) {
                        builder.append(',');
                    }
                    else {
                        comma = true;
                    }
                    builder.append(parameter.getCanonicalText());
                }
                builder.append('>');
                typeText = builder.toString();
            }
            else {
                typeText = myQualifiedClassName;
            }
            PsiTypeElement newTypeElement = factory.createTypeElementFromText(typeText, referenceExpression);
            PsiElement replacement = returnTypeElement.replace(newTypeElement);
            JavaCodeStyleManager javaCodeStyleManager = JavaCodeStyleManager.getInstance(project);
            javaCodeStyleManager.shortenClassReferences(replacement);
            HighlightUtils.highlightElement(replacement);
        }
    }

    private class ReturnOfCollectionFieldVisitor extends BaseInspectionVisitor {

        @Override
        public void visitReturnStatement(@Nonnull PsiReturnStatement statement) {
            super.visitReturnStatement(statement);
            PsiExpression returnValue = statement.getReturnValue();
            if (returnValue == null) {
                return;
            }
            PsiMethod containingMethod = PsiTreeUtil.getParentOfType(statement, PsiMethod.class);
            if (containingMethod == null) {
                return;
            }
            if (ignorePrivateMethods && containingMethod.hasModifierProperty(PsiModifier.PRIVATE)) {
                return;
            }
            PsiClass returnStatementClass = containingMethod.getContainingClass();
            if (returnStatementClass == null) {
                return;
            }
            if (!(returnValue instanceof PsiReferenceExpression)) {
                return;
            }
            PsiReferenceExpression referenceExpression = (PsiReferenceExpression) returnValue;
            PsiElement referent = referenceExpression.resolve();
            if (!(referent instanceof PsiField)) {
                return;
            }
            PsiField field = (PsiField) referent;
            PsiClass fieldClass = field.getContainingClass();
            if (!returnStatementClass.equals(fieldClass)) {
                return;
            }
            if (!CollectionUtils.isArrayOrCollectionField(field)) {
                return;
            }
            registerError(returnValue, field, returnValue);
        }
    }
}