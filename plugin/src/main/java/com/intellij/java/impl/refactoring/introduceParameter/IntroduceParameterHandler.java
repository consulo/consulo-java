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
package com.intellij.java.impl.refactoring.introduceParameter;

import com.intellij.java.impl.codeInsight.CodeInsightUtil;
import com.intellij.java.impl.codeInsight.completion.JavaCompletionUtil;
import com.intellij.java.impl.refactoring.HelpID;
import com.intellij.java.impl.refactoring.IntroduceHandlerBase;
import com.intellij.java.impl.refactoring.IntroduceParameterRefactoring;
import com.intellij.java.impl.refactoring.introduceField.ElementToWorkOn;
import com.intellij.java.impl.refactoring.ui.MethodCellRenderer;
import com.intellij.java.impl.refactoring.ui.NameSuggestionsGenerator;
import com.intellij.java.impl.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.java.impl.refactoring.util.RefactoringUtil;
import com.intellij.java.impl.refactoring.util.occurrences.ExpressionOccurrenceManager;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.codeStyle.VariableKind;
import consulo.application.ui.wm.IdeFocusManager;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorColors;
import consulo.codeEditor.EditorPopupHelper;
import consulo.codeEditor.ScrollType;
import consulo.codeEditor.markup.HighlighterLayer;
import consulo.codeEditor.markup.HighlighterTargetArea;
import consulo.codeEditor.markup.MarkupModel;
import consulo.codeEditor.markup.RangeHighlighter;
import consulo.colorScheme.EditorColorsManager;
import consulo.colorScheme.TextAttributes;
import consulo.dataContext.DataContext;
import consulo.document.util.TextRange;
import consulo.language.editor.refactoring.introduce.inplace.AbstractInplaceIntroducer;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.rename.SuggestedNameInfo;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.JBList;
import consulo.ui.ex.awt.ScrollPaneFactory;
import consulo.ui.ex.popup.JBPopup;
import consulo.ui.ex.popup.JBPopupFactory;
import consulo.ui.ex.popup.event.JBPopupAdapter;
import consulo.ui.ex.popup.event.LightweightWindowEvent;
import consulo.usage.UsageInfo;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.primitive.ints.IntList;
import consulo.util.collection.primitive.ints.IntLists;
import consulo.util.lang.Pair;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author dsl
 * @since 2002-05-06
 */
public class IntroduceParameterHandler extends IntroduceHandlerBase {
    private static final Logger LOG = Logger.getInstance(IntroduceParameterHandler.class);
    static final LocalizeValue REFACTORING_NAME = RefactoringLocalize.introduceParameterTitle();
    private JBPopup myEnclosingMethodsPopup;
    private InplaceIntroduceParameterPopup myInplaceIntroduceParameterPopup;

    @Override
    @RequiredUIAccess
    public void invoke(@Nonnull final Project project, final Editor editor, PsiFile file, DataContext dataContext) {
        PsiDocumentManager.getInstance(project).commitAllDocuments();
        editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
        ElementToWorkOn.processElementToWorkOn(
            editor,
            file,
            REFACTORING_NAME.get(),
            HelpID.INTRODUCE_PARAMETER,
            project,
            new ElementToWorkOn.ElementsProcessor<>() {
                @Override
                public boolean accept(ElementToWorkOn el) {
                    return true;
                }

                @Override
                @RequiredUIAccess
                public void pass(ElementToWorkOn elementToWorkOn) {
                    if (elementToWorkOn == null) {
                        return;
                    }

                    PsiExpression expr = elementToWorkOn.getExpression();
                    PsiLocalVariable localVar = elementToWorkOn.getLocalVariable();
                    boolean isInvokedOnDeclaration = elementToWorkOn.isInvokedOnDeclaration();

                    invoke(editor, project, expr, localVar, isInvokedOnDeclaration);
                }
            }
        );
    }

    @Override
    @RequiredUIAccess
    protected boolean invokeImpl(@Nonnull Project project, PsiExpression tempExpr, Editor editor) {
        return invoke(editor, project, tempExpr, null, false);
    }

    @Override
    @RequiredUIAccess
    protected boolean invokeImpl(@Nonnull Project project, PsiLocalVariable localVariable, Editor editor) {
        return invoke(editor, project, null, localVariable, true);
    }

