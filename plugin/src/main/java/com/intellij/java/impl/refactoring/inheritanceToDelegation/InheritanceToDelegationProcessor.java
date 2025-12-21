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
package com.intellij.java.impl.refactoring.inheritanceToDelegation;

import com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.java.impl.codeInsight.generation.GenerateMembersUtil;
import com.intellij.java.impl.codeInsight.generation.OverrideImplementUtil;
import com.intellij.java.impl.refactoring.inheritanceToDelegation.usageInfo.*;
import com.intellij.java.impl.refactoring.util.ConflictsUtil;
import com.intellij.java.impl.refactoring.util.classMembers.ClassMemberReferencesVisitor;
import com.intellij.java.impl.refactoring.util.classRefs.ClassInstanceScanner;
import com.intellij.java.impl.refactoring.util.classRefs.ClassReferenceScanner;
import com.intellij.java.impl.refactoring.util.classRefs.ClassReferenceSearchingScanner;
import com.intellij.java.indexing.search.searches.ClassInheritorsSearch;
import com.intellij.java.language.codeInsight.NullableNotNullManager;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.codeStyle.VariableKind;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import com.intellij.java.language.psi.util.*;
import com.intellij.java.language.util.VisibilityUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.ide.impl.find.PsiElement2UsageTargetAdapter;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.refactoring.BaseRefactoringProcessor;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.ui.ConflictsDialog;
import consulo.language.editor.refactoring.ui.RefactoringUIUtil;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.findUsage.DescriptiveNameUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.usage.*;
import consulo.util.collection.MultiMap;
import consulo.util.lang.ref.SimpleReference;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;

/**
 * @author dsl
 */
public class InheritanceToDelegationProcessor extends BaseRefactoringProcessor {
    private static final Logger LOG = Logger.getInstance(InheritanceToDelegationProcessor.class);
    private final PsiClass myClass;
    private final String myInnerClassName;
    private final boolean myIsDelegateOtherMembers;
    private final Set<PsiClass> myDelegatedInterfaces;
    private final Set<PsiMethod> myDelegatedMethods;
    private final Map<PsiMethod, String> myDelegatedMethodsVisibility;
    private final Set<PsiMethod> myOverriddenMethods;

    private final PsiClass myBaseClass;
    private final Set<PsiMember> myBaseClassMembers;
    private final String myFieldName;
    private final String myGetterName;
    private final boolean myGenerateGetter;
    private final Set<PsiClass> myBaseClassBases;
    private Set<PsiClass> myClassImplementedInterfaces;
    private final PsiElementFactory myFactory;
    private final PsiClassType myBaseClassType;
    private final PsiManager myManager;
    private final boolean myIsInnerClassNeeded;
    private Set<PsiClass> myClassInheritors;
    private Set<PsiMethod> myAbstractDelegatedMethods;
    private final Map<PsiClass, PsiSubstitutor> mySuperClassesToSubstitutors = new HashMap<>();

    public InheritanceToDelegationProcessor(
        Project project,
        PsiClass aClass,
        PsiClass targetBaseClass,
        String fieldName,
        String innerClassName,
        PsiClass[] delegatedInterfaces,
        PsiMethod[] delegatedMethods,
        boolean delegateOtherMembers,
        boolean generateGetter
    ) {
        super(project);

        myClass = aClass;
        myInnerClassName = innerClassName;
        myIsDelegateOtherMembers = delegateOtherMembers;
        myManager = myClass.getManager();
        myFactory = JavaPsiFacade.getInstance(myManager.getProject()).getElementFactory();

        myBaseClass = targetBaseClass;
        LOG.assertTrue(
            myBaseClass != null // && !myBaseClass.isInterface()
                && (myBaseClass.getQualifiedName() == null || !myBaseClass.getQualifiedName().equals(CommonClassNames.JAVA_LANG_OBJECT))
        );
        myBaseClassMembers = getAllBaseClassMembers();
        myBaseClassBases = getAllBases();
        myBaseClassType = myFactory.createType(myBaseClass, getSuperSubstitutor(myBaseClass));

        myIsInnerClassNeeded = InheritanceToDelegationUtil.isInnerClassNeeded(myClass, myBaseClass);


        myFieldName = fieldName;
        String propertyName = JavaCodeStyleManager.getInstance(myProject).variableNameToPropertyName(myFieldName, VariableKind.FIELD);
        myGetterName = PropertyUtil.suggestGetterName(propertyName, myBaseClassType);
        myGenerateGetter = generateGetter;

        myDelegatedInterfaces = new LinkedHashSet<>();
        addAll(myDelegatedInterfaces, delegatedInterfaces);
        myDelegatedMethods = new LinkedHashSet<>();
        addAll(myDelegatedMethods, delegatedMethods);
        myDelegatedMethodsVisibility = new HashMap<>();
        for (PsiMethod method : myDelegatedMethods) {
            MethodSignature signature = method.getSignature(getSuperSubstitutor(method.getContainingClass()));
            PsiMethod overridingMethod = MethodSignatureUtil.findMethodBySignature(myClass, signature, false);
            if (overridingMethod != null) {
                myDelegatedMethodsVisibility.put(method, VisibilityUtil.getVisibilityModifier(overridingMethod.getModifierList()));
            }
        }

        myOverriddenMethods = getOverriddenMethods();
    }

    private PsiSubstitutor getSuperSubstitutor(PsiClass superClass) {
        PsiSubstitutor result = mySuperClassesToSubstitutors.get(superClass);
        if (result == null) {
            result = TypeConversionUtil.getSuperClassSubstitutor(superClass, myClass, PsiSubstitutor.EMPTY);
            mySuperClassesToSubstitutors.put(superClass, result);
        }
        return result;
    }

    @Nonnull
    @Override
    protected UsageViewDescriptor createUsageViewDescriptor(@Nonnull UsageInfo[] usages) {
        return new InheritanceToDelegationViewDescriptor(myClass);
    }

    @Nonnull
    @Override
    protected UsageInfo[] findUsages() {
        List<UsageInfo> usages = new ArrayList<>();
        PsiClass[] inheritors = ClassInheritorsSearch.search(myClass, true).toArray(PsiClass.EMPTY_ARRAY);
        myClassInheritors = new HashSet<>();
        myClassInheritors.add(myClass);
        addAll(myClassInheritors, inheritors);

        {
            ClassReferenceScanner scanner = new ClassReferenceSearchingScanner(myClass);
            MyClassInstanceReferenceVisitor instanceReferenceVisitor = new MyClassInstanceReferenceVisitor(myClass, usages);
            scanner.processReferences(new ClassInstanceScanner(myClass, instanceReferenceVisitor));

            MyClassMemberReferencesVisitor visitor = new MyClassMemberReferencesVisitor(usages, instanceReferenceVisitor);
            myClass.accept(visitor);

            myClassImplementedInterfaces = instanceReferenceVisitor.getImplementedInterfaces();
        }
        for (PsiClass inheritor : inheritors) {
            processClass(inheritor, usages);
        }

        return usages.toArray(new UsageInfo[usages.size()]);
    }

