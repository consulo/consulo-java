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
package com.intellij.java.impl.refactoring.extractclass;

import com.intellij.java.impl.codeInsight.PackageUtil;
import com.intellij.java.impl.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.java.impl.refactoring.MoveDestination;
import com.intellij.java.impl.refactoring.extractclass.usageInfo.*;
import com.intellij.java.impl.refactoring.move.MoveInstanceMembersUtil;
import com.intellij.java.impl.refactoring.move.moveClassesOrPackages.DestinationFolderComboBox;
import com.intellij.java.impl.refactoring.psi.MethodInheritanceUtils;
import com.intellij.java.impl.refactoring.psi.TypeParametersVisitor;
import com.intellij.java.impl.refactoring.util.FixableUsageInfo;
import com.intellij.java.impl.refactoring.util.FixableUsagesRefactoringProcessor;
import com.intellij.java.impl.refactoring.util.RefactoringUtil;
import com.intellij.java.impl.refactoring.util.classMembers.MemberInfo;
import com.intellij.java.language.impl.JavaFileType;
import com.intellij.java.language.psi.PsiElementFactory;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.javadoc.PsiDocTagValue;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.PropertyUtil;
import com.intellij.java.language.util.VisibilityUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.Application;
import consulo.application.Result;
import consulo.java.localize.JavaRefactoringLocalize;
import consulo.language.ast.IElementType;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.WriteCommandAction;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.ui.RefactoringUIUtil;
import consulo.language.psi.*;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.usage.UsageInfo;
import consulo.usage.UsageViewDescriptor;
import consulo.usage.UsageViewUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.MultiMap;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ref.SimpleReference;
import jakarta.annotation.Nonnull;

import java.util.*;

public class ExtractClassProcessor extends FixableUsagesRefactoringProcessor {
    private static final Logger logger = Logger.getInstance("com.siyeh.rpp.extractclass.ExtractClassProcessor");

    private final PsiClass sourceClass;
    private final List<PsiField> fields;
    private final List<PsiMethod> methods;
    private final List<PsiClass> innerClasses;
    private final Set<PsiClass> innerClassesToMakePublic = new HashSet<>();
    private final List<PsiTypeParameter> typeParams = new ArrayList<>();
    private final String newPackageName;
    private final MoveDestination myMoveDestination;
    private final String myNewVisibility;
    private final boolean myGenerateAccessors;
    private final List<PsiField> enumConstants;
    private final String newClassName;
    private final String delegateFieldName;
    private final boolean requiresBackpointer;
    private boolean delegationRequired = false;
    private final ExtractEnumProcessor myExtractEnumProcessor;
    private final PsiClass myClass;

    @RequiredUIAccess
    public ExtractClassProcessor(
        PsiClass sourceClass,
        List<PsiField> fields,
        List<PsiMethod> methods,
        List<PsiClass> innerClasses,
        String newPackageName,
        String newClassName
    ) {
        this(
            sourceClass,
            fields,
            methods,
            innerClasses,
            newPackageName,
            null,
            newClassName,
            null,
            false,
            Collections.<MemberInfo>emptyList()
        );
    }

    @RequiredUIAccess
    public ExtractClassProcessor(
        PsiClass sourceClass,
        List<PsiField> fields,
        List<PsiMethod> methods,
        List<PsiClass> classes,
        String packageName,
        MoveDestination moveDestination,
        String newClassName,
        String newVisibility,
        boolean generateAccessors,
        List<MemberInfo> enumConstants
    ) {
        super(sourceClass.getProject());
        this.sourceClass = sourceClass;
        this.newPackageName = packageName;
        myMoveDestination = moveDestination;
        myNewVisibility = newVisibility;
        myGenerateAccessors = generateAccessors;
        this.enumConstants = new ArrayList<>();
        for (MemberInfo constant : enumConstants) {
            if (constant.isChecked()) {
                this.enumConstants.add((PsiField)constant.getMember());
            }
        }
        this.fields = new ArrayList<>(fields);
        this.methods = new ArrayList<>(methods);
        this.innerClasses = new ArrayList<>(classes);
        this.newClassName = newClassName;
        delegateFieldName = calculateDelegateFieldName();
        requiresBackpointer = new BackpointerUsageVisitor(fields, innerClasses, methods, sourceClass).backpointerRequired();
        if (requiresBackpointer) {
            ContainerUtil.addAll(typeParams, sourceClass.getTypeParameters());
        }
        else {
            Set<PsiTypeParameter> typeParamSet = new HashSet<>();
            TypeParametersVisitor visitor = new TypeParametersVisitor(typeParamSet);
            for (PsiField field : fields) {
                field.accept(visitor);
            }
            for (PsiMethod method : methods) {
                method.accept(visitor);
                //do not include method's type parameters in class signature
                typeParamSet.removeAll(Arrays.asList(method.getTypeParameters()));
            }
            typeParams.addAll(typeParamSet);
        }
        myClass = new WriteCommandAction<PsiClass>(myProject, getCommandName()) {
            @Override
            @RequiredReadAction
            protected void run(Result<PsiClass> result) throws Throwable {
                result.setResult(buildClass());
            }
        }.execute().getResultObject();
        myExtractEnumProcessor = new ExtractEnumProcessor(myProject, this.enumConstants, myClass);
    }

