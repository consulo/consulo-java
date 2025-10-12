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
package com.intellij.java.impl.codeInsight.daemon.impl;

import com.intellij.java.analysis.codeInspection.BatchSuppressManager;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import com.intellij.java.language.psi.javadoc.PsiDocTag;
import consulo.java.analysis.impl.localize.JavaQuickFixLocalize;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.SuppressionUtil;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiRecursiveElementWalkingVisitor;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.Comparing;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RemoveSuppressWarningAction implements LocalQuickFix {
    private static final Logger LOG = Logger.getInstance(RemoveSuppressWarningAction.class);

    private final String myID;
    private final String myProblemLine;

    public RemoveSuppressWarningAction(final String ID, final String problemLine) {
        myID = ID;
        myProblemLine = problemLine;
    }

    public RemoveSuppressWarningAction(String id) {
        final int idx = id.indexOf(";");
        if (idx > -1) {
            myID = id.substring(0, idx);
            myProblemLine = id.substring(idx);
        }
        else {
            myID = id;
            myProblemLine = null;
        }
    }

    @Override
    public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        try {
            if (element instanceof PsiIdentifier) {
                if (!FileModificationService.getInstance().prepareFileForWrite(element.getContainingFile())) {
                    return;
                }
                final PsiIdentifier identifier = (PsiIdentifier) element;
                final PsiDocCommentOwner commentOwner = PsiTreeUtil.getParentOfType(identifier, PsiDocCommentOwner.class);
                if (commentOwner != null) {
                    final PsiElement psiElement = BatchSuppressManager.getInstance().getElementMemberSuppressedIn(commentOwner, myID);
                    if (psiElement instanceof PsiAnnotation) {
                        removeFromAnnotation((PsiAnnotation) psiElement);
                    }
                    else if (psiElement instanceof PsiDocComment) {
                        removeFromJavaDoc((PsiDocComment) psiElement);
                    }
                    else { //try to remove from all comments
                        final Set<PsiComment> comments = new HashSet<PsiComment>();
                        commentOwner.accept(new PsiRecursiveElementWalkingVisitor() {
                            @Override
                            public void visitComment(final PsiComment comment) {
                                super.visitComment(comment);
                                if (comment.getText().contains(myID)) {
                                    comments.add(comment);
                                }
                            }
                        });
                        for (PsiComment comment : comments) {
                            try {
                                removeFromComment(comment, comments.size() > 1);
                            }
                            catch (IncorrectOperationException e) {
                                LOG.error(e);
                            }
                        }
                    }
                }
            }
        }
        catch (IncorrectOperationException e) {
            LOG.error(e);
        }
    }

    @Nonnull
    @Override
    public LocalizeValue getName() {
        return JavaQuickFixLocalize.removeSuppressionActionName(myID);
    }

    private void removeFromComment(final PsiComment comment, final boolean checkLine) throws IncorrectOperationException {
        if (checkLine) {
            final PsiStatement statement = PsiTreeUtil.getNextSiblingOfType(comment, PsiStatement.class);
            if (statement != null && !Comparing.strEqual(statement.getText(), myProblemLine)) {
                return;
            }
        }
        String newText = removeFromElementText(comment);
        if (newText != null) {
            if (newText.length() == 0) {
                comment.delete();
            }
            else {
                PsiComment newComment = JavaPsiFacade.getInstance(comment.getProject()).getElementFactory()
                    .createCommentFromText("// " + SuppressionUtil.SUPPRESS_INSPECTIONS_TAG_NAME + " " + newText, comment);
                comment.replace(newComment);
            }
        }
    }

    private void removeFromJavaDoc(PsiDocComment docComment) throws IncorrectOperationException {
        PsiDocTag tag = docComment.findTagByName(SuppressionUtil.SUPPRESS_INSPECTIONS_TAG_NAME);
        if (tag == null) {
            return;
        }
        String newText = removeFromElementText(tag.getDataElements());
        if (newText != null && newText.length() == 0) {
            tag.delete();
        }
        else if (newText != null) {
            newText = "@" + SuppressionUtil.SUPPRESS_INSPECTIONS_TAG_NAME + " " + newText;
            PsiDocTag newTag = JavaPsiFacade.getInstance(tag.getProject()).getElementFactory().createDocTagFromText(newText);
            tag.replace(newTag);
        }
    }

    @Nullable
    private String removeFromElementText(final PsiElement... elements) {
        String text = "";
        for (PsiElement element : elements) {
            text += StringUtil.trimStart(element.getText(), "//").trim();
        }
        text = StringUtil.trimStart(text, "@").trim();
        text = StringUtil.trimStart(text, SuppressionUtil.SUPPRESS_INSPECTIONS_TAG_NAME).trim();
        List<String> ids = StringUtil.split(text, ",");
        int i = ArrayUtil.find(ids.toArray(), myID);
        if (i == -1) {
            return null;
        }
        ids.remove(i);
        return StringUtil.join(ids, ",");
    }

    private void removeFromAnnotation(final PsiAnnotation annotation) throws IncorrectOperationException {
        PsiNameValuePair[] attributes = annotation.getParameterList().getAttributes();
        for (PsiNameValuePair attribute : attributes) {
            PsiAnnotationMemberValue value = attribute.getValue();
            if (value instanceof PsiArrayInitializerMemberValue) {
                PsiAnnotationMemberValue[] initializers = ((PsiArrayInitializerMemberValue) value).getInitializers();
                for (PsiAnnotationMemberValue initializer : initializers) {
                    if (removeFromValue(annotation, initializer, initializers.length == 1)) {
                        return;
                    }
                }
            }
            if (removeFromValue(annotation, value, attributes.length == 1)) {
                return;
            }
        }
    }

    private boolean removeFromValue(
        final PsiAnnotationMemberValue parent,
        final PsiAnnotationMemberValue value,
        final boolean removeParent
    ) throws IncorrectOperationException {
        String text = value.getText();
        text = StringUtil.trimStart(text, "\"");
        text = StringUtil.trimEnd(text, "\"");
        if (myID.equals(text)) {
            if (removeParent) {
                parent.delete();
            }
            else {
                value.delete();
            }
            return true;
        }
        return false;
    }
}
