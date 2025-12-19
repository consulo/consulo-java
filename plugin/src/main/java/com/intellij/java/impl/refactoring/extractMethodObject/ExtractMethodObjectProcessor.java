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

/*
 * User: anna
 * Date: 06-May-2008
 */
package com.intellij.java.impl.refactoring.extractMethodObject;

import com.intellij.java.analysis.impl.refactoring.util.duplicates.Match;
import com.intellij.java.impl.codeInsight.generation.GenerateMembersUtil;
import com.intellij.java.impl.refactoring.HelpID;
import com.intellij.java.impl.refactoring.extractMethod.AbstractExtractDialog;
import com.intellij.java.impl.refactoring.extractMethod.ExtractMethodProcessor;
import com.intellij.java.impl.refactoring.ui.MemberSelectionPanel;
import com.intellij.java.impl.refactoring.util.RefactoringUtil;
import com.intellij.java.impl.refactoring.util.classMembers.MemberInfo;
import com.intellij.java.language.codeInsight.NullableNotNullManager;
import com.intellij.java.language.impl.psi.controlFlow.ControlFlowUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.codeStyle.VariableKind;
import com.intellij.java.language.psi.util.PropertyUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.util.VisibilityUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.codeEditor.Editor;
import consulo.content.scope.SearchScope;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.codeStyle.CodeStyleSettingsManager;
import consulo.language.editor.refactoring.BaseRefactoringProcessor;
import consulo.language.editor.refactoring.classMember.MemberInfoBase;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.PsiUtilCore;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.usage.UsageInfo;
import consulo.usage.UsageViewDescriptor;
import consulo.usage.UsageViewUtil;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.Comparing;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;

import javax.swing.*;
import java.util.*;

public class ExtractMethodObjectProcessor extends BaseRefactoringProcessor {
    private static final Logger LOG = Logger.getInstance(ExtractMethodObjectProcessor.class);
    public static final LocalizeValue REFACTORING_NAME = LocalizeValue.localizeTODO("Extract Method Object");

    private final PsiElementFactory myElementFactory;

    protected final MyExtractMethodProcessor myExtractProcessor;
    private boolean myCreateInnerClass = true;
    private String myInnerClassName;

    private boolean myMultipleExitPoints;
    private PsiField[] myOutputFields;

    private PsiMethod myInnerMethod;
    private boolean myMadeStatic = false;
    private final Set<MethodToMoveUsageInfo> myUsages = new LinkedHashSet<>();
    private PsiClass myInnerClass;
    private boolean myChangeReturnType;
    private Runnable myCopyMethodToInner;

    @RequiredReadAction
    public ExtractMethodObjectProcessor(Project project, Editor editor, PsiElement[] elements, String innerClassName) {
        super(project);
        myInnerClassName = innerClassName;
        myExtractProcessor =
            new MyExtractMethodProcessor(project, editor, elements, null, REFACTORING_NAME, innerClassName, HelpID.EXTRACT_METHOD_OBJECT);
        myElementFactory = JavaPsiFacade.getInstance(project).getElementFactory();
    }

    @Nonnull
    @Override
    protected UsageViewDescriptor createUsageViewDescriptor(@Nonnull UsageInfo[] usages) {
        return new ExtractMethodObjectViewDescriptor(getMethod());
    }

    @Nonnull
    @Override
    @RequiredReadAction
    protected UsageInfo[] findUsages() {
        List<UsageInfo> result = new ArrayList<>();
        PsiClass containingClass = getMethod().getContainingClass();
        SearchScope scope = PsiUtilCore.getVirtualFile(containingClass) == null
            ? new LocalSearchScope(containingClass)
            : GlobalSearchScope.projectScope(myProject);
        PsiReference[] refs = ReferencesSearch.search(getMethod(), scope, false).toArray(PsiReference.EMPTY_ARRAY);
        for (PsiReference ref : refs) {
            PsiElement element = ref.getElement();
            if (element != null && element.isValid()) {
                result.add(new UsageInfo(element));
            }
        }
        if (isCreateInnerClass()) {
            final Set<PsiMethod> usedMethods = new LinkedHashSet<>();
            getMethod().accept(new JavaRecursiveElementWalkingVisitor() {
                @Override
                public void visitMethodCallExpression(@Nonnull PsiMethodCallExpression expression) {
                    super.visitMethodCallExpression(expression);
                    PsiMethod method = expression.resolveMethod();
                    if (method != null) {
                        usedMethods.add(method);
                    }
                }
            });

            for (PsiMethod usedMethod : usedMethods) {
                if (usedMethod.getModifierList().hasModifierProperty(PsiModifier.PRIVATE)) {
                    PsiMethod toMove = usedMethod;
                    for (PsiReference reference : ReferencesSearch.search(usedMethod)) {
                        if (!PsiTreeUtil.isAncestor(getMethod(), reference.getElement(), false)) {
                            toMove = null;
                            break;
                        }
                    }
                    if (toMove != null) {
                        myUsages.add(new MethodToMoveUsageInfo(toMove));
                    }
                }
            }
        }
        UsageInfo[] usageInfos = result.toArray(new UsageInfo[result.size()]);
        return UsageViewUtil.removeDuplicatedUsages(usageInfos);
    }

