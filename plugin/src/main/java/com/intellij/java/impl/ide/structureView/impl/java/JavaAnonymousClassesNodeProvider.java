// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.impl.ide.structureView.impl.java;

import com.intellij.java.language.psi.PsiAnonymousClass;
import consulo.application.AllIcons;
import consulo.application.dumb.DumbAware;
import consulo.application.util.SystemInfo;
import consulo.fileEditor.structureView.tree.ActionPresentation;
import consulo.fileEditor.structureView.tree.ActionPresentationData;
import consulo.fileEditor.structureView.tree.FileStructureNodeProvider;
import consulo.fileEditor.structureView.tree.TreeElement;
import consulo.java.analysis.codeInsight.JavaCodeInsightBundle;
import consulo.language.editor.structureView.PsiTreeElementBase;
import consulo.language.navigation.AnonymousElementProvider;
import consulo.language.psi.PsiElement;
import consulo.ui.ex.action.KeyboardShortcut;
import consulo.ui.ex.action.Shortcut;

import jakarta.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class JavaAnonymousClassesNodeProvider implements FileStructureNodeProvider<JavaAnonymousClassTreeElement>, DumbAware {
  public static final String ID = "SHOW_ANONYMOUS";
  public static final String JAVA_ANONYMOUS_PROPERTY_NAME = "java.anonymous.provider";

  @Nonnull
  @Override
  public Collection<JavaAnonymousClassTreeElement> provideNodes(@Nonnull TreeElement node) {
    if (node instanceof PsiMethodTreeElement || node instanceof PsiFieldTreeElement || node instanceof ClassInitializerTreeElement) {
      final PsiElement el = ((PsiTreeElementBase) node).getElement();
      if (el != null) {
        for (AnonymousElementProvider provider : AnonymousElementProvider.EP_NAME.getExtensionList()) {
          final PsiElement[] elements = provider.getAnonymousElements(el);
          if (elements.length > 0) {
            List<JavaAnonymousClassTreeElement> result = new ArrayList<>(elements.length);
            for (PsiElement element : elements) {
              result.add(new JavaAnonymousClassTreeElement((PsiAnonymousClass) element));
            }
            return result;
          }
        }
      }
    }
    return Collections.emptyList();
  }

  @Nonnull
  @Override
  public String getCheckBoxText() {
    return JavaCodeInsightBundle.message("file.structure.toggle.show.anonymous.classes");
  }

  @Override
  public Shortcut[] getShortcut() {
    return new Shortcut[]{KeyboardShortcut.fromString(SystemInfo.isMac ? "meta I" : "control I")};
  }

  @Nonnull
  @Override
  public ActionPresentation getPresentation() {
    return new ActionPresentationData(getCheckBoxText(), null, AllIcons.Nodes.AnonymousClass);
  }

  @Nonnull
  @Override
  public String getName() {
    return ID;
  }

  @Nonnull
  @Override
  public String getSerializePropertyName() {
    return JAVA_ANONYMOUS_PROPERTY_NAME;
  }
}
