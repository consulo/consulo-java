/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package consulo.java.impl.intelliLang.pattern;

import com.intellij.java.analysis.refactoring.JavaRefactoringActionHandlerFactory;
import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.CachedValue;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.CachedValuesManager;
import consulo.dataContext.DataContext;
import consulo.dataContext.DataManager;
import consulo.java.impl.intelliLang.util.AnnotateFix;
import consulo.java.impl.intelliLang.util.AnnotationUtilEx;
import consulo.java.impl.intelliLang.util.PsiUtilEx;
import consulo.java.impl.intelliLang.util.SubstitutedExpressionEvaluationHelper;
import consulo.language.editor.inspection.*;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.language.inject.advanced.Configuration;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.util.collection.SmartList;
import consulo.util.concurrent.AsyncResult;
import consulo.util.dataholder.Key;
import consulo.util.lang.Comparing;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.inject.Inject;

import java.text.MessageFormat;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Inspection that validates if string literals, compile-time constants or
 * substituted expressions match the pattern of the context they're used in.
 */
@ExtensionImpl
public class PatternValidator extends LocalInspectionTool {
    private static final Key<CachedValue<Pattern>> COMPLIED_PATTERN = Key.create("COMPILED_PATTERN");
    public static final LocalizeValue PATTERN_VALIDATION = LocalizeValue.localizeTODO("Pattern Validation");
    public static final LocalizeValue LANGUAGE_INJECTION = LocalizeValue.localizeTODO("Language Injection");

    private final Configuration myConfiguration;

    @Inject
    public PatternValidator() {
        myConfiguration = Configuration.getInstance();
    }