    @Override
    @RequiredWriteAction
    public void performRefactoring(@Nonnull UsageInfo[] usages) {
        try {
            if (isCreateInnerClass()) {
                myInnerClass = (PsiClass) getMethod().getContainingClass().add(myElementFactory.createClass(getInnerClassName()));
                boolean isStatic = copyMethodModifiers() && notHasGeneratedFields();
                for (UsageInfo usage : usages) {
                    PsiMethodCallExpression methodCallExpression =
                        PsiTreeUtil.getParentOfType(usage.getElement(), PsiMethodCallExpression.class);
                    if (methodCallExpression != null) {
                        replaceMethodCallExpression(inferTypeArguments(methodCallExpression), methodCallExpression);
                    }
                }

                if (myExtractProcessor.generatesConditionalExit()) {
                    myInnerClass.add(myElementFactory.createField("myResult", PsiPrimitiveType.BOOLEAN));
                    myInnerClass.add(myElementFactory.createMethodFromText("boolean is(){return myResult;}", myInnerClass));
                }

                PsiParameter[] parameters = getMethod().getParameterList().getParameters();
                if (parameters.length > 0) {
                    createInnerClassConstructor(parameters);
                }
                else if (isStatic) {
                    PsiMethod copy = (PsiMethod) getMethod().copy();
                    copy.setName("invoke");
                    myInnerClass.add(copy);
                    if (myMultipleExitPoints) {
                        addOutputVariableFieldsWithGetters();
                    }
                    return;
                }
                if (myMultipleExitPoints) {
                    addOutputVariableFieldsWithGetters();
                }
                myCopyMethodToInner = () -> {
                    copyMethodWithoutParameters();
                    copyMethodTypeParameters();
                };
            }
            else {
                for (UsageInfo usage : usages) {
                    PsiMethodCallExpression methodCallExpression =
                        PsiTreeUtil.getParentOfType(usage.getElement(), PsiMethodCallExpression.class);
                    if (methodCallExpression != null) {
                        methodCallExpression.replace(processMethodDeclaration(methodCallExpression.getArgumentList()));
                    }
                }
            }
        }
        catch (IncorrectOperationException e) {
            LOG.error(e);
        }
    }

    @RequiredWriteAction
    void moveUsedMethodsToInner() {
        if (!myUsages.isEmpty()) {
            if (myProject.getApplication().isUnitTestMode()) {
                for (MethodToMoveUsageInfo usage : myUsages) {
                    PsiMember member = (PsiMember) usage.getElement();
                    LOG.assertTrue(member != null);
                    myInnerClass.add(member.copy());
                    member.delete();
                }
                return;
            }
            List<MemberInfo> memberInfos = new ArrayList<>();
            for (MethodToMoveUsageInfo usage : myUsages) {
                memberInfos.add(new MemberInfo((PsiMethod) usage.getElement()));
            }

            final MemberSelectionPanel panel = new MemberSelectionPanel("&Methods to move to the extracted class", memberInfos, null);
            DialogWrapper dlg = new DialogWrapper(myProject, false) {
                {
                    init();
                    setTitle("Move Methods Used in Extracted Block Only");
                }


                @Override
                protected JComponent createCenterPanel() {
                    return panel;
                }
            };
            if (dlg.showAndGet()) {
                myProject.getApplication().runWriteAction(() -> {
                    for (MemberInfoBase<PsiMember> memberInfo : panel.getTable().getSelectedMemberInfos()) {
                        if (memberInfo.isChecked()) {
                            myInnerClass.add(memberInfo.getMember().copy());
                            memberInfo.getMember().delete();
                        }
                    }
                });
            }
        }
    }

