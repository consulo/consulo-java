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
import consulo.fileEditor.structureView.tree.ActionPresentation;
import consulo.fileEditor.structureView.tree.ActionPresentationData;
import consulo.fileEditor.structureView.tree.Filter;
import consulo.fileEditor.structureView.tree.TreeElement;
import consulo.ide.IdeBundle;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;

public class FieldsFilter implements Filter {
  @NonNls
  public static final String ID = "SHOW_FIELDS";

  public boolean isVisible(TreeElement treeNode) {
    return !(treeNode instanceof PsiFieldTreeElement);
  }

  @Nonnull
  public ActionPresentation getPresentation() {
    return new ActionPresentationData(IdeBundle.message("action.structureview.show.fields"), null, AllIcons.Nodes.Field);
  }

  @Nonnull
  public String getName() {
    return ID;
  }

  public boolean isReverted() {
    return true;
  }
}
