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
package com.intellij.java.impl.refactoring.introduceVariable;

import com.intellij.java.impl.codeInsight.CodeInsightUtil;
import com.intellij.java.impl.codeInsight.completion.JavaCompletionUtil;
import com.intellij.java.impl.codeInsight.intention.impl.TypeExpression;
import com.intellij.java.impl.refactoring.HelpID;
import com.intellij.java.impl.refactoring.IntroduceHandlerBase;
import com.intellij.java.impl.refactoring.JavaRefactoringSettings;
import com.intellij.java.impl.refactoring.introduceField.ElementToWorkOn;
import com.intellij.java.impl.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.java.impl.refactoring.util.FieldConflictsResolver;
import com.intellij.java.impl.refactoring.util.RefactoringUtil;
import com.intellij.java.impl.refactoring.util.occurrences.ExpressionOccurrenceManager;
import com.intellij.java.impl.refactoring.util.occurrences.NotInSuperCallOccurrenceFilter;
import com.intellij.java.language.impl.psi.impl.PsiDiamondTypeUtil;
import com.intellij.java.language.impl.psi.impl.source.tree.java.ReplaceExpressionUtil;
import com.intellij.java.language.impl.psi.scope.processor.VariablesProcessor;
import com.intellij.java.language.impl.psi.scope.util.PsiScopesUtil;
import com.intellij.java.language.psi.PsiElementFactory;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.codeStyle.VariableKind;
import com.intellij.java.language.psi.util.PsiExpressionTrimRenderer;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.application.ApplicationPropertiesComponent;
import consulo.codeEditor.*;
import consulo.dataContext.DataContext;
import consulo.document.Document;
import consulo.document.FileDocumentManager;
import consulo.document.RangeMarker;
import consulo.document.util.TextRange;
import consulo.externalService.statistic.FeatureUsageTracker;
import consulo.language.codeStyle.CodeStyleSettingsManager;
import consulo.language.editor.completion.lookup.LookupManager;
import consulo.language.editor.highlight.HighlightManager;
import consulo.language.editor.inject.EditorWindow;
import consulo.language.editor.refactoring.RefactoringSupportProvider;
import consulo.language.editor.refactoring.introduce.IntroduceTargetChooser;
import consulo.language.editor.refactoring.introduce.inplace.AbstractInplaceIntroducer;
import consulo.language.editor.refactoring.introduce.inplace.OccurrencesChooser;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.rename.SuggestedNameInfo;
import consulo.language.editor.refactoring.ui.RefactoringUIUtil;
import consulo.language.editor.refactoring.unwrap.ScopeHighlighter;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.editor.util.ProductivityFeatureNames;
import consulo.language.inject.InjectedLanguageManager;
import consulo.language.psi.*;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.undoRedo.CommandProcessor;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.MultiMap;
import consulo.util.dataholder.Key;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ref.SimpleReference;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;
import java.util.function.Consumer;

/**
 * @author dsl
 * @since 2002-11-15
 */
public abstract class IntroduceVariableBase extends IntroduceHandlerBase {
    private static final Logger LOG = Logger.getInstance(IntroduceVariableBase.class);
    private static final String PREFER_STATEMENTS_OPTION = "introduce.variable.prefer.statements";

    protected static final LocalizeValue REFACTORING_NAME = RefactoringLocalize.introduceVariableTitle();
    public static final Key<Boolean> NEED_PARENTHESIS = Key.create("NEED_PARENTHESIS");

    public static SuggestedNameInfo getSuggestedName(@Nullable PsiType type, @Nonnull PsiExpression expression) {
        return getSuggestedName(type, expression, expression);
    }

    public static SuggestedNameInfo getSuggestedName(@Nullable PsiType type, @Nonnull PsiExpression expression, PsiElement anchor) {
        JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(expression.getProject());
        SuggestedNameInfo nameInfo = codeStyleManager.suggestVariableName(VariableKind.LOCAL_VARIABLE, null, expression, type);
        String[] strings =
            JavaCompletionUtil.completeVariableNameForRefactoring(codeStyleManager, type, VariableKind.LOCAL_VARIABLE, nameInfo);
        SuggestedNameInfo.Delegate delegate = new SuggestedNameInfo.Delegate(strings, nameInfo);
        return codeStyleManager.suggestUniqueVariableName(delegate, anchor, true);
    }

    @Override
    @RequiredUIAccess
    public void invoke(@Nonnull Project project, Editor editor, PsiFile file, DataContext dataContext) {
        SelectionModel selectionModel = editor.getSelectionModel();
        if (!selectionModel.hasSelection()) {
            int offset = editor.getCaretModel().getOffset();
            PsiElement[] statementsInRange = findStatementsAtOffset(editor, file, offset);

            //try line selection
            if (statementsInRange.length == 1 && (!PsiUtil.isStatement(statementsInRange[0]) ||
                statementsInRange[0].getTextRange().getStartOffset() > offset ||
                statementsInRange[0].getTextRange().getEndOffset() < offset ||
                isPreferStatements())) {
                selectionModel.selectLineAtCaret();
                PsiExpression expressionInRange =
                    findExpressionInRange(project, file, selectionModel.getSelectionStart(), selectionModel.getSelectionEnd());
                if (expressionInRange == null || getErrorMessage(expressionInRange) != null) {
                    selectionModel.removeSelection();
                }
            }

            if (!selectionModel.hasSelection()) {
                List<PsiExpression> expressions = collectExpressions(file, editor, offset);
                if (expressions.isEmpty()) {
                    selectionModel.selectLineAtCaret();
                }
                else if (expressions.size() == 1) {
                    TextRange textRange = expressions.get(0).getTextRange();
                    selectionModel.setSelection(textRange.getStartOffset(), textRange.getEndOffset());
                }
                else {
                    int selection;
                    if (statementsInRange.length == 1
                        && statementsInRange[0] instanceof PsiExpressionStatement exprStmt
                        && PsiUtilCore.hasErrorElementChild(exprStmt)) {
                        selection = expressions.indexOf(exprStmt.getExpression());
                    }
                    else if (expressions.get(0) instanceof PsiReferenceExpression refExpr
                        && refExpr.resolve() instanceof PsiLocalVariable) {
                        selection = 1;
                    }
                    else {
                        selection = -1;
                    }
                    IntroduceTargetChooser.showChooser(
                        editor,
                        expressions,
                        selectedValue -> invoke(
                            project,
                            editor,
                            file,
                            selectedValue.getTextRange().getStartOffset(),
                            selectedValue.getTextRange().getEndOffset()
                        ),
                        new PsiExpressionTrimRenderer.RenderFunction(),
                        "Expressions",
                        selection,
                        ScopeHighlighter.NATURAL_RANGER
                    );
                    return;
                }
            }
        }
        if (invoke(project, editor, file, selectionModel.getSelectionStart(), selectionModel.getSelectionEnd())
            && LookupManager.getActiveLookup(editor) == null) {
            selectionModel.removeSelection();
        }
    }

