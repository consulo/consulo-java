/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.java.impl.refactoring.memberPushDown;

import com.intellij.java.impl.codeInsight.intention.impl.CreateClassDialog;
import com.intellij.java.impl.codeInsight.intention.impl.CreateSubclassAction;
import com.intellij.java.impl.refactoring.listeners.JavaRefactoringListenerManager;
import com.intellij.java.impl.refactoring.listeners.impl.JavaRefactoringListenerManagerImpl;
import com.intellij.java.impl.refactoring.util.RefactoringUtil;
import com.intellij.java.impl.refactoring.util.classMembers.MemberInfo;
import com.intellij.java.indexing.search.searches.ClassInheritorsSearch;
import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.impl.codeInsight.ChangeContextUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import com.intellij.java.language.psi.util.MethodSignatureUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.application.progress.ProgressManager;
import consulo.language.editor.refactoring.BaseRefactoringProcessor;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.ui.util.DocCommentPolicy;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.UIUtil;
import consulo.usage.UsageInfo;
import consulo.usage.UsageViewDescriptor;
import consulo.util.dataholder.Key;
import consulo.util.lang.ref.SimpleReference;
import jakarta.annotation.Nonnull;

import java.util.*;

public class PushDownProcessor extends BaseRefactoringProcessor {
    private static final Logger LOG = Logger.getInstance(PushDownProcessor.class);

    private final MemberInfo[] myMemberInfos;
    private PsiClass myClass;
    private final DocCommentPolicy myJavaDocPolicy;
    private CreateClassDialog myCreateClassDlg;

    public PushDownProcessor(
        Project project,
        MemberInfo[] memberInfos,
        PsiClass aClass,
        DocCommentPolicy javaDocPolicy
    ) {
        super(project);
        myMemberInfos = memberInfos;
        myClass = aClass;
        myJavaDocPolicy = javaDocPolicy;
    }

    @Nonnull
    @Override
    protected String getCommandName() {
        return JavaPushDownHandler.REFACTORING_NAME;
    }

    @Nonnull
    @Override
    protected UsageViewDescriptor createUsageViewDescriptor(@Nonnull UsageInfo[] usages) {
        return new PushDownUsageViewDescriptor(myClass);
    }

    @Nonnull
    @Override
    @RequiredReadAction
    protected UsageInfo[] findUsages() {
        PsiClass[] inheritors = ClassInheritorsSearch.search(myClass, false).toArray(PsiClass.EMPTY_ARRAY);
        UsageInfo[] usages = new UsageInfo[inheritors.length];
        for (int i = 0; i < inheritors.length; i++) {
            usages[i] = new UsageInfo(inheritors[i]);
        }
        return usages;
    }

