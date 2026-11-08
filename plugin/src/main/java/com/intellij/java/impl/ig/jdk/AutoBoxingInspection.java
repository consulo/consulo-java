/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.jdk;

import com.intellij.java.analysis.codeInspection.ParenthesesUtils;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.*;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import org.jspecify.annotations.Nullable;

import javax.swing.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiPrimitiveType;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.PsiTypeCastExpression;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.intellij.java.language.psi.CommonClassNames;
import com.intellij.java.language.psi.PsiMethodCallExpression;
import com.intellij.java.language.psi.PsiReferenceExpression;
import com.intellij.java.language.psi.PsiTypes;
import com.siyeh.ig.psiutils.MethodCallUtils;

@ExtensionImpl
public class AutoBoxingInspection extends BaseInspection {
    @SuppressWarnings({"PublicField"})
    public boolean ignoreAddedToCollection = false;

    /**
     * @noinspection StaticCollection
     */
    static final Map<String, String> s_boxingClasses = new HashMap<String, String>(8);

    static {
        s_boxingClasses.put("byte", CommonClassNames.JAVA_LANG_BYTE);
        s_boxingClasses.put("short", CommonClassNames.JAVA_LANG_SHORT);
        s_boxingClasses.put("int", CommonClassNames.JAVA_LANG_INTEGER);
        s_boxingClasses.put("long", CommonClassNames.JAVA_LANG_LONG);
        s_boxingClasses.put("float", CommonClassNames.JAVA_LANG_FLOAT);
        s_boxingClasses.put("double", CommonClassNames.JAVA_LANG_DOUBLE);
        s_boxingClasses.put("boolean", CommonClassNames.JAVA_LANG_BOOLEAN);
        s_boxingClasses.put("char", CommonClassNames.JAVA_LANG_CHARACTER);
    }

    static final Set<String> convertableBoxedClassNames = new HashSet<String>();

