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
package com.intellij.java.impl.ui;

import com.intellij.java.language.impl.JavaFileType;
import com.intellij.java.language.psi.JavaCodeFragment;
import com.intellij.java.language.psi.JavaCodeFragmentFactory;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiJavaPackage;
import consulo.document.Document;
import consulo.project.Project;
import consulo.ui.ex.awt.ComponentWithBrowseButton;
import consulo.util.lang.StringUtil;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.editor.ui.awt.EditorComboBox;
import consulo.ui.ex.RecentsManager;
import consulo.ui.ex.awt.TextAccessor;
import consulo.util.collection.ArrayUtil;
import jakarta.annotation.Nonnull;

import java.awt.event.ActionListener;
import java.util.List;

/**
 * @author ven
 */
public class ReferenceEditorComboWithBrowseButton extends ComponentWithBrowseButton<EditorComboBox> implements TextAccessor {
  public ReferenceEditorComboWithBrowseButton(ActionListener browseActionListener,
                                              String text,
                                              @Nonnull Project project,
                                              boolean toAcceptClasses, String recentsKey) {
    this(browseActionListener, text, project, toAcceptClasses, JavaCodeFragment.VisibilityChecker.EVERYTHING_VISIBLE, recentsKey);
  }

  public ReferenceEditorComboWithBrowseButton(ActionListener browseActionListener,
                                              String text,
                                              @Nonnull Project project,
                                              boolean toAcceptClasses,
                                              JavaCodeFragment.VisibilityChecker visibilityChecker, String recentsKey) {
    super(new EditorComboBox(createDocument(StringUtil.isEmpty(text) ? "" : text, project, toAcceptClasses, visibilityChecker), project, JavaFileType.INSTANCE),
          browseActionListener);
    List<String> recentEntries = RecentsManager.getInstance(project).getRecentEntries(recentsKey);
    if (recentEntries != null) {
      setHistory(ArrayUtil.toStringArray(recentEntries));
    }
    if (text != null && text.length() > 0) {
      prependItem(text);
    }
  }

  private static Document createDocument(String text,
                                         Project project,
                                         boolean isClassesAccepted,
                                         JavaCodeFragment.VisibilityChecker visibilityChecker) {
    PsiJavaPackage defaultPackage = JavaPsiFacade.getInstance(project).findPackage("");
    JavaCodeFragment fragment = JavaCodeFragmentFactory.getInstance(project).createReferenceCodeFragment(text, defaultPackage, true, isClassesAccepted);
    fragment.setVisibilityChecker(visibilityChecker);
    return PsiDocumentManager.getInstance(project).getDocument(fragment);
  }

  public String getText(){
    return getChildComponent().getText().trim();
  }

  public void setText(String text){
    getChildComponent().setText(text);
  }

  public boolean isEditable() {
    return !getChildComponent().getEditorEx().isViewer();
  }

  public void setHistory(String[] history) {
    getChildComponent().setHistory(history);
  }

  public void prependItem(String item) {
    getChildComponent().prependItem(item);
  }

  public void appendItem(String item) {
    getChildComponent().appendItem(item);
  }
}