    public PsiClass getCreatedClass() {
        return myClass;
    }

    @Override
    @RequiredUIAccess
    protected boolean preprocessUsages(@Nonnull SimpleReference<UsageInfo[]> refUsages) {
        MultiMap<PsiElement, String> conflicts = new MultiMap<>();
        myExtractEnumProcessor.findEnumConstantConflicts(refUsages);
        if (!DestinationFolderComboBox.isAccessible(
            myProject,
            sourceClass.getContainingFile().getVirtualFile(),
            myClass.getContainingFile().getContainingDirectory().getVirtualFile()
        )) {
            conflicts.putValue(
                sourceClass,
                "Extracted class won't be accessible in " + RefactoringUIUtil.getDescription(sourceClass, true)
            );
        }
        Application.get().runWriteAction(myClass::delete);
        Project project = sourceClass.getProject();
        GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        PsiClass existingClass =
            JavaPsiFacade.getInstance(project).findClass(StringUtil.getQualifiedName(newPackageName, newClassName), scope);
        if (existingClass != null) {
            conflicts.putValue(
                existingClass,
                RefactoringLocalize.cannotPerformRefactoringWithReason(
                    JavaRefactoringLocalize.thereAlreadyExistsAClassWithTheChosenName()
                ).get()
            );
        }

        if (!myGenerateAccessors) {
            calculateInitializersConflicts(conflicts);
            NecessaryAccessorsVisitor visitor = checkNecessaryGettersSetters4ExtractedClass();
            NecessaryAccessorsVisitor srcVisitor = checkNecessaryGettersSetters4SourceClass();
            Set<PsiField> fieldsNeedingGetter = new LinkedHashSet<>();
            fieldsNeedingGetter.addAll(visitor.getFieldsNeedingGetter());
            fieldsNeedingGetter.addAll(srcVisitor.getFieldsNeedingGetter());
            for (PsiField field : fieldsNeedingGetter) {
                conflicts.putValue(field, LocalizeValue.localizeTODO("Field \'" + field.getName() + "\' needs getter").get());
            }
            Set<PsiField> fieldsNeedingSetter = new LinkedHashSet<>();
            fieldsNeedingSetter.addAll(visitor.getFieldsNeedingSetter());
            fieldsNeedingSetter.addAll(srcVisitor.getFieldsNeedingSetter());
            for (PsiField field : fieldsNeedingSetter) {
                conflicts.putValue(field, LocalizeValue.localizeTODO("Field \'" + field.getName() + "\' needs setter").get());
            }
        }
        checkConflicts(refUsages, conflicts);
        return showConflicts(conflicts, refUsages.get());
    }


    private void calculateInitializersConflicts(MultiMap<PsiElement, String> conflicts) {
        PsiClassInitializer[] initializers = sourceClass.getInitializers();
        for (PsiClassInitializer initializer : initializers) {
            if (initializerDependsOnMoved(initializer)) {
                conflicts.putValue(initializer, LocalizeValue.localizeTODO("Class initializer requires moved members").get());
            }
        }
        for (PsiMethod constructor : sourceClass.getConstructors()) {
            if (initializerDependsOnMoved(constructor.getBody())) {
                conflicts.putValue(constructor, LocalizeValue.localizeTODO("Constructor requires moved members").get());
            }
        }
    }

