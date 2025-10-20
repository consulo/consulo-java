/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.java.impl.testIntegration;

import com.intellij.java.impl.codeInsight.generation.GenerateMembersUtil;
import com.intellij.java.impl.codeInsight.generation.OverrideImplementUtil;
import com.intellij.java.impl.codeInsight.generation.PsiGenerationInfo;
import com.intellij.java.impl.codeInsight.generation.actions.BaseGenerateAction;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.testIntegration.TestFramework;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorPopupHelper;
import consulo.ide.impl.ui.impl.PopupChooserBuilder;
import consulo.language.editor.CodeInsightUtilCore;
import consulo.language.editor.action.CodeInsightActionHandler;
import consulo.language.editor.hint.HintManager;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.psi.PsiCompiledElement;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.ColoredListCellRenderer;
import consulo.ui.ex.awt.JBList;
import consulo.ui.ex.popup.JBPopup;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

public class BaseGenerateTestSupportMethodAction extends BaseGenerateAction {
    protected static final Logger LOG = Logger.getInstance(BaseGenerateTestSupportMethodAction.class);

    public BaseGenerateTestSupportMethodAction(TestIntegrationUtils.MethodKind methodKind, @Nonnull LocalizeValue text) {
        super(new MyHandler(methodKind), text);
    }

    @Override
    @RequiredReadAction
    protected PsiClass getTargetClass(Editor editor, PsiFile file) {
        return findTargetClass(editor, file);
    }

    @Nullable
    @RequiredReadAction
    private static PsiClass findTargetClass(Editor editor, PsiFile file) {
        int offset = editor.getCaretModel().getOffset();
        PsiElement element = file.findElementAt(offset);
        return element == null ? null : TestIntegrationUtils.findOuterClass(element);
    }

    @Override
    protected boolean isValidForClass(PsiClass targetClass) {
        List<TestFramework> frameworks = TestIntegrationUtils.findSuitableFrameworks(targetClass);
        if (frameworks.isEmpty()) {
            return false;
        }

        for (TestFramework each : frameworks) {
            if (isValidFor(targetClass, each)) {
                return true;
            }
        }
        return false;
    }

    @Override
    @RequiredReadAction
    protected boolean isValidForFile(@Nonnull Project project, @Nonnull Editor editor, @Nonnull PsiFile file) {
        if (file instanceof PsiCompiledElement) {
            return false;
        }

        PsiDocumentManager.getInstance(project).commitAllDocuments();

        PsiClass targetClass = getTargetClass(editor, file);
        return targetClass != null && isValidForClass(targetClass);
    }

    protected boolean isValidFor(PsiClass targetClass, TestFramework framework) {
        return true;
    }

    private static class MyHandler implements CodeInsightActionHandler {
        private TestIntegrationUtils.MethodKind myMethodKind;

        private MyHandler(TestIntegrationUtils.MethodKind methodKind) {
            myMethodKind = methodKind;
        }

        @Override
        @RequiredUIAccess
        public void invoke(@Nonnull Project project, @Nonnull Editor editor, @Nonnull PsiFile file) {
            PsiClass targetClass = findTargetClass(editor, file);
            List<TestFramework> frameworks = TestIntegrationUtils.findSuitableFrameworks(targetClass);
            if (frameworks.isEmpty()) {
                return;
            }

            if (frameworks.size() == 1) {
                doGenerate(editor, file, targetClass, frameworks.get(0));
                return;
            }

            JList<TestFramework> list = new JBList<>(frameworks.toArray(new TestFramework[frameworks.size()]));
            list.setCellRenderer(new ColoredListCellRenderer<>() {
                @Override
                protected void customizeCellRenderer(@Nonnull JList jList, TestFramework framework, int i, boolean b, boolean b1) {
                    setIcon(framework.getIcon());
                    append(framework.getName());
                }
            });

            @RequiredUIAccess
            Runnable runnable = () -> {
                TestFramework selected = list.getSelectedValue();
                if (selected == null) {
                    return;
                }
                doGenerate(editor, file, targetClass, selected);
            };

            PopupChooserBuilder builder = new PopupChooserBuilder(list);
            builder.setFilteringEnabled(o -> ((TestFramework) o).getName());

            JBPopup popup = builder
                .setTitle("Choose Framework")
                .setItemChoosenCallback(runnable)
                .setMovable(true)
                .createPopup();

            EditorPopupHelper.getInstance().showPopupInBestPositionFor(editor, popup);
        }

        @RequiredUIAccess
        private void doGenerate(Editor editor, PsiFile file, PsiClass targetClass, TestFramework framework) {
            if (!CommonRefactoringUtil.checkReadOnlyStatus(file)) {
                return;
            }

            Project project = file.getProject();
            project.getApplication().runWriteAction(() -> {
                try {
                    PsiDocumentManager.getInstance(project).commitAllDocuments();
                    PsiMethod method = generateDummyMethod(editor, file);
                    if (method == null) {
                        return;
                    }

                    TestIntegrationUtils.runTestMethodTemplate(myMethodKind, framework, editor, targetClass, method, "name", false);
                }
                catch (IncorrectOperationException e) {
                    HintManager.getInstance().showErrorHint(editor, "Cannot generate method: " + e.getMessage());
                    LOG.warn(e);
                }
            });
        }

        @Nullable
        @RequiredWriteAction
        private static PsiMethod generateDummyMethod(Editor editor, PsiFile file) throws IncorrectOperationException {
            PsiMethod method = TestIntegrationUtils.createDummyMethod(file);
            PsiGenerationInfo<PsiMethod> info = OverrideImplementUtil.createGenerationInfo(method);

            int offset = findOffsetToInsertMethodTo(editor, file);
            GenerateMembersUtil.insertMembersAtOffset(file, offset, Collections.singletonList(info));

            PsiMethod member = info.getPsiMember();
            return member != null ? CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(member) : null;
        }

        @RequiredReadAction
        private static int findOffsetToInsertMethodTo(Editor editor, PsiFile file) {
            int result = editor.getCaretModel().getOffset();

            PsiClass classAtCursor = PsiTreeUtil.getParentOfType(file.findElementAt(result), PsiClass.class, false);

            while (classAtCursor != null && !(classAtCursor.getParent() instanceof PsiFile)) {
                result = classAtCursor.getTextRange().getEndOffset();
                classAtCursor = PsiTreeUtil.getParentOfType(classAtCursor, PsiClass.class);
            }

            return result;
        }

        @Override
        public boolean startInWriteAction() {
            return false;
        }
    }
}
