/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.java.impl.refactoring.memberPullUp;

import com.intellij.java.analysis.impl.codeInsight.intention.AddAnnotationFix;
import com.intellij.java.impl.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.java.impl.refactoring.util.RefactoringHierarchyUtil;
import com.intellij.java.impl.refactoring.util.RefactoringUtil;
import com.intellij.java.impl.refactoring.util.classMembers.ClassMemberReferencesVisitor;
import com.intellij.java.impl.refactoring.util.classMembers.MemberInfo;
import com.intellij.java.indexing.search.searches.OverridingMethodsSearch;
import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.impl.codeInsight.ChangeContextUtil;
import com.intellij.java.language.impl.refactoring.util.RefactoringChangeUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.MethodSignatureUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.util.VisibilityUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.Language;
import consulo.language.codeStyle.CodeStyleSettingsManager;
import consulo.language.editor.PsiEquivalenceUtil;
import consulo.language.editor.ui.util.DocCommentPolicy;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.dataholder.Key;
import consulo.util.lang.function.Condition;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;

/**
 * @author Max Medvedev
 * @since 2013-10-03
 */
public class JavaPullUpHelper implements PullUpHelper<MemberInfo> {
    private static final Logger LOG = Logger.getInstance(JavaPullUpHelper.class);

    private static final Key<Boolean> PRESERVE_QUALIFIER = Key.create("PRESERVE_QUALIFIER");

    private final PsiClass mySourceClass;
    private final PsiClass myTargetSuperClass;
    private final boolean myIsTargetInterface;
    private final DocCommentPolicy myJavaDocPolicy;
    private Set<PsiMember> myMembersAfterMove = null;
    private final Set<PsiMember> myMembersToMove;
    private final Project myProject;

    private final QualifiedThisSuperAdjuster myThisSuperAdjuster;
    private final ExplicitSuperDeleter myExplicitSuperDeleter;

    public JavaPullUpHelper(PullUpData data) {
        myProject = data.getProject();
        myMembersToMove = data.getMembersToMove();
        myMembersAfterMove = data.getMovedMembers();
        myTargetSuperClass = data.getTargetClass();
        mySourceClass = data.getSourceClass();
        myJavaDocPolicy = data.getDocCommentPolicy();
        myIsTargetInterface = myTargetSuperClass.isInterface();

        myThisSuperAdjuster = new QualifiedThisSuperAdjuster();
        myExplicitSuperDeleter = new ExplicitSuperDeleter();
    }

    @Override
    public void encodeContextInfo(MemberInfo info) {
        ChangeContextUtil.encodeContextInfo(info.getMember(), true);
    }

    @Override
    @RequiredWriteAction
    public void move(MemberInfo info, PsiSubstitutor substitutor) {
        if (info.getMember() instanceof PsiMethod) {
            doMoveMethod(substitutor, info);
        }
        else if (info.getMember() instanceof PsiField) {
            doMoveField(substitutor, info);
        }
        else if (info.getMember() instanceof PsiClass) {
            doMoveClass(substitutor, info);
        }
    }

    @Override
    public void postProcessMember(PsiMember member) {
        member.accept(myExplicitSuperDeleter);
        member.accept(myThisSuperAdjuster);

        ChangeContextUtil.decodeContextInfo(member, null, null);

        member.accept(new JavaRecursiveElementWalkingVisitor() {
            @Override
            @RequiredWriteAction
            public void visitReferenceExpression(PsiReferenceExpression expression) {
                PsiExpression qualifierExpression = expression.getQualifierExpression();
                if (qualifierExpression != null) {
                    Boolean preserveQualifier = qualifierExpression.getCopyableUserData(PRESERVE_QUALIFIER);
                    if (preserveQualifier != null && !preserveQualifier) {
                        qualifierExpression.delete();
                        return;
                    }
                }
                super.visitReferenceExpression(expression);
            }
        });

    }

