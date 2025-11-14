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
package com.intellij.java.impl.refactoring.inline;

import com.intellij.java.analysis.impl.psi.controlFlow.DefUseUtil;
import com.intellij.java.impl.codeInspection.sameParameterValue.SameParameterValueInspection;
import com.intellij.java.impl.refactoring.util.InlineUtil;
import com.intellij.java.impl.refactoring.util.RefactoringUtil;
import com.intellij.java.language.impl.codeInsight.ExceptionUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.refactoring.BaseRefactoringProcessor;
import consulo.language.editor.refactoring.ui.RefactoringUIUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.logging.Logger;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.usage.UsageInfo;
import consulo.usage.UsageViewDescriptor;
import consulo.usage.UsageViewUtil;
import consulo.util.collection.MultiMap;
import consulo.util.dataholder.Key;
import consulo.util.lang.ref.SimpleReference;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;

/**
 * @author yole
 */
public class InlineParameterExpressionProcessor extends BaseRefactoringProcessor {
    private static final Logger LOG = Logger.getInstance(InlineParameterExpressionProcessor.class);
    public static final Key<Boolean> CREATE_LOCAL_FOR_TESTS = Key.create("CREATE_INLINE_PARAMETER_LOCAL_FOR_TESTS");

    private final PsiCallExpression myMethodCall;
    private final PsiMethod myMethod;
    private final PsiParameter myParameter;
    private PsiExpression myInitializer;
    private final boolean mySameClass;
    private final PsiMethod myCallingMethod;
    private final boolean myCreateLocal;

    public InlineParameterExpressionProcessor(
        PsiCallExpression methodCall,
        PsiMethod method,
        PsiParameter parameter,
        PsiExpression initializer,
        boolean createLocal
    ) {
        super(method.getProject());
        myMethodCall = methodCall;
        myMethod = method;
        myParameter = parameter;
        myInitializer = initializer;
        myCreateLocal = createLocal;

        PsiClass callingClass = PsiTreeUtil.getParentOfType(methodCall, PsiClass.class);
        mySameClass = (callingClass == myMethod.getContainingClass());
        myCallingMethod = PsiTreeUtil.getParentOfType(myMethodCall, PsiMethod.class);
    }

    @Nonnull
    @Override
    protected String getCommandName() {
        return InlineParameterHandler.REFACTORING_NAME.get();
    }

    @Nonnull
    @Override
    protected UsageViewDescriptor createUsageViewDescriptor(@Nonnull UsageInfo[] usages) {
        return new InlineViewDescriptor(myParameter);
    }

    @Nonnull
    @Override
    @RequiredReadAction
    protected UsageInfo[] findUsages() {
        int parameterIndex = myMethod.getParameterList().getParameterIndex(myParameter);
        Map<PsiVariable, PsiElement> localToParamRef = new HashMap<>();
        PsiExpression[] arguments = myMethodCall.getArgumentList().getExpressions();
        for (int i = 0; i < arguments.length; i++) {
            if (i != parameterIndex && arguments[i] instanceof PsiReferenceExpression refExpr) {
                PsiElement element = refExpr.resolve();
                if (element instanceof PsiLocalVariable || element instanceof PsiParameter) {
                    PsiParameter param = myMethod.getParameterList().getParameters()[i];
                    PsiExpression paramRef = JavaPsiFacade.getInstance(myMethod.getProject())
                        .getElementFactory()
                        .createExpressionFromText(param.getName(), myMethod);
                    localToParamRef.put((PsiVariable)element, paramRef);
                }
            }
        }

        List<UsageInfo> result = new ArrayList<>();
        myInitializer.accept(new JavaRecursiveElementVisitor() {
            @RequiredWriteAction
            @Override
            public void visitReferenceExpression(PsiReferenceExpression expression) {
                super.visitReferenceExpression(expression);
                if (expression.resolve() instanceof PsiLocalVariable localVariable) {
                    PsiElement[] elements = DefUseUtil.getDefs(myCallingMethod.getBody(), localVariable, expression);
                    if (elements.length == 1) {
                        PsiExpression localInitializer = null;
                        if (elements[0] instanceof PsiLocalVariable localVar) {
                            localInitializer = localVar.getInitializer();
                        }
                        else if (elements[0] instanceof PsiAssignmentExpression assignment) {
                            localInitializer = assignment.getRExpression();
                        }
                        else if (elements[0] instanceof PsiReferenceExpression refElement) {
                            if (refElement.getParent() instanceof PsiAssignmentExpression assignment
                                && assignment.getLExpression() == refElement) {
                                localInitializer = assignment.getRExpression();
                            }
                        }
                        if (localInitializer != null) {
                            PsiElement replacement;
                            if (localToParamRef.containsKey(localVariable)) {
                                replacement = localToParamRef.get(localVariable);
                            }
                            else {
                                replacement = replaceArgs(localToParamRef, localInitializer.copy());
                            }
                            result.add(new LocalReplacementUsageInfo(expression, replacement));
                        }
                    }
                }
            }
        });

        if (!myCreateLocal) {
            for (PsiReference ref : ReferencesSearch.search(myParameter).findAll()) {
                result.add(new UsageInfo(ref));
            }
        }

        UsageInfo[] usageInfos = result.toArray(new UsageInfo[result.size()]);
        return UsageViewUtil.removeDuplicatedUsages(usageInfos);
    }

