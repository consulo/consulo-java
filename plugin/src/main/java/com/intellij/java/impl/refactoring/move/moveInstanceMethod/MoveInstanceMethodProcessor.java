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
package com.intellij.java.impl.refactoring.move.moveInstanceMethod;

import com.intellij.java.impl.codeInsight.generation.OverrideImplementUtil;
import com.intellij.java.impl.refactoring.move.MoveInstanceMembersUtil;
import com.intellij.java.impl.refactoring.util.ConflictsUtil;
import com.intellij.java.impl.refactoring.util.RefactoringConflictsUtil;
import com.intellij.java.impl.refactoring.util.RefactoringUtil;
import com.intellij.java.indexing.search.searches.ClassInheritorsSearch;
import com.intellij.java.language.impl.codeInsight.ChangeContextUtil;
import com.intellij.java.language.impl.refactoring.util.RefactoringChangeUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.javadoc.PsiDocTagValue;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.util.VisibilityUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.refactoring.BaseRefactoringProcessor;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.ui.RefactoringUIUtil;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.PsiReference;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.usage.UsageInfo;
import consulo.usage.UsageViewDescriptor;
import consulo.usage.UsageViewUtil;
import consulo.util.collection.MultiMap;
import consulo.util.lang.Comparing;
import consulo.util.lang.ref.SimpleReference;
import jakarta.annotation.Nonnull;

import java.util.*;

/**
 * @author ven
 */
public class MoveInstanceMethodProcessor extends BaseRefactoringProcessor {
    private static final Logger LOG = Logger.getInstance(MoveInstanceMethodProcessor.class);

    public PsiMethod getMethod() {
        return myMethod;
    }

    public PsiVariable getTargetVariable() {
        return myTargetVariable;
    }

    private PsiMethod myMethod;
    private PsiVariable myTargetVariable;
    private PsiClass myTargetClass;
    private final String myNewVisibility;
    private final Map<PsiClass, String> myOldClassParameterNames;

    public MoveInstanceMethodProcessor(
        Project project,
        PsiMethod method,
        PsiVariable targetVariable,
        String newVisibility,
        Map<PsiClass, String> oldClassParameterNames
    ) {
        super(project);
        myMethod = method;
        myTargetVariable = targetVariable;
        myOldClassParameterNames = oldClassParameterNames;
        LOG.assertTrue(myTargetVariable instanceof PsiParameter || myTargetVariable instanceof PsiField);
        LOG.assertTrue(myTargetVariable.getType() instanceof PsiClassType);
        PsiType type = myTargetVariable.getType();
        LOG.assertTrue(type instanceof PsiClassType);
        myTargetClass = ((PsiClassType)type).resolve();
        myNewVisibility = newVisibility;
    }

    @Nonnull
    @Override
    protected UsageViewDescriptor createUsageViewDescriptor(@Nonnull UsageInfo[] usages) {
        return new MoveInstanceMethodViewDescriptor(myMethod, myTargetVariable, myTargetClass);
    }

    @Override
    @RequiredUIAccess
    protected boolean preprocessUsages(@Nonnull SimpleReference<UsageInfo[]> refUsages) {
        UsageInfo[] usages = refUsages.get();
        MultiMap<PsiElement, String> conflicts = new MultiMap<>();
        Set<PsiMember> members = new HashSet<>();
        members.add(myMethod);
        if (myTargetVariable instanceof PsiField field) {
            members.add(field);
        }
        if (!myTargetClass.isInterface()) {
            RefactoringConflictsUtil.analyzeAccessibilityConflicts(members, myTargetClass, conflicts, myNewVisibility);
        }
        else {
            for (UsageInfo usage : usages) {
                if (usage instanceof InheritorUsageInfo inheritorUsageInfo) {
                    RefactoringConflictsUtil.analyzeAccessibilityConflicts(
                        members,
                        inheritorUsageInfo.getInheritor(),
                        conflicts,
                        myNewVisibility
                    );
                }
            }
        }

        if (myTargetVariable instanceof PsiParameter parameter) {
            for (UsageInfo usageInfo : usages) {
                if (usageInfo instanceof MethodCallUsageInfo methodCallUsageInfo) {
                    PsiElement methodCall = methodCallUsageInfo.getMethodCallExpression();
                    if (methodCall instanceof PsiMethodCallExpression methodCallExpr) {
                        PsiExpression[] expressions = methodCallExpr.getArgumentList().getExpressions();
                        int index = myMethod.getParameterList().getParameterIndex(parameter);
                        if (index < expressions.length) {
                            PsiExpression instanceValue = expressions[index];
                            instanceValue = RefactoringUtil.unparenthesizeExpression(instanceValue);
                            if (instanceValue instanceof PsiLiteralExpression literal && literal.getValue() == null) {
                                LocalizeValue message = RefactoringLocalize.zeroContainsCallWithNullArgumentForParameter1(
                                    RefactoringUIUtil.getDescription(ConflictsUtil.getContainer(methodCallExpr), true),
                                    CommonRefactoringUtil.htmlEmphasize(parameter.getName())
                                );
                                conflicts.putValue(instanceValue, message.get());
                            }
                        }
                    }
                    else if (methodCall instanceof PsiMethodReferenceExpression) {
                        conflicts.putValue(methodCall, "Method reference would be broken after move");
                    }
                }
            }
        }

        try {
            ConflictsUtil.checkMethodConflicts(myTargetClass, myMethod, getPatternMethod(), conflicts);
        }
        catch (IncorrectOperationException ignore) {
        }

        return showConflicts(conflicts, usages);
    }

