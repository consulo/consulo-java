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
package consulo.java.properties.impl.i18n.folding;

import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.codeInsight.folding.JavaCodeFoldingSettings;
import com.intellij.java.language.impl.psi.impl.JavaConstantExpressionEvaluator;
import com.intellij.java.language.psi.*;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.Property;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.document.Document;
import consulo.java.analysis.impl.util.JavaI18nUtil;
import consulo.java.properties.impl.i18n.JavaPropertiesUtil;
import consulo.language.Language;
import consulo.language.ast.ASTNode;
import consulo.language.editor.folding.FoldingBuilderEx;
import consulo.language.editor.folding.FoldingDescriptor;
import consulo.language.impl.psi.SourceTreeToPsiMap;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiPolyVariantReference;
import consulo.language.psi.PsiReference;
import consulo.language.psi.ResolveResult;
import consulo.project.Project;
import consulo.util.dataholder.Key;
import consulo.util.lang.ObjectUtil;
import consulo.util.lang.StringUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;

/**
 * @author Konstantin Bulenkov
 */
@ExtensionImpl
public class PropertyFoldingBuilder extends FoldingBuilderEx {
  private static final int FOLD_MAX_LENGTH = 50;
  private static final Key<Object> CACHE = Key.create("i18n.property.cache");

