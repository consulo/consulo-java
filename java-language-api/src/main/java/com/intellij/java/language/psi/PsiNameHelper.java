/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.java.language.psi;

import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.lang.StringUtil;
import consulo.util.collection.ArrayUtil;
import consulo.ide.ServiceManager;
import consulo.project.Project;
import org.jetbrains.annotations.Contract;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static consulo.util.lang.ObjectUtil.notNull;

/**
 * Service for validating and parsing Java identifiers.
 */
@ServiceAPI(ComponentScope.PROJECT)
public abstract class PsiNameHelper {
  @Nonnull
  public static PsiNameHelper getInstance(@Nonnull Project project) {
    return ServiceManager.getService(project, PsiNameHelper.class);
  }

  /**
   * Checks if the specified text is a Java identifier, using the language level of the project
   * with which the name helper is associated to filter out keywords.
   *
   * @param text the text to check.
   * @return true if the text is an identifier, false otherwise
   */
  public abstract boolean isIdentifier(@Nullable String text);

  /**
   * Checks if the specified text is a Java identifier, using the specified language level
   * with which the name helper is associated to filter out keywords.
   *
   * @param text          the text to check.
   * @param languageLevel to check text against. For instance 'assert' or 'enum' might or might not be identifiers
   *                      depending on language level
   * @return true if the text is an identifier, false otherwise
   */
  public abstract boolean isIdentifier(@Nullable String text, LanguageLevel languageLevel);

  /**
   * Checks if the specified text is a Java keyword, using the language level of the project
   * with which the name helper is associated.
   *
   * @param text the text to check.
   * @return true if the text is a keyword, false otherwise
   */
  public abstract boolean isKeyword(@Nullable String text);

  /**
   * Checks if the specified string is a qualified name (sequence of identifiers separated by
   * periods).
   *
   * @param text the text to check.
   * @return true if the text is a qualified name, false otherwise.
   */
  public abstract boolean isQualifiedName(@Nullable String text);

  @Nonnull
  public static String getShortClassName(@Nonnull String referenceText) {
    int lessPos = referenceText.length();
    int bracesBalance = 0;
    int i;

    loop:
    for (i = referenceText.length() - 1; i >= 0; i--) {
      char ch = referenceText.charAt(i);
      switch (ch) {
        case ')':
        case '>':
          bracesBalance++;
          break;

        case '(':
        case '<':
          bracesBalance--;
          lessPos = i;
          break;

        case '@':
        case '.':
          if (bracesBalance <= 0) {
            break loop;
          }
          break;

        default:
          if (Character.isWhitespace(ch) && bracesBalance <= 0) {
            for (int j = i + 1; j < lessPos; j++) {
              if (!Character.isWhitespace(referenceText.charAt(j))) {
                break loop;
              }
            }
            lessPos = i;
          }
      }
    }

    String sub = referenceText.substring(i + 1, lessPos).trim();
    return sub.length() == referenceText.length() ? sub : new String(sub);
  }

  @Nonnull
  public static String getPresentableText(@Nonnull PsiJavaCodeReferenceElement ref) {
    String name = ref.getReferenceName();
    PsiAnnotation[] annotations = PsiTreeUtil.getChildrenOfType(ref, PsiAnnotation.class);
    return getPresentableText(name, notNull(annotations, PsiAnnotation.EMPTY_ARRAY), ref.getTypeParameters());
  }

  @Nonnull
  public static String getPresentableText(@Nullable String refName,
                                          @Nonnull PsiAnnotation[] annotations,
                                          @Nonnull PsiType[] types) {
    if (types.length == 0 && annotations.length == 0) {
      return refName != null ? refName : "";
    }

    StringBuilder buffer = new StringBuilder();
    appendAnnotations(buffer, annotations, false);
    buffer.append(refName);
    appendTypeArgs(buffer, types, false, true);
    return buffer.toString();
  }

  /**
   * @param referenceText text of the inner class reference (without annotations), e.g. {@code A.B<C>.D<E, F.G>}
   * @return outer class reference (e.g. {@code A.B<C>}); empty string if the original reference is unqualified
   */
  @Contract(pure = true)
  @Nonnull
  public static String getOuterClassReference(String referenceText) {
    int stack = 0;
    for (int i = referenceText.length() - 1; i >= 0; i--) {
      char c = referenceText.charAt(i);
      switch (c) {
        case '<':
          stack--;
          break;
        case '>':
          stack++;
          break;
        case '.':
          if (stack == 0) {
            return referenceText.substring(0, i);
          }
      }
    }

    return "";
  }

  @Nonnull
  public static String getQualifiedClassName(@Nonnull String referenceText, boolean removeWhitespace) {
    if (removeWhitespace) {
      referenceText = removeWhitespace(referenceText);
    }
    if (referenceText.indexOf('<') < 0) {
      return referenceText;
    }
    final StringBuilder buffer = new StringBuilder(referenceText.length());
    final char[] chars = referenceText.toCharArray();
    int gtPos = 0;
    int count = 0;
    for (int i = 0; i < chars.length; i++) {
      final char aChar = chars[i];
      switch (aChar) {
        case '<':
          count++;
          if (count == 1) {
            buffer.append(new String(chars, gtPos, i - gtPos));
          }
          break;
        case '>':
          count--;
          gtPos = i + 1;
          break;
      }
    }
    if (count == 0) {
      buffer.append(new String(chars, gtPos, chars.length - gtPos));
    }
    return buffer.toString();
  }