    private boolean initializerDependsOnMoved(PsiElement initializer) {
        final boolean[] dependsOnMoved = new boolean[]{false};
        initializer.accept(new JavaRecursiveElementWalkingVisitor() {
            @Override
            @RequiredReadAction
            public void visitReferenceExpression(PsiReferenceExpression expression) {
                super.visitReferenceExpression(expression);
                if (expression.resolve() instanceof PsiMember member) {
                    dependsOnMoved[0] |= !member.isStatic() && isInMovedElement(member);
                }
            }
        });
        return dependsOnMoved[0];
    }

    private String calculateDelegateFieldName() {
        Project project = sourceClass.getProject();
        JavaCodeStyleSettings settings = JavaCodeStyleSettings.getInstance(sourceClass.getContainingFile());

        String baseName = settings.FIELD_NAME_PREFIX.length() == 0 ? StringUtil.decapitalize(newClassName) : newClassName;
        String name = settings.FIELD_NAME_PREFIX + baseName + settings.FIELD_NAME_SUFFIX;
        if (!existsFieldWithName(name) && !PsiNameHelper.getInstance(project).isKeyword(name)) {
            return name;
        }
        int counter = 1;
        while (true) {
            name = settings.FIELD_NAME_PREFIX + baseName + counter + settings.FIELD_NAME_SUFFIX;
            if (!existsFieldWithName(name) && !PsiNameHelper.getInstance(project).isKeyword(name)) {
                return name;
            }
            counter++;
        }
    }

    private boolean existsFieldWithName(String name) {
        PsiField[] allFields = sourceClass.getAllFields();
        for (PsiField field : allFields) {
            if (name.equals(field.getName()) && !fields.contains(field)) {
                return true;
            }
        }
        return false;
    }

    @Nonnull
    @Override
    protected String getCommandName() {
        return JavaRefactoringLocalize.extractedClassCommandName(newClassName).get();
    }

    @Nonnull
    @Override
    protected UsageViewDescriptor createUsageViewDescriptor(@Nonnull UsageInfo[] usageInfos) {
        return new ExtractClassUsageViewDescriptor(sourceClass);
    }

    @Override
    @RequiredReadAction
    protected void performRefactoring(@Nonnull UsageInfo[] usageInfos) {
        PsiClass psiClass = buildClass();
        if (psiClass == null) {
            return;
        }
        if (delegationRequired) {
            buildDelegate();
        }
        myExtractEnumProcessor.performEnumConstantTypeMigration(usageInfos);
        final Set<PsiMember> members = new HashSet<>();
        for (PsiMethod method : methods) {
            PsiMethod member = psiClass.findMethodBySignature(method, false);
            if (member != null) {
                members.add(member);
            }
        }
        for (PsiField field : fields) {
            PsiField member = psiClass.findFieldByName(field.getName(), false);
            if (member != null) {
                members.add(member);
                PsiExpression initializer = member.getInitializer();
                if (initializer != null) {
                    final boolean[] moveInitializerToConstructor = new boolean[1];
                    initializer.accept(new JavaRecursiveElementWalkingVisitor() {
                        @Override
                        @RequiredReadAction
                        public void visitReferenceExpression(PsiReferenceExpression expression) {
                            super.visitReferenceExpression(expression);
                            if (expression.resolve() instanceof PsiField field && !members.contains(field)) {
                                moveInitializerToConstructor[0] = true;
                            }
                        }
                    });

                    if (moveInitializerToConstructor[0]) {
                        PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(myProject);
                        PsiMethod[] constructors = psiClass.getConstructors();
                        if (constructors.length == 0) {
                            PsiMethod constructor = (PsiMethod)elementFactory.createConstructor().setName(psiClass.getName());
                            constructors = new PsiMethod[]{(PsiMethod)psiClass.add(constructor)};
                        }
                        for (PsiMethod constructor : constructors) {
                            MoveInstanceMembersUtil.moveInitializerToConstructor(elementFactory, constructor, member);
                        }
                    }
                }
            }
        }

        if (myGenerateAccessors) {
            NecessaryAccessorsVisitor visitor = checkNecessaryGettersSetters4SourceClass();
            for (PsiField field : visitor.getFieldsNeedingGetter()) {
                sourceClass.add(PropertyUtil.generateGetterPrototype(field));
            }

            for (PsiField field : visitor.getFieldsNeedingSetter()) {
                sourceClass.add(PropertyUtil.generateSetterPrototype(field));
            }
        }
        super.performRefactoring(usageInfos);
        if (myNewVisibility == null) {
            return;
        }
        for (PsiMember member : members) {
            VisibilityUtil.fixVisibility(UsageViewUtil.toElements(usageInfos), member, myNewVisibility);
        }
    }

