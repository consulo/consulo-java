/*
 * Copyright (c) 2007-2009, Osmorc Development Team
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright notice, this list
 *       of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright notice, this
 *       list of conditions and the following disclaimer in the documentation and/or other
 *       materials provided with the distribution.
 *     * Neither the name of 'Osmorc Development Team' nor the names of its contributors may be
 *       used to endorse or promote products derived from this software without specific
 *       prior written permission.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
 * THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
 * OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
 * TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.osmorc.manifest.codeInspection;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.editor.inspection.LocalInspectionTool;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import org.jetbrains.annotations.Nls;
import org.osmorc.manifest.lang.ManifestLanguage;
import org.osmorc.manifest.lang.ManifestTokenType;
import org.osmorc.manifest.lang.psi.Header;
import org.osmorc.manifest.lang.psi.ManifestFile;
import org.osmorc.manifest.lang.psi.Section;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author Robert F. Beeger (robert@beeger.net)
 */
@ExtensionImpl
public class MissingFinalNewlineInspection extends LocalInspectionTool {
  @Nls
  @Nonnull
  public String getGroupDisplayName() {
    return "General";
  }

  @Nullable
  @Override
  public Language getLanguage() {
    return ManifestLanguage.INSTANCE;
  }

  @Nls
  @Nonnull
  public String getDisplayName() {
    return "Missing Final New Line";
  }

  @Nonnull
  public String getShortName() {
    return getClass().getSimpleName();
  }

  public boolean isEnabledByDefault() {
    return true;
  }

  @Nonnull
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.ERROR;
  }

  @Override
  public ProblemDescriptor[] checkFile(@Nonnull PsiFile file, @Nonnull InspectionManager manager, boolean isOnTheFly) {
    if (file instanceof ManifestFile) {
      String text = file.getText();
      // http://ea.jetbrains.com/browser/ea_problems/22570
      if (text != null && text.length() > 0) {
        if (text.charAt(text.length() - 1) != '\n') {
          Section section = PsiTreeUtil.findElementOfClassAtOffset(file, text.length() - 1,
                                                                   Section.class, false);
          if (section != null) {
            return new ProblemDescriptor[]{manager.createProblemDescriptor(section.getLastChild(),
                                                                           "Manifest file doesn't end with a final newline",
                                                                           new AddNewlineQuickFix(section),
                                                                           ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly)};
          }
        }
      }
    }
    return ProblemDescriptor.EMPTY_ARRAY;
  }

  private static class AddNewlineQuickFix implements LocalQuickFix {
    private final Section section;

    private AddNewlineQuickFix(Section section) {
      this.section = section;
    }

    @Nonnull
    public String getName() {
      return "Add newline";
    }

    @Nonnull
    public String getFamilyName() {
      return "Osmorc";
    }

    public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
      PsiElement lastChild = section.getLastChild();
      if (lastChild instanceof Header) {
        Header header = (Header)lastChild;
        header.getNode().addLeaf(ManifestTokenType.NEWLINE, "\n", null);
      }
      else {
        throw new RuntimeException("No header found to add a newline to");
      }
    }
  }
}