    public static boolean isPreferStatements() {
        return ApplicationPropertiesComponent.getInstance().getBoolean(PREFER_STATEMENTS_OPTION, false);
    }

    @RequiredReadAction
    public static List<PsiExpression> collectExpressions(PsiFile file, Editor editor, int offset) {
        return collectExpressions(file, editor, offset, false);
    }

    @RequiredReadAction
    public static List<PsiExpression> collectExpressions(PsiFile file, Editor editor, int offset, boolean acceptVoid) {
        return collectExpressions(file, editor.getDocument(), offset, acceptVoid);
    }

    @RequiredReadAction
    public static List<PsiExpression> collectExpressions(PsiFile file, Document document, int offset, boolean acceptVoid) {
        CharSequence text = document.getCharsSequence();
        int correctedOffset = offset;
        int textLength = document.getTextLength();
        if (offset >= textLength) {
            correctedOffset = textLength - 1;
        }
        else if (!Character.isJavaIdentifierPart(text.charAt(offset))) {
            correctedOffset--;
        }
        if (correctedOffset < 0) {
            correctedOffset = offset;
        }
        else if (!Character.isJavaIdentifierPart(text.charAt(correctedOffset))) {
            if (text.charAt(correctedOffset) == ';') {//initially caret on the end of line
                correctedOffset--;
            }
            if (correctedOffset < 0 || text.charAt(correctedOffset) != ')') {
                correctedOffset = offset;
            }
        }
        PsiElement elementAtCaret = file.findElementAt(correctedOffset);
        List<PsiExpression> expressions = new ArrayList<>();
        /*
        for (PsiElement element : statementsInRange) {
            if (element instanceof PsiExpressionStatement) {
                final PsiExpression expression = ((PsiExpressionStatement)element).getExpression();
                if (expression.getType() != PsiType.VOID) {
                    expressions.add(expression);
                }
            }
        }*/
        PsiExpression expression = PsiTreeUtil.getParentOfType(elementAtCaret, PsiExpression.class);
        while (expression != null) {
            if (!expressions.contains(expression)
                && !(expression instanceof PsiParenthesizedExpression)
                && !(expression instanceof PsiSuperExpression)
                && (acceptVoid || !PsiType.VOID.equals(expression.getType()))) {
                if (expression instanceof PsiMethodReferenceExpression) {
                    expressions.add(expression);
                }
                else if (!(expression instanceof PsiAssignmentExpression)) {
                    if (expression instanceof PsiReferenceExpression refExpr) {
                        if (!(expression.getParent() instanceof PsiMethodCallExpression call)) {
                            PsiElement resolve = refExpr.resolve();
                            if (!(resolve instanceof PsiClass) && !(resolve instanceof PsiPackage)) {
                                expressions.add(expression);
                            }
                        }
                    }
                    else {
                        expressions.add(expression);
                    }
                }
            }
            expression = PsiTreeUtil.getParentOfType(expression, PsiExpression.class);
        }
        return expressions;
    }

    public static PsiElement[] findStatementsAtOffset(Editor editor, PsiFile file, int offset) {
        Document document = editor.getDocument();
        int lineNumber = document.getLineNumber(offset);
        int lineStart = document.getLineStartOffset(lineNumber);
        int lineEnd = document.getLineEndOffset(lineNumber);

        return CodeInsightUtil.findStatementsInRange(file, lineStart, lineEnd);
    }

    @RequiredUIAccess
    private boolean invoke(Project project, Editor editor, PsiFile file, int startOffset, int endOffset) {
        FeatureUsageTracker.getInstance().triggerFeatureUsed(ProductivityFeatureNames.REFACTORING_INTRODUCE_VARIABLE);
        PsiDocumentManager.getInstance(project).commitAllDocuments();

        return invokeImpl(project, findExpressionInRange(project, file, startOffset, endOffset), editor);
    }

    @RequiredReadAction
    private static PsiExpression findExpressionInRange(Project project, PsiFile file, int startOffset, int endOffset) {
        PsiExpression tempExpr = CodeInsightUtil.findExpressionInRange(file, startOffset, endOffset);
        if (tempExpr == null) {
            PsiElement[] statements = CodeInsightUtil.findStatementsInRange(file, startOffset, endOffset);
            if (statements.length == 1) {
                if (statements[0] instanceof PsiExpressionStatement exprStmt) {
                    tempExpr = exprStmt.getExpression();
                }
                else if (statements[0] instanceof PsiReturnStatement returnStmt) {
                    tempExpr = returnStmt.getReturnValue();
                }
            }
        }

        if (tempExpr == null) {
            tempExpr = getSelectedExpression(project, file, startOffset, endOffset);
        }
        return tempExpr;
    }