    @Override
    @RequiredReadAction
    public void setCorrectVisibility(MemberInfo info) {
        PsiModifierListOwner modifierListOwner = info.getMember();
        if (myIsTargetInterface) {
            PsiUtil.setModifierProperty(modifierListOwner, PsiModifier.PUBLIC, true);
        }
        else if (modifierListOwner.hasModifierProperty(PsiModifier.PRIVATE)) {
            if (info.isToAbstract() || willBeUsedInSubclass(modifierListOwner, myTargetSuperClass, mySourceClass)) {
                PsiUtil.setModifierProperty(modifierListOwner, PsiModifier.PROTECTED, true);
            }
            if (modifierListOwner instanceof PsiClass) {
                modifierListOwner.accept(new JavaRecursiveElementWalkingVisitor() {
                    @Override
                    @RequiredReadAction
                    public void visitMethod(@Nonnull PsiMethod method) {
                        check(method);
                    }

                    @Override
                    @RequiredReadAction
                    public void visitField(@Nonnull PsiField field) {
                        check(field);
                    }

                    @Override
                    @RequiredReadAction
                    public void visitClass(@Nonnull PsiClass aClass) {
                        check(aClass);
                        super.visitClass(aClass);
                    }

                    @RequiredReadAction
                    private void check(PsiMember member) {
                        if (member.isPrivate() && willBeUsedInSubclass(member, myTargetSuperClass, mySourceClass)) {
                            PsiUtil.setModifierProperty(member, PsiModifier.PROTECTED, true);
                        }
                    }
                });
            }
        }
    }

    @RequiredWriteAction
    private void doMoveClass(PsiSubstitutor substitutor, MemberInfo info) {
        PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(myProject);
        PsiClass aClass = (PsiClass) info.getMember();
        if (Boolean.FALSE.equals(info.getOverrides())) {
            PsiReferenceList sourceReferenceList = info.getSourceReferenceList();
            LOG.assertTrue(sourceReferenceList != null);
            PsiJavaCodeReferenceElement ref = mySourceClass.equals(sourceReferenceList.getParent())
                ? RefactoringUtil.removeFromReferenceList(sourceReferenceList, aClass)
                : RefactoringUtil.findReferenceToClass(sourceReferenceList, aClass);
            if (ref != null && !myTargetSuperClass.isInheritor(aClass, false)) {
                RefactoringUtil.replaceMovedMemberTypeParameters(ref, PsiUtil.typeParametersIterable(mySourceClass),
                    substitutor, elementFactory
                );
                PsiReferenceList referenceList = myIsTargetInterface ? myTargetSuperClass.getExtendsList() :
                    myTargetSuperClass.getImplementsList();
                assert referenceList != null;
                referenceList.add(ref);
            }
        }
        else {
            RefactoringUtil.replaceMovedMemberTypeParameters(aClass, PsiUtil.typeParametersIterable(mySourceClass),
                substitutor, elementFactory
            );
            fixReferencesToStatic(aClass);
            PsiMember movedElement = (PsiMember) myTargetSuperClass.add(convertClassToLanguage(
                aClass,
                myTargetSuperClass.getLanguage()
            ));
            myMembersAfterMove.add(movedElement);
            aClass.delete();
        }
    }

    @RequiredWriteAction
    private void doMoveField(PsiSubstitutor substitutor, MemberInfo info) {
        PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(myProject);
        PsiField field = (PsiField) info.getMember();
        field.normalizeDeclaration();
        RefactoringUtil.replaceMovedMemberTypeParameters(field, PsiUtil.typeParametersIterable(mySourceClass),
            substitutor, elementFactory
        );
        fixReferencesToStatic(field);
        if (myIsTargetInterface) {
            PsiUtil.setModifierProperty(field, PsiModifier.PUBLIC, true);
        }
        PsiMember movedElement = (PsiMember) myTargetSuperClass.add(convertFieldToLanguage(
            field,
            myTargetSuperClass.getLanguage()
        ));
        myMembersAfterMove.add(movedElement);
        field.delete();
    }

