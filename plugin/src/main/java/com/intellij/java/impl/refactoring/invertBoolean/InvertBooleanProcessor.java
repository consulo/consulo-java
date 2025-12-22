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
package com.intellij.java.impl.refactoring.invertBoolean;

import com.intellij.java.impl.codeInsight.CodeInsightServicesUtil;
import com.intellij.java.indexing.search.searches.MethodReferencesSearch;
import com.intellij.java.indexing.search.searches.OverridingMethodsSearch;
import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.application.util.query.Query;
import consulo.language.editor.refactoring.BaseRefactoringProcessor;
import consulo.language.editor.refactoring.rename.RenameProcessor;
import consulo.language.editor.refactoring.rename.RenameUtil;
import consulo.language.psi.*;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.usage.MoveRenameUsageInfo;
import consulo.usage.UsageInfo;
import consulo.usage.UsageViewDescriptor;
import consulo.util.lang.ref.SimpleReference;
import jakarta.annotation.Nonnull;

import java.util.*;

/**
 * @author ven
 */
public class InvertBooleanProcessor extends BaseRefactoringProcessor {
    private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.invertBoolean.InvertBooleanMethodProcessor");

    private PsiNamedElement myElement;
    private final String myNewName;
    private final RenameProcessor myRenameProcessor;
    private final Map<UsageInfo, SmartPsiElementPointer> myToInvert = new HashMap<>();
    private final SmartPointerManager mySmartPointerManager;

    @RequiredReadAction
    public InvertBooleanProcessor(PsiNamedElement namedElement, String newName) {
        super(namedElement.getProject());
        myElement = namedElement;
        myNewName = newName;
        Project project = namedElement.getProject();
        myRenameProcessor = new RenameProcessor(project, namedElement, newName, false, false);
        mySmartPointerManager = SmartPointerManager.getInstance(project);
    }

    @Override
    @Nonnull
    protected UsageViewDescriptor createUsageViewDescriptor(@Nonnull UsageInfo[] usages) {
        return new InvertBooleanUsageViewDescriptor(myElement);
    }

    @Override
    @RequiredUIAccess
    protected boolean preprocessUsages(@Nonnull SimpleReference<UsageInfo[]> refUsages) {
        if (myRenameProcessor.preprocessUsages(refUsages)) {
            prepareSuccessful();
            return true;
        }
        return false;
    }

    @Nonnull
    @Override
    @RequiredReadAction
    protected UsageInfo[] findUsages() {
        List<SmartPsiElementPointer> toInvert = new ArrayList<>();

        addRefsToInvert(toInvert, myElement);

        if (myElement instanceof PsiMethod currentMethod) {
            Collection<PsiMethod> overriders = OverridingMethodsSearch.search(currentMethod).findAll();
            for (PsiMethod overrider : overriders) {
                myRenameProcessor.addElement(overrider, myNewName);
            }

            Collection<PsiMethod> allMethods = new HashSet<>(overriders);
            allMethods.add(currentMethod);

            for (PsiMethod method : allMethods) {
                method.accept(new JavaRecursiveElementWalkingVisitor() {
                    @Override
                    public void visitReturnStatement(@Nonnull PsiReturnStatement statement) {
                        PsiExpression returnValue = statement.getReturnValue();
                        if (returnValue != null && PsiType.BOOLEAN.equals(returnValue.getType())) {
                            toInvert.add(mySmartPointerManager.createSmartPsiElementPointer(returnValue));
                        }
                    }

                    @Override
                    public void visitClass(@Nonnull PsiClass aClass) {
                    }
                });
            }
        }
        else if (myElement instanceof PsiParameter parameter && parameter.getDeclarationScope() instanceof PsiMethod method) {
            int index = method.getParameterList().getParameterIndex(parameter);
            LOG.assertTrue(index >= 0);
            Query<PsiReference> methodQuery = MethodReferencesSearch.search(method);
            Collection<PsiReference> methodRefs = methodQuery.findAll();
            for (PsiReference ref : methodRefs) {
                PsiElement parent = ref.getElement().getParent();
                if (parent instanceof PsiAnonymousClass) {
                    parent = parent.getParent();
                }
                if (parent instanceof PsiCall call) {
                    PsiReferenceExpression methodExpression = call instanceof PsiMethodCallExpression methodCall
                        ? methodCall.getMethodExpression()
                        : null;
                    PsiExpressionList argumentList = call.getArgumentList();
                    if (argumentList != null) {
                        PsiExpression[] args = argumentList.getExpressions();
                        if (index < args.length
                            && (methodExpression == null || methodExpression.getQualifier() == null
                            || !"super".equals(methodExpression.getQualifierExpression().getText()))) {
                            toInvert.add(mySmartPointerManager.createSmartPsiElementPointer(args[index]));
                        }
                    }
                }
            }
            Collection<PsiMethod> overriders = OverridingMethodsSearch.search(method).findAll();
            for (PsiMethod overrider : overriders) {
                PsiParameter overriderParameter = overrider.getParameterList().getParameters()[index];
                myRenameProcessor.addElement(overriderParameter, myNewName);
                addRefsToInvert(toInvert, overriderParameter);
            }
        }

        UsageInfo[] renameUsages = myRenameProcessor.findUsages();

        SmartPsiElementPointer[] usagesToInvert = toInvert.toArray(new SmartPsiElementPointer[toInvert.size()]);

        //merge rename and invert usages
        Map<PsiElement, UsageInfo> expressionsToUsages = new HashMap<>();
        List<UsageInfo> result = new ArrayList<>();
        for (UsageInfo renameUsage : renameUsages) {
            expressionsToUsages.put(renameUsage.getElement(), renameUsage);
            result.add(renameUsage);
        }

        for (SmartPsiElementPointer pointer : usagesToInvert) {
            PsiExpression expression = (PsiExpression)pointer.getElement();
            if (!expressionsToUsages.containsKey(expression)) {
                UsageInfo usageInfo = new UsageInfo(expression);
                expressionsToUsages.put(expression, usageInfo);
                result.add(usageInfo); //fake UsageInfo
                myToInvert.put(usageInfo, pointer);
            }
            else {
                myToInvert.put(expressionsToUsages.get(expression), pointer);
            }
        }

        return result.toArray(new UsageInfo[result.size()]);
    }

