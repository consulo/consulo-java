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

import com.intellij.java.impl.util.xml.ExtendClass;
import com.intellij.java.language.impl.psi.impl.source.PsiCodeFragmentImpl;
import com.intellij.java.language.impl.ui.JavaReferenceEditorUtil;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.util.ClassFilter;
import com.intellij.java.language.util.TreeClassChooser;
import com.intellij.java.language.util.TreeClassChooserFactory;
import consulo.document.Document;
import consulo.ide.impl.idea.openapi.module.ModuleUtil;
import consulo.language.editor.intention.IntentionFilterOwner;
import consulo.language.editor.ui.awt.EditorTextField;
import consulo.language.editor.ui.awt.ReferenceEditorWithBrowseButton;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.project.Project;
import consulo.ui.ex.localize.UILocalize;
import consulo.xml.util.xml.DomElement;
import consulo.xml.util.xml.GenericDomValue;
import consulo.xml.util.xml.ui.DomWrapper;
import consulo.xml.util.xml.ui.EditorTextFieldControl;
import consulo.xml.util.xml.ui.PsiClassPanel;
import jakarta.annotation.Nonnull;

import javax.swing.*;

/**
 * @author peter
 */
public class PsiClassControl extends EditorTextFieldControl<PsiClassPanel> {

  public PsiClassControl(final DomWrapper<String> domWrapper) {
    super(domWrapper);
  }

  public PsiClassControl(final DomWrapper<String> domWrapper, final boolean commitOnEveryChange) {
    super(domWrapper, commitOnEveryChange);
  }

  protected EditorTextField getEditorTextField(@Nonnull final PsiClassPanel component) {
    return ((ReferenceEditorWithBrowseButton) component.getComponent(0)).getEditorTextField();
  }

  protected PsiClassPanel createMainComponent(PsiClassPanel boundedComponent, final Project project) {
    if (boundedComponent == null) {
      boundedComponent = new PsiClassPanel();
    }
    ReferenceEditorWithBrowseButton editor =
      JavaReferenceEditorUtil.createReferenceEditorWithBrowseButton(null, "", project, true);
    Document document = editor.getChildComponent().getDocument();
    PsiCodeFragmentImpl fragment = (PsiCodeFragmentImpl) PsiDocumentManager.getInstance(project).getPsiFile(document);
    assert fragment != null;
    fragment.setIntentionActionsFilter(IntentionFilterOwner.IntentionActionsFilter.EVERYTHING_AVAILABLE);
    fragment.putUserData(ModuleUtil.KEY_MODULE, getDomWrapper().getExistingDomElement().getModule());
    return initReferenceEditorWithBrowseButton(boundedComponent, editor, this);
  }

  protected static <T extends JPanel> T initReferenceEditorWithBrowseButton(
    final T boundedComponent,
    final ReferenceEditorWithBrowseButton editor,
    final EditorTextFieldControl control
  ) {
    boundedComponent.removeAll();
    boundedComponent.add(editor);
    final GlobalSearchScope resolveScope = control.getDomWrapper().getResolveScope();
    editor.addActionListener(e -> {
      final DomElement domElement = control.getDomElement();
      ExtendClass extend = domElement.getAnnotation(ExtendClass.class);
      PsiClass baseClass = null;
      ClassFilter filter = null;
      if (extend != null) {
        baseClass = JavaPsiFacade.getInstance(control.getProject()).findClass(extend.value(), resolveScope);
        if (extend.instantiatable()) {
          filter = ClassFilter.INSTANTIABLE;
        }
      }

      PsiClass initialClass = null;
      if (domElement instanceof GenericDomValue) {
        final Object value = ((GenericDomValue) domElement).getValue();
        if (value instanceof PsiClass)
          initialClass = (PsiClass) value;
      }

      TreeClassChooser chooser = TreeClassChooserFactory.getInstance(control.getProject())
          .createInheritanceClassChooser(UILocalize.chooseClass().get(), resolveScope, baseClass, initialClass, filter);
      chooser.showDialog();
      final PsiClass psiClass = chooser.getSelected();
      if (psiClass != null) {
        control.setValue(psiClass.getQualifiedName());
      }
    });
    return boundedComponent;
  }
}