    @Override
    @RequiredUIAccess
    protected boolean preprocessUsages(@Nonnull SimpleReference<UsageInfo[]> refUsages) {
        UsageInfo[] usagesIn = refUsages.get();
        PushDownConflicts pushDownConflicts = new PushDownConflicts(myClass, myMemberInfos);
        pushDownConflicts.checkSourceClassConflicts();

        if (usagesIn.length == 0) {
            if (myClass.isEnum() || myClass.isFinal()) {
                if (Messages.showOkCancelDialog(
                    (
                        myClass.isEnum()
                            ? "Enum " + myClass.getQualifiedName() + " doesn't have constants to inline to. "
                            : "Final class " + myClass.getQualifiedName() + "does not have inheritors. "
                    ) + "Pushing members down will result in them being deleted. Would you like to proceed?",
                    JavaPushDownHandler.REFACTORING_NAME,
                    UIUtil.getWarningIcon()
                ) != DialogWrapper.OK_EXIT_CODE) {
                    return false;
                }
            }
            else {
                LocalizeValue noInheritors = myClass.isInterface()
                    ? RefactoringLocalize.interface0DoesNotHaveInheritors(myClass.getQualifiedName())
                    : RefactoringLocalize.class0DoesNotHaveInheritors(myClass.getQualifiedName());
                String message = noInheritors + "\n" + RefactoringLocalize.pushDownWillDeleteMembers();
                int answer = Messages.showYesNoCancelDialog(message, JavaPushDownHandler.REFACTORING_NAME, UIUtil.getWarningIcon());
                if (answer == DialogWrapper.OK_EXIT_CODE) {
                    myCreateClassDlg = CreateSubclassAction.chooseSubclassToCreate(myClass);
                    if (myCreateClassDlg != null) {
                        pushDownConflicts.checkTargetClassConflicts(null, false, myCreateClassDlg.getTargetDirectory());
                        return showConflicts(pushDownConflicts.getConflicts(), usagesIn);
                    }
                    else {
                        return false;
                    }
                }
                else if (answer != 1) {
                    return false;
                }
            }
        }
        @RequiredReadAction
        Runnable runnable = () -> {
            for (UsageInfo usage : usagesIn) {
                PsiElement element = usage.getElement();
                if (element instanceof PsiClass psiClass) {
                    pushDownConflicts.checkTargetClassConflicts(psiClass, usagesIn.length > 1, element);
                }
            }
        };

        boolean processFinished = ProgressManager.getInstance().runProcessWithProgressSynchronously(
            runnable,
            RefactoringLocalize.detectingPossibleConflicts().get(),
            true,
            myProject
        );
        return processFinished && showConflicts(pushDownConflicts.getConflicts(), usagesIn);
    }

    @Override
    protected void refreshElements(PsiElement[] elements) {
        if (elements.length == 1 && elements[0] instanceof PsiClass psiClass) {
            myClass = psiClass;
        }
        else {
            LOG.assertTrue(false);
        }
    }

    @Override
    @RequiredUIAccess
    protected void performRefactoring(@Nonnull UsageInfo[] usages) {
        try {
            encodeRefs();
            if (myCreateClassDlg != null) { //usages.length == 0
                PsiClass psiClass =
                    CreateSubclassAction.createSubclass(myClass, myCreateClassDlg.getTargetDirectory(), myCreateClassDlg.getClassName());
                if (psiClass != null) {
                    pushDownToClass(psiClass);
                }
            }
            for (UsageInfo usage : usages) {
                if (usage.getElement() instanceof PsiClass targetClass) {
                    pushDownToClass(targetClass);
                }
            }
            removeFromTargetClass();
        }
        catch (IncorrectOperationException e) {
            LOG.error(e);
        }
    }

    private static final Key<Boolean> REMOVE_QUALIFIER_KEY = Key.create("REMOVE_QUALIFIER_KEY");
    private static final Key<PsiClass> REPLACE_QUALIFIER_KEY = Key.create("REPLACE_QUALIFIER_KEY");

    protected void encodeRefs() {
        final Set<PsiMember> movedMembers = new HashSet<>();
        for (MemberInfo memberInfo : myMemberInfos) {
            movedMembers.add(memberInfo.getMember());
        }

        for (MemberInfo memberInfo : myMemberInfos) {
            PsiMember member = memberInfo.getMember();
            member.accept(new JavaRecursiveElementVisitor() {
                @Override
                @RequiredReadAction
                public void visitReferenceExpression(PsiReferenceExpression expression) {
                    encodeRef(expression, movedMembers, expression);
                    super.visitReferenceExpression(expression);
                }

                @Override
                @RequiredReadAction
                public void visitNewExpression(@Nonnull PsiNewExpression expression) {
                    PsiJavaCodeReferenceElement classReference = expression.getClassReference();
                    if (classReference != null) {
                        encodeRef(classReference, movedMembers, expression);
                    }
                    super.visitNewExpression(expression);
                }

                @Override
                @RequiredReadAction
                public void visitTypeElement(@Nonnull PsiTypeElement type) {
                    PsiJavaCodeReferenceElement referenceElement = type.getInnermostComponentReferenceElement();
                    if (referenceElement != null) {
                        encodeRef(referenceElement, movedMembers, type);
                    }
                    super.visitTypeElement(type);
                }
            });
            ChangeContextUtil.encodeContextInfo(member, false);
        }
    }

