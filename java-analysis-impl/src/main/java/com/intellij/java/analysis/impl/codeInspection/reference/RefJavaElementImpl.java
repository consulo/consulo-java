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
package com.intellij.java.analysis.impl.codeInspection.reference;

import com.intellij.java.analysis.codeInspection.reference.*;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiFormatUtil;
import com.intellij.java.language.psi.util.PsiFormatUtilBase;
import consulo.annotation.access.RequiredReadAction;
import consulo.component.util.Iconable;
import consulo.java.analysis.impl.localize.JavaInspectionsLocalize;
import consulo.language.editor.impl.inspection.reference.RefElementImpl;
import consulo.language.editor.inspection.reference.RefElement;
import consulo.language.editor.inspection.reference.RefManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.SyntheticElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.ui.image.Image;
import consulo.util.collection.Stack;
import consulo.virtualFileSystem.VirtualFileManager;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @author anna
 * @since 2007-12-20
 */
public abstract class RefJavaElementImpl extends RefElementImpl implements RefJavaElement {
    private Set<RefClass> myOutTypeReferences;
    private static final int ACCESS_MODIFIER_MASK = 0x03;
    private static final int ACCESS_PRIVATE = 0x00;
    private static final int ACCESS_PROTECTED = 0x01;
    private static final int ACCESS_PACKAGE = 0x02;
    private static final int ACCESS_PUBLIC = 0x03;
    private static final int IS_STATIC_MASK = 0x04;
    private static final int IS_FINAL_MASK = 0x08;
    private static final int IS_USES_DEPRECATION_MASK = 0x200;
    private static final int IS_SYNTHETIC_JSP_ELEMENT = 0x400;

    protected RefJavaElementImpl(@Nonnull LocalizeValue name, RefJavaElement owner) {
        super(name.get(), owner);
        String am = owner.getAccessModifier();
        doSetAccessModifier(am);

        boolean synthOwner = owner.isSyntheticJSP();
        if (synthOwner) {
            setSyntheticJSP(true);
        }
    }

    protected RefJavaElementImpl(PsiFile file, RefManager manager) {
        super(file, manager);
    }

    @RequiredReadAction
    protected RefJavaElementImpl(PsiModifierListOwner elem, RefManager manager) {
        super(getName(elem).get(), elem, manager);

        setAccessModifier(RefJavaUtil.getInstance().getAccessModifier(elem));
        boolean isSynth = elem instanceof PsiMethod && elem instanceof SyntheticElement || elem instanceof PsiSyntheticClass;
        if (isSynth) {
            setSyntheticJSP(true);
        }

        setIsStatic(elem.hasModifierProperty(PsiModifier.STATIC));
        setIsFinal(elem.hasModifierProperty(PsiModifier.FINAL));
    }

    @Override
    @Nonnull
    public Collection<RefClass> getOutTypeReferences() {
        if (myOutTypeReferences == null) {
            return Collections.emptySet();
        }
        return myOutTypeReferences;
    }

    public void addOutTypeReference(RefClass refClass) {
        if (myOutTypeReferences == null) {
            myOutTypeReferences = new HashSet<>();
        }
        myOutTypeReferences.add(refClass);
    }

    @RequiredReadAction
    public static LocalizeValue getName(@Nonnull PsiElement element) {
        if (element instanceof PsiAnonymousClass psiAnonymousClass) {
            String name = psiAnonymousClass.getBaseClassType().resolve() instanceof PsiClass psiBaseClass ? psiBaseClass.getName() : null;
            return name == null
                ? JavaInspectionsLocalize.inspectionReferenceAnonymousClass()
                : JavaInspectionsLocalize.inspectionReferenceAnonymousName(name);
        }

        if (element instanceof PsiSyntheticClass jspClass) {
            PsiFile jspxFile = jspClass.getContainingFile();
            return JavaInspectionsLocalize.inspectionReferenceJspSyntheticClassName(jspxFile.getName());
        }

        if (element instanceof PsiMethod method) {
            if (element instanceof SyntheticElement) {
                return JavaInspectionsLocalize.inspectionReferenceJspHolderMethodAnonymousName();
            }

            return LocalizeValue.of(PsiFormatUtil.formatMethod(
                method,
                PsiSubstitutor.EMPTY,
                PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS,
                PsiFormatUtilBase.SHOW_TYPE
            ));
        }

        if (element instanceof PsiLambdaExpression || element instanceof PsiMethodReferenceExpression) {
            boolean isMethodReference = element instanceof PsiMethodReferenceExpression;
            PsiElement parentDeclaration = PsiTreeUtil.getParentOfType(
                element,
                PsiMethod.class,
                PsiClass.class,
                PsiLambdaExpression.class,
                PsiField.class
            );
            String name = parentDeclaration instanceof PsiNamedElement namedDeclaration ? namedDeclaration.getName() : null;
            if (name != null) {
                return isMethodReference
                    ? JavaInspectionsLocalize.inspectionReferenceMethodReferenceName(name)
                    : JavaInspectionsLocalize.inspectionReferenceLambdaName(name);
            }
            return isMethodReference
                ? JavaInspectionsLocalize.inspectionReferenceDefaultMethodReferenceName()
                : JavaInspectionsLocalize.inspectionReferenceDefaultLambdaName();
        }

        String name = element instanceof PsiNamedElement namedElement ? namedElement.getName() : null;
        return name == null ? JavaInspectionsLocalize.inspectionReferenceAnonymous() : LocalizeValue.of(name);
    }

    @Override
    public boolean isFinal() {
        return checkFlag(IS_FINAL_MASK);
    }

    @Override
    public boolean isStatic() {
        return checkFlag(IS_STATIC_MASK);
    }