    private FieldAccessibility getFieldAccessibility(PsiElement element) {
        for (PsiClass aClass : myClassInheritors) {
            if (PsiTreeUtil.isAncestor(aClass, element, false)) {
                return new FieldAccessibility(true, aClass);
            }
        }
        return FieldAccessibility.INVISIBLE;
    }

    @Override
    @RequiredUIAccess
    protected boolean preprocessUsages(@Nonnull SimpleReference<UsageInfo[]> refUsages) {
        UsageInfo[] usagesIn = refUsages.get();
        List<UsageInfo> oldUsages = new ArrayList<>();
        addAll(oldUsages, usagesIn);
        ObjectUpcastedUsageInfo[] objectUpcastedUsageInfos = objectUpcastedUsages(usagesIn);
        if (myPrepareSuccessfulSwingThreadCallback != null) {
            MultiMap<PsiElement, LocalizeValue> conflicts = new MultiMap<>();
            if (objectUpcastedUsageInfos.length > 0) {
                LocalizeValue message = RefactoringLocalize.instancesOf0UpcastedTo1WereFound(
                    RefactoringUIUtil.getDescription(myClass, true),
                    CommonRefactoringUtil.htmlEmphasize(CommonClassNames.JAVA_LANG_OBJECT)
                );

                conflicts.putValue(myClass, message);
            }

            analyzeConflicts(usagesIn, conflicts);
            if (!conflicts.isEmpty()) {
                ConflictsDialog conflictsDialog = prepareConflictsDialog(conflicts, usagesIn);
                conflictsDialog.show();
                if (!conflictsDialog.isOK()) {
                    if (conflictsDialog.isShowConflicts()) {
                        prepareSuccessful();
                    }
                    return false;
                }
            }

            if (objectUpcastedUsageInfos.length > 0) {
                showObjectUpcastedUsageView(objectUpcastedUsageInfos);
                setPreviewUsages(true);
            }
        }
        List<UsageInfo> filteredUsages = filterUsages(oldUsages);
        refUsages.set(filteredUsages.toArray(new UsageInfo[filteredUsages.size()]));
        prepareSuccessful();
        return true;
    }

    @RequiredReadAction
    private void analyzeConflicts(UsageInfo[] usage, MultiMap<PsiElement, LocalizeValue> conflicts) {
        Map<PsiElement, Set<PsiElement>> reportedNonDelegatedUsages = new HashMap<>();
        Map<PsiClass, Set<PsiElement>> reportedUpcasts = new HashMap<>();
        //Set reportedObjectUpcasts = new HashSet();

        //String nameJavaLangObject = ConflictsUtil.htmlEmphasize(CommonClassNames.JAVA_LANG_OBJECT);
        String classDescription = RefactoringUIUtil.getDescription(myClass, false);

        for (UsageInfo aUsage : usage) {
            PsiElement element = aUsage.getElement();
            if (aUsage instanceof InheritanceToDelegationUsageInfo usageInfo) {
                /*if (usageInfo instanceof ObjectUpcastedUsageInfo) {
                    PsiElement container = ConflictsUtil.getContainer(usageInfo.element);
                    if (!reportedObjectUpcasts.contains(container)) {
                        String message = "An instance of " + classDescription + " is upcasted to "
                            + nameJavaLangObject + " in " + ConflictsUtil.getDescription(container, true) + ".";
                        conflicts.add(message);
                        reportedObjectUpcasts.add(container);
                    }
                } else*/
                if (!myIsDelegateOtherMembers && !usageInfo.getDelegateFieldAccessible().isAccessible()) {
                    if (usageInfo instanceof NonDelegatedMemberUsageInfo nonDelegatedMemberUsageInfo) {
                        PsiElement nonDelegatedMember = nonDelegatedMemberUsageInfo.nonDelegatedMember;
                        Set<PsiElement> reportedContainers = reportedNonDelegatedUsages.get(nonDelegatedMember);
                        if (reportedContainers == null) {
                            reportedContainers = new HashSet<>();
                            reportedNonDelegatedUsages.put(nonDelegatedMember, reportedContainers);
                        }
                        PsiElement container = ConflictsUtil.getContainer(element);
                        if (!reportedContainers.contains(container)) {
                            LocalizeValue message = RefactoringLocalize.zeroUses1OfAnInstanceOfA2(
                                RefactoringUIUtil.getDescription(container, true),
                                RefactoringUIUtil.getDescription(nonDelegatedMember, true),
                                classDescription
                            );
                            conflicts.putValue(container, message.capitalize());
                            reportedContainers.add(container);
                        }
                    }
                    else if (usageInfo instanceof UpcastedUsageInfo upcastedUsageInfo) {
                        PsiClass upcastedTo = upcastedUsageInfo.upcastedTo;
                        Set<PsiElement> reportedContainers = reportedUpcasts.get(upcastedTo);
                        if (reportedContainers == null) {
                            reportedContainers = new HashSet<>();
                            reportedUpcasts.put(upcastedTo, reportedContainers);
                        }
                        PsiElement container = ConflictsUtil.getContainer(element);
                        if (!reportedContainers.contains(container)) {
                            LocalizeValue message = RefactoringLocalize.zeroUpcastsAnInstanceOf1To2(
                                RefactoringUIUtil.getDescription(container, true),
                                classDescription,
                                RefactoringUIUtil.getDescription(upcastedTo, false)
                            );
                            conflicts.putValue(container, message.capitalize());
                            reportedContainers.add(container);
                        }
                    }
                }
            }
            else if (aUsage instanceof NoLongerOverridingSubClassMethodUsageInfo info) {
                LocalizeValue message = RefactoringLocalize.zeroWillNoLongerOverride1(
                    RefactoringUIUtil.getDescription(info.getSubClassMethod(), true),
                    RefactoringUIUtil.getDescription(info.getOverridenMethod(), true)
                );
                conflicts.putValue(info.getSubClassMethod(), message.capitalize());
            }
        }
    }