    @RequiredReadAction
    private void encodeRef(PsiJavaCodeReferenceElement expression, Set<PsiMember> movedMembers, PsiElement toPut) {
        PsiElement resolved = expression.resolve();
        if (resolved == null) {
            return;
        }
        PsiElement qualifier = expression.getQualifier();
        for (PsiMember movedMember : movedMembers) {
            if (movedMember.equals(resolved)) {
                if (qualifier == null) {
                    toPut.putCopyableUserData(REMOVE_QUALIFIER_KEY, Boolean.TRUE);
                }
                else {
                    if (qualifier instanceof PsiJavaCodeReferenceElement referenceElement && referenceElement.isReferenceTo(myClass)) {
                        toPut.putCopyableUserData(REPLACE_QUALIFIER_KEY, myClass);
                    }
                }
            }
            else if (movedMember instanceof PsiClass movedClass
                && PsiTreeUtil.getParentOfType(resolved, PsiClass.class, false) == movedClass) {
                if (qualifier instanceof PsiJavaCodeReferenceElement reference && reference.isReferenceTo(movedClass)) {
                    toPut.putCopyableUserData(REPLACE_QUALIFIER_KEY, movedClass);
                }
            }
            else {
                if (qualifier instanceof PsiThisExpression thisExpression) {
                    PsiJavaCodeReferenceElement qElement = thisExpression.getQualifier();
                    if (qElement != null && qElement.isReferenceTo(myClass)) {
                        toPut.putCopyableUserData(REPLACE_QUALIFIER_KEY, myClass);
                    }
                }
            }
        }
    }

    private void decodeRefs(PsiMember member, final PsiClass targetClass) {
        try {
            ChangeContextUtil.decodeContextInfo(member, null, null);
        }
        catch (IncorrectOperationException e) {
            LOG.error(e);
        }

        final PsiElementFactory factory = JavaPsiFacade.getInstance(myProject).getElementFactory();
        member.accept(new JavaRecursiveElementWalkingVisitor() {
            @Override
            @RequiredReadAction
            public void visitReferenceExpression(PsiReferenceExpression expression) {
                decodeRef(expression, factory, targetClass, expression);
                super.visitReferenceExpression(expression);
            }

            @Override
            @RequiredReadAction
            public void visitNewExpression(@Nonnull PsiNewExpression expression) {
                PsiJavaCodeReferenceElement classReference = expression.getClassReference();
                if (classReference != null) {
                    decodeRef(classReference, factory, targetClass, expression);
                }
                super.visitNewExpression(expression);
            }

            @Override
            @RequiredReadAction
            public void visitTypeElement(@Nonnull PsiTypeElement type) {
                PsiJavaCodeReferenceElement referenceElement = type.getInnermostComponentReferenceElement();
                if (referenceElement != null) {
                    decodeRef(referenceElement, factory, targetClass, type);
                }
                super.visitTypeElement(type);
            }
        });
    }

