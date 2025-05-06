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
 * Checks and Highlights problems with classes
 * User: cdr
 * Date: Aug 19, 2002
 */
package com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis;

import com.intellij.java.analysis.codeInsight.intention.QuickFixFactory;
import com.intellij.java.analysis.impl.codeInsight.ClassUtil;
import com.intellij.java.analysis.impl.psi.util.JavaMatchers;
import com.intellij.java.analysis.impl.psi.util.PsiMatchers;
import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.impl.JavaFileType;
import com.intellij.java.language.impl.codeInsight.ExceptionUtil;
import com.intellij.java.language.impl.refactoring.util.RefactoringChangeUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.document.util.TextRange;
import consulo.java.language.impl.localize.JavaErrorLocalize;
import consulo.java.language.module.util.JavaClassNames;
import consulo.language.editor.rawHighlight.HighlightInfo;
import consulo.language.editor.rawHighlight.HighlightInfoType;
import consulo.language.psi.*;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.PsiMatcherImpl;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.ModuleUtilCore;
import consulo.localize.LocalizeValue;
import consulo.module.Module;
import consulo.project.Project;
import consulo.util.io.FileUtil;
import consulo.util.lang.Comparing;
import consulo.util.lang.ref.SimpleReference;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;

public class HighlightClassUtil {
    /**
     * new ref(...) or new ref(..) { ... } where ref is abstract class
     */
    @Nullable
    @RequiredReadAction
    public static HighlightInfo.Builder checkAbstractInstantiation(@Nonnull PsiJavaCodeReferenceElement ref) {
        if (ref.getParent() instanceof PsiAnonymousClass anonymousClass
            && anonymousClass.getParent() instanceof PsiNewExpression newExpr
            && !PsiUtilCore.hasErrorElementChild(newExpr)) {
            return checkClassWithAbstractMethods(anonymousClass, ref.getTextRange());
        }
        return null;
    }

