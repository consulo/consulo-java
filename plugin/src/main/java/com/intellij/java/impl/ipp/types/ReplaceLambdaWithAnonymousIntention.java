/*
 * Copyright 2011 Bas Leijdekkers
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
package com.intellij.java.impl.ipp.types;

import com.intellij.java.impl.codeInsight.generation.GenerateMembersUtil;
import com.intellij.java.impl.codeInsight.generation.OverrideImplementUtil;
import com.intellij.java.impl.codeInsight.generation.PsiGenerationInfo;
import com.intellij.java.impl.ipp.base.Intention;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.language.impl.codeInsight.ChangeContextUtil;
import com.intellij.java.language.impl.refactoring.util.RefactoringChangeUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.util.PsiTypesUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.localize.IntentionPowerPackLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import jakarta.annotation.Nonnull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.ReplaceLambdaWithAnonymousIntention", fileExtensions = "java", categories = {"Java", "Declaration"})
public class ReplaceLambdaWithAnonymousIntention extends Intention {
    private static final Logger LOG = Logger.getInstance(ReplaceLambdaWithAnonymousIntention.class);

    @Nonnull
    @Override
    public LocalizeValue getText() {
        return IntentionPowerPackLocalize.replaceLambdaWithAnonymousIntentionName();
    }

    @Nonnull
    @Override
    protected PsiElementPredicate getElementPredicate() {
        return new LambdaPredicate();
    }

    @Override
    protected void processIntention(@Nonnull PsiElement element) throws IncorrectOperationException {
    }

    @Override
    protected void processIntention(Editor editor, @Nonnull PsiElement element) throws IncorrectOperationException {
        PsiLambdaExpression lambdaExpression = PsiTreeUtil.getParentOfType(element, PsiLambdaExpression.class);
        LOG.assertTrue(lambdaExpression != null);
        PsiParameter[] paramListCopy = ((PsiParameterList) lambdaExpression.getParameterList().copy())
            .getParameters();
        PsiType functionalInterfaceType = lambdaExpression.getFunctionalInterfaceType();
        LOG.assertTrue(functionalInterfaceType != null);
        PsiMethod method = LambdaUtil.getFunctionalInterfaceMethod(functionalInterfaceType);
        LOG.assertTrue(method != null);

        String blockText = getBodyText(lambdaExpression);
        if (blockText == null) {
            return;
        }

        final PsiElementFactory psiElementFactory = JavaPsiFacade.getElementFactory(element.getProject());
        PsiCodeBlock blockFromText = psiElementFactory.createCodeBlockFromText(blockText, lambdaExpression);
        ChangeContextUtil.encodeContextInfo(blockFromText, true);
        PsiNewExpression newExpression = (PsiNewExpression) psiElementFactory.createExpressionFromText("new " +
            functionalInterfaceType.getCanonicalText() + "(){}", lambdaExpression);
        PsiClass thisClass = RefactoringChangeUtil.getThisClass(lambdaExpression);
        final String thisClassName = thisClass != null ? thisClass.getName() : null;
        if (thisClassName != null) {
            final PsiThisExpression thisAccessExpr = thisClass instanceof PsiAnonymousClass ? null :
                RefactoringChangeUtil.createThisExpression(lambdaExpression.getManager(), thisClass);
            ChangeContextUtil.decodeContextInfo(blockFromText, thisClass, thisAccessExpr);
            final Map<PsiElement, PsiElement> replacements = new HashMap<PsiElement, PsiElement>();
            blockFromText.accept(new JavaRecursiveElementWalkingVisitor() {
                @Override
                public void visitClass(PsiClass aClass) {
                }

                @Override
                public void visitSuperExpression(PsiSuperExpression expression) {
                    super.visitSuperExpression(expression);
                    if (expression.getQualifier() == null) {
                        replacements.put(expression, psiElementFactory.createExpressionFromText(thisClassName + "." +
                            expression.getText(), expression));
                    }
                }

                @Override
                public void visitMethodCallExpression(PsiMethodCallExpression expression) {
                    super.visitMethodCallExpression(expression);
                    if (thisAccessExpr != null) {
                        PsiMethod psiMethod = expression.resolveMethod();
                        if (psiMethod != null && !psiMethod.hasModifierProperty(PsiModifier.STATIC) && expression
                            .getMethodExpression().getQualifierExpression() == null) {
                            replacements.put(expression, psiElementFactory.createExpressionFromText(thisAccessExpr
                                .getText() + "." + expression.getText(), expression));
                        }
                    }
                }
            });
            for (PsiElement psiElement : replacements.keySet()) {
                psiElement.replace(replacements.get(psiElement));
            }
        }
        blockFromText = psiElementFactory.createCodeBlockFromText(blockFromText.getText(), null);
        newExpression = (PsiNewExpression) JavaCodeStyleManager.getInstance(lambdaExpression.getProject())
            .shortenClassReferences(lambdaExpression.replace(newExpression));

        PsiAnonymousClass anonymousClass = newExpression.getAnonymousClass();
        LOG.assertTrue(anonymousClass != null);
        List<PsiGenerationInfo<PsiMethod>> infos = OverrideImplementUtil.overrideOrImplement(anonymousClass,
            method);
        if (infos != null && infos.size() == 1) {
            PsiMethod member = infos.get(0).getPsiMember();
            PsiParameter[] parameters = member.getParameterList().getParameters();
            for (int i = 0; i < parameters.length; i++) {
                PsiParameter parameter = parameters[i];
                String lambdaParamName = paramListCopy[i].getName();
                if (lambdaParamName != null) {
                    parameter.setName(lambdaParamName);
                }
            }
            PsiCodeBlock codeBlock = member.getBody();
            LOG.assertTrue(codeBlock != null);

            codeBlock = (PsiCodeBlock) codeBlock.replace(blockFromText);
            GenerateMembersUtil.positionCaret(editor, member, true);
        }
    }

    private static String getBodyText(PsiLambdaExpression lambdaExpression) {
        String blockText;
        PsiElement body = lambdaExpression.getBody();
        if (body instanceof PsiExpression) {
            PsiType returnType = LambdaUtil.getFunctionalInterfaceReturnType(lambdaExpression);
            blockText = "{";
            blockText += PsiType.VOID.equals(returnType) ? "" : "return ";
            blockText += body.getText() + ";}";
        }
        else if (body != null) {
            blockText = body.getText();
        }
        else {
            blockText = null;
        }
        return blockText;
    }

    private static class LambdaPredicate implements PsiElementPredicate {
        @Override
        public boolean satisfiedBy(PsiElement element) {
            PsiLambdaExpression lambdaExpression = PsiTreeUtil.getParentOfType(element,
                PsiLambdaExpression.class);
            if (lambdaExpression != null && (element.getParent() == lambdaExpression && element instanceof PsiJavaToken
                && ((PsiJavaToken) element).getTokenType() == JavaTokenType.ARROW || PsiTreeUtil.isAncestor
                (lambdaExpression.getParameterList(), element, false))) {
                PsiClass thisClass = PsiTreeUtil.getParentOfType(lambdaExpression, PsiClass.class, true);
                if (thisClass == null || thisClass instanceof PsiAnonymousClass) {
                    PsiElement body = lambdaExpression.getBody();
                    if (body == null) {
                        return false;
                    }
                    final boolean[] disabled = new boolean[1];
                    body.accept(new JavaRecursiveElementWalkingVisitor() {
                        @Override
                        public void visitThisExpression(PsiThisExpression expression) {
                            disabled[0] = true;
                        }

                        @Override
                        public void visitSuperExpression(PsiSuperExpression expression) {
                            disabled[0] = true;
                        }
                    });
                    if (disabled[0]) {
                        return false;
                    }
                }
                PsiType functionalInterfaceType = lambdaExpression.getFunctionalInterfaceType();
                if (functionalInterfaceType != null &&
                    LambdaUtil.isLambdaFullyInferred(lambdaExpression, functionalInterfaceType) &&
                    LambdaUtil.isFunctionalType(functionalInterfaceType)) {
                    PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(functionalInterfaceType);
                    if (interfaceMethod != null) {
                        PsiSubstitutor substitutor = LambdaUtil.getSubstitutor(interfaceMethod,
                            PsiUtil.resolveGenericsClassInType(functionalInterfaceType));
                        for (PsiType type : interfaceMethod.getSignature(substitutor).getParameterTypes()) {
                            if (!PsiTypesUtil.isDenotableType(type)) {
                                return false;
                            }
                        }
                        PsiType returnType = LambdaUtil.getFunctionalInterfaceReturnType
                            (functionalInterfaceType);
                        return PsiTypesUtil.isDenotableType(returnType);
                    }
                }
            }
            return false;
        }
    }
}