    @Nonnull
    @Override
    @RequiredReadAction
    protected UsageInfo[] findUsages() {
        PsiManager manager = myMethod.getManager();
        GlobalSearchScope searchScope = GlobalSearchScope.allScope(manager.getProject());
        final List<UsageInfo> usages = new ArrayList<>();
        for (PsiReference ref : ReferencesSearch.search(myMethod, searchScope, false)) {
            PsiElement element = ref.getElement();
            if (element instanceof PsiReferenceExpression refExpr) {
                boolean isInternal = PsiTreeUtil.isAncestor(myMethod, element, true);
                usages.add(new MethodCallUsageInfo(refExpr, isInternal));
            }
            else if (element instanceof PsiDocTagValue docTagValue) {
                usages.add(new JavadocUsageInfo(docTagValue));
            }
            else {
                throw new UnknownReferenceTypeException(element.getLanguage());
            }
        }

        if (myTargetClass.isInterface()) {
            addInheritorUsages(myTargetClass, searchScope, usages);
        }

        PsiCodeBlock body = myMethod.getBody();
        if (body != null) {
            body.accept(new JavaRecursiveElementWalkingVisitor() {
                @Override
                public void visitNewExpression(@Nonnull PsiNewExpression expression) {
                    if (MoveInstanceMembersUtil.getClassReferencedByThis(expression) != null) {
                        usages.add(new InternalUsageInfo(expression));
                    }
                    super.visitNewExpression(expression);
                }

                @Override
                @RequiredReadAction
                public void visitReferenceExpression(PsiReferenceExpression expression) {
                    if (MoveInstanceMembersUtil.getClassReferencedByThis(expression) != null) {
                        usages.add(new InternalUsageInfo(expression));
                    }
                    else if (!expression.isQualified()) {
                        PsiElement resolved = expression.resolve();
                        if (myTargetVariable.equals(resolved)) {
                            usages.add(new InternalUsageInfo(expression));
                        }
                    }

                    super.visitReferenceExpression(expression);
                }
            });
        }

        return usages.toArray(new UsageInfo[usages.size()]);
    }

    private static void addInheritorUsages(PsiClass aClass, GlobalSearchScope searchScope, List<UsageInfo> usages) {
        for (PsiClass inheritor : ClassInheritorsSearch.search(aClass, searchScope, false).findAll()) {
            if (!inheritor.isInterface()) {
                usages.add(new InheritorUsageInfo(inheritor));
            }
            else {
                addInheritorUsages(inheritor, searchScope, usages);
            }
        }
    }

    @Override
    protected void refreshElements(PsiElement[] elements) {
        LOG.assertTrue(elements.length == 3);
        myMethod = (PsiMethod)elements[0];
        myTargetVariable = (PsiVariable)elements[1];
        myTargetClass = (PsiClass)elements[2];
    }

    @Nonnull
    @Override
    protected String getCommandName() {
        return RefactoringLocalize.moveInstanceMethodCommand().get();
    }

    public PsiClass getTargetClass() {
        return myTargetClass;
    }

