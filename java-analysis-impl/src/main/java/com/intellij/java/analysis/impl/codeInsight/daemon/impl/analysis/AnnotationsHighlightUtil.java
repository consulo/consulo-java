/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis;

import com.intellij.java.analysis.codeInsight.intention.QuickFixFactory;
import com.intellij.java.language.JavaFeature;
import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.codeInsight.AnnotationTargetUtil;
import com.intellij.java.language.impl.psi.impl.PsiImplUtil;
import com.intellij.java.language.impl.psi.impl.source.PsiClassReferenceType;
import com.intellij.java.language.impl.psi.impl.source.PsiImmediateClassType;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.ClassUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.codeEditor.Editor;
import consulo.java.language.impl.localize.JavaErrorLocalize;
import consulo.java.language.localize.JavaCompilationErrorLocalize;
import consulo.language.editor.intention.IntentionAction;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.editor.rawHighlight.HighlightInfo;
import consulo.language.editor.rawHighlight.HighlightInfoType;
import consulo.language.pattern.ElementPattern;
import consulo.language.psi.*;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.lang.Comparing;
import consulo.util.lang.ObjectUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.intellij.java.language.patterns.PsiJavaPatterns.psiElement;

/**
 * @author ven
 */
public class AnnotationsHighlightUtil {
    private static final Logger LOG = Logger.getInstance(AnnotationsHighlightUtil.class);

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkNameValuePair(PsiNameValuePair pair) {
        PsiReference ref = pair.getReference();
        if (ref == null) {
            return null;
        }
        PsiMethod method = (PsiMethod) ref.resolve();
        if (method == null) {
            if (pair.getName() != null) {
                return HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF)
                    .range(ref.getElement())
                    .descriptionAndTooltip(JavaErrorLocalize.annotationUnknownMethod(ref.getCanonicalText()))
                    .registerFix(QuickFixFactory.getInstance().createCreateAnnotationMethodFromUsageFix(pair))
                    .create();
            }
            else {
                HighlightInfo.Builder hlBuilder = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(ref.getElement())
                    .descriptionAndTooltip(JavaErrorLocalize.annotationMissingMethod(ref.getCanonicalText()));
                for (IntentionAction action : QuickFixFactory.getInstance().createAddAnnotationAttributeNameFixes(pair)) {
                    hlBuilder.registerFix(action);
                }
                return hlBuilder.create();
            }
        }
        else {
            PsiType returnType = method.getReturnType();
            assert returnType != null : method;
            PsiAnnotationMemberValue value = pair.getValue();
            HighlightInfo info = checkMemberValueType(value, returnType);
            if (info != null) {
                return info;
            }

            return checkDuplicateAttribute(pair);
        }
    }

    @Nullable
    @RequiredReadAction
    private static HighlightInfo checkDuplicateAttribute(PsiNameValuePair pair) {
        PsiAnnotationParameterList annotation = (PsiAnnotationParameterList) pair.getParent();
        PsiNameValuePair[] attributes = annotation.getAttributes();
        for (PsiNameValuePair attribute : attributes) {
            if (attribute == pair) {
                break;
            }
            String name = pair.getName();
            if (Comparing.equal(attribute.getName(), name)) {
                return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(pair)
                    .descriptionAndTooltip(JavaCompilationErrorLocalize.annotationAttributeDuplicate(
                        name == null ? PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME : name
                    ))
                    .create();
            }
        }

        return null;
    }

    @RequiredReadAction
    private static String formatReference(PsiJavaCodeReferenceElement ref) {
        return ref.getCanonicalText();
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkMemberValueType(@Nullable PsiAnnotationMemberValue value, PsiType expectedType) {
        if (value == null) {
            return null;
        }

        if (expectedType instanceof PsiClassType && expectedType.equalsToText(CommonClassNames.JAVA_LANG_CLASS)) {
            if (!(value instanceof PsiClassObjectAccessExpression)) {
                return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(value)
                    .descriptionAndTooltip(JavaCompilationErrorLocalize.annotationAttributeNonClassLiteral())
                    .create();
            }
        }

        if (value instanceof PsiAnnotation annotation) {
            PsiJavaCodeReferenceElement nameRef = annotation.getNameReferenceElement();
            if (nameRef == null) {
                return null;
            }

            if (expectedType instanceof PsiClassType expectedClassType) {
                PsiClass aClass = expectedClassType.resolve();
                if (aClass != null && nameRef.isReferenceTo(aClass)) {
                    return null;
                }
            }

            if (expectedType instanceof PsiArrayType expectedArrayType
                && expectedArrayType.getComponentType() instanceof PsiClassType componentClassType) {
                PsiClass aClass = componentClassType.resolve();
                if (aClass != null && nameRef.isReferenceTo(aClass)) {
                    return null;
                }
            }

            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(annotation)
                .descriptionAndTooltip(JavaCompilationErrorLocalize.annotationAttributeIncompatibleType(
                    JavaHighlightUtil.formatType(expectedType),
                    formatReference(nameRef)
                ))
                .create();
        }

        if (value instanceof PsiArrayInitializerMemberValue) {
            if (expectedType instanceof PsiArrayType) {
                return null;
            }
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(value)
                .descriptionAndTooltip(JavaCompilationErrorLocalize.annotationAttributeIllegalArrayInitializer(JavaHighlightUtil.formatType(expectedType)))
                .create();
        }

        if (value instanceof PsiExpression expr) {
            PsiType type = expr.getType();

            PsiClass psiClass = PsiUtil.resolveClassInType(type);
            if (psiClass != null && psiClass.isEnum()
                && !(expr instanceof PsiReferenceExpression refExpr && refExpr.resolve() instanceof PsiEnumConstant)) {
                return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(expr)
                    .descriptionAndTooltip(JavaCompilationErrorLocalize.annotationAttributeNonEnumConstant())
                    .create();
            }

            if (type != null && TypeConversionUtil.areTypesAssignmentCompatible(expectedType, expr)
                || expectedType instanceof PsiArrayType expectedArrayType
                && TypeConversionUtil.areTypesAssignmentCompatible(expectedArrayType.getComponentType(), expr)) {
                return null;
            }

            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(value)
                .descriptionAndTooltip(JavaCompilationErrorLocalize.annotationAttributeIncompatibleType(
                    JavaHighlightUtil.formatType(expectedType),
                    JavaHighlightUtil.formatType(type)
                ))
                .registerFix(QuickFixFactory.getInstance().createSurroundWithQuotesAnnotationParameterValueFix(value, expectedType))
                .create();
        }

        LOG.error("Unknown annotation member value: " + value);
        return null;
    }

    @RequiredReadAction
    public static HighlightInfo.Builder checkDuplicateAnnotations(
        @Nonnull PsiAnnotation annotationToCheck,
        @Nonnull LanguageLevel languageLevel
    ) {
        PsiAnnotationOwner owner = annotationToCheck.getOwner();
        if (owner == null) {
            return null;
        }

        PsiJavaCodeReferenceElement element = annotationToCheck.getNameReferenceElement();
        if (element == null || !(element.resolve() instanceof PsiClass annotationType)) {
            return null;
        }

        PsiClass contained = contained(annotationType);
        String containedElementFQN = contained == null ? null : contained.getQualifiedName();

        if (containedElementFQN != null) {
            String containerName = annotationType.getQualifiedName();
            if (isAnnotationRepeatedTwice(owner, containedElementFQN)) {
                return annotationError(annotationToCheck, JavaCompilationErrorLocalize.annotationContainerWrongPlace(containerName));
            }
        }
        else if (isAnnotationRepeatedTwice(owner, annotationType.getQualifiedName())) {
            if (!languageLevel.isAtLeast(LanguageLevel.JDK_1_8)) {
                return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(element)
                    .descriptionAndTooltip(JavaCompilationErrorLocalize.annotationDuplicate());
            }

            PsiAnnotation metaAnno =
                PsiImplUtil.findAnnotation(annotationType.getModifierList(), CommonClassNames.JAVA_LANG_ANNOTATION_REPEATABLE);
            if (metaAnno == null) {
                LocalizeValue explanation = JavaCompilationErrorLocalize.annotationDuplicateNonRepeatable(annotationType.getQualifiedName());
                return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(element)
                    .descriptionAndTooltip(JavaCompilationErrorLocalize.annotationDuplicateExplained(explanation));
            }

            LocalizeValue explanation = doCheckRepeatableAnnotation(metaAnno);
            if (explanation.isNotEmpty()) {
                return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(element)
                    .descriptionAndTooltip(JavaCompilationErrorLocalize.annotationDuplicateExplained(explanation));
            }

            PsiClass container = getRepeatableContainer(metaAnno);
            if (container != null) {
                PsiAnnotation.TargetType[] targets = AnnotationTargetUtil.getTargetsForLocation(owner);
                PsiAnnotation.TargetType applicable = AnnotationTargetUtil.findAnnotationTarget(container, targets);
                if (applicable == null) {
                    return annotationError(
                        annotationToCheck,
                        JavaCompilationErrorLocalize.annotationContainerNotApplicable(container.getName(), targets[0].getPresentableText())
                    );
                }
            }
        }

        return null;
    }

    // returns contained element
    private static PsiClass contained(PsiClass annotationType) {
        if (!annotationType.isAnnotationType()) {
            return null;
        }
        PsiMethod[] values = annotationType.findMethodsByName("value", false);
        if (values.length != 1) {
            return null;
        }
        if (!(values[0].getReturnType() instanceof PsiArrayType arrayType)) {
            return null;
        }
        if (!(arrayType.getComponentType() instanceof PsiClassType componentClassType)) {
            return null;
        }
        PsiClass contained = componentClassType.resolve();
        if (contained == null || !contained.isAnnotationType()) {
            return null;
        }
        if (PsiImplUtil.findAnnotation(contained.getModifierList(), CommonClassNames.JAVA_LANG_ANNOTATION_REPEATABLE) == null) {
            return null;
        }

        return contained;
    }

    @RequiredReadAction
    private static boolean isAnnotationRepeatedTwice(@Nonnull PsiAnnotationOwner owner, @Nullable String qualifiedName) {
        int count = 0;
        for (PsiAnnotation annotation : owner.getAnnotations()) {
            PsiJavaCodeReferenceElement nameRef = annotation.getNameReferenceElement();
            if (nameRef == null
                || !(nameRef.resolve() instanceof PsiClass psiClass)
                || !Comparing.equal(qualifiedName, psiClass.getQualifiedName())) {
                continue;
            }
            if (++count == 2) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo.Builder checkMissingAttributes(PsiAnnotation annotation) {
        PsiJavaCodeReferenceElement nameRef = annotation.getNameReferenceElement();
        if (nameRef == null) {
            return null;
        }
        PsiClass aClass = (PsiClass) nameRef.resolve();
        if (aClass != null && aClass.isAnnotationType()) {
            Set<String> names = new HashSet<>();
            PsiNameValuePair[] attributes = annotation.getParameterList().getAttributes();
            for (PsiNameValuePair attribute : attributes) {
                String name = attribute.getName();
                if (name != null) {
                    names.add(name);
                }
                else {
                    names.add(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME);
                }
            }

            PsiMethod[] annotationMethods = aClass.getMethods();
            List<String> missed = new ArrayList<>();
            for (PsiMethod method : annotationMethods) {
                if (PsiUtil.isAnnotationMethod(method)) {
                    PsiAnnotationMethod annotationMethod = (PsiAnnotationMethod) method;
                    if (annotationMethod.getDefaultValue() == null && !names.contains(annotationMethod.getName())) {
                        missed.add(annotationMethod.getName());
                    }
                }
            }

            if (!missed.isEmpty()) {
                StringBuffer buff = new StringBuffer("'" + missed.get(0) + "'");
                for (int i = 1; i < missed.size(); i++) {
                    buff.append(", ");
                    buff.append("'").append(missed.get(i)).append("'");
                }

                return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(nameRef)
                    .descriptionAndTooltip(JavaCompilationErrorLocalize.annotationMissingAttribute(buff))
                    .registerFix(
                        QuickFixFactory.getInstance().createAddMissingRequiredAnnotationParametersFix(annotation, annotationMethods, missed)
                    );
            }
        }

        return null;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo.Builder checkConstantExpression(PsiExpression expression) {
        PsiElement parent = expression.getParent();
        if (PsiUtil.isAnnotationMethod(parent) || parent instanceof PsiNameValuePair || parent instanceof PsiArrayInitializerMemberValue) {
            if (!PsiUtil.isConstantExpression(expression)) {
                return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(expression)
                    .descriptionAndTooltip(JavaCompilationErrorLocalize.annotationAttributeNonConstant());
            }
        }

        return null;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo.Builder checkValidAnnotationType(PsiType type, PsiTypeElement typeElement) {
        if (type != null && type.accept(AnnotationReturnTypeVisitor.INSTANCE)) {
            return null;
        }
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
            .range(typeElement)
            .descriptionAndTooltip(JavaCompilationErrorLocalize.annotationMemberInvalidType(type != null ? type.getPresentableText() : "?"));
    }

    private static final ElementPattern<PsiElement> ANY_ANNOTATION_ALLOWED = psiElement().andOr(
        psiElement().withParent(PsiNameValuePair.class),
        psiElement().withParents(PsiArrayInitializerMemberValue.class, PsiNameValuePair.class),
        psiElement().withParents(PsiArrayInitializerMemberValue.class, PsiAnnotationMethod.class),
        psiElement().withParent(PsiAnnotationMethod.class).afterLeaf(PsiKeyword.DEFAULT)
    );

    @Nullable
    @RequiredReadAction
    public static HighlightInfo.Builder checkApplicability(
        @Nonnull PsiAnnotation annotation,
        @Nonnull LanguageLevel level,
        @Nonnull PsiFile file
    ) {
        if (ANY_ANNOTATION_ALLOWED.accepts(annotation)) {
            return null;
        }

        PsiJavaCodeReferenceElement nameRef = annotation.getNameReferenceElement();
        if (nameRef == null) {
            return null;
        }

        PsiAnnotationOwner owner = annotation.getOwner();
        PsiAnnotation.TargetType[] targets = AnnotationTargetUtil.getTargetsForLocation(owner);
        if (owner == null || targets.length == 0) {
            return annotationError(annotation, JavaCompilationErrorLocalize.annotationNotAllowedHere());
        }

        if (!(owner instanceof PsiModifierList)) {
            HighlightInfo.Builder hlBuilder = HighlightUtil.checkFeature(annotation, JavaFeature.TYPE_ANNOTATIONS, level, file);
            if (hlBuilder != null) {
                return hlBuilder;
            }
        }

        PsiAnnotation.TargetType applicable = AnnotationTargetUtil.findAnnotationTarget(annotation, targets);
        if (applicable == PsiAnnotation.TargetType.UNKNOWN) {
            return null;
        }

        if (applicable == null) {
            return annotationError(
                annotation,
                JavaCompilationErrorLocalize.annotationNotApplicable(nameRef.getText(), targets[0].getPresentableText())
            );
        }

        if (applicable == PsiAnnotation.TargetType.TYPE_USE) {
            if (owner instanceof PsiClassReferenceType classRefType) {
                PsiJavaCodeReferenceElement ref = classRefType.getReference();
                HighlightInfo.Builder hlBuilder = checkReferenceTarget(annotation, ref);
                if (hlBuilder != null) {
                    return hlBuilder;
                }
            }
            else if (owner instanceof PsiModifierList modifierList) {
                PsiElement nextElement =
                    PsiTreeUtil.skipSiblingsForward(modifierList, PsiComment.class, PsiWhiteSpace.class, PsiTypeParameterList.class);
                if (nextElement instanceof PsiTypeElement typeElement) {
                    PsiType type = typeElement.getType();
                    if (PsiType.VOID.equals(type)) {
                        return annotationError(annotation, JavaCompilationErrorLocalize.annotationNotAllowedVoid());
                    }
                    if (!(type instanceof PsiPrimitiveType)) {
                        PsiJavaCodeReferenceElement ref = getOutermostReferenceElement(typeElement.getInnermostComponentReferenceElement());
                        HighlightInfo.Builder hlBuilder = checkReferenceTarget(annotation, ref);
                        if (hlBuilder != null) {
                            return hlBuilder;
                        }
                    }
                }
            }
            else if (owner instanceof PsiTypeElement typeElem) {
                PsiElement context = PsiTreeUtil.skipParentsOfType(typeElem, PsiTypeElement.class);
                if (context instanceof PsiClassObjectAccessExpression) {
                    return annotationError(annotation, JavaCompilationErrorLocalize.annotationNotAllowedClass());
                }
            }
        }

        return null;
    }

    @RequiredReadAction
    private static HighlightInfo.Builder annotationError(PsiAnnotation annotation, LocalizeValue message) {
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
            .range(annotation)
            .descriptionAndTooltip(message)
            .registerFix(new DeleteAnnotationAction(annotation));
    }

    @Nullable
    @RequiredReadAction
    private static HighlightInfo.Builder checkReferenceTarget(PsiAnnotation annotation, @Nullable PsiJavaCodeReferenceElement ref) {
        if (ref == null) {
            return null;
        }
        PsiElement refTarget = ref.resolve();
        if (refTarget == null) {
            return null;
        }

        if (!(refTarget instanceof PsiClass)) {
            return annotationError(annotation, JavaCompilationErrorLocalize.annotationNotAllowedRef());
        }
        else if (ref.getParent() instanceof PsiJavaCodeReferenceElement javaCodeRef
            && javaCodeRef.resolve() instanceof PsiMember member && member.isStatic()) {
            return annotationError(annotation, JavaCompilationErrorLocalize.annotationNotAllowedStatic());
        }
        return null;
    }

    @Nullable
    private static PsiJavaCodeReferenceElement getOutermostReferenceElement(@Nullable PsiJavaCodeReferenceElement ref) {
        if (ref == null) {
            return null;
        }

        while (ref.getQualifier() instanceof PsiJavaCodeReferenceElement javaCodeRef) {
            ref = javaCodeRef;
        }
        return ref;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo.Builder checkAnnotationType(PsiAnnotation annotation) {
        PsiJavaCodeReferenceElement nameRefElem = annotation.getNameReferenceElement();
        if (nameRefElem != null && (!(nameRefElem.resolve() instanceof PsiClass annotationClass) || !annotationClass.isAnnotationType())) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(nameRefElem)
                .descriptionAndTooltip(JavaCompilationErrorLocalize.annotationTypeExpected());
        }
        return null;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo.Builder checkCyclicMemberType(PsiTypeElement typeElement, PsiClass aClass) {
        LOG.assertTrue(aClass.isAnnotationType());
        PsiType type = typeElement.getType();
        Set<PsiClass> checked = new HashSet<>();
        if (cyclicDependencies(aClass, type, checked, aClass.getManager())) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(typeElement)
                .descriptionAndTooltip(JavaCompilationErrorLocalize.annotationCyclicElementType());
        }
        return null;
    }

    private static boolean cyclicDependencies(PsiClass aClass, PsiType type, @Nonnull Set<PsiClass> checked, @Nonnull PsiManager manager) {
        PsiClass resolvedClass = PsiUtil.resolveClassInType(type);
        if (resolvedClass != null && resolvedClass.isAnnotationType()) {
            if (aClass == resolvedClass) {
                return true;
            }
            if (!checked.add(resolvedClass) || !manager.isInProject(resolvedClass)) {
                return false;
            }
            PsiMethod[] methods = resolvedClass.getMethods();
            for (PsiMethod method : methods) {
                if (cyclicDependencies(aClass, method.getReturnType(), checked, manager)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo.Builder checkClashesWithSuperMethods(@Nonnull PsiAnnotationMethod psiMethod) {
        PsiIdentifier nameIdentifier = psiMethod.getNameIdentifier();
        if (nameIdentifier != null) {
            PsiMethod[] methods = psiMethod.findDeepestSuperMethods();
            for (PsiMethod method : methods) {
                PsiClass containingClass = method.getContainingClass();
                if (containingClass != null) {
                    String qualifiedName = containingClass.getQualifiedName();
                    if (CommonClassNames.JAVA_LANG_OBJECT.equals(qualifiedName)
                        || CommonClassNames.JAVA_LANG_ANNOTATION_ANNOTATION.equals(qualifiedName)) {
                        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                            .range(nameIdentifier)
                            .descriptionAndTooltip(JavaCompilationErrorLocalize.annotationMemberClash(
                                JavaHighlightUtil.formatMethod(method),
                                HighlightUtil.formatClass(containingClass)
                            ));
                    }
                }
            }
        }
        return null;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkAnnotationDeclaration(PsiElement parent, PsiReferenceList list) {
        if (PsiUtil.isAnnotationMethod(parent)) {
            PsiAnnotationMethod method = (PsiAnnotationMethod) parent;
            if (list == method.getThrowsList()) {
                return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(list)
                    .descriptionAndTooltip(JavaCompilationErrorLocalize.annotationMemberMayNotHaveThrowsList())
                    .create();
            }
        }
        else if (parent instanceof PsiClass annotationClass && annotationClass.isAnnotationType()) {
            if (PsiKeyword.EXTENDS.equals(list.getFirstChild().getText())) {
                return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(list)
                    .descriptionAndTooltip(JavaCompilationErrorLocalize.annotationMayNotHaveExtendsList())
                    .create();
            }
        }
        return null;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkPackageAnnotationContainingFile(PsiPackageStatement statement, PsiFile file) {
        PsiModifierList annotationList = statement.getAnnotationList();
        if (annotationList != null && !PsiJavaPackage.PACKAGE_INFO_FILE.equals(file.getName())) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(annotationList.getTextRange())
                .descriptionAndTooltip(JavaErrorLocalize.invalidPackageAnnotationContainingFile())
                .create();
        }
        return null;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo.Builder checkTargetAnnotationDuplicates(PsiAnnotation annotation) {
        PsiJavaCodeReferenceElement nameRef = annotation.getNameReferenceElement();
        if (nameRef == null) {
            return null;
        }

        if (!(nameRef.resolve() instanceof PsiClass annotationClass)
            || !CommonClassNames.JAVA_LANG_ANNOTATION_TARGET.equals(annotationClass.getQualifiedName())) {
            return null;
        }

        PsiNameValuePair[] attributes = annotation.getParameterList().getAttributes();
        if (attributes.length < 1) {
            return null;
        }
        if (!(attributes[0].getValue() instanceof PsiArrayInitializerMemberValue arrayInitializerMemberValue)) {
            return null;
        }
        PsiAnnotationMemberValue[] arrayInitializers = arrayInitializerMemberValue.getInitializers();
        Set<PsiElement> targets = new HashSet<>();
        for (PsiAnnotationMemberValue initializer : arrayInitializers) {
            if (initializer instanceof PsiReferenceExpression refExpr && refExpr.resolve() instanceof PsiElement target) {
                if (targets.contains(target)) {
                    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                        .range(initializer)
                        .descriptionAndTooltip(JavaCompilationErrorLocalize.annotationRepeatedTarget());
                }
                targets.add(target);
            }
        }
        return null;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo.Builder checkFunctionalInterface(@Nonnull PsiAnnotation annotation, @Nonnull LanguageLevel languageLevel) {
        if (languageLevel.isAtLeast(LanguageLevel.JDK_1_8)
            && CommonClassNames.JAVA_LANG_FUNCTIONAL_INTERFACE.equals(annotation.getQualifiedName())
            && annotation.getOwner() instanceof PsiModifierList modifierList
            && modifierList.getParent() instanceof PsiClass psiClass) {
            LocalizeValue errorMessage = LambdaHighlightingUtil.checkInterfaceFunctional(
                psiClass,
                JavaCompilationErrorLocalize.lambdaNotAFunctionalInterface(psiClass.getName())
            );
            if (errorMessage.isNotEmpty()) {
                return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(annotation)
                    .descriptionAndTooltip(errorMessage);
            }
        }
        return null;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo.Builder checkRepeatableAnnotation(PsiAnnotation annotation) {
        String qualifiedName = annotation.getQualifiedName();
        if (!CommonClassNames.JAVA_LANG_ANNOTATION_REPEATABLE.equals(qualifiedName)) {
            return null;
        }

        LocalizeValue description = doCheckRepeatableAnnotation(annotation);
        if (description.isNotEmpty()) {
            PsiAnnotationMemberValue containerRef = PsiImplUtil.findAttributeValue(annotation, null);
            if (containerRef != null) {
                return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(containerRef)
                    .descriptionAndTooltip(description);
            }
        }

        return null;
    }

    @Nonnull
    @RequiredReadAction
    private static LocalizeValue doCheckRepeatableAnnotation(@Nonnull PsiAnnotation annotation) {
        if (!(annotation.getOwner() instanceof PsiModifierList modifierList)) {
            return LocalizeValue.empty();
        }
        if (!(modifierList.getParent() instanceof PsiClass targetClass) || !targetClass.isAnnotationType()) {
            return LocalizeValue.empty();
        }
        PsiClass container = getRepeatableContainer(annotation);
        if (container == null) {
            return LocalizeValue.empty();
        }

        PsiMethod[] methods = container.findMethodsByName("value", false);
        if (methods.length == 0) {
            return JavaCompilationErrorLocalize.annotationContainerNoValue(container.getQualifiedName());
        }

        if (methods.length == 1) {
            PsiType expected = new PsiImmediateClassType(targetClass, PsiSubstitutor.EMPTY).createArrayType();
            if (!expected.equals(methods[0].getReturnType())) {
                return JavaCompilationErrorLocalize.annotationContainerBadType(container.getQualifiedName(), JavaHighlightUtil.formatType(expected));
            }
        }

        RetentionPolicy targetPolicy = getRetentionPolicy(targetClass);
        if (targetPolicy != null) {
            RetentionPolicy containerPolicy = getRetentionPolicy(container);
            if (containerPolicy != null && targetPolicy.compareTo(containerPolicy) > 0) {
                return JavaCompilationErrorLocalize.annotationContainerLowRetention(container.getQualifiedName(), containerPolicy);
            }
        }

        Set<PsiAnnotation.TargetType> repeatableTargets = AnnotationTargetUtil.getAnnotationTargets(targetClass);
        if (repeatableTargets != null) {
            Set<PsiAnnotation.TargetType> containerTargets = AnnotationTargetUtil.getAnnotationTargets(container);
            if (containerTargets != null && !repeatableTargets.containsAll(containerTargets)) {
                return JavaCompilationErrorLocalize.annotationContainerWideTarget(container.getQualifiedName());
            }
        }

        return LocalizeValue.empty();
    }

    @Nullable
    @RequiredReadAction
    private static PsiClass getRepeatableContainer(@Nonnull PsiAnnotation annotation) {
        if (!(PsiImplUtil.findAttributeValue(annotation, null) instanceof PsiClassObjectAccessExpression containerRef)) {
            return null;
        }
        if (!(containerRef.getOperand().getType() instanceof PsiClassType containerType)) {
            return null;
        }
        PsiClass container = containerType.resolve();
        return container != null && container.isAnnotationType() ? container : null;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkReceiverPlacement(PsiReceiverParameter parameter) {
        PsiElement owner = parameter.getParent().getParent();
        if (owner == null) {
            return null;
        }

        if (!(owner instanceof PsiMethod method)) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(parameter.getIdentifier())
                .descriptionAndTooltip(JavaCompilationErrorLocalize.receiverWrongContext())
                .create();
        }

        if (isStatic(method) || method.isConstructor() && isStatic(method.getContainingClass())) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(parameter.getIdentifier())
                .descriptionAndTooltip(JavaCompilationErrorLocalize.receiverStaticContext())
                .create();
        }

        PsiElement leftNeighbour = PsiTreeUtil.skipSiblingsBackward(parameter, PsiWhiteSpace.class);
        if (leftNeighbour != null && !PsiUtil.isJavaToken(leftNeighbour, JavaTokenType.LPARENTH)) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(parameter.getIdentifier())
                .descriptionAndTooltip(JavaCompilationErrorLocalize.receiverWrongPosition())
                .create();
        }

        return null;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkReceiverType(PsiReceiverParameter parameter) {
        PsiElement owner = parameter.getParent().getParent();
        if (!(owner instanceof PsiMethod method)) {
            return null;
        }

        PsiClass enclosingClass = method.getContainingClass();
        if (method.isConstructor() && enclosingClass != null) {
            enclosingClass = enclosingClass.getContainingClass();
        }

        if (enclosingClass != null && !enclosingClass.equals(PsiUtil.resolveClassInType(parameter.getType()))) {
            PsiElement range = ObjectUtil.notNull(parameter.getTypeElement(), parameter);
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(range)
                .descriptionAndTooltip(JavaCompilationErrorLocalize.receiverTypeMismatch())
                .create();
        }

        PsiThisExpression identifier = parameter.getIdentifier();
        if (enclosingClass != null && !enclosingClass.equals(PsiUtil.resolveClassInType(identifier.getType()))) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(identifier)
                .descriptionAndTooltip(JavaCompilationErrorLocalize.receiverNameMismatch())
                .create();
        }

        return null;
    }

    @RequiredReadAction
    @SuppressWarnings("SimplifiableIfStatement")
    private static boolean isStatic(PsiModifierListOwner owner) {
        if (owner == null) {
            return false;
        }
        if (owner instanceof PsiClass psiClass && ClassUtil.isTopLevelClass(psiClass)) {
            return true;
        }
        return owner.hasModifierProperty(PsiModifier.STATIC);
    }

    @Nullable
    @RequiredReadAction
    public static RetentionPolicy getRetentionPolicy(@Nonnull PsiClass annotation) {
        PsiModifierList modifierList = annotation.getModifierList();
        if (modifierList != null) {
            PsiAnnotation retentionAnno = modifierList.findAnnotation(CommonClassNames.JAVA_LANG_ANNOTATION_RETENTION);
            if (retentionAnno == null) {
                return RetentionPolicy.CLASS;
            }

            if (PsiImplUtil.findAttributeValue(retentionAnno, null) instanceof PsiReference policyRef
                && policyRef.resolve() instanceof PsiEnumConstant enumConst) {
                String name = enumConst.getName();
                try {
                    //noinspection ConstantConditions
                    return Enum.valueOf(RetentionPolicy.class, name);
                }
                catch (Exception e) {
                    LOG.warn("Unknown policy: " + name);
                }
            }
        }

        return null;
    }

    public static class AnnotationReturnTypeVisitor extends PsiTypeVisitor<Boolean> {
        public static final AnnotationReturnTypeVisitor INSTANCE = new AnnotationReturnTypeVisitor();

        @Override
        public Boolean visitType(PsiType type) {
            return Boolean.FALSE;
        }

        @Override
        public Boolean visitPrimitiveType(PsiPrimitiveType primitiveType) {
            return PsiType.VOID.equals(primitiveType) || PsiType.NULL.equals(primitiveType) ? Boolean.FALSE : Boolean.TRUE;
        }

        @Override
        public Boolean visitArrayType(PsiArrayType arrayType) {
            if (arrayType.getArrayDimensions() != 1) {
                return Boolean.FALSE;
            }
            PsiType componentType = arrayType.getComponentType();
            return componentType.accept(this);
        }

        @Override
        public Boolean visitClassType(PsiClassType classType) {
            if (classType.getParameters().length > 0) {
                PsiClassType rawType = classType.rawType();
                return rawType.equalsToText(CommonClassNames.JAVA_LANG_CLASS);
            }

            PsiClass aClass = classType.resolve();
            if (aClass != null && (aClass.isAnnotationType() || aClass.isEnum())) {
                return Boolean.TRUE;
            }

            return classType.equalsToText(CommonClassNames.JAVA_LANG_CLASS) || classType.equalsToText(CommonClassNames.JAVA_LANG_STRING);
        }
    }

    private static class DeleteAnnotationAction implements SyntheticIntentionAction {
        private final PsiAnnotation myAnnotation;

        private DeleteAnnotationAction(PsiAnnotation annotation) {
            myAnnotation = annotation;
        }

        @Nonnull
        @Override
        public LocalizeValue getText() {
            return LocalizeValue.localizeTODO("Remove");
        }

        @Override
        public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
            return true;
        }

        @Override
        @RequiredWriteAction
        public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
            myAnnotation.delete();
        }

        @Override
        public boolean startInWriteAction() {
            return true;
        }
    }
}