    @RequiredUIAccess
    private boolean invoke(
        Editor editor,
        @Nonnull Project project,
        PsiExpression expr,
        PsiLocalVariable localVar,
        boolean invokedOnDeclaration
    ) {
        LOG.assertTrue(!PsiDocumentManager.getInstance(project).hasUncommitedDocuments());
        PsiMethod method;
        if (expr != null) {
            method = Util.getContainingMethod(expr);
        }
        else {
            method = Util.getContainingMethod(localVar);
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("expression:" + expr);
        }

        if (expr == null && localVar == null) {
            LocalizeValue message =
                RefactoringLocalize.cannotPerformRefactoringWithReason(RefactoringLocalize.selectedBlockShouldRepresentAnExpression());
            showErrorMessage(project, message, editor);
            return false;
        }

        if (localVar != null) {
            PsiElement parent = localVar.getParent();
            if (!(parent instanceof PsiDeclarationStatement)) {
                LocalizeValue message = RefactoringLocalize.cannotPerformRefactoringWithReason(
                    RefactoringLocalize.errorWrongCaretPositionLocalOrExpressionName()
                );
                showErrorMessage(project, message, editor);
                return false;
            }
        }

        if (method == null) {
            LocalizeValue message = RefactoringLocalize.cannotPerformRefactoringWithReason(
                RefactoringLocalize.isNotSupportedInTheCurrentContext(REFACTORING_NAME)
            );
            showErrorMessage(project, message, editor);
            return false;
        }

        if (!CommonRefactoringUtil.checkReadOnlyStatus(project, method)) {
            return false;
        }

        PsiType typeByExpression = invokedOnDeclaration ? null : RefactoringUtil.getTypeByExpressionWithExpectedType(expr);
        if (!invokedOnDeclaration && (typeByExpression == null || LambdaUtil.notInferredType(typeByExpression))) {
            LocalizeValue message =
                RefactoringLocalize.cannotPerformRefactoringWithReason(RefactoringLocalize.typeOfTheSelectedExpressionCannotBeDetermined());
            showErrorMessage(project, message, editor);
            return false;
        }

        if (!invokedOnDeclaration && PsiType.VOID.equals(typeByExpression)) {
            LocalizeValue message =
                RefactoringLocalize.cannotPerformRefactoringWithReason(RefactoringLocalize.selectedExpressionHasVoidType());
            showErrorMessage(project, message, editor);
            return false;
        }

        List<PsiMethod> validEnclosingMethods = getEnclosingMethods(method);
        if (validEnclosingMethods.isEmpty()) {
            return false;
        }
        Introducer introducer = new Introducer(project, expr, localVar, editor);
        boolean unitTestMode = project.getApplication().isUnitTestMode();
        if (validEnclosingMethods.size() == 1 || unitTestMode) {
            PsiMethod methodToIntroduceParameterTo = validEnclosingMethods.get(0);
            if (methodToIntroduceParameterTo.findDeepestSuperMethod() == null || unitTestMode) {
                introducer.introduceParameter(methodToIntroduceParameterTo, methodToIntroduceParameterTo);
                return true;
            }
        }

        chooseMethodToIntroduceParameter(editor, validEnclosingMethods, introducer);

        return true;
    }