    @Nullable
    @RequiredReadAction
    private static HighlightInfo.Builder checkClassWithAbstractMethods(PsiClass aClass, TextRange range) {
        return checkClassWithAbstractMethods(aClass, aClass, range);
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo.Builder checkClassWithAbstractMethods(PsiClass aClass, PsiElement implementsFixElement, TextRange range) {
        PsiMethod abstractMethod = ClassUtil.getAnyAbstractMethod(aClass);

        if (abstractMethod == null) {
            return null;
        }

        PsiClass superClass = abstractMethod.getContainingClass();
        if (superClass == null) {
            return null;
        }

        String baseClassName = HighlightUtil.formatClass(aClass, false);
        String methodName = JavaHighlightUtil.formatMethod(abstractMethod);
        String c = HighlightUtil.formatClass(superClass, false);
        LocalizeValue description = aClass instanceof PsiEnumConstantInitializer || implementsFixElement instanceof PsiEnumConstant
            ? JavaErrorLocalize.enumConstantShouldImplementMethod(baseClassName, methodName, c)
            : JavaErrorLocalize.classMustBeAbstract(baseClassName, methodName, c);

        HighlightInfo.Builder hlBuilder = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
            .range(range)
            .descriptionAndTooltip(description);
        PsiMethod anyMethodToImplement = ClassUtil.getAnyMethodToImplement(aClass);
        QuickFixFactory factory = QuickFixFactory.getInstance();
        if (anyMethodToImplement != null) {
            if (!anyMethodToImplement.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)
                || JavaPsiFacade.getInstance(aClass.getProject()).arePackagesTheSame(aClass, superClass)) {
                hlBuilder.registerFix(factory.createImplementMethodsFix(implementsFixElement));
            }
            else {
                hlBuilder.registerFix(
                    factory.createModifierFixBuilder(anyMethodToImplement).add(PsiModifier.PROTECTED).showContainingClass().create()
                );
                hlBuilder.registerFix(
                    factory.createModifierFixBuilder(anyMethodToImplement).add(PsiModifier.PUBLIC).showContainingClass().create()
                );
            }
        }
        if (!(aClass instanceof PsiAnonymousClass)
            && HighlightUtil.getIncompatibleModifier(PsiModifier.ABSTRACT, aClass.getModifierList()) == null) {
            hlBuilder.registerFix(factory.createModifierFixBuilder(aClass).add(PsiModifier.ABSTRACT).create());
        }
        return hlBuilder;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo.Builder checkClassMustBeAbstract(PsiClass aClass, TextRange textRange) {
        if (aClass.isAbstract() || aClass.getRBrace() == null || aClass.isEnum() && hasEnumConstants(aClass)) {
            return null;
        }
        return checkClassWithAbstractMethods(aClass, textRange);
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo.Builder checkInstantiationOfAbstractClass(PsiClass aClass, @Nonnull PsiElement highlightElement) {
        if (aClass != null && aClass.isAbstract()
            && !(highlightElement instanceof PsiNewExpression newExpr && newExpr.getType() instanceof PsiArrayType)) {
            HighlightInfo.Builder hlBuilder = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(highlightElement)
                .descriptionAndTooltip(JavaErrorLocalize.abstractCannotBeInstantiated(aClass.getName()));
            PsiMethod anyAbstractMethod = ClassUtil.getAnyAbstractMethod(aClass);
            QuickFixFactory factory = QuickFixFactory.getInstance();
            if (!aClass.isInterface() && anyAbstractMethod == null) {
                // suggest to make not abstract only if possible
                hlBuilder.registerFix(factory.createModifierFixBuilder(aClass).remove(PsiModifier.ABSTRACT).create());
            }
            if (anyAbstractMethod != null && highlightElement instanceof PsiNewExpression newExpr && newExpr.getClassReference() != null) {
                hlBuilder.registerFix(factory.createImplementAbstractClassMethodsFix(highlightElement));
            }
            return hlBuilder;
        }
        return null;
    }

    private static boolean hasEnumConstants(PsiClass aClass) {
        PsiField[] fields = aClass.getFields();
        for (PsiField field : fields) {
            if (field instanceof PsiEnumConstant) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkDuplicateTopLevelClass(PsiClass aClass) {
        if (!(aClass.getParent() instanceof PsiFile)) {
            return null;
        }
        String qualifiedName = aClass.getQualifiedName();
        if (qualifiedName == null) {
            return null;
        }
        int numOfClassesToFind = 2;
        if (qualifiedName.contains("$")) {
            qualifiedName = qualifiedName.replaceAll("\\$", ".");
            numOfClassesToFind = 1;
        }
        PsiManager manager = aClass.getManager();
        Module module = ModuleUtilCore.findModuleForPsiElement(aClass);
        if (module == null) {
            return null;
        }

        PsiClass[] classes = JavaPsiFacade.getInstance(aClass.getProject())
            .findClasses(qualifiedName, GlobalSearchScope.moduleScope(module));
        if (classes.length < numOfClassesToFind) {
            return null;
        }
        String dupFileName = null;
        for (PsiClass dupClass : classes) {
            // do not use equals
            if (dupClass != aClass) {
                VirtualFile file = dupClass.getContainingFile().getVirtualFile();
                if (file != null && manager.isInProject(dupClass)) {
                    dupFileName = FileUtil.toSystemDependentName(file.getPath());
                    break;
                }
            }
        }
        if (dupFileName == null) {
            return null;
        }

        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
            .range(HighlightNamesUtil.getClassDeclarationTextRange(aClass))
            .descriptionAndTooltip(JavaErrorLocalize.duplicateClassInOtherFile(dupFileName))
            .create();
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo.Builder checkDuplicateNestedClass(PsiClass aClass) {
        if (aClass == null) {
            return null;
        }
        PsiElement parent = aClass;
        if (aClass.getParent() instanceof PsiDeclarationStatement) {
            parent = aClass.getParent();
        }
        String name = aClass.getName();
        if (name == null) {
            return null;
        }
        boolean duplicateFound = false;
        boolean checkSiblings = true;
        while (parent != null) {
            if (parent instanceof PsiFile) {
                break;
            }
            PsiElement element = checkSiblings ? parent.getPrevSibling() : null;
            if (element == null) {
                element = parent.getParent();
                // JLS 14.3:
                // The name of a local class C may not be redeclared
                //  as a local class of the directly enclosing method, constructor, or initializer block within the
                // scope of C
                // , or a compile-time error occurs.
                //  However, a local class declaration may be shadowed (?6.3.1)
                //  anywhere inside a class declaration nested within the local class declaration's scope.
                if (element instanceof PsiMethod || element instanceof PsiClass ||
                    element instanceof PsiCodeBlock && element.getParent() instanceof PsiClassInitializer) {
                    checkSiblings = false;
                }
            }
            parent = element;

            if (element instanceof PsiDeclarationStatement) {
                element = PsiTreeUtil.getChildOfType(element, PsiClass.class);
            }
            if (element instanceof PsiClass psiClass && name.equals(psiClass.getName())) {
                duplicateFound = true;
                break;
            }
        }

        if (duplicateFound) {
            TextRange textRange = HighlightNamesUtil.getClassDeclarationTextRange(aClass);
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(textRange)
                .descriptionAndTooltip(JavaErrorLocalize.duplicateClass(name));
        }
        return null;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo.Builder checkPublicClassInRightFile(PsiClass aClass) {
        PsiFile containingFile = aClass.getContainingFile();
        if (aClass.getParent() != containingFile || !aClass.isPublic() || !(containingFile instanceof PsiJavaFile)) {
            return null;
        }
        PsiJavaFile file = (PsiJavaFile)containingFile;
        VirtualFile virtualFile = file.getVirtualFile();
        if (virtualFile == null || aClass.getName().equals(virtualFile.getNameWithoutExtension())) {
            return null;
        }
        TextRange range = HighlightNamesUtil.getClassDeclarationTextRange(aClass);
        PsiModifierList psiModifierList = aClass.getModifierList();
        QuickFixFactory factory = QuickFixFactory.getInstance();
        HighlightInfo.Builder errorResult = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
            .range(aClass, range.getStartOffset(), range.getEndOffset())
            .descriptionAndTooltip(JavaErrorLocalize.publicClassShouldBeNamedAfterFile(aClass.getName()))
            .registerFix(factory.createModifierFixBuilder(psiModifierList).remove(PsiModifier.PUBLIC).create());
        PsiClass[] classes = file.getClasses();
        if (classes.length > 1) {
            errorResult.registerFix(factory.createMoveClassToSeparateFileFix(aClass));
        }
        for (PsiClass otherClass : classes) {
            if (!otherClass.getManager().areElementsEquivalent(otherClass, aClass)
                && otherClass.isPublic()
                && otherClass.getName().equals(virtualFile.getNameWithoutExtension())) {
                return errorResult;
            }
        }
        return errorResult.registerFix(factory.createRenameFileFix(aClass.getName() + JavaFileType.DOT_DEFAULT_EXTENSION))
            .registerFix(factory.createRenameElementFix(aClass));
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo.Builder checkClassAndPackageConflict(@Nonnull PsiClass aClass) {
        String name = aClass.getQualifiedName();

        if (JavaClassNames.DEFAULT_PACKAGE.equals(name)) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(HighlightNamesUtil.getClassDeclarationTextRange(aClass))
                .descriptionAndTooltip(JavaErrorLocalize.classClashesWithPackage(name));
        }

        PsiElement file = aClass.getParent();
        if (file instanceof PsiJavaFile javaFile && !javaFile.getPackageName().isEmpty()
            && file.getParent() instanceof PsiDirectory directory) {
            String simpleName = aClass.getName();
            PsiDirectory subDirectory = directory.findSubdirectory(simpleName);
            if (subDirectory != null && simpleName.equals(subDirectory.getName())) {
                return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(HighlightNamesUtil.getClassDeclarationTextRange(aClass))
                    .descriptionAndTooltip(JavaErrorLocalize.classClashesWithPackage(name));
            }
        }

        return null;
    }

    @Nullable
    @RequiredReadAction
    private static HighlightInfo.Builder checkStaticFieldDeclarationInInnerClass(@Nonnull PsiKeyword keyword) {
        if (getEnclosingStaticClass(keyword, PsiField.class) == null) {
            return null;
        }

        PsiField field = (PsiField)keyword.getParent().getParent();
        if (PsiUtilCore.hasErrorElementChild(field) || PsiUtil.isCompileTimeConstant(field)) {
            return null;
        }

        QuickFixFactory factory = QuickFixFactory.getInstance();
        HighlightInfo.Builder hlBuilder = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
            .range(keyword)
            .descriptionAndTooltip(JavaErrorLocalize.staticDeclarationInInnerClass())
            .registerFix(factory.createModifierFixBuilder(field).remove(PsiModifier.STATIC).create());

        PsiClass aClass = field.getContainingClass();
        if (aClass != null) {
            hlBuilder.registerFix(factory.createModifierFixBuilder(aClass).add(PsiModifier.STATIC).create());
        }

        return hlBuilder;
    }

    @Nullable
    @RequiredReadAction
    private static HighlightInfo.Builder checkStaticMethodDeclarationInInnerClass(PsiKeyword keyword, LanguageLevel languageLevel) {
        if (languageLevel.isAtLeast(LanguageLevel.JDK_16)) {
            return null;
        }

        if (getEnclosingStaticClass(keyword, PsiMethod.class) == null) {
            return null;
        }
        PsiMethod method = (PsiMethod)keyword.getParent().getParent();
        if (PsiUtilCore.hasErrorElementChild(method)) {
            return null;
        }
        QuickFixFactory factory = QuickFixFactory.getInstance();
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
            .range(keyword)
            .descriptionAndTooltip(JavaErrorLocalize.staticDeclarationInInnerClass())
            .registerFix(factory.createModifierFixBuilder(method).remove(PsiModifier.STATIC).create())
            .registerFix(factory.createModifierFixBuilder((PsiClass)method.getParent()).add(PsiModifier.STATIC).create());
    }

    @Nullable
    @RequiredReadAction
    private static HighlightInfo.Builder checkStaticInitializerDeclarationInInnerClass(PsiKeyword keyword) {
        if (getEnclosingStaticClass(keyword, PsiClassInitializer.class) == null) {
            return null;
        }
        PsiClassInitializer initializer = (PsiClassInitializer)keyword.getParent().getParent();
        if (PsiUtilCore.hasErrorElementChild(initializer)) {
            return null;
        }
        PsiClass owner = (PsiClass)initializer.getParent();
        QuickFixFactory factory = QuickFixFactory.getInstance();
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
            .range(keyword)
            .descriptionAndTooltip(JavaErrorLocalize.staticDeclarationInInnerClass())
            .registerFix(factory.createModifierFixBuilder(initializer).remove(PsiModifier.STATIC).create())
            .registerFix(factory.createModifierFixBuilder(owner).add(PsiModifier.STATIC).create());
    }

    private static PsiElement getEnclosingStaticClass(@Nonnull PsiKeyword keyword, @Nonnull Class<?> parentClass) {
        return new PsiMatcherImpl(keyword)
            .dot(PsiMatchers.hasText(PsiModifier.STATIC))
            .parent(PsiMatchers.hasClass(PsiModifierList.class))
            .parent(PsiMatchers.hasClass(parentClass))
            .parent(PsiMatchers.hasClass(PsiClass.class))
            .dot(JavaMatchers.hasNoModifier(PsiModifier.STATIC))
            .parent(PsiMatchers.hasClass(PsiClass.class, PsiDeclarationStatement.class, PsiNewExpression.class, PsiEnumConstant.class))
            .getElement();
    }

    @Nullable
    @RequiredReadAction
    private static HighlightInfo.Builder checkStaticClassDeclarationInInnerClass(PsiKeyword keyword) {
        // keyword points to 'class' or 'interface' or 'enum'
        if (new PsiMatcherImpl(keyword)
            .parent(PsiMatchers.hasClass(PsiClass.class))
            .dot(JavaMatchers.hasModifier(PsiModifier.STATIC))
            .parent(PsiMatchers.hasClass(PsiClass.class))
            .dot(JavaMatchers.hasNoModifier(PsiModifier.STATIC))
            .parent(PsiMatchers.hasClass(PsiClass.class, PsiDeclarationStatement.class, PsiNewExpression.class, PsiEnumConstant.class))
            .getElement() == null) {
            return null;
        }

        PsiClass aClass = (PsiClass)keyword.getParent();
        if (PsiUtilCore.hasErrorElementChild(aClass)) {
            return null;
        }

        // highlight 'static' keyword if any, or class or interface if not
        PsiElement context = null;
        PsiModifierList modifierList = aClass.getModifierList();
        if (modifierList != null) {
            for (PsiElement element : modifierList.getChildren()) {
                if (Comparing.equal(element.getText(), PsiModifier.STATIC)) {
                    context = element;
                    break;
                }
            }
        }
        TextRange range = context != null
            ? context.getTextRange()
            : HighlightNamesUtil.getClassDeclarationTextRange(aClass);
        HighlightInfo.Builder hlBuilder = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
            .range(range)
            .descriptionAndTooltip(JavaErrorLocalize.staticDeclarationInInnerClass());
        QuickFixFactory factory = QuickFixFactory.getInstance();
        if (context != keyword) {
            hlBuilder.registerFix(factory.createModifierFixBuilder(aClass).remove(PsiModifier.STATIC).create());
        }
        PsiClass containingClass = aClass.getContainingClass();
        if (containingClass != null) {
            hlBuilder.registerFix(factory.createModifierFixBuilder(containingClass).add(PsiModifier.STATIC).create());
        }
        return hlBuilder;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo.Builder checkStaticDeclarationInInnerClass(PsiKeyword keyword, LanguageLevel languageLevel) {
        HighlightInfo.Builder errorResult = checkStaticFieldDeclarationInInnerClass(keyword);
        if (errorResult == null) {
            errorResult = checkStaticMethodDeclarationInInnerClass(keyword, languageLevel);
        }
        if (errorResult == null) {
            errorResult = checkStaticClassDeclarationInInnerClass(keyword);
        }
        if (errorResult == null) {
            errorResult = checkStaticInitializerDeclarationInInnerClass(keyword);
        }
        return errorResult;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo.Builder checkExtendsAllowed(PsiReferenceList list) {
        if (list.getParent() instanceof PsiClass aClass && aClass.isEnum()) {
            boolean isExtends = list.equals(aClass.getExtendsList());
            if (isExtends) {
                return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(list)
                    .descriptionAndTooltip(JavaErrorLocalize.extendsAfterEnum());
            }
        }
        return null;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo.Builder checkImplementsAllowed(PsiReferenceList list) {
        if (list.getParent() instanceof PsiClass aClass && aClass.isInterface()) {
            boolean isImplements = list.equals(aClass.getImplementsList());
            if (isImplements) {
                HighlightInfo.Builder result = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(list)
                    .descriptionAndTooltip(JavaErrorLocalize.implementsAfterInterface());
                PsiClassType[] referencedTypes = list.getReferencedTypes();
                if (referencedTypes.length > 0) {
                    result.registerFix(QuickFixFactory.getInstance().createChangeExtendsToImplementsFix(aClass, referencedTypes[0]));
                }
                return result;
            }
        }
        return null;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo.Builder checkExtendsClassAndImplementsInterface(
        PsiReferenceList referenceList,
        JavaResolveResult resolveResult,
        PsiJavaCodeReferenceElement ref
    ) {
        PsiClass aClass = (PsiClass)referenceList.getParent();
        boolean isImplements = referenceList.equals(aClass.getImplementsList());
        boolean isInterface = aClass.isInterface();
        if (isInterface && isImplements) {
            return null;
        }
        boolean mustBeInterface = isImplements || isInterface;
        PsiClass extendFrom = (PsiClass)resolveResult.getElement();
        if (extendFrom.isInterface() != mustBeInterface) {
            LocalizeValue message = mustBeInterface
                ? JavaErrorLocalize.interfaceExpected()
                : JavaErrorLocalize.noInterfaceExpected();
            PsiClassType type = JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory().createType(ref);
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(ref)
                .descriptionAndTooltip(message)
                .registerFix(QuickFixFactory.getInstance().createChangeExtendsToImplementsFix(aClass, type));
        }
        return null;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo.Builder checkCannotInheritFromFinal(PsiClass superClass, PsiElement elementToHighlight) {
        if (superClass.isFinal() || superClass.isEnum()) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(elementToHighlight)
                .descriptionAndTooltip(JavaErrorLocalize.inheritanceFromFinalClass(superClass.getQualifiedName()))
                .registerFix(QuickFixFactory.getInstance().createModifierFixBuilder(superClass).remove(PsiModifier.FINAL).create());
        }
        return null;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo.Builder checkAnonymousInheritFinal(PsiNewExpression expression) {
        PsiAnonymousClass aClass = PsiTreeUtil.getChildOfType(expression, PsiAnonymousClass.class);
        if (aClass == null) {
            return null;
        }
        PsiClassType baseClassReference = aClass.getBaseClassType();
        PsiClass baseClass = baseClassReference.resolve();
        if (baseClass == null) {
            return null;
        }
        return checkCannotInheritFromFinal(baseClass, aClass.getBaseClassReference());
    }

    @Nonnull
    private static List<PsiClassType> collectUnhandledExceptions(PsiMethod constructor, @Nonnull PsiClassType[] handledExceptions) {
        PsiClassType[] referencedTypes = constructor.getThrowsList().getReferencedTypes();
        List<PsiClassType> exceptions = new ArrayList<>();
        for (PsiClassType referencedType : referencedTypes) {
            if (!ExceptionUtil.isUncheckedException(referencedType)
                && !ExceptionUtil.isHandledBy(referencedType, handledExceptions)) {
                exceptions.add(referencedType);
            }
        }
        return exceptions;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo.Builder checkClassDoesNotCallSuperConstructorOrHandleExceptions(
        @Nonnull PsiClass aClass,
        RefCountHolder refCountHolder,
        @Nonnull PsiResolveHelper resolveHelper
    ) {
        if (aClass.isEnum()) {
            return null;
        }
        // check only no-ctr classes. Problem with specific constructor will be highlighted inside it
        if (aClass.getConstructors().length != 0) {
            return null;
        }
        // find no-args base class ctr
        TextRange textRange = HighlightNamesUtil.getClassDeclarationTextRange(aClass);
        return checkBaseClassDefaultConstructorProblem(aClass, refCountHolder, resolveHelper, textRange, PsiClassType.EMPTY_ARRAY);
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo.Builder checkBaseClassDefaultConstructorProblem(
        @Nonnull PsiClass aClass,
        RefCountHolder refCountHolder,
        @Nonnull PsiResolveHelper resolveHelper,
        @Nonnull TextRange range,
        @Nonnull PsiClassType[] handledExceptions
    ) {
        if (aClass instanceof PsiAnonymousClass) {
            return null;
        }
        PsiClass baseClass = aClass.getSuperClass();
        if (baseClass == null) {
            return null;
        }
        PsiMethod[] constructors = baseClass.getConstructors();
        if (constructors.length == 0) {
            return null;
        }

        for (PsiMethod constructor : constructors) {
            if (resolveHelper.isAccessible(constructor, aClass, null)) {
                int parametersCount = constructor.getParameterList().getParametersCount();
                if (parametersCount == 0 || parametersCount == 1 && constructor.isVarArgs()) {
                    // it is an error if base ctr throws exceptions
                    List<PsiClassType> exceptions = collectUnhandledExceptions(constructor, handledExceptions);
                    if (!exceptions.isEmpty()) {
                        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                            .range(range)
                            .descriptionAndTooltip(HighlightUtil.getUnhandledExceptionsDescriptor(exceptions))
                            .registerFix(QuickFixFactory.getInstance().createCreateConstructorMatchingSuperFix(aClass));
                    }
                    if (refCountHolder != null) {
                        refCountHolder.registerLocallyReferenced(constructor);
                    }
                    return null;
                }
            }
        }

        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
            .range(range)
            .descriptionAndTooltip(JavaErrorLocalize.noDefaultConstructorAvailable(HighlightUtil.formatClass(baseClass)))
            .registerFix(QuickFixFactory.getInstance().createCreateConstructorMatchingSuperFix(aClass));
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo.Builder checkInterfaceCannotBeLocal(PsiClass aClass) {
        if (PsiUtil.isLocalClass(aClass)) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(HighlightNamesUtil.getClassDeclarationTextRange(aClass))
                .descriptionAndTooltip(JavaErrorLocalize.interfaceCannotBeLocal());
        }
        return null;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo.Builder checkCyclicInheritance(PsiClass aClass) {
        PsiClass circularClass = getCircularClass(aClass, new HashSet<>());
        if (circularClass != null) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(HighlightNamesUtil.getClassDeclarationTextRange(aClass))
                .descriptionAndTooltip(JavaErrorLocalize.cyclicInheritance(HighlightUtil.formatClass(circularClass)));
        }
        return null;
    }

    @Nullable
    public static PsiClass getCircularClass(PsiClass aClass, Collection<PsiClass> usedClasses) {
        if (usedClasses.contains(aClass)) {
            return aClass;
        }
        try {
            usedClasses.add(aClass);
            PsiClass[] superTypes = aClass.getSupers();
            for (PsiElement superType : superTypes) {
                while (superType instanceof PsiClass) {
                    if (!JavaClassNames.JAVA_LANG_OBJECT.equals(((PsiClass)superType).getQualifiedName())) {
                        PsiClass circularClass = getCircularClass((PsiClass)superType, usedClasses);
                        if (circularClass != null) {
                            return circularClass;
                        }
                    }
                    // check class qualifier
                    superType = superType.getParent();
                }
            }
        }
        finally {
            usedClasses.remove(aClass);
        }
        return null;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo.Builder checkExtendsDuplicate(
        PsiJavaCodeReferenceElement element,
        PsiElement resolved,
        @Nonnull PsiFile containingFile
    ) {
        if (!(element.getParent() instanceof PsiReferenceList)) {
            return null;
        }
        PsiReferenceList list = (PsiReferenceList)element.getParent();
        if (!(list.getParent() instanceof PsiClass)) {
            return null;
        }
        if (!(resolved instanceof PsiClass)) {
            return null;
        }
        PsiClass aClass = (PsiClass)resolved;
        PsiClassType[] referencedTypes = list.getReferencedTypes();
        int dupCount = 0;
        PsiManager manager = containingFile.getManager();
        for (PsiClassType referencedType : referencedTypes) {
            PsiClass resolvedElement = referencedType.resolve();
            if (resolvedElement != null && manager.areElementsEquivalent(resolvedElement, aClass)) {
                dupCount++;
            }
        }
        if (dupCount > 1) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(element)
                .descriptionAndTooltip(JavaErrorLocalize.duplicateClass(HighlightUtil.formatClass(aClass)));
        }
        return null;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkClassAlreadyImported(PsiClass aClass, PsiElement elementToHighlight) {
        PsiFile file = aClass.getContainingFile();
        if (!(file instanceof PsiJavaFile)) {
            return null;
        }
        PsiJavaFile javaFile = (PsiJavaFile)file;
        // check only top-level classes conflicts
        if (aClass.getParent() != javaFile) {
            return null;
        }
        PsiImportList importList = javaFile.getImportList();
        if (importList == null) {
            return null;
        }
        PsiImportStatementBase[] importStatements = importList.getAllImportStatements();
        for (PsiImportStatementBase importStatement : importStatements) {
            if (importStatement.isOnDemand()) {
                continue;
            }
            if (importStatement.resolve() instanceof PsiClass importClass && !importClass.equals(aClass)
                && Objects.equals(aClass.getName(), importClass.getName())) {
                return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(elementToHighlight)
                    .descriptionAndTooltip(JavaErrorLocalize.classAlreadyImported(HighlightUtil.formatClass(aClass, false)))
                    .create();
            }
        }
        return null;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo.Builder checkClassExtendsOnlyOneClass(PsiReferenceList list) {
        PsiClassType[] referencedTypes = list.getReferencedTypes();
        if (list.getParent() instanceof PsiClass aClass && !aClass.isInterface()
            && referencedTypes.length > 1 && aClass.getExtendsList() == list) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(list)
                .descriptionAndTooltip(JavaErrorLocalize.classCannotExtendMultipleClasses());
        }

        return null;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo.Builder checkThingNotAllowedInInterface(PsiElement element, PsiClass aClass) {
        if (aClass == null || !aClass.isInterface()) {
            return null;
        }
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
            .range(element)
            .descriptionAndTooltip(JavaErrorLocalize.notAllowedInInterface());
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo.Builder checkQualifiedNew(PsiNewExpression expression, PsiType type, PsiClass aClass) {
        PsiExpression qualifier = expression.getQualifier();
        if (qualifier == null) {
            return null;
        }
        QuickFixFactory factory = QuickFixFactory.getInstance();
        if (type instanceof PsiArrayType) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(expression)
                .descriptionAndTooltip(JavaErrorLocalize.invalidQualifiedNew())
                .registerFix(factory.createRemoveNewQualifierFix(expression, null));
        }
        if (aClass != null) {
            if (aClass.isStatic()) {
                HighlightInfo.Builder hlBuilder = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(expression)
                    .descriptionAndTooltip(JavaErrorLocalize.qualifiedNewOfStaticClass());
                if (!aClass.isEnum()) {
                    hlBuilder.registerFix(factory.createModifierFixBuilder(aClass).remove(PsiModifier.STATIC).create());
                }
                hlBuilder.registerFix(factory.createRemoveNewQualifierFix(expression, aClass));
            }
            else if (aClass instanceof PsiAnonymousClass anonymousClass) {
                PsiClass baseClass = PsiUtil.resolveClassInType(anonymousClass.getBaseClassType());
                if (baseClass != null && baseClass.isInterface()) {
                    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                        .range(expression)
                        .descriptionAndTooltip(LocalizeValue.localizeTODO(
                            "Anonymous class implements interface; cannot have qualifier for new"
                        ))
                        .registerFix(factory.createRemoveNewQualifierFix(expression, aClass));
                }
            }
        }
        return null;
    }

    /**
     * class c extends foreign.inner {}
     *
     * @param extendRef points to the class in the extends list
     * @param resolved  extendRef resolved
     */
    @Nullable
    @RequiredReadAction
    public static HighlightInfo.Builder checkClassExtendsForeignInnerClass(PsiJavaCodeReferenceElement extendRef, PsiElement resolved) {
        if (!(extendRef.getParent() instanceof PsiReferenceList referenceList
            && referenceList.getParent() instanceof PsiClass aClass)) {
            return null;
        }
        PsiClass containerClass;
        if (aClass instanceof PsiTypeParameter typeParam) {
            if (!(typeParam.getOwner() instanceof PsiClass ownerClass)) {
                return null;
            }
            containerClass = ownerClass;
        }
        else {
            containerClass = aClass;
        }

        if (aClass.getExtendsList() != referenceList && aClass.getImplementsList() != referenceList) {
            return null;
        }
        if (!(resolved instanceof PsiClass)) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(extendRef)
                .descriptionAndTooltip(JavaErrorLocalize.classNameExpected());
        }
        SimpleReference<HighlightInfo.Builder> infos = SimpleReference.create();
        extendRef.accept(new JavaRecursiveElementWalkingVisitor() {
            @Override
            public void visitElement(PsiElement element) {
                if (!infos.isNull()) {
                    return;
                }
                super.visitElement(element);
            }

            @Override
            @RequiredReadAction
            public void visitReferenceElement(@Nonnull PsiJavaCodeReferenceElement reference) {
                super.visitReferenceElement(reference);
                if (reference.resolve() instanceof PsiClass base) {
                    PsiClass baseClass = base.getContainingClass();
                    if (baseClass != null && base.isPrivate() && baseClass == containerClass) {
                        infos.set(
                            HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                                .range(extendRef)
                                .descriptionAndTooltip(JavaErrorLocalize.privateSymbol(
                                    HighlightUtil.formatClass(base),
                                    HighlightUtil.formatClass(baseClass)
                                ))
                        );
                        return;
                    }

                    // must be inner class
                    if (!PsiUtil.isInnerClass(base)) {
                        return;
                    }

                    if (base == resolved && baseClass != null
                        && (!PsiTreeUtil.isAncestor(baseClass, extendRef, true) || aClass.isStatic())
                        && !InheritanceUtil.hasEnclosingInstanceInScope(baseClass, extendRef, !aClass.isStatic(), true)
                        && !qualifiedNewCalledInConstructors(aClass)) {
                        infos.set(
                            HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                                .range(extendRef)
                                .descriptionAndTooltip(JavaErrorLocalize.noEnclosingInstanceInScope(HighlightUtil.formatClass(baseClass)))
                        );
                    }
                }
            }
        });

        return infos.get();
    }

    /**
     * 15.9 Class Instance Creation Expressions | 15.9.2 Determining Enclosing Instances
     */
    private static boolean qualifiedNewCalledInConstructors(PsiClass aClass) {
        PsiMethod[] constructors = aClass.getConstructors();
        if (constructors.length == 0) {
            return false;
        }
        for (PsiMethod constructor : constructors) {
            PsiCodeBlock body = constructor.getBody();
            if (body == null) {
                return false;
            }
            PsiStatement[] statements = body.getStatements();
            if (statements.length == 0) {
                return false;
            }
            PsiStatement firstStatement = statements[0];
            if (!(firstStatement instanceof PsiExpressionStatement)) {
                return false;
            }
            PsiExpression expression = ((PsiExpressionStatement)firstStatement).getExpression();
            if (!RefactoringChangeUtil.isSuperOrThisMethodCall(expression)) {
                return false;
            }
            PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
            if (PsiKeyword.THIS.equals(methodCallExpression.getMethodExpression().getReferenceName())) {
                continue;
            }
            PsiReferenceExpression referenceExpression = methodCallExpression.getMethodExpression();
            PsiExpression qualifierExpression = PsiUtil.skipParenthesizedExprDown(referenceExpression
                .getQualifierExpression());
            //If the class instance creation expression is qualified, then the immediately
            //enclosing instance of i is the object that is the value of the Primary expression or the ExpressionName,
            //otherwise aClass needs to be a member of a class enclosing the class in which the class instance
            // creation expression appears
            //already excluded by InheritanceUtil.hasEnclosingInstanceInScope
            if (qualifierExpression == null) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkCreateInnerClassFromStaticContext(
        PsiNewExpression expression,
        PsiType type,
        PsiClass aClass
    ) {
        if (type == null || type instanceof PsiArrayType || type instanceof PsiPrimitiveType) {
            return null;
        }
        if (aClass == null) {
            return null;
        }
        if (aClass instanceof PsiAnonymousClass anonymousClass) {
            aClass = anonymousClass.getBaseClassType().resolve();
            if (aClass == null) {
                return null;
            }
        }

        PsiExpression qualifier = expression.getQualifier();
        return checkCreateInnerClassFromStaticContext(expression, qualifier, aClass);
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkCreateInnerClassFromStaticContext(
        PsiElement element,
        @Nullable PsiExpression qualifier,
        PsiClass aClass
    ) {
        PsiElement placeToSearchEnclosingFrom;
        if (qualifier != null) {
            PsiType qType = qualifier.getType();
            placeToSearchEnclosingFrom = PsiUtil.resolveClassInType(qType);
        }
        else {
            placeToSearchEnclosingFrom = element;
        }
        return checkCreateInnerClassFromStaticContext(element, placeToSearchEnclosingFrom, aClass);
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkCreateInnerClassFromStaticContext(
        PsiElement element,
        PsiElement placeToSearchEnclosingFrom,
        PsiClass aClass
    ) {
        if (aClass == null || !PsiUtil.isInnerClass(aClass)) {
            return null;
        }
        PsiClass outerClass = aClass.getContainingClass();
        if (outerClass == null) {
            return null;
        }

        if (outerClass instanceof PsiSyntheticClass
            || InheritanceUtil.hasEnclosingInstanceInScope(outerClass, placeToSearchEnclosingFrom, true, false)) {
            return null;
        }
        return reportIllegalEnclosingUsage(placeToSearchEnclosingFrom, aClass, outerClass, element);
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo.Builder checkSuperQualifierType(@Nonnull Project project, @Nonnull PsiMethodCallExpression superCall) {
        if (!RefactoringChangeUtil.isSuperMethodCall(superCall)) {
            return null;
        }
        PsiMethod ctr = PsiTreeUtil.getParentOfType(superCall, PsiMethod.class, true, PsiMember.class);
        if (ctr == null) {
            return null;
        }
        PsiClass aClass = ctr.getContainingClass();
        if (aClass == null) {
            return null;
        }
        PsiClass targetClass = aClass.getSuperClass();
        if (targetClass == null) {
            return null;
        }
        PsiExpression qualifier = superCall.getMethodExpression().getQualifierExpression();
        if (qualifier != null) {
            if (PsiUtil.isInnerClass(targetClass)) {
                PsiClass outerClass = targetClass.getContainingClass();
                if (outerClass != null) {
                    PsiClassType outerType = JavaPsiFacade.getInstance(project).getElementFactory().createType(outerClass);
                    return HighlightUtil.checkAssignability(outerType, null, qualifier, qualifier);
                }
            }
            else {
                String description = "'" + HighlightUtil.formatClass(targetClass) + "' is not an inner class";
                return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(qualifier)
                    .descriptionAndTooltip(description);
            }
        }
        return null;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo reportIllegalEnclosingUsage(
        PsiElement place,
        @Nullable PsiClass aClass,
        PsiClass outerClass,
        PsiElement elementToHighlight
    ) {
        if (outerClass != null && !PsiTreeUtil.isContextAncestor(outerClass, place, false)) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(elementToHighlight)
                .descriptionAndTooltip(JavaErrorLocalize.isNotAnEnclosingClass(HighlightUtil.formatClass(outerClass)))
                .create();
        }
        PsiModifierListOwner staticParent = PsiUtil.getEnclosingStaticElement(place, outerClass);
        if (staticParent != null) {
            String element = outerClass == null ? "" : HighlightUtil.formatClass(outerClass) + "." +
                (place instanceof PsiSuperExpression ? PsiKeyword.SUPER : PsiKeyword.THIS);
            QuickFixFactory factory = QuickFixFactory.getInstance();
            HighlightInfo.Builder highlightInfo = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(elementToHighlight)
                .descriptionAndTooltip(JavaErrorLocalize.cannotBeReferencedFromStaticContext(element))
                // make context not static or referenced class static
                .registerFix(factory.createModifierFixBuilder(staticParent).remove(PsiModifier.STATIC).create());
            if (aClass != null
                && HighlightUtil.getIncompatibleModifier(PsiModifier.STATIC, aClass.getModifierList()) == null) {
                highlightInfo.registerFix(factory.createModifierFixBuilder(aClass).add(PsiModifier.STATIC).create());
            }
            return highlightInfo.create();
        }
        return null;
    }
}