    @RequiredWriteAction
    private void doMoveMethod(PsiSubstitutor substitutor, MemberInfo info) {
        PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(myProject);
        PsiMethod method = (PsiMethod) info.getMember();
        PsiMethod sibling = method;
        PsiMethod anchor = null;
        while (sibling != null) {
            sibling = PsiTreeUtil.getNextSiblingOfType(sibling, PsiMethod.class);
            if (sibling != null) {
                anchor = MethodSignatureUtil.findMethodInSuperClassBySignatureInDerived(
                    method.getContainingClass(),
                    myTargetSuperClass,
                    sibling.getSignature(PsiSubstitutor.EMPTY),
                    false
                );
                if (anchor != null) {
                    break;
                }
            }
        }
        PsiMethod methodCopy = (PsiMethod) method.copy();
        RefactoringUtil.replaceMovedMemberTypeParameters(methodCopy, PsiUtil.typeParametersIterable(mySourceClass),
            substitutor, elementFactory
        );

        Language language = myTargetSuperClass.getLanguage();
        PsiMethod superClassMethod = myTargetSuperClass.findMethodBySignature(methodCopy, false);
        if (superClassMethod != null && superClassMethod.findDeepestSuperMethods().length == 0 || method
            .findSuperMethods(myTargetSuperClass).length == 0) {
            deleteOverrideAnnotationIfFound(methodCopy);
        }
        boolean isOriginalMethodAbstract = method.isAbstract()
            || method.hasModifierProperty(PsiModifier.DEFAULT);
        if (myIsTargetInterface || info.isToAbstract()) {
            ChangeContextUtil.clearContextInfo(method);

            if (!info.isToAbstract() && !method.isAbstract() && PsiUtil.isLanguageLevel8OrHigher(myTargetSuperClass)) {
                //pull as default
                RefactoringUtil.makeMethodDefault(methodCopy);
                isOriginalMethodAbstract = true;
            }
            else {
                RefactoringUtil.makeMethodAbstract(myTargetSuperClass, methodCopy);
            }

            myJavaDocPolicy.processCopiedJavaDoc(methodCopy.getDocComment(), method.getDocComment(), isOriginalMethodAbstract);

            PsiMember movedElement;
            if (superClassMethod != null && superClassMethod.isAbstract()) {
                movedElement = (PsiMember) superClassMethod.replace(convertMethodToLanguage(methodCopy, language));
            }
            else {
                movedElement = anchor != null
                    ? (PsiMember) myTargetSuperClass.addBefore(methodCopy, anchor)
                    : (PsiMember) myTargetSuperClass.add(methodCopy);
            }
            JavaCodeStyleSettings styleSettings =
                CodeStyleSettingsManager.getSettings(method.getProject()).getCustomSettings(JavaCodeStyleSettings.class);
            if (styleSettings.INSERT_OVERRIDE_ANNOTATION) {
                if (PsiUtil.isLanguageLevel5OrHigher(mySourceClass) && !myIsTargetInterface
                    || PsiUtil.isLanguageLevel6OrHigher(mySourceClass)) {
                    new AddAnnotationFix(Override.class.getName(), method).invoke(method.getProject(), null,
                        mySourceClass.getContainingFile()
                    );
                }
            }
            if (!PsiUtil.isLanguageLevel6OrHigher(mySourceClass) && myIsTargetInterface) {
                if (isOriginalMethodAbstract) {
                    for (PsiMethod oMethod : OverridingMethodsSearch.search(method)) {
                        deleteOverrideAnnotationIfFound(oMethod);
                    }
                }
                deleteOverrideAnnotationIfFound(method);
            }
            myMembersAfterMove.add(movedElement);
            if (isOriginalMethodAbstract) {
                method.delete();
            }
        }
        else {
            if (isOriginalMethodAbstract) {
                PsiUtil.setModifierProperty(myTargetSuperClass, PsiModifier.ABSTRACT, true);
            }
            RefactoringUtil.replaceMovedMemberTypeParameters(methodCopy, PsiUtil.typeParametersIterable(mySourceClass)
                , substitutor, elementFactory);
            fixReferencesToStatic(methodCopy);

            if (superClassMethod != null && superClassMethod.isAbstract()) {
                superClassMethod.replace(convertMethodToLanguage(methodCopy, language));
            }
            else {
                PsiMember movedElement = anchor != null ? (PsiMember) myTargetSuperClass.addBefore
                    (convertMethodToLanguage(methodCopy, language), anchor) : (PsiMember) myTargetSuperClass.add
                    (convertMethodToLanguage(methodCopy, language));
                myMembersAfterMove.add(movedElement);
            }
            method.delete();
        }
    }

    @RequiredReadAction
    private static PsiMethod convertMethodToLanguage(PsiMethod method, Language language) {
        if (method.getLanguage().equals(language)) {
            return method;
        }
        return JVMElementFactories.getFactory(language, method.getProject()).createMethodFromText(method.getText(), null);
    }

