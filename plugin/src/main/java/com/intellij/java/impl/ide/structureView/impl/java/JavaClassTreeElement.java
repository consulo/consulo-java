// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.impl.ide.structureView.impl.java;

import com.intellij.java.language.impl.psi.impl.PsiImplUtil;
import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.fileEditor.structureView.StructureViewTreeElement;
import consulo.language.impl.psi.LightElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.localize.LocalizeValue;

import java.util.*;

/**
 * @author Konstantin Bulenkov
 */
public class JavaClassTreeElement extends JavaClassTreeElementBase<PsiClass> {
    public JavaClassTreeElement(PsiClass cls, boolean inherited) {
        super(inherited, cls);
    }

    /**
     * @noinspection unused
     * @deprecated use {@link #JavaClassTreeElement(PsiClass, boolean)}
     */
    @Deprecated
    public JavaClassTreeElement(PsiClass cls, boolean inherited, Set<PsiClass> parents) {
        this(cls, inherited);
    }

    @Override
    @RequiredReadAction
    public Collection<StructureViewTreeElement> getChildrenBase() {
        return getClassChildren(getElement());
    }

    @RequiredReadAction
    static Collection<StructureViewTreeElement> getClassChildren(PsiClass aClass) {
        if (aClass == null) {
            return Collections.emptyList();
        }

        Set<PsiElement> members = getOwnChildren(aClass);
        List<StructureViewTreeElement> children = new ArrayList<>(members.size());

        for (PsiElement child : members) {
            if (!child.isValid()) {
                continue;
            }
            if (child instanceof PsiClass psiClass) {
                children.add(new JavaClassTreeElement(psiClass, false));
            }
            else if (child instanceof PsiField field) {
                children.add(new PsiFieldTreeElement(field, false));
            }
            else if (child instanceof PsiMethod method) {
                children.add(new PsiMethodTreeElement(method, false));
            }
            else if (child instanceof PsiClassInitializer classInitializer) {
                children.add(new ClassInitializerTreeElement(classInitializer));
            }
        }
        PsiRecordHeader header = aClass.getRecordHeader();
        if (header != null) {
            for (PsiRecordComponent recordComponent : header.getRecordComponents()) {
                children.add(new JavaRecordComponentTreeElement(recordComponent, false));
            }
        }
        return children;
    }

    static LinkedHashSet<PsiElement> getOwnChildren(PsiClass aClass) {
        LinkedHashSet<PsiElement> members = new LinkedHashSet<>();
        addPhysicalElements(aClass.getFields(), members, aClass);
        addPhysicalElements(aClass.getMethods(), members, aClass);
        addPhysicalElements(aClass.getInnerClasses(), members, aClass);
        addPhysicalElements(aClass.getInitializers(), members, aClass);
        return members;
    }

    private static void addPhysicalElements(PsiMember[] elements, Collection<? super PsiElement> to, PsiClass aClass) {
        for (PsiMember element : elements) {
            PsiElement mirror = PsiImplUtil.handleMirror(element);
            if (mirror instanceof LightElement) {
                continue;
            }
            if (mirror instanceof PsiMember && aClass.equals(((PsiMember) mirror).getContainingClass())) {
                to.add(mirror);
            }
        }
    }

    @Override
    @RequiredReadAction
    public LocalizeValue getPresentableText() {
        PsiClass o = getElement();
        return o == null ? LocalizeValue.empty() : LocalizeValue.ofNullable(o.getName());
    }

    @Override
    @RequiredReadAction
    public boolean isPublic() {
        PsiClass o = getElement();
        return o != null && o.getParent() instanceof PsiFile || super.isPublic();
    }
}