    /**
     * @return can return NotNull value although extraction will fail: reason could be retrieved from {@link #getErrorMessage(PsiExpression)}
     */
    @RequiredReadAction
    public static PsiExpression getSelectedExpression(Project project, PsiFile file, int startOffset, int endOffset) {
        InjectedLanguageManager injectedLanguageManager = InjectedLanguageManager.getInstance(project);
        PsiElement elementAtStart = file.findElementAt(startOffset);
        if (elementAtStart == null || elementAtStart instanceof PsiWhiteSpace || elementAtStart instanceof PsiComment) {
            elementAtStart = PsiTreeUtil.skipSiblingsForward(elementAtStart, PsiWhiteSpace.class, PsiComment.class);
            if (elementAtStart == null) {
                if (injectedLanguageManager.isInjectedFragment(file)) {
                    return getSelectionFromInjectedHost(project, file, injectedLanguageManager, startOffset, endOffset);
                }
                else {
                    return null;
                }
            }
            startOffset = elementAtStart.getTextOffset();
        }
        PsiElement elementAtEnd = file.findElementAt(endOffset - 1);
        if (elementAtEnd == null || elementAtEnd instanceof PsiWhiteSpace || elementAtEnd instanceof PsiComment) {
            elementAtEnd = PsiTreeUtil.skipSiblingsBackward(elementAtEnd, PsiWhiteSpace.class, PsiComment.class);
            if (elementAtEnd == null) {
                return null;
            }
            endOffset = elementAtEnd.getTextRange().getEndOffset();
        }

        if (endOffset <= startOffset) {
            return null;
        }

        PsiElement elementAt = PsiTreeUtil.findCommonParent(elementAtStart, elementAtEnd);
        if (PsiTreeUtil.getParentOfType(elementAt, PsiExpression.class, false) == null) {
            if (injectedLanguageManager.isInjectedFragment(file)) {
                return getSelectionFromInjectedHost(project, file, injectedLanguageManager, startOffset, endOffset);
            }
            elementAt = null;
        }
        PsiLiteralExpression literalExpression = PsiTreeUtil.getParentOfType(elementAt, PsiLiteralExpression.class);

        PsiLiteralExpression startLiteralExpression = PsiTreeUtil.getParentOfType(elementAtStart, PsiLiteralExpression.class);
        PsiLiteralExpression endLiteralExpression =
            PsiTreeUtil.getParentOfType(file.findElementAt(endOffset), PsiLiteralExpression.class);

        PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();
        String text = null;
        PsiExpression tempExpr;
        try {
            text = file.getText().subSequence(startOffset, endOffset).toString();
            String prefix = null;
            String stripped = text;
            if (startLiteralExpression != null) {
                int startExpressionOffset = startLiteralExpression.getTextOffset();
                if (startOffset == startExpressionOffset) {
                    if (StringUtil.startsWithChar(text, '\"') || StringUtil.startsWithChar(text, '\'')) {
                        stripped = text.substring(1);
                    }
                }
                else if (startOffset == startExpressionOffset + 1) {
                    text = "\"" + text;
                }
                else if (startOffset > startExpressionOffset + 1) {
                    prefix = "\" + ";
                    text = "\"" + text;
                }
            }

            String suffix = null;
            if (endLiteralExpression != null) {
                int endExpressionOffset = endLiteralExpression.getTextOffset() + endLiteralExpression.getTextLength();
                if (endOffset == endExpressionOffset) {
                    if (StringUtil.endsWithChar(stripped, '\"') || StringUtil.endsWithChar(stripped, '\'')) {
                        stripped = stripped.substring(0, stripped.length() - 1);
                    }
                }
                else if (endOffset == endExpressionOffset - 1) {
                    text += "\"";
                }
                else if (endOffset < endExpressionOffset - 1) {
                    suffix = " + \"";
                    text += "\"";
                }
            }

            boolean primitive = false;
            if (stripped.equals("true") || stripped.equals("false")) {
                primitive = true;
            }
            else {
                try {
                    Integer.parseInt(stripped);
                    primitive = true;
                }
                catch (NumberFormatException e1) {
                    //then not primitive
                }
            }

            if (primitive) {
                text = stripped;
            }

            if (literalExpression != null && text.equals(literalExpression.getText())) {
                return literalExpression;
            }

            PsiElement parent = literalExpression != null ? literalExpression : elementAt;
            tempExpr = elementFactory.createExpressionFromText(text, parent);

            final boolean[] hasErrors = new boolean[1];
            JavaRecursiveElementWalkingVisitor errorsVisitor = new JavaRecursiveElementWalkingVisitor() {
                @Override
                public void visitElement(PsiElement element) {
                    if (hasErrors[0]) {
                        return;
                    }
                    super.visitElement(element);
                }

                @Override
                public void visitErrorElement(PsiErrorElement element) {
                    hasErrors[0] = true;
                }
            };
            tempExpr.accept(errorsVisitor);
            if (hasErrors[0]) {
                return null;
            }

            tempExpr.putUserData(ElementToWorkOn.PREFIX, prefix);
            tempExpr.putUserData(ElementToWorkOn.SUFFIX, suffix);

            RangeMarker rangeMarker = FileDocumentManager.getInstance().getDocument(file.getVirtualFile())
                .createRangeMarker(startOffset, endOffset);
            tempExpr.putUserData(ElementToWorkOn.TEXT_RANGE, rangeMarker);

            if (parent != null) {
                tempExpr.putUserData(ElementToWorkOn.PARENT, parent);
            }
            else {
                PsiErrorElement errorElement = elementAtStart instanceof PsiErrorElement errorElem
                    ? errorElem
                    : PsiTreeUtil.getNextSiblingOfType(elementAtStart, PsiErrorElement.class);
                if (errorElement == null) {
                    errorElement = PsiTreeUtil.getParentOfType(elementAtStart, PsiErrorElement.class);
                }
                if (errorElement == null) {
                    return null;
                }
                if (!(errorElement.getParent() instanceof PsiClass)) {
                    return null;
                }
                tempExpr.putUserData(ElementToWorkOn.PARENT, errorElement);
                tempExpr.putUserData(ElementToWorkOn.OUT_OF_CODE_BLOCK, Boolean.TRUE);
            }

            String fakeInitializer = "intellijidearulezzz";
            int[] refIdx = new int[1];
            PsiExpression toBeExpression = createReplacement(fakeInitializer, project, prefix, suffix, parent, rangeMarker, refIdx);
            toBeExpression.accept(errorsVisitor);
            if (hasErrors[0]) {
                return null;
            }
            if (literalExpression != null) {
                PsiType type = toBeExpression.getType();
                if (type != null && !type.equals(literalExpression.getType())) {
                    return null;
                }
            }

            PsiReferenceExpression refExpr = PsiTreeUtil.getParentOfType(
                toBeExpression.findElementAt(refIdx[0]),
                PsiReferenceExpression.class
            );
            if (refExpr == null) {
                return null;
            }
            if (toBeExpression == refExpr && refIdx[0] > 0) {
                return null;
            }
            if (ReplaceExpressionUtil.isNeedParenthesis(refExpr.getNode(), tempExpr.getNode())) {
                tempExpr.putCopyableUserData(NEED_PARENTHESIS, Boolean.TRUE);
                return tempExpr;
            }
        }
        catch (IncorrectOperationException e) {
            return elementAt instanceof PsiExpressionList exprList && exprList.getParent() instanceof PsiCallExpression call
                ? createArrayCreationExpression(text, startOffset, endOffset, call) : null;
        }

        return tempExpr;
    }

    @RequiredReadAction
    private static PsiExpression getSelectionFromInjectedHost(
        Project project,
        PsiFile file,
        InjectedLanguageManager injectedLanguageManager,
        int startOffset,
        int endOffset
    ) {
        PsiLanguageInjectionHost injectionHost = injectedLanguageManager.getInjectionHost(file);
        return getSelectedExpression(
            project,
            injectionHost.getContainingFile(),
            injectedLanguageManager.injectedToHost(file, startOffset),
            injectedLanguageManager.injectedToHost(file, endOffset)
        );
    }