    @RequiredWriteAction
    private void addOutputVariableFieldsWithGetters() throws IncorrectOperationException {
        Map<String, String> var2FieldNames = new HashMap<>();
        final PsiVariable[] outputVariables = myExtractProcessor.getOutputVariables();
        for (int i = 0; i < outputVariables.length; i++) {
            PsiVariable var = outputVariables[i];
            PsiField outputField = myOutputFields[i];
            String name = getPureName(var);
            LOG.assertTrue(name != null);
            PsiField field;
            if (outputField != null) {
                var2FieldNames.put(var.getName(), outputField.getName());
                myInnerClass.add(outputField);
                field = outputField;
            }
            else {
                field = PropertyUtil.findPropertyField(myInnerClass, name, false);
            }
            LOG.assertTrue(
                field != null,
                "i:" + i + "; output variables: " + Arrays.toString(outputVariables) +
                    "; parameters: " + Arrays.toString(getMethod().getParameterList().getParameters()) +
                    "; output field: " + outputField
            );
            myInnerClass.add(GenerateMembersUtil.generateGetterPrototype(field));
        }

        PsiCodeBlock body = getMethod().getBody();
        LOG.assertTrue(body != null);
        final LinkedHashSet<PsiLocalVariable> vars = new LinkedHashSet<>();
        final Map<PsiElement, PsiElement> replacementMap = new LinkedHashMap<>();
        final List<PsiReturnStatement> returnStatements = new ArrayList<>();
        body.accept(new JavaRecursiveElementWalkingVisitor() {
            @Override
            public void visitReturnStatement(@Nonnull PsiReturnStatement statement) {
                returnStatements.add(statement);
            }

            @Override
            public void visitClass(@Nonnull PsiClass aClass) {
            }

            @Override
            public void visitLambdaExpression(@Nonnull PsiLambdaExpression expression) {
            }
        });
        if (myExtractProcessor.generatesConditionalExit()) {
            for (int i = 0; i < returnStatements.size() - 1; i++) {
                PsiReturnStatement condition = returnStatements.get(i);
                PsiElement container = condition.getParent();
                PsiStatement resultStmt = myElementFactory.createStatementFromText("myResult = true;", container);
                if (!RefactoringUtil.isLoopOrIf(container)) {
                    container.addBefore(resultStmt, condition);
                }
                else {
                    RefactoringUtil.putStatementInLoopBody(resultStmt, container, condition);
                }
            }

            LOG.assertTrue(!returnStatements.isEmpty());
            PsiReturnStatement returnStatement = returnStatements.get(returnStatements.size() - 1);
            PsiElement container = returnStatement.getParent();
            PsiStatement resultStmt = myElementFactory.createStatementFromText("myResult = false;", container);
            if (!RefactoringUtil.isLoopOrIf(container)) {
                container.addBefore(resultStmt, returnStatement);
            }
            else {
                RefactoringUtil.putStatementInLoopBody(resultStmt, container, returnStatement);
            }
        }
        body.accept(new JavaRecursiveElementWalkingVisitor() {
            @Override
            public void visitReturnStatement(@Nonnull PsiReturnStatement statement) {
                super.visitReturnStatement(statement);
                try {
                    replacementMap.put(statement, myElementFactory.createStatementFromText("return this;", statement));
                }
                catch (IncorrectOperationException e) {
                    LOG.error(e);
                }
            }

            @Override
            public void visitClass(@Nonnull PsiClass aClass) {
            }

            @Override
            public void visitLambdaExpression(@Nonnull PsiLambdaExpression expression) {
            }

            @Override
            public void visitDeclarationStatement(@Nonnull PsiDeclarationStatement statement) {
                super.visitDeclarationStatement(statement);
                PsiElement[] declaredElements = statement.getDeclaredElements();//todo
                for (PsiElement declaredElement : declaredElements) {
                    if (declaredElement instanceof PsiVariable var) {
                        for (PsiVariable variable : outputVariables) {
                            if (Comparing.strEqual(var.getName(), variable.getName())) {
                                PsiExpression initializer = var.getInitializer();
                                if (initializer == null) {
                                    replacementMap.put(statement, null);
                                }
                                else {
                                    replacementMap.put(var, var);
                                }
                            }
                        }
                    }
                }
            }

            @Override
            @RequiredReadAction
            public void visitReferenceExpression(PsiReferenceExpression expression) {
                super.visitReferenceExpression(expression);
                if (expression.resolve() instanceof PsiLocalVariable localVar) {
                    String localVarName = localVar.getName();
                    for (PsiVariable variable : outputVariables) {
                        if (Objects.equals(variable.getName(), localVarName)) {
                            vars.add(localVar);
                            break;
                        }
                    }
                }
            }
        });

        for (PsiLocalVariable var : vars) {
            String fieldName = var2FieldNames.get(var.getName());
            for (PsiReference reference : ReferencesSearch.search(var)) {
                reference.handleElementRename(fieldName);
            }
        }

        for (PsiElement statement : replacementMap.keySet()) {
            PsiElement replacement = replacementMap.get(statement);
            if (replacement != null) {
                if (statement instanceof PsiLocalVariable variable) {
                    variable.normalizeDeclaration();
                    PsiExpression initializer = variable.getInitializer();
                    LOG.assertTrue(initializer != null);
                    PsiStatement assignmentStatement = myElementFactory.createStatementFromText(
                        var2FieldNames.get(variable.getName()) + " = " + initializer.getText() + ";",
                        statement
                    );
                    PsiDeclarationStatement declaration = PsiTreeUtil.getParentOfType(statement, PsiDeclarationStatement.class);
                    LOG.assertTrue(declaration != null);
                    declaration.replace(assignmentStatement);
                }
                else {
                    if (statement instanceof PsiReturnStatement returnStmt) {
                        PsiExpression returnValue = returnStmt.getReturnValue();
                        if (!(returnValue instanceof PsiReferenceExpression
                            || returnValue == null
                            || returnValue instanceof PsiLiteralExpression)) {
                            returnStmt.getParent()
                                .addBefore(myElementFactory.createStatementFromText(returnValue.getText() + ";", returnValue), statement);
                        }
                    }
                    statement.replace(replacement);
                }
            }
            else {
                statement.delete();
            }
        }

        myChangeReturnType = true;
    }