    private static ObjectUpcastedUsageInfo[] objectUpcastedUsages(UsageInfo[] usages) {
        List<ObjectUpcastedUsageInfo> result = new ArrayList<>();
        for (UsageInfo usage : usages) {
            if (usage instanceof ObjectUpcastedUsageInfo objectUpcastedUsageInfo) {
                result.add(objectUpcastedUsageInfo);
            }
        }
        return result.toArray(new ObjectUpcastedUsageInfo[result.size()]);
    }

    private List<UsageInfo> filterUsages(List<UsageInfo> usages) {
        List<UsageInfo> result = new ArrayList<>();

        for (UsageInfo usageInfo : usages) {
            if (!(usageInfo instanceof InheritanceToDelegationUsageInfo inheritanceToDelegationUsageInfo)) {
                continue;
            }
            if (usageInfo instanceof ObjectUpcastedUsageInfo) {
                continue;
            }

            if (!myIsDelegateOtherMembers) {
                FieldAccessibility delegateFieldAccessible = inheritanceToDelegationUsageInfo.getDelegateFieldAccessible();
                if (!delegateFieldAccessible.isAccessible()) {
                    continue;
                }
            }

            result.add(usageInfo);
        }
        return result;
    }

    private void processClass(PsiClass inheritor, List<UsageInfo> usages) {
        ClassReferenceScanner scanner = new ClassReferenceSearchingScanner(inheritor);
        MyClassInstanceReferenceVisitor instanceVisitor = new MyClassInstanceReferenceVisitor(inheritor, usages);
        scanner.processReferences(new ClassInstanceScanner(inheritor, instanceVisitor));
        MyClassInheritorMemberReferencesVisitor classMemberVisitor =
            new MyClassInheritorMemberReferencesVisitor(inheritor, usages, instanceVisitor);
        inheritor.accept(classMemberVisitor);
        PsiSubstitutor inheritorSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(myClass, inheritor, PsiSubstitutor.EMPTY);

        PsiMethod[] methods = inheritor.getMethods();
        for (PsiMethod method : methods) {
            PsiMethod baseMethod = findSuperMethodInBaseClass(method);

            if (baseMethod != null) {
                if (!baseMethod.isAbstract()) {
                    usages.add(new NoLongerOverridingSubClassMethodUsageInfo(method, baseMethod));
                }
                else {
                    PsiMethod[] methodsByName = myClass.findMethodsByName(method.getName(), false);
                    for (PsiMethod classMethod : methodsByName) {
                        MethodSignature signature = classMethod.getSignature(inheritorSubstitutor);
                        if (signature.equals(method.getSignature(PsiSubstitutor.EMPTY)) && !classMethod.isAbstract()) {
                            usages.add(new NoLongerOverridingSubClassMethodUsageInfo(method, baseMethod));
                            break;
                        }
                    }
                }
            }
        }
    }

    @Override
    @RequiredWriteAction
    protected void performRefactoring(UsageInfo[] usages) {
        try {
            for (UsageInfo aUsage : usages) {
                if (aUsage instanceof UnqualifiedNonDelegatedMemberUsageInfo unqualifiedNonDelegatedMemberUsageInfo) {
                    delegateUsageFromClass(
                        unqualifiedNonDelegatedMemberUsageInfo.getElement(),
                        unqualifiedNonDelegatedMemberUsageInfo.nonDelegatedMember,
                        unqualifiedNonDelegatedMemberUsageInfo.getDelegateFieldAccessible()
                    );
                }
                else {
                    InheritanceToDelegationUsageInfo usage = (InheritanceToDelegationUsageInfo) aUsage;
                    upcastToDelegation(usage.getElement(), usage.getDelegateFieldAccessible());
                }
            }

            myAbstractDelegatedMethods = new HashSet<>();
            addInnerClass();
            addField(usages);
            delegateMethods();
            addImplementingInterfaces();
        }
        catch (IncorrectOperationException e) {
            LOG.error(e);
        }
    }

    @RequiredWriteAction
    private void addInnerClass() throws IncorrectOperationException {
        if (!myIsInnerClassNeeded) {
            return;
        }

        PsiClass innerClass = myFactory.createClass(myInnerClassName);
        PsiSubstitutor superClassSubstitutor =
            TypeConversionUtil.getSuperClassSubstitutor(myBaseClass, myClass, PsiSubstitutor.EMPTY);
        PsiClassType superClassType = myFactory.createType(myBaseClass, superClassSubstitutor);
        PsiJavaCodeReferenceElement baseClassReferenceElement = myFactory.createReferenceElementByType(superClassType);
        if (!myBaseClass.isInterface()) {
            innerClass.getExtendsList().add(baseClassReferenceElement);
        }
        else {
            innerClass.getImplementsList().add(baseClassReferenceElement);
        }
        PsiUtil.setModifierProperty(innerClass, PsiModifier.PRIVATE, true);
        innerClass = (PsiClass) myClass.add(innerClass);

        List<InnerClassMethod> innerClassMethods = getInnerClassMethods();
        for (InnerClassMethod innerClassMethod : innerClassMethods) {
            innerClassMethod.createMethod(innerClass);
        }
    }

    @RequiredWriteAction
    private void delegateUsageFromClass(
        PsiElement element,
        PsiElement nonDelegatedMember,
        FieldAccessibility fieldAccessibility
    ) throws IncorrectOperationException {
        if (element instanceof PsiReferenceExpression refExpr) {
            if (refExpr.getQualifierExpression() != null) {
                upcastToDelegation(refExpr.getQualifierExpression(), fieldAccessibility);
            }
            else {
                String name = ((PsiNamedElement) nonDelegatedMember).getName();
                String qualifier;
                if (isStatic(nonDelegatedMember)) {
                    qualifier = myBaseClass.getName();
                }
                else if (!fieldAccessibility.isAccessible() && myGenerateGetter) {
                    qualifier = myGetterName + "()";
                }
                else {
                    qualifier = myFieldName;
                }

                PsiExpression newExpr = myFactory.createExpressionFromText(qualifier + "." + name, element);
                newExpr = (PsiExpression) CodeStyleManager.getInstance(myProject).reformat(newExpr);
                element.replace(newExpr);
            }
        }
        else if (element instanceof PsiJavaCodeReferenceElement) {
            String name = ((PsiNamedElement) nonDelegatedMember).getName();

            if (!isStatic(nonDelegatedMember) && element.getParent() instanceof PsiNewExpression newExpr) {
                if (newExpr.getQualifier() != null) {
                    upcastToDelegation(newExpr.getQualifier(), fieldAccessibility);
                }
                else {
                    String qualifier;
                    if (!fieldAccessibility.isAccessible() && myGenerateGetter) {
                        qualifier = myGetterName + "()";
                    }
                    else {
                        qualifier = myFieldName;
                    }
                    newExpr.replace(myFactory.createExpressionFromText(qualifier + "." + newExpr.getText(), newExpr));
                }
            }
            else {
                String qualifier = myBaseClass.getName();
                PsiJavaCodeReferenceElement newRef =
                    myFactory.createFQClassNameReferenceElement(qualifier + "." + name, element.getResolveScope());
                //newRef = (PsiJavaCodeReferenceElement) CodeStyleManager.getInstance(myProject).reformat(newRef);
                element.replace(newRef);
            }
        }
        else {
            LOG.assertTrue(false);
        }
    }

