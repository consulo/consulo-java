// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.impl.codeInsight.daemon.impl;

import com.intellij.java.language.psi.*;
import consulo.language.editor.QualifiedNameProviderUtil;
import consulo.language.editor.ui.awt.HintUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiNamedElement;
import consulo.ui.ex.JBColor;
import consulo.ui.ex.action.ActionManager;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.keymap.util.KeymapUtil;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;

import static consulo.language.psi.PsiUtilCore.getVirtualFile;
import static consulo.language.psi.util.PsiTreeUtil.getStubOrPsiParentOfType;
import static consulo.ui.ex.awt.util.ColorUtil.toHex;
import static consulo.util.io.FileUtil.toSystemIndependentName;

public final class GutterTooltipHelper {
  private static final JBColor SEPARATOR_COLOR = JBColor.namedColor("GutterTooltip.lineSeparatorColor", HintUtil.INFORMATION_BORDER_COLOR);
  private static final JBColor CONTEXT_HELP_FOREGROUND
      = JBColor.namedColor("GutterTooltip.infoForeground", new JBColor(0x787878, 0x878787));

  private GutterTooltipHelper() {
  }

  /**
   * @param elements        a collection of elements to create a formatted tooltip text
   * @param prefix          a text to insert before all elements
   * @param skipFirstMember {@code true} to skip a method (or field) name in the link to element
   * @param actionId        an action identifier to generate context help or {@code null} if not applicable
   */
  @Nonnull
  public static <E extends PsiElement> String getTooltipText(@Nonnull Collection<E> elements,
                                                             @Nonnull String prefix,
                                                             boolean skipFirstMember,
                                                             @Nullable String actionId) {
    String firstDivider = getElementDivider(true, true, elements.size());
    String nextDivider = getElementDivider(false, true, elements.size());
    AtomicReference<String> reference = new AtomicReference<>(firstDivider); // optimization: calculate next divider only once
    return getTooltipText(prefix, elements, e -> reference.getAndSet(nextDivider), e -> skipFirstMember, actionId);
  }

  static String getElementDivider(boolean firstElement, boolean marginLeft, int elementsCount) {
    if (elementsCount <= 1) {
      return " ";
    }
    StringBuilder sb = new StringBuilder("</p><p style='margin-top:2pt");
    if (marginLeft) {
      sb.append(";margin-left:20pt");
    }
    if (!firstElement) {
      sb.append(";border-top:thin solid #").append(toHex(SEPARATOR_COLOR));
    }
    return sb.append(";'>").toString();
  }

  /**
   * @param elements                 a collection of elements to create a formatted tooltip text
   * @param elementToPrefix          a function that returns a text to insert before the current element
   * @param skipFirstMemberOfElement a function that returns {@code true} to skip a method (or field) name for the current element
   * @param actionId                 an action identifier to generate context help or {@code null} if not applicable
   */
  @Nonnull
  public static <E extends PsiElement> String getTooltipText(@Nonnull Collection<? extends E> elements,
                                                             @Nonnull Function<? super E, String> elementToPrefix,
                                                             @Nonnull Predicate<? super E> skipFirstMemberOfElement,
                                                             @Nullable String actionId) {
    return getTooltipText(null, elements, elementToPrefix, skipFirstMemberOfElement, actionId);
  }

  @Nonnull
  private static <E extends PsiElement> String getTooltipText(@Nullable String prefix,
                                                              @Nonnull Collection<? extends E> elements,
                                                              @Nonnull Function<? super E, String> elementToPrefix,
                                                              @Nonnull Predicate<? super E> skipFirstMemberOfElement,
                                                              @Nullable String actionId) {
    StringBuilder sb = new StringBuilder("<html><body><p>");
    if (prefix != null) {
      sb.append(prefix);
    }
    for (E element : elements) {
      String elementPrefix = elementToPrefix.apply(element);
      if (elementPrefix != null) {
        sb.append(elementPrefix);
      }
      appendElement(sb, element, skipFirstMemberOfElement.test(element));
    }
    appendContextHelp(sb, actionId);
    sb.append("</p></body></html>");
    return sb.toString();
  }