    @Override
    @RequiredUIAccess
    protected void performRefactoring(@Nonnull UsageInfo[] usages) {
        if (!CommonRefactoringUtil.checkReadOnlyStatus(myProject, myTargetClass)) {
            return;
        }

        PsiMethod patternMethod = createMethodToAdd();
        List<PsiReference> docRefs = new ArrayList<>();
        for (UsageInfo usage : usages) {
            if (usage instanceof InheritorUsageInfo inheritorUsageInfo) {
                PsiClass inheritor = inheritorUsageInfo.getInheritor();
                addMethodToClass(inheritor, patternMethod, true);
            }
            else if (usage instanceof MethodCallUsageInfo methodCallUsageInfo && !methodCallUsageInfo.isInternal()) {
                PsiElement expression = methodCallUsageInfo.getMethodCallExpression();
                if (expression instanceof PsiMethodCallExpression methodCall) {
                    correctMethodCall(methodCall, false);
                }
                else if (expression instanceof PsiMethodReferenceExpression methodRefExpr) {
                    PsiExpression newQualifier = JavaPsiFacade.getInstance(myProject)
                        .getElementFactory()
                        .createExpressionFromText(myTargetVariable.getType().getCanonicalText(), null);
                    methodRefExpr.setQualifierExpression(newQualifier);
                }
            }
            else if (usage instanceof JavadocUsageInfo) {
                docRefs.add(usage.getElement().getReference());
            }
        }

        try {
            if (myTargetClass.isInterface()) {
                patternMethod.getBody().delete();
            }

            PsiMethod method = addMethodToClass(myTargetClass, patternMethod, false);
            myMethod.delete();
            for (PsiReference reference : docRefs) {
                reference.bindToElement(method);
            }
            VisibilityUtil.fixVisibility(UsageViewUtil.toElements(usages), method, myNewVisibility);
        }
        catch (IncorrectOperationException e) {
            LOG.error(e);
        }
    }

    @RequiredWriteAction
    private void correctMethodCall(PsiMethodCallExpression expression, boolean isInternalCall) {
        try {
            PsiManager manager = myMethod.getManager();
            PsiReferenceExpression methodExpression = expression.getMethodExpression();
            if (!methodExpression.isReferenceTo(myMethod)) {
                return;
            }
            PsiExpression oldQualifier = methodExpression.getQualifierExpression();
            PsiExpression newQualifier = null;
            PsiClass classReferencedByThis = MoveInstanceMembersUtil.getClassReferencedByThis(methodExpression);
            if (myTargetVariable instanceof PsiParameter parameter) {
                int index = myMethod.getParameterList().getParameterIndex(parameter);
                PsiExpression[] arguments = expression.getArgumentList().getExpressions();
                if (index < arguments.length) {
                    newQualifier = (PsiExpression)arguments[index].copy();
                    arguments[index].delete();
                }
            }
            else if (myTargetVariable instanceof PsiField field) {
                VisibilityUtil.escalateVisibility(field, expression);
                String newQualifierName = field.getName();
                if (myTargetVariable instanceof PsiField && oldQualifier != null) {
                    PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(oldQualifier.getType());
                    if (aClass == field.getContainingClass()) {
                        newQualifierName = oldQualifier.getText() + "." + newQualifierName;
                    }
                }
                newQualifier =
                    JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createExpressionFromText(newQualifierName, null);
            }

            PsiExpression newArgument = null;

            if (classReferencedByThis != null) {
                String thisArgumentText = null;
                if (manager.areElementsEquivalent(myMethod.getContainingClass(), classReferencedByThis)) {
                    if (myOldClassParameterNames.containsKey(myMethod.getContainingClass())) {
                        thisArgumentText = "this";
                    }
                }
                else {
                    thisArgumentText = classReferencedByThis.getName() + ".this";
                }

                if (thisArgumentText != null) {
                    newArgument = JavaPsiFacade.getInstance(manager.getProject())
                        .getElementFactory()
                        .createExpressionFromText(thisArgumentText, null);
                }
            }
            else {
                if (!isInternalCall && oldQualifier != null) {
                    PsiType type = oldQualifier.getType();
                    if (type instanceof PsiClassType classType) {
                        PsiClass resolved = classType.resolve();
                        if (resolved != null && getParameterNameToCreate(resolved) != null) {
                            newArgument =
                                replaceRefsToTargetVariable(oldQualifier);  //replace is needed in case old qualifier is e.g. the same as field as target variable
                        }
                    }
                }
            }


            if (newArgument != null) {
                expression.getArgumentList().add(newArgument);
            }

            if (newQualifier != null) {
                if (newQualifier instanceof PsiThisExpression thisExpr && thisExpr.getQualifier() == null) {
                    //Remove now redundant 'this' qualifier
                    if (oldQualifier != null) {
                        oldQualifier.delete();
                    }
                }
                else {
                    PsiReferenceExpression refExpr = (PsiReferenceExpression)JavaPsiFacade.getInstance(manager.getProject())
                        .getElementFactory()
                        .createExpressionFromText("q." + myMethod.getName(), null);
                    refExpr.getQualifierExpression().replace(newQualifier);
                    methodExpression.replace(refExpr);
                }
            }
        }
        catch (IncorrectOperationException e) {
            LOG.error(e);
        }
    }

