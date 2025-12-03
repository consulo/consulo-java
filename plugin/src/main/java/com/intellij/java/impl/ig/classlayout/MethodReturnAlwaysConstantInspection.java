/*
 * Copyright 2006-2011 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.classlayout;

import com.intellij.java.analysis.codeInspection.reference.RefMethod;
import com.intellij.java.impl.ig.BaseGlobalInspection;
import com.intellij.java.impl.ig.psiutils.MethodInheritanceUtils;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.editor.inspection.*;
import consulo.language.editor.inspection.reference.RefEntity;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.language.editor.scope.AnalysisScope;
import consulo.localize.LocalizeValue;
import consulo.util.dataholder.Key;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public abstract class MethodReturnAlwaysConstantInspection extends BaseGlobalInspection {
    private static final Key<Boolean> ALWAYS_CONSTANT = Key.create("ALWAYS_CONSTANT");

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.methodReturnAlwaysConstantDisplayName();
    }

    @RequiredReadAction
    public CommonProblemDescriptor[] checkElement(
        RefEntity refEntity,
        AnalysisScope scope,
        InspectionManager manager,
        GlobalInspectionContext globalContext
    ) {
        if (!(refEntity instanceof RefMethod refMethod)) {
            return null;
        }
        Boolean alreadyProcessed = refMethod.getUserData(ALWAYS_CONSTANT);
        if (alreadyProcessed != null && alreadyProcessed) {
            return null;
        }
        if (!(refMethod.getElement() instanceof PsiMethod method)) {
            return null;
        }
        if (method.getBody() == null) {
            return null;     //we'll catch it on another method
        }
        if (!alwaysReturnsConstant(method)) {
            return null;
        }
        Set<RefMethod> siblingMethods = MethodInheritanceUtils.calculateSiblingMethods(refMethod);
        for (RefMethod siblingMethod : siblingMethods) {
            PsiMethod siblingPsiMethod = (PsiMethod) siblingMethod.getElement();
            if (method.getBody() != null &&
                !alwaysReturnsConstant(siblingPsiMethod)) {
                return null;
            }
        }
        List<ProblemDescriptor> out = new ArrayList<>();
        for (RefMethod siblingRefMethod : siblingMethods) {
            PsiMethod siblingMethod = (PsiMethod) siblingRefMethod.getElement();
            PsiIdentifier identifier = siblingMethod.getNameIdentifier();
            if (identifier == null) {
                continue;
            }
            out.add(
                manager.newProblemDescriptor(InspectionGadgetsLocalize.methodReturnAlwaysConstantProblemDescriptor())
                    .range(identifier)
                    .create()
            );
            siblingRefMethod.putUserData(ALWAYS_CONSTANT, Boolean.TRUE);
        }
        return out.toArray(new ProblemDescriptor[out.size()]);
    }

    private static boolean alwaysReturnsConstant(PsiMethod method) {
        PsiCodeBlock body = method.getBody();
        if (body == null) {
            return false;
        }
        PsiStatement[] statements = body.getStatements();
        if (!(statements.length == 1 && statements[0] instanceof PsiReturnStatement returnStatement)) {
            return false;
        }
        PsiExpression value = returnStatement.getReturnValue();
        return value != null && PsiUtil.isConstantExpression(value);
    }
}