    @Nullable
    public static String getErrorMessage(PsiExpression expr) {
        Boolean needParenthesis = expr.getCopyableUserData(NEED_PARENTHESIS);
        if (needParenthesis != null && needParenthesis) {
            return "Extracting selected expression would change the semantic of the whole expression.";
        }
        return null;
    }

    @RequiredReadAction
    private static PsiExpression createArrayCreationExpression(String text, int startOffset, int endOffset, PsiCallExpression parent) {
        if (text == null || parent == null) {
            return null;
        }
        String[] varargsExpressions = text.split("s*,s*");
        if (varargsExpressions.length > 1) {
            PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(parent.getProject());
            JavaResolveResult resolveResult = parent.resolveMethodGenerics();
            PsiMethod psiMethod = (PsiMethod) resolveResult.getElement();
            if (psiMethod == null || !psiMethod.isVarArgs()) {
                return null;
            }
            PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
            PsiParameter varargParameter = parameters[parameters.length - 1];
            PsiType type = varargParameter.getType();
            LOG.assertTrue(type instanceof PsiEllipsisType);
            PsiArrayType psiType = (PsiArrayType) ((PsiEllipsisType) type).toArrayType();
            PsiExpression[] args = parent.getArgumentList().getExpressions();
            PsiSubstitutor psiSubstitutor = resolveResult.getSubstitutor();

            if (args.length < parameters.length || startOffset < args[parameters.length - 1].getTextRange().getStartOffset()) {
                return null;
            }

            PsiFile containingFile = parent.getContainingFile();

            PsiElement startElement = containingFile.findElementAt(startOffset);
            while (startElement != null && startElement.getParent() != parent.getArgumentList()) {
                startElement = startElement.getParent();
            }
            if (startElement == null || startOffset > startElement.getTextOffset()) {
                return null;
            }

            PsiElement endElement = containingFile.findElementAt(endOffset - 1);
            while (endElement != null && endElement.getParent() != parent.getArgumentList()) {
                endElement = endElement.getParent();
            }
            if (endElement == null || endOffset < endElement.getTextRange().getEndOffset()) {
                return null;
            }

            PsiType componentType = TypeConversionUtil.erasure(psiSubstitutor.substitute(psiType.getComponentType()));
            try {
                PsiExpression expressionFromText = elementFactory.createExpressionFromText(
                    "new " + componentType.getCanonicalText() + "[]{" + text + "}",
                    parent
                );
                RangeMarker rangeMarker = FileDocumentManager.getInstance().getDocument(containingFile.getVirtualFile())
                    .createRangeMarker(startOffset, endOffset);
                expressionFromText.putUserData(ElementToWorkOn.TEXT_RANGE, rangeMarker);
                expressionFromText.putUserData(ElementToWorkOn.PARENT, parent);
                return expressionFromText;
            }
            catch (IncorrectOperationException e) {
                return null;
            }
        }
        return null;
    }

