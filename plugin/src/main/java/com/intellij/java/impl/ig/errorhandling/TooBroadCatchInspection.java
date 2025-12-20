/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.errorhandling;

import com.intellij.java.impl.ig.psiutils.ExceptionUtils;
import com.intellij.java.language.psi.PsiElementFactory;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.TestUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.codeEditor.ScrollType;
import consulo.deadCodeNotWorking.impl.MultipleCheckboxOptionsPanel;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.fileEditor.FileEditorManager;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.*;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.intellij.lang.annotations.Pattern;

import javax.swing.*;
import java.util.*;

@ExtensionImpl
public class TooBroadCatchInspection extends BaseInspection {
    @SuppressWarnings({"PublicField"})
    public boolean onlyWarnOnRootExceptions = false;

    @SuppressWarnings("PublicField")
    public boolean ignoreInTestCode = false;

    @SuppressWarnings("PublicField")
    public boolean ignoreThrown = false;

    @Nonnull
    @Override
    @Pattern(VALID_ID_PATTERN)
    public String getID() {
        return "OverlyBroadCatchBlock";
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.tooBroadCatchDisplayName();
    }

    @Override
    @Nonnull
    protected String buildErrorString(Object... infos) {
        List<PsiClass> typesMasked = (List<PsiClass>) infos[0];
        String typesMaskedString = typesMasked.get(0).getName();
        if (typesMasked.size() == 1) {
            return InspectionGadgetsLocalize.tooBroadCatchProblemDescriptor(typesMaskedString).get();
        }
        else {
            //Collections.sort(typesMasked);
            int lastTypeIndex = typesMasked.size() - 1;
            for (int i = 1; i < lastTypeIndex; i++) {
                typesMaskedString += ", ";
                typesMaskedString += typesMasked.get(i).getName();
            }
            String lastTypeString = typesMasked.get(lastTypeIndex).getName();
            return InspectionGadgetsLocalize.tooBroadCatchProblemDescriptor1(typesMaskedString, lastTypeString).get();
        }
    }

    @Nonnull
    @Override
    protected InspectionGadgetsFix[] buildFixes(Object... infos) {
        List<PsiClass> maskedTypes = (List<PsiClass>) infos[0];
        List<InspectionGadgetsFix> fixes = new ArrayList();
        for (PsiClass thrown : maskedTypes) {
            fixes.add(new AddCatchSectionFix(thrown));
        }
        return fixes.toArray(new InspectionGadgetsFix[fixes.size()]);
    }

    @Override
    public JComponent createOptionsPanel() {
        MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
        panel.addCheckbox(InspectionGadgetsLocalize.tooBroadCatchOption().get(), "onlyWarnOnRootExceptions");
        panel.addCheckbox(InspectionGadgetsLocalize.ignoreInTestCode().get(), "ignoreInTestCode");
        panel.addCheckbox(InspectionGadgetsLocalize.overlyBroadThrowsClauseIgnoreThrownOption().get(), "ignoreThrown");
        return panel;
    }

    private static class AddCatchSectionFix extends InspectionGadgetsFix {

        private final SmartPsiElementPointer<PsiClass> myThrown;
        private final String myText;

        AddCatchSectionFix(PsiClass thrown) {
            myThrown = SmartPointerManager.getInstance(thrown.getProject()).createSmartPsiElementPointer(thrown);
            myText = thrown.getName();
        }

        @Nonnull
        @Override
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.tooBroadCatchQuickfix(myText);
        }

        @Override
        protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
            PsiElement typeElement = descriptor.getPsiElement();
            if (typeElement == null) {
                return;
            }
            PsiElement catchParameter = typeElement.getParent();
            if (!(catchParameter instanceof PsiParameter)) {
                return;
            }
            PsiElement catchBlock = ((PsiParameter) catchParameter).getDeclarationScope();
            if (!(catchBlock instanceof PsiCatchSection)) {
                return;
            }
            PsiCatchSection myBeforeCatchSection = (PsiCatchSection) catchBlock;
            PsiTryStatement myTryStatement = myBeforeCatchSection.getTryStatement();
            JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
            String name = codeStyleManager.suggestUniqueVariableName("e", myTryStatement.getTryBlock(), false);
            PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
            PsiClass aClass = myThrown.getElement();
            if (aClass == null) {
                return;
            }
            PsiCatchSection section = factory.createCatchSection(factory.createType(aClass), name, myTryStatement);
            PsiCatchSection element = (PsiCatchSection) myTryStatement.addBefore(section, myBeforeCatchSection);
            codeStyleManager.shortenClassReferences(element);