    private static boolean isStatic(PsiElement member) {
        return member instanceof PsiModifierListOwner method && method.hasModifierProperty(PsiModifier.STATIC);
    }

    @RequiredWriteAction
    private void upcastToDelegation(PsiElement element, FieldAccessibility fieldAccessibility) throws IncorrectOperationException {
        PsiExpression expression = (PsiExpression) element;

        PsiExpression newExpr;
        PsiReferenceExpression ref;
        String delegateQualifier;
        if (!(expression instanceof PsiThisExpression || expression instanceof PsiSuperExpression)) {
            delegateQualifier = "a.";
        }
        else {
            PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(myProject).getResolveHelper();
            PsiVariable psiVariable = resolveHelper.resolveReferencedVariable(myFieldName, element);
            if (psiVariable == null) {
                delegateQualifier = "";
            }
            else {
                delegateQualifier = "a.";
            }
        }
        if (!fieldAccessibility.isAccessible() && myGenerateGetter) {
            newExpr = myFactory.createExpressionFromText(delegateQualifier + myGetterName + "()", expression);
            ref = (PsiReferenceExpression) ((PsiMethodCallExpression) newExpr).getMethodExpression().getQualifierExpression();
        }
        else {
            newExpr = myFactory.createExpressionFromText(delegateQualifier + myFieldName, expression);
            ref = (PsiReferenceExpression) ((PsiReferenceExpression) newExpr).getQualifierExpression();
        }
        //    LOG.debug("upcastToDelegation:" + element + ":newExpr = " + newExpr);
        //    LOG.debug("upcastToDelegation:" + element + ":ref = " + ref);
        if (ref != null) {
            ref.replace(expression);
        }
        expression.replace(newExpr);
        //    LOG.debug("upcastToDelegation:" + element + ":replaced = " + replaced);
    }

    @RequiredWriteAction
    private void delegateMethods() throws IncorrectOperationException {
        for (PsiMethod method : myDelegatedMethods) {
            if (!myAbstractDelegatedMethods.contains(method)) {
                PsiMethod methodToAdd = delegateMethod(myFieldName, method, getSuperSubstitutor(method.getContainingClass()));

                String visibility = myDelegatedMethodsVisibility.get(method);
                if (visibility != null) {
                    PsiUtil.setModifierProperty(methodToAdd, visibility, true);
                }

                myClass.add(methodToAdd);
            }
        }
    }

    @RequiredWriteAction
    private PsiMethod delegateMethod(
        String delegationTarget,
        PsiMethod method,
        PsiSubstitutor substitutor
    ) throws IncorrectOperationException {
        substitutor = OverrideImplementUtil.correctSubstitutor(method, substitutor);
        PsiMethod methodToAdd = GenerateMembersUtil.substituteGenericMethod(method, substitutor);

        PsiModifierList modifierList = methodToAdd.getModifierList();
        modifierList.setModifierProperty(PsiModifier.ABSTRACT, false);

        NullableNotNullManager.getInstance(myProject).copyNullableOrNotNullAnnotation(method, methodToAdd);

        String delegationBody = getDelegationBody(methodToAdd, delegationTarget);
        PsiCodeBlock newBody = myFactory.createCodeBlockFromText(delegationBody, method);

        PsiCodeBlock oldBody = methodToAdd.getBody();
        if (oldBody != null) {
            oldBody.replace(newBody);
        }
        else {
            methodToAdd.addBefore(newBody, null);
        }

        if (methodToAdd.getDocComment() != null) {
            methodToAdd.getDocComment().delete();
        }
        methodToAdd = (PsiMethod) CodeStyleManager.getInstance(myProject).reformat(methodToAdd);
        methodToAdd = (PsiMethod) JavaCodeStyleManager.getInstance(myProject).shortenClassReferences(methodToAdd);
        return methodToAdd;
    }

    private static String getDelegationBody(PsiMethod methodToAdd, String delegationTarget) {
        StringBuilder buffer = new StringBuilder();
        buffer.append("{\n");

        if (!PsiType.VOID.equals(methodToAdd.getReturnType())) {
            buffer.append("return ");
        }

        buffer.append(delegationTarget);
        buffer.append(".");
        buffer.append(methodToAdd.getName());
        buffer.append("(");
        PsiParameter[] params = methodToAdd.getParameterList().getParameters();
        for (int i = 0; i < params.length; i++) {
            PsiParameter param = params[i];
            if (i > 0) {
                buffer.append(",");
            }
            buffer.append(param.getName());
        }
        buffer.append(");\n}");
        return buffer.toString();
    }

    @RequiredWriteAction
    private void addImplementingInterfaces() throws IncorrectOperationException {
        PsiReferenceList implementsList = myClass.getImplementsList();
        LOG.assertTrue(implementsList != null);
        for (PsiClass delegatedInterface : myDelegatedInterfaces) {
            if (!myClassImplementedInterfaces.contains(delegatedInterface)) {
                implementsList.add(myFactory.createClassReferenceElement(delegatedInterface));
            }
        }

        if (!myBaseClass.isInterface()) {
            PsiReferenceList extendsList = myClass.getExtendsList();
            LOG.assertTrue(extendsList != null);
            extendsList.getReferenceElements()[0].delete();
        }
        else {
            PsiJavaCodeReferenceElement[] interfaceRefs = implementsList.getReferenceElements();
            for (PsiJavaCodeReferenceElement interfaceRef : interfaceRefs) {
                PsiElement resolved = interfaceRef.resolve();
                if (myManager.areElementsEquivalent(myBaseClass, resolved)) {
                    interfaceRef.delete();
                    break;
                }
            }
        }
    }