  private static final Pattern WHITESPACE_PATTERN = Pattern.compile("(?:\\s)|(?:/\\*.*\\*/)|(?://[^\\n]*)");

  private static String removeWhitespace(@Nonnull String referenceText) {
    return WHITESPACE_PATTERN.matcher(referenceText).replaceAll("");
  }

  /**
   * Obtains text of all type parameter values in a reference.
   * They go in left-to-right order: <code>A&lt;List&lt;String&gt, B&lt;Integer&gt;&gt;</code> yields
   * <code>["List&lt;String&gt", "B&lt;Integer&gt;"]</code>. Parameters of the outer reference are ignored:
   * <code>A&lt;List&lt;String&gt&gt;.B&lt;Integer&gt;</code> yields <code>["Integer"]</code>
   *
   * @param referenceText the text of the reference to calculate type parameters for.
   * @return the calculated array of type parameters.
   */
  @Nonnull
  public static String[] getClassParametersText(@Nonnull String referenceText) {
    if (referenceText.indexOf('<') < 0) {
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }
    referenceText = removeWhitespace(referenceText);
    final char[] chars = referenceText.toCharArray();
    int afterLastDotIndex = 0;

    int level = 0;
    for (int i = 0; i < chars.length; i++) {
      char aChar = chars[i];
      switch (aChar) {
        case '<':
          level++;
          break;
        case '.':
          if (level == 0) {
            afterLastDotIndex = i + 1;
          }
          break;
        case '>':
          level--;
          break;
      }
    }

    if (level != 0) {
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }

    int dim = 0;
    for (int i = afterLastDotIndex; i < chars.length; i++) {
      char aChar = chars[i];
      switch (aChar) {
        case '<':
          level++;
          if (level == 1) {
            dim++;
          }
          break;
        case ',':
          if (level == 1) {
            dim++;
          }
          break;
        case '>':
          level--;
          break;
      }
    }
    if (level != 0 || dim == 0) {
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }

    final String[] result = new String[dim];
    dim = 0;
    int ltPos = 0;
    for (int i = afterLastDotIndex; i < chars.length; i++) {
      final char aChar = chars[i];
      switch (aChar) {
        case '<':
          level++;
          if (level == 1) {
            ltPos = i;
          }
          break;
        case ',':
          if (level == 1) {
            result[dim++] = new String(chars, ltPos + 1, i - ltPos - 1);
            ltPos = i;
          }
          break;
        case '>':
          level--;
          if (level == 0) {
            result[dim++] = new String(chars, ltPos + 1, i - ltPos - 1);
          }
          break;
      }
    }

    return result;
  }

  public static boolean isSubpackageOf(@Nonnull String subpackageName, @Nonnull String packageName) {
    return subpackageName.equals(packageName) || subpackageName.startsWith(packageName) && subpackageName.charAt
        (packageName.length()) == '.';
  }

  public static void appendTypeArgs(@Nonnull StringBuilder sb,
                                    @Nonnull PsiType[] types,
                                    boolean canonical,
                                    boolean annotated) {
    if (types.length == 0) {
      return;
    }

    sb.append('<');
    for (int i = 0; i < types.length; i++) {
      if (i > 0) {
        sb.append(canonical ? "," : ", ");
      }

      PsiType type = types[i];
      if (canonical) {
        sb.append(type.getCanonicalText(annotated));
      } else {
        sb.append(type.getPresentableText());
      }
    }
    sb.append('>');
  }

  public static boolean appendAnnotations(@Nonnull StringBuilder sb,
                                          @Nonnull PsiAnnotation[] annotations,
                                          boolean canonical) {
    return appendAnnotations(sb, Arrays.asList(annotations), canonical);
  }

  public static boolean appendAnnotations(@Nonnull StringBuilder sb,
                                          @Nonnull List<PsiAnnotation> annotations,
                                          boolean canonical) {
    boolean updated = false;
    for (PsiAnnotation annotation : annotations) {
      if (canonical) {
        String name = annotation.getQualifiedName();
        if (name != null) {
          sb.append('@').append(name).append(annotation.getParameterList().getText()).append(' ');
          updated = true;
        }
      } else {
        PsiJavaCodeReferenceElement refElement = annotation.getNameReferenceElement();
        if (refElement != null) {
          sb.append('@').append(refElement.getText()).append(' ');
          updated = true;
        }
      }
    }
    return updated;
  }

  public static boolean isValidModuleName(@Nonnull String name, @Nonnull PsiElement context) {
    PsiNameHelper helper = getInstance(context.getProject());
    LanguageLevel level = PsiUtil.getLanguageLevel(context);
    return StringUtil.split(name, ".", true, false).stream().allMatch(part -> helper.isIdentifier(part, level));
  }
}