    @Override
    @RequiredUIAccess
    protected boolean invokeImpl(@Nonnull Project project, PsiExpression expr, Editor editor) {
        if (expr != null) {
            String errorMessage = getErrorMessage(expr);
            if (errorMessage != null) {
                showErrorMessage(project, editor, RefactoringLocalize.cannotPerformRefactoringWithReason(errorMessage));
                return false;
            }
        }

        if (expr != null && expr.getParent() instanceof PsiExpressionStatement) {
            FeatureUsageTracker.getInstance().triggerFeatureUsed("refactoring.introduceVariable.incompleteStatement");
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("expression:" + expr);
        }

        if (expr == null || !expr.isPhysical()) {
            if (ReassignVariableUtil.reassign(editor)) {
                return false;
            }
            if (expr == null) {
                LocalizeValue message =
                    RefactoringLocalize.cannotPerformRefactoringWithReason(RefactoringLocalize.selectedBlockShouldRepresentAnExpression());
                showErrorMessage(project, editor, message);
                return false;
            }
        }

        PsiType originalType = RefactoringUtil.getTypeByExpressionWithExpectedType(expr);
        if (originalType == null || LambdaUtil.notInferredType(originalType)) {
            LocalizeValue message = RefactoringLocalize.cannotPerformRefactoringWithReason(RefactoringLocalize.unknownExpressionType());
            showErrorMessage(project, editor, message);
            return false;
        }

        if (PsiType.VOID.equals(originalType)) {
            LocalizeValue message =
                RefactoringLocalize.cannotPerformRefactoringWithReason(RefactoringLocalize.selectedExpressionHasVoidType());
            showErrorMessage(project, editor, message);
            return false;
        }

        PsiElement physicalElement = expr.getUserData(ElementToWorkOn.PARENT);

        PsiElement anchorStatement = RefactoringUtil.getParentStatement(physicalElement != null ? physicalElement : expr, false);

        if (anchorStatement == null) {
            return parentStatementNotFound(project, editor);
        }
        if (checkAnchorBeforeThisOrSuper(project, editor, anchorStatement, REFACTORING_NAME, HelpID.INTRODUCE_VARIABLE)) {
            return false;
        }

        PsiElement tempContainer = anchorStatement.getParent();

        if (!(tempContainer instanceof PsiCodeBlock)
            && !RefactoringUtil.isLoopOrIf(tempContainer)
            && (tempContainer.getParent() instanceof PsiLambdaExpression)) {
            LocalizeValue message = RefactoringLocalize.refactoringIsNotSupportedInTheCurrentContext(REFACTORING_NAME);
            showErrorMessage(project, editor, message);
            return false;
        }

        if (!NotInSuperCallOccurrenceFilter.INSTANCE.isOK(expr)) {
            LocalizeValue message =
                RefactoringLocalize.cannotPerformRefactoringWithReason(RefactoringLocalize.cannotIntroduceVariableInSuperConstructorCall());
            showErrorMessage(project, editor, message);
            return false;
        }

        PsiFile file = anchorStatement.getContainingFile();
        LOG.assertTrue(file != null, "expr.getContainingFile() == null");
        PsiElement nameSuggestionContext = editor == null ? null : file.findElementAt(editor.getCaretModel().getOffset());
        RefactoringSupportProvider supportProvider = RefactoringSupportProvider.forLanguage(expr.getLanguage());
        boolean isInplaceAvailableOnDataContext = editor.getSettings().isVariableInplaceRenameEnabled()
            && supportProvider.isInplaceIntroduceAvailable(expr, nameSuggestionContext)
            && (!project.getApplication().isUnitTestMode() || isInplaceAvailableInTestMode())
            && !isInJspHolderMethod(expr);

        if (isInplaceAvailableOnDataContext) {
            MultiMap<PsiElement, LocalizeValue> conflicts = new MultiMap<>();
            checkInLoopCondition(expr, conflicts);
            if (!conflicts.isEmpty()) {
                showErrorMessage(project, editor, LocalizeValue.localizeTODO(StringUtil.join(conflicts.values(), "<br>")));
                return false;
            }
        }

        ExpressionOccurrenceManager occurrenceManager = createOccurrenceManager(expr, tempContainer);
        PsiExpression[] occurrences = occurrenceManager.getOccurrences();
        PsiElement anchorStatementIfAll = occurrenceManager.getAnchorStatementForAll();

        List<PsiExpression> nonWrite = new ArrayList<>();
        boolean cantReplaceAll = false;
        boolean cantReplaceAllButWrite = false;
        for (PsiExpression occurrence : occurrences) {
            if (!RefactoringUtil.isAssignmentLHS(occurrence)) {
                nonWrite.add(occurrence);
            }
            else if (isFinalVariableOnLHS(occurrence)) {
                cantReplaceAll = true;
            }
            else if (!nonWrite.isEmpty()) {
                cantReplaceAllButWrite = true;
                cantReplaceAll = true;
            }
        }
        if (!CommonRefactoringUtil.checkReadOnlyStatus(project, file)) {
            return false;
        }

        Map<OccurrencesChooser.ReplaceChoice, List<PsiExpression>> occurrencesMap = new LinkedHashMap<>();
        occurrencesMap.put(OccurrencesChooser.ReplaceChoice.NO, Collections.singletonList(expr));
        boolean hasWriteAccess = occurrences.length > nonWrite.size() && occurrences.length > 1;
        if (hasWriteAccess && !cantReplaceAllButWrite) {
            occurrencesMap.put(OccurrencesChooser.ReplaceChoice.NO_WRITE, nonWrite);
        }

        if (occurrences.length > 1 && !cantReplaceAll) {
            occurrencesMap.put(OccurrencesChooser.ReplaceChoice.ALL, Arrays.asList(occurrences));
        }

        boolean inFinalContext = occurrenceManager.isInFinalContext();
        InputValidator validator = new InputValidator(this, project, anchorStatementIfAll, anchorStatement, occurrenceManager);
        TypeSelectorManagerImpl typeSelectorManager = new TypeSelectorManagerImpl(project, originalType, expr, occurrences);
        boolean[] wasSucceed = new boolean[]{true};
        @RequiredWriteAction
        Consumer<OccurrencesChooser.ReplaceChoice> callback = choice -> {
            boolean allOccurrences =
                choice == OccurrencesChooser.ReplaceChoice.ALL || choice == OccurrencesChooser.ReplaceChoice.NO_WRITE;
            SimpleReference<SmartPsiElementPointer<PsiVariable>> variable = new SimpleReference<>();

            Editor topLevelEditor;
            if (!InjectedLanguageManager.getInstance(project).isInjectedFragment(anchorStatement.getContainingFile())) {
                topLevelEditor = EditorWindow.getTopLevelEditor(editor);
            }
            else {
                topLevelEditor = editor;
            }

            IntroduceVariableSettings settings;
            PsiElement chosenAnchor;
            if (choice != null) {
                chosenAnchor = chooseAnchor(
                    allOccurrences,
                    choice == OccurrencesChooser.ReplaceChoice.NO_WRITE,
                    nonWrite,
                    anchorStatementIfAll,
                    anchorStatement
                );
                settings = getSettings(
                    project,
                    topLevelEditor,
                    expr,
                    occurrences,
                    typeSelectorManager,
                    inFinalContext,
                    hasWriteAccess,
                    validator,
                    chosenAnchor,
                    choice
                );
            }
            else {
                settings = getSettings(
                    project,
                    topLevelEditor,
                    expr,
                    occurrences,
                    typeSelectorManager,
                    inFinalContext,
                    hasWriteAccess,
                    validator,
                    anchorStatement,
                    choice
                );
                chosenAnchor =
                    chooseAnchor(settings.isReplaceAllOccurrences(), hasWriteAccess, nonWrite, anchorStatementIfAll, anchorStatement);
            }
            if (!settings.isOK()) {
                wasSucceed[0] = false;
                return;
            }
            typeSelectorManager.setAllOccurrences(allOccurrences);
            TypeExpression expression = new TypeExpression(
                project,
                allOccurrences ? typeSelectorManager.getTypesForAll() : typeSelectorManager.getTypesForOne()
            );
            RangeMarker exprMarker = topLevelEditor.getDocument().createRangeMarker(expr.getTextRange());
            SuggestedNameInfo suggestedName = getSuggestedName(settings.getSelectedType(), expr, chosenAnchor);
            List<RangeMarker> occurrenceMarkers = new ArrayList<>();
            boolean noWrite = choice == OccurrencesChooser.ReplaceChoice.NO_WRITE;
            for (PsiExpression occurrence : occurrences) {
                if (allOccurrences || (noWrite && !PsiUtil.isAccessedForWriting(occurrence))) {
                    occurrenceMarkers.add(topLevelEditor.getDocument().createRangeMarker(occurrence.getTextRange()));
                }
            }
            String expressionText = expr.getText();
            Runnable runnable = introduce(project, expr, topLevelEditor, chosenAnchor, occurrences, settings, variable);
            CommandProcessor.getInstance().newCommand()
                .project(project)
                .name(REFACTORING_NAME)
                .run(() -> {
                    project.getApplication().runWriteAction(runnable);
                    if (isInplaceAvailableOnDataContext) {
                        PsiVariable elementToRename = variable.get().getElement();
                        if (elementToRename != null) {
                            topLevelEditor.getCaretModel().moveToOffset(elementToRename.getTextOffset());
                            boolean cantChangeFinalModifier = (hasWriteAccess || inFinalContext)
                                && choice == OccurrencesChooser.ReplaceChoice.ALL;
                            JavaVariableInplaceIntroducer renamer = new JavaVariableInplaceIntroducer(
                                project,
                                expression,
                                topLevelEditor,
                                elementToRename,
                                cantChangeFinalModifier,
                                typeSelectorManager.getTypesForAll().length > 1,
                                exprMarker,
                                occurrenceMarkers,
                                REFACTORING_NAME.get()
                            );
                            renamer.initInitialText(expressionText);
                            PsiDocumentManager.getInstance(project)
                                .doPostponedOperationsAndUnblockDocument(topLevelEditor.getDocument());
                            renamer.performInplaceRefactoring(new LinkedHashSet<>(Arrays.asList(suggestedName.names)));
                        }
                    }
                });
        };

        if (!isInplaceAvailableOnDataContext) {
            callback.accept(null);
        }
        else {
            OccurrencesChooser.<PsiExpression>simpleChooser(editor).showChooser(callback, occurrencesMap);
        }
        return wasSucceed[0];
    }