  @RequiredReadAction
  @Override
  @Nonnull
  public FoldingDescriptor[] buildFoldRegions(@Nonnull PsiElement element, @Nonnull Document document, boolean quick) {
    if (!(element instanceof PsiJavaFile) || quick || !isFoldingsOn()) {
      return FoldingDescriptor.EMPTY;
    }
    final PsiJavaFile file = (PsiJavaFile) element;
    final Project project = file.getProject();
    final List<FoldingDescriptor> result = new ArrayList<>();
    //hack here because JspFile PSI elements are not threaded correctly via nextSibling/prevSibling
    file.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitLiteralExpression(PsiLiteralExpression expression) {
        checkLiteral(project, expression, result, document);
      }
    });

    return result.toArray(new FoldingDescriptor[result.size()]);
  }

  private static boolean isFoldingsOn() {
    return JavaCodeFoldingSettings.getInstance().isCollapseI18nMessages();
  }

  @RequiredReadAction
  private static void checkLiteral(Project project, PsiLiteralExpression expression, List<FoldingDescriptor> result, Document document) {
    if (isI18nProperty(expression)) {
      final IProperty property = getI18nProperty(project, expression);
      final HashSet<Object> set = new HashSet<>();
      if (property != null) {
        set.add(property);
      }
      final String msg = formatI18nProperty(expression, property);

      final PsiElement parent = expression.getParent();
      if (!msg.equals(expression.getText()) &&
          parent instanceof PsiExpressionList &&
          ((PsiExpressionList) parent).getExpressions()[0] == expression) {
        final PsiExpressionList expressions = (PsiExpressionList) parent;
        final int count = JavaPropertiesUtil.getPropertyValueParamsMaxCount(expression);
        final PsiExpression[] args = expressions.getExpressions();
        PsiElement elementToFold = parent.getParent();
        if (args.length == 1 + count && elementToFold instanceof PsiMethodCallExpression) {
          boolean ok = true;
          for (int i = 1; i < count + 1; i++) {
            Object value = JavaConstantExpressionEvaluator.computeConstantExpression(args[i], false);
            if (value == null) {
              if (!(args[i] instanceof PsiReferenceExpression)) {
                ok = false;
                break;
              }
            }
          }
          if (ok) {
            result.add(new FoldingDescriptor(ObjectUtil.assertNotNull(elementToFold.getNode()),
                                             elementToFold.getTextRange(), null, set));
            if (property != null) {
              EditPropertyValueAction.registerFoldedElement(elementToFold, document);
            }
            return;
          }
        }
      }

      result.add(new FoldingDescriptor(ObjectUtil.assertNotNull(expression.getNode()),
          expression.getTextRange(), null, set));
      if (property != null) {
        EditPropertyValueAction.registerFoldedElement(expression, document);
      }
    }
  }


  @Override
  public String getPlaceholderText(@Nonnull ASTNode node) {
    final PsiElement element = SourceTreeToPsiMap.treeElementToPsi(node);
    if (element instanceof PsiLiteralExpression) {
      return getI18nMessage(element.getProject(), (PsiLiteralExpression) element);
    } else if (element instanceof PsiMethodCallExpression) {
      return formatMethodCallExpression(element.getProject(), (PsiMethodCallExpression) element);
    }
    return element.getText();
  }

  private static String formatMethodCallExpression(Project project, PsiMethodCallExpression methodCallExpression) {
    final PsiExpression[] args = methodCallExpression.getArgumentList().getExpressions();
    if (args.length > 0 && args[0] instanceof PsiLiteralExpression && args[0].isValid() && isI18nProperty(
      (PsiLiteralExpression) args[0])) {
      final int count = JavaPropertiesUtil.getPropertyValueParamsMaxCount((PsiLiteralExpression) args[0]);
      if (args.length == 1 + count) {
        String text = getI18nMessage(project, (PsiLiteralExpression) args[0]);
        for (int i = 1; i < count + 1; i++) {
          Object value = JavaConstantExpressionEvaluator.computeConstantExpression(args[i], false);
          if (value == null) {
            if (args[i] instanceof PsiReferenceExpression) {
              value = "{" + args[i].getText() + "}";
            } else {
              text = null;
              break;
            }
          }
          text = text.replace("{" + (i - 1) + "}", value.toString());
        }
        if (text != null) {
          if (!text.equals(methodCallExpression.getText())) {
            text = text.replace("''", "'");
          }
          return text.length() > FOLD_MAX_LENGTH ? text.substring(0, FOLD_MAX_LENGTH - 3) + "...\"" : text;
        }
      }
    }

    return methodCallExpression.getText();
  }

  private static String getI18nMessage(@Nonnull Project project, PsiLiteralExpression literal) {
    final IProperty property = getI18nProperty(project, literal);
    return property == null ? literal.getText() : formatI18nProperty(literal, property);
  }

  @Nullable
  private static IProperty getI18nProperty(Project project, PsiLiteralExpression literal) {
    final Object value = literal.getUserData(CACHE);
    if (value == ObjectUtil.NULL) {
      return null;
    }

    if (value instanceof Property property && isValid(property, literal)) {
      return property;
    }

    if (isI18nProperty(literal)) {
      final PsiReference[] references = literal.getReferences();
      for (PsiReference reference : references) {
        if (reference instanceof PsiPolyVariantReference) {
          final ResolveResult[] results = ((PsiPolyVariantReference) reference).multiResolve(false);
          for (ResolveResult result : results) {
            final PsiElement element = result.getElement();
            if (element instanceof IProperty) {
              IProperty p = (IProperty) element;
              literal.putUserData(CACHE, p);
              return p;
            }
          }
        } else {
          final PsiElement element = reference.resolve();
          if (element instanceof IProperty) {
            IProperty p = (IProperty) element;
            literal.putUserData(CACHE, p);
            return p;
          }
        }
      }
    }
    return null;
  }

  private static boolean isValid(Property property, PsiLiteralExpression literal) {
    if (literal == null || property == null || !property.isValid()) {
      return false;
    }
    return StringUtil.unquoteString(literal.getText()).equals(property.getKey());
  }

  private static String formatI18nProperty(PsiLiteralExpression literal, IProperty property) {
    return property == null ? literal.getText() : "\"" + property.getValue() + "\"";
  }

  @Override
  public boolean isCollapsedByDefault(@Nonnull ASTNode node) {
    return isFoldingsOn();
  }


  public static boolean isI18nProperty(@Nonnull PsiLiteralExpression expr) {
    if (!isStringLiteral(expr)) {
      return false;
    }
    
    final Object value = expr.getUserData(CACHE);
    if (value == ObjectUtil.NULL) {
      return false;
    }

    if (value != null) {
      return true;
    }

    final Map<String, Object> annotationParams = new HashMap<>();
    annotationParams.put(AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER, null);
    final boolean isI18n = JavaI18nUtil.mustBePropertyKey(expr.getProject(), expr, annotationParams);
    if (!isI18n) {
      expr.putUserData(CACHE, ObjectUtil.NULL);
    }
    return isI18n;
  }

  private static boolean isStringLiteral(PsiLiteralExpression expr) {
    final String text;
    if (expr == null || (text = expr.getText()) == null) {
      return false;
    }
    return text.startsWith("\"") && text.endsWith("\"") && text.length() > 2;
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }
}