    private NecessaryAccessorsVisitor checkNecessaryGettersSetters4SourceClass() {
        NecessaryAccessorsVisitor visitor = new NecessaryAccessorsVisitor() {
            @Override
            protected boolean hasGetterOrSetter(PsiMethod[] getters) {
                for (PsiMethod getter : getters) {
                    if (!isInMovedElement(getter)) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            protected boolean isProhibitedReference(PsiField field) {
                return !fields.contains(field) && !innerClasses.contains(field.getContainingClass());
            }
        };
        for (PsiField field : fields) {
            field.accept(visitor);
        }
        for (PsiMethod method : methods) {
            method.accept(visitor);
        }
        for (PsiClass innerClass : innerClasses) {
            innerClass.accept(visitor);
        }
        return visitor;
    }

    private NecessaryAccessorsVisitor checkNecessaryGettersSetters4ExtractedClass() {
        NecessaryAccessorsVisitor visitor = new NecessaryAccessorsVisitor() {
            @Override
            protected boolean hasGetterOrSetter(PsiMethod[] getters) {
                for (PsiMethod getter : getters) {
                    if (isInMovedElement(getter)) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            protected boolean isProhibitedReference(PsiField field) {
                return fields.contains(field) || innerClasses.contains(field.getContainingClass());
            }

            @Override
            public void visitMethod(@Nonnull PsiMethod method) {
                if (methods.contains(method)) {
                    return;
                }
                super.visitMethod(method);
            }

            @Override
            public void visitField(@Nonnull PsiField field) {
                if (fields.contains(field)) {
                    return;
                }
                super.visitField(field);
            }

            @Override
            public void visitClass(@Nonnull PsiClass aClass) {
                if (innerClasses.contains(aClass)) {
                    return;
                }
                super.visitClass(aClass);
            }

        };
        sourceClass.accept(visitor);
        return visitor;
    }

    @RequiredReadAction
    private void buildDelegate() {
        PsiManager manager = sourceClass.getManager();
        PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
        CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(manager.getProject());
        StringBuilder fieldBuffer = new StringBuilder();
        String delegateVisibility = calculateDelegateVisibility();
        if (delegateVisibility.length() > 0) {
            fieldBuffer.append(delegateVisibility).append(' ');
        }
        fieldBuffer.append("final ");
        String fullyQualifiedName = StringUtil.getQualifiedName(newPackageName, newClassName);
        fieldBuffer.append(fullyQualifiedName);
        if (!typeParams.isEmpty()) {
            fieldBuffer.append('<');
            for (PsiTypeParameter typeParameter : typeParams) {
                fieldBuffer.append(typeParameter.getName());
            }
            fieldBuffer.append('>');
        }
        fieldBuffer.append(' ');
        fieldBuffer.append(delegateFieldName);
        fieldBuffer.append(" = new ").append(fullyQualifiedName);
        if (!typeParams.isEmpty()) {
            fieldBuffer.append('<');
            for (PsiTypeParameter typeParameter : typeParams) {
                fieldBuffer.append(typeParameter.getName());
            }
            fieldBuffer.append('>');
        }
        fieldBuffer.append('(');
        if (requiresBackpointer) {
            fieldBuffer.append("this");
        }

        fieldBuffer.append(");");
        try {
            String fieldString = fieldBuffer.toString();
            PsiField field = factory.createFieldFromText(fieldString, sourceClass);
            PsiElement newField = sourceClass.add(field);
            codeStyleManager.reformat(JavaCodeStyleManager.getInstance(myProject).shortenClassReferences(newField));
        }
        catch (IncorrectOperationException e) {
            logger.error(e);
        }
    }

    private String calculateDelegateVisibility() {
        for (PsiField field : fields) {
            if (field.isPublic() && !field.isStatic()) {
                return "public";
            }
        }
        for (PsiField field : fields) {
            if (field.isProtected() && !field.isStatic()) {
                return "protected";
            }
        }
        for (PsiField field : fields) {
            if (field.hasModifierProperty(PsiModifier.PACKAGE_LOCAL) && !field.isStatic()) {
                return "";
            }
        }
        return "private";
    }

    @Override
    @RequiredReadAction
    public void findUsages(@Nonnull List<FixableUsageInfo> usages) {
        for (PsiField field : fields) {
            findUsagesForField(field, usages);
            usages.add(new RemoveField(field));
        }
        usages.addAll(myExtractEnumProcessor.findEnumConstantUsages(new ArrayList<>(usages)));
        for (PsiClass innerClass : innerClasses) {
            findUsagesForInnerClass(innerClass, usages);
            usages.add(new RemoveInnerClass(innerClass));
        }
        for (PsiMethod method : methods) {
            if (method.isStatic()) {
                findUsagesForStaticMethod(method, usages);
            }
            else {
                findUsagesForMethod(method, usages);
            }
        }
    }

    @RequiredReadAction
    private void findUsagesForInnerClass(PsiClass innerClass, List<FixableUsageInfo> usages) {
        PsiManager psiManager = innerClass.getManager();
        Project project = psiManager.getProject();
        GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        Iterable<PsiReference> calls = ReferencesSearch.search(innerClass, scope);
        String innerName = innerClass.getQualifiedName();
        assert innerName != null;
        String sourceClassQualifiedName = sourceClass.getQualifiedName();
        assert sourceClassQualifiedName != null;
        String newInnerClassName =
            StringUtil.getQualifiedName(newPackageName, newClassName) + innerName.substring(sourceClassQualifiedName.length());
        boolean hasExternalReference = false;
        for (PsiReference reference : calls) {
            PsiElement referenceElement = reference.getElement();
            if (referenceElement instanceof PsiJavaCodeReferenceElement javaCodeReferenceElement) {
                if (!isInMovedElement(referenceElement)) {
                    usages.add(new ReplaceClassReference(javaCodeReferenceElement, newInnerClassName));
                    hasExternalReference = true;
                }
            }
        }
        if (hasExternalReference) {
            innerClassesToMakePublic.add(innerClass);
        }
    }

    @RequiredReadAction
    private void findUsagesForMethod(PsiMethod method, List<FixableUsageInfo> usages) {
        PsiManager psiManager = method.getManager();
        Project project = psiManager.getProject();
        GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        Iterable<PsiReference> calls = ReferencesSearch.search(method, scope);
        for (PsiReference reference : calls) {
            if (reference.getElement().getParent() instanceof PsiMethodCallExpression call) {
                if (isInMovedElement(call)) {
                    continue;
                }
                PsiReferenceExpression methodExpression = call.getMethodExpression();
                PsiExpression qualifier = methodExpression.getQualifierExpression();
                if (qualifier == null || qualifier instanceof PsiThisExpression) {
                    usages.add(new ReplaceThisCallWithDelegateCall(call, delegateFieldName));
                }
                delegationRequired = true;
            }
        }

        if (!delegationRequired && MethodInheritanceUtils.hasSiblingMethods(method)) {
            delegationRequired = true;
        }

        if (delegationRequired) {
            usages.add(new MakeMethodDelegate(method, delegateFieldName));
        }
        else {
            usages.add(new RemoveMethod(method));
        }
    }

    @RequiredReadAction
    private void findUsagesForStaticMethod(PsiMethod method, List<FixableUsageInfo> usages) {
        PsiManager psiManager = method.getManager();
        Project project = psiManager.getProject();
        GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        Iterable<PsiReference> calls = ReferencesSearch.search(method, scope);
        String fullyQualifiedName = StringUtil.getQualifiedName(newPackageName, newClassName);
        for (PsiReference reference : calls) {
            PsiElement referenceElement = reference.getElement();

            PsiElement parent = referenceElement.getParent();
            if (parent instanceof PsiMethodCallExpression call) {
                if (!isInMovedElement(call)) {
                    usages.add(new RetargetStaticMethodCall(call, fullyQualifiedName));
                }
            }
            else if (parent instanceof PsiImportStaticStatement psiImportStatic) {
                PsiJavaCodeReferenceElement importReference = psiImportStatic.getImportReference();
                if (importReference != null && importReference.getQualifier() instanceof PsiJavaCodeReferenceElement qualifier) {
                    usages.add(new ReplaceClassReference(qualifier, fullyQualifiedName));
                }
            }
        }
        usages.add(new RemoveMethod(method));
    }

    private boolean isInMovedElement(PsiElement exp) {
        for (PsiField field : fields) {
            if (PsiTreeUtil.isAncestor(field, exp, false)) {
                return true;
            }
        }
        for (PsiMethod method : methods) {
            if (PsiTreeUtil.isAncestor(method, exp, false)) {
                return true;
            }
        }
        return false;
    }

    @RequiredReadAction
    private void findUsagesForField(PsiField field, List<FixableUsageInfo> usages) {
        PsiManager psiManager = field.getManager();
        Project project = psiManager.getProject();
        GlobalSearchScope scope = GlobalSearchScope.allScope(project);

        String qualifiedName = StringUtil.getQualifiedName(newPackageName, newClassName);
        String getter = null;
        if (myGenerateAccessors) {
            getter = PropertyUtil.suggestGetterName(field);
        }
        else {
            PsiMethod fieldGetter = PropertyUtil.findPropertyGetter(sourceClass, field.getName(), false, false);
            if (fieldGetter != null && isInMovedElement(fieldGetter)) {
                getter = fieldGetter.getName();
            }
        }

        String setter = null;
        if (myGenerateAccessors) {
            setter = PropertyUtil.suggestSetterName(field);
        }
        else {
            PsiMethod fieldSetter = PropertyUtil.findPropertySetter(sourceClass, field.getName(), false, false);
            if (fieldSetter != null && isInMovedElement(fieldSetter)) {
                setter = fieldSetter.getName();
            }
        }
        boolean isStatic = field.isStatic();

        for (PsiReference reference : ReferencesSearch.search(field, scope)) {
            PsiElement element = reference.getElement();
            if (isInMovedElement(element)) {
                continue;
            }

            if (element instanceof PsiReferenceExpression exp) {
                if (RefactoringUtil.isPlusPlusOrMinusMinus(exp.getParent())) {
                    usages.add(
                        isStatic
                            ? new ReplaceStaticVariableIncrementDecrement(exp, qualifiedName)
                            : new ReplaceInstanceVariableIncrementDecrement(exp, delegateFieldName, setter, getter, field.getName())
                    );
                }
                else if (RefactoringUtil.isAssignmentLHS(exp)) {
                    usages.add(
                        isStatic
                            ? new ReplaceStaticVariableAssignment(exp, qualifiedName)
                            : new ReplaceInstanceVariableAssignment(
                            PsiTreeUtil.getParentOfType(exp, PsiAssignmentExpression.class),
                            delegateFieldName,
                            setter,
                            getter,
                            field.getName()
                        )
                    );
                }
                else {
                    usages.add(
                        isStatic
                            ? new ReplaceStaticVariableAccess(exp, qualifiedName, enumConstants.contains(field))
                            : new ReplaceInstanceVariableAccess(exp, delegateFieldName, getter, field.getName())
                    );
                }

                if (!isStatic) {
                    delegationRequired = true;
                }
            }
            else if (element instanceof PsiDocTagValue) {
                usages.add(new BindJavadocReference(element, qualifiedName, field.getName()));
            }
        }
    }

    @RequiredReadAction
    private PsiClass buildClass() {
        PsiManager manager = sourceClass.getManager();
        Project project = sourceClass.getProject();
        ExtractedClassBuilder extractedClassBuilder = new ExtractedClassBuilder();
        extractedClassBuilder.setProject(myProject);
        extractedClassBuilder.setClassName(newClassName);
        extractedClassBuilder.setPackageName(newPackageName);
        extractedClassBuilder.setOriginalClassName(sourceClass.getQualifiedName());
        extractedClassBuilder.setRequiresBackPointer(requiresBackpointer);
        extractedClassBuilder.setExtractAsEnum(enumConstants);
        for (PsiField field : fields) {
            extractedClassBuilder.addField(field);
        }
        for (PsiMethod method : methods) {
            extractedClassBuilder.addMethod(method);
        }
        for (PsiClass innerClass : innerClasses) {
            extractedClassBuilder.addInnerClass(innerClass, innerClassesToMakePublic.contains(innerClass));
        }
        extractedClassBuilder.setTypeArguments(typeParams);
        List<PsiClass> interfaces = calculateInterfacesSupported();
        extractedClassBuilder.setInterfaces(interfaces);

        if (myGenerateAccessors) {
            NecessaryAccessorsVisitor visitor = checkNecessaryGettersSetters4ExtractedClass();
            sourceClass.accept(visitor);
            extractedClassBuilder.setFieldsNeedingGetters(visitor.getFieldsNeedingGetter());
            extractedClassBuilder.setFieldsNeedingSetters(visitor.getFieldsNeedingSetter());
        }

        String classString = extractedClassBuilder.buildBeanClass();

        try {
            PsiFile containingFile = sourceClass.getContainingFile();
            PsiDirectory directory;
            PsiDirectory containingDirectory = containingFile.getContainingDirectory();
            if (myMoveDestination != null) {
                directory = myMoveDestination.getTargetDirectory(containingDirectory);
            }
            else {
                Module module = containingFile.getModule();
                assert module != null;
                directory = PackageUtil.findOrCreateDirectoryForPackage(module, newPackageName, containingDirectory, false, true);
            }
            if (directory != null) {
                PsiFileFactory factory = PsiFileFactory.getInstance(project);
                PsiFile newFile = factory.createFileFromText(newClassName + ".java", JavaFileType.INSTANCE, classString);
                PsiElement addedFile = directory.add(newFile);
                CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(manager.getProject());
                PsiElement shortenedFile = JavaCodeStyleManager.getInstance(project).shortenClassReferences(addedFile);
                return ((PsiJavaFile)codeStyleManager.reformat(shortenedFile)).getClasses()[0];
            }
            else {
                return null;
            }
        }
        catch (IncorrectOperationException e) {
            return null;
        }
    }

    private List<PsiClass> calculateInterfacesSupported() {
        List<PsiClass> out = new ArrayList<>();
        PsiClass[] supers = sourceClass.getSupers();
        for (PsiClass superClass : supers) {
            if (!superClass.isInterface()) {
                continue;
            }
            PsiMethod[] superclassMethods = superClass.getMethods();
            if (superclassMethods.length == 0) {
                continue;
            }
            boolean allMethodsCovered = true;

            for (PsiMethod method : superclassMethods) {
                boolean isCovered = false;
                for (PsiMethod movedMethod : methods) {
                    if (isSuperMethod(method, movedMethod)) {
                        isCovered = true;
                        break;
                    }
                }
                if (!isCovered) {
                    allMethodsCovered = false;
                    break;
                }
            }
            if (allMethodsCovered) {
                out.add(superClass);
            }
        }
        Project project = sourceClass.getProject();
        PsiManager manager = sourceClass.getManager();
        GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        if (usesDefaultSerialization(sourceClass)) {
            PsiClass serializable = JavaPsiFacade.getInstance(manager.getProject()).findClass(CommonClassNames.JAVA_IO_SERIALIZABLE, scope);
            out.add(serializable);
        }
        if (usesDefaultClone(sourceClass)) {
            PsiClass cloneable = JavaPsiFacade.getInstance(manager.getProject()).findClass(CommonClassNames.JAVA_LANG_CLONEABLE, scope);
            out.add(cloneable);
        }
        return out;
    }

    private static boolean isSuperMethod(PsiMethod method, PsiMethod movedMethod) {
        PsiMethod[] superMethods = movedMethod.findSuperMethods();
        for (PsiMethod testMethod : superMethods) {
            if (testMethod.equals(method)) {
                return true;
            }
        }
        return false;
    }

    private static boolean usesDefaultClone(PsiClass aClass) {
        Project project = aClass.getProject();
        PsiManager manager = aClass.getManager();
        GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        PsiClass cloneable = JavaPsiFacade.getInstance(manager.getProject()).findClass(CommonClassNames.JAVA_LANG_CLONEABLE, scope);
        if (!InheritanceUtil.isInheritorOrSelf(aClass, cloneable, true)) {
            return false;
        }
        PsiMethod[] methods = aClass.findMethodsByName("clone", false);
        for (PsiMethod method : methods) {
            PsiParameterList parameterList = method.getParameterList();
            PsiParameter[] parameters = parameterList.getParameters();
            if (parameters.length == 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean usesDefaultSerialization(PsiClass aClass) {
        Project project = aClass.getProject();
        PsiManager manager = aClass.getManager();
        GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        PsiClass serializable = JavaPsiFacade.getInstance(manager.getProject()).findClass(CommonClassNames.JAVA_IO_SERIALIZABLE, scope);
        if (!InheritanceUtil.isInheritorOrSelf(aClass, serializable, true)) {
            return false;
        }
        PsiMethod[] methods = aClass.findMethodsByName("writeObject", false);
        for (PsiMethod method : methods) {
            PsiParameterList parameterList = method.getParameterList();
            PsiParameter[] parameters = parameterList.getParameters();
            if (parameters.length == 1) {
                PsiType type = parameters[0].getType();
                String text = type.getCanonicalText();
                if ("java.io.DataOutputStream".equals(text)) {
                    return false;
                }
            }
        }
        return true;
    }

    private abstract class NecessaryAccessorsVisitor extends JavaRecursiveElementWalkingVisitor {
        private final Set<PsiField> fieldsNeedingGetter = new HashSet<>();
        private final Set<PsiField> fieldsNeedingSetter = new HashSet<>();

        @Override
        @RequiredReadAction
        public void visitReferenceExpression(PsiReferenceExpression expression) {
            super.visitReferenceExpression(expression);
            if (isProhibitedReference(expression)) {
                PsiField field = getReferencedField(expression);
                if (!hasGetter(field) && !(field.isStatic() && field.isFinal()) && !field.isPublic()) {
                    fieldsNeedingGetter.add(field);
                }
            }
        }

        @Override
        @RequiredReadAction
        public void visitAssignmentExpression(@Nonnull PsiAssignmentExpression expression) {
            super.visitAssignmentExpression(expression);

            PsiExpression lhs = expression.getLExpression();
            if (isProhibitedReference(lhs)) {
                PsiField field = getReferencedField(lhs);
                if (!hasGetter(field) && !(field.isStatic() && field.isFinal()) && !field.isPublic()) {
                    fieldsNeedingSetter.add(field);
                }
            }
        }

        @Override
        @RequiredReadAction
        public void visitPostfixExpression(@Nonnull PsiPostfixExpression expression) {
            super.visitPostfixExpression(expression);
            checkSetterNeeded(expression.getOperand(), expression.getOperationSign());
        }

        @Override
        @RequiredReadAction
        public void visitPrefixExpression(@Nonnull PsiPrefixExpression expression) {
            super.visitPrefixExpression(expression);
            checkSetterNeeded(expression.getOperand(), expression.getOperationSign());
        }

        @RequiredReadAction
        private void checkSetterNeeded(PsiExpression operand, PsiJavaToken sign) {
            IElementType tokenType = sign.getTokenType();
            if (!tokenType.equals(JavaTokenType.PLUSPLUS) && !tokenType.equals(JavaTokenType.MINUSMINUS)) {
                return;
            }
            if (isProhibitedReference(operand)) {
                PsiField field = getReferencedField(operand);
                if (!hasSetter(field) && !(field.isStatic() && field.isFinal())) {
                    fieldsNeedingSetter.add(field);
                }
            }
        }

        public Set<PsiField> getFieldsNeedingGetter() {
            return fieldsNeedingGetter;
        }

        public Set<PsiField> getFieldsNeedingSetter() {
            return fieldsNeedingSetter;
        }

        private boolean hasGetter(PsiField field) {
            return hasGetterOrSetter(sourceClass.findMethodsBySignature(PropertyUtil.generateGetterPrototype(field), false));
        }

        private boolean hasSetter(PsiField field) {
            return hasGetterOrSetter(sourceClass.findMethodsBySignature(PropertyUtil.generateSetterPrototype(field), false));
        }

        protected abstract boolean hasGetterOrSetter(PsiMethod[] getters);

        protected boolean isProhibitedReference(PsiExpression expression) {
            return BackpointerUtil.isBackpointerReference(expression, NecessaryAccessorsVisitor.this::isProhibitedReference);
        }

        protected abstract boolean isProhibitedReference(PsiField field);

        @RequiredReadAction
        private PsiField getReferencedField(PsiExpression expression) {
            if (expression instanceof PsiParenthesizedExpression parenthesized) {
                return getReferencedField(parenthesized.getExpression());
            }
            PsiReferenceExpression reference = (PsiReferenceExpression)expression;
            return (PsiField)reference.resolve();
        }
    }
}