    @RequiredReadAction
    private void decodeRef(PsiJavaCodeReferenceElement ref, PsiElementFactory factory, PsiClass targetClass, PsiElement toGet) {
        try {
            if (toGet.getCopyableUserData(REMOVE_QUALIFIER_KEY) != null) {
                toGet.putCopyableUserData(REMOVE_QUALIFIER_KEY, null);
                PsiElement qualifier = ref.getQualifier();
                if (qualifier != null) {
                    qualifier.delete();
                }
            }
            else {
                PsiClass psiClass = toGet.getCopyableUserData(REPLACE_QUALIFIER_KEY);
                if (psiClass != null) {
                    toGet.putCopyableUserData(REPLACE_QUALIFIER_KEY, null);
                    PsiElement qualifier = ref.getQualifier();
                    if (qualifier != null) {

                        if (psiClass == myClass) {
                            psiClass = targetClass;
                        }
                        else if (psiClass.getContainingClass() == myClass) {
                            psiClass = targetClass.findInnerClassByName(psiClass.getName(), false);
                            LOG.assertTrue(psiClass != null);
                        }

                        if (!(qualifier instanceof PsiThisExpression) && ref instanceof PsiReferenceExpression refExpr) {
                            refExpr.setQualifierExpression(factory.createReferenceExpression(psiClass));
                        }
                        else {
                            if (qualifier instanceof PsiThisExpression thisExpr) {
                                qualifier = thisExpr.getQualifier();
                            }
                            qualifier.replace(factory.createReferenceElementByType(factory.createType(psiClass)));
                        }
                    }
                }
            }
        }
        catch (IncorrectOperationException e) {
            LOG.error(e);
        }
    }

    @RequiredWriteAction
    private void removeFromTargetClass() throws IncorrectOperationException {
        for (MemberInfo memberInfo : myMemberInfos) {
            PsiElement member = memberInfo.getMember();

            if (member instanceof PsiField) {
                member.delete();
            }
            else if (member instanceof PsiMethod method) {
                if (memberInfo.isToAbstract()) {
                    if (method.isPrivate()) {
                        PsiUtil.setModifierProperty(method, PsiModifier.PROTECTED, true);
                    }
                    RefactoringUtil.makeMethodAbstract(myClass, method);
                    myJavaDocPolicy.processOldJavaDoc(method.getDocComment());
                }
                else {
                    member.delete();
                }
            }
            else if (member instanceof PsiClass psiClass) {
                if (Boolean.FALSE.equals(memberInfo.getOverrides())) {
                    RefactoringUtil.removeFromReferenceList(myClass.getImplementsList(), psiClass);
                }
                else {
                    member.delete();
                }
            }
        }
    }