    @RequiredWriteAction
    void runChangeSignature() {
        if (myCopyMethodToInner != null) {
            myCopyMethodToInner.run();
        }
        if (myChangeReturnType) {
            PsiTypeElement typeElement =
                ((PsiLocalVariable) ((PsiDeclarationStatement) JavaPsiFacade.getElementFactory(myProject).createStatementFromText(
                    myInnerClassName + " l =null;",
                    myInnerClass
                )).getDeclaredElements()[0]).getTypeElement();
            PsiTypeElement innerMethodReturnTypeElement = myInnerMethod.getReturnTypeElement();
            LOG.assertTrue(innerMethodReturnTypeElement != null);
            innerMethodReturnTypeElement.replace(typeElement);
        }
    }

    private String getPureName(PsiVariable var) {
        JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(myProject);
        return styleManager.variableNameToPropertyName(var.getName(), styleManager.getVariableKind(var));
    }

    @RequiredWriteAction
    public PsiExpression processMethodDeclaration(PsiExpressionList expressionList) throws IncorrectOperationException {
        if (isCreateInnerClass()) {
            String typeArguments =
                getMethod().hasTypeParameters() ? "<" + StringUtil.join(
                    Arrays.asList(getMethod().getTypeParameters()),
                    typeParameter -> {
                        String typeParameterName = typeParameter.getName();
                        LOG.assertTrue(typeParameterName != null);
                        return typeParameterName;
                    },
                    ", "
                ) + ">" : "";
            PsiMethodCallExpression methodCallExpression =
                (PsiMethodCallExpression) myElementFactory.createExpressionFromText("invoke" + expressionList.getText(), null);
            return replaceMethodCallExpression(typeArguments, methodCallExpression);
        }
        else {
            String paramsDeclaration = getMethod().getParameterList().getText();
            PsiType returnType = getMethod().getReturnType();
            LOG.assertTrue(returnType != null);

            PsiCodeBlock methodBody = getMethod().getBody();
            LOG.assertTrue(methodBody != null);
            return myElementFactory.createExpressionFromText(
                "new Object(){ \n" +
                    "private " +
                    returnType.getPresentableText() +
                    " " + myInnerClassName +
                    paramsDeclaration +
                    methodBody.getText() +
                    "}." + myInnerClassName +
                    expressionList.getText(),
                null
            );
        }
    }

    @RequiredWriteAction
    private PsiMethodCallExpression replaceMethodCallExpression(
        String inferredTypeArguments,
        PsiMethodCallExpression methodCallExpression
    ) throws IncorrectOperationException {
        String staticQualifier = getMethod().isStatic() && notHasGeneratedFields() ? getInnerClassName() : null;
        String newReplacement;
        PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
        PsiExpressionList argumentList = methodCallExpression.getArgumentList();
        if (staticQualifier != null) {
            newReplacement = argumentList.getExpressions().length > 0
                ? "new " + staticQualifier + inferredTypeArguments + argumentList.getText() + "."
                : staticQualifier + ".";
        }
        else {
            PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
            String qualifier = qualifierExpression != null ? qualifierExpression.getText() + "." : "";
            newReplacement = qualifier + "new " + getInnerClassName() + inferredTypeArguments + argumentList.getText() + ".";
        }
        return (PsiMethodCallExpression) methodCallExpression.replace(myElementFactory.createExpressionFromText(
            newReplacement + "invoke()",
            null
        ));
    }