    @RequiredWriteAction
    private PsiExpression replaceRefsToTargetVariable(PsiExpression expression) {
        final PsiManager manager = expression.getManager();
        if (expression instanceof PsiReferenceExpression refExpr && refExpr.isReferenceTo(myTargetVariable)) {
            return createThisExpr(manager);
        }

        expression.accept(new JavaRecursiveElementVisitor() {
            @Override
            @RequiredWriteAction
            public void visitReferenceExpression(PsiReferenceExpression expression) {
                super.visitReferenceExpression(expression);
                if (expression.isReferenceTo(myTargetVariable)) {
                    try {
                        expression.replace(createThisExpr(manager));
                    }
                    catch (IncorrectOperationException e) {
                        LOG.error(e);
                    }
                }
            }
        });

        return expression;
    }

    private static PsiExpression createThisExpr(PsiManager manager) {
        try {
            return JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createExpressionFromText("this", null);
        }
        catch (IncorrectOperationException e) {
            LOG.error(e);
            return null;
        }
    }

    @RequiredWriteAction
    private static PsiMethod addMethodToClass(PsiClass aClass, PsiMethod patternMethod, boolean canAddOverride) {
        try {
            PsiMethod method = (PsiMethod)aClass.add(patternMethod);
            ChangeContextUtil.decodeContextInfo(method, null, null);
            if (canAddOverride && OverrideImplementUtil.isInsertOverride(method, aClass)) {
                method.getModifierList().addAnnotation(CommonClassNames.JAVA_LANG_OVERRIDE);
            }
            return method;
        }
        catch (IncorrectOperationException e) {
            LOG.error(e);
        }

        return null;
    }

