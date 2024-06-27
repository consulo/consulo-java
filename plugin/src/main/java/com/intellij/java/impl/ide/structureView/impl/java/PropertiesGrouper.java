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

import consulo.application.AllIcons;
import consulo.fileEditor.structureView.tree.*;
import consulo.language.psi.PsiElement;
import consulo.platform.base.localize.IdeLocalize;
import consulo.project.ui.view.tree.AbstractTreeNode;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

import java.util.*;

public class PropertiesGrouper implements Grouper {
  @NonNls public static final String ID = "SHOW_PROPERTIES";

  @Nonnull
  public Collection<Group> group(final Object parent, Collection<TreeElement> children) {
    if (((AbstractTreeNode) parent).getValue() instanceof PropertyGroup) {
      return Collections.emptyList();
    }
    Map<Group,Group> result = new HashMap<>();
    for (TreeElement o : children) {
      if (o instanceof JavaClassTreeElementBase) {
        PsiElement element = ((JavaClassTreeElementBase)o).getElement();
        PropertyGroup group = PropertyGroup.createOn(element, o);
        if (group != null) {
          PropertyGroup existing = (PropertyGroup)result.get(group);
          if (existing != null) {
            existing.copyAccessorsFrom(group);
          }
          else {
            result.put(group, group);
          }
        }
      }
    }
    for (Iterator<Group> iterator = result.keySet().iterator(); iterator.hasNext();) {
      PropertyGroup group = (PropertyGroup)iterator.next();
      if (!group.isComplete()) {
        iterator.remove();
      }
    }
    return result.values();
  }

  @Nonnull
  public ActionPresentation getPresentation() {
    return new ActionPresentationData(IdeLocalize.actionStructureviewShowProperties().get(), null, AllIcons.Nodes.Property);
  }

  @Nonnull
  public String getName() {
    return ID;
  }
}