    @Nonnull
    @RequiredReadAction
    private String inferTypeArguments(PsiMethodCallExpression methodCallExpression) {
        PsiReferenceParameterList list = methodCallExpression.getMethodExpression().getParameterList();

        if (list != null && list.getTypeArguments().length > 0) {
            return list.getText();
        }
        PsiTypeParameter[] methodTypeParameters = getMethod().getTypeParameters();
        if (methodTypeParameters.length > 0) {
            List<String> typeSignature = new ArrayList<>();
            PsiSubstitutor substitutor = methodCallExpression.resolveMethodGenerics().getSubstitutor();
            for (PsiTypeParameter typeParameter : methodTypeParameters) {
                PsiType type = substitutor.substitute(typeParameter);
                if (type == null || PsiType.NULL.equals(type)) {
                    return "";
                }
                typeSignature.add(type.getPresentableText());
            }
            return "<" + StringUtil.join(typeSignature, ", ") + ">";

        }
        return "";
    }

    @Nonnull
    @Override
    protected String getCommandName() {
        return REFACTORING_NAME.get();
    }

    @RequiredReadAction
    private boolean copyMethodModifiers() throws IncorrectOperationException {
        PsiModifierList methodModifierList = getMethod().getModifierList();

        PsiModifierList innerClassModifierList = myInnerClass.getModifierList();
        LOG.assertTrue(innerClassModifierList != null);
        innerClassModifierList.setModifierProperty(VisibilityUtil.getVisibilityModifier(methodModifierList), true);
        boolean isStatic = methodModifierList.hasModifierProperty(PsiModifier.STATIC);
        innerClassModifierList.setModifierProperty(PsiModifier.STATIC, isStatic);
        return isStatic;
    }

    private void copyMethodTypeParameters() throws IncorrectOperationException {
        PsiTypeParameterList typeParameterList = myInnerClass.getTypeParameterList();
        LOG.assertTrue(typeParameterList != null);

        for (PsiTypeParameter parameter : getMethod().getTypeParameters()) {
            typeParameterList.add(parameter);
        }
    }

    @RequiredWriteAction
    private void copyMethodWithoutParameters() throws IncorrectOperationException {
        PsiMethod newMethod = myElementFactory.createMethod("invoke", getMethod().getReturnType());
        newMethod.getThrowsList().replace(getMethod().getThrowsList());

        PsiCodeBlock replacedMethodBody = newMethod.getBody();
        LOG.assertTrue(replacedMethodBody != null);
        PsiCodeBlock methodBody = getMethod().getBody();
        LOG.assertTrue(methodBody != null);
        replacedMethodBody.replace(methodBody);
        PsiUtil.setModifierProperty(
            newMethod,
            PsiModifier.STATIC,
            myInnerClass.isStatic() && notHasGeneratedFields()
        );
        myInnerMethod = (PsiMethod) myInnerClass.add(newMethod);
    }

    private boolean notHasGeneratedFields() {
        return !myMultipleExitPoints && getMethod().getParameterList().getParametersCount() == 0;
    }

    @RequiredWriteAction
    private void createInnerClassConstructor(PsiParameter[] parameters) throws IncorrectOperationException {
        PsiMethod constructor = myElementFactory.createConstructor();
        PsiParameterList parameterList = constructor.getParameterList();
        for (PsiParameter parameter : parameters) {
            PsiModifierList parameterModifierList = parameter.getModifierList();
            LOG.assertTrue(parameterModifierList != null);
            PsiParameter param = myElementFactory.createParameter(parameter.getName(), parameter.getType());
            if (CodeStyleSettingsManager.getSettings(myProject).GENERATE_FINAL_PARAMETERS) {
                PsiModifierList modifierList = param.getModifierList();
                LOG.assertTrue(modifierList != null);
                modifierList.setModifierProperty(PsiModifier.FINAL, true);
            }
            parameterList.add(param);

            PsiField field = createField(param, constructor, parameterModifierList.hasModifierProperty(PsiModifier.FINAL));
            for (PsiReference reference : ReferencesSearch.search(parameter)) {
                reference.handleElementRename(field.getName());
            }
        }
        myInnerClass.add(constructor);
    }

