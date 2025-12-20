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
package com.intellij.java.impl.codeInsight.daemon.impl;

import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.impl.psi.impl.JavaConstantExpressionEvaluator;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiTypesUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.psi.ElementColorProvider;
import consulo.language.psi.PsiElement;
import consulo.ui.color.ColorValue;
import consulo.ui.color.RGBColor;
import consulo.ui.ex.awt.util.ColorUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
@ExtensionImpl
public class JavaColorProvider implements ElementColorProvider {
  // @Override
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }

  @RequiredReadAction
  @Override
  public ColorValue getColorFrom(@Nonnull PsiElement element) {
    return getJavaColorFromExpression(element);
  }

  public static boolean isColorType(@Nullable PsiType type) {
    if (type != null) {
      PsiClass aClass = PsiTypesUtil.getPsiClass(type);
      if (aClass != null) {
        String fqn = aClass.getQualifiedName();
        if ("java.awt.Color".equals(fqn) || "javax.swing.plaf.ColorUIResource".equals(fqn)) {
          return true;
        }
      }
    }
    return false;
  }

  @Nullable
  public static ColorValue getJavaColorFromExpression(@Nullable PsiElement element) {
    if (element instanceof PsiNewExpression) {
      PsiNewExpression expr = (PsiNewExpression) element;
      if (isColorType(expr.getType())) {
        return getColor(expr.getArgumentList());
      }
    }
    return null;
  }

  @Nullable
  private static ColorValue getColor(PsiExpressionList list) {
    try {
      PsiExpression[] args = list.getExpressions();
      PsiType[] types = list.getExpressionTypes();
      ColorConstructors type = getConstructorType(types);
      if (type != null) {
        switch (type) {
          case INT: {
            int i = getInt(args[0]);
            return new RGBColor((i >> 16) & 0xFF, (i >> 8) & 0xFF, i & 0xFF);
          }
          case INT_BOOL: {
            int i = getInt(args[0]);
            if (getBoolean(args[1])) {
              return new RGBColor((i >> 16) & 0xFF, (i >> 8) & 0xFF, i & 0xFF, ((i >> 24) & 0xFF) / 255f);
            }
            return new RGBColor((i >> 16) & 0xFF, (i >> 8) & 0xFF, i & 0xFF);
          }
          case INT_x3:
            return new RGBColor(getInt(args[0]), getInt(args[1]), getInt(args[2]));
          case INT_x4:
            return new RGBColor(getInt(args[0]), getInt(args[1]), getInt(args[2]), getInt(args[3]) / 255f);
          case FLOAT_x3: {
            float r = getFloat(args[0]);
            float g = getFloat(args[1]);
            float b = getFloat(args[2]);
            return new RGBColor((int) (r * 255 + 0.5), (int) (g * 255 + 0.5), (int) (b * 255 + 0.5));
          }
          case FLOAT_x4: {
            float r = getFloat(args[0]);
            float g = getFloat(args[1]);
            float b = getFloat(args[2]);
            float a = getFloat(args[3]);
            return new RGBColor((int) (r * 255 + 0.5), (int) (g * 255 + 0.5), (int) (b * 255 + 0.5), a);
          }
        }
      }
    } catch (Exception ignore) {
    }
    return null;
  }

  @Nullable
  private static ColorConstructors getConstructorType(PsiType[] types) {
    int len = types.length;
    if (len == 0) {
      return null;
    }

    switch (len) {
      case 1:
        return ColorConstructors.INT;
      case 2:
        return ColorConstructors.INT_BOOL;
      case 3:
        return PsiType.INT.equals(types[0]) ? ColorConstructors.INT_x3 : ColorConstructors.FLOAT_x3;
      case 4:
        return PsiType.INT.equals(types[0]) ? ColorConstructors.INT_x4 : ColorConstructors.FLOAT_x4;
    }

    return null;
  }

  public static int getInt(PsiExpression expr) {
    return ((Number) getObject(expr)).intValue();
  }

  public static float getFloat(PsiExpression expr) {
    return ((Number) getObject(expr)).floatValue();
  }

  public static boolean getBoolean(PsiExpression expr) {
    return ((Boolean) getObject(expr)).booleanValue();
  }

  private static Object getObject(PsiExpression expr) {
    return JavaConstantExpressionEvaluator.computeConstantExpression(expr, true);
  }

  @RequiredWriteAction
  @Override
  public void setColorTo(@Nonnull PsiElement element, @Nonnull ColorValue colorValue) {
    PsiExpressionList argumentList = ((PsiNewExpression) element).getArgumentList();
    assert argumentList != null;

    PsiExpression[] expr = argumentList.getExpressions();
    ColorConstructors type = getConstructorType(argumentList.getExpressionTypes());

    assert type != null;

    RGBColor rgb = colorValue.toRGB();

    switch (type) {
      case INT:
      case INT_BOOL: {
        int value = (((int) (rgb.getAlpha() * 255) & 0xFF) << 24) | ((rgb.getRed() & 0xFF) << 16) | ((rgb.getGreen() & 0xFF) << 8) | ((rgb.getBlue() & 0xFF) << 0);

        replaceInt(expr[0], value, true);
        return;
      }
      case INT_x3:
      case INT_x4:
        replaceInt(expr[0], rgb.getRed());
        replaceInt(expr[1], rgb.getGreen());
        replaceInt(expr[2], rgb.getBlue());
        if (type == ColorConstructors.INT_x4) {
          replaceInt(expr[3], (int) (rgb.getAlpha() * 255));
        } else if (rgb.getAlpha() != 1f) {
          //todo add alpha
        }
        return;
      case FLOAT_x3:
      case FLOAT_x4:
        float[] rgba = rgb.getFloatValues();
        replaceFloat(expr[0], rgba[0]);
        replaceFloat(expr[1], rgba[1]);
        replaceFloat(expr[2], rgba[2]);
        if (type == ColorConstructors.FLOAT_x4) {
          replaceFloat(expr[3], rgba.length == 4 ? rgba[3] : 0f);
        } else if (rgb.getAlpha() != 1f) {
          //todo add alpha
        }
    }
  }

  private static void replaceInt(PsiExpression expr, int newValue) {
    replaceInt(expr, newValue, false);
  }

  private static void replaceInt(PsiExpression expr, int newValue, boolean hex) {
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(expr.getProject());
    if (getInt(expr) != newValue) {
      String text = hex ? "0x" + ColorUtil.toHex(new Color(newValue)).toUpperCase() : Integer.toString(newValue);
      expr.replace(factory.createExpressionFromText(text, null));
    }
  }

  private static void replaceFloat(PsiExpression expr, float newValue) {
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(expr.getProject());
    if (getFloat(expr) != newValue) {
      expr.replace(factory.createExpressionFromText(String.valueOf(newValue) + "f", null));
    }
  }

  private enum ColorConstructors {
    INT,
    INT_BOOL,
    INT_x3,
    INT_x4,
    FLOAT_x3,
    FLOAT_x4
  }
}