    public void setIsStatic(boolean isStatic) {
        setFlag(isStatic, IS_STATIC_MASK);
    }

    @Override
    public boolean isUsesDeprecatedApi() {
        return checkFlag(IS_USES_DEPRECATION_MASK);
    }

    public void setUsesDeprecatedApi(boolean usesDeprecatedApi) {
        setFlag(usesDeprecatedApi, IS_USES_DEPRECATION_MASK);
    }

    public void setIsFinal(boolean isFinal) {
        setFlag(isFinal, IS_FINAL_MASK);
    }

    @Override
    public void setReachable(boolean reachable) {
        setFlag(reachable, IS_REACHABLE_MASK);
    }

    @Override
    public boolean isSyntheticJSP() {
        return checkFlag(IS_SYNTHETIC_JSP_ELEMENT);
    }

    public void setSyntheticJSP(boolean b) {
        setFlag(b, IS_SYNTHETIC_JSP_ELEMENT);
    }

    @Override
    @Nullable
    public String getAccessModifier() {
        long accessId = myFlags & ACCESS_MODIFIER_MASK;
        if (accessId == ACCESS_PRIVATE) {
            return PsiModifier.PRIVATE;
        }
        if (accessId == ACCESS_PUBLIC) {
            return PsiModifier.PUBLIC;
        }
        if (accessId == ACCESS_PACKAGE) {
            return PsiModifier.PACKAGE_LOCAL;
        }
        return PsiModifier.PROTECTED;
    }

    public void setAccessModifier(String am) {
        doSetAccessModifier(am);
    }

    private void doSetAccessModifier(String am) {
        int accessId = switch (am) {
            case PsiModifier.PRIVATE -> ACCESS_PRIVATE;
            case PsiModifier.PUBLIC -> ACCESS_PUBLIC;
            case PsiModifier.PACKAGE_LOCAL -> ACCESS_PACKAGE;
            default -> ACCESS_PROTECTED;
        };

        myFlags = myFlags & ~0x3 | accessId;
    }

    public boolean isSuspiciousRecursive() {
        return isCalledOnlyFrom(this, new Stack<>());
    }

    private boolean isCalledOnlyFrom(RefJavaElement refElement, Stack<RefJavaElement> callStack) {
        if (callStack.contains(this)) {
            return refElement == this;
        }
        if (getInReferences().isEmpty()) {
            return false;
        }

        if (refElement instanceof RefMethod refMethod) {
            for (RefMethod refSuper : refMethod.getSuperMethods()) {
                if (!refSuper.getInReferences().isEmpty()) {
                    return false;
                }
            }
            if (refMethod.isConstructor()) {
                boolean unreachable = true;
                for (RefElement refOut : refMethod.getOutReferences()) {
                    unreachable &= !refOut.isReachable();
                }
                if (unreachable) {
                    return true;
                }
            }
        }

        callStack.push(this);
        for (RefElement refCaller : getInReferences()) {
            if (!((RefElementImpl) refCaller).isSuspicious() || !((RefJavaElementImpl) refCaller).isCalledOnlyFrom(refElement, callStack)) {
                callStack.pop();
                return false;
            }
        }

        callStack.pop();
        return true;
    }

    public void addReference(
        RefElement refWhat,
        PsiElement psiWhat,
        PsiElement psiFrom,
        boolean forWriting,
        boolean forReading,
        PsiReferenceExpression expression
    ) {
        if (refWhat != null) {
            if (refWhat instanceof RefParameter refParameter) {
                if (forWriting) {
                    refParameter.parameterReferenced(true);
                }
                // TODO: else if?
                if (forReading) {
                    refParameter.parameterReferenced(false);
                }
            }
            addOutReference(refWhat);
            ((RefJavaElementImpl) refWhat).markReferenced(this, psiFrom, psiWhat, forWriting, forReading, expression);
        }
        else if (psiWhat instanceof PsiMethod method) {
            PsiClass containingClass = method.getContainingClass();
            if (containingClass != null && containingClass.isEnum() && "values".equals(method.getName())) {
                for (PsiField enumConstant : containingClass.getFields()) {
                    if (enumConstant instanceof PsiEnumConstant) {
                        RefJavaElementImpl enumConstantReference = (RefJavaElementImpl) getRefManager().getReference(enumConstant);
                        if (enumConstantReference != null) {
                            addOutReference(enumConstantReference);
                            enumConstantReference.markReferenced(this, psiFrom, enumConstant, false, true, expression);
                        }
                    }
                }
            }
        }
    }

    protected void markReferenced(
        RefElementImpl refFrom,
        PsiElement psiFrom,
        PsiElement psiWhat,
        boolean forWriting,
        boolean forReading,
        PsiReferenceExpression expressionFrom
    ) {
        addInReference(refFrom);
        getRefManager().fireNodeMarkedReferenced(this, refFrom, false, forReading, forWriting);
    }

    protected RefJavaManager getRefJavaManager() {
        return getRefManager().getExtension(RefJavaManager.MANAGER);
    }

    @Override
    public void referenceRemoved() {
        super.referenceRemoved();
        if (isEntry()) {
            getRefJavaManager().getEntryPointsManager().removeEntryPoint(this);
        }
    }

    @Override
    @RequiredReadAction
    public Image getIcon(boolean expanded) {
        if (isSyntheticJSP()) {
            PsiElement element = getPsiElement();
            if (element != null && element.isValid()) {
                return VirtualFileManager.getInstance().getFileIcon(
                    element.getContainingFile().getVirtualFile(),
                    element.getProject(),
                    Iconable.ICON_FLAG_VISIBILITY | Iconable.ICON_FLAG_READ_STATUS
                );
            }
        }
        return super.getIcon(expanded);
    }
}
