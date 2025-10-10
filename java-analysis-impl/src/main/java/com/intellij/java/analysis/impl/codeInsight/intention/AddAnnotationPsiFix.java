// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInsight.intention;

import com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis.AnnotationsHighlightUtil;
import com.intellij.java.language.codeInsight.AnnotationTargetUtil;
import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.codeInsight.ExternalAnnotationsManager;
import com.intellij.java.language.codeInsight.NullableNotNullManager;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.util.JavaElementKind;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.java.analysis.localize.JavaAnalysisLocalize;
import consulo.language.editor.WriteCommandAction;
import consulo.language.editor.inspection.LocalQuickFixOnPsiElement;
import consulo.language.editor.intention.BaseIntentionAction;
import consulo.language.editor.util.LanguageUndoUtil;
import consulo.language.impl.psi.LightElement;
import consulo.language.psi.*;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.ObjectUtil;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import one.util.streamex.StreamEx;

import java.lang.annotation.RetentionPolicy;
import java.util.List;

import static com.intellij.java.language.codeInsight.AnnotationUtil.CHECK_EXTERNAL;
import static com.intellij.java.language.codeInsight.AnnotationUtil.CHECK_TYPE;

public class AddAnnotationPsiFix extends LocalQuickFixOnPsiElement {
    protected final String myAnnotation;
    final String[] myAnnotationsToRemove;
    @SafeFieldForPreview
    final PsiNameValuePair[] myPairs; // not used when registering local quick fix
    protected final LocalizeValue myText;
    private final ExternalAnnotationsManager.AnnotationPlace myAnnotationPlace;

    @RequiredReadAction
    public AddAnnotationPsiFix(
        @Nonnull String fqn,
        @Nonnull PsiModifierListOwner modifierListOwner,
        @Nonnull String... annotationsToRemove
    ) {
        this(fqn, modifierListOwner, PsiNameValuePair.EMPTY_ARRAY, annotationsToRemove);
    }

    @RequiredReadAction
    public AddAnnotationPsiFix(
        @Nonnull String fqn,
        @Nonnull PsiModifierListOwner modifierListOwner,
        @Nonnull PsiNameValuePair[] values,
        @Nonnull String... annotationsToRemove
    ) {
        super(modifierListOwner);
        myAnnotation = fqn;
        ObjectUtil.assertAllElementsNotNull(values);
        myPairs = values;
        ObjectUtil.assertAllElementsNotNull(annotationsToRemove);
        myAnnotationsToRemove = annotationsToRemove;
        myText = calcText(modifierListOwner, myAnnotation);
        myAnnotationPlace = choosePlace(modifierListOwner);
    }

    @RequiredReadAction
    public static LocalizeValue calcText(PsiModifierListOwner modifierListOwner, @Nullable String annotation) {
        String shortName = annotation == null ? null : annotation.substring(annotation.lastIndexOf('.') + 1);
        if (modifierListOwner instanceof PsiNamedElement namedElement) {
            String name = namedElement.getName();
            if (name != null) {
                JavaElementKind type = JavaElementKind.fromElement(modifierListOwner).lessDescriptive();
                return shortName == null
                    ? JavaAnalysisLocalize.inspectionI18nQuickfixAnnotateElement(type.object(), name)
                    : JavaAnalysisLocalize.inspectionI18nQuickfixAnnotateElementAs(type.object(), name, shortName);
            }
        }
        return shortName == null
            ? JavaAnalysisLocalize.inspectionI18nQuickfixAnnotate()
            : JavaAnalysisLocalize.inspectionI18nQuickfixAnnotateAs(shortName);
    }

    @Nullable
    @RequiredReadAction
    public static PsiModifierListOwner getContainer(PsiFile file, int offset) {
        return getContainer(file, offset, false);
    }

    @Nullable
    @RequiredReadAction
    public static PsiModifierListOwner getContainer(PsiFile file, int offset, boolean availableOnReference) {
        PsiReference reference = availableOnReference ? file.findReferenceAt(offset) : null;
        if (reference != null) {
            PsiElement target = reference.resolve();
            if (target instanceof PsiMember member) {
                return member;
            }
        }

        PsiElement element = file.findElementAt(offset);

        PsiModifierListOwner listOwner = PsiTreeUtil.getParentOfType(element, PsiModifierListOwner.class, false);
        if (listOwner instanceof PsiParameter) {
            return listOwner;
        }

        if (listOwner instanceof PsiNameIdentifierOwner nameIdentifierOwner) {
            PsiElement id = nameIdentifierOwner.getNameIdentifier();
            if (id != null && id.getTextRange().containsOffset(offset)) { // Groovy methods will pass this check as well
                return listOwner;
            }
        }

        return null;
    }

