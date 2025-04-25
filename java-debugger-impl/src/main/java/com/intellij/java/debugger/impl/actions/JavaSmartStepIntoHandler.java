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
package com.intellij.java.debugger.impl.actions;

import com.intellij.java.debugger.SourcePosition;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.document.Document;
import consulo.document.FileDocumentManager;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiCompiledElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiFile;
import consulo.util.lang.CharArrayUtil;
import consulo.util.lang.Range;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;

import jakarta.annotation.Nullable;

import java.util.*;

/**
 * @author Alexander Podkhalyuzin
 * @since 2011-11-22
 */
@ExtensionImpl
public class JavaSmartStepIntoHandler extends JvmSmartStepIntoHandler {
    @Override
    @RequiredReadAction
    public boolean isAvailable(SourcePosition position) {
        PsiFile file = position.getFile();
        return file.getLanguage().isKindOf(JavaLanguage.INSTANCE);
    }

    @Nonnull
    @Override
    @RequiredReadAction
    public List<SmartStepTarget> findSmartStepTargets(SourcePosition position) {
        int line = position.getLine();
        if (line < 0) {
            return Collections.emptyList(); // the document has been changed
        }

        PsiFile file = position.getFile();
        VirtualFile vFile = file.getVirtualFile();
        if (vFile == null) {
            // the file is not physical
            return Collections.emptyList();
        }

        Document doc = FileDocumentManager.getInstance().getDocument(vFile);
        if (doc == null) {
            return Collections.emptyList();
        }
        if (line >= doc.getLineCount()) {
            return Collections.emptyList(); // the document has been changed
        }
        int startOffset = doc.getLineStartOffset(line);
        TextRange lineRange = new TextRange(startOffset, doc.getLineEndOffset(line));
        int offset = CharArrayUtil.shiftForward(doc.getCharsSequence(), startOffset, " \t");
        PsiElement element = file.findElementAt(offset);
        if (element != null && !(element instanceof PsiCompiledElement)) {
            do {
                PsiElement parent = element.getParent();
                if (parent == null || (parent.getTextOffset() < lineRange.getStartOffset())) {
                    break;
                }
                element = parent;
            }
            while (true);

            Set<SmartStepTarget> targets = new LinkedHashSet<>();

            Range<Integer> lines = new Range<>(
                doc.getLineNumber(element.getTextOffset()),
                doc.getLineNumber(element.getTextOffset() + element.getTextLength())
            );

            PsiElementVisitor methodCollector = new JavaRecursiveElementVisitor() {
                Stack<PsiMethod> myContextStack = new Stack<>();
                Stack<String> myParamNameStack = new Stack<>();
                private int myNextLambdaExpressionOrdinal = 0;

                @Nullable
                private String getCurrentParamName() {
                    return myParamNameStack.isEmpty() ? null : myParamNameStack.peek();
                }

                @Override
                public void visitAnonymousClass(PsiAnonymousClass aClass) {
                    for (PsiMethod psiMethod : aClass.getMethods()) {
                        targets.add(new MethodSmartStepTarget(psiMethod, getCurrentParamName(), psiMethod.getBody(), true, lines));
                    }
                }

                @Override
                public void visitLambdaExpression(@Nonnull PsiLambdaExpression expression) {
                    targets.add(new LambdaSmartStepTarget(
                        expression,
                        getCurrentParamName(),
                        expression.getBody(),
                        myNextLambdaExpressionOrdinal++,
                        lines
                    ));
                }

                @Override
                @RequiredReadAction
                public void visitStatement(PsiStatement statement) {
                    if (lineRange.intersects(statement.getTextRange())) {
                        super.visitStatement(statement);
                    }
                }

                @Override
                public void visitExpressionList(@Nonnull PsiExpressionList expressionList) {
                    PsiMethod psiMethod = myContextStack.isEmpty() ? null : myContextStack.peek();
                    if (psiMethod != null) {
                        String methodName = psiMethod.getName();
                        PsiExpression[] expressions = expressionList.getExpressions();
                        PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
                        for (int idx = 0; idx < expressions.length; idx++) {
                            String paramName = (idx < parameters.length && !parameters[idx].isVarArgs())
                                ? parameters[idx].getName()
                                : "arg" + (idx + 1);
                            myParamNameStack.push(methodName + ": " + paramName + ".");
                            PsiExpression argExpression = expressions[idx];
                            try {
                                argExpression.accept(this);
                            }
                            finally {
                                myParamNameStack.pop();
                            }
                        }
                    }
                    else {
                        super.visitExpressionList(expressionList);
                    }
                }

                @Override
                public void visitCallExpression(PsiCallExpression expression) {
                    PsiMethod psiMethod = expression.resolveMethod();
                    if (psiMethod != null) {
                        myContextStack.push(psiMethod);
                        targets.add(new MethodSmartStepTarget(
                            psiMethod,
                            null,
                            expression instanceof PsiMethodCallExpression methodCall
                                ? methodCall.getMethodExpression().getReferenceNameElement()
                                : expression instanceof PsiNewExpression newExpr
                                ? newExpr.getClassOrAnonymousClassReference()
                                : expression,
                            false,
                            lines
                        ));
                    }
                    try {
                        super.visitCallExpression(expression);
                    }
                    finally {
                        if (psiMethod != null) {
                            myContextStack.pop();
                        }
                    }
                }
            };

            element.accept(methodCollector);
            for (PsiElement sibling = element.getNextSibling(); sibling != null; sibling = sibling.getNextSibling()) {
                if (!lineRange.intersects(sibling.getTextRange())) {
                    break;
                }
                sibling.accept(methodCollector);
            }
            return new ArrayList<>(targets);
        }
        return Collections.emptyList();
    }
}