    @RequiredWriteAction
    private void addField(UsageInfo[] usages) throws IncorrectOperationException {
        String fieldVisibility = getFieldVisibility(usages);

        boolean fieldInitializerNeeded = isFieldInitializerNeeded();

        PsiField field = createField(fieldVisibility, fieldInitializerNeeded, defaultClassFieldType());

        if (!myIsInnerClassNeeded) {
            field.getTypeElement().replace(myFactory.createTypeElement(myBaseClassType));
            if (fieldInitializerNeeded) {
                PsiJavaCodeReferenceElement classReferenceElement = myFactory.createReferenceElementByType(myBaseClassType);
                PsiNewExpression newExpression = (PsiNewExpression) field.getInitializer();
                newExpression.getClassReference().replace(classReferenceElement);
            }
        }

        field = (PsiField) CodeStyleManager.getInstance(myProject).reformat(field);
        myClass.add(field);
        if (!fieldInitializerNeeded) {
            fixConstructors();
        }

        if (myGenerateGetter) {
            String getterVisibility = PsiModifier.PUBLIC;
            StringBuilder getterBuffer = new StringBuilder();
            getterBuffer.append(getterVisibility);
            getterBuffer.append(" Object ");
            getterBuffer.append(myGetterName);
            getterBuffer.append("() {\n return ");
            getterBuffer.append(myFieldName);
            getterBuffer.append(";\n}");
            PsiMethod getter = myFactory.createMethodFromText(getterBuffer.toString(), myClass);
            getter.getReturnTypeElement().replace(myFactory.createTypeElement(myBaseClassType));
            getter = (PsiMethod) CodeStyleManager.getInstance(myProject).reformat(getter);
            myClass.add(getter);
        }
    }

    private String getFieldVisibility(UsageInfo[] usages) {
        if (myIsDelegateOtherMembers && !myGenerateGetter) {
            return PsiModifier.PUBLIC;
        }

        for (UsageInfo aUsage : usages) {
            InheritanceToDelegationUsageInfo usage = (InheritanceToDelegationUsageInfo) aUsage;
            FieldAccessibility delegateFieldAccessible = usage.getDelegateFieldAccessible();
            if (delegateFieldAccessible.isAccessible() && delegateFieldAccessible.getContainingClass() != myClass) {
                return PsiModifier.PROTECTED;
            }
        }
        return PsiModifier.PRIVATE;
    }

    private String defaultClassFieldType() {
        return (myIsInnerClassNeeded ? myInnerClassName : "Object");
    }

    @RequiredWriteAction
    private PsiField createField(String fieldVisibility, boolean fieldInitializerNeeded, String defaultTypeName)
        throws IncorrectOperationException {
        StringBuilder buffer = new StringBuilder();
        buffer.append(fieldVisibility);
        buffer.append(" final ").append(defaultTypeName).append("  ");
        buffer.append(myFieldName);
        if (fieldInitializerNeeded) {
            buffer.append(" = new ").append(defaultTypeName).append("()");
        }
        buffer.append(";");
        return myFactory.createFieldFromText(buffer.toString(), myClass);
    }

    @RequiredWriteAction
    private void fixConstructors() throws IncorrectOperationException {
        if (myBaseClass.isInterface()) {
            return;
        }
        PsiJavaCodeReferenceElement baseClassReference = myFactory.createClassReferenceElement(myBaseClass);

        PsiMethod[] constructors = myClass.getConstructors();
        for (PsiMethod constructor : constructors) {
            PsiCodeBlock body = constructor.getBody();
            PsiStatement[] statements = body.getStatements();
            String fieldQualifier = "";
            PsiParameter[] constructorParams = constructor.getParameterList().getParameters();
            for (PsiParameter constructorParam : constructorParams) {
                if (myFieldName.equals(constructorParam.getName())) {
                    fieldQualifier = "this.";
                    break;
                }
            }
            String assignmentText = fieldQualifier + myFieldName + "= new " + defaultClassFieldType() + "()";
            if (statements.length < 1 || !JavaHighlightUtil.isSuperOrThisCall(statements[0], true, true) || myBaseClass.isInterface()) {
                PsiExpressionStatement assignmentStatement =
                    (PsiExpressionStatement) myFactory.createStatementFromText(
                        assignmentText, body
                    );
                if (!myIsInnerClassNeeded) {
                    PsiAssignmentExpression assignmentExpr = (PsiAssignmentExpression) assignmentStatement.getExpression();
                    PsiNewExpression newExpression = (PsiNewExpression) assignmentExpr.getRExpression();
                    assert newExpression != null;
                    PsiJavaCodeReferenceElement classRef = newExpression.getClassReference();
                    assert classRef != null;
                    classRef.replace(baseClassReference);
                }

                assignmentStatement = (PsiExpressionStatement) CodeStyleManager.getInstance(myProject).reformat(assignmentStatement);
                if (statements.length > 0) {
                    if (!JavaHighlightUtil.isSuperOrThisCall(statements[0], true, false)) {
                        body.addBefore(assignmentStatement, statements[0]);
                    }
                    else {
                        body.addAfter(assignmentStatement, statements[0]);
                    }
                }
                else {
                    body.add(assignmentStatement);
                }
            }
            else {
                PsiExpressionStatement callStatement = ((PsiExpressionStatement) statements[0]);
                if (!JavaHighlightUtil.isSuperOrThisCall(callStatement, false, true)) {
                    PsiMethodCallExpression superConstructorCall = (PsiMethodCallExpression) callStatement.getExpression();
                    PsiAssignmentExpression assignmentExpression =
                        (PsiAssignmentExpression) myFactory.createExpressionFromText(assignmentText, superConstructorCall);
                    PsiNewExpression newExpression = (PsiNewExpression) assignmentExpression.getRExpression();
                    if (!myIsInnerClassNeeded) {
                        newExpression.getClassReference().replace(baseClassReference);
                    }
                    assignmentExpression = (PsiAssignmentExpression) CodeStyleManager.getInstance(myProject).reformat(assignmentExpression);
                    newExpression.getArgumentList().replace(superConstructorCall.getArgumentList());
                    superConstructorCall.replace(assignmentExpression);
                }
            }
        }
    }

    private boolean isFieldInitializerNeeded() {
        if (myBaseClass.isInterface()) {
            return true;
        }
        PsiMethod[] constructors = myClass.getConstructors();
        for (PsiMethod constructor : constructors) {
            PsiStatement[] statements = constructor.getBody().getStatements();
            if (statements.length > 0 && JavaHighlightUtil.isSuperOrThisCall(statements[0], true, false)) {
                return false;
            }
        }
        return true;
    }