    @RequiredReadAction
    private void addRefsToInvert(List<SmartPsiElementPointer> toInvert, PsiNamedElement namedElement) {
        Query<PsiReference> query = namedElement instanceof PsiMethod method
            ? MethodReferencesSearch.search(method)
            : ReferencesSearch.search(namedElement);
        Collection<PsiReference> refs = query.findAll();

        for (PsiReference ref : refs) {
            PsiElement element = ref.getElement();
            if (element instanceof PsiReferenceExpression refExpr) {
                if (refExpr.getParent() instanceof PsiAssignmentExpression assignment && refExpr.equals(assignment.getLExpression())) {
                    toInvert.add(mySmartPointerManager.createSmartPsiElementPointer(assignment.getRExpression()));
                }
                else {
                    if (namedElement instanceof PsiParameter
                        && refExpr.getParent().getParent() instanceof PsiMethodCallExpression methodCall) {
                        PsiReferenceExpression methodExpr = methodCall.getMethodExpression();
                        if (methodExpr.getQualifier() != null && "super".equals(methodExpr.getQualifierExpression().getText())) {
                            continue;
                        }
                    }

                    toInvert.add(mySmartPointerManager.createSmartPsiElementPointer(refExpr));
                }
            }
        }

        if (namedElement instanceof PsiVariable variable) {
            PsiExpression initializer = variable.getInitializer();
            if (initializer != null) {
                toInvert.add(mySmartPointerManager.createSmartPsiElementPointer(initializer));
            }
        }
    }

    @Override
    protected void refreshElements(PsiElement[] elements) {
        LOG.assertTrue(elements.length == 1 && elements[0] instanceof PsiMethod);
        myElement = (PsiMethod)elements[0];
    }

    private static UsageInfo[] extractUsagesForElement(PsiElement element, UsageInfo[] usages) {
        List<UsageInfo> extractedUsages = new ArrayList<>(usages.length);
        for (UsageInfo usage : usages) {
            if (usage instanceof MoveRenameUsageInfo usageInfo && element.equals(usageInfo.getReferencedElement())) {
                extractedUsages.add(usageInfo);
            }
        }
        return extractedUsages.toArray(new UsageInfo[extractedUsages.size()]);
    }

    @Override
    @RequiredWriteAction
    protected void performRefactoring(@Nonnull UsageInfo[] usages) {
        for (PsiElement element : myRenameProcessor.getElements()) {
            try {
                RenameUtil.doRename(
                    element,
                    myRenameProcessor.getNewName(element),
                    extractUsagesForElement(element, usages),
                    myProject,
                    null
                );
            }
            catch (IncorrectOperationException e) {
                RenameUtil.showErrorMessage(e, element, myProject);
                return;
            }
        }

        for (UsageInfo usage : usages) {
            SmartPsiElementPointer pointerToInvert = myToInvert.get(usage);
            if (pointerToInvert != null) {
                PsiExpression expression = (PsiExpression)pointerToInvert.getElement();
                LOG.assertTrue(expression != null);
                if (expression.getParent() instanceof PsiMethodCallExpression methodCall) {
                    expression = methodCall;
                }
                try {
                    while (expression.getParent() instanceof PsiPrefixExpression prefixExpr
                        && prefixExpr.getOperationTokenType() == JavaTokenType.EXCL) {
                        expression = prefixExpr;
                    }

                    if (!(expression.getParent() instanceof PsiExpressionStatement)) {
                        expression.replace(CodeInsightServicesUtil.invertCondition(expression));
                    }
                }
                catch (IncorrectOperationException e) {
                    LOG.error(e);
                }
            }
        }
    }

    @Nonnull
    @Override
    protected LocalizeValue getCommandName() {
        return InvertBooleanHandler.REFACTORING_NAME;
    }
}
