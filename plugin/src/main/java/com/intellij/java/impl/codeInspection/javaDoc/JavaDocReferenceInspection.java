/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInspection.javaDoc;

import com.intellij.java.analysis.impl.codeInspection.ex.BaseLocalInspectionTool;
import com.intellij.java.impl.codeInsight.daemon.impl.quickfix.ImportClassFix;
import com.intellij.java.impl.ide.util.FQNameCellRenderer;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.javadoc.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.Result;
import consulo.codeEditor.Editor;
import consulo.dataContext.DataContext;
import consulo.dataContext.DataManager;
import consulo.document.util.TextRange;
import consulo.ide.impl.ui.impl.PopupChooserBuilder;
import consulo.java.analysis.impl.JavaQuickFixBundle;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.WriteCommandAction;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.editor.completion.lookup.LookupManager;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.ProblemDescriptorBase;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiReference;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.proximity.PsiProximityComparator;
import consulo.project.Project;
import consulo.ui.ex.awt.JBList;
import consulo.util.concurrent.AsyncResult;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.util.*;

@ExtensionImpl
public class JavaDocReferenceInspection extends BaseLocalInspectionTool {
  @NonNls
  public static final String SHORT_NAME = "JavadocReference";

  private static ProblemDescriptor createDescriptor(
    @Nonnull PsiElement element,
    String template,
    InspectionManager manager,
    boolean onTheFly
  ) {
    return manager.createProblemDescriptor(element, template, onTheFly, null, ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
  }

  @Override
  @Nullable
  @RequiredReadAction
  public ProblemDescriptor[] checkMethod(@Nonnull PsiMethod psiMethod, @Nonnull InspectionManager manager, boolean isOnTheFly, Object state) {
    return checkMember(psiMethod, manager, isOnTheFly);
  }

  @Override
  @Nullable
  @RequiredReadAction
  public ProblemDescriptor[] checkField(@Nonnull PsiField field, @Nonnull InspectionManager manager, boolean isOnTheFly, Object state) {
    return checkMember(field, manager, isOnTheFly);
  }

  @Override
  @Nullable
  @RequiredReadAction
  public ProblemDescriptor[] checkClass(@Nonnull PsiClass aClass, @Nonnull InspectionManager manager, boolean isOnTheFly, Object state) {
    return checkMember(aClass, manager, isOnTheFly);
  }

  @Nullable
  @RequiredReadAction
  private ProblemDescriptor[] checkMember(
    final PsiDocCommentOwner docCommentOwner,
    final InspectionManager manager,
    final boolean isOnTheFly
  ) {
    final ArrayList<ProblemDescriptor> problems = new ArrayList<>();
    final PsiDocComment docComment = docCommentOwner.getDocComment();
    if (docComment == null) {
      return null;
    }

    final Set<PsiJavaCodeReferenceElement> references = new HashSet<>();
    docComment.accept(getVisitor(references, docCommentOwner, problems, manager, isOnTheFly));
    for (PsiJavaCodeReferenceElement reference : references) {
      final List<PsiClass> classesToImport = new ImportClassFix(reference).getClassesToImport();
      final PsiElement referenceNameElement = reference.getReferenceNameElement();
      problems.add(manager.createProblemDescriptor(
        referenceNameElement != null ? referenceNameElement : reference,
        cannotResolveSymbolMessage("<code>" + reference.getText() + "</code>"),
        !isOnTheFly || classesToImport.isEmpty() ? null : new AddQualifierFix(classesToImport),
        ProblemHighlightType.LIKE_UNKNOWN_SYMBOL,
        isOnTheFly
      ));
    }

    return problems.isEmpty() ? null : problems.toArray(new ProblemDescriptor[problems.size()]);
  }

  private PsiElementVisitor getVisitor(
    final Set<PsiJavaCodeReferenceElement> references,
    final PsiElement context,
    final ArrayList<ProblemDescriptor> problems,
    final InspectionManager manager,
    final boolean onTheFly
  ) {
    return new JavaElementVisitor() {
      @Override
      @RequiredReadAction
      public void visitReferenceExpression(@Nonnull PsiReferenceExpression expression) {
        visitElement(expression);
      }

      @Override
      public void visitReferenceElement(@Nonnull PsiJavaCodeReferenceElement reference) {
        super.visitReferenceElement(reference);
        JavaResolveResult result = reference.advancedResolve(false);
        if (result.getElement() == null && !result.isPackagePrefixPackageReference()) {
          references.add(reference);
        }
      }

      @Override
      @RequiredReadAction
      public void visitDocTag(@Nonnull PsiDocTag tag) {
        super.visitDocTag(tag);
        final JavadocManager javadocManager = JavadocManager.SERVICE.getInstance(tag.getProject());
        final JavadocTagInfo info = javadocManager.getTagInfo(tag.getName());
        if (info == null || !info.isInline()) {
          visitRefInDocTag(tag, javadocManager, context, problems, manager, onTheFly);
        }
      }

      @Override
      @RequiredReadAction
      public void visitInlineDocTag(@Nonnull PsiInlineDocTag tag) {
        super.visitInlineDocTag(tag);
        final JavadocManager javadocManager = JavadocManager.SERVICE.getInstance(tag.getProject());
        visitRefInDocTag(tag, javadocManager, context, problems, manager, onTheFly);
      }

      @Override
      @RequiredReadAction
      public void visitElement(PsiElement element) {
        PsiElement[] children = element.getChildren();
        for (PsiElement child : children) {
          //do not visit method javadoc twice
          if (!(child instanceof PsiDocCommentOwner)) {
            child.accept(this);
          }
        }
      }
    };
  }

  @RequiredReadAction
  public static void visitRefInDocTag(
    final PsiDocTag tag,
    final JavadocManager manager,
    final PsiElement context,
    final ArrayList<ProblemDescriptor> problems,
    final InspectionManager inspectionManager,
    final boolean onTheFly
  ) {
    final String tagName = tag.getName();
    final PsiDocTagValue value = tag.getValueElement();
    if (value == null) {
      return;
    }
    final JavadocTagInfo info = manager.getTagInfo(tagName);
    if (info != null && !info.isValidInContext(context)) {
      return;
    }
    final String message = info == null || !info.isInline() ? null : info.checkTagValue(value);
    if (message != null) {
      problems.add(createDescriptor(value, message, inspectionManager, onTheFly));
    }

    final PsiReference reference = value.getReference();
    if (reference == null) {
      return;
    }
    final PsiElement element = reference.resolve();
    if (element != null) {
      return;
    }
    final int textOffset = value.getTextOffset();
    if (textOffset == value.getTextRange().getEndOffset()) {
      return;
    }
    final PsiDocTagValue valueElement = tag.getValueElement();
    if (valueElement == null) {
      return;
    }

    final CharSequence paramName =
      value.getContainingFile().getViewProvider().getContents().subSequence(textOffset, value.getTextRange().getEndOffset());
    final String params = "<code>" + paramName + "</code>";
    final List<LocalQuickFix> fixes = new ArrayList<>();
    if (onTheFly && "param".equals(tagName)) {
      final PsiDocCommentOwner commentOwner = PsiTreeUtil.getParentOfType(tag, PsiDocCommentOwner.class);
      if (commentOwner instanceof PsiMethod method) {
        final PsiParameter[] parameters = method.getParameterList().getParameters();
        final PsiDocTag[] tags = tag.getContainingComment().getTags();
        final Set<String> unboundParams = new HashSet<>();
        for (PsiParameter parameter : parameters) {
          if (!JavaDocLocalInspection.isFound(tags, parameter)) {
            unboundParams.add(parameter.getName());
          }
        }
        if (!unboundParams.isEmpty()) {
          fixes.add(new RenameReferenceQuickFix(unboundParams));
        }
      }
    }
    fixes.add(new RemoveTagFix(tagName, paramName));

    problems.add(inspectionManager.createProblemDescriptor(
      valueElement,
      reference.getRangeInElement(),
      cannotResolveSymbolMessage(params),
      ProblemHighlightType.LIKE_UNKNOWN_SYMBOL,
      onTheFly,
      fixes.toArray(new LocalQuickFix[fixes.size()])
    ));
  }

  private static String cannotResolveSymbolMessage(String params) {
    return InspectionLocalize.inspectionJavadocProblemCannotResolve(params).get();
  }

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionLocalize.inspectionJavadocRefDisplayName().get();
  }

