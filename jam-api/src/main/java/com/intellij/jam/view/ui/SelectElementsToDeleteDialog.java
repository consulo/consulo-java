/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package com.intellij.jam.view.ui;

import com.intellij.jam.JamMessages;
import consulo.project.Project;
import consulo.language.psi.PsiElement;
import javax.annotation.Nonnull;

import javax.swing.*;
import java.util.List;

public class SelectElementsToDeleteDialog extends SelectElementsDialog {

  public SelectElementsToDeleteDialog(final List<PsiElement> elements, final Project project) {
    super(project, elements, JamMessages.message("dialog.title.select.elements.to.delete"), JamMessages.message("column.name.elements.to.delete"));
    getSelectedItems().addAll(elements);
  }

  @Nonnull
  protected Action[] createActions() {
    getOKAction().putValue(Action.NAME, JamMessages.message("button.delete"));
    getCancelAction().putValue(Action.NAME, JamMessages.message("button.do.not.delete"));

    return super.createActions();
  }

}
