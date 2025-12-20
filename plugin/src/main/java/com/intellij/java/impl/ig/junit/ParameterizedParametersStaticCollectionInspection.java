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
package com.intellij.java.impl.ig.junit;

import com.intellij.java.impl.codeInsight.daemon.impl.quickfix.CreateMethodQuickFix;
import com.intellij.java.impl.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.java.impl.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author anna
 * @since 2009-01-10
 */
@ExtensionImpl
public class ParameterizedParametersStaticCollectionInspection extends BaseInspection {
    private static final String PARAMETERS_FQN = "org.junit.runners.Parameterized.Parameters";
    private static final String PARAMETERIZED_FQN = "org.junit.runners.Parameterized";

    @Nonnull
    protected String buildErrorString(Object... infos) {
        return infos.length > 0
            ? (String) infos[1]
            : "Class #ref annotated @RunWith(Parameterized.class) lacks data provider";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new BaseInspectionVisitor() {
            @Override
            public void visitClass(PsiClass aClass) {
                PsiAnnotation annotation = AnnotationUtil.findAnnotation(aClass, "org.junit.runner.RunWith");
                if (annotation != null) {
                    for (PsiNameValuePair pair : annotation.getParameterList().getAttributes()) {
                        PsiAnnotationMemberValue value = pair.getValue();
                        if (value instanceof PsiClassObjectAccessExpression) {
                            PsiTypeElement typeElement = ((PsiClassObjectAccessExpression) value).getOperand();
                            if (typeElement.getType().getCanonicalText().equals(PARAMETERIZED_FQN)) {
                                List<MethodCandidate> candidates = new ArrayList<MethodCandidate>();
                                for (PsiMethod method : aClass.getMethods()) {
                                    PsiType returnType = method.getReturnType();
                                    PsiClass returnTypeClass = PsiUtil.resolveClassInType(returnType);
                                    Project project = aClass.getProject();
                                    PsiClass collectionsClass =
                                        JavaPsiFacade.getInstance(project)
                                            .findClass(Collection.class.getName(), GlobalSearchScope.allScope(project));
                                    if (AnnotationUtil.isAnnotated(method, PARAMETERS_FQN, false)) {
                                        PsiModifierList modifierList = method.getModifierList();
                                        boolean hasToFixSignature = false;
                                        String message = "Make method \'" + method.getName() + "\' ";
                                        String errorString = "Method \'#ref()\' should be ";
                                        if (!modifierList.hasModifierProperty(PsiModifier.PUBLIC)) {
                                            message += PsiModifier.PUBLIC + " ";
                                            errorString += PsiModifier.PUBLIC + " ";
                                            hasToFixSignature = true;
                                        }
                                        if (!modifierList.hasModifierProperty(PsiModifier.STATIC)) {
                                            message += PsiModifier.STATIC;
                                            errorString += PsiModifier.STATIC;
                                            hasToFixSignature = true;
                                        }
                                        if (collectionsClass != null &&
                                            (returnTypeClass == null || !InheritanceUtil.isInheritorOrSelf(
                                                returnTypeClass,
                                                collectionsClass,
                                                true
                                            ))) {
                                            message += (hasToFixSignature ? " and" : "") + " return Collection";
                                            errorString += (hasToFixSignature ? " and" : "") + " return Collection";
                                            returnType = JavaPsiFacade.getElementFactory(project).createType(collectionsClass);
                                            hasToFixSignature = true;
                                        }
                                        if (hasToFixSignature) {
                                            candidates.add(new MethodCandidate(method, message, errorString, returnType));
                                            continue;
                                        }
                                        return;
                                    }
                                }
                                if (candidates.isEmpty()) {
                                    registerClassError(aClass);
                                }
                                else {
                                    for (MethodCandidate candidate : candidates) {
                                        registerMethodError(
                                            candidate.myMethod,
                                            candidate.myProblem,
                                            candidate.myErrorString,
                                            candidate.myReturnType
                                        );
                                    }
                                }
                            }
                        }
                    }
                }
            }
        };
    }

    @Override
    protected InspectionGadgetsFix buildFix(final Object... infos) {
        return new InspectionGadgetsFix() {
            @Override
            protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
                PsiElement element = descriptor.getPsiElement();
                PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
                if (method != null) {
                    PsiType type = (PsiType) infos[1];
                    if (type == null) {
                        type = method.getReturnType();
                    }
                    ChangeSignatureProcessor csp =
                        new ChangeSignatureProcessor(
                            project,
                            method,
                            false,
                            PsiModifier.PUBLIC,
                            method.getName(),
                            type,
                            new ParameterInfoImpl[0]
                        );
                    csp.run();
                }
                else {
                    PsiClass psiClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
                    if (psiClass != null) {
                        CreateMethodQuickFix fix = CreateMethodQuickFix
                            .createFix(psiClass, "@" + PARAMETERS_FQN + " public static java.util.Collection parameters()", "");
                        if (fix != null) {
                            fix.applyFix(project, descriptor);
                        }
                    }
                }
            }

            @Nonnull
            @Override
            public LocalizeValue getName() {
                return LocalizeValue.localizeTODO(infos.length > 0 ? (String) infos[0] : "Create @Parameterized.Parameters data provider");
            }
        };
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return LocalizeValue.localizeTODO("@RunWith(Parameterized.class) without data provider");
    }

    private static class MethodCandidate {
        PsiMethod myMethod;
        String myProblem;
        private final String myErrorString;
        PsiType myReturnType;

        public MethodCandidate(PsiMethod method, String problem, String errorString, PsiType returnType) {
            myMethod = method;
            myProblem = problem;
            myErrorString = errorString;
            myReturnType = returnType;
        }
    }
}