    @RequiredReadAction
    private static PsiField convertFieldToLanguage(PsiField field, Language language) {
        if (field.getLanguage().equals(language)) {
            return field;
        }
        return JVMElementFactories.getFactory(language, field.getProject()).createField(field.getName(), field.getType());
    }

    private static PsiClass convertClassToLanguage(PsiClass clazz, Language language) {
        //if (clazz.getLanguage().equals(language)) {
        //    return clazz;
        //}
        //PsiClass newClass = JVMElementFactories.getFactory(language, clazz.getProject()).createClass(clazz.getName());
        return clazz;
    }

    @RequiredWriteAction
    private static void deleteOverrideAnnotationIfFound(PsiMethod oMethod) {
        PsiAnnotation annotation = AnnotationUtil.findAnnotation(oMethod, Override.class.getName());
        if (annotation != null) {
            annotation.delete();
        }
    }

    @Override
    @RequiredWriteAction
    public void moveFieldInitializations(Set<PsiField> movedFields) {
        PsiMethod[] constructors = myTargetSuperClass.getConstructors();

        if (constructors.length == 0) {
            constructors = new PsiMethod[]{null};
        }

        Map<PsiMethod, Set<PsiMethod>> constructorsToSubConstructors = buildConstructorsToSubConstructorsMap(constructors);
        for (PsiMethod constructor : constructors) {
            Set<PsiMethod> subConstructors = constructorsToSubConstructors.get(constructor);
            tryToMoveInitializers(constructor, subConstructors, movedFields);
        }
    }

    @Override
    @RequiredWriteAction
    public void updateUsage(PsiElement element) {
        if (element instanceof PsiReferenceExpression refExpr
            && refExpr.getQualifierExpression() instanceof PsiReferenceExpression qRefExpr
            && qRefExpr.resolve() == mySourceClass) {
            qRefExpr.bindToElement(myTargetSuperClass);
        }
    }

    private static class Initializer {
        public final PsiStatement initializer;
        public final Set<PsiField> movedFieldsUsed;
        public final Set<PsiParameter> usedParameters;
        public final List<PsiElement> statementsToRemove;

        private Initializer(
            PsiStatement initializer,
            Set<PsiField> movedFieldsUsed,
            Set<PsiParameter> usedParameters,
            List<PsiElement> statementsToRemove
        ) {
            this.initializer = initializer;
            this.movedFieldsUsed = movedFieldsUsed;
            this.statementsToRemove = statementsToRemove;
            this.usedParameters = usedParameters;
        }
    }

