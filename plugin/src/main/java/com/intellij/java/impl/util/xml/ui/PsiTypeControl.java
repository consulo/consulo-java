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
package com.intellij.java.impl.util.xml.ui;

import com.intellij.java.impl.util.xml.JvmPsiTypeConverterImpl;
import com.intellij.java.language.impl.ui.JavaReferenceEditorUtil;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiType;
import consulo.language.editor.ui.awt.EditorTextField;
import consulo.language.editor.ui.awt.ReferenceEditorWithBrowseButton;
import consulo.language.psi.PsiManager;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import consulo.xml.util.xml.AbstractConvertContext;
import consulo.xml.util.xml.DomElement;
import consulo.xml.util.xml.ui.DomWrapper;
import consulo.xml.util.xml.ui.EditorTextFieldControl;
import consulo.xml.util.xml.ui.PsiTypePanel;

import jakarta.annotation.Nonnull;

/**
 * @author peter
 */
public class PsiTypeControl extends EditorTextFieldControl<PsiTypePanel> {

  public PsiTypeControl(final DomWrapper<String> domWrapper, final boolean commitOnEveryChange) {
    super(domWrapper, commitOnEveryChange);
  }

  @Nonnull
  public String getValue() {
    final String rawValue = super.getValue();
    try {
      final PsiType psiType = JavaPsiFacade.getInstance(getProject()).getElementFactory().createTypeFromText(rawValue, null);
      final String s = JvmPsiTypeConverterImpl.convertToString(psiType);
      if (s != null) {
        return s;
      }
    } catch (IncorrectOperationException e) {
    }
    return rawValue;
  }

  private PsiManager getPsiManager() {
    return PsiManager.getInstance(getProject());
  }

  public void setValue(String value) {
    final PsiType type = JvmPsiTypeConverterImpl.convertFromString(value, new AbstractConvertContext() {
      @jakarta.annotation.Nonnull
      public DomElement getInvocationElement() {
        return getDomElement();
      }

      public PsiManager getPsiManager() {
        return PsiTypeControl.this.getPsiManager();
      }
    });
    if (type != null) {
      value = type.getCanonicalText();
    }
    super.setValue(value);
  }

  protected EditorTextField getEditorTextField(@jakarta.annotation.Nonnull final PsiTypePanel component) {
    return ((ReferenceEditorWithBrowseButton) component.getComponent(0)).getEditorTextField();
  }

  protected PsiTypePanel createMainComponent(PsiTypePanel boundedComponent, final Project project) {
    if (boundedComponent == null) {
      boundedComponent = new PsiTypePanel();
    }
    return PsiClassControl.initReferenceEditorWithBrowseButton(boundedComponent,
        new ReferenceEditorWithBrowseButton(null, project, s -> JavaReferenceEditorUtil.createTypeDocument(s, project), ""), this);
  }


}
