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
package com.intellij.java.impl.refactoring.makeStatic;

import com.intellij.java.impl.refactoring.move.MoveInstanceMembersUtil;
import com.intellij.java.impl.refactoring.util.RefactoringUtil;
import com.intellij.java.impl.refactoring.util.javadoc.MethodJavaDocHelper;
import com.intellij.java.language.impl.refactoring.util.RefactoringChangeUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.codeStyle.VariableKind;
import com.intellij.java.language.psi.javadoc.PsiDocTag;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.codeStyle.CodeStyleSettingsManager;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.ui.RefactoringUIUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.PsiReference;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.usage.UsageInfo;
import consulo.util.collection.MultiMap;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ven
 */
public class MakeClassStaticProcessor extends MakeMethodOrClassStaticProcessor<PsiClass> {
    private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.makeMethodStatic.MakeClassStaticProcessor");
    private List<PsiField> myFieldsToSplit = new ArrayList<PsiField>();

    public MakeClassStaticProcessor(Project project, PsiClass aClass, Settings settings) {
        super(project, aClass, settings);
    }

    protected void changeSelf(PsiElementFactory factory, UsageInfo[] usages) throws IncorrectOperationException {
        PsiClass containingClass = myMember.getContainingClass();

        //Add fields
        if (mySettings.isMakeClassParameter()) {
            PsiType type = factory.createType(containingClass, PsiSubstitutor.EMPTY);
            String classParameterName = mySettings.getClassParameterName();
            String fieldName = convertToFieldName(classParameterName);
            myMember.add(factory.createField(fieldName, type));
        }

        if (mySettings.isMakeFieldParameters()) {
            List<Settings.FieldParameter> parameters = mySettings.getParameterOrderList();

            for (Settings.FieldParameter fieldParameter : parameters) {
                PsiType type = fieldParameter.type;
                PsiField field = factory.createField(convertToFieldName(fieldParameter.name), type);
                myMember.add(field);
            }
        }


        PsiMethod[] constructors = myMember.getConstructors();

        if (constructors.length == 0) {
            PsiMethod defConstructor = (PsiMethod) myMember.add(factory.createConstructor());
            constructors = new PsiMethod[]{defConstructor};
        }

        boolean generateFinalParams = CodeStyleSettingsManager.getSettings(myProject).GENERATE_FINAL_PARAMETERS;
        for (PsiMethod constructor : constructors) {
            MethodJavaDocHelper javaDocHelper = new MethodJavaDocHelper(constructor);
            PsiParameterList paramList = constructor.getParameterList();
            PsiElement addParameterAfter = null;
            PsiDocTag anchor = null;

            if (mySettings.isMakeClassParameter()) {
                // Add parameter for object
                PsiType parameterType = factory.createType(containingClass, PsiSubstitutor.EMPTY);
                String classParameterName = mySettings.getClassParameterName();
                PsiParameter parameter = factory.createParameter(classParameterName, parameterType);
                PsiUtil.setModifierProperty(parameter, PsiModifier.FINAL, makeClassParameterFinal(usages) || generateFinalParams);
                addParameterAfter = paramList.addAfter(parameter, null);
                anchor = javaDocHelper.addParameterAfter(classParameterName, anchor);

                addAssignmentToField(classParameterName, constructor);

            }

            if (mySettings.isMakeFieldParameters()) {
                List<Settings.FieldParameter> parameters = mySettings.getParameterOrderList();

                for (Settings.FieldParameter fieldParameter : parameters) {
                    PsiType fieldParameterType = fieldParameter.field.getType();
                    PsiParameter parameter = factory.createParameter(fieldParameter.name, fieldParameterType);
                    PsiUtil.setModifierProperty(parameter, PsiModifier.FINAL,
                        makeFieldParameterFinal(fieldParameter.field, usages) || generateFinalParams
                    );
                    addParameterAfter = paramList.addAfter(parameter, addParameterAfter);
                    anchor = javaDocHelper.addParameterAfter(fieldParameter.name, anchor);
                    addAssignmentToField(fieldParameter.name, constructor);
                }
                for (UsageInfo usage : usages) {
                    if (usage instanceof InternalUsageInfo) {
                        PsiElement element = usage.getElement();
                        PsiElement referencedElement = ((InternalUsageInfo) usage).getReferencedElement();
                        if (referencedElement instanceof PsiField && mySettings.getNameForField((PsiField) referencedElement) != null) {
                            PsiField field = PsiTreeUtil.getParentOfType(element, PsiField.class);
                            if (field != null) {
                                MoveInstanceMembersUtil.moveInitializerToConstructor(factory, constructor, field);
                            }
                        }
                    }
                }
            }
            for (PsiField field : myFieldsToSplit) {
                MoveInstanceMembersUtil.moveInitializerToConstructor(factory, constructor, field);
            }
        }


        setupTypeParameterList();

        // Add static modifier
        PsiModifierList modifierList = myMember.getModifierList();
        modifierList.setModifierProperty(PsiModifier.STATIC, true);
        modifierList.setModifierProperty(PsiModifier.FINAL, false);
    }

