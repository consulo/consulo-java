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

/*
 * User: anna
 * Date: 25-Jan-2008
 */
package com.intellij.java.impl.codeEditor.printing;

import com.intellij.java.language.impl.JavaFileType;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiJavaCodeReferenceElement;
import consulo.annotation.component.ExtensionImpl;
import consulo.configurable.ConfigurationException;
import consulo.configurable.UnnamedConfigurable;
import consulo.ide.impl.idea.codeEditor.printing.CodeEditorBundle;
import consulo.language.editor.action.PrintOption;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import consulo.virtualFileSystem.fileType.FileType;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import java.util.Map;
import java.util.TreeMap;

@ExtensionImpl
public class HyperlinksToClassesOption extends PrintOption {
  private boolean isGenerateHyperlinksToClasses = false;

  @Nullable
  public TreeMap<Integer, PsiReference> collectReferences(PsiFile psiFile, Map<PsiFile, PsiFile> filesMap) {
    if (isGenerateHyperlinksToClasses) {
      FileType fileType = psiFile.getFileType();
      if (JavaFileType.INSTANCE == fileType /*|| StdFileTypes.JSP == fileType*/) {
        final TreeMap<Integer, PsiReference> refMap = new TreeMap<Integer, PsiReference>();
        findClassReferences(psiFile, refMap, filesMap, psiFile);
        return refMap;
      }
    }
    return null;
  }

  @Nonnull
  @Override
  public UnnamedConfigurable createConfigurable() {
    return new HyperlinksToClassesConfigurable();
  }


  private static void findClassReferences(PsiElement psiElement, TreeMap<Integer, PsiReference> refMap, Map<PsiFile, PsiFile> filesMap, PsiFile psiFile) {
    PsiReference ref = psiElement.getReference();
    if (ref instanceof PsiJavaCodeReferenceElement) {
      PsiElement refElement = ref.resolve();
      if (refElement instanceof PsiClass) {
        PsiFile containingFile = refElement.getContainingFile();
        if (!containingFile.equals(psiFile) && filesMap.get(containingFile) != null) {
          refMap.put(psiElement.getTextRange().getStartOffset(), ref);
        }
        return;
      }
    }
    PsiElement[] children = psiElement.getChildren();
    for (PsiElement aChildren : children) {
      findClassReferences(aChildren, refMap, filesMap, psiFile);
    }
  }

  private class HyperlinksToClassesConfigurable implements UnnamedConfigurable {
    private JCheckBox myCbGenerateHyperlinksToClasses;

    public JComponent createComponent() {
      myCbGenerateHyperlinksToClasses = new JCheckBox(CodeEditorBundle.message("export.to.html.generate.hyperlinks.checkbox"), isGenerateHyperlinksToClasses);
      return myCbGenerateHyperlinksToClasses;
    }

    public boolean isModified() {
      return myCbGenerateHyperlinksToClasses.isSelected() != isGenerateHyperlinksToClasses;
    }

    public void apply() throws ConfigurationException {
      isGenerateHyperlinksToClasses = myCbGenerateHyperlinksToClasses.isSelected();
    }

    public void reset() {
      myCbGenerateHyperlinksToClasses.setSelected(isGenerateHyperlinksToClasses);
    }

    public void disposeUIResources() {
    }
  }
}