    @RequiredReadAction
    private List<InnerClassMethod> getInnerClassMethods() {
        List<InnerClassMethod> result = new ArrayList<>();

        // find all necessary constructors
        if (!myBaseClass.isInterface()) {
            PsiMethod[] constructors = myClass.getConstructors();
            for (PsiMethod constructor : constructors) {
                PsiStatement[] statements = constructor.getBody().getStatements();
                if (statements.length > 0 && JavaHighlightUtil.isSuperOrThisCall(statements[0], true, false)) {
                    PsiMethodCallExpression superConstructorCall =
                        (PsiMethodCallExpression) ((PsiExpressionStatement) statements[0]).getExpression();
                    if (superConstructorCall.getMethodExpression().resolve() instanceof PsiMethod superConstructor
                        && superConstructor.isConstructor()) {
                        result.add(new InnerClassConstructor(superConstructor));
                    }
                }
            }
        }

        // find overriding/implementing method
        {
            class InnerClassOverridingMethod extends InnerClassMethod {
                public InnerClassOverridingMethod(PsiMethod method) {
                    super(method);
                }

                @Override
                @RequiredWriteAction
                public void createMethod(PsiClass innerClass)
                    throws IncorrectOperationException {
                    OverriddenMethodClassMemberReferencesVisitor visitor = new OverriddenMethodClassMemberReferencesVisitor();
                    myClass.accept(visitor);
                    List<PsiAction> actions = visitor.getPsiActions();
                    for (PsiAction action : actions) {
                        action.run();
                    }
                    innerClass.add(myMethod);
                    myMethod.delete();
                    // myMethod.replace(delegateMethod(myMethod));
                }
            }

            for (PsiMethod method : myOverriddenMethods) {
                result.add(new InnerClassOverridingMethod(method));
            }
        }

        // fix abstract methods
        {
            class InnerClassAbstractMethod extends InnerClassMethod {
                private final boolean myImplicitImplementation;

                public InnerClassAbstractMethod(PsiMethod method, boolean implicitImplementation) {
                    super(method);
                    myImplicitImplementation = implicitImplementation;
                }

                @Override
                @RequiredWriteAction
                public void createMethod(PsiClass innerClass)
                    throws IncorrectOperationException {
                    PsiSubstitutor substitutor = getSuperSubstitutor(myMethod.getContainingClass());
                    PsiMethod method = delegateMethod(myClass.getName() + ".this", myMethod, substitutor);
                    PsiClass containingClass = myMethod.getContainingClass();
                    if (myBaseClass.isInterface() || containingClass.isInterface()) {
                        PsiUtil.setModifierProperty(method, PsiModifier.PUBLIC, true);
                    }
                    innerClass.add(method);
                    if (!myImplicitImplementation) {
                        MethodSignature signature = myMethod.getSignature(substitutor);
                        PsiMethod outerMethod = MethodSignatureUtil.findMethodBySignature(myClass, signature, false);
                        if (outerMethod == null) {
                            String visibility = checkOuterClassAbstractMethod(signature);
                            PsiMethod newOuterMethod = (PsiMethod) myClass.add(myMethod);
                            PsiUtil.setModifierProperty(newOuterMethod, visibility, true);
                            PsiDocComment docComment = newOuterMethod.getDocComment();
                            if (docComment != null) {
                                docComment.delete();
                            }
                        }
                    }
                }
            }
            PsiMethod[] methods = myBaseClass.getAllMethods();

            for (PsiMethod method : methods) {
                if (method.isAbstract()) {
                    MethodSignature signature = method.getSignature(getSuperSubstitutor(method.getContainingClass()));
                    PsiMethod classMethod = MethodSignatureUtil.findMethodBySignature(myClass, signature, true);
                    if (classMethod == null || classMethod.isAbstract()) {
                        result.add(new InnerClassAbstractMethod(method, false));
                    }
                    else if ((myBaseClass.isInterface() && classMethod.getContainingClass() != myClass)) {   // IDEADEV-19675
                        result.add(new InnerClassAbstractMethod(method, true));
                    }
                }
            }
        }


        return result;
    }

    @RequiredReadAction
    private void showObjectUpcastedUsageView(ObjectUpcastedUsageInfo[] usages) {
        UsageViewPresentation presentation = new UsageViewPresentation();
        presentation.setTargetsNodeText(RefactoringLocalize.replacingInheritanceWithDelegation());
        presentation.setCodeUsagesString(RefactoringLocalize.instancesCastedToJavaLangObject());
        LocalizeValue upcastedString = RefactoringLocalize.instancesUpcastedToObject();
        presentation.setUsagesString(upcastedString);
        presentation.setTabText(upcastedString);

        UsageViewManager manager = UsageViewManager.getInstance(myProject);
        manager.showUsages(
            new UsageTarget[]{new PsiElement2UsageTargetAdapter(myClass)},
            UsageInfoToUsageConverter.convert(new UsageInfoToUsageConverter.TargetElementsDescriptor(myClass), usages),
            presentation
        );
    }

    /**
     * @param methodSignature
     * @return Visibility
     */
    @PsiModifier.ModifierConstant
    private String checkOuterClassAbstractMethod(MethodSignature methodSignature) {
        String visibility = PsiModifier.PROTECTED;
        for (PsiMethod method : myDelegatedMethods) {
            MethodSignature otherSignature = method.getSignature(getSuperSubstitutor(method.getContainingClass()));

            if (MethodSignatureUtil.areSignaturesEqual(otherSignature, methodSignature)) {
                visibility = VisibilityUtil.getHighestVisibility(
                    visibility,
                    VisibilityUtil.getVisibilityModifier(method.getModifierList())
                );
                myAbstractDelegatedMethods.add(method);
            }
        }
        return visibility;
    }

    private Set<PsiMethod> getOverriddenMethods() {
        Set<PsiMethod> result = new LinkedHashSet<>();

        PsiMethod[] methods = myClass.getMethods();
        for (PsiMethod method : methods) {
            if (findSuperMethodInBaseClass(method) != null) {
                result.add(method);
            }
        }
        return result;
    }

    @Nullable
    private PsiMethod findSuperMethodInBaseClass(PsiMethod method) {
        PsiMethod[] superMethods = method.findSuperMethods();
        for (PsiMethod superMethod : superMethods) {
            PsiClass containingClass = superMethod.getContainingClass();
            if (InheritanceUtil.isInheritorOrSelf(myBaseClass, containingClass, true)) {
                String qName = containingClass.getQualifiedName();
                if (qName == null || !CommonClassNames.JAVA_LANG_OBJECT.equals(qName)) {
                    return superMethod;
                }
            }
        }
        return null;
    }

    @Nonnull
    @Override
    @RequiredReadAction
    protected LocalizeValue getCommandName() {
        return RefactoringLocalize.replaceInheritanceWithDelegationCommand(DescriptiveNameUtil.getDescriptiveName(myClass));
    }