    private PsiMethod createMethodToAdd() {
        ChangeContextUtil.encodeContextInfo(myMethod, true);
        try {
            final PsiManager manager = myMethod.getManager();
            final PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();

            //correct internal references
            PsiCodeBlock body = myMethod.getBody();
            if (body != null) {
                final Map<PsiElement, PsiElement> replaceMap = new HashMap<>();
                body.accept(new JavaRecursiveElementVisitor() {
                    @Override
                    public void visitThisExpression(@Nonnull PsiThisExpression expression) {
                        PsiClass classReferencedByThis = MoveInstanceMembersUtil.getClassReferencedByThis(expression);
                        if (classReferencedByThis != null && !PsiTreeUtil.isAncestor(myMethod, classReferencedByThis, false)) {
                            PsiElementFactory factory = JavaPsiFacade.getInstance(myProject).getElementFactory();
                            String paramName = getParameterNameToCreate(classReferencedByThis);
                            try {
                                PsiExpression refExpression = factory.createExpressionFromText(paramName, null);
                                replaceMap.put(expression, refExpression);
                            }
                            catch (IncorrectOperationException e) {
                                LOG.error(e);
                            }
                        }
                    }

                    @Override
                    @RequiredWriteAction
                    public void visitReferenceExpression(PsiReferenceExpression expression) {
                        try {
                            PsiExpression qualifier = expression.getQualifierExpression();
                            PsiElement resolved = expression.resolve();
                            if (qualifier instanceof PsiReferenceExpression qRefExpr && qRefExpr.isReferenceTo(myTargetVariable)) {
                                if (resolved instanceof PsiField field) {
                                    for (PsiParameter parameter : myMethod.getParameterList().getParameters()) {
                                        if (Comparing.strEqual(parameter.getName(), field.getName())) {
                                            qualifier.replace(factory.createExpressionFromText("this", null));
                                            return;
                                        }
                                    }
                                }
                                //Target is a field, replace target.m -> m
                                qualifier.delete();
                                return;
                            }
                            if (myTargetVariable.equals(resolved)) {
                                PsiThisExpression thisExpression = RefactoringChangeUtil.createThisExpression(
                                    manager,
                                    PsiTreeUtil.isAncestor(myMethod, PsiTreeUtil.getParentOfType(expression, PsiClass.class), true)
                                        ? myTargetClass
                                        : null
                                );
                                replaceMap.put(expression, thisExpression);
                                return;
                            }
                            else if (myMethod.equals(resolved)) {
                            }
                            else {
                                PsiClass classReferencedByThis = MoveInstanceMembersUtil.getClassReferencedByThis(expression);
                                if (classReferencedByThis != null) {
                                    String paramName = getParameterNameToCreate(classReferencedByThis);
                                    if (paramName != null) {
                                        PsiReferenceExpression newQualifier =
                                            (PsiReferenceExpression)factory.createExpressionFromText(paramName, null);
                                        expression.setQualifierExpression(newQualifier);
                                        return;
                                    }
                                }
                            }
                            super.visitReferenceExpression(expression);
                        }
                        catch (IncorrectOperationException e) {
                            LOG.error(e);
                        }
                    }

                    @Override
                    @RequiredWriteAction
                    public void visitNewExpression(@Nonnull PsiNewExpression expression) {
                        try {
                            PsiExpression qualifier = expression.getQualifier();
                            if (qualifier instanceof PsiReferenceExpression qRefExpr && qRefExpr.isReferenceTo(myTargetVariable)) {
                                //Target is a field, replace target.new A() -> new A()
                                qualifier.delete();
                            }
                            else {
                                PsiClass classReferencedByThis = MoveInstanceMembersUtil.getClassReferencedByThis(expression);
                                if (classReferencedByThis != null) {
                                    if (qualifier != null) {
                                        qualifier.delete();
                                    }
                                    String paramName = getParameterNameToCreate(classReferencedByThis);
                                    PsiExpression newExpression =
                                        factory.createExpressionFromText(paramName + "." + expression.getText(), null);
                                    replaceMap.put(expression, newExpression);
                                }
                            }
                            super.visitNewExpression(expression);
                        }
                        catch (IncorrectOperationException e) {
                            LOG.error(e);
                        }
                    }

                    @Override
                    @RequiredWriteAction
                    public void visitMethodCallExpression(@Nonnull PsiMethodCallExpression expression) {
                        correctMethodCall(expression, true);
                        super.visitMethodCallExpression(expression);
                    }
                });
                for (PsiElement element : replaceMap.keySet()) {
                    PsiElement replacement = replaceMap.get(element);
                    element.replace(replacement);
                }
            }

            PsiMethod methodCopy = getPatternMethod();

            List<PsiParameter> newParameters = Arrays.asList(methodCopy.getParameterList().getParameters());
            RefactoringUtil.fixJavadocsForParams(methodCopy, new HashSet<>(newParameters));
            return methodCopy;
        }
        catch (IncorrectOperationException e) {
            LOG.error(e);
            return myMethod;
        }
    }

    private PsiMethod getPatternMethod() throws IncorrectOperationException {
        PsiMethod methodCopy = (PsiMethod)myMethod.copy();
        String name = myTargetClass.isInterface() ? PsiModifier.PUBLIC
            : !Comparing.strEqual(myNewVisibility, VisibilityUtil.ESCALATE_VISIBILITY) ? myNewVisibility : null;
        if (name != null) {
            PsiUtil.setModifierProperty(methodCopy, name, true);
        }
        if (myTargetVariable instanceof PsiParameter parameter) {
            int index = myMethod.getParameterList().getParameterIndex(parameter);
            methodCopy.getParameterList().getParameters()[index].delete();
        }

        addParameters(JavaPsiFacade.getInstance(myProject).getElementFactory(), methodCopy, myTargetClass.isInterface());
        return methodCopy;
    }

    private void addParameters(PsiElementFactory factory, PsiMethod methodCopy, boolean isInterface) throws IncorrectOperationException {
        Set<Map.Entry<PsiClass, String>> entries = myOldClassParameterNames.entrySet();
        for (Map.Entry<PsiClass, String> entry : entries) {
            PsiClassType type = factory.createType(entry.getKey());
            PsiParameter parameter = factory.createParameter(entry.getValue(), type);
            if (isInterface) {
                PsiUtil.setModifierProperty(parameter, PsiModifier.FINAL, false);
            }
            methodCopy.getParameterList().add(parameter);
        }
    }

    private String getParameterNameToCreate(@Nonnull PsiClass aClass) {
        return myOldClassParameterNames.get(aClass);
    }
}
