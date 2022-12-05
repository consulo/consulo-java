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

/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Aug 20, 2002
 * Time: 8:49:24 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.java.impl.codeInsight.daemon.impl.quickfix;

import com.intellij.java.language.psi.*;
import consulo.codeEditor.Editor;
import consulo.document.util.TextRange;
import consulo.ide.impl.idea.openapi.editor.ex.util.EditorUtil;
import consulo.java.analysis.impl.JavaQuickFixBundle;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.editor.intention.IntentionAction;
import consulo.language.editor.template.*;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.PsiUtilCore;
import consulo.language.psi.resolve.BaseScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import org.jetbrains.annotations.NonNls;

import javax.annotation.Nonnull;
import java.util.*;

public class RenameWrongRefFix implements IntentionAction {
  private final PsiReferenceExpression myRefExpr;
  @NonNls private static final String INPUT_VARIABLE_NAME = "INPUTVAR";
  @NonNls private static final String OTHER_VARIABLE_NAME = "OTHERVAR";
  private final boolean myUnresolvedOnly;

  public RenameWrongRefFix(PsiReferenceExpression refExpr) {
    this(refExpr, false);
  }

  public RenameWrongRefFix(PsiReferenceExpression refExpr, final boolean unresolvedOnly) {
    myRefExpr = refExpr;
    myUnresolvedOnly = unresolvedOnly;
  }

  @Override
  @Nonnull
  public String getText() {
    return JavaQuickFixBundle.message("rename.wrong.reference.text");
  }

  @Override
  @Nonnull
  public String getFamilyName() {
    return JavaQuickFixBundle.message("rename.wrong.reference.family");
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    if (!myRefExpr.isValid() || !myRefExpr.getManager().isInProject(myRefExpr)) return false;
    int offset = editor.getCaretModel().getOffset();
    PsiElement refName = myRefExpr.getReferenceNameElement();
    if (refName == null) return false;
    TextRange textRange = refName.getTextRange();
    if (textRange == null || offset < textRange.getStartOffset() ||
        offset > textRange.getEndOffset()) {
      return false;
    }

    return !CreateFromUsageUtils.isValidReference(myRefExpr, myUnresolvedOnly);
  }

  private class ReferenceNameExpression extends Expression {
    class HammingComparator implements Comparator<LookupElement> {
      @Override
      public int compare(LookupElement lookupItem1, LookupElement lookupItem2) {
        String s1 = lookupItem1.getLookupString();
        String s2 = lookupItem2.getLookupString();
        int diff1 = 0;
        for (int i = 0; i < Math.min(s1.length(), myOldReferenceName.length()); i++) {
          if (s1.charAt(i) != myOldReferenceName.charAt(i)) diff1++;
        }
        int diff2 = 0;
        for (int i = 0; i < Math.min(s2.length(), myOldReferenceName.length()); i++) {
          if (s2.charAt(i) != myOldReferenceName.charAt(i)) diff2++;
        }
        return diff1 - diff2;
      }
    }

    ReferenceNameExpression(LookupElement[] items, String oldReferenceName) {
      myItems = items;
      myOldReferenceName = oldReferenceName;
      Arrays.sort(myItems, new HammingComparator ());
    }

    LookupElement[] myItems;
    private final String myOldReferenceName;

    @Override
    public Result calculateResult(ExpressionContext context) {
      if (myItems == null || myItems.length == 0) {
        return new TextResult(myOldReferenceName);
      }
      return new TextResult(myItems[0].getLookupString());
    }

    @Override
    public Result calculateQuickResult(ExpressionContext context) {
      return null;
    }

    @Override
    public LookupElement[] calculateLookupItems(ExpressionContext context) {
      if (myItems == null || myItems.length == 1) return null;
      return myItems;
    }
  }

