/*
 * Copyright 2013-2026 consulo.io
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
package com.intellij.java.impl.ide.hierarchy.method;

import com.intellij.java.language.psi.PsiMethod;
import consulo.language.psi.PsiElement;
import consulo.util.dataholder.Key;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * What the Java method hierarchy tells the Java actions sitting in its context menu: the method the
 * hierarchy was opened on, and what is selected in the tree right now.
 * <p>
 * {@link #KEY} is answered only by the Java method hierarchy, so an action bound to it disappears rather
 * than misreading a hierarchy another language put on screen.
 *
 * @param baseMethod       the method the hierarchy is rooted at, or null once it has gone invalid
 * @param selectedElements what the selected rows stand for - classes, and the functional expressions the
 *                         hierarchy also shows
 * @author VISTALL
 * @since 2026-09-19
 */
public record JavaMethodHierarchySelection(@Nullable PsiMethod baseMethod, List<PsiElement> selectedElements) {
    public static final Key<JavaMethodHierarchySelection> KEY =
        Key.create("com.intellij.java.impl.ide.hierarchy.method.JavaMethodHierarchySelection");
}