    @RequiredWriteAction
    private static PsiElement replaceArgs(Map<PsiVariable, PsiElement> elementsToReplace, PsiElement expression) {
        Map<PsiElement, PsiElement> replacements = new HashMap<>();
        expression.accept(new JavaRecursiveElementVisitor() {
            @Override
            @RequiredReadAction
            public void visitReferenceExpression(PsiReferenceExpression referenceExpression) {
                super.visitReferenceExpression(referenceExpression);
                if (referenceExpression.resolve() instanceof PsiVariable variable) {
                    PsiElement replacement = elementsToReplace.get(variable);
                    if (replacement != null) {
                        replacements.put(referenceExpression, replacement);
                    }
                }
            }
        });
        return RefactoringUtil.replaceElementsWithMap(expression, replacements);
    }

    @Override
    @RequiredUIAccess
    protected boolean preprocessUsages(@Nonnull SimpleReference<UsageInfo[]> refUsages) {
        MultiMap<PsiElement, String> conflicts = new MultiMap<>();
        UsageInfo[] usages = refUsages.get();
        InaccessibleExpressionsDetector detector = new InaccessibleExpressionsDetector(conflicts);
        myInitializer.accept(detector);
        for (UsageInfo usage : usages) {
            if (usage instanceof LocalReplacementUsageInfo localReplacementUsageInfo) {
                PsiElement replacement = localReplacementUsageInfo.getReplacement();
                if (replacement != null) {
                    replacement.accept(detector);
                }
            }
        }

        Set<PsiVariable> vars = new HashSet<>();
        for (UsageInfo usageInfo : usages) {
            if (usageInfo instanceof LocalReplacementUsageInfo localReplacementUsageInfo) {
                PsiVariable var = localReplacementUsageInfo.getVariable();
                if (var != null) {
                    vars.add(var);
                }
            }
        }
        for (PsiVariable var : vars) {
            for (PsiReference ref : ReferencesSearch.search(var)) {
                if (ref.getElement() instanceof PsiExpression expression && isAccessedForWriting(expression)) {
                    conflicts.putValue(
                        expression,
                        "Parameter initializer depends on value which is not available inside method and cannot be inlined"
                    );
                    break;
                }
            }
        }
        return showConflicts(conflicts, usages);
    }

    private static boolean isAccessedForWriting(PsiExpression expr) {
        while (expr.getParent() instanceof PsiArrayAccessExpression arrayAccessExpr) {
            expr = arrayAccessExpr;
        }
        return PsiUtil.isAccessedForWriting(expr);
    }