    protected PsiElement chooseAnchor(
        boolean allOccurrences,
        boolean hasWriteAccess,
        List<PsiExpression> nonWrite,
        PsiElement anchorStatementIfAll,
        PsiElement anchorStatement
    ) {
        if (!allOccurrences) {
            return anchorStatement;
        }
        else if (hasWriteAccess) {
            return RefactoringUtil.getAnchorElementForMultipleExpressions(nonWrite.toArray(new PsiExpression[nonWrite.size()]), null);
        }
        else {
            return anchorStatementIfAll;
        }
    }

    protected boolean isInplaceAvailableInTestMode() {
        return false;
    }

    private static ExpressionOccurrenceManager createOccurrenceManager(PsiExpression expr, PsiElement tempContainer) {
        boolean skipForStatement = true;
        final PsiForStatement forStatement = PsiTreeUtil.getParentOfType(expr, PsiForStatement.class);
        if (forStatement != null) {
            VariablesProcessor variablesProcessor = new VariablesProcessor(false) {
                @Override
                protected boolean check(PsiVariable var, ResolveState state) {
                    return PsiTreeUtil.isAncestor(forStatement.getInitialization(), var, true);
                }
            };
            PsiScopesUtil.treeWalkUp(variablesProcessor, expr, null);
            skipForStatement = variablesProcessor.size() == 0;
        }

        PsiElement containerParent = tempContainer;
        PsiElement lastScope = tempContainer;
        while (true) {
            if (containerParent instanceof PsiFile) {
                break;
            }
            if (containerParent instanceof PsiMethod) {
                break;
            }
            if (containerParent instanceof PsiLambdaExpression) {
                break;
            }
            if (!skipForStatement && containerParent instanceof PsiForStatement) {
                break;
            }
            containerParent = containerParent.getParent();
            if (containerParent instanceof PsiCodeBlock) {
                lastScope = containerParent;
            }
        }

        return new ExpressionOccurrenceManager(expr, lastScope, NotInSuperCallOccurrenceFilter.INSTANCE);
    }

    private static boolean isInJspHolderMethod(PsiExpression expr) {
        /*final PsiElement parent1 = expr.getParent();
        if(parent1 == null)   */
        {
            return false;
        }
        /*final PsiElement parent2 = parent1.getParent();
        if(!(parent2 instanceof JspCodeBlock))
        {
            return false;
        } */
        /*final PsiElement parent3 = parent2.getParent();
        return parent3 instanceof JspHolderMethod;    */
    }

    @RequiredWriteAction
    private static Runnable introduce(
        final Project project,
        final PsiExpression expr,
        final Editor editor,
        final PsiElement anchorStatement,
        final PsiExpression[] occurrences,
        final IntroduceVariableSettings settings,
        final SimpleReference<SmartPsiElementPointer<PsiVariable>> variable
    ) {
        final PsiElement container = anchorStatement.getParent();
        PsiElement child = anchorStatement;
        if (!RefactoringUtil.isLoopOrIf(container)) {
            child = locateAnchor(child);
            if (isFinalVariableOnLHS(expr)) {
                child = child.getNextSibling();
            }
        }
        final PsiElement anchor = child == null ? anchorStatement : child;

        boolean tempDeleteSelf = false;
        final boolean replaceSelf = settings.isReplaceLValues() || !RefactoringUtil.isAssignmentLHS(expr);
        if (!RefactoringUtil.isLoopOrIf(container)) {
            if (expr.getParent() instanceof PsiExpressionStatement statement && anchor.equals(anchorStatement)) {
                PsiElement parent = statement.getParent();
                if (parent instanceof PsiCodeBlock
                    //fabrique
                    || parent instanceof PsiCodeFragment) {
                    tempDeleteSelf = true;
                }
            }
            tempDeleteSelf &= replaceSelf;
        }
        final boolean deleteSelf = tempDeleteSelf;


        final int col = editor != null ? editor.getCaretModel().getLogicalPosition().column : 0;
        final int line = editor != null ? editor.getCaretModel().getLogicalPosition().line : 0;
        if (deleteSelf) {
            if (editor != null) {
                LogicalPosition pos = new LogicalPosition(line, col);
                editor.getCaretModel().moveToLogicalPosition(pos);
            }
        }

        PsiCodeBlock newDeclarationScope = PsiTreeUtil.getParentOfType(container, PsiCodeBlock.class, false);
        final FieldConflictsResolver fieldConflictsResolver = new FieldConflictsResolver(settings.getEnteredName(), newDeclarationScope);
        return new Runnable() {
            @Override
            @RequiredWriteAction
            public void run() {
                try {
                    PsiStatement statement = null;
                    boolean isInsideLoop = RefactoringUtil.isLoopOrIf(container);
                    if (!isInsideLoop && deleteSelf) {
                        statement = (PsiStatement) expr.getParent();
                    }

                    PsiExpression expr1 = fieldConflictsResolver.fixInitializer(expr);
                    PsiExpression initializer = RefactoringUtil.unparenthesizeExpression(expr1);
                    SmartTypePointer selectedType = SmartTypePointerManager.getInstance(project)
                        .createSmartTypePointer(settings.getSelectedType());
                    if (expr1 instanceof PsiNewExpression newExpression) {
                        if (newExpression.getArrayInitializer() != null) {
                            initializer = newExpression.getArrayInitializer();
                        }
                        initializer = replaceExplicitWithDiamondWhenApplicable(initializer, selectedType.getType());
                    }

                    PsiDeclarationStatement declaration = JavaPsiFacade.getInstance(project).getElementFactory()
                        .createVariableDeclarationStatement(settings.getEnteredName(), selectedType.getType(), initializer);
                    if (!isInsideLoop) {
                        declaration = addDeclaration(declaration, initializer);
                        LOG.assertTrue(expr1.isValid());
                        if (deleteSelf) { // never true
                            // keep trailing comment
                            if (statement.getLastChild() instanceof PsiComment comment) {
                                declaration.addBefore(comment, null);
                            }
                            statement.delete();
                            if (editor != null) {
                                LogicalPosition pos = new LogicalPosition(line, col);
                                editor.getCaretModel().moveToLogicalPosition(pos);
                                editor.getCaretModel().moveToOffset(declaration.getTextRange().getEndOffset());
                                editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
                                editor.getSelectionModel().removeSelection();
                            }
                        }
                    }

                    PsiExpression ref = JavaPsiFacade.getInstance(project).getElementFactory().createExpressionFromText(
                        settings.getEnteredName(),
                        null
                    );
                    if (settings.isReplaceAllOccurrences()) {
                        List<PsiElement> array = new ArrayList<>();
                        for (PsiExpression occurrence : occurrences) {
                            if (deleteSelf && occurrence.equals(expr)) {
                                continue;
                            }
                            if (occurrence.equals(expr)) {
                                occurrence = expr1;
                            }
                            if (occurrence != null) {
                                occurrence = RefactoringUtil.outermostParenthesizedExpression(occurrence);
                            }
                            if (settings.isReplaceLValues() || !RefactoringUtil.isAssignmentLHS(occurrence)) {
                                array.add(replace(occurrence, ref, project));
                            }
                        }

                        if (!deleteSelf && replaceSelf && expr1 instanceof PsiPolyadicExpression && expr1.isValid() && !expr1.isPhysical()) {
                            array.add(replace(expr1, ref, project));
                        }

                        if (editor != null) {
                            PsiElement[] replacedOccurrences = PsiUtilCore.toPsiElementArray(array);
                            highlightReplacedOccurences(project, editor, replacedOccurrences);
                        }
                    }
                    else {
                        if (!deleteSelf && replaceSelf) {
                            replace(expr1, ref, project);
                        }
                    }

                    declaration = (PsiDeclarationStatement) RefactoringUtil.putStatementInLoopBody(declaration, container, anchorStatement);
                    declaration = (PsiDeclarationStatement) JavaCodeStyleManager.getInstance(project).shortenClassReferences(declaration);
                    PsiVariable var = (PsiVariable) declaration.getDeclaredElements()[0];
                    PsiUtil.setModifierProperty(var, PsiModifier.FINAL, settings.isDeclareFinal());
                    variable.set(SmartPointerManager.getInstance(project).createSmartPsiElementPointer(var));
                    fieldConflictsResolver.fix();
                }
                catch (IncorrectOperationException e) {
                    LOG.error(e);
                }
            }

            @RequiredWriteAction
            private PsiDeclarationStatement addDeclaration(PsiDeclarationStatement declaration, PsiExpression initializer) {
                if (anchor instanceof PsiDeclarationStatement anchorDecl) {
                    final PsiElement[] declaredElements = anchorDecl.getDeclaredElements();
                    if (declaredElements.length > 1) {
                        final int[] usedFirstVar = new int[]{-1};
                        initializer.accept(new JavaRecursiveElementWalkingVisitor() {
                            @Override
                            @RequiredReadAction
                            public void visitReferenceExpression(PsiReferenceExpression expression) {
                                int i = ArrayUtil.find(declaredElements, expression.resolve());
                                if (i > -1) {
                                    usedFirstVar[0] = Math.max(i, usedFirstVar[0]);
                                }
                                super.visitReferenceExpression(expression);
                            }
                        });
                        if (usedFirstVar[0] > -1) {
                            PsiVariable psiVariable = (PsiVariable) declaredElements[usedFirstVar[0]];
                            psiVariable.normalizeDeclaration();
                            PsiDeclarationStatement parDeclarationStatement =
                                PsiTreeUtil.getParentOfType(psiVariable, PsiDeclarationStatement.class);
                            return (PsiDeclarationStatement) container.addAfter(declaration, parDeclarationStatement);
                        }
                    }
                }
                return (PsiDeclarationStatement) container.addBefore(declaration, anchor);
            }
        };
    }

