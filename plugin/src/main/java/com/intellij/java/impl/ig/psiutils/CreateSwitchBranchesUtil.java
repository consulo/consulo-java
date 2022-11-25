// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.impl.ig.psiutils;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.TypeUtils;
import consulo.codeEditor.Editor;
import consulo.document.Document;
import consulo.fileEditor.FileEditorManager;
import consulo.language.editor.template.ConstantNode;
import consulo.language.editor.template.TemplateBuilder;
import consulo.language.editor.template.TemplateBuilderFactory;
import consulo.language.inject.InjectedLanguageManager;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.Couple;
import consulo.util.lang.ObjectUtil;
import one.util.streamex.Joining;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NonNls;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Function;

public class CreateSwitchBranchesUtil {
  /**
   * @param names names of individual branches to create (non-empty)
   * @return a name of the action which creates missing switch branches.
   */
  @Nonnull
  public static String getActionName(Collection<String> names) {
    if (names.size() == 1) {
      return "Create missing switch branch '" + names.iterator().next() + "'";
    }
    return "Create missing branches: " + formatMissingBranches(names);
  }

  /**
   * @param names names of individual branches to create (non-empty)
   * @return a string which contains all the names (probably abbreviated if too long)
   */
  public static String formatMissingBranches(Collection<String> names) {
    return StreamEx.of(names).map(name -> name.startsWith("'") || name.startsWith("\"") ? name : "'" + name + "'").mapLast("and "::concat)
        .collect(Joining.with(", ").maxChars(50).cutAfterDelimiter());
  }

  /**
   * Create missing switch branches
   *
   * @param switchBlock   a switch block to process
   * @param allNames      an ordered list of all expected switch branches (e.g. list of all possible enum values)
   * @param missingNames  a collection of missing branch names which should be created
   * @param caseExtractor a function which extracts list of the case string representations from the given switch label.
   *                      The resulting strings should appear in the allNames list if the label matches the same constant,
   *                      thus some kind of normalization could be necessary.
   * @return a list of created branches
   */
  public static List<PsiSwitchLabelStatementBase> createMissingBranches(@Nonnull PsiSwitchBlock switchBlock,
                                                                        @Nonnull List<String> allNames,
                                                                        @Nonnull Collection<String> missingNames,
                                                                        @Nonnull Function<PsiSwitchLabelStatementBase, List<String>> caseExtractor) {
    boolean isRuleBasedFormat = SwitchUtils.isRuleFormatSwitch(switchBlock);
    final PsiCodeBlock body = switchBlock.getBody();
    if (body == null) {
      // replace entire switch statement if no code block is present
      @NonNls final StringBuilder newStatementText = new StringBuilder();
      CommentTracker commentTracker = new CommentTracker();
      final PsiExpression switchExpression = switchBlock.getExpression();
      newStatementText.append("switch(").append(switchExpression == null ? "" : commentTracker.text(switchExpression)).append("){");
      for (String missingName : missingNames) {
        newStatementText.append(String.join("", generateStatements(missingName, switchBlock, isRuleBasedFormat)));
      }
      newStatementText.append('}');
      PsiSwitchBlock block = (PsiSwitchBlock) commentTracker.replaceAndRestoreComments(switchBlock, newStatementText.toString());
      return PsiTreeUtil.getChildrenOfTypeAsList(block.getBody(), PsiSwitchLabelStatementBase.class);
    }
    Map<String, String> prevToNext =
        StreamEx.of(allNames).pairMap(Couple::of).toMap(c -> c.getFirst(), c -> c.getSecond());
    List<String> missingLabels = StreamEx.of(allNames).filter(missingNames::contains).toList();
    String nextLabel = getNextLabel(prevToNext, missingLabels);
    PsiElement bodyElement = body.getFirstBodyElement();
    List<PsiSwitchLabelStatementBase> addedLabels = new ArrayList<>();
    while (bodyElement != null) {
      PsiSwitchLabelStatementBase label = ObjectUtil.tryCast(bodyElement, PsiSwitchLabelStatementBase.class);
      if (label != null) {
        List<String> constants = caseExtractor.apply(label);
        while (nextLabel != null && constants.contains(nextLabel)) {
          addedLabels.add(addSwitchLabelStatementBefore(missingLabels.get(0), bodyElement, switchBlock, isRuleBasedFormat));
          missingLabels.remove(0);
          if (missingLabels.isEmpty()) {
            break;
          }
          nextLabel = getNextLabel(prevToNext, missingLabels);
        }
        if (label.isDefaultCase()) {
          for (String missingEnumElement : missingLabels) {
            addedLabels.add(addSwitchLabelStatementBefore(missingEnumElement, bodyElement, switchBlock, isRuleBasedFormat));
          }
          missingLabels.clear();
          break;
        }
      }
      bodyElement = bodyElement.getNextSibling();
    }
    if (!missingLabels.isEmpty()) {
      final PsiElement lastChild = body.getLastChild();
      for (String missingEnumElement : missingLabels) {
        addedLabels.add(addSwitchLabelStatementBefore(missingEnumElement, lastChild, switchBlock, isRuleBasedFormat));
      }
    }
    return addedLabels;
  }

