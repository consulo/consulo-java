package com.intellij.java.impl.ide.highlighter;

import com.intellij.java.language.impl.JavaClassFileType;
import com.intellij.java.language.impl.JavaFileType;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.highlight.SyntaxHighlighter;
import consulo.language.editor.highlight.SyntaxHighlighterProvider;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.fileType.FileType;
import jakarta.annotation.Nullable;

/**
 * @author VISTALL
 * @since 10/12/2022
 */
@ExtensionImpl
public class JavaSyntaxHighlighterProvider implements SyntaxHighlighterProvider {
  @Nullable
  @Override
  public SyntaxHighlighter create(FileType fileType, @jakarta.annotation.Nullable Project project, @jakarta.annotation.Nullable VirtualFile virtualFile) {
    if (fileType == JavaFileType.INSTANCE || fileType == JavaClassFileType.INSTANCE) {
      return new JavaFileHighlighter();
    }
    return null;
  }
}
