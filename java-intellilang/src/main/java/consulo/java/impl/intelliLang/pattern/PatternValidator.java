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
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.CachedValue;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.CachedValuesManager;
import consulo.dataContext.DataContext;
import consulo.dataContext.DataManager;
import consulo.ide.impl.intelliLang.Configuration;
import consulo.ide.impl.psi.injection.impl.ApplicationInjectionConfiguration;
import consulo.java.impl.intelliLang.util.AnnotateFix;
import consulo.java.impl.intelliLang.util.AnnotationUtilEx;
import consulo.java.impl.intelliLang.util.PsiUtilEx;
import consulo.java.impl.intelliLang.util.SubstitutedExpressionEvaluationHelper;
import consulo.language.editor.inspection.*;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import consulo.util.collection.SmartList;
import consulo.util.concurrent.AsyncResult;
import consulo.util.dataholder.Key;
import consulo.util.lang.Comparing;
import consulo.util.lang.StringUtil;
import jakarta.inject.Inject;

import jakarta.annotation.Nonnull;
import java.text.MessageFormat;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Inspection that validates if string literals, compile-time constants or
 * substituted expressions match the pattern of the context they're used in.
 */
@ExtensionImpl
public class PatternValidator extends LocalInspectionTool
{
	private static final Key<CachedValue<Pattern>> COMPLIED_PATTERN = Key.create("COMPILED_PATTERN");
	public static final String PATTERN_VALIDATION = "Pattern Validation";
	public static final String LANGUAGE_INJECTION = "Language Injection";

	private final Configuration myConfiguration;

	@Inject
	public PatternValidator(ApplicationInjectionConfiguration configuration)
	{
		myConfiguration = configuration;
	}

	@jakarta.annotation.Nonnull
	@Override
	public InspectionToolState<?> createStateProvider()
	{
		return new PatternValidatorState();
	}

	@Override
	public boolean isEnabledByDefault()
	{
		return true;
	}

	@jakarta.annotation.Nonnull
	@Override
	public HighlightDisplayLevel getDefaultLevel()
	{
		return HighlightDisplayLevel.WARNING;
	}

	@Override
	@Nonnull
	public String getGroupDisplayName()
	{
		return PATTERN_VALIDATION;
	}

	@Override
	@Nonnull
	public String getDisplayName()
	{
		return "Validate Annotated Patterns";
	}

	@Override
	@jakarta.annotation.Nonnull
	public String getShortName()
	{
		return "PatternValidation";
	}

	@Override
	@jakarta.annotation.Nonnull
	public PsiElementVisitor buildVisitor(@Nonnull final ProblemsHolder holder, boolean isOnTheFly, LocalInspectionToolSession session, Object state)
	{
		PatternValidatorState inspectionState = (PatternValidatorState) state;
		return new JavaElementVisitor()
		{

			@Override
			public final void visitReferenceExpression(PsiReferenceExpression expression)
			{
				visitExpression(expression);
			}

			@Override
			public void visitExpression(PsiExpression expression)
			{
				final PsiElement element = expression.getParent();
				if(element instanceof PsiExpressionList)
				{
					// this checks method arguments
					check(expression, holder, false);
				}
				else if(element instanceof PsiNameValuePair)
				{
					final PsiNameValuePair valuePair = (PsiNameValuePair) element;
					final String name = valuePair.getName();
					if(name == null || name.equals(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME))
					{
						// check whether @Subst complies with pattern
						check(expression, holder, true);
					}
				}
			}

			@Override
			public void visitReturnStatement(PsiReturnStatement statement)
			{
				final PsiExpression returnValue = statement.getReturnValue();
				if(returnValue != null)
				{
					check(returnValue, holder, false);
				}
			}

			@Override
			public void visitVariable(PsiVariable var)
			{
				final PsiExpression initializer = var.getInitializer();
				if(initializer != null)
				{
					// variable/field initializer
					check(initializer, holder, false);
				}
			}

			@Override
			public void visitAssignmentExpression(PsiAssignmentExpression expression)
			{
				final PsiExpression e = expression.getRExpression();
				if(e != null)
				{
					check(e, holder, false);
				}
				visitExpression(expression);
			}

			private void check(@Nonnull PsiExpression expression, ProblemsHolder holder, boolean isAnnotationValue)
			{
				if(expression instanceof PsiConditionalExpression)
				{
					final PsiConditionalExpression expr = (PsiConditionalExpression) expression;
					PsiExpression e = expr.getThenExpression();
					if(e != null)
					{
						check(e, holder, isAnnotationValue);
					}
					e = expr.getElseExpression();
					if(e != null)
					{
						check(e, holder, isAnnotationValue);
					}
				}
				else
				{
					final PsiType type = expression.getType();
					// optimiziation: only check expressions of type String
					if(type != null && PsiUtilEx.isString(type))
					{
						final PsiModifierListOwner element;
						if(isAnnotationValue)
						{
							final PsiAnnotation psiAnnotation = PsiTreeUtil.getParentOfType(expression, PsiAnnotation.class);
							if(psiAnnotation != null && myConfiguration.getAdvancedConfiguration()
									.getSubstAnnotationClass()
									.equals(psiAnnotation.getQualifiedName()))
							{
								element = PsiTreeUtil.getParentOfType(expression, PsiModifierListOwner.class);
							}
							else
							{
								return;
							}
						}
						else
						{
							element = AnnotationUtilEx.getAnnotatedElementFor(expression, AnnotationUtilEx.LookupType.PREFER_CONTEXT);
						}
						if(element != null && PsiUtilEx.isLanguageAnnotationTarget(element))
						{
							PsiAnnotation[] annotations =
									AnnotationUtilEx.getAnnotationFrom(element, myConfiguration.getAdvancedConfiguration().getPatternAnnotationPair(), true);
							checkExpression(expression, annotations, holder, inspectionState);
						}
					}
				}
			}
		};
	}

