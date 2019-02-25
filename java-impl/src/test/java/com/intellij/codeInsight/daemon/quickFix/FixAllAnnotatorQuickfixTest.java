/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
 * Date: 17-Jun-2007
 */
package com.intellij.codeInsight.daemon.quickFix;

import static org.junit.Assert.fail;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.NonNls;
import com.intellij.codeInsight.daemon.impl.DefaultHighlightVisitorBasedInspection;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.Language;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.util.IncorrectOperationException;

public abstract class FixAllAnnotatorQuickfixTest extends LightQuickFixTestCase {
  public void testAnnotator() throws Exception {
    Annotator annotator = new MyAnnotator();
    Language javaLanguage = JavaFileType.INSTANCE.getLanguage();
    //LanguageAnnotators.INSTANCE.addExplicitExtension(javaLanguage, annotator);
    enableInspectionTool(new DefaultHighlightVisitorBasedInspection.AnnotatorBasedInspection());
    try {
      doAllTests();
    }
    finally {
      //LanguageAnnotators.INSTANCE.removeExplicitExtension(javaLanguage, annotator);
    }
  }

  @Override
  protected boolean shouldBeAvailableAfterExecution() {
    return true;
  }

  @Override
  @NonNls
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/fixAllAnnotator";
  }

  public static class MyAnnotator implements Annotator {
    @Override
    public void annotate(@Nonnull PsiElement element, @Nonnull AnnotationHolder holder) {
      if (element instanceof PsiMethod) {
        Annotation annotation = holder.createErrorAnnotation(((PsiMethod)element).getNameIdentifier(), null);
        annotation.registerUniversalFix(new MyFix(), null, null);
        annotation.setTextAttributes(DefaultLanguageHighlighterColors.DOC_COMMENT_TAG_VALUE);
      }
    }

    static class MyFix implements IntentionAction, LocalQuickFix {

      @Nonnull
      @Override
      public String getText() {
        return getName();
      }

      @Nonnull
      @Override
      public String getName() {
        return "MyFix";
      }

      @Nonnull
      @Override
      public String getFamilyName() {
        return getName();
      }

      @Override
      public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
        final PsiElement element = descriptor.getPsiElement();
        if (element != null) {
          final PsiElement parent = element.getParent();
          if (parent instanceof PsiMethod) {
            ((PsiMethod)parent).setName(((PsiMethod)parent).getName() + "F");
          }
        }
      }

      @Override
      public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
        return true;
      }

      @Override
      public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        fail();
      }

      @Override
      public boolean startInWriteAction() {
        return true;
      }
    }
  }
}