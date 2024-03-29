/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInsight.completion;

import com.intellij.java.impl.codeInsight.lookup.TypedLookupItem;
import com.intellij.java.language.impl.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.java.language.psi.*;
import consulo.application.util.CachedValueProvider;
import consulo.language.editor.completion.lookup.InsertionContext;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementDecorator;
import consulo.language.editor.completion.lookup.LookupElementPresentation;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.LanguageCachedValueUtil;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.dataholder.Key;
import consulo.util.lang.StringUtil;
import consulo.util.lang.SystemProperties;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * @author peter
 */
public class JavaConstructorCallElement extends LookupElementDecorator<LookupElement> implements TypedLookupItem {
  private static boolean JAVA_COMPLETION_SHOW_CONSTRUCTORS = SystemProperties.getBooleanProperty("java.completion.show.constructors", false);

  private static final Key<JavaConstructorCallElement> WRAPPING_CONSTRUCTOR_CALL = Key.create("WRAPPING_CONSTRUCTOR_CALL");
  @Nonnull
  private final PsiMethod myConstructor;
  @Nonnull
  private final PsiClassType myType;
  @Nonnull
  private final PsiSubstitutor mySubstitutor;

  private JavaConstructorCallElement(@Nonnull LookupElement classItem, @Nonnull PsiMethod constructor, @Nonnull PsiClassType type) {
    super(classItem);
    myConstructor = constructor;
    myType = type;
    mySubstitutor = myType.resolveGenerics().getSubstitutor();
  }

  private void markClassItemWrapped(@Nonnull LookupElement classItem) {
    LookupElement delegate = classItem;
    while (true) {
      delegate.putUserData(WRAPPING_CONSTRUCTOR_CALL, this);
      if (!(delegate instanceof LookupElementDecorator)) {
        break;
      }
      delegate = ((LookupElementDecorator) delegate).getDelegate();
    }
  }

  @Override
  public void handleInsert(InsertionContext context) {
    markClassItemWrapped(getDelegate());
    super.handleInsert(context);
  }

  @Nonnull
  @Override
  public PsiMethod getObject() {
    return myConstructor;
  }

  @Override
  public boolean equals(Object o) {
    return this == o || super.equals(o) && myConstructor.equals(((JavaConstructorCallElement) o).myConstructor);
  }

  @Override
  public int hashCode() {
    return 31 * super.hashCode() + myConstructor.hashCode();
  }

  @Nonnull
  @Override
  public PsiType getType() {
    return myType;
  }

  @Override
  public void renderElement(LookupElementPresentation presentation) {
    super.renderElement(presentation);

    String tailText = StringUtil.notNullize(presentation.getTailText());
    int genericsEnd = tailText.lastIndexOf('>') + 1;

    presentation.clearTail();
    presentation.appendTailText(tailText.substring(0, genericsEnd), false);
    presentation.appendTailText(MemberLookupHelper.getMethodParameterString(myConstructor, mySubstitutor), false);
    presentation.appendTailText(tailText.substring(genericsEnd), true);
  }

  static List<? extends LookupElement> wrap(@Nonnull JavaPsiClassReferenceElement classItem, @Nonnull PsiElement position) {
    PsiClass psiClass = classItem.getObject();
    return wrap(classItem, psiClass, position, () -> JavaPsiFacade.getElementFactory(psiClass.getProject()).createType(psiClass, PsiSubstitutor.EMPTY));
  }

  static List<? extends LookupElement> wrap(@Nonnull LookupElement classItem, @Nonnull PsiClass psiClass, @Nonnull PsiElement position, @Nonnull Supplier<PsiClassType> type) {
    if (JAVA_COMPLETION_SHOW_CONSTRUCTORS && isConstructorCallPlace(position)) {
      List<PsiMethod> constructors = ContainerUtil.filter(psiClass.getConstructors(), c -> shouldSuggestConstructor(psiClass, position, c));
      if (!constructors.isEmpty()) {
        return ContainerUtil.map(constructors, c -> new JavaConstructorCallElement(classItem, c, type.get()));
      }
    }
    return Collections.singletonList(classItem);
  }

  private static boolean shouldSuggestConstructor(@Nonnull PsiClass psiClass, @Nonnull PsiElement position, PsiMethod constructor) {
    return JavaResolveUtil.isAccessible(constructor, psiClass, constructor.getModifierList(), position, null, null) || willBeAccessibleInAnonymous(psiClass, constructor);
  }

  private static boolean willBeAccessibleInAnonymous(@Nonnull PsiClass psiClass, PsiMethod constructor) {
    return !constructor.hasModifierProperty(PsiModifier.PRIVATE) && psiClass.hasModifierProperty(PsiModifier.ABSTRACT);
  }

  private static boolean isConstructorCallPlace(@Nonnull PsiElement position) {
    return LanguageCachedValueUtil.getCachedValue(position, () ->
    {
      boolean result = JavaClassNameCompletionContributor.AFTER_NEW.accepts(position) && !JavaClassNameInsertHandler.isArrayTypeExpected(PsiTreeUtil.getParentOfType(position, PsiNewExpression
          .class));
      return CachedValueProvider.Result.create(result, position);
    });
  }

  @Nullable
  static PsiMethod extractCalledConstructor(@Nonnull LookupElement element) {
    JavaConstructorCallElement callItem = element.getUserData(WRAPPING_CONSTRUCTOR_CALL);
    return callItem != null ? callItem.getObject() : null;
  }

}
