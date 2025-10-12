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
package com.intellij.java.impl.codeInsight.daemon.quickFix;

import com.intellij.java.impl.codeInsight.CodeInsightUtil;
import com.intellij.java.impl.codeInsight.generation.GenerateFieldOrPropertyHandler;
import com.intellij.java.impl.codeInsight.generation.GenerateMembersUtil;
import com.intellij.java.language.impl.codeInsight.generation.GenerationInfo;
import com.intellij.java.language.psi.PsiAnnotation;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.PsiTypeElement;
import com.intellij.java.language.psi.util.PropertyMemberType;
import com.intellij.java.language.psi.util.PropertyUtil;
import consulo.application.Result;
import consulo.codeEditor.Editor;
import consulo.java.analysis.impl.localize.JavaQuickFixLocalize;
import consulo.language.editor.WriteCommandAction;
import consulo.language.editor.generation.ClassMember;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.intention.IntentionAction;
import consulo.language.editor.template.*;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

import java.util.List;

/**
 * @author peter
 */
public class CreateFieldOrPropertyFix implements IntentionAction, LocalQuickFix {
    private static final Logger LOG = Logger.getInstance(CreateFieldOrPropertyFix.class);

    private final PsiClass myClass;
    private final String myName;
    private final PsiType myType;
    private final PropertyMemberType myMemberType;
    private final PsiAnnotation[] myAnnotations;

    public CreateFieldOrPropertyFix(
        final PsiClass aClass,
        final String name,
        final PsiType type,
        final PropertyMemberType memberType,
        final PsiAnnotation[] annotations
    ) {
        myClass = aClass;
        myName = name;
        myType = type;
        myMemberType = memberType;
        myAnnotations = annotations;
    }

    @Override
    @Nonnull
    public LocalizeValue getText() {
        return myMemberType == PropertyMemberType.FIELD
            ? JavaQuickFixLocalize.createFieldText(myName)
            : JavaQuickFixLocalize.createPropertyText(myName);
    }

    @Nonnull
    @Override
    public LocalizeValue getName() {
        return getText();
    }

    @Override
    public void applyFix(@Nonnull final Project project, @Nonnull ProblemDescriptor descriptor) {
        applyFixInner(project);
    }

    @Override
    public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
        return true;
    }

    @Override
    public void invoke(@Nonnull Project project, Editor editor, PsiFile file) {
        applyFixInner(project);
    }

    private void applyFixInner(final Project project) {
        final PsiFile file = myClass.getContainingFile();
        final Editor editor = CodeInsightUtil.positionCursor(project, myClass.getContainingFile(), myClass.getLBrace());
        if (editor != null) {
            new WriteCommandAction(project, file) {
                @Override
                protected void run(Result result) throws Throwable {
                    generateMembers(project, editor, file);
                }

                @Override
                protected boolean isGlobalUndoAction() {
                    return true; // todo check
                }
            }.execute();
        }
    }

    private void generateMembers(final Project project, final Editor editor, final PsiFile file) {
        try {
            List<? extends GenerationInfo> prototypes = new GenerateFieldOrPropertyHandler(myName, myType, myMemberType, myAnnotations)
                .generateMemberPrototypes(myClass, ClassMember.EMPTY_ARRAY);
            prototypes =
                GenerateMembersUtil.insertMembersAtOffset(myClass.getContainingFile(), editor.getCaretModel().getOffset(), prototypes);
            if (prototypes.isEmpty()) {
                return;
            }
            final PsiElement scope = prototypes.get(0).getPsiMember().getContext();
            assert scope != null;
            final Expression expression = new EmptyExpression() {
                @Override
                public consulo.language.editor.template.Result calculateResult(final ExpressionContext context) {
                    return new TextResult(myType.getCanonicalText());
                }
            };
            final TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(scope);
            boolean first = true;
            @NonNls final String TYPE_NAME_VAR = "TYPE_NAME_VAR";
            for (GenerationInfo prototype : prototypes) {
                final PsiTypeElement typeElement = PropertyUtil.getPropertyTypeElement(prototype.getPsiMember());
                if (first) {
                    first = false;
                    builder.replaceElement(typeElement, TYPE_NAME_VAR, expression, true);
                }
                else {
                    builder.replaceElement(typeElement, TYPE_NAME_VAR, TYPE_NAME_VAR, false);
                }
            }
            PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.getDocument());
            editor.getCaretModel().moveToOffset(scope.getTextRange().getStartOffset());
            TemplateManager.getInstance(project).startTemplate(editor, builder.buildInlineTemplate());
        }
        catch (IncorrectOperationException e) {
            LOG.error(e);
        }
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }
}