    @RequiredWriteAction
    private void tryToMoveInitializers(
        PsiMethod constructor,
        Set<PsiMethod> subConstructors,
        Set<PsiField> movedFields
    ) throws IncorrectOperationException {
        final Map<PsiField, Initializer> fieldsToInitializers = new LinkedHashMap<>();
        boolean anyFound = false;

        for (PsiField field : movedFields) {
            PsiStatement commonInitializer = null;
            List<PsiElement> fieldInitializersToRemove = new ArrayList<>();
            for (PsiMethod subConstructor : subConstructors) {
                commonInitializer = hasCommonInitializer(commonInitializer, subConstructor, field, fieldInitializersToRemove);
                if (commonInitializer == null) {
                    break;
                }
            }
            if (commonInitializer != null) {
                ParametersAndMovedFieldsUsedCollector visitor = new ParametersAndMovedFieldsUsedCollector(movedFields);
                commonInitializer.accept(visitor);
                fieldsToInitializers.put(field, new Initializer(commonInitializer, visitor.getUsedFields(),
                    visitor.getUsedParameters(), fieldInitializersToRemove
                ));
                anyFound = true;
            }
        }

        if (!anyFound) {
            return;
        }


        {
            final Set<PsiField> initializedFields = fieldsToInitializers.keySet();
            Set<PsiField> unmovable = RefactoringUtil.transitiveClosure(
                new RefactoringUtil.Graph<>() {
                    @Override
                    public Set<PsiField> getVertices() {
                        return initializedFields;
                    }

                    @Override
                    public Set<PsiField> getTargets(PsiField source) {
                        return fieldsToInitializers.get(source).movedFieldsUsed;
                    }
                },
                (Condition<PsiField>) object -> !initializedFields.contains(object)
            );

            for (PsiField psiField : unmovable) {
                fieldsToInitializers.remove(psiField);
            }
        }

        PsiElementFactory factory = JavaPsiFacade.getElementFactory(myProject);

        if (constructor == null) {
            constructor = (PsiMethod) myTargetSuperClass.add(factory.createConstructor());
            String visibilityModifier = VisibilityUtil.getVisibilityModifier(myTargetSuperClass.getModifierList
                ());
            PsiUtil.setModifierProperty(constructor, visibilityModifier, true);
        }


        List<PsiField> initializedFields = new ArrayList<>(fieldsToInitializers.keySet());

        Collections.sort(
            initializedFields,
            (field1, field2) -> {
                Initializer i1 = fieldsToInitializers.get(field1);
                Initializer i2 = fieldsToInitializers.get(field2);
                if (i1.movedFieldsUsed.contains(field2)) {
                    return 1;
                }
                if (i2.movedFieldsUsed.contains(field1)) {
                    return -1;
                }
                return 0;
            }
        );

        for (PsiField initializedField : initializedFields) {
            Initializer initializer = fieldsToInitializers.get(initializedField);

            //correct constructor parameters and subConstructors super calls
            PsiParameterList parameterList = constructor.getParameterList();
            for (PsiParameter parameter : initializer.usedParameters) {
                parameterList.add(parameter);
            }

            for (PsiMethod subConstructor : subConstructors) {
                modifySuperCall(subConstructor, initializer.usedParameters);
            }

            PsiStatement assignmentStatement = (PsiStatement) constructor.getBody().add(initializer.initializer);

            PsiManager manager = PsiManager.getInstance(myProject);
            ChangeContextUtil.decodeContextInfo(
                assignmentStatement,
                myTargetSuperClass,
                RefactoringChangeUtil.createThisExpression(manager, null)
            );
            for (PsiElement psiElement : initializer.statementsToRemove) {
                psiElement.delete();
            }
        }
    }

    @RequiredWriteAction
    private static void modifySuperCall(PsiMethod subConstructor, Set<PsiParameter> parametersToPassToSuper) {
        PsiCodeBlock body = subConstructor.getBody();
        if (body != null) {
            PsiMethodCallExpression superCall = null;
            PsiStatement[] statements = body.getStatements();
            if (statements.length > 0
                && statements[0] instanceof PsiExpressionStatement fistStmt
                && fistStmt.getExpression() instanceof PsiMethodCallExpression methodCall
                && "super".equals(methodCall.getMethodExpression().getText())) {
                superCall = methodCall;
            }

            PsiElementFactory factory = JavaPsiFacade.getInstance(subConstructor.getProject()).getElementFactory();
            try {
                if (superCall == null) {
                    PsiExpressionStatement statement = (PsiExpressionStatement) factory.createStatementFromText("super();", null);
                    statement = (PsiExpressionStatement) body.addAfter(statement, null);
                    superCall = (PsiMethodCallExpression) statement.getExpression();
                }

                PsiExpressionList argList = superCall.getArgumentList();
                for (PsiParameter parameter : parametersToPassToSuper) {
                    argList.add(factory.createExpressionFromText(parameter.getName(), null));
                }
            }
            catch (IncorrectOperationException e) {
                LOG.error(e);
            }
        }
    }