  private LookupElement[] collectItems() {
    Set<LookupElement> items = new LinkedHashSet<LookupElement>();
    boolean qualified = myRefExpr.getQualifierExpression() != null;

    if (!qualified && !(myRefExpr.getParent() instanceof PsiMethodCallExpression)) {
      PsiVariable[] vars = CreateFromUsageUtils.guessMatchingVariables(myRefExpr);
      for (PsiVariable var : vars) {
        items.add(LookupElementBuilder.create(var.getName()));
      }
    } else {
      class MyScopeProcessor extends BaseScopeProcessor {
        ArrayList<PsiElement> myResult = new ArrayList<PsiElement>();
        boolean myFilterMethods;
        boolean myFilterStatics = false;

        MyScopeProcessor(PsiReferenceExpression refExpression) {
          myFilterMethods = refExpression.getParent() instanceof PsiMethodCallExpression;
          PsiExpression qualifier = refExpression.getQualifierExpression();
          if (qualifier instanceof PsiReferenceExpression) {
            PsiElement e = ((PsiReferenceExpression) qualifier).resolve();
            myFilterStatics = e instanceof PsiClass;
          } else if (qualifier == null) {
            PsiModifierListOwner scope = PsiTreeUtil.getParentOfType(refExpression, PsiModifierListOwner.class);
            myFilterStatics = scope != null && scope.hasModifierProperty(PsiModifier.STATIC);
          }
        }

        @Override
        public boolean execute(@Nonnull PsiElement element, ResolveState state) {
          if (element instanceof PsiNamedElement
              && element instanceof PsiModifierListOwner
              && myFilterMethods == element instanceof PsiMethod) {
            if (((PsiModifierListOwner)element).hasModifierProperty(PsiModifier.STATIC) == myFilterStatics) {
              myResult.add(element);
            }
          }
          return true;
        }

        public PsiElement[] getVariants () {
          return PsiUtilCore.toPsiElementArray(myResult);
        }
      }

      items.add(LookupElementBuilder.create(myRefExpr.getReferenceName()));
      MyScopeProcessor processor = new MyScopeProcessor(myRefExpr);
      myRefExpr.processVariants(processor);
      PsiElement[] variants = processor.getVariants();
      for (PsiElement variant : variants) {
        items.add(LookupElementBuilder.create(((PsiNamedElement)variant).getName()));
      }
    }

    return items.toArray(new LookupElement[items.size()]);
  }

  @Override
  public void invoke(@Nonnull Project project, final Editor editor, PsiFile file) {
    if (!FileModificationService.getInstance().prepareFileForWrite(file)) return;
    PsiReferenceExpression[] refs = CreateFromUsageUtils.collectExpressions(myRefExpr, PsiMember.class, PsiFile.class);
    PsiElement element = PsiTreeUtil.getParentOfType(myRefExpr, PsiMember.class, PsiFile.class);
    LookupElement[] items = collectItems();
    ReferenceNameExpression refExpr = new ReferenceNameExpression(items, myRefExpr.getReferenceName());

    TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(element);
    for (PsiReferenceExpression expr : refs) {
      if (!expr.equals(myRefExpr)) {
        builder.replaceElement(expr.getReferenceNameElement(), OTHER_VARIABLE_NAME, INPUT_VARIABLE_NAME, false);
      }
      else {
        builder.replaceElement(expr.getReferenceNameElement(), INPUT_VARIABLE_NAME, refExpr, true);
      }
    }

    final float proportion = EditorUtil.calcVerticalScrollProportion(editor);
    editor.getCaretModel().moveToOffset(element.getTextRange().getStartOffset());

    /*for (int i = refs.length - 1; i >= 0; i--) {
      TextRange range = refs[i].getReferenceNameElement().getTextRange();
      document.deleteString(range.getStartOffset(), range.getEndOffset());
    }
*/
    Template template = builder.buildInlineTemplate();
    editor.getCaretModel().moveToOffset(element.getTextRange().getStartOffset());

    TemplateManager.getInstance(project).startTemplate(editor, template);

    EditorUtil.setVerticalScrollProportion(editor, proportion);
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
