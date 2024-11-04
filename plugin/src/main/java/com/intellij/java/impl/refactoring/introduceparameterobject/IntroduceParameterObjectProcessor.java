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
package com.intellij.java.impl.refactoring.introduceparameterobject;

import com.intellij.java.analysis.impl.refactoring.util.VariableData;
import com.intellij.java.impl.codeInsight.PackageUtil;
import com.intellij.java.impl.refactoring.MoveDestination;
import com.intellij.java.impl.refactoring.introduceparameterobject.usageInfo.*;
import com.intellij.java.impl.refactoring.util.FixableUsageInfo;
import com.intellij.java.impl.refactoring.util.FixableUsagesRefactoringProcessor;
import com.intellij.java.impl.refactoring.util.RefactoringUtil;
import com.intellij.java.indexing.search.searches.OverridingMethodsSearch;
import com.intellij.java.language.impl.JavaFileType;
import com.intellij.java.language.impl.psi.impl.source.javadoc.PsiDocParamRef;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.codeStyle.VariableKind;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import com.intellij.java.language.psi.javadoc.PsiDocTag;
import com.intellij.java.language.psi.util.PropertyUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import com.intellij.java.language.util.VisibilityUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.ide.impl.idea.openapi.module.ModuleUtil;
import consulo.java.localize.JavaRefactoringLocalize;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.psi.*;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.usage.UsageInfo;
import consulo.usage.UsageViewDescriptor;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.MultiMap;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ref.Ref;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class IntroduceParameterObjectProcessor extends FixableUsagesRefactoringProcessor {
    private static final Logger logger = Logger.getInstance("com.siyeh.rpp.introduceparameterobject.IntroduceParameterObjectProcessor");

    private MoveDestination myMoveDestination;
    private final PsiMethod method;
    private final String className;
    private final String packageName;
    private final boolean keepMethodAsDelegate;
    private final boolean myUseExistingClass;
    private final boolean myCreateInnerClass;
    private final String myNewVisibility;
    private final boolean myGenerateAccessors;
    private final List<ParameterChunk> parameters;
    private final int[] paramsToMerge;
    private final List<PsiTypeParameter> typeParams;
    private final Set<PsiParameter> paramsNeedingSetters = new HashSet<>();
    private final Set<PsiParameter> paramsNeedingGetters = new HashSet<>();
    private final PsiClass existingClass;
    private PsiMethod myExistingClassCompatibleConstructor;

    public IntroduceParameterObjectProcessor(
        String className,
        String packageName,
        MoveDestination moveDestination,
        PsiMethod method,
        VariableData[] parameters, boolean keepMethodAsDelegate, final boolean useExistingClass,
        final boolean createInnerClass,
        String newVisibility,
        boolean generateAccessors
    ) {
        super(method.getProject());
        myMoveDestination = moveDestination;
        this.method = method;
        this.className = className;
        this.packageName = packageName;
        this.keepMethodAsDelegate = keepMethodAsDelegate;
        myUseExistingClass = useExistingClass;
        myCreateInnerClass = createInnerClass;
        myNewVisibility = newVisibility;
        myGenerateAccessors = generateAccessors;
        this.parameters = new ArrayList<>();
        for (VariableData parameter : parameters) {
            this.parameters.add(new ParameterChunk(parameter));
        }
        final PsiParameterList parameterList = method.getParameterList();
        final PsiParameter[] methodParams = parameterList.getParameters();
        paramsToMerge = new int[parameters.length];
        for (int p = 0; p < parameters.length; p++) {
            VariableData parameter = parameters[p];
            for (int i = 0; i < methodParams.length; i++) {
                final PsiParameter methodParam = methodParams[i];
                if (parameter.variable.equals(methodParam)) {
                    paramsToMerge[p] = i;
                    break;
                }
            }
        }
        final Set<PsiTypeParameter> typeParamSet = new HashSet<>();
        final PsiTypeVisitor<Object> typeParametersVisitor = new PsiTypeVisitor<>() {
            @Override
            public Object visitClassType(PsiClassType classType) {
                final PsiClass referent = classType.resolve();
                if (referent instanceof PsiTypeParameter) {
                    typeParamSet.add((PsiTypeParameter)referent);
                }
                return super.visitClassType(classType);
            }
        };
        for (VariableData parameter : parameters) {
            parameter.type.accept(typeParametersVisitor);
        }
        typeParams = new ArrayList<>(typeParamSet);

        final String qualifiedName = StringUtil.getQualifiedName(packageName, className);
        final GlobalSearchScope scope = GlobalSearchScope.allScope(myProject);
        existingClass = JavaPsiFacade.getInstance(myProject).findClass(qualifiedName, scope);
    }

    @Nonnull
    @Override
    protected UsageViewDescriptor createUsageViewDescriptor(@Nonnull UsageInfo[] usageInfos) {
        return new IntroduceParameterObjectUsageViewDescriptor(method);
    }

    @Override
    @RequiredUIAccess
    protected boolean preprocessUsages(@Nonnull final Ref<UsageInfo[]> refUsages) {
        MultiMap<PsiElement, String> conflicts = new MultiMap<>();
        if (myUseExistingClass) {
            if (existingClass == null) {
                conflicts.putValue(
                    null,
                    RefactoringLocalize.cannotPerformRefactoringWithReason(
                        LocalizeValue.localizeTODO("Could not find the selected class")
                    ).get()
                );
            }
            if (myExistingClassCompatibleConstructor == null) {
                conflicts.putValue(
                    existingClass,
                    RefactoringLocalize.cannotPerformRefactoringWithReason(
                        LocalizeValue.localizeTODO("Selected class has no compatible constructors")
                    ).get()
                );
            }
        }
        else {
            if (existingClass != null) {
                conflicts.putValue(
                    existingClass,
                    RefactoringLocalize.cannotPerformRefactoringWithReason(
                        JavaRefactoringLocalize.thereAlreadyExistsAClassWithTheChosenName()
                    ).get()
                );
            }
            if (myMoveDestination != null) {
                if (!myMoveDestination.isTargetAccessible(myProject, method.getContainingFile().getVirtualFile())) {
                    conflicts.putValue(method, LocalizeValue.localizeTODO("Created class won't be accessible").get());
                }
            }
        }
        for (UsageInfo usageInfo : refUsages.get()) {
            if (usageInfo instanceof FixableUsageInfo) {
                final String conflictMessage = ((FixableUsageInfo)usageInfo).getConflictMessage();
                if (conflictMessage != null) {
                    conflicts.putValue(usageInfo.getElement(), conflictMessage);
                }
            }
        }
        return showConflicts(conflicts, refUsages.get());
    }

    @Override
    @RequiredReadAction
    public void findUsages(@Nonnull List<FixableUsageInfo> usages) {
        if (myUseExistingClass && existingClass != null) {
            myExistingClassCompatibleConstructor = existingClassIsCompatible(existingClass, parameters);
        }
        findUsagesForMethod(method, usages, true);

        if (myUseExistingClass && existingClass != null && !(paramsNeedingGetters.isEmpty() && paramsNeedingSetters.isEmpty())) {
            usages.add(new AppendAccessorsUsageInfo(
                existingClass,
                myGenerateAccessors,
                paramsNeedingGetters,
                paramsNeedingSetters,
                parameters
            ));
        }

        final PsiMethod[] overridingMethods = OverridingMethodsSearch.search(method, true).toArray(PsiMethod.EMPTY_ARRAY);
        for (PsiMethod siblingMethod : overridingMethods) {
            findUsagesForMethod(siblingMethod, usages, false);
        }

        if (myNewVisibility != null) {
            usages.add(new BeanClassVisibilityUsageInfo(
                existingClass,
                usages.toArray(new UsageInfo[usages.size()]),
                myNewVisibility,
                myExistingClassCompatibleConstructor
            ));
        }
    }

    @RequiredReadAction
    private void findUsagesForMethod(PsiMethod overridingMethod, List<FixableUsageInfo> usages, boolean changeSignature) {
        final PsiCodeBlock body = overridingMethod.getBody();
        final String baseParameterName = StringUtil.decapitalize(className);
        final String fixedParamName = body != null
            ? JavaCodeStyleManager.getInstance(myProject).suggestUniqueVariableName(baseParameterName, body.getLBrace(), true)
            : JavaCodeStyleManager.getInstance(myProject).propertyNameToVariableName(baseParameterName, VariableKind.PARAMETER);

        usages.add(new MergeMethodArguments(
            overridingMethod,
            className,
            packageName,
            fixedParamName,
            paramsToMerge,
            typeParams,
            keepMethodAsDelegate,
            myCreateInnerClass ? method.getContainingClass() : null,
            changeSignature
        ));

        final ParamUsageVisitor visitor = new ParamUsageVisitor(overridingMethod, paramsToMerge);
        overridingMethod.accept(visitor);
        final Set<PsiReferenceExpression> values = visitor.getParameterUsages();
        for (PsiReferenceExpression paramUsage : values) {
            final PsiParameter parameter = (PsiParameter)paramUsage.resolve();
            assert parameter != null;
            final PsiMethod containingMethod = (PsiMethod)parameter.getDeclarationScope();
            final int index = containingMethod.getParameterList().getParameterIndex(parameter);
            final PsiParameter replacedParameter = method.getParameterList().getParameters()[index];
            final ParameterChunk parameterChunk = ParameterChunk.getChunkByParameter(parameter, parameters);

            String getter = parameterChunk != null ? parameterChunk.getter : null;
            if (getter == null) {
                getter = PropertyUtil.suggestGetterName(replacedParameter.getName(), replacedParameter.getType());
                paramsNeedingGetters.add(replacedParameter);
            }
            String setter = parameterChunk != null ? parameterChunk.setter : null;
            if (setter == null) {
                setter = PropertyUtil.suggestSetterName(replacedParameter.getName());
            }
            if (RefactoringUtil.isPlusPlusOrMinusMinus(paramUsage.getParent())) {
                usages.add(new ReplaceParameterIncrementDecrement(paramUsage, fixedParamName, setter, getter));
                if (parameterChunk == null || parameterChunk.setter == null) {
                    paramsNeedingSetters.add(replacedParameter);
                }
            }
            else if (RefactoringUtil.isAssignmentLHS(paramUsage)) {
                usages.add(new ReplaceParameterAssignmentWithCall(paramUsage, fixedParamName, setter, getter));
                if (parameterChunk == null || parameterChunk.setter == null) {
                    paramsNeedingSetters.add(replacedParameter);
                }
            }
            else {
                usages.add(new ReplaceParameterReferenceWithCall(paramUsage, fixedParamName, getter));
            }
        }
    }

    @Override
    @RequiredReadAction
    protected void performRefactoring(UsageInfo[] usageInfos) {
        final PsiClass psiClass = buildClass();
        if (psiClass != null) {
            fixJavadocForConstructor(psiClass);
            super.performRefactoring(usageInfos);
            if (!myUseExistingClass) {
                for (PsiReference reference : ReferencesSearch.search(method)) {
                    final PsiElement place = reference.getElement();
                    VisibilityUtil.escalateVisibility(psiClass, place);
                    for (PsiMethod constructor : psiClass.getConstructors()) {
                        VisibilityUtil.escalateVisibility(constructor, place);
                    }
                }
            }
        }
    }

    @RequiredReadAction
    private PsiClass buildClass() {
        if (existingClass != null) {
            return existingClass;
        }
        final ParameterObjectBuilder beanClassBuilder = new ParameterObjectBuilder();
        beanClassBuilder.setVisibility(myCreateInnerClass ? PsiModifier.PRIVATE : PsiModifier.PUBLIC);
        beanClassBuilder.setProject(myProject);
        beanClassBuilder.setTypeArguments(typeParams);
        beanClassBuilder.setClassName(className);
        beanClassBuilder.setPackageName(packageName);
        for (ParameterChunk parameterChunk : parameters) {
            final VariableData parameter = parameterChunk.parameter;
            final boolean setterRequired = paramsNeedingSetters.contains(parameter.variable);
            beanClassBuilder.addField((PsiParameter)parameter.variable, parameter.name, parameter.type, setterRequired);
        }
        final String classString = beanClassBuilder.buildBeanClass();

        try {
            final PsiFileFactory factory = PsiFileFactory.getInstance(method.getProject());
            final PsiJavaFile newFile = (PsiJavaFile)factory.createFileFromText(className + ".java", JavaFileType.INSTANCE, classString);
            if (myCreateInnerClass) {
                final PsiClass containingClass = method.getContainingClass();
                final PsiClass[] classes = newFile.getClasses();
                assert classes.length > 0 : classString;
                final PsiClass innerClass = (PsiClass)containingClass.add(classes[0]);
                PsiUtil.setModifierProperty(innerClass, PsiModifier.STATIC, true);
                return (PsiClass)JavaCodeStyleManager.getInstance(newFile.getProject()).shortenClassReferences(innerClass);
            }
            else {
                final PsiFile containingFile = method.getContainingFile();
                final PsiDirectory containingDirectory = containingFile.getContainingDirectory();
                final PsiDirectory directory;
                if (myMoveDestination != null) {
                    directory = myMoveDestination.getTargetDirectory(containingDirectory);
                }
                else {
                    final Module module = ModuleUtil.findModuleForPsiElement(containingFile);
                    directory = PackageUtil.findOrCreateDirectoryForPackage(module, packageName, containingDirectory, true, true);
                }

                if (directory != null) {
                    final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(method.getManager().getProject());
                    final PsiElement shortenedFile = JavaCodeStyleManager.getInstance(newFile.getProject()).shortenClassReferences(newFile);
                    final PsiElement reformattedFile = codeStyleManager.reformat(shortenedFile);
                    return ((PsiJavaFile)directory.add(reformattedFile)).getClasses()[0];
                }
            }
        }
        catch (IncorrectOperationException e) {
            logger.info(e);
        }
        return null;
    }

    @RequiredReadAction
    private void fixJavadocForConstructor(PsiClass psiClass) {
        final PsiDocComment docComment = method.getDocComment();
        if (docComment != null) {
            final List<PsiDocTag> mergedTags = new ArrayList<>();
            final PsiDocTag[] paramTags = docComment.findTagsByName("param");
            for (PsiDocTag paramTag : paramTags) {
                final PsiElement[] dataElements = paramTag.getDataElements();
                if (dataElements.length > 0) {
                    if (dataElements[0] instanceof PsiDocParamRef docParamRef) {
                        final PsiReference reference = docParamRef.getReference();
                        if (reference != null && reference.resolve() instanceof PsiParameter parameter) {
                            final int parameterIndex = method.getParameterList().getParameterIndex(parameter);
                            if (ArrayUtil.find(paramsToMerge, parameterIndex) < 0) {
                                continue;
                            }
                        }
                    }
                    mergedTags.add((PsiDocTag)paramTag.copy());
                }
            }

            PsiMethod compatibleParamObjectConstructor = null;
            if (myExistingClassCompatibleConstructor != null && myExistingClassCompatibleConstructor.getDocComment() == null) {
                compatibleParamObjectConstructor = myExistingClassCompatibleConstructor;
            }
            else if (!myUseExistingClass) {
                compatibleParamObjectConstructor = psiClass.getConstructors()[0];
            }

            if (compatibleParamObjectConstructor != null) {
                PsiDocComment psiDocComment = JavaPsiFacade.getElementFactory(myProject).createDocCommentFromText("/**\n*/");
                psiDocComment = (PsiDocComment)compatibleParamObjectConstructor.addBefore(
                    psiDocComment,
                    compatibleParamObjectConstructor.getFirstChild()
                );

                for (PsiDocTag tag : mergedTags) {
                    psiDocComment.add(tag);
                }
            }
        }
    }

    @Nonnull
    @Override
    @RequiredReadAction
    protected String getCommandName() {
        final PsiClass containingClass = method.getContainingClass();
        return JavaRefactoringLocalize.introducedParameterClassCommandName(className, containingClass.getName(), method.getName()).get();
    }

    private static class ParamUsageVisitor extends JavaRecursiveElementVisitor {
        private final Set<PsiParameter> paramsToMerge = new HashSet<>();
        private final Set<PsiReferenceExpression> parameterUsages = new HashSet<>(4);

        ParamUsageVisitor(PsiMethod method, int[] paramIndicesToMerge) {
            super();
            final PsiParameterList paramList = method.getParameterList();
            final PsiParameter[] parameters = paramList.getParameters();
            for (int i : paramIndicesToMerge) {
                paramsToMerge.add(parameters[i]);
            }
        }

        @Override
        @RequiredReadAction
        public void visitReferenceExpression(PsiReferenceExpression expression) {
            super.visitReferenceExpression(expression);
            final PsiElement referent = expression.resolve();
            if (referent instanceof PsiParameter parameter && paramsToMerge.contains(parameter)) {
                parameterUsages.add(expression);
            }
        }

        public Set<PsiReferenceExpression> getParameterUsages() {
            return parameterUsages;
        }
    }

    @Nullable
    private static PsiMethod existingClassIsCompatible(PsiClass aClass, List<ParameterChunk> params) {
        if (params.size() == 1) {
            final ParameterChunk parameterChunk = params.get(0);
            final PsiType paramType = parameterChunk.parameter.type;
            if (TypeConversionUtil.isPrimitiveWrapper(aClass.getQualifiedName())) {
                parameterChunk.setField(aClass.findFieldByName("value", false));
                parameterChunk.setGetter(paramType.getCanonicalText() + "Value");
                for (PsiMethod constructor : aClass.getConstructors()) {
                    if (constructorIsCompatible(constructor, params)) {
                        return constructor;
                    }
                }
            }
        }
        final PsiMethod[] constructors = aClass.getConstructors();
        PsiMethod compatibleConstructor = null;
        for (PsiMethod constructor : constructors) {
            if (constructorIsCompatible(constructor, params)) {
                compatibleConstructor = constructor;
                break;
            }
        }
        if (compatibleConstructor == null) {
            return null;
        }
        final PsiParameterList parameterList = compatibleConstructor.getParameterList();
        final PsiParameter[] constructorParams = parameterList.getParameters();
        for (int i = 0; i < constructorParams.length; i++) {
            final PsiParameter param = constructorParams[i];
            final ParameterChunk parameterChunk = params.get(i);

            final PsiField field = findFieldAssigned(param, compatibleConstructor);
            if (field == null) {
                return null;
            }

            parameterChunk.setField(field);

            final PsiMethod getterForField = PropertyUtil.findGetterForField(field);
            if (getterForField != null) {
                parameterChunk.setGetter(getterForField.getName());
            }

            final PsiMethod setterForField = PropertyUtil.findSetterForField(field);
            if (setterForField != null) {
                parameterChunk.setSetter(setterForField.getName());
            }
        }
        return compatibleConstructor;
    }

    private static boolean constructorIsCompatible(PsiMethod constructor, List<ParameterChunk> params) {
        final PsiParameterList parameterList = constructor.getParameterList();
        final PsiParameter[] constructorParams = parameterList.getParameters();
        if (constructorParams.length != params.size()) {
            return false;
        }
        for (int i = 0; i < constructorParams.length; i++) {
            if (!TypeConversionUtil.isAssignable(constructorParams[i].getType(), params.get(i).parameter.type)) {
                return false;
            }
        }
        return true;
    }

    public static class ParameterChunk {
        private final VariableData parameter;
        private PsiField field;
        private String getter;
        private String setter;

        public ParameterChunk(VariableData parameter) {
            this.parameter = parameter;
        }

        public void setField(PsiField field) {
            this.field = field;
        }

        public void setGetter(String getter) {
            this.getter = getter;
        }

        public void setSetter(String setter) {
            this.setter = setter;
        }

        @Nullable
        public PsiField getField() {
            return field;
        }

        @Nullable
        public static ParameterChunk getChunkByParameter(PsiParameter param, List<ParameterChunk> params) {
            for (ParameterChunk chunk : params) {
                if (chunk.parameter.variable.equals(param)) {
                    return chunk;
                }
            }
            return null;
        }
    }

    private static PsiField findFieldAssigned(PsiParameter param, PsiMethod constructor) {
        final ParamAssignmentFinder visitor = new ParamAssignmentFinder(param);
        constructor.accept(visitor);
        return visitor.getFieldAssigned();
    }

    private static class ParamAssignmentFinder extends JavaRecursiveElementWalkingVisitor {
        private final PsiParameter myParam;

        private PsiField fieldAssigned = null;

        ParamAssignmentFinder(PsiParameter param) {
            myParam = param;
        }

        @Override
        @RequiredReadAction
        public void visitAssignmentExpression(@Nonnull PsiAssignmentExpression assignment) {
            super.visitAssignmentExpression(assignment);
            if (assignment.getLExpression() instanceof PsiReferenceExpression lhs
                && assignment.getRExpression() instanceof PsiReferenceExpression rhs) {
                PsiElement referent = rhs.resolve();
                if (referent == null || !referent.equals(myParam)) {
                    return;
                }
                PsiElement assigned = lhs.resolve();
                if (assigned != null && assigned instanceof PsiField assignedField) {
                    fieldAssigned = assignedField;
                }
            }
        }

        public PsiField getFieldAssigned() {
            return fieldAssigned;
        }
    }
}
