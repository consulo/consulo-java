/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.java.language.impl.refactoring.util;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public class RefactoringChangeUtil {
    private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.util.ChangeUtil");

    @Nullable
    private static String getMethodExpressionName(@Nullable PsiElement element) {
        return element instanceof PsiMethodCallExpression methodCall ? methodCall.getMethodExpression().getReferenceName() : null;
    }

    public static boolean isSuperOrThisMethodCall(@Nullable PsiElement element) {
        String name = getMethodExpressionName(element);
        return PsiKeyword.SUPER.equals(name) || PsiKeyword.THIS.equals(name);
    }

    public static boolean isSuperMethodCall(@Nullable PsiElement element) {
        String name = getMethodExpressionName(element);
        return PsiKeyword.SUPER.equals(name);
    }

    public static PsiType getTypeByExpression(PsiExpression expr) {
        PsiType type = expr.getType();
        if (type == null) {
            if (expr instanceof PsiArrayInitializerExpression) {
                PsiExpression[] initializers = ((PsiArrayInitializerExpression)expr).getInitializers();
                if (initializers.length > 0) {
                    PsiType initType = getTypeByExpression(initializers[0]);
                    if (initType == null) {
                        return null;
                    }
                    return initType.createArrayType();
                }
            }

            if (expr instanceof PsiReferenceExpression refExpr && PsiUtil.isOnAssignmentLeftHand(expr)) {
                return getTypeByExpression(((PsiAssignmentExpression)refExpr.getParent()).getRExpression());
            }
            return null;
        }
        if (PsiUtil.resolveClassInType(type) instanceof PsiAnonymousClass anonymousClass) {
            type = anonymousClass.getBaseClassType();
        }

        return GenericsUtil.getVariableTypeByExpressionType(type);
    }

    public static PsiReferenceExpression qualifyReference(
        @Nonnull PsiReferenceExpression referenceExpression,
        @Nonnull PsiMember member,
        @Nullable PsiClass qualifyingClass
    ) throws IncorrectOperationException {
        PsiManager manager = referenceExpression.getManager();
        PsiMethodCallExpression methodCallExpression = PsiTreeUtil.getParentOfType(
            referenceExpression,
            PsiMethodCallExpression.class,
            true
        );
        while (methodCallExpression != null) {
            if (isSuperOrThisMethodCall(methodCallExpression)) {
                return referenceExpression;
            }
            methodCallExpression = PsiTreeUtil.getParentOfType(
                methodCallExpression,
                PsiMethodCallExpression.class,
                true
            );
        }
        PsiReferenceExpression expressionFromText;
        PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
        if (qualifyingClass == null) {
            PsiClass parentClass = PsiTreeUtil.getParentOfType(referenceExpression, PsiClass.class);
            PsiClass containingClass = member.getContainingClass();
            if (parentClass != null && !InheritanceUtil.isInheritorOrSelf(parentClass, containingClass, true)) {
                while (parentClass != null && !InheritanceUtil.isInheritorOrSelf(parentClass, containingClass, true)) {
                    parentClass = PsiTreeUtil.getParentOfType(parentClass, PsiClass.class, true);
                }
                LOG.assertTrue(parentClass != null);
                expressionFromText = (PsiReferenceExpression)
                    factory.createExpressionFromText("A.this." + member.getName(), null);
                ((PsiThisExpression)expressionFromText.getQualifierExpression()).getQualifier()
                    .replace(factory.createClassReferenceElement(parentClass));
            }
            else {
                expressionFromText = (PsiReferenceExpression)factory
                    .createExpressionFromText("this." + member.getName(), null);
            }
        }
        else {
            expressionFromText = (PsiReferenceExpression)factory
                .createExpressionFromText("A." + member.getName(), null);
            expressionFromText.setQualifierExpression(factory.createReferenceExpression(qualifyingClass));
        }
        CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(manager.getProject());
        expressionFromText = (PsiReferenceExpression)codeStyleManager.reformat(expressionFromText);
        return (PsiReferenceExpression)referenceExpression.replace(expressionFromText);
    }

    public static PsiClass getThisClass(@Nonnull PsiElement place) {
        PsiElement parent = place.getContext();
        if (parent == null) {
            return null;
        }
        PsiElement prev = null;
        while (true) {
            if (parent instanceof PsiClass) {
                if (!(parent instanceof PsiAnonymousClass && ((PsiAnonymousClass)parent).getArgumentList() == prev)) {
                    return (PsiClass)parent;
                }
            }
            prev = parent;
            parent = parent.getContext();
            if (parent == null) {
                return null;
            }
        }
    }

    @RequiredWriteAction
    @SuppressWarnings("unchecked")
    static <T extends PsiQualifiedExpression> T createQualifiedExpression(
        @Nonnull PsiManager manager,
        PsiClass qualifierClass,
        @Nonnull String qName
    ) throws IncorrectOperationException {
        PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
        if (qualifierClass != null) {
            T qualifiedThis = (T)factory.createExpressionFromText("q." + qName, null);
            qualifiedThis = (T)CodeStyleManager.getInstance(manager.getProject()).reformat(qualifiedThis);
            PsiJavaCodeReferenceElement thisQualifier = qualifiedThis.getQualifier();
            LOG.assertTrue(thisQualifier != null);
            thisQualifier.bindToElement(qualifierClass);
            return qualifiedThis;
        }
        else {
            return (T)factory.createExpressionFromText(qName, null);
        }
    }

    @RequiredWriteAction
    public static PsiThisExpression createThisExpression(PsiManager manager, PsiClass qualifierClass)
        throws IncorrectOperationException {
        return RefactoringChangeUtil.<PsiThisExpression>createQualifiedExpression(manager, qualifierClass, "this");
    }

    @RequiredWriteAction
    public static PsiSuperExpression createSuperExpression(PsiManager manager, PsiClass qualifierClass)
        throws IncorrectOperationException {
        return RefactoringChangeUtil.<PsiSuperExpression>createQualifiedExpression(manager, qualifierClass, "super");
    }
}