    @RequiredUIAccess
    protected void pushDownToClass(PsiClass targetClass) throws IncorrectOperationException {
        PsiElementFactory factory = JavaPsiFacade.getInstance(myClass.getProject()).getElementFactory();
        PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(myClass, targetClass, PsiSubstitutor.EMPTY);
        for (MemberInfo memberInfo : myMemberInfos) {
            PsiMember member = memberInfo.getMember();
            List<PsiReference> refsToRebind = new ArrayList<>();
            PsiModifierList list = member.getModifierList();
            LOG.assertTrue(list != null);
            if (list.hasModifierProperty(PsiModifier.STATIC)) {
                for (PsiReference reference : ReferencesSearch.search(member)) {
                    PsiElement element = reference.getElement();
                    if (element instanceof PsiReferenceExpression referenceExpression) {
                        PsiExpression qualifierExpression = referenceExpression.getQualifierExpression();
                        if (qualifierExpression instanceof PsiReferenceExpression refExpr && !(refExpr.resolve() instanceof PsiClass)) {
                            continue;
                        }
                    }
                    refsToRebind.add(reference);
                }
            }
            member = (PsiMember)member.copy();
            RefactoringUtil.replaceMovedMemberTypeParameters(member, PsiUtil.typeParametersIterable(myClass), substitutor, factory);
            PsiMember newMember = null;
            if (member instanceof PsiField field) {
                field.normalizeDeclaration();
                newMember = (PsiMember)targetClass.add(member);
            }
            else if (member instanceof PsiMethod method) {
                PsiMethod methodBySignature =
                    MethodSignatureUtil.findMethodBySuperSignature(targetClass, method.getSignature(substitutor), false);
                if (methodBySignature == null) {
                    newMember = (PsiMethod)targetClass.add(method);
                    if (myClass.isInterface()) {
                        if (!targetClass.isInterface()) {
                            PsiUtil.setModifierProperty(newMember, PsiModifier.PUBLIC, true);
                            if (newMember.hasModifierProperty(PsiModifier.DEFAULT)) {
                                PsiUtil.setModifierProperty(newMember, PsiModifier.DEFAULT, false);
                            }
                            else {
                                PsiUtil.setModifierProperty(newMember, PsiModifier.ABSTRACT, true);
                            }
                        }
                    }
                    else if (memberInfo.isToAbstract()) {
                        if (newMember.isPrivate()) {
                            PsiUtil.setModifierProperty(newMember, PsiModifier.PROTECTED, true);
                        }
                        myJavaDocPolicy.processNewJavaDoc(((PsiMethod)newMember).getDocComment());
                    }
                }
                else { //abstract method: remove @Override
                    PsiAnnotation annotation = AnnotationUtil.findAnnotation(methodBySignature, CommonClassNames.JAVA_LANG_OVERRIDE);
                    if (annotation != null && !leaveOverrideAnnotation(substitutor, method)) {
                        annotation.delete();
                    }
                    PsiDocComment oldDocComment = method.getDocComment();
                    if (oldDocComment != null) {
                        PsiDocComment docComment = methodBySignature.getDocComment();
                        int policy = myJavaDocPolicy.getJavaDocPolicy();
                        if (policy == DocCommentPolicy.COPY || policy == DocCommentPolicy.MOVE) {
                            if (docComment != null) {
                                docComment.replace(oldDocComment);
                            }
                            else {
                                methodBySignature.getParent().addBefore(oldDocComment, methodBySignature);
                            }
                        }
                    }
                }
            }
            else if (member instanceof PsiClass) {
                if (Boolean.FALSE.equals(memberInfo.getOverrides())) {
                    PsiClass aClass = (PsiClass)memberInfo.getMember();
                    PsiClassType classType = null;
                    if (!targetClass.isInheritor(aClass, false)) {
                        PsiClassType[] types = memberInfo.getSourceReferenceList().getReferencedTypes();
                        for (PsiClassType type : types) {
                            if (type.resolve() == aClass) {
                                classType = (PsiClassType)substitutor.substitute(type);
                            }
                        }
                        PsiJavaCodeReferenceElement classRef = classType != null
                            ? factory.createReferenceElementByType(classType)
                            : factory.createClassReferenceElement(aClass);
                        if (aClass.isInterface()) {
                            targetClass.getImplementsList().add(classRef);
                        }
                        else {
                            targetClass.getExtendsList().add(classRef);
                        }
                    }
                }
                else {
                    newMember = (PsiMember)targetClass.add(member);
                }
            }

            if (newMember != null) {
                decodeRefs(newMember, targetClass);
                //rebind imports first
                Collections.sort(refsToRebind, (o1, o2) -> PsiUtil.BY_POSITION.compare(o1.getElement(), o2.getElement()));
                for (PsiReference psiReference : refsToRebind) {
                    JavaCodeStyleManager.getInstance(myProject).shortenClassReferences(psiReference.bindToElement(newMember));
                }
                JavaRefactoringListenerManager listenerManager = JavaRefactoringListenerManager.getInstance(newMember.getProject());
                ((JavaRefactoringListenerManagerImpl)listenerManager).fireMemberMoved(myClass, newMember);
            }
        }
    }

    @RequiredReadAction
    private boolean leaveOverrideAnnotation(PsiSubstitutor substitutor, PsiMethod method) {
        PsiMethod methodBySignature = MethodSignatureUtil.findMethodBySignature(myClass, method.getSignature(substitutor), false);
        if (methodBySignature == null) {
            return false;
        }
        PsiMethod[] superMethods = methodBySignature.findDeepestSuperMethods();
        if (superMethods.length == 0) {
            return false;
        }
        boolean is15 = !PsiUtil.isLanguageLevel6OrHigher(methodBySignature);
        if (is15) {
            for (PsiMethod psiMethod : superMethods) {
                PsiClass aClass = psiMethod.getContainingClass();
                if (aClass != null && aClass.isInterface()) {
                    return false;
                }
            }
        }
        return true;
    }
}
