/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInspection.duplicateStringLiteral;

import com.intellij.java.analysis.codeInspection.SuppressManager;
import com.intellij.java.analysis.impl.codeInspection.ex.BaseLocalInspectionTool;
import com.intellij.java.impl.refactoring.introduceField.IntroduceConstantHandlerImpl;
import com.intellij.java.impl.refactoring.util.occurrences.BaseOccurrenceManager;
import com.intellij.java.impl.refactoring.util.occurrences.OccurrenceFilter;
import com.intellij.java.impl.refactoring.util.occurrences.OccurrenceManager;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.PsiElementFactory;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiFormatUtil;
import com.intellij.java.language.psi.util.PsiFormatUtilBase;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.application.util.StringSearcher;
import consulo.application.util.function.CommonProcessors;
import consulo.codeEditor.Editor;
import consulo.java.analysis.impl.util.JavaI18nUtil;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.inspection.*;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.file.FileViewProvider;
import consulo.language.psi.*;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.LowLevelSearchUtil;
import consulo.language.psi.search.PsiSearchHelper;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.ex.awt.event.DocumentAdapter;
import consulo.util.collection.SmartList;
import consulo.util.lang.Comparing;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.util.*;

@ExtensionImpl
public class DuplicateStringLiteralInspection extends BaseLocalInspectionTool {
  private static final Logger LOG = Logger.getInstance(DuplicateStringLiteralInspection.class);
  @SuppressWarnings({"WeakerAccess"})
  public int MIN_STRING_LENGTH = 5;
  @SuppressWarnings({"WeakerAccess"})
  public boolean IGNORE_PROPERTY_KEYS = false;
  @NonNls
  private static final String BR = "<br>";

  @Override
  @Nonnull
  public PsiElementVisitor buildVisitorImpl(
    @Nonnull final ProblemsHolder holder,
    final boolean isOnTheFly,
    LocalInspectionToolSession session,
    Object state
  ) {
    return new JavaElementVisitor() {
      @Override
      public void visitReferenceExpression(@Nonnull final PsiReferenceExpression expression) {
        visitExpression(expression);
      }

      @Override
      public void visitLiteralExpression(@Nonnull PsiLiteralExpression expression) {
        checkStringLiteralExpression(expression, holder, isOnTheFly);
      }
    };
  }

