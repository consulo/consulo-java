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
package com.intellij.java.language.impl.codeInsight.javadoc;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JavaDocUtil {
  private static final Logger LOG = Logger.getInstance(JavaDocUtil.class);

  private static final Pattern ourTypePattern = Pattern.compile("[ ]+[^ ^\\[^\\]]");

  private JavaDocUtil() {
  }

  /**
   * Extracts a reference to a source element from the beginning of the text.
   *
   * @return length of the extracted reference
   */
  public static int extractReference(String text) {
    int lParenIndex = text.indexOf('(');
    int spaceIndex = text.indexOf(' ');
    if (spaceIndex < 0) {
      spaceIndex = text.length();
    }
    if (lParenIndex < 0) {
      return spaceIndex;
    } else {
      if (spaceIndex < lParenIndex) {
        return spaceIndex;
      }
      int rParenIndex = text.indexOf(')', lParenIndex);
      if (rParenIndex < 0) {
        rParenIndex = text.length() - 1;
      }
      return rParenIndex + 1;
    }
  }

  @Nullable
  public static PsiElement findReferenceTarget(PsiManager manager, String refText, PsiElement context) {
    LOG.assertTrue(context == null || context.isValid());

    int poundIndex = refText.indexOf('#');
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(manager.getProject());
    if (poundIndex < 0) {
      PsiClass aClass = facade.getResolveHelper().resolveReferencedClass(refText, context);

      if (aClass == null) {
        aClass = facade.findClass(refText, context.getResolveScope());
      }

      if (aClass != null) {
        return aClass.getNavigationElement();
      }
      PsiJavaPackage aPackage = facade.findPackage(refText);
      if (aPackage != null) {
        return aPackage;
      }
      return null;
    } else {
      String classRef = refText.substring(0, poundIndex).trim();
      if (classRef.length() > 0) {
        PsiClass aClass = facade.getResolveHelper().resolveReferencedClass(classRef, context);

        if (aClass == null) {
          aClass = facade.findClass(classRef, context.getResolveScope());
        }

        if (aClass == null) {
          return null;
        }
        return findReferencedMember(aClass, refText.substring(poundIndex + 1), context);
      } else {
        String memberRefText = refText.substring(1);
        PsiElement scope = context;
        while (true) {
          if (scope instanceof PsiFile) {
            break;
          }
          if (scope instanceof PsiClass) {
            PsiElement member = findReferencedMember((PsiClass) scope, memberRefText, context);
            if (member != null) {
              return member;
            }
          }
          scope = scope.getParent();
        }
        return null;
      }
    }
  }

  @Nullable
  private static PsiElement findReferencedMember(PsiClass aClass, String memberRefText, PsiElement context) {
    int parenIndex = memberRefText.indexOf('(');
    if (parenIndex < 0) {
      String name = memberRefText;
      PsiField field = aClass.findFieldByName(name, true);
      if (field != null) {
        return field.getNavigationElement();
      }
      PsiClass inner = aClass.findInnerClassByName(name, true);
      if (inner != null) {
        return inner.getNavigationElement();
      }
      PsiMethod[] methods = aClass.getAllMethods();
      for (PsiMethod method : methods) {
        if (method.getName().equals(name)) {
          return method.getNavigationElement();
        }
      }
      return null;
    } else {
      String name = memberRefText.substring(0, parenIndex).trim();
      int rparenIndex = memberRefText.lastIndexOf(')');
      if (rparenIndex == -1) {
        return null;
      }

      String paramsText = memberRefText.substring(parenIndex + 1, rparenIndex).trim();
      StringTokenizer tokenizer = new StringTokenizer(paramsText.replaceAll("[*]", ""), ",");
      PsiType[] types = new PsiType[tokenizer.countTokens()];
      int i = 0;
      PsiElementFactory factory = JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory();
      while (tokenizer.hasMoreTokens()) {
        String paramText = tokenizer.nextToken().trim();
        try {
          Matcher typeMatcher = ourTypePattern.matcher(paramText);
          String typeText = paramText;

          if (typeMatcher.find()) {
            typeText = paramText.substring(0, typeMatcher.start());
          }

          PsiType type = factory.createTypeFromText(typeText, context);
          types[i++] = type;
        } catch (IncorrectOperationException e) {
          LOG.info(e);
        }
      }
      PsiMethod[] methods = aClass.findMethodsByName(name, true);
      MethodsLoop:
      for (PsiMethod method : methods) {
        PsiParameter[] params = method.getParameterList().getParameters();
        if (params.length != types.length) {
          continue;
        }

        for (int k = 0; k < params.length; k++) {
          PsiParameter param = params[k];
          final PsiType paramType = param.getType();
          if (types[k] != null &&
              !TypeConversionUtil.erasure(paramType).getCanonicalText().equals(types[k].getCanonicalText
                  ()) &&
              !paramType.getCanonicalText().equals(types[k].getCanonicalText()) &&
              !TypeConversionUtil.isAssignable(paramType, types[k])) {
            continue MethodsLoop;
          }
        }

        int hashIndex = memberRefText.indexOf('#', rparenIndex);
        if (hashIndex != -1) {
          int parameterNumber = Integer.parseInt(memberRefText.substring(hashIndex + 1));
          if (parameterNumber < params.length) {
            return method.getParameterList().getParameters()[parameterNumber].getNavigationElement();
          }
        }
        return method.getNavigationElement();
      }
      return null;
    }
  }

  @Nullable
  public static String getReferenceText(Project project, PsiElement element) {
    if (element instanceof PsiJavaPackage) {
      return ((PsiJavaPackage) element).getQualifiedName();
    } else if (element instanceof PsiClass) {
      final String refText = ((PsiClass) element).getQualifiedName();
      if (refText != null) {
        return refText;
      }
      return ((PsiClass) element).getName();
    } else if (element instanceof PsiField) {
      PsiField field = (PsiField) element;
      String name = field.getName();
      PsiClass aClass = field.getContainingClass();
      if (aClass != null) {
        return getReferenceText(project, aClass) + "#" + name;
      } else {
        return "#" + name;
      }
    } else if (element instanceof PsiMethod) {
      PsiMethod method = (PsiMethod) element;
      String name = method.getName();
      StringBuffer buffer = new StringBuffer();
      PsiClass aClass = method.getContainingClass();
      if (aClass != null) {
        buffer.append(getReferenceText(project, aClass));
      }
      buffer.append("#");
      buffer.append(name);
      buffer.append("(");
      PsiParameter[] params = method.getParameterList().getParameters();
      boolean spaceBeforeComma = JavaDocCodeStyle.getInstance(project).spaceBeforeComma();
      boolean spaceAfterComma = JavaDocCodeStyle.getInstance(project).spaceAfterComma();
      for (int i = 0; i < params.length; i++) {
        PsiParameter param = params[i];
        String typeText = TypeConversionUtil.erasure(param.getType()).getCanonicalText();
        buffer.append(typeText);
        if (i < params.length - 1) {
          if (spaceBeforeComma) {
            buffer.append(" ");
          }
          buffer.append(",");
          if (spaceAfterComma) {
            buffer.append(" ");
          }
        }
      }
      buffer.append(")");
      return buffer.toString();
    } else if (element instanceof PsiParameter) {
      final PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
      if (method != null) {
        return getReferenceText(project, method) +
            "#" +
            ((PsiParameterList) element.getParent()).getParameterIndex((PsiParameter) element);
      }
    }

    return null;
  }

  public static String getShortestClassName(PsiClass aClass, PsiElement context) {
    @NonNls String shortName = aClass.getName();
    if (shortName == null) {
      shortName = "null";
    }
    PsiClass containingClass = aClass.getContainingClass();
    while (containingClass != null && containingClass.isPhysical()) {
      shortName = containingClass.getName() + "." + shortName;
      containingClass = containingClass.getContainingClass();
    }

    String qName = aClass.getQualifiedName();
    if (qName == null) {
      return shortName;
    }

    final PsiManager manager = aClass.getManager();
    return manager.areElementsEquivalent(aClass, JavaPsiFacade.getInstance(manager.getProject()).getResolveHelper
        ().resolveReferencedClass(shortName, context)) ? shortName : qName;
  }

  public static String getLabelText(Project project, PsiManager manager, String refText, PsiElement context) {
    PsiElement refElement = findReferenceTarget(manager, refText, context);
    if (refElement == null) {
      return refText.replaceFirst("^#", "").replaceAll("#", ".");
    }
    int poundIndex = refText.indexOf('#');
    if (poundIndex < 0) {
      if (refElement instanceof PsiClass) {
        return getShortestClassName((PsiClass) refElement, context);
      } else {
        return refText;
      }
    } else {
      PsiClass aClass = null;
      if (refElement instanceof PsiField) {
        aClass = ((PsiField) refElement).getContainingClass();
      } else if (refElement instanceof PsiMethod) {
        aClass = ((PsiMethod) refElement).getContainingClass();
      } else if (refElement instanceof PsiClass) {
        return refText.replaceAll("#", ".");
      }
      if (aClass == null) {
        return refText;
      }
      String classRef = refText.substring(0, poundIndex).trim();
      String memberText = refText.substring(poundIndex + 1);
      String memberLabel = getMemberLabelText(project, manager, memberText, context);
      if (classRef.length() > 0) {
        PsiElement refClass = findReferenceTarget(manager, classRef, context);
        if (refClass instanceof PsiClass) {
          PsiElement scope = context;
          while (true) {
            if (scope == null || scope instanceof PsiFile) {
              break;
            }
            if (scope.equals(refClass)) {
              return memberLabel;
            }
            scope = scope.getParent();
          }
        }
        return getLabelText(project, manager, classRef, context) + "." + memberLabel;
      } else {
        return memberLabel;
      }
    }
  }

  private static String getMemberLabelText(Project project, PsiManager manager, String memberText,
                                           PsiElement context) {
    int parenIndex = memberText.indexOf('(');
    if (parenIndex < 0) {
      return memberText;
    }
    if (!StringUtil.endsWithChar(memberText, ')')) {
      return memberText;
    }
    String params = memberText.substring(parenIndex + 1, memberText.length() - 1);
    StringBuffer buffer = new StringBuffer();
    boolean spaceBeforeComma = JavaDocCodeStyle.getInstance(project).spaceBeforeComma();
    boolean spaceAfterComma = JavaDocCodeStyle.getInstance(project).spaceAfterComma();
    StringTokenizer tokenizer = new StringTokenizer(params, ",");
    while (tokenizer.hasMoreTokens()) {
      String param = tokenizer.nextToken().trim();
      int index1 = param.indexOf('[');
      if (index1 < 0) {
        index1 = param.length();
      }
      int index2 = param.indexOf(' ');
      if (index2 < 0) {
        index2 = param.length();
      }
      int index = Math.min(index1, index2);
      String className = param.substring(0, index).trim();
      String shortClassName = getLabelText(project, manager, className, context);
      buffer.append(shortClassName);
      buffer.append(param.substring(className.length()));
      if (tokenizer.hasMoreElements()) {
        if (spaceBeforeComma) {
          buffer.append(" ");
        }
        buffer.append(",");
        if (spaceAfterComma) {
          buffer.append(" ");
        }
      }
    }
    return memberText.substring(0, parenIndex + 1) + buffer.toString() + ")";
  }

  public static PsiClassType[] getImplementsList(PsiClass aClass) {
    if (aClass instanceof PsiAnonymousClass) {
      return new PsiClassType[]{((PsiAnonymousClass) aClass).getBaseClassType()};
    }

    PsiReferenceList list = aClass.getImplementsList();

    return list == null ? PsiClassType.EMPTY_ARRAY : list.getReferencedTypes();
  }

  public static PsiClassType[] getExtendsList(PsiClass aClass) {
    if (aClass instanceof PsiAnonymousClass) {
      return new PsiClassType[]{((PsiAnonymousClass) aClass).getBaseClassType()};
    }

    PsiReferenceList list = aClass.getExtendsList();

    return list == null ? PsiClassType.EMPTY_ARRAY : list.getReferencedTypes();
  }

  public static boolean isInsidePackageInfo(@Nullable PsiDocComment containingComment) {
    return containingComment != null && containingComment.getOwner() == null && containingComment.getParent()
        instanceof PsiJavaFile;
  }
}