    @RequiredWriteAction
    private PsiField createField(PsiParameter parameter, PsiMethod constructor, boolean isFinal) {
        String parameterName = parameter.getName();
        PsiType type = parameter.getType();
        if (type instanceof PsiEllipsisType ellipsisType) {
            type = ellipsisType.toArrayType();
        }
        try {
            JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(getMethod().getProject());
            String propertyName = styleManager.variableNameToPropertyName(parameterName, VariableKind.PARAMETER);
            String fieldName = styleManager.suggestVariableName(VariableKind.FIELD, propertyName, null, type).names[0];
            PsiField field = myElementFactory.createField(fieldName, type);

            PsiModifierList modifierList = field.getModifierList();
            LOG.assertTrue(modifierList != null);
            NullableNotNullManager manager = NullableNotNullManager.getInstance(myProject);
            if (manager.isNullable(parameter, false)) {
                modifierList.addAfter(myElementFactory.createAnnotationFromText("@" + manager.getDefaultNullable(), field), null);
            }
            modifierList.setModifierProperty(PsiModifier.FINAL, isFinal);

            PsiCodeBlock methodBody = constructor.getBody();

            LOG.assertTrue(methodBody != null);

            String stmtText;
            if (Comparing.strEqual(parameterName, fieldName)) {
                stmtText = "this." + fieldName + " = " + parameterName + ";";
            }
            else {
                stmtText = fieldName + " = " + parameterName + ";";
            }
            PsiStatement assignmentStmt = myElementFactory.createStatementFromText(stmtText, methodBody);
            assignmentStmt = (PsiStatement) CodeStyleManager.getInstance(constructor.getProject()).reformat(assignmentStmt);
            methodBody.add(assignmentStmt);

            field = (PsiField) myInnerClass.add(field);
            return field;
        }
        catch (IncorrectOperationException e) {
            LOG.error(e);
        }
        return null;
    }