    private Set<PsiMember> getAllBaseClassMembers() {
        Set<PsiMember> result = new HashSet<>();
        addAll(result, myBaseClass.getAllFields());
        addAll(result, myBaseClass.getAllInnerClasses());
        addAll(result, myBaseClass.getAllMethods());

        //remove java.lang.Object members
        for (Iterator<PsiMember> iterator = result.iterator(); iterator.hasNext(); ) {
            PsiMember member = iterator.next();
            if (CommonClassNames.JAVA_LANG_OBJECT.equals(member.getContainingClass().getQualifiedName())) {
                iterator.remove();
            }
        }
        return Collections.unmodifiableSet(result);
    }

    private Set<PsiClass> getAllBases() {
        Set<PsiClass> temp = new HashSet<>();
        InheritanceUtil.getSuperClasses(myBaseClass, temp, true);
        temp.add(myBaseClass);
        return Collections.unmodifiableSet(temp);
    }

    private static <T> void addAll(Collection<T> collection, T[] objs) {
        Collections.addAll(collection, objs);
    }

    private boolean isDelegated(PsiMember classMember) {
        if (!(classMember instanceof PsiMethod method)) {
            return false;
        }
        for (PsiMethod delegatedMethod : myDelegatedMethods) {
            //methods reside in base class, so no substitutor needed
            if (MethodSignatureUtil.areSignaturesEqual(
                method.getSignature(PsiSubstitutor.EMPTY),
                delegatedMethod.getSignature(PsiSubstitutor.EMPTY)
            )) {
                return true;
            }
        }
        return false;
    }

    private class MyClassInheritorMemberReferencesVisitor extends ClassMemberReferencesVisitor {
        private final List<UsageInfo> myUsageInfoStorage;
        private final ClassInstanceScanner.ClassInstanceReferenceVisitor myInstanceVisitor;

        MyClassInheritorMemberReferencesVisitor(
            PsiClass aClass,
            List<UsageInfo> usageInfoStorage,
            ClassInstanceScanner.ClassInstanceReferenceVisitor instanceScanner
        ) {
            super(aClass);

            myUsageInfoStorage = usageInfoStorage;
            myInstanceVisitor = instanceScanner;
        }

        @Override
        @RequiredReadAction
        protected void visitClassMemberReferenceElement(PsiMember classMember, PsiJavaCodeReferenceElement classMemberReference) {
            if ("super".equals(classMemberReference.getText()) && classMemberReference.getParent() instanceof PsiMethodCallExpression) {
                return;
            }

            if (classMember != null && myBaseClassMembers.contains(classMember) && !isDelegated(classMember)) {
                FieldAccessibility delegateFieldVisibility = new FieldAccessibility(true, getPsiClass());
                InheritanceToDelegationUsageInfo usageInfo;
                if (classMemberReference instanceof PsiReferenceExpression referenceExpression) {
                    if (referenceExpression.getQualifierExpression() == null) {
                        usageInfo = new UnqualifiedNonDelegatedMemberUsageInfo(classMemberReference, classMember, delegateFieldVisibility);
                    }
                    else {
                        usageInfo = new NonDelegatedMemberUsageInfo(
                            referenceExpression.getQualifierExpression(),
                            classMember, delegateFieldVisibility
                        );
                    }
                    myUsageInfoStorage.add(usageInfo);
                }
                else /*if (classMemberReference instanceof PsiJavaCodeReferenceElement)*/ {
                    usageInfo = new UnqualifiedNonDelegatedMemberUsageInfo(classMemberReference, classMember, delegateFieldVisibility);
                    myUsageInfoStorage.add(usageInfo);
                }
            }
        }

        @Override
        public void visitThisExpression(@Nonnull PsiThisExpression expression) {
            ClassInstanceScanner.processNonArrayExpression(myInstanceVisitor, expression, null);
        }
    }

    private class MyClassMemberReferencesVisitor extends MyClassInheritorMemberReferencesVisitor {
        MyClassMemberReferencesVisitor(
            List<UsageInfo> usageInfoStorage,
            ClassInstanceScanner.ClassInstanceReferenceVisitor instanceScanner
        ) {
            super(InheritanceToDelegationProcessor.this.myClass, usageInfoStorage, instanceScanner);
        }

        @Override
        public void visitMethod(@Nonnull PsiMethod method) {
            if (!myOverriddenMethods.contains(method)) {
                super.visitMethod(method);
            }
        }
    }

    interface PsiAction {
        void run() throws IncorrectOperationException;
    }

    /**
     * This visitor should be called for overridden methods before they are moved to an inner class
     */
    private class OverriddenMethodClassMemberReferencesVisitor extends ClassMemberReferencesVisitor {
        private final List<PsiAction> myPsiActions;
        private final PsiThisExpression myQualifiedThis;

        @RequiredWriteAction
        OverriddenMethodClassMemberReferencesVisitor() throws IncorrectOperationException {
            super(myClass);
            myPsiActions = new ArrayList<>();
            PsiJavaCodeReferenceElement classReferenceElement = myFactory.createClassReferenceElement(myClass);
            myQualifiedThis = (PsiThisExpression) myFactory.createExpressionFromText("A.this", null);
            myQualifiedThis.getQualifier().replace(classReferenceElement);
        }

        public List<PsiAction> getPsiActions() {
            return myPsiActions;
        }

        class QualifyThis implements PsiAction {
            private final PsiThisExpression myThisExpression;

            QualifyThis(PsiThisExpression thisExpression) {
                myThisExpression = thisExpression;
            }

            @Override
            @RequiredWriteAction
            public void run() throws IncorrectOperationException {
                myThisExpression.replace(myQualifiedThis);
            }
        }

        class QualifyName implements PsiAction {
            private final PsiReferenceExpression myRef;
            private final String myReferencedName;

            QualifyName(PsiReferenceExpression ref, String name) {
                myRef = ref;
                myReferencedName = name;
            }

            @Override
            @RequiredWriteAction
            public void run() throws IncorrectOperationException {
                PsiReferenceExpression newRef = (PsiReferenceExpression) myFactory.createExpressionFromText("a." + myReferencedName, null);
                newRef.getQualifierExpression().replace(myQualifiedThis);
                myRef.replace(newRef);
            }
        }

        class QualifyWithField implements PsiAction {
            private final PsiReferenceExpression myReference;
            private final String myReferencedName;

            public QualifyWithField(PsiReferenceExpression reference, String name) {
                myReference = reference;
                myReferencedName = name;
            }

