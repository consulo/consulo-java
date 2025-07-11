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
package com.intellij.java.impl.refactoring.replaceConstructorWithBuilder;

import com.intellij.java.impl.codeInsight.PackageUtil;
import com.intellij.java.impl.refactoring.MoveDestination;
import com.intellij.java.impl.refactoring.replaceConstructorWithBuilder.usageInfo.ReplaceConstructorWithSettersChainInfo;
import com.intellij.java.impl.refactoring.util.FixableUsageInfo;
import com.intellij.java.impl.refactoring.util.FixableUsagesRefactoringProcessor;
import com.intellij.java.impl.refactoring.util.RefactoringUtil;
import com.intellij.java.language.impl.JavaFileType;
import com.intellij.java.language.psi.PsiElementFactory;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.codeStyle.VariableKind;
import com.intellij.java.language.psi.util.PropertyUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import com.intellij.java.language.util.VisibilityUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.psi.*;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.module.Module;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.usage.UsageInfo;
import consulo.usage.UsageViewDescriptor;
import consulo.util.collection.MultiMap;
import consulo.util.lang.Comparing;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ref.SimpleReference;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author anna
 * @since 2008-09-04
 */
public class ReplaceConstructorWithBuilderProcessor extends FixableUsagesRefactoringProcessor {
    public static final String REFACTORING_NAME = "Replace Constructor with Builder";
    private final PsiMethod[] myConstructors;
    private final Map<String, ParameterData> myParametersMap;
    private final String myClassName;
    private final String myPackageName;
    private final boolean myCreateNewBuilderClass;
    private final PsiElementFactory myElementFactory;
    private MoveDestination myMoveDestination;


    public ReplaceConstructorWithBuilderProcessor(
        Project project,
        PsiMethod[] constructors,
        Map<String, ParameterData> parametersMap,
        String className,
        String packageName,
        MoveDestination moveDestination, boolean createNewBuilderClass
    ) {
        super(project);
        myMoveDestination = moveDestination;
        myElementFactory = JavaPsiFacade.getInstance(myProject).getElementFactory();
        myConstructors = constructors;
        myParametersMap = parametersMap;

        myClassName = className;
        myPackageName = packageName;
        myCreateNewBuilderClass = createNewBuilderClass;
    }

    @Nonnull
    @Override
    protected UsageViewDescriptor createUsageViewDescriptor(@Nonnull UsageInfo[] usages) {
        return new ReplaceConstructorWithBuilderViewDescriptor();
    }

    @Override
    @RequiredReadAction
    protected void findUsages(@Nonnull List<FixableUsageInfo> usages) {
        String builderQualifiedName = StringUtil.getQualifiedName(myPackageName, myClassName);
        PsiClass builderClass =
            JavaPsiFacade.getInstance(myProject).findClass(builderQualifiedName, GlobalSearchScope.projectScope(myProject));

        for (PsiMethod constructor : myConstructors) {
            for (PsiReference reference : ReferencesSearch.search(constructor)) {
                PsiElement element = reference.getElement();
                PsiNewExpression newExpression = PsiTreeUtil.getParentOfType(element, PsiNewExpression.class);
                if (newExpression != null && !PsiTreeUtil.isAncestor(builderClass, element, false)) {
                    usages.add(new ReplaceConstructorWithSettersChainInfo(
                        newExpression,
                        StringUtil.getQualifiedName(myPackageName, myClassName),
                        myParametersMap
                    ));
                }
            }
        }
    }