    private void addAssignmentToField(String parameterName, PsiMethod constructor) {
        @NonNls String fieldName = convertToFieldName(parameterName);
        PsiManager manager = PsiManager.getInstance(myProject);
        PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
        PsiCodeBlock body = constructor.getBody();
        if (body != null) {
            try {
                PsiReferenceExpression refExpr = (PsiReferenceExpression) factory.createExpressionFromText(fieldName, body);
                if (refExpr.resolve() != null) {
                    fieldName = "this." + fieldName;
                }
                PsiStatement statement = factory.createStatementFromText(fieldName + "=" + parameterName + ";", null);
                statement = (PsiStatement) CodeStyleManager.getInstance(manager.getProject()).reformat(statement);
                body.add(statement);
            }
            catch (IncorrectOperationException e) {
                LOG.error(e);
            }
        }
    }

    private String convertToFieldName(String parameterName) {
        JavaCodeStyleManager manager = JavaCodeStyleManager.getInstance(myProject);
        String propertyName = manager.variableNameToPropertyName(parameterName, VariableKind.PARAMETER);
        String fieldName = manager.propertyNameToVariableName(propertyName, VariableKind.FIELD);
        return fieldName;
    }

    protected void changeSelfUsage(SelfUsageInfo usageInfo) throws IncorrectOperationException {
        PsiElement parent = usageInfo.getElement().getParent();
        LOG.assertTrue(parent instanceof PsiCallExpression); //either this() or new()
        PsiCallExpression call = (PsiCallExpression) parent;
        PsiElementFactory factory = JavaPsiFacade.getInstance(call.getProject()).getElementFactory();
        PsiExpressionList args = call.getArgumentList();
        PsiElement addParameterAfter = null;

        if (mySettings.isMakeClassParameter()) {
            PsiElement arg = factory.createExpressionFromText(convertToFieldName(mySettings.getClassParameterName()), null);
            addParameterAfter = args.addAfter(arg, null);
        }

        if (mySettings.isMakeFieldParameters()) {
            List<Settings.FieldParameter> parameters = mySettings.getParameterOrderList();
            for (Settings.FieldParameter fieldParameter : parameters) {
                PsiElement arg = factory.createExpressionFromText(convertToFieldName(fieldParameter.name), null);
                if (addParameterAfter == null) {
                    addParameterAfter = args.addAfter(arg, null);
                }
                else {
                    addParameterAfter = args.addAfter(arg, addParameterAfter);
                }
            }
        }
    }