    static {
        convertableBoxedClassNames.add(CommonClassNames.JAVA_LANG_BYTE);
        convertableBoxedClassNames.add(CommonClassNames.JAVA_LANG_CHARACTER);
        convertableBoxedClassNames.add(CommonClassNames.JAVA_LANG_SHORT);
    }

    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.autoBoxingDisplayName();
    }

    @Override
    public String getAlternativeID() {
        return "boxing";
    }

    @Override
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.autoBoxingProblemDescriptor().get();
    }

    @Override
    @Nullable
    public JComponent createOptionsPanel() {
        LocalizeValue message = InspectionGadgetsLocalize.autoBoxingIgnoreAddedToCollectionOption();
        return new SingleCheckboxOptionsPanel(message.get(), this, "ignoreAddedToCollection");
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new AutoBoxingVisitor();
    }

    @Override
    public InspectionGadgetsFix buildFix(Object... infos) {
        return new AutoBoxingFix();
    }

    private static class AutoBoxingFix extends InspectionGadgetsFix {
        @Override
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.autoBoxingMakeBoxingExplicitQuickfix();
        }

        @Override
        public void doFix(Project project, ProblemDescriptor descriptor) {
            PsiExpression expression = (PsiExpression) descriptor.getPsiElement();
            PsiType expectedType = ExpectedTypeUtils.findExpectedType(expression, false);
            if (expectedType == null) {
                return;
            }
            String expectedTypeText = expectedType.getCanonicalText();
            String classToConstruct;
            if (s_boxingClasses.containsValue(expectedTypeText)) {
                classToConstruct = expectedTypeText;
            }
            else {
                PsiType type = expression.getType();
                if (type == null) {
                    return;
                }
                String expressionTypeText = type.getCanonicalText();
                classToConstruct = s_boxingClasses.get(expressionTypeText);
            }
            if (shortcutReplace(expression, classToConstruct)) {
                return;
            }
            PsiExpression strippedExpression = ParenthesesUtils.stripParentheses(expression);
            if (strippedExpression == null) {
                return;
            }
            String expressionText = strippedExpression.getText();
            String newExpression;
            if ("true".equals(expressionText)) {
                newExpression = "java.lang.Boolean.TRUE";
            }
            else if ("false".equals(expressionText)) {
                newExpression = "java.lang.Boolean.FALSE";
            }
            else {
                newExpression = classToConstruct + ".valueOf(" + expressionText + ')';
            }
            replaceExpression(expression, newExpression);
        }

        private static boolean shortcutReplace(PsiExpression expression, String classToConstruct) {
            if (!(expression instanceof PsiMethodCallExpression)) {
                return false;
            }
            PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression) expression;
            PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
            PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
            if (qualifierExpression == null) {
                return false;
            }
            if (classToConstruct.equals(CommonClassNames.JAVA_LANG_INTEGER)) {
                if (MethodCallUtils.isCallToMethod(methodCallExpression, CommonClassNames.JAVA_LANG_INTEGER, PsiType.INT, "intValue")) {
                    expression.replace(qualifierExpression);
                    return true;
                }
            }
            else if (classToConstruct.equals(CommonClassNames.JAVA_LANG_SHORT)) {
                if (MethodCallUtils.isCallToMethod(methodCallExpression, CommonClassNames.JAVA_LANG_SHORT, PsiType.SHORT, "shortValue")) {
                    expression.replace(qualifierExpression);
                    return true;
                }
            }
            else if (classToConstruct.equals(CommonClassNames.JAVA_LANG_BYTE)) {
                if (MethodCallUtils.isCallToMethod(methodCallExpression, CommonClassNames.JAVA_LANG_BYTE, PsiType.BYTE, "byteValue")) {
                    expression.replace(qualifierExpression);
                    return true;
                }
            }
            else if (classToConstruct.equals(CommonClassNames.JAVA_LANG_CHARACTER)) {
                if (MethodCallUtils.isCallToMethod(methodCallExpression, CommonClassNames.JAVA_LANG_CHARACTER, PsiType.CHAR, "charValue")) {
                    expression.replace(qualifierExpression);
                    return true;
                }
            }
            else if (classToConstruct.equals(CommonClassNames.JAVA_LANG_LONG)) {
                if (MethodCallUtils.isCallToMethod(methodCallExpression, CommonClassNames.JAVA_LANG_LONG, PsiType.LONG, "longValue")) {
                    expression.replace(qualifierExpression);
                    return true;
                }
            }
            else if (classToConstruct.equals(CommonClassNames.JAVA_LANG_FLOAT)) {
                if (MethodCallUtils.isCallToMethod(methodCallExpression, CommonClassNames.JAVA_LANG_FLOAT, PsiType.FLOAT, "floatValue")) {
                    expression.replace(qualifierExpression);
                    return true;
                }
            }
            else if (classToConstruct.equals(CommonClassNames.JAVA_LANG_DOUBLE)) {
                if (MethodCallUtils.isCallToMethod(
                    methodCallExpression,
                    CommonClassNames.JAVA_LANG_DOUBLE,
                    PsiType.DOUBLE,
                    "doubleValue"
                )) {
                    expression.replace(qualifierExpression);
                    return true;
                }
            }
            return false;
        }
    }

    private class AutoBoxingVisitor extends BaseInspectionVisitor {

        @Override
        public void visitElement(PsiElement element) {
            if (element.getLanguage() != JavaLanguage.INSTANCE) {
                return;
            }
            if (!PsiUtil.isLanguageLevel5OrHigher(element)) {
                return;
            }
            super.visitElement(element);
        }

        @Override
        public void visitArrayAccessExpression(PsiArrayAccessExpression expression) {
            super.visitArrayAccessExpression(expression);
            checkExpression(expression);
        }

        @Override
        public void visitAssignmentExpression(PsiAssignmentExpression expression) {
            super.visitAssignmentExpression(expression);
            checkExpression(expression);
        }

        @Override
        public void visitConditionalExpression(PsiConditionalExpression expression) {
            super.visitConditionalExpression(expression);
            checkExpression(expression);
        }

        @Override
        public void visitInstanceOfExpression(PsiInstanceOfExpression expression) {
            super.visitInstanceOfExpression(expression);
            checkExpression(expression);
        }

        @Override
        public void visitLiteralExpression(PsiLiteralExpression expression) {
            super.visitLiteralExpression(expression);
            checkExpression(expression);
        }

        @Override
        public void visitMethodCallExpression(PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            checkExpression(expression);
        }

        @Override
        public void visitParenthesizedExpression(PsiParenthesizedExpression expression) {
            super.visitParenthesizedExpression(expression);
            checkExpression(expression);
        }

        @Override
        public void visitPolyadicExpression(PsiPolyadicExpression expression) {
            super.visitPolyadicExpression(expression);
            checkExpression(expression);
        }

        @Override
        public void visitPostfixExpression(PsiPostfixExpression expression) {
            super.visitPostfixExpression(expression);
            checkExpression(expression);
        }

        @Override
        public void visitPrefixExpression(PsiPrefixExpression expression) {
            super.visitPrefixExpression(expression);
            checkExpression(expression);
        }

        @Override
        public void visitReferenceExpression(PsiReferenceExpression expression) {
            super.visitReferenceExpression(expression);
            checkExpression(expression);
        }

        @Override
        public void visitTypeCastExpression(PsiTypeCastExpression expression) {
            super.visitTypeCastExpression(expression);
            checkExpression(expression);
        }

        private void checkExpression(PsiExpression expression) {
            if (expression.getParent() instanceof PsiParenthesizedExpression) {
                return;
            }
            PsiType expressionType = expression.getType();
            if (expressionType == null || expressionType.equals(PsiType.VOID) || !TypeConversionUtil.isPrimitiveAndNotNull(expressionType)) {
                return;
            }
            PsiPrimitiveType primitiveType = (PsiPrimitiveType) expressionType;
            PsiClassType boxedType = primitiveType.getBoxedType(expression);
            if (boxedType == null) {
                return;
            }
            PsiType expectedType = ExpectedTypeUtils.findExpectedType(expression, false);
            if (expectedType == null || ClassUtils.isPrimitive(expectedType)) {
                return;
            }
            if (!expectedType.isAssignableFrom(boxedType)) {
                // JLS 5.2 Assignment Conversion
                // check if a narrowing primitive conversion is applicable
                if (!(expectedType instanceof PsiClassType) || !PsiUtil.isConstantExpression(expression)) {
                    return;
                }
                PsiClassType classType = (PsiClassType) expectedType;
                String className = classType.getCanonicalText();
                if (!convertableBoxedClassNames.contains(className)) {
                    return;
                }
                if (!PsiType.BYTE.equals(expressionType) && !PsiType.CHAR.equals(expressionType) &&
                    !PsiType.SHORT.equals(expressionType) && !PsiType.INT.equals(expressionType)) {
                    return;
                }
            }
            if (ignoreAddedToCollection && isAddedToCollection(expression)) {
                return;
            }
            registerError(expression);
        }

        private boolean isAddedToCollection(PsiExpression expression) {
            PsiElement parent = expression.getParent();
            if (!(parent instanceof PsiExpressionList)) {
                return false;
            }
            PsiExpressionList expressionList = (PsiExpressionList) parent;
            PsiElement grandParent = expressionList.getParent();
            if (!(grandParent instanceof PsiMethodCallExpression)) {
                return false;
            }
            PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression) grandParent;
            PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
            String methodName = methodExpression.getReferenceName();
            if (!"put".equals(methodName) && !"set".equals(methodName) && !"add".equals(methodName)) {
                return false;
            }
            PsiExpression qualifier = methodExpression.getQualifierExpression();
            return TypeUtils.expressionHasTypeOrSubtype(
                qualifier,
                CommonClassNames.JAVA_UTIL_COLLECTION,
                CommonClassNames.JAVA_UTIL_MAP
            ) != null;
        }
    }


  /**
   * Replaces expression of primitive type with explicitly boxed expression
   *
   * @param expression expression to box
   */
  public static void replaceWithBoxing(PsiExpression expression) {
    final PsiType type = expression.getType();
    if (!(type instanceof PsiPrimitiveType)) return;
    final PsiType expectedType = ((PsiPrimitiveType)type).getBoxedType(expression);
    if (expectedType == null) {
      return;
    }
    final String expectedTypeText = expectedType.getCanonicalText();
    final String classToConstruct;
    if (s_boxingClasses.containsValue(expectedTypeText)) {
      classToConstruct = expectedTypeText;
    }
    else {
      final String expressionTypeText = type.getCanonicalText();
      classToConstruct = s_boxingClasses.get(expressionTypeText);
    }
    if (shortcutReplace(expression, classToConstruct)) {
      return;
    }
    final PsiExpression strippedExpression = PsiUtil.skipParenthesizedExprDown(expression);
    if (strippedExpression == null) {
      return;
    }
    CommentTracker commentTracker = new CommentTracker();
    final String expressionText = strippedExpression.getText();
    final String newExpression;
    if ("true".equals(expressionText)) {
      newExpression = "java.lang.Boolean.TRUE";
    }
    else if ("false".equals(expressionText)) {
      newExpression = "java.lang.Boolean.FALSE";
    }
    else {
      commentTracker.markUnchanged(strippedExpression);
      newExpression = classToConstruct + ".valueOf(" + expressionText + ')';
    }
    final PsiElement parent = expression.getParent();
    if (parent instanceof PsiTypeCastExpression typeCastExpression) {
      PsiReplacementUtil.replaceExpression(typeCastExpression, newExpression, commentTracker);
    }
    else {
      PsiReplacementUtil.replaceExpression(expression, newExpression, commentTracker);
    }
  }


  private static boolean shortcutReplace(PsiExpression expression, String classToConstruct) {
    if (!(expression instanceof PsiMethodCallExpression methodCallExpression)) {
      return false;
    }
    final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
    final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
    if (qualifierExpression == null) {
      return false;
    }
    boolean shouldReplace = switch (classToConstruct) {
      case CommonClassNames.JAVA_LANG_INTEGER ->
        MethodCallUtils.isCallToMethod(methodCallExpression, CommonClassNames.JAVA_LANG_INTEGER, PsiTypes.intType(), "intValue");
      case CommonClassNames.JAVA_LANG_SHORT ->
        MethodCallUtils.isCallToMethod(methodCallExpression, CommonClassNames.JAVA_LANG_SHORT, PsiTypes.shortType(), "shortValue");
      case CommonClassNames.JAVA_LANG_BYTE ->
        MethodCallUtils.isCallToMethod(methodCallExpression, CommonClassNames.JAVA_LANG_BYTE, PsiTypes.byteType(), "byteValue");
      case CommonClassNames.JAVA_LANG_CHARACTER ->
        MethodCallUtils.isCallToMethod(methodCallExpression, CommonClassNames.JAVA_LANG_CHARACTER, PsiTypes.charType(), "charValue");
      case CommonClassNames.JAVA_LANG_LONG ->
        MethodCallUtils.isCallToMethod(methodCallExpression, CommonClassNames.JAVA_LANG_LONG, PsiTypes.longType(), "longValue");
      case CommonClassNames.JAVA_LANG_FLOAT ->
        MethodCallUtils.isCallToMethod(methodCallExpression, CommonClassNames.JAVA_LANG_FLOAT, PsiTypes.floatType(), "floatValue");
      case CommonClassNames.JAVA_LANG_DOUBLE ->
        MethodCallUtils.isCallToMethod(methodCallExpression, CommonClassNames.JAVA_LANG_DOUBLE, PsiTypes.doubleType(), "doubleValue");
      default -> false;
    };
    if (shouldReplace) {
      expression.replace(qualifierExpression);
      return true;
    }
    return false;
  }
}