  @Override
  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionLocalize.inspectionDuplicatesDisplayName();
  }

  @Override
  @Nonnull
  public LocalizeValue getGroupDisplayName() {
    return InspectionLocalize.groupNamesInternationalizationIssues();
  }

  @Override
  @Nonnull
  public String getShortName() {
    return "DuplicateStringLiteralInspection";
  }

  @RequiredReadAction
  private void checkStringLiteralExpression(
    @Nonnull final PsiLiteralExpression originalExpression,
    @Nonnull ProblemsHolder holder,
    final boolean isOnTheFly
  ) {
    Object value = originalExpression.getValue();
    if (!(value instanceof String)) {
      return;
    }
    final Project project = holder.getProject();
    if (!shouldCheck(project, originalExpression)) {
      return;
    }
    final String stringToFind = (String) value;
    if (stringToFind.isEmpty()) {
      return;
    }
    final GlobalSearchScope scope = GlobalSearchScope.projectScope(originalExpression.getProject());
    final PsiSearchHelper searchHelper = PsiSearchHelper.SERVICE.getInstance(holder.getFile().getProject());
    final List<String> words = StringUtil.getWordsIn(stringToFind);
    if (words.isEmpty()) {
      return;
    }
    // put longer strings first
    Collections.sort(words, (o1, o2) -> o2.length() - o1.length());

    final ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    Set<PsiFile> resultFiles = null;
    for (String word : words) {
      if (word.length() < MIN_STRING_LENGTH) {
        continue;
      }
      progress.checkCanceled();
      final Set<PsiFile> files = new HashSet<>();
      searchHelper.processAllFilesWithWordInLiterals(word, scope, new CommonProcessors.CollectProcessor<>(files));
      if (resultFiles == null) {
        resultFiles = files;
      } else {
        resultFiles.retainAll(files);
      }
      if (resultFiles.isEmpty()) {
        return;
      }
    }

    if (resultFiles == null || resultFiles.isEmpty()) {
      return;
    }
    final List<PsiExpression> foundExpr = new ArrayList<>();

    for (final PsiFile file : resultFiles) {
      progress.checkCanceled();
      FileViewProvider viewProvider = file.getViewProvider();
      // important: skip non-java files with given word in literal (IDEA-126201)
      if (viewProvider.getPsi(JavaLanguage.INSTANCE) == null) {
        continue;
      }
      CharSequence text = viewProvider.getContents();
      StringSearcher searcher = new StringSearcher(stringToFind, true, true);

      LowLevelSearchUtil.processTextOccurrences(text, 0, text.length(), searcher, progress, offset -> {
        PsiElement element = file.findElementAt(offset);
        if (element == null || !(element.getParent() instanceof PsiLiteralExpression)) {
          return true;
        }
        PsiLiteralExpression expression = (PsiLiteralExpression) element.getParent();
        if (expression != originalExpression && Comparing.equal(stringToFind,
            expression.getValue()) && shouldCheck(project, expression)) {
          foundExpr.add(expression);
        }
        return true;
      });
    }
    if (foundExpr.isEmpty()) {
      return;
    }
    Set<PsiClass> classes = new HashSet<>();
    for (PsiElement aClass : foundExpr) {
      progress.checkCanceled();
      do {
        aClass = PsiTreeUtil.getParentOfType(aClass, PsiClass.class);
      }
      while (aClass != null && ((PsiClass) aClass).getQualifiedName() == null);
      if (aClass != null) {
        classes.add((PsiClass) aClass);
      }
    }
    if (classes.isEmpty()) {
      return;
    }

    List<PsiClass> tenClassesMost = Arrays.asList(classes.toArray(new PsiClass[classes.size()]));
    if (tenClassesMost.size() > 10) {
      tenClassesMost = tenClassesMost.subList(0, 10);
    }

    String classList;
    if (isOnTheFly) {
      classList = StringUtil.join(tenClassesMost, aClass -> {
        final boolean thisFile = aClass.getContainingFile() == originalExpression.getContainingFile();
        //noinspection HardCodedStringLiteral
        return "&nbsp;&nbsp;&nbsp;'<b>" + aClass.getQualifiedName() + "</b>'" +
            (thisFile ? " " + InspectionLocalize.inspectionDuplicatesMessageInThisFile().get()
                : "");
      }, ", " + BR);
    } else {
      classList = StringUtil.join(tenClassesMost, aClass -> "'" + aClass.getQualifiedName() + "'", ", ");
    }

    if (classes.size() > tenClassesMost.size()) {
      classList += BR + InspectionLocalize.inspectionDuplicatesMessageMore(classes.size() - 10).get();
    }

    String msg = InspectionLocalize.inspectionDuplicatesMessage(classList).get();

    Collection<LocalQuickFix> fixes = new SmartList<>();
    if (isOnTheFly) {
      final LocalQuickFix introduceConstFix = createIntroduceConstFix(foundExpr, originalExpression);
      fixes.add(introduceConstFix);
    }
    createReplaceFixes(foundExpr, originalExpression, fixes);
    LocalQuickFix[] array = fixes.toArray(new LocalQuickFix[fixes.size()]);
    holder.registerProblem(originalExpression, msg, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, array);
  }

  private boolean shouldCheck(@Nonnull Project project, @Nonnull PsiLiteralExpression expression) {
    return !(IGNORE_PROPERTY_KEYS && JavaI18nUtil.mustBePropertyKey(project, expression, new HashMap<>()))
      && !SuppressManager.isSuppressedInspectionName(expression);
  }

  private static void createReplaceFixes(
    final List<PsiExpression> foundExpr,
    final PsiLiteralExpression originalExpression,
    final Collection<LocalQuickFix> fixes
  ) {
    Set<PsiField> constants = new HashSet<>();
    for (Iterator<PsiExpression> iterator = foundExpr.iterator(); iterator.hasNext(); ) {
      PsiExpression expression1 = iterator.next();
      PsiElement parent = expression1.getParent();
      if (parent instanceof PsiField field) {
        if (field.getInitializer() == expression1 && field.hasModifierProperty(PsiModifier.STATIC)) {
          constants.add(field);
          iterator.remove();
        }
      }
    }
    for (final PsiField constant : constants) {
      final PsiClass containingClass = constant.getContainingClass();
      if (containingClass == null) {
        continue;
      }
      boolean isAccessible = JavaPsiFacade.getInstance(constant.getProject()).getResolveHelper()
        .isAccessible(constant, originalExpression, containingClass);
      if (!isAccessible && containingClass.getQualifiedName() == null) {
        continue;
      }
      final LocalQuickFix replaceQuickFix = new ReplaceFix(constant, originalExpression);
      fixes.add(replaceQuickFix);
    }
  }

  private static LocalQuickFix createIntroduceConstFix(
    final List<PsiExpression> foundExpr,
    final PsiLiteralExpression originalExpression
  ) {
    final PsiExpression[] expressions = foundExpr.toArray(new PsiExpression[foundExpr.size() + 1]);
    expressions[foundExpr.size()] = originalExpression;

    return new IntroduceLiteralConstantFix(expressions);
  }

  @Nullable
  @RequiredWriteAction
  private static PsiReferenceExpression createReferenceTo(
    final PsiField constant,
    final PsiLiteralExpression context
  ) throws IncorrectOperationException {
    PsiElementFactory factory = JavaPsiFacade.getInstance(constant.getProject()).getElementFactory();
    PsiReferenceExpression reference = (PsiReferenceExpression) factory.createExpressionFromText(constant.getName(), context);
    if (reference.isReferenceTo(constant)) {
      return reference;
    }
    reference = (PsiReferenceExpression) factory.createExpressionFromText("XXX." + constant.getName(), null);
    final PsiReferenceExpression classQualifier = (PsiReferenceExpression) reference.getQualifierExpression();
    PsiClass containingClass = constant.getContainingClass();
    if (containingClass.getQualifiedName() == null) {
      return null;
    }
    classQualifier.bindToElement(containingClass);

    if (reference.isReferenceTo(constant)) {
      return reference;
    }
    return null;
  }

  @Override
  public boolean isEnabledByDefault() {
    return false;
  }

  @Override
  public JComponent createOptionsPanel() {
    final OptionsPanel optionsPanel = new OptionsPanel();
    optionsPanel.myIgnorePropertyKeyExpressions
      .addActionListener(e -> IGNORE_PROPERTY_KEYS = optionsPanel.myIgnorePropertyKeyExpressions.isSelected());
    optionsPanel.myMinStringLengthField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(final DocumentEvent e) {
        try {
          MIN_STRING_LENGTH = Integer.parseInt(optionsPanel.myMinStringLengthField.getText());
        } catch (NumberFormatException ignored) {
        }
      }
    });
    optionsPanel.myIgnorePropertyKeyExpressions.setSelected(IGNORE_PROPERTY_KEYS);
    optionsPanel.myMinStringLengthField.setText(Integer.toString(MIN_STRING_LENGTH));
    return optionsPanel.myPanel;
  }

  public static class OptionsPanel {
    private JTextField myMinStringLengthField;
    private JPanel myPanel;
    private JCheckBox myIgnorePropertyKeyExpressions;
  }

  private static class IntroduceLiteralConstantFix implements LocalQuickFix {
    private final SmartPsiElementPointer[] myExpressions;

    public IntroduceLiteralConstantFix(final PsiExpression[] expressions) {
      myExpressions = new SmartPsiElementPointer[expressions.length];
      for (int i = 0; i < expressions.length; i++) {
        PsiExpression expression = expressions[i];
        myExpressions[i] = SmartPointerManager.getInstance(expression.getProject())
            .createSmartPsiElementPointer(expression);
      }
    }

    @Override
    @Nonnull
    public LocalizeValue getName() {
      return InspectionLocalize.introduceConstantAcrossTheProject();
    }

    @Override
    public void applyFix(@Nonnull final Project project, @Nonnull ProblemDescriptor descriptor) {
      SwingUtilities.invokeLater(() -> {
        if (project.isDisposed()) {
          return;
        }
        final List<PsiExpression> expressions = new ArrayList<>();
        for (SmartPsiElementPointer ptr : myExpressions) {
          final PsiElement element = ptr.getElement();
          if (element != null) {
            expressions.add((PsiExpression) element);
          }
        }
        final PsiExpression[] expressionArray = expressions.toArray(new PsiExpression[expressions.size()]);
        final IntroduceConstantHandlerImpl handler = new IntroduceConstantHandlerImpl() {
          @Override
          protected OccurrenceManager createOccurrenceManager(PsiExpression selectedExpr, PsiClass parentClass) {
            final OccurrenceFilter filter = occurrence -> true;
            return new BaseOccurrenceManager(filter) {
              @Override
              protected PsiExpression[] defaultOccurrences() {
                return expressionArray;
              }

              @Override
              protected PsiExpression[] findOccurrences() {
                return expressionArray;
              }
            };
          }
        };
        handler.invoke(project, expressionArray);
      });
    }
  }

  private static class ReplaceFix extends LocalQuickFixAndIntentionActionOnPsiElement {
    private final LocalizeValue myText;
    private final SmartPsiElementPointer<PsiField> myConst;

    public ReplaceFix(PsiField constant, PsiLiteralExpression originalExpression) {
      super(originalExpression);
      myText = InspectionLocalize.inspectionDuplicatesReplaceQuickfix(PsiFormatUtil.formatVariable(
        constant,
        PsiFormatUtilBase.SHOW_CONTAINING_CLASS | PsiFormatUtilBase.SHOW_FQ_NAME | PsiFormatUtilBase.SHOW_NAME,
        PsiSubstitutor.EMPTY)
      );
      myConst = SmartPointerManager.getInstance(constant.getProject()).createSmartPsiElementPointer(constant);
    }

    @Nonnull
    @Override
    public LocalizeValue getText() {
      return myText;
    }

    @Override
    @RequiredWriteAction
    public void invoke(
      @Nonnull Project project,
      @Nonnull PsiFile file,
      @Nullable Editor editor,
      @Nonnull PsiElement startElement,
      @Nonnull PsiElement endElement
    ) {
      final PsiLiteralExpression myOriginalExpression = (PsiLiteralExpression) startElement;
      final PsiField myConstant = myConst.getElement();
      if (myConstant == null || !FileModificationService.getInstance().prepareFileForWrite(myOriginalExpression.getContainingFile())) {
        return;
      }
      try {
        final PsiReferenceExpression reference = createReferenceTo(myConstant, myOriginalExpression);
        if (reference != null) {
          myOriginalExpression.replace(reference);
        }
      } catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }

    @Nonnull
    public LocalizeValue getFamilyName() {
      return InspectionLocalize.inspectionDuplicatesReplaceFamilyQuickfix();
    }
  }
}