            if (isOnTheFly()) {
                TextRange range = getRangeToSelect(element.getCatchBlock());
                PsiFile file = element.getContainingFile();
                Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
                if (editor == null) {
                    return;
                }
                Document document = PsiDocumentManager.getInstance(project).getDocument(file);
                if (editor.getDocument() != document) {
                    return;
                }
                editor.getCaretModel().moveToOffset(range.getStartOffset());
                editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
                editor.getSelectionModel().setSelection(range.getStartOffset(), range.getEndOffset());
            }
        }
    }

    private static TextRange getRangeToSelect(PsiCodeBlock block) {
        PsiElement first = block.getFirstBodyElement();
        if (first instanceof PsiWhiteSpace) {
            first = first.getNextSibling();
        }
        if (first == null) {
            int offset = block.getTextRange().getStartOffset() + 1;
            return new TextRange(offset, offset);
        }
        PsiElement last = block.getLastBodyElement();
        if (last instanceof PsiWhiteSpace) {
            last = last.getPrevSibling();
        }
        TextRange textRange;
        if (last == null) {
            textRange = first.getTextRange();
        }
        else {
            textRange = last.getTextRange();
        }
        return new TextRange(first.getTextRange().getStartOffset(), textRange.getEndOffset());
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new TooBroadCatchVisitor();
    }

    private class TooBroadCatchVisitor extends BaseInspectionVisitor {

        @Override
        public void visitTryStatement(@Nonnull PsiTryStatement statement) {
            super.visitTryStatement(statement);
            PsiCodeBlock tryBlock = statement.getTryBlock();
            if (tryBlock == null) {
                return;
            }
            if (ignoreInTestCode && TestUtils.isInTestCode(statement)) {
                return;
            }
            Set<PsiClassType> exceptionsThrown = ExceptionUtils.calculateExceptionsThrown(tryBlock);
            int numExceptionsThrown = exceptionsThrown.size();
            Set<PsiType> exceptionsCaught = new HashSet<PsiType>(numExceptionsThrown);
            PsiCatchSection[] catchSections = statement.getCatchSections();
            for (PsiCatchSection catchSection : catchSections) {
                PsiParameter parameter = catchSection.getParameter();
                if (parameter == null) {
                    continue;
                }
                PsiType typeCaught = parameter.getType();
                if (typeCaught instanceof PsiDisjunctionType) {
                    PsiDisjunctionType disjunctionType = (PsiDisjunctionType) typeCaught;
                    List<PsiType> types = disjunctionType.getDisjunctions();
                    for (PsiType type : types) {
                        check(exceptionsThrown, exceptionsCaught, parameter, type);
                    }
                }
                else {
                    check(exceptionsThrown, exceptionsCaught, parameter, typeCaught);
                }
            }
        }

        private void check(Set<PsiClassType> exceptionsThrown, Set<PsiType> exceptionsCaught, PsiParameter parameter, PsiType type) {
            List<PsiClass> maskedExceptions = findMaskedExceptions(exceptionsThrown, exceptionsCaught, type);
            if (maskedExceptions.isEmpty()) {
                return;
            }
            PsiTypeElement typeElement = parameter.getTypeElement();
            if (typeElement == null) {
                return;
            }
            registerError(typeElement, maskedExceptions);
        }

        private List<PsiClass> findMaskedExceptions(Set<PsiClassType> exceptionsThrown, Set<PsiType> exceptionsCaught, PsiType typeCaught) {
            if (exceptionsThrown.contains(typeCaught)) {
                if (ignoreThrown) {
                    return Collections.emptyList();
                }
                exceptionsCaught.add(typeCaught);
                exceptionsThrown.remove(typeCaught);
            }
            if (onlyWarnOnRootExceptions) {
                if (!ExceptionUtils.isGenericExceptionClass(typeCaught)) {
                    return Collections.emptyList();
                }
            }
            List<PsiClass> typesMasked = new ArrayList();
            for (PsiClassType typeThrown : exceptionsThrown) {
                if (!exceptionsCaught.contains(typeThrown) && typeCaught.isAssignableFrom(typeThrown)) {
                    exceptionsCaught.add(typeThrown);
                    PsiClass aClass = typeThrown.resolve();
                    if (aClass != null) {
                        typesMasked.add(aClass);
                    }
                }
            }
            return typesMasked;
        }
    }
}