    @Nullable
    @RequiredReadAction
    private PsiStatement hasCommonInitializer(
        PsiStatement commonInitializer,
        PsiMethod subConstructor,
        PsiField field,
        List<PsiElement> statementsToRemove
    ) {
        PsiCodeBlock body = subConstructor.getBody();
        if (body == null) {
            return null;
        }
        PsiStatement[] statements = body.getStatements();

        // Algorithm: there should be only one write usage of field in a subConstructor,
        // and in that usage field must be a target of top-level assignment, and RHS of assignment
        // should be the same as commonInitializer if latter is non-null.
        //
        // There should be no usages before that initializer, and there should be
        // no write usages afterwards.
        PsiStatement commonInitializerCandidate = null;
        for (PsiStatement statement : statements) {
            Set<PsiStatement> collectedStatements = new HashSet<>();
            collectPsiStatements(statement, collectedStatements);
            boolean doLookup = true;
            for (PsiStatement collectedStatement : collectedStatements) {
                if (collectedStatement instanceof PsiExpressionStatement exprStmt
                    && exprStmt.getExpression() instanceof PsiAssignmentExpression assignment
                    && assignment.getLExpression() instanceof PsiReferenceExpression lRef) {
                    if (lRef.getQualifierExpression() == null || lRef.getQualifierExpression() instanceof PsiThisExpression) {
                        if (lRef.resolve() == field) {
                            doLookup = false;
                            if (commonInitializerCandidate == null) {
                                PsiExpression initializer = assignment.getRExpression();
                                if (initializer == null) {
                                    return null;
                                }
                                if (commonInitializer == null) {
                                    IsMovableInitializerVisitor visitor = new IsMovableInitializerVisitor();
                                    statement.accept(visitor);
                                    if (visitor.isMovable()) {
                                        ChangeContextUtil.encodeContextInfo(statement, true);
                                        PsiStatement statementCopy = (PsiStatement) statement.copy();
                                        ChangeContextUtil.clearContextInfo(statement);
                                        statementsToRemove.add(statement);
                                        commonInitializerCandidate = statementCopy;
                                    }
                                    else {
                                        return null;
                                    }
                                }
                                else if (PsiEquivalenceUtil.areElementsEquivalent(commonInitializer, statement)) {
                                    statementsToRemove.add(statement);
                                    commonInitializerCandidate = commonInitializer;
                                }
                                else {
                                    return null;
                                }
                            }
                            else if (!PsiEquivalenceUtil.areElementsEquivalent(commonInitializerCandidate, statement)) {
                                return null;
                            }
                        }
                    }
                }
            }
            if (doLookup) {
                PsiReference[] references =
                    ReferencesSearch.search(field, new LocalSearchScope(statement), false).toArray(new PsiReference[0]);
                if (commonInitializerCandidate == null && references.length > 0) {
                    return null;
                }

                for (PsiReference reference : references) {
                    if (RefactoringUtil.isAssignmentLHS(reference.getElement())) {
                        return null;
                    }
                }
            }
        }
        return commonInitializerCandidate;
    }

    @RequiredReadAction
    private static void collectPsiStatements(PsiElement root, Set<PsiStatement> collected) {
        if (root instanceof PsiStatement statement) {
            collected.add(statement);
        }

        for (PsiElement element : root.getChildren()) {
            collectPsiStatements(element, collected);
        }
    }

    private static class ParametersAndMovedFieldsUsedCollector extends JavaRecursiveElementWalkingVisitor {
        private final Set<PsiField> myMovedFields;
        private final Set<PsiField> myUsedFields;

        private final Set<PsiParameter> myUsedParameters = new LinkedHashSet<>();

        private ParametersAndMovedFieldsUsedCollector(Set<PsiField> movedFields) {
            myMovedFields = movedFields;
            myUsedFields = new HashSet<>();
        }

        public Set<PsiParameter> getUsedParameters() {
            return myUsedParameters;
        }

        public Set<PsiField> getUsedFields() {
            return myUsedFields;
        }

        @Override
        @RequiredReadAction
        public void visitReferenceExpression(PsiReferenceExpression expression) {
            PsiExpression qualifierExpression = expression.getQualifierExpression();
            if (qualifierExpression != null && !(qualifierExpression instanceof PsiThisExpression)) {
                return;
            }
            PsiElement resolved = expression.resolve();
            if (resolved instanceof PsiParameter parameter) {
                myUsedParameters.add(parameter);
            }
            else if (myMovedFields.contains(resolved)) {
                myUsedFields.add((PsiField) resolved);
            }
        }
    }

    private class IsMovableInitializerVisitor extends JavaRecursiveElementWalkingVisitor {
        private boolean myIsMovable = true;

        public boolean isMovable() {
            return myIsMovable;
        }

        @Override
        @RequiredReadAction
        public void visitReferenceExpression(PsiReferenceExpression expression) {
            visitReferenceElement(expression);
        }