    @RequiredUIAccess
    private void chooseMethodToIntroduceParameter(
        Editor editor,
        @Nonnull List<PsiMethod> validEnclosingMethods,
        @Nonnull Introducer introducer
    ) {
        AbstractInplaceIntroducer inplaceIntroducer = AbstractInplaceIntroducer.getActiveIntroducer(editor);
        if (inplaceIntroducer instanceof InplaceIntroduceParameterPopup) {
            InplaceIntroduceParameterPopup introduceParameterPopup = (InplaceIntroduceParameterPopup) inplaceIntroducer;
            introducer.introduceParameter(
                introduceParameterPopup.getMethodToIntroduceParameter(),
                introduceParameterPopup.getMethodToSearchFor()
            );
            return;
        }
        JPanel panel = new JPanel(new BorderLayout());
        JCheckBox superMethod = new JCheckBox("Refactor super method", true);
        superMethod.setMnemonic('U');
        panel.add(superMethod, BorderLayout.SOUTH);
        JBList<PsiMethod> list = new JBList<>(validEnclosingMethods);
        list.setVisibleRowCount(5);
        list.setCellRenderer(new MethodCellRenderer());
        list.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setSelectedIndex(0);
        final List<RangeHighlighter> highlighters = new ArrayList<>();
        TextAttributes attributes =
            EditorColorsManager.getInstance().getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
        list.addListSelectionListener(e -> {
            PsiMethod selectedMethod = (PsiMethod) list.getSelectedValue();
            if (selectedMethod == null) {
                return;
            }
            dropHighlighters(highlighters);
            updateView(selectedMethod, editor, attributes, highlighters, superMethod);
        });
        updateView(validEnclosingMethods.get(0), editor, attributes, highlighters, superMethod);
        JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(list);
        scrollPane.setBorder(null);
        panel.add(scrollPane, BorderLayout.CENTER);

        List<Pair<ActionListener, KeyStroke>>
            keyboardActions = Collections.singletonList(Pair.<ActionListener, KeyStroke>create(e -> {
            PsiMethod methodToSearchIn = (PsiMethod) list.getSelectedValue();
            if (myEnclosingMethodsPopup != null && myEnclosingMethodsPopup.isVisible()) {
                myEnclosingMethodsPopup.cancel();
            }

            PsiMethod methodToSearchFor = superMethod.isEnabled() && superMethod.isSelected()
                ? methodToSearchIn.findDeepestSuperMethod() : methodToSearchIn;
            Runnable runnable = () -> introducer.introduceParameter(methodToSearchIn, methodToSearchFor);
            IdeFocusManager.findInstance().doWhenFocusSettlesDown(runnable);
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)));
        myEnclosingMethodsPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(panel, list)
            .setTitle(LocalizeValue.localizeTODO("Introduce parameter to method"))
            .setMovable(false)
            .setResizable(false)
            .setRequestFocus(true)
            .setKeyboardActions(keyboardActions)
            .addListener(new JBPopupAdapter() {
                @Override
                public void onClosed(LightweightWindowEvent event) {
                    dropHighlighters(highlighters);
                }
            })
            .createPopup();
        EditorPopupHelper.getInstance().showPopupInBestPositionFor(editor, myEnclosingMethodsPopup);
    }

    private static void updateView(
        PsiMethod selectedMethod,
        Editor editor,
        TextAttributes attributes,
        List<RangeHighlighter> highlighters,
        JCheckBox superMethod
    ) {
        MarkupModel markupModel = editor.getMarkupModel();
        PsiIdentifier nameIdentifier = selectedMethod.getNameIdentifier();
        if (nameIdentifier != null) {
            TextRange textRange = nameIdentifier.getTextRange();
            RangeHighlighter rangeHighlighter = markupModel.addRangeHighlighter(
                textRange.getStartOffset(), textRange.getEndOffset(), HighlighterLayer.SELECTION - 1,
                attributes,
                HighlighterTargetArea.EXACT_RANGE
            );
            highlighters.add(rangeHighlighter);
        }
        superMethod.setEnabled(selectedMethod.findDeepestSuperMethod() != null);
    }

    private static void dropHighlighters(List<RangeHighlighter> highlighters) {
        for (RangeHighlighter highlighter : highlighters) {
            highlighter.dispose();
        }
        highlighters.clear();
    }

    protected static NameSuggestionsGenerator createNameSuggestionGenerator(
        PsiExpression expr,
        String propName,
        Project project,
        String enteredName
    ) {
        return type -> {
            JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
            SuggestedNameInfo info = codeStyleManager.suggestVariableName(
                VariableKind.PARAMETER,
                propName,
                expr != null && expr.isValid() ? expr : null,
                type
            );
            if (expr != null && expr.isValid()) {
                info = codeStyleManager.suggestUniqueVariableName(info, expr, true);
            }
            String[] strings = AbstractJavaInplaceIntroducer.appendUnresolvedExprName(
                JavaCompletionUtil.completeVariableNameForRefactoring(codeStyleManager, type, VariableKind.LOCAL_VARIABLE, info),
                expr
            );
            return new SuggestedNameInfo.Delegate(
                enteredName != null ? ArrayUtil.mergeArrays(new String[]{enteredName}, strings) : strings,
                info
            );
        };
    }