    @Nonnull
    @Override
    public LocalizeValue getText() {
        return myText;
    }

    @Override
    @RequiredReadAction
    public boolean isAvailable(
        @Nonnull Project project,
        @Nonnull PsiFile file,
        @Nonnull PsiElement startElement,
        @Nonnull PsiElement endElement
    ) {
        return isAvailable((PsiModifierListOwner)startElement, myAnnotation);
    }

    @RequiredReadAction
    public static boolean isAvailable(@Nonnull PsiModifierListOwner modifierListOwner, @Nonnull String annotationFQN) {
        if (!modifierListOwner.isValid()) {
            return false;
        }
        if (!PsiUtil.isLanguageLevel5OrHigher(modifierListOwner)) {
            return false;
        }

        if (modifierListOwner instanceof PsiParameter parameter && parameter.getTypeElement() == null) {
            if (modifierListOwner.getParent() instanceof PsiParameterList parameterList &&
                parameterList.getParent() instanceof PsiLambdaExpression lambda) {
                // Lambda parameter without type cannot be annotated. Check if we can specify types
                //noinspection SimplifiableIfStatement
                if (PsiUtil.isLanguageLevel11OrHigher(modifierListOwner)) {
                    return true;
                }
                return LambdaUtil.createLambdaParameterListWithFormalTypes(lambda.getFunctionalInterfaceType(), lambda, false) != null;
            }
            return false;
        }
        // e.g. PsiTypeParameterImpl doesn't have modifier list
        PsiModifierList modifierList = modifierListOwner.getModifierList();
        return modifierList != null
            && !(modifierList instanceof LightElement)
            && !(modifierListOwner instanceof LightElement)
            && !AnnotationUtil.isAnnotated(modifierListOwner, annotationFQN, CHECK_EXTERNAL | CHECK_TYPE);
    }

    @Override
    public boolean startInWriteAction() {
        return myAnnotationPlace == ExternalAnnotationsManager.AnnotationPlace.IN_CODE;
    }

    @Override
    @RequiredUIAccess
    public void invoke(
        @Nonnull Project project,
        @Nonnull PsiFile file,
        @Nonnull PsiElement startElement,
        @Nonnull PsiElement endElement
    ) {
        PsiModifierListOwner myModifierListOwner = (PsiModifierListOwner)startElement;

        PsiAnnotationOwner target = AnnotationTargetUtil.getTarget(myModifierListOwner, myAnnotation);
        if (target == null || ContainerUtil.exists(target.getApplicableAnnotations(), anno -> anno.hasQualifiedName(myAnnotation))) {
            return;
        }
        ExternalAnnotationsManager annotationsManager = ExternalAnnotationsManager.getInstance(project);
        ExternalAnnotationsManager.AnnotationPlace place = myAnnotationPlace == ExternalAnnotationsManager.AnnotationPlace.NEED_ASK_USER
            ? annotationsManager.chooseAnnotationsPlace(myModifierListOwner)
            : myAnnotationPlace;
        switch (place) {
            case NOWHERE:
                return;
            case EXTERNAL:
                for (String fqn : myAnnotationsToRemove) {
                    annotationsManager.deannotate(myModifierListOwner, fqn);
                }
                try {
                    annotationsManager.annotateExternally(myModifierListOwner, myAnnotation, file, myPairs);
                }
                catch (ExternalAnnotationsManager.CanceledConfigurationException ignored) {
                }
                break;
            case IN_CODE:
                PsiFile containingFile = myModifierListOwner.getContainingFile();
                Runnable command = () -> {
                    removePhysicalAnnotations(myModifierListOwner, myAnnotationsToRemove);

                    PsiAnnotation inserted = addPhysicalAnnotationTo(myAnnotation, myPairs, target);
                    JavaCodeStyleManager.getInstance(project).shortenClassReferences(inserted);
                };

                if (!containingFile.isPhysical()) {
                    command.run();
                }
                else {
                    WriteCommandAction.runWriteCommandAction(project, null, null, command, containingFile);
                }

                if (containingFile != file) {
                    LanguageUndoUtil.markPsiFileForUndo(file);
                }
                break;
        }
    }