    protected void changeInternalUsage(InternalUsageInfo usage, PsiElementFactory factory) throws IncorrectOperationException {
        if (!mySettings.isChangeSignature()) {
            return;
        }

        PsiElement element = usage.getElement();

        if (element instanceof PsiReferenceExpression) {
            PsiReferenceExpression newRef = null;

            if (mySettings.isMakeFieldParameters()) {
                PsiElement resolved = ((PsiReferenceExpression) element).resolve();
                if (resolved instanceof PsiField) {
                    String name = mySettings.getNameForField((PsiField) resolved);
                    if (name != null) {
                        name = convertToFieldName(name);
                        if (name != null) {
                            newRef = (PsiReferenceExpression) factory.createExpressionFromText(name, null);
                        }
                    }
                }
            }

            if (newRef == null && mySettings.isMakeClassParameter()) {
                newRef =
                    (PsiReferenceExpression) factory.createExpressionFromText(
                        convertToFieldName(mySettings.getClassParameterName()) + "." + element.getText(), null);
            }

            if (newRef != null) {
                CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(myProject);
                newRef = (PsiReferenceExpression) codeStyleManager.reformat(newRef);
                element.replace(newRef);
            }
        }
        else if (mySettings.isMakeClassParameter() && (element instanceof PsiThisExpression || element instanceof PsiSuperExpression)) {
            PsiElement replace =
                element.replace(factory.createExpressionFromText(convertToFieldName(mySettings.getClassParameterName()), null));
            PsiField field = PsiTreeUtil.getParentOfType(replace, PsiField.class);
            if (field != null) {
                myFieldsToSplit.add(field);
            }
        }
        else if (element instanceof PsiNewExpression && mySettings.isMakeClassParameter()) {
            PsiNewExpression newExpression = ((PsiNewExpression) element);
            LOG.assertTrue(newExpression.getQualifier() == null);
            String newText = convertToFieldName(mySettings.getClassParameterName()) + "." + newExpression.getText();
            PsiExpression expr = factory.createExpressionFromText(newText, null);
            element.replace(expr);
        }
    }

    protected void changeExternalUsage(UsageInfo usage, PsiElementFactory factory) throws IncorrectOperationException {
        PsiElement element = usage.getElement();
        if (!(element instanceof PsiJavaCodeReferenceElement)) {
            return;
        }

        PsiJavaCodeReferenceElement methodRef = (PsiJavaCodeReferenceElement) element;
        PsiElement parent = methodRef.getParent();
        if (parent instanceof PsiAnonymousClass) {
            parent = parent.getParent();
        }
        LOG.assertTrue(parent instanceof PsiCallExpression, "call expression expected, found " + parent);

        PsiCallExpression call = (PsiCallExpression) parent;

        PsiExpression instanceRef;

        instanceRef =
            call instanceof PsiMethodCallExpression ? ((PsiMethodCallExpression) call).getMethodExpression().getQualifierExpression() :
                ((PsiNewExpression) call).getQualifier();
        PsiElement newQualifier;

        if (instanceRef == null || instanceRef instanceof PsiSuperExpression) {
            PsiClass thisClass = RefactoringChangeUtil.getThisClass(element);
            @NonNls String thisText;
            if (thisClass.getManager().areElementsEquivalent(thisClass, myMember.getContainingClass())) {
                thisText = "this";
            }
            else {
                thisText = myMember.getContainingClass().getName() + ".this";
            }
            instanceRef = factory.createExpressionFromText(thisText, null);
            newQualifier = null;
        }
        else {
            newQualifier = factory.createReferenceExpression(myMember.getContainingClass());
        }

        if (mySettings.getNewParametersNumber() > 1) {
            int copyingSafetyLevel = RefactoringUtil.verifySafeCopyExpression(instanceRef);
            if (copyingSafetyLevel == RefactoringUtil.EXPR_COPY_PROHIBITED) {
                String tempVar = RefactoringUtil.createTempVar(instanceRef, call, true);
                instanceRef = factory.createExpressionFromText(tempVar, null);
            }
        }


        PsiElement anchor = null;
        PsiExpressionList argList = call.getArgumentList();
        PsiExpression[] exprs = argList.getExpressions();
        if (mySettings.isMakeClassParameter()) {
            if (exprs.length > 0) {
                anchor = argList.addBefore(instanceRef, exprs[0]);
            }
            else {
                anchor = argList.add(instanceRef);
            }
        }


        if (mySettings.isMakeFieldParameters()) {
            List<Settings.FieldParameter> parameters = mySettings.getParameterOrderList();

            for (Settings.FieldParameter fieldParameter : parameters) {
                PsiReferenceExpression fieldRef;
                String fieldName = fieldParameter.field.getName();
                if (newQualifier != null) {
                    fieldRef = (PsiReferenceExpression) factory.createExpressionFromText(
                        "a." + fieldName, null);
                    fieldRef.getQualifierExpression().replace(instanceRef);
                }
                else {
                    fieldRef = (PsiReferenceExpression) factory.createExpressionFromText(fieldName, null);
                }

                if (anchor != null) {
                    anchor = argList.addAfter(fieldRef, anchor);
                }
                else {
                    if (exprs.length > 0) {
                        anchor = argList.addBefore(fieldRef, exprs[0]);
                    }
                    else {
                        anchor = argList.add(fieldRef);
                    }
                }
            }
        }

        if (newQualifier != null) {
            if (call instanceof PsiMethodCallExpression) {
                instanceRef.replace(newQualifier);
            }
            else {
                PsiAnonymousClass anonymousClass = ((PsiNewExpression) call).getAnonymousClass();
                if (anonymousClass != null) {
                    ((PsiNewExpression) call).getQualifier().delete();
                    PsiJavaCodeReferenceElement baseClassReference = anonymousClass.getBaseClassReference();
                    baseClassReference.replace(((PsiNewExpression) factory.createExpressionFromText(
                        "new " + newQualifier.getText() + "." + baseClassReference.getText() + "()",
                        baseClassReference
                    )).getClassReference());
                }
                else {
                    PsiJavaCodeReferenceElement classReference = ((PsiNewExpression) call).getClassReference();
                    LOG.assertTrue(classReference != null);
                    PsiNewExpression newExpr =
                        (PsiNewExpression) factory.createExpressionFromText(
                            "new " + newQualifier.getText() + "." + classReference.getText() + "()",
                            classReference
                        );
                    PsiExpressionList callArgs = call.getArgumentList();
                    if (callArgs != null) {
                        PsiExpressionList argumentList = newExpr.getArgumentList();
                        LOG.assertTrue(argumentList != null);
                        argumentList.replace(callArgs);
                    }
                    call.replace(newExpr);
                }
            }
        }
    }

