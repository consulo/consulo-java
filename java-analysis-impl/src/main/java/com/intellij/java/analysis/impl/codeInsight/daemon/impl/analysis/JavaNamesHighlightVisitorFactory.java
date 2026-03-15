package com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis;

import com.intellij.java.language.psi.PsiImportHolder;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.rawHighlight.HighlightVisitor;
import consulo.language.editor.rawHighlight.HighlightVisitorFactory;
import consulo.language.psi.PsiFile;

/**
 * @author VISTALL
 * @since 2024-02-03
 */
@ExtensionImpl
public class JavaNamesHighlightVisitorFactory implements HighlightVisitorFactory {
  @Override
  public boolean suitableForFile(PsiFile file) {
    return file instanceof PsiImportHolder;
  }

  @Override
  public HighlightVisitor createVisitor() {
    return new JavaNamesHighlightVisitor();
  }
}