    @Nonnull
    @RequiredReadAction
    private ExternalAnnotationsManager.AnnotationPlace choosePlace(@Nonnull PsiModifierListOwner modifierListOwner) {
        Project project = modifierListOwner.getProject();
        ExternalAnnotationsManager annotationsManager = ExternalAnnotationsManager.getInstance(project);
        PsiClass aClass = JavaPsiFacade.getInstance(project).findClass(myAnnotation, modifierListOwner.getResolveScope());
        if (aClass != null && BaseIntentionAction.canModify(modifierListOwner)) {
            if (AnnotationsHighlightUtil.getRetentionPolicy(aClass) == RetentionPolicy.RUNTIME) {
                return ExternalAnnotationsManager.AnnotationPlace.IN_CODE;
            }
            if (!CommonClassNames.DEFAULT_PACKAGE.equals(StringUtil.getPackageName(myAnnotation))) {
                PsiClass resolvedBySimpleName = JavaPsiFacade.getInstance(project).getResolveHelper()
                    .resolveReferencedClass(StringUtil.getShortName(myAnnotation), modifierListOwner);
                if (resolvedBySimpleName != null && resolvedBySimpleName.getManager().areElementsEquivalent(resolvedBySimpleName, aClass)) {
                    // if class is already imported in current file
                    return ExternalAnnotationsManager.AnnotationPlace.IN_CODE;
                }
            }
        }
        return annotationsManager.chooseAnnotationsPlaceNoUi(modifierListOwner);
    }

    /**
     * @deprecated use {@link #addPhysicalAnnotationIfAbsent(String, PsiNameValuePair[], PsiAnnotationOwner)}
     */
    @Deprecated
    //@ApiStatus.ScheduledForRemoval(inVersion = "2020.3")
    @RequiredWriteAction
    public static PsiAnnotation addPhysicalAnnotation(String fqn, PsiNameValuePair[] pairs, PsiModifierList modifierList) {
        return addPhysicalAnnotationTo(fqn, pairs, modifierList);
    }

    /**
     * Add new physical (non-external) annotation to the annotation owner. Annotation will not be added if it already exists
     * on the same annotation owner (externally or explicitly) or if there's a {@link PsiTypeElement} that follows the owner,
     * and its innermost component type has the annotation with the same fully-qualified name.
     * E.g. the method like {@code java.lang.@Foo String[] getStringArray()} will not be annotated with another {@code @Foo}
     * annotation.
     *
     * @param fqn   fully-qualified annotation name
     * @param pairs name/value pairs for the new annotation (not changed by this method,
     *              could be result of {@link PsiAnnotationParameterList#getAttributes()} of existing annotation).
     * @param owner an owner object to add the annotation to ({@link PsiModifierList} or {@link PsiType}).
     * @return added physical annotation; null if annotation already exists (in this case, no changes are performed)
     */
    @Nullable
    @RequiredWriteAction
    public static PsiAnnotation addPhysicalAnnotationIfAbsent(
        @Nonnull String fqn,
        @Nonnull PsiNameValuePair[] pairs,
        @Nonnull PsiAnnotationOwner owner
    ) {
        if (owner.hasAnnotation(fqn)) {
            return null;
        }
        if (owner instanceof PsiModifierList modifierList && modifierList.getParent() instanceof PsiModifierListOwner modListOwner) {
            if (ExternalAnnotationsManager.getInstance(modListOwner.getProject()).findExternalAnnotation(modListOwner, fqn) != null) {
                return null;
            }
            PsiTypeElement typeElement = modListOwner instanceof PsiMethod method
                ? method.getReturnTypeElement()
                : modListOwner instanceof PsiVariable variable
                ? variable.getTypeElement()
                : null;
            while (typeElement != null && typeElement.getType() instanceof PsiArrayType) {
                typeElement = PsiTreeUtil.getChildOfType(typeElement, PsiTypeElement.class);
            }
            if (typeElement != null && typeElement.getType().hasAnnotation(fqn)) {
                return null;
            }
        }
        return addPhysicalAnnotationTo(fqn, pairs, owner);
    }

    @RequiredWriteAction
    public static PsiAnnotation addPhysicalAnnotationTo(String fqn, PsiNameValuePair[] pairs, PsiAnnotationOwner owner) {
        owner = expandParameterIfNecessary(owner);
        PsiAnnotation inserted;
        try {
            inserted = owner.addAnnotation(fqn);
        }
        catch (UnsupportedOperationException | IncorrectOperationException e) {
            String message = "Cannot add annotation to " + owner.getClass();
            if (owner instanceof PsiElement psiElement) {
                StreamEx.iterate(
                        psiElement.getParent(),
                        p -> p != null && !(p instanceof PsiFileSystemItem),
                        PsiElement::getParent
                    )
                    .map(p -> p.getClass().getName()).toList();
                message += "; parents: " + message;
            }
            throw new RuntimeException(message, e);
        }
        for (PsiNameValuePair pair : pairs) {
            inserted.setDeclaredAttributeValue(pair.getName(), pair.getValue());
        }
        return inserted;
    }