    @Override
    @RequiredWriteAction
    protected void performRefactoring(UsageInfo[] usages) {
        List<PsiClassType> thrownExceptions = ExceptionUtil.getThrownCheckedExceptions(myInitializer);
        Set<PsiVariable> varsUsedInInitializer = new HashSet<>();
        Set<PsiJavaCodeReferenceElement> paramRefsToInline = new HashSet<>();
        Map<PsiElement, PsiElement> replacements = new HashMap<>();
        for (UsageInfo usage : usages) {
            if (usage instanceof LocalReplacementUsageInfo replacementUsageInfo) {
                PsiElement element = replacementUsageInfo.getElement();
                PsiElement replacement = replacementUsageInfo.getReplacement();
                if (element != null && replacement != null) {
                    replacements.put(element, replacement);
                }
                varsUsedInInitializer.add(replacementUsageInfo.getVariable());
            }
            else {
                LOG.assertTrue(!myCreateLocal);
                paramRefsToInline.add((PsiJavaCodeReferenceElement)usage.getElement());
            }
        }
        myInitializer = (PsiExpression)RefactoringUtil.replaceElementsWithMap(myInitializer, replacements);

        if (myCreateLocal) {
            PsiElementFactory factory = JavaPsiFacade.getInstance(myMethod.getProject()).getElementFactory();
            PsiDeclarationStatement localDeclaration =
                factory.createVariableDeclarationStatement(myParameter.getName(), myParameter.getType(), myInitializer);
            PsiLocalVariable declaredVar = (PsiLocalVariable)localDeclaration.getDeclaredElements()[0];
            PsiUtil.setModifierProperty(declaredVar, PsiModifier.FINAL, myParameter.hasModifierProperty(PsiModifier.FINAL));
            PsiExpression localVarInitializer = InlineUtil.inlineVariable(
                myParameter,
                myInitializer,
                (PsiReferenceExpression)factory.createExpressionFromText(myParameter.getName(), myMethod)
            );
            PsiExpression initializer = declaredVar.getInitializer();
            LOG.assertTrue(initializer != null);
            initializer.replace(localVarInitializer);
            PsiCodeBlock body = myMethod.getBody();
            if (body != null) {
                PsiElement anchor = findAnchorForLocalVariableDeclaration(body);
                body.addAfter(localDeclaration, anchor);
            }
        }
        else {
            for (PsiJavaCodeReferenceElement paramRef : paramRefsToInline) {
                InlineUtil.inlineVariable(myParameter, myInitializer, paramRef);
            }
        }

        //delete var if it becomes unused
        for (PsiVariable variable : varsUsedInInitializer) {
            if (variable != null && variable.isValid()) {
                if (ReferencesSearch.search(variable).findFirst() == null) {
                    variable.delete();
                }
            }
        }

        SameParameterValueInspection.InlineParameterValueFix.removeParameter(myMethod, myParameter);

        if (!thrownExceptions.isEmpty()) {
            for (PsiClassType exception : thrownExceptions) {
                PsiClass exceptionClass = exception.resolve();
                if (exceptionClass != null) {
                    PsiUtil.addException(myMethod, exceptionClass);
                }
            }
        }
    }

    @Nullable
    private PsiElement findAnchorForLocalVariableDeclaration(PsiCodeBlock body) {
        PsiElement anchor = body.getLBrace();
        if (myMethod.isConstructor()) {
            PsiStatement[] statements = body.getStatements();
            if (statements.length > 0 && statements[0] instanceof PsiExpressionStatement exprStmt
                && exprStmt.getExpression() instanceof PsiMethodCallExpression methodCall) {
                String referenceName = methodCall.getMethodExpression().getReferenceName();
                if (PsiKeyword.SUPER.equals(referenceName) || PsiKeyword.THIS.equals(referenceName)) {
                    anchor = exprStmt;
                }
            }
        }
        return anchor;
    }

    private static class LocalReplacementUsageInfo extends UsageInfo {
        private final PsiElement myReplacement;
        private final PsiVariable myVariable;

        @RequiredReadAction
        public LocalReplacementUsageInfo(@Nonnull PsiReference element, @Nonnull PsiElement replacement) {
            super(element);
            myVariable = element.resolve() instanceof PsiVariable variable ? variable : null;
            myReplacement = replacement;
        }

        @Nullable
        @RequiredReadAction
        public PsiElement getReplacement() {
            return myReplacement.isValid() ? myReplacement : null;
        }

        @Nullable
        @RequiredReadAction
        public PsiVariable getVariable() {
            return myVariable != null && myVariable.isValid() ? myVariable : null;
        }
    }

