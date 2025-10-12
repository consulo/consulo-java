/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInsight.daemon.impl.quickfix;

import com.intellij.java.impl.codeInsight.ExpectedTypeInfo;
import com.intellij.java.impl.codeInsight.ExpectedTypesProvider;
import com.intellij.java.language.psi.*;
import consulo.application.ApplicationManager;
import consulo.application.Result;
import consulo.codeEditor.Editor;
import consulo.document.RangeMarker;
import consulo.java.analysis.impl.localize.JavaQuickFixLocalize;
import consulo.language.editor.CodeInsightUtilCore;
import consulo.language.editor.WriteCommandAction;
import consulo.language.editor.template.Template;
import consulo.language.editor.template.TemplateBuilder;
import consulo.language.editor.template.TemplateBuilderFactory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.SmartPointerManager;
import consulo.language.psi.SmartPsiElementPointer;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.undoRedo.CommandProcessor;
import jakarta.annotation.Nullable;

/**
 * @author mike
 */
public class CreateClassFromNewFix extends CreateFromUsageBaseFix {
    private final SmartPsiElementPointer myNewExpression;

    public CreateClassFromNewFix(PsiNewExpression newExpression) {
        myNewExpression = SmartPointerManager.getInstance(newExpression.getProject()).createSmartPsiElementPointer(newExpression);
    }

    protected PsiNewExpression getNewExpression() {
        return (PsiNewExpression) myNewExpression.getElement();
    }