    @Nullable
    @RequiredReadAction
    private PsiClass createBuilderClass() {
        PsiClass psiClass = myConstructors[0].getContainingClass();
        assert psiClass != null;
        PsiTypeParameterList typeParameterList = psiClass.getTypeParameterList();
        String text = "public class " + myClassName + (typeParameterList != null ? typeParameterList.getText() : "") + "{}";
        PsiFileFactory factory = PsiFileFactory.getInstance(myProject);
        PsiJavaFile newFile = (PsiJavaFile)factory.createFileFromText(myClassName + ".java", JavaFileType.INSTANCE, text);

        PsiFile containingFile = myConstructors[0].getContainingFile();
        PsiDirectory containingDirectory = containingFile.getContainingDirectory();
        PsiDirectory directory;
        if (myMoveDestination != null) {
            directory = myMoveDestination.getTargetDirectory(containingDirectory);
        }
        else {
            Module module = containingFile.getModule();
            assert module != null;
            directory = PackageUtil.findOrCreateDirectoryForPackage(module, myPackageName, containingDirectory, true, true);
        }

        if (directory != null) {
            CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(PsiManager.getInstance(myProject).getProject());
            PsiJavaFile reformattedFile =
                (PsiJavaFile)codeStyleManager.reformat(JavaCodeStyleManager.getInstance(newFile.getProject())
                    .shortenClassReferences(newFile));

            if (directory.findFile(reformattedFile.getName()) != null) {
                return reformattedFile.getClasses()[0];
            }
            return ((PsiJavaFile)directory.add(reformattedFile)).getClasses()[0];
        }
        return null;
    }

    @Override
    @RequiredReadAction
    protected void performRefactoring(@Nonnull UsageInfo[] usageInfos) {
        JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(myProject);
        PsiClass builderClass = myCreateNewBuilderClass
            ? createBuilderClass()
            : psiFacade.findClass(
            StringUtil.getQualifiedName(myPackageName, myClassName),
            GlobalSearchScope.projectScope(myProject)
        );
        if (builderClass == null) {
            return;
        }

        for (String propertyName : myParametersMap.keySet()) {
            ParameterData parameterData = myParametersMap.get(propertyName);
            PsiField field = createField(builderClass, parameterData);
            createSetter(builderClass, parameterData, field);
        }

        super.performRefactoring(usageInfos);

        PsiMethod method = createMethodSignature(createMethodName());
        if (builderClass.findMethodBySignature(method, false) == null) {
            builderClass.add(method);
        }

        //fix visibilities
        PsiMethod constructor = getWorkingConstructor();
        VisibilityUtil.escalateVisibility(constructor, builderClass);
        PsiClass containingClass = constructor.getContainingClass();
        while (containingClass != null) {
            VisibilityUtil.escalateVisibility(containingClass, builderClass);
            containingClass = containingClass.getContainingClass();
        }
    }

    private void createSetter(PsiClass builderClass, ParameterData parameterData, PsiField field) {
        PsiMethod setter = null;
        for (PsiMethod method : builderClass.getMethods()) {
            if (Comparing.strEqual(method.getName(), parameterData.getSetterName()) && method.getParameterList().getParametersCount() == 1
                && TypeConversionUtil.isAssignable(method.getParameterList().getParameters()[0].getType(), parameterData.getType())) {
                setter = method;
                fixSetterReturnType(builderClass, field, setter);
                break;
            }
        }
        if (setter == null) {
            setter = PropertyUtil.generateSetterPrototype(field, builderClass, true);
            PsiIdentifier nameIdentifier = setter.getNameIdentifier();
            assert nameIdentifier != null;
            nameIdentifier.replace(myElementFactory.createIdentifier(parameterData.getSetterName()));
            setter.getParameterList().getParameters()[0].getTypeElement()
                .replace(myElementFactory.createTypeElement(parameterData.getType())); //setter varargs
            builderClass.add(setter);
        }
    }

    private PsiField createField(PsiClass builderClass, ParameterData parameterData) {
        PsiField field = builderClass.findFieldByName(parameterData.getFieldName(), false);

        if (field == null) {
            PsiType type = parameterData.getType();
            if (type instanceof PsiEllipsisType ellipsisType) {
                type = ellipsisType.toArrayType();
            }
            field = myElementFactory.createField(parameterData.getFieldName(), type);
            field = (PsiField)builderClass.add(field);
        }

        String defaultValue = parameterData.getDefaultValue();
        if (defaultValue != null) {
            PsiExpression initializer = field.getInitializer();
            if (initializer == null) {
                try {
                    field.setInitializer(myElementFactory.createExpressionFromText(defaultValue, field));
                }
                catch (IncorrectOperationException e) {
                    //skip invalid default value
                }
            }
        }
        return field;
    }

