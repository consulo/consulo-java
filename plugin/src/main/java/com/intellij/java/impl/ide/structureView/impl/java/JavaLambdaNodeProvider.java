// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.impl.ide.structureView.impl.java;

import com.intellij.java.language.psi.PsiLambdaExpression;
import com.intellij.java.language.psi.PsiMember;
import consulo.application.AllIcons;
import consulo.application.dumb.DumbAware;
import consulo.application.util.SystemInfo;
import consulo.fileEditor.structureView.tree.ActionPresentation;
import consulo.fileEditor.structureView.tree.ActionPresentationData;
import consulo.fileEditor.structureView.tree.FileStructureNodeProvider;
import consulo.fileEditor.structureView.tree.TreeElement;
import consulo.java.analysis.codeInsight.JavaCodeInsightBundle;
import consulo.language.editor.structureView.PsiTreeElementBase;
import consulo.language.psi.PsiElement;
import consulo.language.psi.SyntaxTraverser;
import consulo.ui.ex.action.KeyboardShortcut;
import consulo.ui.ex.action.Shortcut;

import jakarta.annotation.Nonnull;
import java.util.Collections;
import java.util.List;

public class JavaLambdaNodeProvider implements FileStructureNodeProvider<JavaLambdaTreeElement>, DumbAware {
  public static final String ID = "SHOW_LAMBDA";
  public static final String JAVA_LAMBDA_PROPERTY_NAME = "java.lambda.provider";

  @Nonnull
  @Override
  public List<JavaLambdaTreeElement> provideNodes(@Nonnull TreeElement node) {
    if (!(node instanceof PsiTreeElementBase)) {
      return Collections.emptyList();
    }
    PsiElement element = ((PsiTreeElementBase) node).getElement();
    return SyntaxTraverser.psiTraverser(element)
        .expand(o -> o == element || !(o instanceof PsiMember || o instanceof PsiLambdaExpression))
        .filter(PsiLambdaExpression.class)
        .filter(o -> o != element)
        .map(JavaLambdaTreeElement::new)
        .toList();
  }

  @Nonnull
  @Override
  public String getCheckBoxText() {
    return JavaCodeInsightBundle.message("file.structure.toggle.show.collapse.show.lambdas");
  }

  @Nonnull
  @Override
  public Shortcut[] getShortcut() {
    return new Shortcut[]{KeyboardShortcut.fromString(SystemInfo.isMac ? "meta L" : "control L")};
  }

  @Nonnull
  @Override
  public ActionPresentation getPresentation() {
    return new ActionPresentationData(getCheckBoxText(), null, AllIcons.Nodes.Lambda);
  }

  @Nonnull
  @Override
  public String getName() {
    return ID;
  }

  @Nonnull
  @Override
  public String getSerializePropertyName() {
    return JAVA_LAMBDA_PROPERTY_NAME;
  }
}