    private class InaccessibleExpressionsDetector extends JavaRecursiveElementWalkingVisitor {
        private final MultiMap<PsiElement, String> myConflicts;

        public InaccessibleExpressionsDetector(MultiMap<PsiElement, String> conflicts) {
            myConflicts = conflicts;
        }

        @Override
        @RequiredReadAction
        public void visitReferenceExpression(PsiReferenceExpression expression) {
            super.visitReferenceExpression(expression);
            PsiElement element = expression.resolve();
            if (element instanceof PsiMember member && !member.isStatic()) {
                if (myMethod.isStatic()) {
                    myConflicts.putValue(
                        expression,
                        "Parameter initializer depends on " + RefactoringUIUtil.getDescription(
                            element,
                            false
                        ) + " which is not available inside the static method"
                    );
                }
            }
            if (element instanceof PsiMethod || element instanceof PsiField) {
                if (!mySameClass && !((PsiModifierListOwner)element).hasModifierProperty(PsiModifier.STATIC)) {
                    myConflicts.putValue(expression, "Parameter initializer depends on non static member from some other class");
                }
                else if (!PsiUtil.isAccessible((PsiMember)element, myMethod, null)) {
                    myConflicts.putValue(expression, "Parameter initializer depends on value which is not available inside method");
                }
            }
            else if (element instanceof PsiParameter) {
                myConflicts.putValue(expression, "Parameter initializer depends on callers parameter");
            }
        }

        @Override
        @RequiredReadAction
        public void visitThisExpression(@Nonnull PsiThisExpression thisExpression) {
            super.visitThisExpression(thisExpression);
            PsiJavaCodeReferenceElement qualifier = thisExpression.getQualifier();
            PsiElement containingClass;
            if (qualifier != null) {
                containingClass = qualifier.resolve();
            }
            else {
                containingClass = PsiTreeUtil.getParentOfType(myMethodCall, PsiClass.class);
            }
            PsiClass methodContainingClass = myMethod.getContainingClass();
            LOG.assertTrue(methodContainingClass != null);
            if (!PsiTreeUtil.isAncestor(containingClass, methodContainingClass, false)) {
                myConflicts.putValue(
                    thisExpression,
                    "Parameter initializer depends on this which is not available inside the method and cannot be inlined"
                );
            }
            else if (myMethod.isStatic()) {
                myConflicts.putValue(
                    thisExpression,
                    "Parameter initializer depends on this which is not available inside the static method"
                );
            }
        }

        @Override
        @RequiredReadAction
        public void visitReferenceElement(@Nonnull PsiJavaCodeReferenceElement reference) {
            super.visitReferenceElement(reference);
            if (myMethod.isStatic() && reference.resolve() instanceof PsiClass psiClass && !psiClass.isStatic()) {
                myConflicts.putValue(
                    reference,
                    "Parameter initializer depends on non static class which is not available inside static method"
                );
            }
        }

        @Override
        @RequiredReadAction
        public void visitNewExpression(@Nonnull PsiNewExpression expression) {
            super.visitNewExpression(expression);
            PsiJavaCodeReferenceElement reference = expression.getClassOrAnonymousClassReference();
            if (reference != null && reference.resolve() instanceof PsiClass refClass) {
                String classUnavailableMessage = "Parameter initializer depends on " +
                    RefactoringUIUtil.getDescription(refClass, true) +
                    " which is not available inside method and cannot be inlined";
                if (!PsiUtil.isAccessible(refClass, myMethod, null)) {
                    myConflicts.putValue(expression, classUnavailableMessage);
                }
                else {
                    PsiClass methodContainingClass = myMethod.getContainingClass();
                    LOG.assertTrue(methodContainingClass != null);
                    if (!PsiTreeUtil.isAncestor(myMethod, refClass, false)) {
                        PsiElement parent = refClass;
                        while ((parent = parent.getParent()) instanceof PsiClass psiClass) {
                            if (!PsiUtil.isAccessible(psiClass, myMethod, null)) {
                                break;
                            }
                        }
                        if (!(parent instanceof PsiFile)) {
                            myConflicts.putValue(expression, classUnavailableMessage);
                        }
                    }
                }
            }
        }
    }
}