    private void fixSetterReturnType(PsiClass builderClass, PsiField field, PsiMethod method) {
        if (PsiUtil.resolveClassInType(method.getReturnType()) != builderClass) {
            PsiCodeBlock body = method.getBody();
            PsiCodeBlock generatedBody = PropertyUtil.generateSetterPrototype(field, builderClass, true).getBody();
            assert body != null;
            assert generatedBody != null;
            body.replace(generatedBody);
            PsiTypeElement typeElement = method.getReturnTypeElement();
            assert typeElement != null;
            typeElement.replace(myElementFactory.createTypeElement(myElementFactory.createType(builderClass)));
        }
    }

    private PsiMethod createMethodSignature(String createMethodName) {
        JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(myProject);
        StringBuilder buf = new StringBuilder();
        PsiMethod constructor = getWorkingConstructor();
        for (PsiParameter parameter : constructor.getParameterList().getParameters()) {
            String pureParamName = styleManager.variableNameToPropertyName(parameter.getName(), VariableKind.PARAMETER);
            if (buf.length() > 0) {
                buf.append(", ");
            }
            buf.append(myParametersMap.get(pureParamName).getFieldName());
        }
        return myElementFactory.createMethodFromText(
            "public " + constructor.getName() + " " + createMethodName + "(){\n" +
                " return new " + constructor.getName() + "(" + buf.toString() + ");\n" +
                "}",
            constructor
        );
    }

    private PsiMethod getWorkingConstructor() {
        PsiMethod constructor = getMostCommonConstructor();
        if (constructor == null) {
            constructor = myConstructors[0];
            if (constructor.getParameterList().getParametersCount() == 0) {
                constructor = myConstructors[1];
            }
        }
        return constructor;
    }

    @Nullable
    private PsiMethod getMostCommonConstructor() {
        if (myConstructors.length == 1) {
            return myConstructors[0];
        }
        PsiMethod commonConstructor = null;
        for (PsiMethod constructor : myConstructors) {
            PsiMethod chainedConstructor = RefactoringUtil.getChainedConstructor(constructor);
            if (chainedConstructor == null) {
                if (commonConstructor != null) {
                    if (!isChained(commonConstructor, constructor)) {
                        return null;
                    }
                }
                commonConstructor = constructor;
            }
            else if (commonConstructor == null) {
                commonConstructor = chainedConstructor;
            }
            else if (!isChained(commonConstructor, chainedConstructor)) {
                return null;
            }
        }
        return commonConstructor;
    }

    private static boolean isChained(PsiMethod first, PsiMethod last) {
        return first != null && (first == last || isChained(RefactoringUtil.getChainedConstructor(first), last));
    }

    private String createMethodName() {
        return "create" + StringUtil.capitalize(myConstructors[0].getName());
    }


    @Override
    @RequiredUIAccess
    protected boolean preprocessUsages(@Nonnull SimpleReference<UsageInfo[]> refUsages) {
        MultiMap<PsiElement, String> conflicts = new MultiMap<>();
        JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(myProject);
        PsiClass builderClass =
            psiFacade.findClass(StringUtil.getQualifiedName(myPackageName, myClassName), GlobalSearchScope.projectScope(myProject));
        if (builderClass == null) {
            if (!myCreateNewBuilderClass) {
                conflicts.putValue(null, "Selected class was not found.");
            }
        }
        else if (myCreateNewBuilderClass) {
            conflicts.putValue(builderClass, "Class with chosen name already exist.");
        }

        if (myMoveDestination != null && myCreateNewBuilderClass) {
            myMoveDestination.analyzeModuleConflicts(Collections.<PsiElement>emptyList(), conflicts, refUsages.get());
        }

        PsiMethod commonConstructor = getMostCommonConstructor();
        if (commonConstructor == null) {
            conflicts.putValue(null, "Found constructors are not reducible to simple chain");
        }

        return showConflicts(conflicts, refUsages.get());
    }

    @Nonnull
    @Override
    protected String getCommandName() {
        return REFACTORING_NAME;
    }
}
