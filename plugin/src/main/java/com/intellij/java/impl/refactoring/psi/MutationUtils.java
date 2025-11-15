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
package com.intellij.java.impl.refactoring.psi;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;

public class MutationUtils {
    private MutationUtils() {
        super();
    }

    @RequiredWriteAction
    public static void replaceType(String newExpression, PsiTypeElement typeElement) throws IncorrectOperationException {
        PsiManager mgr = typeElement.getManager();
        PsiElementFactory factory = JavaPsiFacade.getInstance(mgr.getProject()).getElementFactory();
        PsiType newType = factory.createTypeFromText(newExpression, null);
        PsiTypeElement newTypeElement = factory.createTypeElement(newType);
        PsiElement insertedElement = typeElement.replace(newTypeElement);
        CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(mgr.getProject());
        PsiElement shortenedElement = JavaCodeStyleManager.getInstance(mgr.getProject()).shortenClassReferences(insertedElement);
        codeStyleManager.reformat(shortenedElement);
    }

    @RequiredWriteAction
    public static void replaceExpression(String newExpression, PsiExpression exp) throws IncorrectOperationException {
        PsiManager mgr = exp.getManager();
        PsiElementFactory factory = JavaPsiFacade.getInstance(mgr.getProject()).getElementFactory();
        PsiExpression newCall = factory.createExpressionFromText(newExpression, null);
        PsiElement insertedElement = exp.replace(newCall);
        CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(mgr.getProject());
        PsiElement shortenedElement = JavaCodeStyleManager.getInstance(mgr.getProject()).shortenClassReferences(insertedElement);
        codeStyleManager.reformat(shortenedElement);
    }

    @RequiredWriteAction
    public static void replaceExpressionIfValid(String newExpression, PsiExpression exp) throws IncorrectOperationException {
        PsiManager mgr = exp.getManager();
        PsiElementFactory factory = JavaPsiFacade.getInstance(mgr.getProject()).getElementFactory();
        PsiExpression newCall;
        try {
            newCall = factory.createExpressionFromText(newExpression, null);
        }
        catch (IncorrectOperationException e) {
            return;
        }
        PsiElement insertedElement = exp.replace(newCall);
        CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(mgr.getProject());
        PsiElement shortenedElement = JavaCodeStyleManager.getInstance(mgr.getProject()).shortenClassReferences(insertedElement);
        codeStyleManager.reformat(shortenedElement);
    }

    @RequiredWriteAction
    public static void replaceReference(String className, PsiJavaCodeReferenceElement reference) throws IncorrectOperationException {
        PsiManager mgr = reference.getManager();
        Project project = mgr.getProject();
        JavaPsiFacade facade = JavaPsiFacade.getInstance(mgr.getProject());
        PsiElementFactory factory = facade.getElementFactory();
        GlobalSearchScope scope = GlobalSearchScope.allScope(project);

        PsiElement insertedElement;
        if (reference.getParent() instanceof PsiReferenceExpression refExpr) {
            PsiClass aClass = facade.findClass(className, scope);
            if (aClass == null) {
                return;
            }
            refExpr.setQualifierExpression(factory.createReferenceExpression(aClass));
            insertedElement = refExpr.getQualifierExpression();
        }
        else {
            PsiJavaCodeReferenceElement newReference = factory.createReferenceElementByFQClassName(className, scope);
            insertedElement = reference.replace(newReference);
        }
        CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(mgr.getProject());
        PsiElement shortenedElement = JavaCodeStyleManager.getInstance(mgr.getProject()).shortenClassReferences(insertedElement);
        codeStyleManager.reformat(shortenedElement);
    }

    @RequiredWriteAction
    public static void replaceStatement(String newStatement, PsiStatement statement) throws IncorrectOperationException {
        Project project = statement.getProject();
        PsiManager mgr = PsiManager.getInstance(project);
        PsiElementFactory factory = JavaPsiFacade.getInstance(mgr.getProject()).getElementFactory();
        PsiStatement newCall = factory.createStatementFromText(newStatement, null);
        PsiElement insertedElement = statement.replace(newCall);
        CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(mgr.getProject());
        PsiElement shortenedElement = JavaCodeStyleManager.getInstance(mgr.getProject()).shortenClassReferences(insertedElement);
        codeStyleManager.reformat(shortenedElement);
    }
}
