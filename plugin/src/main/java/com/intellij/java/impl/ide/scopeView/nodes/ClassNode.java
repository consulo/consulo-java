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
package com.intellij.java.impl.ide.scopeView.nodes;

import com.intellij.java.impl.ide.projectView.impl.nodes.ClassTreeNode;
import com.intellij.java.language.impl.psi.presentation.java.ClassPresentationUtil;
import com.intellij.java.language.psi.PsiClass;
import consulo.annotation.access.RequiredReadAction;
import consulo.ide.impl.idea.ide.scopeView.nodes.BasePsiNode;

/**
 * @author anna
 * @since 2006-01-30
 */
public class ClassNode extends BasePsiNode<PsiClass> implements Comparable<ClassNode> {
    @RequiredReadAction
    public ClassNode(PsiClass aClass) {
        super(aClass);
    }

    @Override
    @RequiredReadAction
    public String toString() {
        PsiClass aClass = (PsiClass) getPsiElement();
        return aClass != null && aClass.isValid() ? ClassPresentationUtil.getNameForClass(aClass, false).get() : "";
    }

    @Override
    @RequiredReadAction
    public boolean isDeprecated() {
        PsiClass psiClass = (PsiClass) getPsiElement();
        return psiClass != null && psiClass.isDeprecated();
    }

    @Override
    @RequiredReadAction
    public int compareTo(ClassNode o) {
        int comparison = ClassTreeNode.getClassPosition((PsiClass) getPsiElement()) -
            ClassTreeNode.getClassPosition((PsiClass) o.getPsiElement());
        if (comparison == 0) {
            return toString().compareToIgnoreCase(o.toString());
        }
        return comparison;
    }
}