    @RequiredWriteAction
    protected void changeInstanceAccess(Project project) throws IncorrectOperationException {
        if (myMadeStatic) {
            PsiReference[] refs =
                ReferencesSearch.search(myInnerMethod, GlobalSearchScope.projectScope(project), false).toArray(PsiReference.EMPTY_ARRAY);
            for (PsiReference ref : refs) {
                PsiElement element = ref.getElement();
                PsiMethodCallExpression callExpression = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class);
                if (callExpression != null) {
                    replaceMethodCallExpression(inferTypeArguments(callExpression), callExpression);
                }
            }
        }
    }

    public PsiMethod getMethod() {
        return myExtractProcessor.getExtractedMethod();
    }

    public String getInnerClassName() {
        return myInnerClassName;
    }

    public void setCreateInnerClass(boolean createInnerClass) {
        myCreateInnerClass = createInnerClass;
    }

    public boolean isCreateInnerClass() {
        return myCreateInnerClass;
    }

    public MyExtractMethodProcessor getExtractProcessor() {
        return myExtractProcessor;
    }

    protected AbstractExtractDialog createExtractMethodObjectDialog(final MyExtractMethodProcessor processor) {
        return new ExtractMethodObjectDialog(
            myProject,
            processor.getTargetClass(),
            processor.getInputVariables(),
            processor.getReturnType(),
            processor.getTypeParameterList(),
            processor.getThrownExceptions(),
            processor.isStatic(),
            processor.isCanBeStatic(),
            processor.getElements(),
            myMultipleExitPoints
        ) {
            @Override
            protected boolean isUsedAfter(PsiVariable variable) {
                return ArrayUtil.find(processor.getOutputVariables(), variable) != -1;
            }
        };
    }

    public PsiClass getInnerClass() {
        return myInnerClass;
    }

    protected boolean isFoldingApplicable() {
        return true;
    }

    public class MyExtractMethodProcessor extends ExtractMethodProcessor {
        @RequiredReadAction
        public MyExtractMethodProcessor(
            Project project,
            Editor editor,
            PsiElement[] elements,
            PsiType forcedReturnType,
            LocalizeValue refactoringName,
            String initialMethodName,
            String helpId
        ) {
            super(project, editor, elements, forcedReturnType, refactoringName, initialMethodName, helpId);
        }

        @Override
        protected boolean insertNotNullCheckIfPossible() {
            return false;
        }

        @Override
        protected boolean isNeedToChangeCallContext() {
            return false;
        }

        @Override
        protected void apply(AbstractExtractDialog dialog) {
            super.apply(dialog);
            myCreateInnerClass = !(dialog instanceof ExtractMethodObjectDialog) || ((ExtractMethodObjectDialog) dialog).createInnerClass();
            myInnerClassName = myCreateInnerClass ? StringUtil.capitalize(dialog.getChosenMethodName()) : dialog.getChosenMethodName();
        }

        @Override
        @RequiredReadAction
        protected AbstractExtractDialog createExtractMethodDialog(boolean direct) {
            return createExtractMethodObjectDialog(this);
        }

        @Override
        protected boolean checkOutputVariablesCount() {
            myMultipleExitPoints = super.checkOutputVariablesCount();
            myOutputFields = new PsiField[myOutputVariables.length];
            for (int i = 0; i < myOutputVariables.length; i++) {
                PsiVariable variable = myOutputVariables[i];
                if (!myInputVariables.contains(variable)) { //one field creation
                    JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(myProject);
                    String fieldName =
                        styleManager.suggestVariableName(VariableKind.FIELD, getPureName(variable), null, variable.getType()).names[0];
                    try {
                        myOutputFields[i] = myElementFactory.createField(fieldName, variable.getType());
                    }
                    catch (IncorrectOperationException e) {
                        LOG.error(e);
                    }
                }
            }
            return !myCreateInnerClass && myMultipleExitPoints;
        }

        @Override
        @RequiredWriteAction
        public PsiElement processMatch(Match match) throws IncorrectOperationException {
            boolean makeStatic = myInnerMethod != null
                && RefactoringUtil.isInStaticContext(match.getMatchStart(), getExtractedMethod().getContainingClass())
                && !myInnerMethod.getContainingClass().isStatic();
            PsiElement element = super.processMatch(match);
            if (makeStatic) {
                myMadeStatic = true;
                PsiModifierList modifierList = myInnerMethod.getContainingClass().getModifierList();
                LOG.assertTrue(modifierList != null);
                modifierList.setModifierProperty(PsiModifier.STATIC, true);
                PsiUtil.setModifierProperty(myInnerMethod, PsiModifier.STATIC, true);
            }
            PsiMethodCallExpression methodCallExpression = null;
            if (element instanceof PsiMethodCallExpression call) {
                methodCallExpression = call;
            }
            else if (element instanceof PsiExpressionStatement exprStmt) {
                PsiExpression expression = exprStmt.getExpression();
                if (expression instanceof PsiMethodCallExpression call) {
                    methodCallExpression = call;
                }
                else if (expression instanceof PsiAssignmentExpression assignment
                    && assignment.getRExpression() instanceof PsiMethodCallExpression call) {
                    methodCallExpression = call;
                }
            }
            else if (element instanceof PsiDeclarationStatement declaration) {
                PsiElement[] declaredElements = declaration.getDeclaredElements();
                for (PsiElement declaredElement : declaredElements) {
                    if (declaredElement instanceof PsiLocalVariable localVar
                        && localVar.getInitializer() instanceof PsiMethodCallExpression call) {
                        methodCallExpression = call;
                        break;
                    }
                }
            }
            if (methodCallExpression == null) {
                return element;
            }

            PsiExpression expression = processMethodDeclaration(methodCallExpression.getArgumentList());

            return methodCallExpression.replace(expression);
        }

        @Override
        public PsiVariable[] getOutputVariables() {
            return myOutputVariables;
        }

        @Override
        @RequiredWriteAction
        protected void declareNecessaryVariablesAfterCall(PsiVariable outputVariable) throws IncorrectOperationException {
            if (myMultipleExitPoints) {
                String object = JavaCodeStyleManager.getInstance(myProject)
                    .suggestUniqueVariableName(StringUtil.decapitalize(myInnerClassName), outputVariable, true);
                PsiStatement methodCallStatement = PsiTreeUtil.getParentOfType(getMethodCall(), PsiStatement.class);
                LOG.assertTrue(methodCallStatement != null);
                PsiStatement declarationStatement = myElementFactory.createStatementFromText(
                    myInnerClassName + " " + object + " = " + getMethodCall().getText() + ";",
                    myInnerMethod
                );
                if (methodCallStatement instanceof PsiIfStatement ifStatement) {
                    ifStatement.getParent().addBefore(declarationStatement, ifStatement);
                    setMethodCall(
                        (PsiMethodCallExpression) ifStatement.getCondition()
                            .replace(myElementFactory.createExpressionFromText(object + ".is()", myInnerMethod))
                    );
                }
                else if (myElements[0] instanceof PsiExpression) {
                    methodCallStatement.getParent().addBefore(declarationStatement, methodCallStatement);
                }
                else {
                    PsiDeclarationStatement replace = (PsiDeclarationStatement) methodCallStatement.replace(declarationStatement);
                    setMethodCall((PsiMethodCallExpression) ((PsiLocalVariable) replace.getDeclaredElements()[0]).getInitializer());
                }

                List<PsiVariable> usedVariables = myControlFlowWrapper.getUsedVariables();
                Collection<ControlFlowUtil.VariableInfo> reassigned = myControlFlowWrapper.getInitializedTwice();
                for (PsiVariable variable : usedVariables) {
                    String name = variable.getName();
                    LOG.assertTrue(name != null);
                    PsiStatement st = null;
                    String pureName = getPureName(variable);
                    int varIdxInOutput = ArrayUtil.find(myOutputVariables, variable);
                    String getterName = varIdxInOutput > -1 && myOutputFields[varIdxInOutput] != null
                        ? GenerateMembersUtil.suggestGetterName(myOutputFields[varIdxInOutput])
                        : GenerateMembersUtil.suggestGetterName(pureName, variable.getType(), myProject);
                    if (isDeclaredInside(variable)) {
                        st = myElementFactory.createStatementFromText(variable.getType()
                            .getCanonicalText() + " " + name + " = " + object + "." + getterName + "();", myInnerMethod);
                        if (reassigned.contains(new ControlFlowUtil.VariableInfo(variable, null))) {
                            PsiElement[] psiElements = ((PsiDeclarationStatement) st).getDeclaredElements();
                            assert psiElements.length > 0;
                            PsiVariable var = (PsiVariable) psiElements[0];
                            PsiUtil.setModifierProperty(var, PsiModifier.FINAL, false);
                        }
                    }
                    else if (varIdxInOutput != -1) {
                        st = myElementFactory.createStatementFromText(name + " = " + object + "." + getterName + "();", myInnerMethod);
                    }
                    if (st != null) {
                        addToMethodCallLocation(st);
                    }
                }
                if (myElements[0] instanceof PsiAssignmentExpression) {
                    PsiAssignmentExpression parentAssignment = (PsiAssignmentExpression) getMethodCall().getParent();
                    parentAssignment.replace(parentAssignment.getLExpression());
                }
                else if (myElements[0] instanceof PsiPostfixExpression || myElements[0] instanceof PsiPrefixExpression) {
                    PsiBinaryExpression parentBinary = (PsiBinaryExpression) getMethodCall().getParent();
                    parentBinary.replace(parentBinary.getLOperand());
                }

                rebindExitStatement(object);
            }
            else {
                super.declareNecessaryVariablesAfterCall(outputVariable);
            }
        }

        @Override
        protected boolean isFoldingApplicable() {
            return ExtractMethodObjectProcessor.this.isFoldingApplicable();
        }

        @RequiredWriteAction
        private void rebindExitStatement(final String objectName) {
            PsiStatement exitStatementCopy = myExtractProcessor.myFirstExitStatementCopy;
            if (exitStatementCopy != null) {
                myExtractProcessor.getDuplicates().clear();
                final Map<String, PsiVariable> outVarsNames = new HashMap<>();
                for (PsiVariable variable : myOutputVariables) {
                    outVarsNames.put(variable.getName(), variable);
                }
                final Map<PsiElement, PsiElement> replaceMap = new HashMap<>();
                exitStatementCopy.accept(new JavaRecursiveElementWalkingVisitor() {
                    @Override
                    public void visitReferenceExpression(PsiReferenceExpression expression) {
                        super.visitReferenceExpression(expression);
                        if (expression.resolve() == null) {
                            PsiVariable variable = outVarsNames.get(expression.getReferenceName());
                            if (variable != null) {
                                String call2Getter = objectName + "." + GenerateMembersUtil.suggestGetterName(
                                    getPureName(variable),
                                    variable.getType(),
                                    myProject
                                ) + "()";
                                PsiExpression callToGetter = myElementFactory.createExpressionFromText(call2Getter, variable);
                                replaceMap.put(expression, callToGetter);
                            }
                        }
                    }
                });
                for (PsiElement element : replaceMap.keySet()) {
                    if (element.isValid()) {
                        element.replace(replaceMap.get(element));
                    }
                }
            }
        }

        public boolean generatesConditionalExit() {
            return myGenerateConditionalExit;
        }
    }
}
