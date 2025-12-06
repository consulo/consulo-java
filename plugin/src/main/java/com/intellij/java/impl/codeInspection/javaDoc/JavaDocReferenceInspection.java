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
import consulo.java.analysis.impl.localize.JavaQuickFixLocalize;
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
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.JBList;
import consulo.util.concurrent.AsyncResult;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import java.util.*;

@ExtensionImpl
public class JavaDocReferenceInspection extends BaseLocalInspectionTool {
    public static final String SHORT_NAME = "JavadocReference";

    @Override
    @Nullable
    @RequiredReadAction
    public ProblemDescriptor[] checkMethod(
        @Nonnull PsiMethod psiMethod,
        @Nonnull InspectionManager manager,
        boolean isOnTheFly,
        Object state
    ) {
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
        PsiDocCommentOwner docCommentOwner,
        InspectionManager manager,
        boolean isOnTheFly
    ) {
        List<ProblemDescriptor> problems = new ArrayList<>();
        PsiDocComment docComment = docCommentOwner.getDocComment();
        if (docComment == null) {
            return null;
        }

        Set<PsiJavaCodeReferenceElement> references = new HashSet<>();
        docComment.accept(getVisitor(references, docCommentOwner, problems, manager, isOnTheFly));
        for (PsiJavaCodeReferenceElement reference : references) {
            List<PsiClass> classesToImport = new ImportClassFix(reference).getClassesToImport();
            PsiElement referenceNameElement = reference.getReferenceNameElement();
            PsiElement psiElement = referenceNameElement != null ? referenceNameElement : reference;
            problems.add(
                manager.newProblemDescriptor(InspectionLocalize.inspectionJavadocProblemCannotResolve("<code>" + reference.getText() + "</code>"))
                    .range(psiElement)
                    .highlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
                    .onTheFly(isOnTheFly)
                    .withOptionalFix(!isOnTheFly || classesToImport.isEmpty() ? null : new AddQualifierFix(classesToImport))
                    .create()
            );
        }

        return problems.isEmpty() ? null : problems.toArray(new ProblemDescriptor[problems.size()]);
    }

    private PsiElementVisitor getVisitor(
        final Set<PsiJavaCodeReferenceElement> references,
        final PsiElement context,
        final List<ProblemDescriptor> problems,
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
                JavadocManager javadocManager = JavadocManager.SERVICE.getInstance(tag.getProject());
                JavadocTagInfo info = javadocManager.getTagInfo(tag.getName());
                if (info == null || !info.isInline()) {
                    visitRefInDocTag(tag, javadocManager, context, problems, manager, onTheFly);
                }
            }

            @Override
            @RequiredReadAction
            public void visitInlineDocTag(@Nonnull PsiInlineDocTag tag) {
                super.visitInlineDocTag(tag);
                JavadocManager javadocManager = JavadocManager.SERVICE.getInstance(tag.getProject());
                visitRefInDocTag(tag, javadocManager, context, problems, manager, onTheFly);
            }