    @Override
    protected void invokeImpl(PsiClass targetClass) {
        assert ApplicationManager.getApplication().isWriteAccessAllowed();

        final PsiNewExpression newExpression = getNewExpression();

        final PsiJavaCodeReferenceElement referenceElement = getReferenceElement(newExpression);
        ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
                final PsiClass[] psiClass = new PsiClass[1];
                CommandProcessor.getInstance().executeCommand(newExpression.getProject(), new Runnable() {
                    @Override
                    public void run() {
                        psiClass[0] = CreateFromUsageUtils.createClass(referenceElement, CreateClassKind.CLASS, null);
                    }
                }, getText(), getText());
                new WriteCommandAction(newExpression.getProject(), getText(), getText()) {
                    @Override
                    protected void run(Result result) throws Throwable {
                        setupClassFromNewExpression(psiClass[0], newExpression);
                    }
                }.execute();
            }
        });
    }

    protected void setupClassFromNewExpression(final PsiClass psiClass, final PsiNewExpression newExpression) {
        assert ApplicationManager.getApplication().isWriteAccessAllowed();

        final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(newExpression.getProject()).getElementFactory();
        PsiClass aClass = psiClass;
        if (aClass == null) {
            return;
        }

        final PsiJavaCodeReferenceElement classReference = newExpression.getClassReference();
        if (classReference != null) {
            classReference.bindToElement(aClass);
        }
        setupInheritance(newExpression, aClass);

        PsiExpressionList argList = newExpression.getArgumentList();
        final Project project = aClass.getProject();
        if (argList != null && argList.getExpressions().length > 0) {
            PsiMethod constructor = elementFactory.createConstructor();
            constructor = (PsiMethod) aClass.add(constructor);

            TemplateBuilder templateBuilder = TemplateBuilderFactory.getInstance().createTemplateBuilder(aClass);
            CreateFromUsageUtils.setupMethodParameters(constructor, templateBuilder, argList, getTargetSubstitutor(newExpression));

            setupSuperCall(aClass, constructor, templateBuilder);

            getReferenceElement(newExpression).bindToElement(aClass);
            aClass = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(aClass);
            final Template template = templateBuilder.buildTemplate();
            template.setToReformat(true);

            final Editor editor = positionCursor(project, aClass.getContainingFile(), aClass);
            if (editor == null) {
                return;
            }
            final RangeMarker textRange = editor.getDocument().createRangeMarker(aClass.getTextRange());
            final Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    new WriteCommandAction(project, getText(), getText()) {
                        @Override
                        protected void run(Result result) throws Throwable {
                            try {
                                editor.getDocument().deleteString(textRange.getStartOffset(), textRange.getEndOffset());
                            }
                            finally {
                                textRange.dispose();
                            }
                        }
                    }.execute();
                    startTemplate(editor, template, project, null, getText());
                }
            };
            if (ApplicationManager.getApplication().isUnitTestMode()) {
                runnable.run();
            }
            else {
                ApplicationManager.getApplication().invokeLater(runnable);
            }
        }
        else {
            positionCursor(project, aClass.getContainingFile(), aClass);
        }
    }

    @Nullable
    public static PsiMethod setupSuperCall(PsiClass targetClass, PsiMethod constructor, TemplateBuilder templateBuilder)
        throws IncorrectOperationException {
        PsiElementFactory elementFactory = JavaPsiFacade.getInstance(targetClass.getProject()).getElementFactory();
        PsiMethod supConstructor = null;
        PsiClass superClass = targetClass.getSuperClass();
        if (superClass != null && !CommonClassNames.JAVA_LANG_OBJECT.equals(superClass.getQualifiedName()) &&
            !CommonClassNames.JAVA_LANG_ENUM.equals(superClass.getQualifiedName())) {
            PsiMethod[] constructors = superClass.getConstructors();
            boolean hasDefaultConstructor = false;

            for (PsiMethod superConstructor : constructors) {
                if (superConstructor.getParameterList().getParametersCount() == 0) {
                    hasDefaultConstructor = true;
                    supConstructor = null;
                    break;
                }
                else {
                    supConstructor = superConstructor;
                }
            }

            if (!hasDefaultConstructor) {
                PsiExpressionStatement statement =
                    (PsiExpressionStatement) elementFactory.createStatementFromText("super();", constructor);
                statement = (PsiExpressionStatement) constructor.getBody().add(statement);

                PsiMethodCallExpression call = (PsiMethodCallExpression) statement.getExpression();
                PsiExpressionList argumentList = call.getArgumentList();
                templateBuilder.setEndVariableAfter(argumentList.getFirstChild());
                return supConstructor;
            }
        }

        templateBuilder.setEndVariableAfter(constructor.getBody().getLBrace());
        return supConstructor;
    }

    private static void setupInheritance(PsiNewExpression element, PsiClass targetClass) throws IncorrectOperationException {
        if (element.getParent() instanceof PsiReferenceExpression) {
            return;
        }

        ExpectedTypeInfo[] expectedTypes = ExpectedTypesProvider.getExpectedTypes(element, false);

        for (ExpectedTypeInfo expectedType : expectedTypes) {
            PsiType type = expectedType.getType();
            if (!(type instanceof PsiClassType)) {
                continue;
            }
            final PsiClassType classType = (PsiClassType) type;
            PsiClass aClass = classType.resolve();
            if (aClass == null) {
                continue;
            }
            if (aClass.equals(targetClass) || aClass.hasModifierProperty(PsiModifier.FINAL)) {
                continue;
            }
            PsiElementFactory factory = JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory();

            if (aClass.isInterface()) {
                PsiReferenceList implementsList = targetClass.getImplementsList();
                assert implementsList != null : targetClass;
                implementsList.add(factory.createReferenceElementByType(classType));
            }
            else {
                PsiReferenceList extendsList = targetClass.getExtendsList();
                assert extendsList != null : targetClass;
                if (extendsList.getReferencedTypes().length == 0 && !CommonClassNames.JAVA_LANG_OBJECT.equals(classType.getCanonicalText())) {
                    extendsList.add(factory.createReferenceElementByType(classType));
                }
            }
        }
    }

    private static PsiFile getTargetFile(PsiElement element) {
        PsiJavaCodeReferenceElement referenceElement = getReferenceElement((PsiNewExpression) element);

        PsiElement q = referenceElement.getQualifier();
        if (q instanceof PsiJavaCodeReferenceElement) {
            PsiJavaCodeReferenceElement qualifier = (PsiJavaCodeReferenceElement) q;
            PsiElement psiElement = qualifier.resolve();
            if (psiElement instanceof PsiClass) {
                PsiClass psiClass = (PsiClass) psiElement;
                return psiClass.getContainingFile();
            }
        }

        return null;
    }

    @Override
    protected PsiElement getElement() {
        final PsiNewExpression expression = getNewExpression();
        if (expression == null || !expression.getManager().isInProject(expression)) {
            return null;
        }
        PsiJavaCodeReferenceElement referenceElement = getReferenceElement(expression);
        if (referenceElement == null) {
            return null;
        }
        if (referenceElement.getReferenceNameElement() instanceof PsiIdentifier) {
            return expression;
        }

        return null;
    }

    @Override
    protected boolean isAllowOuterTargetClass() {
        return false;
    }

    @Override
    protected boolean isValidElement(PsiElement element) {
        PsiJavaCodeReferenceElement ref = PsiTreeUtil.getChildOfType(element, PsiJavaCodeReferenceElement.class);
        return ref != null && ref.resolve() != null;
    }

    @Override
    protected boolean isAvailableImpl(int offset) {
        PsiNewExpression expression = getNewExpression();
        if (expression.getQualifier() != null) {
            return false;
        }

        PsiFile targetFile = getTargetFile(expression);
        if (targetFile != null && !targetFile.getManager().isInProject(targetFile)) {
            return false;
        }

        PsiElement nameElement = getNameElement(expression);
        if (CreateFromUsageUtils.shouldShowTag(offset, nameElement, expression)) {
            String varName = nameElement.getText();
            setText(getText(varName));
            return true;
        }

        return false;
    }

    protected LocalizeValue getText(final String varName) {
        return JavaQuickFixLocalize.createClassFromNewText(varName);
    }

    protected static PsiJavaCodeReferenceElement getReferenceElement(PsiNewExpression expression) {
        return expression.getClassOrAnonymousClassReference();
    }

    private static PsiElement getNameElement(PsiNewExpression targetElement) {
        PsiJavaCodeReferenceElement referenceElement = getReferenceElement(targetElement);
        return referenceElement != null ? referenceElement.getReferenceNameElement() : null;
    }

    @Override
    protected boolean canBeTargetClass(PsiClass psiClass) {
        return false;
    }
}
