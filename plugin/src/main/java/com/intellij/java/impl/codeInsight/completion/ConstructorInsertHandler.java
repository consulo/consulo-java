package com.intellij.java.impl.codeInsight.completion;

import com.intellij.java.impl.codeInsight.ExpectedTypeInfo;
import com.intellij.java.impl.codeInsight.ExpectedTypesProvider;
import com.intellij.java.impl.codeInsight.generation.GenerateMembersUtil;
import com.intellij.java.impl.codeInsight.generation.OverrideImplementUtil;
import com.intellij.java.impl.codeInsight.generation.PsiGenerationInfo;
import com.intellij.java.impl.codeInsight.intention.impl.TypeExpression;
import com.intellij.java.impl.codeInsight.lookup.PsiTypeLookupItem;
import com.intellij.java.language.impl.codeInsight.generation.OverrideImplementExploreUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.infos.CandidateInfo;
import consulo.application.ApplicationManager;
import consulo.application.Result;
import consulo.codeEditor.Editor;
import consulo.codeEditor.ScrollType;
import consulo.document.Document;
import consulo.externalService.statistic.FeatureUsageTracker;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.codeStyle.PostprocessReformattingAspect;
import consulo.language.editor.WriteCommandAction;
import consulo.language.editor.completion.OffsetKey;
import consulo.language.editor.completion.lookup.*;
import consulo.language.editor.template.Template;
import consulo.language.editor.template.TemplateBuilder;
import consulo.language.editor.template.TemplateBuilderFactory;
import consulo.language.editor.template.TemplateManager;
import consulo.language.editor.template.event.TemplateEditingAdapter;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiUtilCore;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.undoRedo.CommandProcessor;
import consulo.undoRedo.UndoConfirmationPolicy;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.Contract;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * @author peter
 */
public class ConstructorInsertHandler implements InsertHandler<LookupElementDecorator<LookupElement>> {
  private static final Logger LOG = Logger.getInstance(ConstructorInsertHandler.class);
  public static final ConstructorInsertHandler SMART_INSTANCE = new ConstructorInsertHandler(true);
  public static final ConstructorInsertHandler BASIC_INSTANCE = new ConstructorInsertHandler(false);
  static final OffsetKey PARAM_LIST_START = OffsetKey.create("paramListStart");
  static final OffsetKey PARAM_LIST_END = OffsetKey.create("paramListEnd");
  private final boolean mySmart;

  private ConstructorInsertHandler(boolean smart) {
    mySmart = smart;
  }

  @Override
  public void handleInsert(InsertionContext context, LookupElementDecorator<LookupElement> item) {
    @SuppressWarnings({"unchecked"}) final LookupElement delegate = item.getDelegate();

    PsiClass psiClass = (PsiClass) item.getObject();

    boolean isAbstract = psiClass.hasModifierProperty(PsiModifier.ABSTRACT);

    if (Lookup.REPLACE_SELECT_CHAR == context.getCompletionChar() && context.getOffsetMap().containsOffset(PARAM_LIST_START)) {
      final int plStart = context.getOffset(PARAM_LIST_START);
      final int plEnd = context.getOffset(PARAM_LIST_END);
      if (plStart >= 0 && plEnd >= 0) {
        context.getDocument().deleteString(plStart, plEnd);
      }
    }

    context.commitDocument();

    OffsetKey insideRef = context.trackOffset(context.getTailOffset(), false);

    final PsiElement position = SmartCompletionDecorator.getPosition(context, delegate);
    if (position == null) {
      return;
    }

    final PsiExpression enclosing = PsiTreeUtil.getContextOfType(position, PsiExpression.class, true);
    final PsiAnonymousClass anonymousClass = PsiTreeUtil.getParentOfType(position, PsiAnonymousClass.class);
    final boolean inAnonymous = anonymousClass != null && anonymousClass.getParent() == enclosing;
    boolean fillTypeArgs = false;
    if (delegate instanceof PsiTypeLookupItem) {
      fillTypeArgs = !isRawTypeExpected(context, (PsiTypeLookupItem) delegate) && psiClass.getTypeParameters().length > 0 && ((PsiTypeLookupItem) delegate).calcGenerics(position, context)
          .isEmpty() && context.getCompletionChar() != '(';

      if (context.getDocument().getTextLength() > context.getTailOffset() && context.getDocument().getCharsSequence().charAt(context.getTailOffset()) == '<') {
        PsiJavaCodeReferenceElement ref = PsiTreeUtil.findElementOfClassAtOffset(context.getFile(), context.getTailOffset(), PsiJavaCodeReferenceElement.class, false);
        if (ref != null) {
          PsiReferenceParameterList parameterList = ref.getParameterList();
          if (parameterList != null && context.getTailOffset() == parameterList.getTextRange().getStartOffset()) {
            context.getDocument().deleteString(parameterList.getTextRange().getStartOffset(), parameterList.getTextRange().getEndOffset());
            context.commitDocument();
          }
        }
      }

      delegate.handleInsert(context);
      PostprocessReformattingAspect.getInstance(context.getProject()).doPostponedFormatting(context.getFile().getViewProvider());
    }

    if (item.getDelegate() instanceof JavaPsiClassReferenceElement) {
      PsiTypeLookupItem.addImportForItem(context, psiClass);
    }


    insertParentheses(context, delegate, psiClass, !inAnonymous && isAbstract);

    if (inAnonymous) {
      return;
    }

    if (mySmart) {
      FeatureUsageTracker.getInstance().triggerFeatureUsed(JavaCompletionFeatures.AFTER_NEW);
    }
    if (isAbstract) {
      PostprocessReformattingAspect.getInstance(context.getProject()).doPostponedFormatting(context.getFile().getViewProvider());

      final Editor editor = context.getEditor();
      final Document document = editor.getDocument();
      final int offset = context.getTailOffset();

      document.insertString(offset, " {}");
      editor.getCaretModel().moveToOffset(offset + 2);

      final PsiFile file = context.getFile();
      PsiDocumentManager.getInstance(file.getProject()).commitDocument(document);
      reformatEnclosingExpressionListAtOffset(file, offset);

      if (fillTypeArgs && JavaCompletionUtil.promptTypeArgs(context, context.getOffset(insideRef))) {
        return;
      }

      context.setLaterRunnable(generateAnonymousBody(editor, file));
    } else {
      PsiDocumentManager.getInstance(context.getProject()).commitAllDocuments();
      final PsiNewExpression newExpression = PsiTreeUtil.findElementOfClassAtOffset(context.getFile(), context.getStartOffset(), PsiNewExpression.class, false);
      if (newExpression != null) {
        final PsiJavaCodeReferenceElement classReference = newExpression.getClassOrAnonymousClassReference();
        if (classReference != null) {
          CodeStyleManager.getInstance(context.getProject()).reformat(classReference);
        }
      }
      if (mySmart) {
        FeatureUsageTracker.getInstance().triggerFeatureUsed(JavaCompletionFeatures.AFTER_NEW);
      }
      if (fillTypeArgs) {
        JavaCompletionUtil.promptTypeArgs(context, context.getOffset(insideRef));
      }
    }
  }