            @Override
            @RequiredReadAction
            public void visitElement(PsiElement element) {
                for (PsiElement child : element.getChildren()) {
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
        PsiDocTag tag,
        JavadocManager manager,
        PsiElement context,
        List<ProblemDescriptor> problems,
        InspectionManager inspectionManager,
        boolean onTheFly
    ) {
        String tagName = tag.getName();
        PsiDocTagValue value = tag.getValueElement();
        if (value == null) {
            return;
        }
        JavadocTagInfo info = manager.getTagInfo(tagName);
        if (info != null && !info.isValidInContext(context)) {
            return;
        }
        String message = info == null || !info.isInline() ? null : info.checkTagValue(value);
        if (message != null) {
            problems.add(inspectionManager.newProblemDescriptor(LocalizeValue.of(message))
                .range(value)
                .highlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
                .onTheFly(onTheFly)
                .create());
        }

        PsiReference reference = value.getReference();
        if (reference == null) {
            return;
        }
        PsiElement element = reference.resolve();
        if (element != null) {
            return;
        }
        int textOffset = value.getTextOffset();
        if (textOffset == value.getTextRange().getEndOffset()) {
            return;
        }
        PsiDocTagValue valueElement = tag.getValueElement();
        if (valueElement == null) {
            return;
        }

        CharSequence paramName =
            value.getContainingFile().getViewProvider().getContents().subSequence(textOffset, value.getTextRange().getEndOffset());
        String params = "<code>" + paramName + "</code>";
        List<LocalQuickFix> fixes = new ArrayList<>();
        if (onTheFly && "param".equals(tagName)) {
            PsiDocCommentOwner commentOwner = PsiTreeUtil.getParentOfType(tag, PsiDocCommentOwner.class);
            if (commentOwner instanceof PsiMethod method) {
                PsiParameter[] parameters = method.getParameterList().getParameters();
                PsiDocTag[] tags = tag.getContainingComment().getTags();
                Set<String> unboundParams = new HashSet<>();
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

        problems.add(
            inspectionManager.newProblemDescriptor(InspectionLocalize.inspectionJavadocProblemCannotResolve(params))
                .range(valueElement, reference.getRangeInElement())
                .highlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
                .onTheFly(onTheFly)
                .withFixes(fixes)
                .create()
        );
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionLocalize.inspectionJavadocRefDisplayName();
    }

    @Nonnull
    @Override
    public LocalizeValue getGroupDisplayName() {
        return InspectionLocalize.groupNamesJavadocIssues();
    }

    @Nonnull
    @Override
    public String getShortName() {
        return SHORT_NAME;
    }

    @Nonnull
    @Override
    public HighlightDisplayLevel getDefaultLevel() {
        return HighlightDisplayLevel.ERROR;
    }

    private class AddQualifierFix implements LocalQuickFix {
        private final List<PsiClass> originalClasses;

        public AddQualifierFix(List<PsiClass> originalClasses) {
            this.originalClasses = originalClasses;
        }

        @Nonnull
        @Override
        public LocalizeValue getName() {
            return JavaQuickFixLocalize.addQualifier();
        }

        @Override
        @RequiredWriteAction
        public void applyFix(@Nonnull final Project project, @Nonnull ProblemDescriptor descriptor) {
            PsiElement element = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiJavaCodeReferenceElement.class);
            if (element instanceof PsiJavaCodeReferenceElement refElem) {
                Collections.sort(originalClasses, new PsiProximityComparator(refElem.getElement()));
                JList<PsiClass> list = new JBList<>(originalClasses.toArray(new PsiClass[originalClasses.size()]));
                list.setCellRenderer(new FQNameCellRenderer());
                @RequiredUIAccess
                Runnable runnable = () -> {
                    if (!refElem.isValid()) {
                        return;
                    }
                    final int index = list.getSelectedIndex();
                    if (index < 0) {
                        return;
                    }
                    new WriteCommandAction(project, refElem.getContainingFile()) {
                        @Override
                        @RequiredWriteAction
                        protected void run(Result result) throws Throwable {
                            PsiClass psiClass = originalClasses.get(index);
                            if (psiClass.isValid()) {
                                PsiDocumentManager.getInstance(project).commitAllDocuments();
                                refElem.bindToElement(psiClass);
                            }
                        }
                    }.execute();
                };
                AsyncResult<DataContext> asyncResult = DataManager.getInstance().getDataContextFromFocus();
                asyncResult.doWhenDone(
                    dataContext -> new PopupChooserBuilder(list)
                        .setTitle(JavaQuickFixLocalize.addQualifierOriginalClassChooserTitle().get())
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

        @Nonnull
        @Override
        public LocalizeValue getName() {
            return LocalizeValue.localizeTODO("Change to ...");
        }

        @Override
        public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
            AsyncResult<DataContext> asyncResult = DataManager.getInstance().getDataContextFromFocus();
            asyncResult.doWhenDone(dataContext -> {
                Editor editor = dataContext.getData(Editor.KEY);
                assert editor != null;
                TextRange textRange = ((ProblemDescriptorBase) descriptor).getTextRange();
                editor.getSelectionModel().setSelection(textRange.getStartOffset(), textRange.getEndOffset());

                String word = editor.getSelectionModel().getSelectedText();

                if (word == null || StringUtil.isEmptyOrSpaces(word)) {
                    return;
                }
                List<LookupElement> items = new ArrayList<>();
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

        @Nonnull
        @Override
        public LocalizeValue getName() {
            return LocalizeValue.localizeTODO("Remove @" + myTagName + " " + myParamName);
        }

        @Override
        @RequiredWriteAction
        public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
            PsiDocTag myTag = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiDocTag.class);
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