        @Override
        @RequiredReadAction
        public void visitReferenceElement(@Nonnull PsiJavaCodeReferenceElement referenceElement) {
            if (!myIsMovable) {
                return;
            }
            PsiExpression qualifier;
            if (referenceElement instanceof PsiReferenceExpression refExpr) {
                qualifier = refExpr.getQualifierExpression();
            }
            else {
                qualifier = null;
            }
            if (qualifier == null || qualifier instanceof PsiThisExpression || qualifier instanceof PsiSuperExpression) {
                PsiElement resolved = referenceElement.resolve();
                if (!(resolved instanceof PsiParameter)) {
                    if (resolved instanceof PsiClass psiClass && (psiClass.isStatic() || psiClass.getContainingClass() == null)) {
                        return;
                    }
                    PsiClass containingClass = null;
                    if (resolved instanceof PsiMember member && !member.isStatic()) {
                        containingClass = member.getContainingClass();
                    }
                    myIsMovable = containingClass != null && InheritanceUtil.isInheritorOrSelf(myTargetSuperClass, containingClass, true);
                }
            }
            else {
                qualifier.accept(this);
            }
        }

        @Override
        public void visitElement(PsiElement element) {
            if (myIsMovable) {
                super.visitElement(element);
            }
        }
    }

    @RequiredReadAction
    private Map<PsiMethod, Set<PsiMethod>> buildConstructorsToSubConstructorsMap(PsiMethod[] constructors) {
        Map<PsiMethod, Set<PsiMethod>> constructorsToSubConstructors = new HashMap<>();
        for (PsiMethod constructor : constructors) {
            Set<PsiMethod> referencingSubConstructors = new HashSet<>();
            constructorsToSubConstructors.put(constructor, referencingSubConstructors);
            if (constructor != null) {
                // find references
                for (PsiReference reference : ReferencesSearch.search(constructor, new LocalSearchScope(mySourceClass), false)) {
                    PsiElement element = reference.getElement();
                    if (element != null && "super".equals(element.getText())) {
                        PsiMethod parentMethod = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
                        if (parentMethod != null && parentMethod.isConstructor()) {
                            referencingSubConstructors.add(parentMethod);
                        }
                    }
                }
            }

            // check default constructor
            if (constructor == null || constructor.getParameterList().getParametersCount() == 0) {
                RefactoringUtil.visitImplicitSuperConstructorUsages(
                    mySourceClass,
                    new RefactoringUtil.ImplicitConstructorUsageVisitor() {
                        @Override
                        public void visitConstructor(PsiMethod constructor, PsiMethod baseConstructor) {
                            referencingSubConstructors.add(constructor);
                        }

                        @Override
                        public void visitClassWithoutConstructors(PsiClass aClass) {
                        }
                    },
                    myTargetSuperClass
                );

            }
        }
        return constructorsToSubConstructors;
    }

    @RequiredWriteAction
    private void fixReferencesToStatic(PsiElement classMember) throws IncorrectOperationException {
        StaticReferencesCollector collector = new StaticReferencesCollector();
        classMember.accept(collector);
        List<PsiJavaCodeReferenceElement> refs = collector.getReferences();
        List<PsiElement> members = collector.getReferees();
        List<PsiClass> classes = collector.getRefereeClasses();
        PsiElementFactory factory = JavaPsiFacade.getInstance(classMember.getProject()).getElementFactory();

        for (int i = 0; i < refs.size(); i++) {
            PsiJavaCodeReferenceElement ref = refs.get(i);
            PsiClass aClass = classes.get(i);

            if (members.get(i) instanceof PsiNamedElement namedElement) {
                PsiReferenceExpression newRef =
                    (PsiReferenceExpression) factory.createExpressionFromText("a." + namedElement.getName(), null);
                PsiExpression qualifierExpression = newRef.getQualifierExpression();
                assert qualifierExpression != null;
                qualifierExpression = (PsiExpression) qualifierExpression.replace(factory.createReferenceExpression(aClass));
                qualifierExpression.putCopyableUserData(PRESERVE_QUALIFIER, ref.isQualified());
                ref.replace(newRef);
            }
        }
    }

    private class StaticReferencesCollector extends ClassMemberReferencesVisitor {
        private final List<PsiJavaCodeReferenceElement> myReferences;
        private final List<PsiElement> myReferees;
        private final List<PsiClass> myRefereeClasses;

        private StaticReferencesCollector() {
            super(mySourceClass);
            myReferees = new ArrayList<>();
            myRefereeClasses = new ArrayList<>();
            myReferences = new ArrayList<>();
        }