    @Nonnull
    @Override
    public InspectionToolState<?> createStateProvider() {
        return new PatternValidatorState();
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Nonnull
    @Override
    public HighlightDisplayLevel getDefaultLevel() {
        return HighlightDisplayLevel.WARNING;
    }

    @Nonnull
    @Override
    public LocalizeValue getGroupDisplayName() {
        return PATTERN_VALIDATION;
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return LocalizeValue.localizeTODO("Validate Annotated Patterns");
    }

    @Nonnull
    @Override
    public String getShortName() {
        return "PatternValidation";
    }

    @Nonnull
    @Override
    public PsiElementVisitor buildVisitor(
        @Nonnull final ProblemsHolder holder,
        boolean isOnTheFly,
        @Nonnull LocalInspectionToolSession session,
        @Nonnull Object state
    ) {
        PatternValidatorState inspectionState = (PatternValidatorState) state;
        return new JavaElementVisitor() {
            @Override
            @RequiredReadAction
            public final void visitReferenceExpression(@Nonnull PsiReferenceExpression expression) {
                visitExpression(expression);
            }

            @Override
            @RequiredReadAction
            public void visitExpression(PsiExpression expression) {
                PsiElement element = expression.getParent();
                if (element instanceof PsiExpressionList) {
                    // this checks method arguments
                    check(expression, holder, false);
                }
                else if (element instanceof PsiNameValuePair valuePair) {
                    String name = valuePair.getName();
                    if (name == null || name.equals(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME)) {
                        // check whether @Subst complies with pattern
                        check(expression, holder, true);
                    }
                }
            }

            @Override
            @RequiredReadAction
            public void visitReturnStatement(@Nonnull PsiReturnStatement statement) {
                PsiExpression returnValue = statement.getReturnValue();
                if (returnValue != null) {
                    check(returnValue, holder, false);
                }
            }

            @Override
            @RequiredReadAction
            public void visitVariable(@Nonnull PsiVariable var) {
                PsiExpression initializer = var.getInitializer();
                if (initializer != null) {
                    // variable/field initializer
                    check(initializer, holder, false);
                }
            }

            @Override
            @RequiredReadAction
            public void visitAssignmentExpression(@Nonnull PsiAssignmentExpression expression) {
                PsiExpression e = expression.getRExpression();
                if (e != null) {
                    check(e, holder, false);
                }
                visitExpression(expression);
            }

            @RequiredReadAction
            private void check(@Nonnull PsiExpression expression, ProblemsHolder holder, boolean isAnnotationValue) {
                if (expression instanceof PsiConditionalExpression conditional) {
                    PsiExpression e = conditional.getThenExpression();
                    if (e != null) {
                        check(e, holder, isAnnotationValue);
                    }
                    e = conditional.getElseExpression();
                    if (e != null) {
                        check(e, holder, isAnnotationValue);
                    }
                }
                else {
                    PsiType type = expression.getType();
                    // optimization: only check expressions of type String
                    if (type != null && PsiUtilEx.isString(type)) {
                        PsiModifierListOwner element;
                        if (isAnnotationValue) {
                            PsiAnnotation psiAnnotation = PsiTreeUtil.getParentOfType(expression, PsiAnnotation.class);
                            if (psiAnnotation != null
                                && myConfiguration.getAdvancedConfiguration()
                                .getSubstAnnotationClass()
                                .equals(psiAnnotation.getQualifiedName())) {
                                element = PsiTreeUtil.getParentOfType(expression, PsiModifierListOwner.class);
                            }
                            else {
                                return;
                            }
                        }
                        else {
                            element = AnnotationUtilEx.getAnnotatedElementFor(expression, AnnotationUtilEx.LookupType.PREFER_CONTEXT);
                        }
                        if (element != null && PsiUtilEx.isLanguageAnnotationTarget(element)) {
                            PsiAnnotation[] annotations =
                                AnnotationUtilEx.getAnnotationFrom(
                                    element,
                                    myConfiguration.getAdvancedConfiguration().getPatternAnnotationPair(),
                                    true
                                );
                            checkExpression(expression, annotations, holder, inspectionState);
                        }
                    }
                }
            }
        };
    }

    @RequiredReadAction
    private void checkExpression(
        PsiExpression expression,
        PsiAnnotation[] annotations,
        ProblemsHolder holder,
        PatternValidatorState state
    ) {
        if (annotations.length == 0) {
            return;
        }
        PsiAnnotation psiAnnotation = annotations[0];

        // cache compiled pattern with annotation
        CachedValue<Pattern> p = psiAnnotation.getUserData(COMPLIED_PATTERN);
        if (p == null) {
            CachedValueProvider<Pattern> provider = () -> {
                String pattern = AnnotationUtilEx.calcAnnotationValue(psiAnnotation, "value");
                Pattern p1 = null;
                if (pattern != null) {
                    try {
                        p1 = Pattern.compile(pattern);
                    }
                    catch (PatternSyntaxException e) {
                        // pattern stays null
                    }
                }
                return CachedValueProvider.Result.create(p1, (Object[]) annotations);
            };
            p = CachedValuesManager.getManager(expression.getProject()).createCachedValue(provider, false);
            psiAnnotation.putUserData(COMPLIED_PATTERN, p);
        }

        Pattern pattern = p.getValue();
        if (pattern == null) {
            return;
        }

        List<PsiExpression> nonConstantElements = new SmartList<>();
        Object result = new SubstitutedExpressionEvaluationHelper(expression.getProject()).computeExpression(
            expression,
            myConfiguration.getAdvancedConfiguration().getDfaOption(),
            false,
            nonConstantElements
        );
        String o = result == null ? null : String.valueOf(result);
        if (o != null) {
            if (!pattern.matcher(o).matches()) {
                if (annotations.length > 1) {
                    // the last element contains the element's actual annotation
                    String fqn = annotations[annotations.length - 1].getQualifiedName();
                    assert fqn != null;

                    String name = StringUtil.getShortName(fqn);
                    holder.newProblem(LocalizeValue.localizeTODO(
                            MessageFormat.format("Expression ''{0}'' doesn''t match ''{1}'' pattern: {2}", o, name, pattern.pattern())
                        ))
                        .range(expression)
                        .create();
                }
                else {
                    holder.newProblem(LocalizeValue.localizeTODO(
                            MessageFormat.format("Expression ''{0}'' doesn''t match pattern: {1}", o, pattern.pattern())
                        ))
                        .range(expression)
                        .create();
                }
            }
        }
        else if (state.CHECK_NON_CONSTANT_VALUES) {
            for (PsiExpression expr : nonConstantElements) {
                PsiElement e;
                if (expr instanceof PsiReferenceExpression refExpr) {
                    e = refExpr.resolve();
                }
                else if (expr instanceof PsiMethodCallExpression call) {
                    e = call.getMethodExpression().resolve();
                }
                else {
                    e = expr;
                }
                PsiModifierListOwner owner = e instanceof PsiModifierListOwner modifierListOwner ? modifierListOwner : null;
                LocalQuickFix quickFix;
                if (owner != null && PsiUtilEx.isLanguageAnnotationTarget(owner)) {
                    PsiAnnotation[] resolvedAnnos = AnnotationUtilEx.getAnnotationFrom(
                        owner,
                        myConfiguration.getAdvancedConfiguration().getPatternAnnotationPair(),
                        true
                    );
                    if (resolvedAnnos.length == 2 && annotations.length == 2
                        && Comparing.strEqual(resolvedAnnos[1].getQualifiedName(), annotations[1].getQualifiedName())) {
                        // both target and source annotated indirectly with the same anno
                        return;
                    }

                    String className = myConfiguration.getAdvancedConfiguration().getSubstAnnotationPair().first;
                    AnnotateFix fix = new AnnotateFix((PsiModifierListOwner) e, className);
                    quickFix = fix.canApply() ? fix : new IntroduceVariableFix(expr);
                }
                else {
                    quickFix = new IntroduceVariableFix(expr);
                }
                holder.newProblem(LocalizeValue.of("Unsubstituted expression"))
                    .range(expr)
                    .withFix(quickFix)
                    .create();
            }
        }
    }

    private static class IntroduceVariableFix implements LocalQuickFix {
        private final PsiExpression myExpr;

        public IntroduceVariableFix(PsiExpression expr) {
            myExpr = expr;
        }

        @Override
        @Nonnull
        public LocalizeValue getName() {
            return LocalizeValue.localizeTODO("Introduce Variable");
        }

        @Override
        public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
            RefactoringActionHandler handler = JavaRefactoringActionHandlerFactory.getInstance().createIntroduceVariableHandler();
            AsyncResult<DataContext> dataContextContainer = DataManager.getInstance().getDataContextFromFocus();
            dataContextContainer.doWhenDone(dataContext -> handler.invoke(project, new PsiElement[]{myExpr}, dataContext));
            // how to automatically annotate the variable after it has been introduced?
        }
    }
}
