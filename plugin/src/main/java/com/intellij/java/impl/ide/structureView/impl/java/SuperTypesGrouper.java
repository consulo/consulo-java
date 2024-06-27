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
package com.intellij.java.impl.ide.structureView.impl.java;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiModifier;
import consulo.application.AllIcons;
import consulo.fileEditor.structureView.tree.*;
import consulo.platform.base.localize.IdeLocalize;
import consulo.project.ui.view.tree.AbstractTreeNode;
import consulo.util.collection.ArrayUtil;
import consulo.util.dataholder.Key;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class SuperTypesGrouper implements Grouper {
  public static final Key<WeakReference<PsiMethod>> SUPER_METHOD_KEY = Key.create("StructureTreeBuilder.SUPER_METHOD_KEY");
  @NonNls
  public static final String ID = "SHOW_INTERFACES";

  @Nonnull
  public Collection<Group> group(final Object parent, Collection<TreeElement> children) {
    if (isParentGrouped((AbstractTreeNode) parent)) return Collections.emptyList();
    Map<Group, SuperTypeGroup> groups = new HashMap<>();

    for (TreeElement child : children) {
      if (child instanceof PsiMethodTreeElement element) {
        PsiMethod method = element.getMethod();
        if (element.isInherited()) {
          PsiClass groupClass = method.getContainingClass();
          final SuperTypeGroup group = getOrCreateGroup(groupClass, SuperTypeGroup.OwnershipType.INHERITS, groups);
          group.addMethod(child);
        } else {
          PsiMethod[] superMethods = method.findSuperMethods();

          if (superMethods.length > 0) {
            //prefer interface, if there are any
            for (int i = 1; i < superMethods.length; i++) {
              PsiMethod superMethod = superMethods[i];
              PsiClass containingClass = superMethod.getContainingClass();
              if (containingClass != null && containingClass.isInterface()) {
                ArrayUtil.swap(superMethods, 0, i);
                break;
              }
            }

            PsiMethod superMethod = superMethods[0];
            method.putUserData(SUPER_METHOD_KEY, new WeakReference<>(superMethod));
            PsiClass groupClass = superMethod.getContainingClass();
            boolean overrides = methodOverridesSuper(method, superMethod);
            final SuperTypeGroup.OwnershipType ownershipType =
                overrides ? SuperTypeGroup.OwnershipType.OVERRIDES : SuperTypeGroup.OwnershipType.IMPLEMENTS;
            SuperTypeGroup group = getOrCreateGroup(groupClass, ownershipType, groups);
            group.addMethod(child);
          }
        }
      }
    }
    return groups.keySet();
  }

  private static SuperTypeGroup getOrCreateGroup(final PsiClass groupClass, final SuperTypeGroup.OwnershipType ownershipType, final Map<Group, SuperTypeGroup> groups) {
    SuperTypeGroup superTypeGroup =
        new SuperTypeGroup(groupClass, ownershipType);
    SuperTypeGroup existing = groups.get(superTypeGroup);
    if (existing == null) {
      groups.put(superTypeGroup, superTypeGroup);
      existing = superTypeGroup;
    }
    return existing;
  }

  private static boolean isParentGrouped(AbstractTreeNode parent) {
    while (parent != null) {
      if (parent.getValue() instanceof SuperTypeGroup) return true;
      parent = (AbstractTreeNode) parent.getParent();
    }
    return false;
  }

  private static boolean methodOverridesSuper(PsiMethod method, PsiMethod superMethod) {
    boolean overrides = false;
    if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
      overrides = true;
    } else if (!superMethod.hasModifierProperty(PsiModifier.ABSTRACT)) {
      overrides = true;
    }
    return overrides;

  }

  @Nonnull
  public ActionPresentation getPresentation() {
    return new ActionPresentationData(
      IdeLocalize.actionStructureviewGroupMethodsByDefiningType().get(),
      null,
      AllIcons.General.ImplementingMethod
    );
  }

  @Nonnull
  public String getName() {
    return ID;
  }
}