        public List<PsiElement> getReferees() {
            return myReferees;
        }

        public List<PsiClass> getRefereeClasses() {
            return myRefereeClasses;
        }

        public List<PsiJavaCodeReferenceElement> getReferences() {
            return myReferences;
        }

        @Override
        protected void visitClassMemberReferenceElement(PsiMember classMember, PsiJavaCodeReferenceElement classMemberReference) {
            if (classMember.isStatic()) {
                if (!myMembersToMove.contains(classMember) && RefactoringHierarchyUtil.isMemberBetween
                    (myTargetSuperClass, mySourceClass, classMember)) {
                    myReferences.add(classMemberReference);
                    myReferees.add(classMember);
                    myRefereeClasses.add(classMember.getContainingClass());
                }
                else if (myMembersToMove.contains(classMember) || myMembersAfterMove.contains(classMember)) {
                    myReferences.add(classMemberReference);
                    myReferees.add(classMember);
                    myRefereeClasses.add(myTargetSuperClass);
                }
            }
        }
    }

    private class QualifiedThisSuperAdjuster extends JavaRecursiveElementVisitor {
        @Override
        @RequiredWriteAction
        public void visitThisExpression(@Nonnull PsiThisExpression expression) {
            super.visitThisExpression(expression);
            PsiJavaCodeReferenceElement qualifier = expression.getQualifier();
            if (qualifier != null && qualifier.isReferenceTo(mySourceClass)) {
                try {
                    qualifier.bindToElement(myTargetSuperClass);
                }
                catch (IncorrectOperationException e) {
                    LOG.error(e);
                }
            }
        }

        @Override
        @RequiredWriteAction
        public void visitSuperExpression(@Nonnull PsiSuperExpression expression) {
            super.visitSuperExpression(expression);
            PsiJavaCodeReferenceElement qualifier = expression.getQualifier();
            if (qualifier != null && qualifier.isReferenceTo(mySourceClass)) {
                try {
                    expression.replace(
                        JavaPsiFacade.getElementFactory(myProject).createExpressionFromText(myTargetSuperClass.getName() + ".this", null)
                    );
                }
                catch (IncorrectOperationException e) {
                    LOG.error(e);
                }
            }
        }
    }

    private class ExplicitSuperDeleter extends JavaRecursiveElementWalkingVisitor {
        private final PsiExpression myThisExpression = JavaPsiFacade.getElementFactory(myProject)
            .createExpressionFromText("this", null);

        @Override
        @RequiredWriteAction
        public void visitReferenceExpression(@Nonnull PsiReferenceExpression expression) {
            if (expression.getQualifierExpression() instanceof PsiSuperExpression superExpr) {
                PsiElement resolved = expression.resolve();
                if (resolved == null || resolved instanceof PsiMethod method && shouldFixSuper(method)) {
                    superExpr.delete();
                }
            }
        }

        @Override
        @RequiredWriteAction
        public void visitSuperExpression(PsiSuperExpression expression) {
            expression.replace(myThisExpression);
        }

        @Override
        public void visitClass(@Nonnull PsiClass aClass) {
            // do nothing
        }

        private boolean shouldFixSuper(PsiMethod method) {
            for (PsiMember element : myMembersAfterMove) {
                if (element instanceof PsiMethod member) {
                    // if there is such member among moved members, super qualifier
                    // should not be removed
                    PsiManager manager = method.getManager();
                    if (manager.areElementsEquivalent(member.getContainingClass(), method.getContainingClass())
                        && MethodSignatureUtil.areSignaturesEqual(member, method)) {
                        return false;
                    }
                }
            }

            PsiMethod methodFromSuper = myTargetSuperClass.findMethodBySignature(method, false);
            return methodFromSuper == null;
        }
    }

    @RequiredReadAction
    private boolean willBeUsedInSubclass(PsiElement member, PsiClass superclass, PsiClass subclass) {
        for (PsiReference ref : ReferencesSearch.search(member, new LocalSearchScope(subclass), false)) {
            PsiElement element = ref.getElement();
            if (!RefactoringHierarchyUtil.willBeInTargetClass(element, myMembersToMove, superclass, false)) {
                return true;
            }
        }
        return false;
    }
}