  @Override
  @Nonnull
  public String getGroupDisplayName() {
    return InspectionLocalize.groupNamesJavadocIssues().get();
  }

  @Override
  @Nonnull
  public String getShortName() {
    return SHORT_NAME;
  }

  @Override
  @Nonnull
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.ERROR;
  }

  private class AddQualifierFix implements LocalQuickFix {
    private final List<PsiClass> originalClasses;

    public AddQualifierFix(final List<PsiClass> originalClasses) {
      this.originalClasses = originalClasses;
    }

    @Override
    @Nonnull
    public String getName() {
      return JavaQuickFixBundle.message("add.qualifier");
    }

    @Override
    @Nonnull
    public String getFamilyName() {
      return JavaQuickFixBundle.message("add.qualifier");
    }

    @Override
    @RequiredWriteAction
    public void applyFix(@Nonnull final Project project, @Nonnull final ProblemDescriptor descriptor) {
      final PsiElement element = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiJavaCodeReferenceElement.class);
      if (element instanceof PsiJavaCodeReferenceElement) {
        final PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)element;
        Collections.sort(originalClasses, new PsiProximityComparator(referenceElement.getElement()));
        final JList<PsiClass> list = new JBList<>(originalClasses.toArray(new PsiClass[originalClasses.size()]));
        list.setCellRenderer(new FQNameCellRenderer());
        final Runnable runnable = () -> {
          if (!element.isValid()) {
            return;
          }
          final int index = list.getSelectedIndex();
          if (index < 0) {
            return;
          }
          new WriteCommandAction(project, element.getContainingFile()) {
            @Override
            @RequiredWriteAction
            protected void run(final Result result) throws Throwable {
              final PsiClass psiClass = originalClasses.get(index);
              if (psiClass.isValid()) {
                PsiDocumentManager.getInstance(project).commitAllDocuments();
                referenceElement.bindToElement(psiClass);
              }
            }
          }.execute();
        };
        final AsyncResult<DataContext> asyncResult = DataManager.getInstance().getDataContextFromFocus();
        asyncResult.doWhenDone(
          dataContext -> new PopupChooserBuilder(list)
            .setTitle(JavaQuickFixBundle.message("add.qualifier.original.class.chooser.title"))
            .setItemChoosenCallback(runnable)
            .createPopup()
            .showInBestPositionFor(dataContext)
        );
      }
    }
  }

  private static class RenameReferenceQuickFix implements LocalQuickFix {
    private final Set<String> myUnboundParams;

    public RenameReferenceQuickFix(Set<String> unboundParams) {
      myUnboundParams = unboundParams;
    }

    @Override
    @Nonnull
    public String getName() {
      return "Change to ...";
    }

    @Override
    @Nonnull
    public String getFamilyName() {
      return getName();
    }

    @Override
    public void applyFix(@Nonnull final Project project, @Nonnull final ProblemDescriptor descriptor) {
      final AsyncResult<DataContext> asyncResult = DataManager.getInstance().getDataContextFromFocus();
      asyncResult.doWhenDone(dataContext -> {
        final Editor editor = dataContext.getData(Editor.KEY);
        assert editor != null;
        final TextRange textRange = ((ProblemDescriptorBase)descriptor).getTextRange();
        editor.getSelectionModel().setSelection(textRange.getStartOffset(), textRange.getEndOffset());

        final String word = editor.getSelectionModel().getSelectedText();

        if (word == null || StringUtil.isEmptyOrSpaces(word)) {
          return;
        }
        final List<LookupElement> items = new ArrayList<>();
        for (String variant : myUnboundParams) {
          items.add(LookupElementBuilder.create(variant));
        }
        LookupManager.getInstance(project).showLookup(editor, items.toArray(new LookupElement[items.size()]));
      });
    }
  }

  private static class RemoveTagFix implements LocalQuickFix {
    private final String myTagName;
    private final CharSequence myParamName;

    public RemoveTagFix(String tagName, CharSequence paramName) {
      myTagName = tagName;
      myParamName = paramName;
    }

    @Override
    @Nonnull
    public String getName() {
      return "Remove @" + myTagName + " " + myParamName;
    }

    @Override
    @Nonnull
    public String getFamilyName() {
      return getName();
    }

    @Override
    @RequiredWriteAction
    public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
      final PsiDocTag myTag = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiDocTag.class);
      if (myTag == null) {
        return;
      }
      if (!FileModificationService.getInstance().preparePsiElementForWrite(myTag)) {
        return;
      }
      myTag.delete();
    }
  }
}