    @RequiredUIAccess
    private static void showErrorMessage(Project project, @Nonnull LocalizeValue message, Editor editor) {
        CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.INTRODUCE_PARAMETER);
    }

    @Override
    public void invoke(@Nonnull Project project, @Nonnull PsiElement[] elements, DataContext dataContext) {
        // Never called
        /* do nothing */
    }

    public static List<PsiMethod> getEnclosingMethods(PsiMethod nearest) {
        List<PsiMethod> enclosingMethods = new ArrayList<>();
        enclosingMethods.add(nearest);
        PsiMethod method = nearest;
        while (true) {
            method = PsiTreeUtil.getParentOfType(method, PsiMethod.class, true);
            if (method == null) {
                break;
            }
            enclosingMethods.add(method);
        }
        if (enclosingMethods.size() > 1) {
            List<PsiMethod> methodsNotImplementingLibraryInterfaces = new ArrayList<>();
            for (PsiMethod enclosing : enclosingMethods) {
                PsiMethod[] superMethods = enclosing.findDeepestSuperMethods();
                boolean libraryInterfaceMethod = false;
                for (PsiMethod superMethod : superMethods) {
                    libraryInterfaceMethod |= isLibraryInterfaceMethod(superMethod);
                }
                if (!libraryInterfaceMethod) {
                    methodsNotImplementingLibraryInterfaces.add(enclosing);
                }
            }
            if (methodsNotImplementingLibraryInterfaces.size() > 0) {
                return methodsNotImplementingLibraryInterfaces;
            }
        }
        return enclosingMethods;
    }

    @Nullable
    @RequiredUIAccess
    public static PsiMethod chooseEnclosingMethod(@Nonnull PsiMethod method) {
        List<PsiMethod> validEnclosingMethods = getEnclosingMethods(method);
        if (validEnclosingMethods.size() > 1 && !method.getApplication().isUnitTestMode()) {
            EnclosingMethodSelectionDialog dialog = new EnclosingMethodSelectionDialog(method.getProject(), validEnclosingMethods);
            dialog.show();
            if (!dialog.isOK()) {
                return null;
            }
            method = dialog.getSelectedMethod();
        }
        else if (validEnclosingMethods.size() == 1) {
            method = validEnclosingMethods.get(0);
        }
        return method;
    }

    private static boolean isLibraryInterfaceMethod(PsiMethod method) {
        return method.isAbstract() && !method.getManager().isInProject(method);
    }

    private class Introducer {
        @Nonnull
        private final Project myProject;

        private PsiExpression myExpr;
        private PsiLocalVariable myLocalVar;
        private final Editor myEditor;

        public Introducer(
            @Nonnull Project project,
            PsiExpression expr,
            PsiLocalVariable localVar,
            Editor editor
        ) {
            myProject = project;
            myExpr = expr;
            myLocalVar = localVar;
            myEditor = editor;
        }

        @RequiredUIAccess
        public void introduceParameter(PsiMethod method, PsiMethod methodToSearchFor) {
            if (!CommonRefactoringUtil.checkReadOnlyStatus(myProject, methodToSearchFor)) {
                return;
            }

            PsiExpression[] occurrences = myExpr != null
                ? new ExpressionOccurrenceManager(myExpr, method, null).findExpressionOccurrences()
                // local variable
                : CodeInsightUtil.findReferenceExpressions(method, myLocalVar);

            String enteredName = null;
            boolean replaceAllOccurrences = false;
            boolean delegate = false;
            PsiType initializerType = IntroduceParameterProcessor.getInitializerType(null, myExpr, myLocalVar);

            AbstractInplaceIntroducer activeIntroducer = AbstractInplaceIntroducer.getActiveIntroducer(myEditor);
            if (activeIntroducer != null) {
                activeIntroducer.stopIntroduce(myEditor);
                myExpr = (PsiExpression) activeIntroducer.getExpr();
                myLocalVar = (PsiLocalVariable) activeIntroducer.getLocalVariable();
                occurrences = (PsiExpression[]) activeIntroducer.getOccurrences();
                enteredName = activeIntroducer.getInputName();
                replaceAllOccurrences = activeIntroducer.isReplaceAllOccurrences();
                delegate = ((InplaceIntroduceParameterPopup) activeIntroducer).isGenerateDelegate();
                initializerType = ((AbstractJavaInplaceIntroducer) activeIntroducer).getType();
            }

            boolean mustBeFinal = false;
            for (PsiExpression occurrence : occurrences) {
                if (PsiTreeUtil.getParentOfType(occurrence, PsiClass.class, PsiMethod.class) != method) {
                    mustBeFinal = true;
                    break;
                }
            }

            List<UsageInfo> localVars = new ArrayList<>();
            List<UsageInfo> classMemberRefs = new ArrayList<>();
            List<UsageInfo> params = new ArrayList<>();

            if (myExpr != null) {
                Util.analyzeExpression(myExpr, localVars, classMemberRefs, params);
            }

            String propName = myLocalVar != null
                ? JavaCodeStyleManager.getInstance(myProject).variableNameToPropertyName(myLocalVar.getName(), VariableKind.LOCAL_VARIABLE)
                : null;

            boolean isInplaceAvailableOnDataContext = myEditor != null && myEditor.getSettings().isVariableInplaceRenameEnabled();

            if (myExpr != null) {
                isInplaceAvailableOnDataContext &= myExpr.isPhysical();
            }

            if (isInplaceAvailableOnDataContext && activeIntroducer == null) {
                myInplaceIntroduceParameterPopup = new InplaceIntroduceParameterPopup(
                    myProject,
                    myEditor,
                    classMemberRefs,
                    createTypeSelectorManager(occurrences, initializerType),
                    myExpr,
                    myLocalVar,
                    method,
                    methodToSearchFor,
                    occurrences,
                    getParamsToRemove(method, occurrences),
                    mustBeFinal
                );
                if (myInplaceIntroduceParameterPopup.startInplaceIntroduceTemplate()) {
                    return;
                }
            }
            if (myProject.getApplication().isUnitTestMode()) {
                String parameterName = "anObject";
                boolean isDeleteLocalVariable = true;
                PsiExpression initializer = myLocalVar != null && myExpr == null ? myLocalVar.getInitializer() : myExpr;
                new IntroduceParameterProcessor(
                    myProject,
                    method,
                    methodToSearchFor,
                    initializer,
                    myExpr,
                    myLocalVar,
                    isDeleteLocalVariable,
                    parameterName,
                    replaceAllOccurrences,
                    IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE,
                    mustBeFinal,
                    false,
                    null,
                    getParamsToRemove(method, occurrences)
                ).run();
            }
            else {
                if (myEditor != null) {
                    RefactoringUtil.highlightAllOccurrences(myProject, occurrences, myEditor);
                }
                IntroduceParameterDialog dialog =
                    new IntroduceParameterDialog(
                        myProject,
                        classMemberRefs,
                        occurrences,
                        myLocalVar,
                        myExpr,
                        createNameSuggestionGenerator(myExpr, propName, myProject, enteredName),
                        createTypeSelectorManager(occurrences, initializerType),
                        methodToSearchFor,
                        method,
                        getParamsToRemove(method, occurrences),
                        mustBeFinal
                    );
                dialog.setReplaceAllOccurrences(replaceAllOccurrences);
                dialog.setGenerateDelegate(delegate);
                dialog.show();
                if (myEditor != null) {
                    myEditor.getSelectionModel().removeSelection();
                }
            }
        }

        private TypeSelectorManagerImpl createTypeSelectorManager(PsiExpression[] occurrences, PsiType initializerType) {
            return myExpr != null
                ? new TypeSelectorManagerImpl(myProject, initializerType, myExpr, occurrences)
                : new TypeSelectorManagerImpl(myProject, initializerType, occurrences);
        }

        private IntList getParamsToRemove(PsiMethod method, PsiExpression[] occurrences) {
            PsiExpression expressionToRemoveParamFrom = myExpr;
            if (myExpr == null) {
                expressionToRemoveParamFrom = myLocalVar.getInitializer();
            }
            return expressionToRemoveParamFrom == null
                ? IntLists.newArrayList()
                : Util.findParametersToRemove(method, expressionToRemoveParamFrom, occurrences);
        }
    }

    @Override
    public AbstractInplaceIntroducer getInplaceIntroducer() {
        return myInplaceIntroduceParameterPopup;
    }
}
