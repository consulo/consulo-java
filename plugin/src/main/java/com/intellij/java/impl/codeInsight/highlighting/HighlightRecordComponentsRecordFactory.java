// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.impl.codeInsight.highlighting;

import com.intellij.java.language.impl.psi.impl.light.LightRecordMember;
import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.language.editor.highlight.usage.HighlightUsagesHandlerBase;
import consulo.language.editor.highlight.usage.HighlightUsagesHandlerFactoryBase;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static consulo.util.lang.ObjectUtil.tryCast;

@ExtensionImpl
public final class HighlightRecordComponentsRecordFactory extends HighlightUsagesHandlerFactoryBase {
  @Nullable
  @Override
  public HighlightUsagesHandlerBase<PsiRecordComponent> createHighlightUsagesHandler(@Nonnull Editor editor,
                                                                                     @Nonnull PsiFile file,
                                                                                     @Nonnull PsiElement target) {
    if (!(target instanceof PsiIdentifier)) return null;
    PsiElement parent = target.getParent();
    if (!(parent instanceof PsiReferenceExpression)) return null;
    PsiElement resolved = ((PsiReferenceExpression)parent).resolve();
    if (!(resolved instanceof LightRecordMember member)) return null;

    PsiRecordComponent component = member.getRecordComponent();
    return new RecordComponentHighlightUsagesHandler(editor, file, component);
  }

  private static class RecordComponentHighlightUsagesHandler extends HighlightUsagesHandlerBase<PsiRecordComponent> {
    private final PsiRecordComponent myComponent;

    RecordComponentHighlightUsagesHandler(Editor editor, PsiFile file, PsiRecordComponent component) {
      super(editor, file);
      myComponent = component;
    }

    @Override
    @Nonnull
    public List<PsiRecordComponent> getTargets() {
      return Collections.singletonList(myComponent);
    }

    @Override
    protected void selectTargets(List<PsiRecordComponent> targets, Consumer<List<PsiRecordComponent>> selectionConsumer) {
      selectionConsumer.accept(targets);
    }

    @Override
    @RequiredReadAction
    public void computeUsages(@Nonnull List<PsiRecordComponent> targets) {
      assert targets.size() == 1;
      PsiRecordComponent record = targets.get(0);
      PsiIdentifier nameIdentifier = record.getNameIdentifier();
      if (nameIdentifier != null) {
        addOccurrence(nameIdentifier);
        final String name = nameIdentifier.getText();
        Consumer<PsiExpression> onOccurence = (expr) -> addOccurrence(expr);
        JavaRecursiveElementWalkingVisitor visitor = new JavaRecursiveElementWalkingVisitor() {
          @Override
          public void visitReferenceExpression(@Nonnull PsiReferenceExpression expression) {
            super.visitReferenceExpression(expression);
            if (isReferenceToRecordComponent(name, expression)) {
              onOccurence.accept(expression);
            }
          }
        };
        myComponent.getContainingFile().accept(visitor);
      }
    }

    private boolean isReferenceToRecordComponent(String name, PsiReferenceExpression referenceExpression) {
      if (!name.equals(referenceExpression.getReferenceName())) return false;
      LightRecordMember recordMember = tryCast(referenceExpression.resolve(), LightRecordMember.class);
      if (recordMember == null) return false;
      return recordMember.getRecordComponent() == myComponent;
    }
  }
}
