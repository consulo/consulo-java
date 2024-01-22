package com.intellij.java.impl.codeInsight.completion;

import consulo.language.editor.completion.lookup.InsertHandler;
import consulo.language.editor.completion.lookup.InsertionContext;
import consulo.language.editor.completion.lookup.DefaultLookupItemRenderer;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementPresentation;
import consulo.externalService.statistic.FeatureUsageTracker;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMember;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiSubstitutor;
import jakarta.annotation.Nonnull;

import java.util.List;
import java.util.Set;

import static consulo.util.lang.ObjectUtil.assertNotNull;

/**
 * @author peter
 */
public class JavaGlobalMemberLookupElement extends LookupElement implements StaticallyImportable {
  private final MemberLookupHelper myHelper;
  private final InsertHandler<JavaGlobalMemberLookupElement> myQualifiedInsertion;
  private final InsertHandler<JavaGlobalMemberLookupElement> myImportInsertion;

  public JavaGlobalMemberLookupElement(List<PsiMethod> overloads,
                                       PsiClass containingClass,
                                       InsertHandler<JavaGlobalMemberLookupElement> qualifiedInsertion,
                                       InsertHandler<JavaGlobalMemberLookupElement> importInsertion,
                                       boolean shouldImport) {
    myHelper = new MemberLookupHelper(overloads, containingClass, shouldImport);
    myQualifiedInsertion = qualifiedInsertion;
    myImportInsertion = importInsertion;
  }

  public JavaGlobalMemberLookupElement(PsiMember member,
                                       PsiClass containingClass,
                                       InsertHandler<JavaGlobalMemberLookupElement> qualifiedInsertion,
                                       InsertHandler<JavaGlobalMemberLookupElement> importInsertion,
                                       boolean shouldImport) {
    myHelper = new MemberLookupHelper(member, containingClass, shouldImport, false);
    myQualifiedInsertion = qualifiedInsertion;
    myImportInsertion = importInsertion;
  }

  @jakarta.annotation.Nonnull
  @Override
  public PsiMember getObject() {
    return myHelper.getMember();
  }

  @Nonnull
  public PsiClass getContainingClass() {
    return assertNotNull(myHelper.getContainingClass());
  }

  @jakarta.annotation.Nonnull
  @Override
  public String getLookupString() {
    return assertNotNull(getObject().getName());
  }

  @Override
  public Set<String> getAllLookupStrings() {
    return JavaCompletionUtil.getAllLookupStrings(getObject());
  }

  @Override
  public void renderElement(LookupElementPresentation presentation) {
    presentation.setIcon(DefaultLookupItemRenderer.getRawIcon(this, presentation.isReal()));
    myHelper.renderElement(presentation, !myHelper.willBeImported(), true, PsiSubstitutor.EMPTY);
  }

  @Override
  public void setShouldBeImported(boolean shouldImportStatic) {
    myHelper.setShouldBeImported(shouldImportStatic);
  }

  @Override
  public boolean canBeImported() {
    return true;
  }

  @Override
  public boolean willBeImported() {
    return myHelper.willBeImported();
  }

  @Override
  public void handleInsert(InsertionContext context) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed(JavaCompletionFeatures.GLOBAL_MEMBER_NAME);

    (willBeImported() ? myImportInsertion : myQualifiedInsertion).handleInsert(context, this);
  }

}
