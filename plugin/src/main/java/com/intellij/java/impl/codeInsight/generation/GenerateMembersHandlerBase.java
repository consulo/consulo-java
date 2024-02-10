/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInsight.generation;

import com.intellij.java.impl.generate.exception.GenerateCodeException;
import com.intellij.java.language.impl.codeInsight.generation.GenerationInfo;
import com.intellij.java.language.impl.codeInsight.generation.PsiElementClassMember;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiDocCommentOwner;
import com.intellij.java.language.psi.PsiMember;
import consulo.application.ApplicationManager;
import consulo.application.Result;
import consulo.codeEditor.Editor;
import consulo.codeEditor.LogicalPosition;
import consulo.codeEditor.ScrollType;
import consulo.dataContext.DataContext;
import consulo.document.Document;
import consulo.document.FileDocumentManager;
import consulo.document.util.TextRange;
import consulo.ide.impl.idea.codeInsight.CodeInsightUtilBase;
import consulo.ide.impl.idea.ide.util.MemberChooser;
import consulo.ide.impl.idea.openapi.editor.actions.EnterAction;
import consulo.language.editor.WriteCommandAction;
import consulo.language.editor.action.CodeInsightActionHandler;
import consulo.language.editor.generation.ClassMember;
import consulo.language.editor.hint.HintManager;
import consulo.language.editor.impl.inspection.GlobalInspectionContextBase;
import consulo.language.editor.refactoring.ContextAwareActionHandler;
import consulo.language.editor.template.Template;
import consulo.language.editor.template.TemplateManager;
import consulo.language.editor.template.event.TemplateEditingAdapter;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.StringUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public abstract class GenerateMembersHandlerBase implements CodeInsightActionHandler, ContextAwareActionHandler {
  private static final Logger LOG = Logger.getInstance(GenerateMembersHandlerBase.class);

  private final String myChooserTitle;
  protected boolean myToCopyJavaDoc = false;

  public GenerateMembersHandlerBase(String chooserTitle) {
    myChooserTitle = chooserTitle;
  }

  @Override
  public boolean isAvailableForQuickList(@Nonnull Editor editor, @Nonnull PsiFile file, @Nonnull DataContext dataContext) {
    final PsiClass aClass = OverrideImplementUtil.getContextClass(file.getProject(), editor, file, false);
    return aClass != null && hasMembers(aClass);
  }

  protected boolean hasMembers(@Nonnull PsiClass aClass) {
    return true;
  }

  @Override
  public final void invoke(@Nonnull final Project project, @Nonnull final Editor editor, @Nonnull PsiFile file) {
    if (!CodeInsightUtilBase.prepareEditorForWrite(editor)) {
      return;
    }
    if (!FileDocumentManager.getInstance().requestWriting(editor.getDocument(), project)) {
      return;
    }
    final PsiClass aClass = OverrideImplementUtil.getContextClass(project, editor, file, false);
    if (aClass == null || aClass.isInterface()) {
      return; //?
    }
    LOG.assertTrue(aClass.isValid());
    LOG.assertTrue(aClass.getContainingFile() != null);

    try {
      final ClassMember[] members = chooseOriginalMembers(aClass, project, editor);
      if (members == null) {
        return;
      }

      WriteCommandAction.runWriteCommandAction(project, new Runnable() {
        @Override
        public void run() {
          final int offset = editor.getCaretModel().getOffset();
          try {
            doGenerate(project, editor, aClass, members);
          } catch (GenerateCodeException e) {
            final String message = e.getMessage();
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              @Override
              public void run() {
                if (!editor.isDisposed()) {
                  editor.getCaretModel().moveToOffset(offset);
                  HintManager.getInstance().showErrorHint(editor, message);
                }
              }
            }, project.getDisposed());
          }
        }
      });
    } finally {
      cleanup();
    }
  }

  protected void cleanup() {
  }

  private void doGenerate(final Project project, final Editor editor, PsiClass aClass, ClassMember[] members) {
    int offset = editor.getCaretModel().getOffset();

    int col = editor.getCaretModel().getLogicalPosition().column;
    int line = editor.getCaretModel().getLogicalPosition().line;
    final Document document = editor.getDocument();
    int lineStartOffset = document.getLineStartOffset(line);
    CharSequence docText = document.getCharsSequence();
    String textBeforeCaret = docText.subSequence(lineStartOffset, offset).toString();
    final String afterCaret = docText.subSequence(offset, document.getLineEndOffset(line)).toString();
    if (textBeforeCaret.trim().length() > 0 && StringUtil.isEmptyOrSpaces(afterCaret) && !editor.getSelectionModel().hasSelection()) {
      EnterAction.insertNewLineAtCaret(editor);
      PsiDocumentManager.getInstance(project).commitDocument(document);
      offset = editor.getCaretModel().getOffset();
      col = editor.getCaretModel().getLogicalPosition().column;
      line = editor.getCaretModel().getLogicalPosition().line;
    }

    editor.getCaretModel().moveToLogicalPosition(new LogicalPosition(0, 0));

    List<? extends GenerationInfo> newMembers;
    try {
      List<? extends GenerationInfo> prototypes = generateMemberPrototypes(aClass, members);
      newMembers = GenerateMembersUtil.insertMembersAtOffset(aClass.getContainingFile(), offset, prototypes);
    } catch (IncorrectOperationException e) {
      LOG.error(e);
      return;
    }

    editor.getCaretModel().moveToLogicalPosition(new LogicalPosition(line, col));

    if (newMembers.isEmpty()) {
      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        HintManager.getInstance().showErrorHint(editor, getNothingFoundMessage());
      }
      return;
    } else {
      final List<PsiElement> elements = new ArrayList<PsiElement>();
      for (GenerationInfo member : newMembers) {
        if (!(member instanceof TemplateGenerationInfo)) {
          final PsiMember psiMember = member.getPsiMember();
          if (psiMember != null) {
            elements.add(psiMember);
          }
        }
      }

      GlobalInspectionContextBase.cleanupElements(project, null, elements.toArray(new PsiElement[elements.size()]));
    }

    final ArrayList<TemplateGenerationInfo> templates = new ArrayList<TemplateGenerationInfo>();
    for (GenerationInfo member : newMembers) {
      if (member instanceof TemplateGenerationInfo) {
        templates.add((TemplateGenerationInfo) member);
      }
    }

    if (!templates.isEmpty()) {
      PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document);
      runTemplates(project, editor, templates, 0);
    } else if (!newMembers.isEmpty()) {
      newMembers.get(0).positionCaret(editor, false);
    }
  }

  protected String getNothingFoundMessage() {
    return "Nothing found to insert";
  }

  private static void runTemplates(final Project myProject, final Editor editor, final List<TemplateGenerationInfo> templates, final int index) {
    TemplateGenerationInfo info = templates.get(index);
    final Template template = info.getTemplate();

    final PsiElement element = info.getPsiMember();
    final TextRange range = element.getTextRange();
    editor.getDocument().deleteString(range.getStartOffset(), range.getEndOffset());
    int offset = range.getStartOffset();
    editor.getCaretModel().moveToOffset(offset);
    editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
    TemplateManager.getInstance(myProject).startTemplate(editor, template, new TemplateEditingAdapter() {
      @Override
      public void templateFinished(Template template, boolean brokenOff) {
        if (index + 1 < templates.size()) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              new WriteCommandAction(myProject) {
                @Override
                protected void run(@Nonnull Result result) throws Throwable {
                  runTemplates(myProject, editor, templates, index + 1);
                }
              }.execute();
            }
          });
        }
      }
    });
  }


  @Nullable
  protected ClassMember[] chooseOriginalMembers(PsiClass aClass, Project project) {
    ClassMember[] allMembers = getAllOriginalMembers(aClass);
    return chooseMembers(allMembers, false, false, project, null);
  }

  @Nullable
  protected ClassMember[] chooseOriginalMembers(PsiClass aClass, Project project, Editor editor) {
    return chooseOriginalMembers(aClass, project);
  }

  @Nullable
  protected ClassMember[] chooseMembers(ClassMember[] members, boolean allowEmptySelection, boolean copyJavadocCheckbox, Project project, @Nullable Editor editor) {
    MemberChooser<ClassMember> chooser = createMembersChooser(members, allowEmptySelection, copyJavadocCheckbox, project);
    if (editor != null) {
      final int offset = editor.getCaretModel().getOffset();

      ClassMember preselection = null;
      for (ClassMember member : members) {
        if (member instanceof PsiElementClassMember) {
          final PsiDocCommentOwner owner = ((PsiElementClassMember) member).getElement();
          if (owner != null) {
            final TextRange textRange = owner.getTextRange();
            if (textRange != null && textRange.contains(offset)) {
              preselection = member;
              break;
            }
          }
        }
      }
      if (preselection != null) {
        chooser.selectElements(new ClassMember[]{preselection});
      }
    }

    chooser.show();
    myToCopyJavaDoc = chooser.isCopyJavadoc();
    final List<ClassMember> list = chooser.getSelectedElements();
    return list == null ? null : list.toArray(new ClassMember[list.size()]);
  }

  protected MemberChooser<ClassMember> createMembersChooser(ClassMember[] members, boolean allowEmptySelection, boolean copyJavadocCheckbox, Project project) {
    MemberChooser<ClassMember> chooser = new MemberChooser<ClassMember>(members, allowEmptySelection, true, project, false, getHeaderPanel(project)) {
      @Nullable
      @Override
      protected String getHelpId() {
        return GenerateMembersHandlerBase.this.getHelpId();
      }
    };
    chooser.setTitle(myChooserTitle);
    chooser.setCopyJavadocVisible(copyJavadocCheckbox);
    return chooser;
  }

  @Nullable
  protected JComponent getHeaderPanel(Project project) {
    return null;
  }

  protected String getHelpId() {
    return null;
  }

  @Nonnull
  protected List<? extends GenerationInfo> generateMemberPrototypes(PsiClass aClass, ClassMember[] members) throws IncorrectOperationException {
    ArrayList<GenerationInfo> array = new ArrayList<GenerationInfo>();
    for (ClassMember member : members) {
      GenerationInfo[] prototypes = generateMemberPrototypes(aClass, member);
      if (prototypes != null) {
        ContainerUtil.addAll(array, prototypes);
      }
    }
    return array;
  }

  protected abstract ClassMember[] getAllOriginalMembers(PsiClass aClass);

  protected abstract GenerationInfo[] generateMemberPrototypes(PsiClass aClass, ClassMember originalMember) throws IncorrectOperationException;

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