  private static void reformatEnclosingExpressionListAtOffset(@Nonnull PsiFile file, int offset) {
    final PsiElement elementAtOffset = PsiUtilCore.getElementAtOffset(file, offset);
    PsiExpressionList listToReformat = getEnclosingExpressionList(elementAtOffset.getParent());
    if (listToReformat != null) {
      CodeStyleManager.getInstance(file.getProject()).reformat(listToReformat);
    }
  }

  @Nullable
  private static PsiExpressionList getEnclosingExpressionList(@Nonnull PsiElement element) {
    if (!(element instanceof PsiAnonymousClass)) {
      return null;
    }

    PsiElement e = element.getParent();
    if (e instanceof PsiNewExpression && e.getParent() instanceof PsiExpressionList) {
      return (PsiExpressionList) e.getParent();
    }

    return null;
  }

  static boolean isRawTypeExpected(InsertionContext context, PsiTypeLookupItem delegate) {
    PsiNewExpression newExpr = PsiTreeUtil.findElementOfClassAtOffset(context.getFile(), context.getStartOffset(), PsiNewExpression.class, false);
    if (newExpr != null) {
      for (ExpectedTypeInfo info : ExpectedTypesProvider.getExpectedTypes(newExpr, true)) {
        PsiType expected = info.getDefaultType();
        if (expected.isAssignableFrom(delegate.getType())) {
          if (expected instanceof PsiClassType && ((PsiClassType) expected).isRaw()) {
            return true;
          }
        }
      }
    }
    return false;
  }

  public static boolean insertParentheses(InsertionContext context, LookupElement delegate, final PsiClass psiClass, final boolean forAnonymous) {
    if (context.getCompletionChar() == '[') {
      return false;
    }

    final PsiElement place = context.getFile().findElementAt(context.getStartOffset());
    assert place != null;
    boolean hasParams = hasConstructorParameters(psiClass, place);

    JavaCompletionUtil.insertParentheses(context, delegate, false, hasParams, forAnonymous);

    return true;
  }

  static boolean hasConstructorParameters(PsiClass psiClass, @Nonnull PsiElement place) {
    final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(place.getProject()).getResolveHelper();
    boolean hasParams = false;
    for (PsiMethod constructor : psiClass.getConstructors()) {
      if (!resolveHelper.isAccessible(constructor, place, null)) {
        continue;
      }
      if (constructor.getParameterList().getParametersCount() > 0) {
        hasParams = true;
        break;
      }
    }
    return hasParams;
  }