    @RequiredReadAction
    private static boolean isFinalVariableOnLHS(PsiExpression expr) {
        //should be inserted after assignment
        return expr instanceof PsiReferenceExpression refExpr
            && RefactoringUtil.isAssignmentLHS(expr)
            && refExpr.resolve() instanceof PsiVariable variable
            && variable.hasModifierProperty(PsiModifier.FINAL);
    }

    public static PsiExpression replaceExplicitWithDiamondWhenApplicable(PsiExpression initializer, PsiType expectedType) {
        if (initializer instanceof PsiNewExpression newExpression) {
            PsiExpression tryToDetectDiamondNewExpr = ((PsiVariable) JavaPsiFacade.getElementFactory(initializer.getProject())
                .createVariableDeclarationStatement("x", expectedType, initializer).getDeclaredElements()[0]).getInitializer();
            if (tryToDetectDiamondNewExpr instanceof PsiNewExpression newExpr
                && PsiDiamondTypeUtil.canCollapseToDiamond(newExpr, newExpr, expectedType)) {
                PsiElement paramList = PsiDiamondTypeUtil.replaceExplicitWithDiamond(
                    newExpression.getClassOrAnonymousClassReference().getParameterList()
                );
                return PsiTreeUtil.getParentOfType(paramList, PsiNewExpression.class);
            }
        }
        return initializer;
    }

    @RequiredWriteAction
    public static PsiElement replace(PsiExpression expr1, PsiExpression ref, Project project) throws IncorrectOperationException {
        PsiExpression expr2;
        if (expr1 instanceof PsiArrayInitializerExpression arrayInit && arrayInit.getParent() instanceof PsiNewExpression arrayNew) {
            expr2 = arrayNew;
        }
        else {
            expr2 = RefactoringUtil.outermostParenthesizedExpression(expr1);
        }
        if (expr2.isPhysical()) {
            return expr2.replace(ref);
        }
        else {
            String prefix = expr1.getUserData(ElementToWorkOn.PREFIX);
            String suffix = expr1.getUserData(ElementToWorkOn.SUFFIX);
            PsiElement parent = expr1.getUserData(ElementToWorkOn.PARENT);
            RangeMarker rangeMarker = expr1.getUserData(ElementToWorkOn.TEXT_RANGE);

            LOG.assertTrue(parent != null, expr1);
            return parent.replace(createReplacement(ref.getText(), project, prefix, suffix, parent, rangeMarker, new int[1]));
        }
    }

    @RequiredReadAction
    private static PsiExpression createReplacement(
        String refText,
        Project project,
        String prefix,
        String suffix,
        PsiElement parent,
        RangeMarker rangeMarker,
        int[] refIdx
    ) {
        String text = refText;
        if (parent != null) {
            String allText = parent.getContainingFile().getText();
            TextRange parentRange = parent.getTextRange();

            LOG.assertTrue(
                parentRange.getStartOffset() <= rangeMarker.getStartOffset(),
                parent + "; prefix:" + prefix + "; suffix:" + suffix
            );
            String beg = allText.substring(parentRange.getStartOffset(), rangeMarker.getStartOffset());
            if (StringUtil.stripQuotesAroundValue(beg).trim().length() == 0 && prefix == null) {
                beg = "";
            }

            LOG.assertTrue(rangeMarker.getEndOffset() <= parentRange.getEndOffset(), parent + "; prefix:" + prefix + "; suffix:" + suffix);
            String end = allText.substring(rangeMarker.getEndOffset(), parentRange.getEndOffset());
            if (StringUtil.stripQuotesAroundValue(end).trim().length() == 0 && suffix == null) {
                end = "";
            }

            String start = beg + (prefix != null ? prefix : "");
            refIdx[0] = start.length();
            text = start + refText + (suffix != null ? suffix : "") + end;
        }
        return JavaPsiFacade.getInstance(project).getElementFactory().createExpressionFromText(text, parent);
    }

