/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.java.language.patterns;

import com.intellij.java.language.psi.*;
import consulo.language.pattern.ElementPattern;
import consulo.language.pattern.IElementTypePattern;
import consulo.language.pattern.InitialPatternCondition;
import consulo.language.pattern.InitialPatternConditionPlus;
import consulo.language.pattern.PlatformPatterns;
import consulo.language.pattern.StandardPatterns;
import consulo.language.psi.PsiElement;
import consulo.language.ast.IElementType;
import consulo.language.util.ProcessingContext;
import consulo.language.pattern.VirtualFilePattern;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * @author peter
 */
public class PsiJavaPatterns extends StandardPatterns {

  public static IElementTypePattern elementType() {
    return PlatformPatterns.elementType();
  }

  public static VirtualFilePattern virtualFile() {
    return PlatformPatterns.virtualFile();
  }

  public static PsiJavaElementPattern.Capture<PsiElement> psiJavaElement() {
    return new PsiJavaElementPattern.Capture<>(PsiElement.class);
  }

  public static PsiJavaElementPattern.Capture<PsiElement> psiJavaElement(IElementType type) {
    return psiJavaElement().withElementType(type);
  }

  public static <T extends PsiElement> PsiJavaElementPattern.Capture<T> psiJavaElement(final Class<T> aClass) {
    return new PsiJavaElementPattern.Capture<>(aClass);
  }

  @SafeVarargs
  public static PsiJavaElementPattern.Capture<PsiElement> psiJavaElement(final Class<? extends PsiElement>... classAlternatives) {
    return new PsiJavaElementPattern.Capture<>(new InitialPatternCondition<>(PsiElement.class) {
      @Override
      public boolean accepts(@Nullable Object o, ProcessingContext context) {
        for (Class<? extends PsiElement> classAlternative : classAlternatives) {
          if (classAlternative.isInstance(o)) {
            return true;
          }
        }
        return false;
      }
    });
  }

  public static PsiJavaElementPattern.Capture<PsiLiteralExpression> literalExpression() {
    return literalExpression(null);
  }

  public static PsiJavaElementPattern.Capture<PsiLiteral> psiLiteral() {
    return psiLiteral(null);
  }

  public static PsiJavaElementPattern.Capture<PsiLiteral> psiLiteral(@Nullable final ElementPattern value) {
    return new PsiJavaElementPattern.Capture<>(new InitialPatternConditionPlus<>(PsiLiteral.class) {
      public boolean accepts(@Nullable final Object o, final ProcessingContext context) {
        return o instanceof PsiLiteral && (value == null || value.accepts(((PsiLiteral)o).getValue(), context));
      }

      @Override
      public List<ElementPattern<?>> getPatterns() {
        return Collections.<ElementPattern<?>>singletonList(value);
      }
    });
  }

  public static PsiJavaElementPattern.Capture<PsiNewExpression> psiNewExpression(@Nonnull final String... fqns) {
    return new PsiJavaElementPattern.Capture<>(new InitialPatternCondition<>(PsiNewExpression.class) {
      public boolean accepts(@Nullable final Object o, final ProcessingContext context) {
        if(o instanceof PsiNewExpression) {
          PsiJavaCodeReferenceElement reference = ((PsiNewExpression)o).getClassOrAnonymousClassReference();
          if (reference != null) {
            for (String fqn : fqns) {
              if( fqn.equals(reference.getQualifiedName())) return true;
            }
          }
        }
        return  false;
      }
    });
  }

  public static PsiJavaElementPattern.Capture<PsiLiteralExpression> literalExpression(@Nullable final ElementPattern value) {
    return new PsiJavaElementPattern.Capture<>(new InitialPatternConditionPlus<>(PsiLiteralExpression.class) {
      public boolean accepts(@Nullable final Object o, final ProcessingContext context) {
        return o instanceof PsiLiteralExpression && (value == null || value.accepts(((PsiLiteralExpression)o).getValue(), context));
      }

      @Override
      public List<ElementPattern<?>> getPatterns() {
        return Collections.<ElementPattern<?>>singletonList(value);
      }
    });
  }

  public static PsiMemberPattern.Capture psiMember() {
    return new PsiMemberPattern.Capture();
  }

  public static PsiMethodPattern psiMethod() {
    return new PsiMethodPattern();
  }

  public static PsiParameterPattern psiParameter() {
    return new PsiParameterPattern();
  }

  public static PsiModifierListOwnerPattern.Capture<PsiModifierListOwner> psiModifierListOwner() {
    return new PsiModifierListOwnerPattern.Capture<>(new InitialPatternCondition<>(PsiModifierListOwner.class) {
      @Override
      public boolean accepts(@Nullable Object o, ProcessingContext context) {
        return o instanceof PsiModifierListOwner;
      }
    });
  }


  public static PsiFieldPattern psiField() {
    return new PsiFieldPattern();
  }

  public static PsiClassPattern psiClass() {
    return new PsiClassPattern();
  }

  public static PsiAnnotationPattern psiAnnotation() {
    return new PsiAnnotationPattern();
  }

  public static PsiNameValuePairPattern psiNameValuePair() {
    return new PsiNameValuePairPattern();
  }

  public static PsiTypePattern psiType() {
    return new PsiTypePattern();
  }

  public static PsiExpressionPattern.Capture<PsiExpression> psiExpression() {
    return new PsiExpressionPattern.Capture<>(PsiExpression.class);
  }

  public static PsiBinaryExpressionPattern psiBinaryExpression() {
    return new PsiBinaryExpressionPattern();
  }

  public static PsiTypeCastExpressionPattern psiTypeCastExpression() {
    return new PsiTypeCastExpressionPattern();
  }

  public static PsiJavaElementPattern.Capture<PsiReferenceExpression> psiReferenceExpression() {
    return psiJavaElement(PsiReferenceExpression.class);
  }

  public static PsiStatementPattern.Capture<PsiExpressionStatement> psiExpressionStatement() {
    return new PsiStatementPattern.Capture<>(PsiExpressionStatement.class);
  }

  public static PsiStatementPattern.Capture<PsiReturnStatement> psiReturnStatement() {
    return new PsiStatementPattern.Capture<>(PsiReturnStatement.class);
  }
}