  @Nullable
  private static Runnable generateAnonymousBody(final Editor editor, final PsiFile file) {
    final Project project = file.getProject();
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    if (element == null) {
      return null;
    }

    PsiElement parent = element.getParent();
    if (!(parent instanceof PsiAnonymousClass)) {
      return null;
    }

    return genAnonymousBodyFor((PsiAnonymousClass) parent, editor, file, project);
  }

  public static Runnable genAnonymousBodyFor(PsiAnonymousClass parent, final Editor editor, final PsiFile file, final Project project) {
    try {
      CodeStyleManager.getInstance(project).reformat(parent);
    } catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    int offset = parent.getTextRange().getEndOffset() - 1;
    editor.getCaretModel().moveToOffset(offset);
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    editor.getSelectionModel().removeSelection();

    final PsiReferenceParameterList parameterList = parent.getBaseClassReference().getParameterList();
    final PsiTypeElement[] parameters = parameterList != null ? parameterList.getTypeParameterElements() : null;
    if (shouldStartTypeTemplate(parameters)) {
      startTemplate(parent, editor, createOverrideRunnable(editor, file, project), parameters);
      return null;
    }

    return createOverrideRunnable(editor, file, project);
  }

  private static Runnable createOverrideRunnable(final Editor editor, final PsiFile file, final Project project) {
    return new Runnable() {
      @Override
      public void run() {
        PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
        final PsiAnonymousClass aClass = PsiTreeUtil.findElementOfClassAtOffset(file, editor.getCaretModel().getOffset(), PsiAnonymousClass.class, false);
        if (aClass == null) {
          return;
        }
        CommandProcessor.getInstance().executeCommand(project, new Runnable() {
          @Override
          public void run() {
            final Collection<CandidateInfo> candidatesToImplement = OverrideImplementExploreUtil.getMethodsToOverrideImplement(aClass, true);
            for (Iterator<CandidateInfo> iterator = candidatesToImplement.iterator(); iterator.hasNext(); ) {
              final CandidateInfo candidate = iterator.next();
              final PsiElement element = candidate.getElement();
              if (element instanceof PsiMethod && ((PsiMethod) element).hasModifierProperty(PsiModifier.DEFAULT)) {
                iterator.remove();
              }
            }
            boolean invokeOverride = candidatesToImplement.isEmpty();
            if (invokeOverride) {
              OverrideImplementUtil.chooseAndOverrideOrImplementMethods(project, editor, aClass, false);
            } else {
              ApplicationManager.getApplication().runWriteAction(new Runnable() {
                @Override
                public void run() {
                  try {
                    List<PsiMethod> methods = OverrideImplementUtil.overrideOrImplementMethodCandidates(aClass, candidatesToImplement, false);
                    List<PsiGenerationInfo<PsiMethod>> prototypes = OverrideImplementUtil.convert2GenerationInfos(methods);
                    List<PsiGenerationInfo<PsiMethod>> resultMembers = GenerateMembersUtil.insertMembersBeforeAnchor(aClass, null, prototypes);
                    resultMembers.get(0).positionCaret(editor, true);
                  } catch (IncorrectOperationException ioe) {
                    LOG.error(ioe);
                  }
                }
              });
            }
          }
        }, getCommandName(), getCommandName(), UndoConfirmationPolicy.DEFAULT, editor.getDocument());
      }
    };
  }

  @Contract("null -> false")
  private static boolean shouldStartTypeTemplate(PsiTypeElement[] parameters) {
    if (parameters != null && parameters.length > 0) {
      for (PsiTypeElement parameter : parameters) {
        if (parameter.getType().equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
          return true;
        }
      }
    }
    return false;
  }

  private static void startTemplate(final PsiAnonymousClass aClass, final Editor editor, final Runnable runnable, @Nonnull final PsiTypeElement[] parameters) {
    final Project project = aClass.getProject();
    new WriteCommandAction(project, getCommandName(), getCommandName()) {
      @Override
      protected void run(@Nonnull Result result) throws Throwable {
        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.getDocument());
        editor.getCaretModel().moveToOffset(aClass.getTextOffset());
        final TemplateBuilder templateBuilder =  TemplateBuilderFactory.getInstance().createTemplateBuilder(aClass);
        for (int i = 0; i < parameters.length; i++) {
          PsiTypeElement parameter = parameters[i];
          templateBuilder.replaceElement(parameter, "param" + i, new TypeExpression(project, new PsiType[]{parameter.getType()}), true);
        }
        Template template = templateBuilder.buildInlineTemplate();
        TemplateManager.getInstance(project).startTemplate(editor, template, false, null, new TemplateEditingAdapter() {
          @Override
          public void templateFinished(Template template, boolean brokenOff) {
            if (!brokenOff) {
              runnable.run();
            }
          }
        });
      }
    }.execute();
  }

  private static String getCommandName() {
    return CompletionBundle.message("completion.smart.type.generate.anonymous.body");
  }
}