    @RequiredUIAccess
    private boolean parentStatementNotFound(Project project, Editor editor) {
        LocalizeValue message = RefactoringLocalize.refactoringIsNotSupportedInTheCurrentContext(REFACTORING_NAME);
        showErrorMessage(project, editor, message);
        return false;
    }

    @Override
    @RequiredUIAccess
    protected boolean invokeImpl(@Nonnull Project project, PsiLocalVariable localVariable, Editor editor) {
        throw new UnsupportedOperationException();
    }

    @RequiredReadAction
    private static PsiElement locateAnchor(PsiElement child) {
        while (child != null) {
            PsiElement prev = child.getPrevSibling();
            if (prev instanceof PsiStatement) {
                break;
            }
            if (prev instanceof PsiJavaToken token && token.getTokenType() == JavaTokenType.LBRACE) {
                break;
            }
            child = prev;
        }

        while (child instanceof PsiWhiteSpace || child instanceof PsiComment) {
            child = child.getNextSibling();
        }
        return child;
    }

    @RequiredReadAction
    protected static void highlightReplacedOccurences(Project project, Editor editor, PsiElement[] replacedOccurrences) {
        if (editor == null) {
            return;
        }
        if (project.getApplication().isUnitTestMode()) {
            return;
        }
        HighlightManager highlightManager = HighlightManager.getInstance(project);
        highlightManager.addOccurrenceHighlights(editor, replacedOccurrences, EditorColors.SEARCH_RESULT_ATTRIBUTES, true, null);
    }

    @RequiredUIAccess
    protected abstract void showErrorMessage(Project project, Editor editor, @Nonnull LocalizeValue message);

    protected boolean reportConflicts(MultiMap<PsiElement, LocalizeValue> conflicts, Project project, IntroduceVariableSettings settings) {
        return false;
    }

    public IntroduceVariableSettings getSettings(
        Project project,
        Editor editor,
        PsiExpression expr,
        PsiExpression[] occurrences,
        final TypeSelectorManagerImpl typeSelectorManager,
        boolean declareFinalIfAll,
        boolean anyAssignmentLHS,
        InputValidator validator,
        PsiElement anchor,
        OccurrencesChooser.ReplaceChoice replaceChoice
    ) {
        final boolean replaceAll =
            replaceChoice == OccurrencesChooser.ReplaceChoice.ALL || replaceChoice == OccurrencesChooser.ReplaceChoice.NO_WRITE;
        SuggestedNameInfo suggestedName = getSuggestedName(typeSelectorManager.getDefaultType(), expr, anchor);
        final String variableName = suggestedName.names.length > 0 ? suggestedName.names[0] : "";
        final boolean declareFinal = replaceAll && declareFinalIfAll || !anyAssignmentLHS && createFinals(project);
        final boolean replaceWrite = anyAssignmentLHS && replaceChoice == OccurrencesChooser.ReplaceChoice.ALL;
        return new IntroduceVariableSettings() {
            @Override
            public String getEnteredName() {
                return variableName;
            }

            @Override
            public boolean isReplaceAllOccurrences() {
                return replaceAll;
            }

            @Override
            public boolean isDeclareFinal() {
                return declareFinal;
            }

            @Override
            public boolean isReplaceLValues() {
                return replaceWrite;
            }

            @Override
            public PsiType getSelectedType() {
                PsiType selectedType = typeSelectorManager.getTypeSelector().getSelectedType();
                return selectedType != null ? selectedType : typeSelectorManager.getDefaultType();
            }

            @Override
            public boolean isOK() {
                return true;
            }
        };
    }

    public static boolean createFinals(Project project) {
        Boolean createFinals = JavaRefactoringSettings.getInstance().INTRODUCE_LOCAL_CREATE_FINALS;
        return createFinals == null ? CodeStyleSettingsManager.getSettings(project).GENERATE_FINAL_LOCALS : createFinals;
    }

    @RequiredUIAccess
    public static boolean checkAnchorBeforeThisOrSuper(
        Project project,
        Editor editor,
        PsiElement tempAnchorElement,
        @Nonnull LocalizeValue refactoringName,
        String helpID
    ) {
        if (tempAnchorElement instanceof PsiExpressionStatement exprStmt
            && exprStmt.getExpression() instanceof PsiMethodCallExpression call) {
            PsiMethod method = call.resolveMethod();
            if (method != null && method.isConstructor()) {
                //This is either 'this' or 'super', both must be the first in the respective constructor
                LocalizeValue message =
                    RefactoringLocalize.cannotPerformRefactoringWithReason(RefactoringLocalize.invalidExpressionContext());
                CommonRefactoringUtil.showErrorHint(project, editor, message, refactoringName, helpID);
                return true;
            }
        }
        return false;
    }

    public interface Validator {
        boolean isOK(IntroduceVariableSettings dialog);
    }

    public static void checkInLoopCondition(PsiExpression occurrence, MultiMap<PsiElement, LocalizeValue> conflicts) {
        PsiElement loopForLoopCondition = RefactoringUtil.getLoopForLoopCondition(occurrence);
        if (loopForLoopCondition == null) {
            return;
        }
        List<PsiVariable> referencedVariables = RefactoringUtil.collectReferencedVariables(occurrence);
        List<PsiVariable> modifiedInBody = new ArrayList<>();
        for (PsiVariable psiVariable : referencedVariables) {
            if (RefactoringUtil.isModifiedInScope(psiVariable, loopForLoopCondition)) {
                modifiedInBody.add(psiVariable);
            }
        }

        if (!modifiedInBody.isEmpty()) {
            for (PsiVariable variable : modifiedInBody) {
                LocalizeValue message = RefactoringLocalize.isModifiedInLoopBody(RefactoringUIUtil.getDescription(variable, false));
                conflicts.putValue(variable, message.capitalize());
            }
            conflicts.putValue(occurrence, RefactoringLocalize.introducingVariableMayBreakCodeLogic());
        }
    }

    @Override
    public AbstractInplaceIntroducer getInplaceIntroducer() {
        return null;
    }
}