  private static void appendElement(@Nonnull StringBuilder sb, @Nonnull PsiElement element, boolean skip) {
    boolean useSingleLink = true;
    String packageName = null;
    boolean addedSingleLink = useSingleLink && appendLink(sb, element);
    PsiElement original = element; // use original member as a first separate link
    if (skip && (element instanceof PsiMethod || element instanceof PsiField)) {
      element = getContainingElement(element);
    }
    while (element != null) {
      String name = getPresentableName(element);
      if (name != null) {
        boolean addedLink = !useSingleLink && appendLink(sb, original != null ? original : element);
        original = null; // do not use a link to the original element if it is already added
        // Swing uses simple HTML processing and paints a link incorrectly if it contains different fonts.
        // This is the reason why I use monospaced font not only for element name, but for a whole link.
        // By the same reason I have to comment out support for deprecated elements.
        //
        // boolean deprecated = RefJavaUtil.isDeprecated(element);
        // if (deprecated) sb.append("<strike>");
        // sb.append("<code>");
        sb.append(name);
        // sb.append("</code>");
        // if (deprecated) sb.append("</strike>");
        if (addedLink) {
          sb.append("</code></a>");
        }
      }
      if (element instanceof PsiFile) {
        break;
      }
      PsiElement parent = getContainingElement(element);
      if (parent == null || parent instanceof PsiFile) {
        if (element instanceof PsiClass && !(element instanceof PsiAnonymousClass)) {
          String qualifiedName = ((PsiClass) element).getQualifiedName();
          if (qualifiedName != null) {
            packageName = StringUtil.getPackageName(qualifiedName);
          }
          break;
        }
      }
      if (name != null && parent != null) {
        sb.append(" in ");
      }
      element = parent;
    }
    if (addedSingleLink) {
      sb.append("</code></a>");
    }
    appendPackageName(sb, packageName);
  }

  private static void appendPackageName(@Nonnull StringBuilder sb, @Nullable String name) {
    if (StringUtil.isEmpty(name)) {
      return; // no package name
    }
    sb.append(" <font color='#").append(toHex(CONTEXT_HELP_FOREGROUND));
    sb.append("'><code>(").append(name).append(")</code></font>");
  }

  private static void appendContextHelp(@Nonnull StringBuilder sb, @Nullable String actionId) {
    if (actionId == null) {
      return; // action id is not set
    }
    AnAction action = ActionManager.getInstance().getAction(actionId);
    if (action == null) {
      return; // action is not exist
    }
    String text = KeymapUtil.getPreferredShortcutText(action.getShortcutSet().getShortcuts());
    if (StringUtil.isEmpty(text)) {
      return; // action have no shortcuts
    }
    sb.append("</p><p style='margin-top:8px;'><font size='2' color='#");
    sb.append(toHex(CONTEXT_HELP_FOREGROUND));
    sb.append("'>Press ").append(text).append(" to navigate</font>");
  }

  private static boolean appendLink(@Nonnull StringBuilder sb, @Nonnull PsiElement element) {
    try {
      String name = getQualifiedName(element);
      if (!StringUtil.isEmpty(name)) {
        sb.append("<a href=\"#element/").append(name).append("\"><code>");
        return true;
      }
      VirtualFile file = getVirtualFile(element);
      if (file == null) {
        return false;
      }

      int offset = element.getTextOffset();
      sb.append("<a href=\"#navigation/");
      sb.append(toSystemIndependentName(file.getPath()));
      sb.append(":").append(offset).append("\"><code>");
      return true;
    } catch (Exception ignored) {
      return false;
    }
  }

  @Nullable
  private static String getQualifiedName(@Nonnull PsiElement element) {
    PsiClass psiClass = element instanceof PsiClass ? (PsiClass) element : getStubOrPsiParentOfType(element, PsiClass.class);
    if (psiClass instanceof PsiAnonymousClass) {
      return null;
    }
    return QualifiedNameProviderUtil.getQualifiedName(element);
  }

  @Nullable
  private static PsiElement getContainingElement(@Nonnull PsiElement element) {
    PsiMember member = getStubOrPsiParentOfType(element, PsiMember.class);
    if (member == null && element instanceof PsiMember) {
      member = ((PsiMember) element).getContainingClass();
    }
    return member != null ? member : element.getContainingFile();
  }

  @Nullable
  private static String getPresentableName(@Nonnull PsiElement element) {
    if (element instanceof PsiEnumConstantInitializer) {
      PsiEnumConstantInitializer initializer = (PsiEnumConstantInitializer) element;
      return initializer.getEnumConstant().getName();
    }
    if (element instanceof PsiAnonymousClass) {
      return "Anonymous";
    }
    if (element instanceof PsiNamedElement) {
      PsiNamedElement named = (PsiNamedElement) element;
      return named.getName();
    }
    return null;
  }
}
