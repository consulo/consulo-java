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

/*
 * User: anna
 * Date: 27-Aug-2008
 */
package com.intellij.java.impl.refactoring.inlineSuperClass;

import com.intellij.java.impl.refactoring.inlineSuperClass.usageInfo.*;
import com.intellij.java.impl.refactoring.memberPushDown.PushDownConflicts;
import com.intellij.java.impl.refactoring.memberPushDown.PushDownProcessor;
import com.intellij.java.impl.refactoring.util.FixableUsageInfo;
import com.intellij.java.impl.refactoring.util.FixableUsagesRefactoringProcessor;
import com.intellij.java.impl.refactoring.util.classMembers.MemberInfo;
import com.intellij.java.impl.refactoring.util.classMembers.MemberInfoStorage;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiTypesUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.editor.ui.util.DocCommentPolicy;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.usage.UsageInfo;
import consulo.usage.UsageViewDescriptor;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.MultiMap;
import consulo.util.lang.ref.SimpleReference;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InlineSuperClassRefactoringProcessor extends FixableUsagesRefactoringProcessor {
    public static final Logger LOG = Logger.getInstance(InlineSuperClassRefactoringProcessor.class);

    private final PsiClass myCurrentInheritor;
    private final PsiClass mySuperClass;
    private final int myPolicy;
    private final PsiClass[] myTargetClasses;
    private final MemberInfo[] myMemberInfos;

    public InlineSuperClassRefactoringProcessor(
        Project project,
        PsiClass currentInheritor,
        PsiClass superClass,
        int policy,
        PsiClass... targetClasses
    ) {
        super(project);
        myCurrentInheritor = currentInheritor;
        mySuperClass = superClass;
        myPolicy = policy;
        myTargetClasses = currentInheritor != null ? new PsiClass[]{currentInheritor} : targetClasses;
        MemberInfoStorage memberInfoStorage = new MemberInfoStorage(
            mySuperClass,
            element -> !(element instanceof PsiClass psiClass && !PsiTreeUtil.isAncestor(mySuperClass, psiClass, true))
        );
        List<MemberInfo> members = memberInfoStorage.getClassMemberInfos(mySuperClass);
        for (MemberInfo member : members) {
            member.setChecked(true);
        }
        myMemberInfos = members.toArray(new MemberInfo[members.size()]);
    }

    @Nonnull
    @Override
    protected UsageViewDescriptor createUsageViewDescriptor(@Nonnull UsageInfo[] usages) {
        return new InlineSuperClassUsageViewDescriptor(mySuperClass);
    }

    @Override
    @RequiredReadAction
    protected void findUsages(@Nonnull List<FixableUsageInfo> usages) {
        JavaPsiFacade facade = JavaPsiFacade.getInstance(myProject);
        PsiElementFactory elementFactory = facade.getElementFactory();
        PsiResolveHelper resolveHelper = facade.getResolveHelper();

        ReferencesSearch.search(mySuperClass).forEach(reference -> {
            PsiElement element = reference.getElement();
            if (element instanceof PsiJavaCodeReferenceElement codeRef) {
                if (myCurrentInheritor != null) {
                    if (element.getParent() instanceof PsiReferenceList refList
                        && refList.getParent() instanceof PsiClass inheritor
                        && (refList.equals(inheritor.getExtendsList()) || refList.equals(inheritor.getImplementsList()))
                        && myCurrentInheritor.equals(inheritor)) {
                        usages.add(new ReplaceExtendsListUsageInfo(codeRef, mySuperClass, inheritor));
                    }
                    return true;
                }
                PsiImportStaticStatement staticImportStatement = PsiTreeUtil.getParentOfType(element, PsiImportStaticStatement.class);
                if (staticImportStatement != null) {
                    usages.add(new ReplaceStaticImportUsageInfo(staticImportStatement, myTargetClasses));
                }
                else {
                    PsiImportStatement importStatement = PsiTreeUtil.getParentOfType(element, PsiImportStatement.class);
                    if (importStatement != null) {
                        usages.add(new RemoveImportUsageInfo(importStatement));
                    }
                    else if (element.getParent() instanceof PsiReferenceList refList) {
                        if (refList.getParent() instanceof PsiClass inheritor
                            && (refList.equals(inheritor.getExtendsList()) || refList.equals(inheritor.getImplementsList()))) {
                            usages.add(new ReplaceExtendsListUsageInfo(
                                (PsiJavaCodeReferenceElement) element,
                                mySuperClass,
                                inheritor
                            ));
                        }
                    }
                    else {
                        PsiClass targetClass = myTargetClasses[0];
                        PsiClassType targetClassType = elementFactory.createType(
                            targetClass,
                            TypeConversionUtil.getSuperClassSubstitutor(mySuperClass, targetClass, PsiSubstitutor.EMPTY)
                        );

                        if (element.getParent() instanceof PsiTypeElement typeElem) {
                            PsiType superClassType = typeElem.getType();
                            PsiSubstitutor subst = getSuperClassSubstitutor(superClassType, targetClassType, resolveHelper, targetClass);
                            usages.add(new ReplaceWithSubtypeUsageInfo(
                                typeElem,
                                elementFactory.createType(targetClass, subst),
                                myTargetClasses
                            ));
                        }
                        else if (element.getParent() instanceof PsiNewExpression newExpr) {
                            PsiClassType newType = elementFactory.createType(
                                targetClass,
                                getSuperClassSubstitutor(
                                    newExpr.getType(),
                                    targetClassType,
                                    resolveHelper,
                                    targetClass
                                )
                            );
                            usages.add(new ReplaceConstructorUsageInfo(newExpr, newType, myTargetClasses));
                        }
                        else if (element.getParent() instanceof PsiJavaCodeReferenceElement parentCodeRef) {
                            usages.add(new ReplaceReferenceUsageInfo(parentCodeRef.getQualifier(), myTargetClasses));
                        }
                    }
                }
            }
            return true;
        });
        for (PsiClass targetClass : myTargetClasses) {
            for (MemberInfo memberInfo : myMemberInfos) {
                PsiMember member = memberInfo.getMember();
                for (PsiReference reference : ReferencesSearch.search(member, member.getUseScope(), true)) {
                    if (reference.getElement() instanceof PsiReferenceExpression refExpr
                        && refExpr.getQualifierExpression() instanceof PsiSuperExpression
                        && PsiTreeUtil.isAncestor(targetClass, refExpr, false)) {
                        usages.add(new RemoveQualifierUsageInfo(refExpr));
                    }
                }
            }

            PsiMethod[] superConstructors = mySuperClass.getConstructors();
            for (PsiMethod constructor : targetClass.getConstructors()) {
                PsiCodeBlock constrBody = constructor.getBody();
                LOG.assertTrue(constrBody != null);
                PsiStatement[] statements = constrBody.getStatements();
                if (statements.length > 0 && statements[0] instanceof PsiExpressionStatement exprStmt
                    && exprStmt.getExpression() instanceof PsiMethodCallExpression methodCall
                    && methodCall.getMethodExpression().getText().equals(PsiKeyword.SUPER)) {
                    PsiMethod superConstructor = methodCall.resolveMethod();
                    if (superConstructor != null && superConstructor.getBody() != null) {
                        usages.add(new InlineSuperCallUsageInfo(methodCall));
                        continue;
                    }
                }

                //insert implicit call to super
                for (PsiMethod superConstructor : superConstructors) {
                    if (superConstructor.getParameterList().getParametersCount() == 0) {
                        PsiExpression expression =
                            JavaPsiFacade.getElementFactory(myProject).createExpressionFromText("super()", constructor);
                        usages.add(new InlineSuperCallUsageInfo((PsiMethodCallExpression) expression, constrBody));
                    }
                }
            }

            if (targetClass.getConstructors().length == 0) {
                //copy default constructor
                for (PsiMethod superConstructor : superConstructors) {
                    if (superConstructor.getParameterList().getParametersCount() == 0) {
                        usages.add(new CopyDefaultConstructorUsageInfo(targetClass, superConstructor));
                        break;
                    }
                }
            }
        }
    }

    @Override
    @RequiredUIAccess
    protected boolean preprocessUsages(@Nonnull SimpleReference<UsageInfo[]> refUsages) {
        MultiMap<PsiElement, String> conflicts = new MultiMap<>();
        PushDownConflicts pushDownConflicts = new PushDownConflicts(mySuperClass, myMemberInfos);
        for (PsiClass targetClass : myTargetClasses) {
            for (MemberInfo info : myMemberInfos) {
                PsiMember member = info.getMember();
                pushDownConflicts.checkMemberPlacementInTargetClassConflict(targetClass, member);
            }
            //todo check accessibility conflicts
        }
        MultiMap<PsiElement, String> conflictsMap = pushDownConflicts.getConflicts();
        for (PsiElement element : conflictsMap.keySet()) {
            conflicts.put(element, conflictsMap.get(element));
        }
        if (myCurrentInheritor != null) {
            ReferencesSearch.search(myCurrentInheritor).forEach(reference -> {
                PsiElement element = reference.getElement();
                if (element != null && element.getParent() instanceof PsiNewExpression newExpr
                    && PsiUtil.resolveClassInType(getPlaceExpectedType(newExpr)) == mySuperClass) {
                    conflicts.putValue(newExpr, "Instance of target type is passed to a place where super class is expected.");
                    return false;
                }
                return true;
            });
        }
        checkConflicts(refUsages, conflicts);
        return showConflicts(conflicts, refUsages.get());
    }

    @Nullable
    private static PsiType getPlaceExpectedType(PsiElement parent) {
        PsiType type = PsiTypesUtil.getExpectedTypeByParent(parent);
        if (type == null) {
            PsiElement arg = PsiUtil.skipParenthesizedExprUp(parent);
            if (arg.getParent() instanceof PsiExpressionList exprList) {
                int i = ArrayUtil.find(exprList.getExpressions(), arg);
                if (exprList.getParent() instanceof PsiCallExpression callExpr) {
                    PsiMethod method = callExpr.resolveMethod();
                    if (method != null) {
                        PsiParameter[] parameters = method.getParameterList().getParameters();
                        if (i >= parameters.length) {
                            if (method.isVarArgs()) {
                                return ((PsiEllipsisType) parameters[parameters.length - 1].getType()).getComponentType();
                            }
                        }
                        else {
                            return parameters[i].getType();
                        }
                    }
                }
            }
        }
        return type;
    }

    @Override
    @RequiredUIAccess
    protected void performRefactoring(@Nonnull UsageInfo[] usages) {
        new PushDownProcessor(mySuperClass.getProject(), myMemberInfos, mySuperClass, new DocCommentPolicy(myPolicy)) {
            //push down conflicts are already collected
            @Override
            @RequiredUIAccess
            protected boolean showConflicts(@Nonnull MultiMap<PsiElement, String> conflicts, UsageInfo[] usages) {
                return true;
            }

            @Override
            @RequiredUIAccess
            @RequiredWriteAction
            protected void performRefactoring(@Nonnull UsageInfo[] pushDownUsages) {
                if (myCurrentInheritor != null) {
                    encodeRefs();
                    pushDownToClass(myCurrentInheritor);
                }
                else {
                    super.performRefactoring(pushDownUsages);
                }
                CommonRefactoringUtil.sortDepthFirstRightLeftOrder(usages);
                for (UsageInfo usageInfo : usages) {
                    if (!(usageInfo instanceof ReplaceExtendsListUsageInfo || usageInfo instanceof RemoveImportUsageInfo)) {
                        try {
                            ((FixableUsageInfo) usageInfo).fixUsage();
                        }
                        catch (IncorrectOperationException e) {
                            LOG.info(e);
                        }
                    }
                }
                replaceInnerTypeUsages();

                //postpone broken hierarchy
                for (UsageInfo usage : usages) {
                    if (usage instanceof ReplaceExtendsListUsageInfo || usage instanceof RemoveImportUsageInfo) {
                        ((FixableUsageInfo) usage).fixUsage();
                    }
                }
                if (myCurrentInheritor == null) {
                    try {
                        mySuperClass.delete();
                    }
                    catch (IncorrectOperationException e) {
                        LOG.error(e);
                    }
                }
            }
        }.run();
    }

    @RequiredWriteAction
    private void replaceInnerTypeUsages() {
        JavaPsiFacade facade = JavaPsiFacade.getInstance(myProject);
        PsiElementFactory elementFactory = facade.getElementFactory();
        PsiResolveHelper resolveHelper = facade.getResolveHelper();
        Map<UsageInfo, PsiElement> replacementMap = new HashMap<>();
        for (PsiClass targetClass : myTargetClasses) {
            PsiSubstitutor superClassSubstitutor =
                TypeConversionUtil.getSuperClassSubstitutor(mySuperClass, targetClass, PsiSubstitutor.EMPTY);
            PsiClassType targetClassType = elementFactory.createType(targetClass, superClassSubstitutor);
            targetClass.accept(new JavaRecursiveElementWalkingVisitor() {
                @Override
                @RequiredReadAction
                public void visitTypeElement(@Nonnull PsiTypeElement typeElement) {
                    super.visitTypeElement(typeElement);
                    PsiType superClassType = typeElement.getType();
                    if (PsiUtil.resolveClassInType(superClassType) == mySuperClass) {
                        PsiSubstitutor subst = getSuperClassSubstitutor(superClassType, targetClassType, resolveHelper, targetClass);
                        replacementMap.put(
                            new UsageInfo(typeElement),
                            elementFactory.createTypeElement(elementFactory.createType(targetClass, subst))
                        );
                    }
                }

                @Override
                @RequiredReadAction
                public void visitNewExpression(@Nonnull PsiNewExpression expression) {
                    super.visitNewExpression(expression);
                    PsiType superClassType = expression.getType();
                    if (PsiUtil.resolveClassInType(superClassType) == mySuperClass) {
                        PsiSubstitutor subst = getSuperClassSubstitutor(superClassType, targetClassType, resolveHelper, targetClass);
                        try {
                            replacementMap.put(
                                new UsageInfo(expression),
                                elementFactory.createExpressionFromText(
                                    "new " + elementFactory.createType(targetClass, subst).getCanonicalText() +
                                        expression.getArgumentList().getText(),
                                    expression
                                )
                            );
                        }
                        catch (IncorrectOperationException e) {
                            LOG.error(e);
                        }
                    }
                }
            });
        }
        try {
            for (Map.Entry<UsageInfo, PsiElement> elementEntry : replacementMap.entrySet()) {
                PsiElement element = elementEntry.getKey().getElement();
                if (element != null) {
                    element.replace(elementEntry.getValue());
                }
            }
        }
        catch (IncorrectOperationException e) {
            LOG.error(e);
        }
    }

    @RequiredReadAction
    private static PsiSubstitutor getSuperClassSubstitutor(
        PsiType superClassType,
        PsiClassType targetClassType,
        PsiResolveHelper resolveHelper,
        PsiClass targetClass
    ) {
        PsiSubstitutor subst = PsiSubstitutor.EMPTY;
        for (PsiTypeParameter typeParameter : PsiUtil.typeParametersIterable(targetClass)) {
            subst = subst.put(
                typeParameter,
                resolveHelper.getSubstitutionForTypeParameter(
                    typeParameter,
                    targetClassType,
                    superClassType,
                    false,
                    PsiUtil.getLanguageLevel(targetClass)
                )
            );
        }
        return subst;
    }

    @Nonnull
    @Override
    protected String getCommandName() {
        return InlineSuperClassRefactoringHandler.REFACTORING_NAME.get();
    }
}