	private void checkExpression(PsiExpression expression, final PsiAnnotation[] annotations, ProblemsHolder holder, PatternValidatorState state)
	{
		if(annotations.length == 0)
		{
			return;
		}
		final PsiAnnotation psiAnnotation = annotations[0];

		// cache compiled pattern with annotation
		CachedValue<Pattern> p = psiAnnotation.getUserData(COMPLIED_PATTERN);
		if(p == null)
		{
			final CachedValueProvider<Pattern> provider = new CachedValueProvider<Pattern>()
			{
				@Override
				public Result<Pattern> compute()
				{
					final String pattern = AnnotationUtilEx.calcAnnotationValue(psiAnnotation, "value");
					Pattern p = null;
					if(pattern != null)
					{
						try
						{
							p = Pattern.compile(pattern);
						}
						catch(PatternSyntaxException e)
						{
							// pattern stays null
						}
					}
					return Result.create(p, (Object[]) annotations);
				}
			};
			p = CachedValuesManager.getManager(expression.getProject()).createCachedValue(provider, false);
			psiAnnotation.putUserData(COMPLIED_PATTERN, p);
		}

		final Pattern pattern = p.getValue();
		if(pattern == null)
		{
			return;
		}

		List<PsiExpression> nonConstantElements = new SmartList<PsiExpression>();
		final Object result = new SubstitutedExpressionEvaluationHelper(expression.getProject()).computeExpression(expression,
				myConfiguration.getAdvancedConfiguration()
						.getDfaOption(),
				false,
				nonConstantElements);
		final String o = result == null ? null : String.valueOf(result);
		if(o != null)
		{
			if(!pattern.matcher(o).matches())
			{
				if(annotations.length > 1)
				{
					// the last element contains the element's actual annotation
					final String fqn = annotations[annotations.length - 1].getQualifiedName();
					assert fqn != null;

					final String name = StringUtil.getShortName(fqn);
					holder.registerProblem(expression,
							MessageFormat.format("Expression ''{0}'' doesn''t match ''{1}'' pattern: {2}",
									o,
									name,
									pattern.pattern()));
				}
				else
				{
					holder.registerProblem(expression, MessageFormat.format("Expression ''{0}'' doesn''t match pattern: {1}", o, pattern.pattern()));
				}
			}
		}
		else if(state.CHECK_NON_CONSTANT_VALUES)
		{
			for(PsiExpression expr : nonConstantElements)
			{
				final PsiElement e;
				if(expr instanceof PsiReferenceExpression)
				{
					e = ((PsiReferenceExpression) expr).resolve();
				}
				else if(expr instanceof PsiMethodCallExpression)
				{
					e = ((PsiMethodCallExpression) expr).getMethodExpression().resolve();
				}
				else
				{
					e = expr;
				}
				final PsiModifierListOwner owner = e instanceof PsiModifierListOwner ? (PsiModifierListOwner) e : null;
				LocalQuickFix quickFix;
				if(owner != null && PsiUtilEx.isLanguageAnnotationTarget(owner))
				{
					PsiAnnotation[] resolvedAnnos =
							AnnotationUtilEx.getAnnotationFrom(owner, myConfiguration.getAdvancedConfiguration().getPatternAnnotationPair(), true);
					if(resolvedAnnos.length == 2 && annotations.length == 2 && Comparing.strEqual(resolvedAnnos[1].getQualifiedName(),
							annotations[1].getQualifiedName()))
					{
						// both target and source annotated indirectly with the same anno
						return;
					}

					final String classname = myConfiguration.getAdvancedConfiguration().getSubstAnnotationPair().first;
					final AnnotateFix fix = new AnnotateFix((PsiModifierListOwner) e, classname);
					quickFix = fix.canApply() ? fix : new IntroduceVariableFix(expr);
				}
				else
				{
					quickFix = new IntroduceVariableFix(expr);
				}
				holder.registerProblem(expr, "Unsubstituted expression", quickFix);
			}
		}
	}

	private static class IntroduceVariableFix implements LocalQuickFix
	{
		private final PsiExpression myExpr;

		public IntroduceVariableFix(PsiExpression expr)
		{
			myExpr = expr;
		}

		@Override
		@jakarta.annotation.Nonnull
		public String getName()
		{
			return "Introduce Variable";
		}

		@Override
		@Nonnull
		public String getFamilyName()
		{
			return getName();
		}

		@Override
		public void applyFix(@jakarta.annotation.Nonnull final Project project, @Nonnull ProblemDescriptor descriptor)
		{
			final RefactoringActionHandler handler = JavaRefactoringActionHandlerFactory.getInstance().createIntroduceVariableHandler();
			final AsyncResult<DataContext> dataContextContainer = DataManager.getInstance().getDataContextFromFocus();
			dataContextContainer.doWhenDone(dataContext -> {
				handler.invoke(project, new PsiElement[]{myExpr}, dataContext);
			});
			// how to automatically annotate the variable after it has been introduced?
		}
	}
}