  /**
   * If necessary, starts a template to modify the bodies of created switch branches
   *
   * @param block       parent switch block
   * @param addedLabels list of created labels (returned from {@link #createMissingBranches(PsiSwitchBlock, List, Collection, Function)}).
   */
  public static void createTemplate(@Nonnull PsiSwitchBlock block, List<PsiSwitchLabelStatementBase> addedLabels) {
    if (!(block instanceof PsiSwitchExpression))
      return;
    Editor editor = prepareForTemplateAndObtainEditor(block);
    if (editor == null)
      return;
    TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(block);
    List<PsiExpression> elementsToReplace = getElementsToReplace(addedLabels);
    for (PsiExpression expression : elementsToReplace) {
      builder.replaceElement(expression, new ConstantNode(expression.getText()));
    }
    builder.run(editor, true);
  }

  @Nonnull
  private static List<PsiExpression> getElementsToReplace(@Nonnull List<PsiSwitchLabelStatementBase> labels) {
    List<PsiExpression> elementsToReplace = new ArrayList<>();
    for (PsiSwitchLabelStatementBase label : labels) {
      if (label instanceof PsiSwitchLabeledRuleStatement) {
        PsiStatement body = ((PsiSwitchLabeledRuleStatement) label).getBody();
        if (body instanceof PsiExpressionStatement) {
          ContainerUtil.addIfNotNull(elementsToReplace, ((PsiExpressionStatement) body).getExpression());
        }
      } else {
        PsiElement next = PsiTreeUtil.skipWhitespacesAndCommentsForward(label);
        if (next instanceof PsiYieldStatement) {
          ContainerUtil.addIfNotNull(elementsToReplace, ((PsiYieldStatement) next).getExpression());
        }
      }
    }
    return elementsToReplace;
  }

  private static List<String> generateStatements(String name, PsiSwitchBlock switchBlock, boolean isRuleBasedFormat) {
    if (switchBlock instanceof PsiSwitchExpression) {
      String value = TypeUtils.getDefaultValue(((PsiSwitchExpression) switchBlock).getType());
      if (isRuleBasedFormat) {
        return Collections.singletonList("case " + name + " -> " + value + ";");
      } else {
        return Arrays.asList("case " + name + ":", "yield " + value + ";");
      }
    }
    if (isRuleBasedFormat) {
      return Collections.singletonList("case " + name + " -> {}");
    }
    return Arrays.asList("case " + name + ":", "break;");
  }

  private static PsiSwitchLabelStatementBase addSwitchLabelStatementBefore(String labelExpression,
                                                                           PsiElement anchor,
                                                                           PsiSwitchBlock switchBlock,
                                                                           boolean isRuleBasedFormat) {
    if (anchor instanceof PsiSwitchLabelStatement) {
      PsiElement sibling = PsiTreeUtil.skipWhitespacesBackward(anchor);
      while (sibling instanceof PsiSwitchLabelStatement) {
        anchor = sibling;
        sibling = PsiTreeUtil.skipWhitespacesBackward(anchor);
      }
    }
    PsiElement correctedAnchor = anchor;
    final PsiElement parent = anchor.getParent();
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(anchor.getProject());
    PsiSwitchLabelStatementBase result = null;
    for (String text : generateStatements(labelExpression, switchBlock, isRuleBasedFormat)) {
      PsiStatement statement = factory.createStatementFromText(text, parent);
      PsiElement inserted = parent.addBefore(statement, correctedAnchor);
      if (inserted instanceof PsiSwitchLabelStatementBase) {
        result = (PsiSwitchLabelStatementBase) inserted;
      }
    }
    return result;
  }

  private static String getNextLabel(Map<String, String> nextLabels, List<String> missingLabels) {
    String nextLabel = nextLabels.get(missingLabels.get(0));
    while (missingLabels.contains(nextLabel)) {
      nextLabel = nextLabels.get(nextLabel);
    }
    return nextLabel;
  }

  /**
   * Prepares the document for starting the template and returns the editor.
   *
   * @param element any element from the document
   * @return an editor, or null if not found.
   */
  @Nullable
  public static Editor prepareForTemplateAndObtainEditor(@Nonnull PsiElement element) {
    PsiFile file = element.getContainingFile();
    Project project = file.getProject();
    Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
    if (editor == null)
      return null;
    Document document = editor.getDocument();
    PsiFile topLevelFile = InjectedLanguageManager.getInstance(project).getTopLevelFile(file);
    if (topLevelFile == null || document != topLevelFile.getViewProvider().getDocument())
      return null;
    PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document);
    return editor;
  }
}