            @Override
            @RequiredWriteAction
            public void run() throws IncorrectOperationException {
                PsiReferenceExpression newRef =
                    (PsiReferenceExpression) myFactory.createExpressionFromText(myFieldName + "." + myReferencedName, null);
                myReference.replace(newRef);
            }
        }

        @Override
        protected void visitClassMemberReferenceExpression(PsiMember classMember, PsiReferenceExpression classMemberReference) {
            if (classMember instanceof PsiField field) {
                if (field.getContainingClass().equals(myClass)) {
                    String name = field.getName();
                    PsiField baseField = myBaseClass.findFieldByName(name, true);
                    if (baseField != null) {
                        myPsiActions.add(new QualifyName(classMemberReference, name));
                    }
                    else if (classMemberReference.getQualifierExpression() instanceof PsiThisExpression thisExpr) {
                        myPsiActions.add(new QualifyThis(thisExpr));
                    }
                }
            }
            else if (classMember instanceof PsiMethod method && method.getContainingClass().equals(myClass)) {
                if (!myOverriddenMethods.contains(method)) {
                    PsiMethod baseMethod = findSuperMethodInBaseClass(method);
                    if (baseMethod != null) {
                        myPsiActions.add(new QualifyName(classMemberReference, baseMethod.getName()));
                    }
                    else if (classMemberReference.getQualifierExpression() instanceof PsiThisExpression thisExpr) {
                        myPsiActions.add(new QualifyThis(thisExpr));
                    }
                }
                else if (!myDelegatedMethods.contains(method)) {
                    myPsiActions.add(new QualifyWithField(classMemberReference, method.getName()));
                }
            }
        }

        @Override
        public void visitThisExpression(@Nonnull final PsiThisExpression expression) {
            class Visitor implements ClassInstanceScanner.ClassInstanceReferenceVisitor {
                @Override
                public void visitQualifier(PsiReferenceExpression qualified, PsiExpression instanceRef, PsiElement referencedInstance) {
                    LOG.assertTrue(false);
                }

                @Override
                public void visitTypeCast(
                    PsiTypeCastExpression typeCastExpression,
                    PsiExpression instanceRef,
                    PsiElement referencedInstance
                ) {
                    processType(typeCastExpression.getCastType().getType());
                }

                @Override
                public void visitReadUsage(PsiExpression instanceRef, PsiType expectedType, PsiElement referencedInstance) {
                    processType(expectedType);
                }

                @Override
                public void visitWriteUsage(PsiExpression instanceRef, PsiType assignedType, PsiElement referencedInstance) {
                    LOG.assertTrue(false);
                }

                private void processType(PsiType type) {
                    PsiClass resolved = PsiUtil.resolveClassInType(type);
                    if (resolved != null && !myBaseClassBases.contains(resolved)) {
                        myPsiActions.add(new QualifyThis(expression));
                    }
                }
            }
            Visitor visitor = new Visitor();
            ClassInstanceScanner.processNonArrayExpression(visitor, expression, null);
        }

        @Override
        protected void visitClassMemberReferenceElement(PsiMember classMember, PsiJavaCodeReferenceElement classMemberReference) {
        }
    }

    private final class MyClassInstanceReferenceVisitor implements ClassInstanceScanner.ClassInstanceReferenceVisitor {
        private final PsiClass myClass;
        private final List<UsageInfo> myUsageInfoStorage;
        private final Set<PsiClass> myImplementedInterfaces;

        public MyClassInstanceReferenceVisitor(PsiClass aClass, List<UsageInfo> usageInfoStorage) {
            myClass = aClass;
            myUsageInfoStorage = usageInfoStorage;
            myImplementedInterfaces = getImplementedInterfaces();
        }

        public Set<PsiClass> getImplementedInterfaces() {
            PsiClass aClass = myClass;
            Set<PsiClass> result = new HashSet<>();
            while (aClass != null && !myManager.areElementsEquivalent(aClass, myBaseClass)) {
                PsiClassType[] implementsTypes = aClass.getImplementsListTypes();
                for (PsiClassType implementsType : implementsTypes) {
                    PsiClass resolved = implementsType.resolve();
                    if (resolved != null && !myManager.areElementsEquivalent(resolved, myBaseClass)) {
                        result.add(resolved);
                        InheritanceUtil.getSuperClasses(resolved, result, true);
                    }
                }

                aClass = aClass.getSuperClass();
            }
            return result;
        }

        @Override
        @RequiredReadAction
        public void visitQualifier(PsiReferenceExpression qualified, PsiExpression instanceRef, PsiElement referencedInstance) {
            PsiExpression qualifierExpression = qualified.getQualifierExpression();

            // do not add usages inside a class
            if (qualifierExpression == null
                || qualifierExpression instanceof PsiThisExpression
                || qualifierExpression instanceof PsiSuperExpression) {
                return;
            }

            if (qualified.resolve() instanceof PsiMember member
                && (myBaseClassMembers.contains(member) || myOverriddenMethods.contains(member))
                && !isDelegated(member)) {
                myUsageInfoStorage.add(new NonDelegatedMemberUsageInfo(instanceRef, member, getFieldAccessibility(instanceRef)));
            }
        }

        @Override
        public void visitTypeCast(PsiTypeCastExpression typeCastExpression, PsiExpression instanceRef, PsiElement referencedInstance) {
            processTypedUsage(typeCastExpression.getCastType().getType(), instanceRef);
        }

        @Override
        public void visitReadUsage(PsiExpression instanceRef, PsiType expectedType, PsiElement referencedInstance) {
            processTypedUsage(expectedType, instanceRef);
        }

        @Override
        public void visitWriteUsage(PsiExpression instanceRef, PsiType assignedType, PsiElement referencedInstance) {
        }

        private void processTypedUsage(PsiType type, PsiExpression instanceRef) {
            PsiClass aClass = PsiUtil.resolveClassInType(type);
            if (aClass == null) {
                return;
            }
            String qName = aClass.getQualifiedName();
            if (qName != null && CommonClassNames.JAVA_LANG_OBJECT.equals(qName)) {
                myUsageInfoStorage.add(new ObjectUpcastedUsageInfo(instanceRef, aClass, getFieldAccessibility(instanceRef)));
            }
            else {
                if (myBaseClassBases.contains(aClass)
                    && !myImplementedInterfaces.contains(aClass) && !myDelegatedInterfaces.contains(aClass)) {
                    myUsageInfoStorage.add(new UpcastedUsageInfo(instanceRef, aClass, getFieldAccessibility(instanceRef)));
                }
            }
        }
    }
}