    @RequiredWriteAction
    private static PsiAnnotationOwner expandParameterIfNecessary(PsiAnnotationOwner owner) {
        if (owner instanceof PsiModifierList modifierList) {
            PsiParameter parameter = ObjectUtil.tryCast(modifierList.getParent(), PsiParameter.class);
            if (parameter != null && parameter.getTypeElement() == null) {
                PsiParameterList list = ObjectUtil.tryCast(parameter.getParent(), PsiParameterList.class);
                if (list != null && list.getParent() instanceof PsiLambdaExpression) {
                    PsiParameter[] parameters = list.getParameters();
                    int index = ArrayUtil.indexOf(parameters, parameter);
                    PsiParameterList newList;
                    if (PsiUtil.isLanguageLevel11OrHigher(list)) {
                        String newListText = StreamEx.of(parameters)
                            .map(p -> PsiKeyword.VAR + " " + p.getName()).joining(",", "(", ")");
                        newList = ((PsiLambdaExpression)JavaPsiFacade.getElementFactory(list.getProject())
                            .createExpressionFromText(newListText + " -> {}", null))
                            .getParameterList();
                        newList = (PsiParameterList)new CommentTracker().replaceAndRestoreComments(list, newList);
                    }
                    else {
                        newList = LambdaUtil.specifyLambdaParameterTypes((PsiLambdaExpression)list.getParent());
                    }
                    if (newList != null) {
                        list = newList;
                        parameter = list.getParameter(index);
                        LOG.assertTrue(parameter != null);
                        owner = parameter.getModifierList();
                        LOG.assertTrue(owner != null);
                    }
                }
            }
        }
        return owner;
    }

    @RequiredWriteAction
    public static void removePhysicalAnnotations(@Nonnull PsiModifierListOwner owner, @Nonnull String... fqns) {
        for (String fqn : fqns) {
            PsiAnnotation annotation = AnnotationUtil.findAnnotation(owner, true, fqn);
            if (annotation != null && !AnnotationUtil.isInferredAnnotation(annotation)) {
                new CommentTracker().deleteAndRestoreComments(annotation);
            }
        }
    }

    @Nonnull
    protected String[] getAnnotationsToRemove() {
        return myAnnotationsToRemove;
    }

    public static boolean isNullabilityAnnotationApplicable(@Nonnull PsiModifierListOwner owner) {
        if (owner instanceof PsiMethod method) {
            PsiType returnType = method.getReturnType();
            return returnType != null && !(returnType instanceof PsiPrimitiveType);
        }
        return !(owner instanceof PsiClass);
    }

    /**
     * Creates a fix which will add default "Nullable" annotation to the given element.
     *
     * @param owner an element to add the annotation
     * @return newly created fix or null if adding nullability annotation is impossible for the specified element.
     */
    @Nullable
    @RequiredReadAction
    public static AddAnnotationPsiFix createAddNullableFix(PsiModifierListOwner owner) {
        NullableNotNullManager manager = NullableNotNullManager.getInstance(owner.getProject());
        return createAddNullableNotNullFix(owner, manager.getDefaultNullable(), manager.getNotNulls());
    }

    /**
     * Creates a fix which will add default "NotNull" annotation to the given element.
     *
     * @param owner an element to add the annotation
     * @return newly created fix or null if adding nullability annotation is impossible for the specified element.
     */
    @Nullable
    @RequiredReadAction
    public static AddAnnotationPsiFix createAddNotNullFix(PsiModifierListOwner owner) {
        NullableNotNullManager manager = NullableNotNullManager.getInstance(owner.getProject());
        return createAddNullableNotNullFix(owner, manager.getDefaultNotNull(), manager.getNullables());
    }

    @Nullable
    @RequiredReadAction
    private static AddAnnotationPsiFix createAddNullableNotNullFix(
        PsiModifierListOwner owner, String annotationToAdd,
        List<String> annotationsToRemove
    ) {
        return !isNullabilityAnnotationApplicable(owner)
            ? null
            : new AddAnnotationPsiFix(annotationToAdd, owner, ArrayUtil.toStringArray(annotationsToRemove));
    }
}
