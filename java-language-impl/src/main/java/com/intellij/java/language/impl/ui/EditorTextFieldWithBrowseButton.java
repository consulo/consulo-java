/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.java.language.impl.ui;

import com.intellij.java.language.impl.JavaFileType;
import consulo.document.Document;
import consulo.ui.ex.awt.TextAccessor;
import consulo.virtualFileSystem.fileType.FileType;
import consulo.project.Project;
import consulo.ui.ex.awt.ComponentWithBrowseButton;
import com.intellij.java.language.psi.JavaCodeFragment;
import com.intellij.java.language.psi.JavaCodeFragmentFactory;
import com.intellij.java.language.psi.JavaPsiFacade;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.editor.ui.awt.EditorTextField;

/**
 * User: anna
 */
public class EditorTextFieldWithBrowseButton extends ComponentWithBrowseButton<EditorTextField> implements TextAccessor {
  public EditorTextFieldWithBrowseButton(Project project, boolean isClassAccepted) {
    this(project, isClassAccepted, JavaCodeFragment.VisibilityChecker.EVERYTHING_VISIBLE);
  }

  public EditorTextFieldWithBrowseButton(Project project,
                                         boolean isClassAccepted,
                                         final JavaCodeFragment.VisibilityChecker visibilityChecker) {
    super(createEditorTextField(project, isClassAccepted, visibilityChecker), null);
  }

  public EditorTextFieldWithBrowseButton(Project project,
                                         boolean isClassAccepted,
                                         final JavaCodeFragment.VisibilityChecker visibilityChecker,
                                         FileType fileType) {
    super(createEditorTextField(project, isClassAccepted, visibilityChecker, fileType), null);
  }

  private static EditorTextField createEditorTextField(Project project,
                                                       boolean isClassAccepted,
                                                       JavaCodeFragment.VisibilityChecker visibilityChecker) {
    return createEditorTextField(project, isClassAccepted, visibilityChecker, JavaFileType.INSTANCE);
  }

  private static EditorTextField createEditorTextField(Project project,
                                                       boolean isClassAccepted,
                                                       JavaCodeFragment.VisibilityChecker visibilityChecker,
                                                       final FileType fileType) {
    if (project.isDefault()) return new EditorTextField();
    return new EditorTextField(createDocument("", project, isClassAccepted,
                                             visibilityChecker), project, fileType);
  }

  private static Document createDocument(final String text,
                                         Project project,
                                         boolean isClassesAccepted,
                                         JavaCodeFragment.VisibilityChecker visibilityChecker) {
    PsiElement defaultPackage = JavaPsiFacade.getInstance(project).findPackage("");
    final JavaCodeFragmentFactory factory = JavaCodeFragmentFactory.getInstance(project);
    final JavaCodeFragment fragment = factory.createReferenceCodeFragment(text, defaultPackage, true, isClassesAccepted);
    fragment.setVisibilityChecker(visibilityChecker);
    return PsiDocumentManager.getInstance(project).getDocument(fragment);
  }

  @Override
  public void setText(String text) {
    if (text == null) text = "";
    getChildComponent().setText(text);
  }

  @Override
  public String getText() {
    return getChildComponent().getText();
  }
}
