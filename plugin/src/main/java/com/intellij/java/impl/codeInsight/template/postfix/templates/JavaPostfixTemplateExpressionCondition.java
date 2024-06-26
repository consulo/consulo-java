// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.impl.codeInsight.template.postfix.templates;

import com.intellij.java.impl.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.PsiTypes;
import com.intellij.java.language.psi.util.InheritanceUtil;
import consulo.java.impl.JavaBundle;
import consulo.language.editor.refactoring.postfixTemplate.PostfixTemplateExpressionCondition;
import jakarta.annotation.Nonnull;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;

import java.util.Objects;

public interface JavaPostfixTemplateExpressionCondition extends PostfixTemplateExpressionCondition<PsiExpression> {

  class JavaPostfixTemplateExpressionFqnCondition implements JavaPostfixTemplateExpressionCondition {
    public static final @NonNls String ID = "fqn";
    public static final @NonNls String FQN_ATTR = "fqn";

    private final String myFqn;

    public JavaPostfixTemplateExpressionFqnCondition(@Nonnull String fqn) {
      myFqn = fqn;
    }

    public String getFqn() {
      return myFqn;
    }

    @Override
    public boolean test(@Nonnull PsiExpression element) {
      PsiType type = element.getType();
      return type != null && InheritanceUtil.isInheritor(type, myFqn);
    }

    @Nonnull
    @Override
    public String getId() {
      return ID;
    }

    @Override
    public @Nonnull @Nls String getPresentableName() {
      return myFqn;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      JavaPostfixTemplateExpressionFqnCondition condition = (JavaPostfixTemplateExpressionFqnCondition)o;
      return Objects.equals(myFqn, condition.myFqn);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myFqn);
    }

    @Override
    public void serializeTo(@Nonnull Element element) {
      JavaPostfixTemplateExpressionCondition.super.serializeTo(element);
      element.setAttribute(FQN_ATTR, getFqn());
    }
  }

  class JavaPostfixTemplateVoidExpressionCondition implements JavaPostfixTemplateExpressionCondition {
    public static final @NonNls String ID = "void";

    @Override
    public boolean test(@Nonnull PsiExpression element) {
      PsiType type = element.getType();
      return type != null && PsiTypes.voidType().equals(type);
    }

    @Nonnull
    @Override
    public String getId() {
      return ID;
    }

    @Override
    public @Nonnull @Nls String getPresentableName() {
      return JavaBundle.message("postfix.template.condition.void.name");
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      return o != null && getClass() == o.getClass();
    }

    @Override
    public int hashCode() {
      return getClass().hashCode();
    }
  }


  class JavaPostfixTemplateNonVoidExpressionCondition implements JavaPostfixTemplateExpressionCondition {
    public static final @NonNls String ID = "non void";

    @Override
    public boolean test(@Nonnull PsiExpression element) {
      return JavaPostfixTemplatesUtils.isNonVoid(element.getType());
    }

    @Nonnull
    @Override
    public String getId() {
      return ID;
    }

    @Override
    public @Nonnull @Nls String getPresentableName() {
      return JavaBundle.message("postfix.template.condition.non.void.name");
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      return o != null && getClass() == o.getClass();
    }

    @Override
    public int hashCode() {
      return getClass().hashCode();
    }
  }

  class JavaPostfixTemplateBooleanExpressionCondition implements JavaPostfixTemplateExpressionCondition {
    public static final @NonNls String ID = "boolean";

    @Override
    public boolean test(@Nonnull PsiExpression element) {
      return JavaPostfixTemplatesUtils.isBoolean(element.getType());
    }

    @Nonnull
    @Override
    public String getId() {
      return ID;
    }

    @Override
    public @Nonnull @Nls String getPresentableName() {
      return JavaBundle.message("postfix.template.condition.boolean.name");
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      return o != null && getClass() == o.getClass();
    }

    @Override
    public int hashCode() {
      return getClass().hashCode();
    }
  }

  class JavaPostfixTemplateNumberExpressionCondition implements JavaPostfixTemplateExpressionCondition {
    public static final @NonNls String ID = "number";

    @Override
    public boolean test(@Nonnull PsiExpression element) {
      return JavaPostfixTemplatesUtils.isNumber(element.getType());
    }

    @Nonnull
    @Override
    public String getId() {
      return ID;
    }

    @Override
    public @Nonnull @Nls String getPresentableName() {
      return JavaBundle.message("postfix.template.condition.number.name");
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      return o != null && getClass() == o.getClass();
    }

    @Override
    public int hashCode() {
      return getClass().hashCode();
    }
  }

  class JavaPostfixTemplateNotPrimitiveTypeExpressionCondition implements JavaPostfixTemplateExpressionCondition {
    public static final @NonNls String ID = "notPrimitive";

    @Override
    public boolean test(@Nonnull PsiExpression element) {
      return JavaPostfixTemplatesUtils.isNotPrimitiveTypeExpression(element);
    }

    @Nonnull
    @Override
    public String getId() {
      return ID;
    }

    @Override
    public @Nonnull @Nls String getPresentableName() {
      return JavaBundle.message("postfix.template.condition.not.primitive.type.name");
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      return o != null && getClass() == o.getClass();
    }

    @Override
    public int hashCode() {
      return getClass().hashCode();
    }
  }

  class JavaPostfixTemplateArrayExpressionCondition implements JavaPostfixTemplateExpressionCondition {
    public static final @NonNls String ID = "array";

    @Override
    public boolean test(@Nonnull PsiExpression element) {
      return JavaPostfixTemplatesUtils.isArray(element.getType());
    }

    @Nonnull
    @Override
    public String getId() {
      return ID;
    }

    @Override
    public @Nonnull @Nls String getPresentableName() {
      return JavaBundle.message("postfix.template.condition.array.name");
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      return o != null && getClass() == o.getClass();
    }

    @Override
    public int hashCode() {
      return getClass().hashCode();
    }
  }
}