    @Override
    @RequiredReadAction
    protected MultiMap<PsiElement, LocalizeValue> getConflictDescriptions(UsageInfo[] usages) {
        MultiMap<PsiElement, LocalizeValue> conflicts = super.getConflictDescriptions(usages);

        //Check fields already exist
        if (mySettings.isMakeClassParameter()) {
            String fieldName = convertToFieldName(mySettings.getClassParameterName());
            PsiField existing = myMember.findFieldByName(fieldName, false);
            if (existing != null) {
                LocalizeValue message = RefactoringLocalize.thereIsAlreadyA0In1(
                    RefactoringUIUtil.getDescription(existing, false),
                    RefactoringUIUtil.getDescription(myMember, false)
                );
                conflicts.putValue(existing, message);
            }
        }

        if (mySettings.isMakeFieldParameters()) {
            List<Settings.FieldParameter> parameterOrderList = mySettings.getParameterOrderList();
            for (Settings.FieldParameter parameter : parameterOrderList) {
                String fieldName = convertToFieldName(parameter.name);
                PsiField existing = myMember.findFieldByName(fieldName, false);

                if (existing != null) {
                    LocalizeValue message = RefactoringLocalize.thereIsAlreadyA0In1(
                        RefactoringUIUtil.getDescription(existing, false),
                        RefactoringUIUtil.getDescription(myMember, false)
                    );
                    conflicts.putValue(existing, message);
                }
            }
        }

        return conflicts;
    }

    @Override
    @RequiredReadAction
    protected void findExternalUsages(List<UsageInfo> result) {
        PsiMethod[] constructors = myMember.getConstructors();
        if (constructors.length > 0) {
            for (PsiMethod constructor : constructors) {
                findExternalReferences(constructor, result);
            }
        }
        else {
            findDefaultConstructorReferences(result);
        }
    }

    private void findDefaultConstructorReferences(List<UsageInfo> result) {
        for (PsiReference ref : ReferencesSearch.search(myMember)) {
            PsiElement element = ref.getElement();
            if (element.getParent() instanceof PsiNewExpression) {
                PsiNewExpression newExpression = (PsiNewExpression) element.getParent();
                PsiElement qualifier = newExpression.getQualifier();
                if (qualifier instanceof PsiThisExpression) {
                    qualifier = null;
                }
                if (!PsiTreeUtil.isAncestor(myMember, element, true) || qualifier != null) {
                    result.add(new UsageInfo(element));
                }
                else {
                    result.add(new InternalUsageInfo(element, myMember));
                }
            }
        }
    }
}
