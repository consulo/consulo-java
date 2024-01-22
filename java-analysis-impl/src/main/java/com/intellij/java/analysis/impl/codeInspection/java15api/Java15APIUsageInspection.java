// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.java15api;

import com.intellij.java.analysis.codeInsight.intention.QuickFixFactory;
import com.intellij.java.analysis.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.java.analysis.codeInspection.GroupNames;
import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.module.EffectiveLanguageLevelUtil;
import com.intellij.java.language.projectRoots.JavaSdkVersion;
import com.intellij.java.language.projectRoots.JavaVersionService;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.InspectionsBundle;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.*;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.ModuleUtilCore;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.ui.ex.awt.*;
import consulo.util.io.FileUtil;
import consulo.util.lang.ObjectUtil;
import consulo.util.lang.lazy.LazyValue;
import consulo.util.lang.ref.SoftReference;
import consulo.util.xml.serializer.InvalidDataException;
import consulo.util.xml.serializer.WriteExternalException;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jdom.Element;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ref.Reference;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.*;
import java.util.function.Supplier;

/**
 * @author max
 */
@ExtensionImpl
public class Java15APIUsageInspection extends AbstractBaseJavaLocalInspectionTool {
  public static final String SHORT_NAME = "Since15";

  private static final String EFFECTIVE_LL = "effectiveLL";

  private static final Map<LanguageLevel, Reference<Set<String>>> ourForbiddenAPI = new EnumMap<>(LanguageLevel.class);
  private static final Supplier<Set<String>> ourIgnored16ClassesAPI = LazyValue.notNull(() -> loadForbiddenApi("ignore16List.txt"));
  private static final Map<LanguageLevel, String> ourPresentableShortMessage = new EnumMap<>(LanguageLevel.class);

  private static final LanguageLevel ourHighestKnownLanguage = LanguageLevel.JDK_19;

  static {
    ourPresentableShortMessage.put(LanguageLevel.JDK_1_3, "1.4");
    ourPresentableShortMessage.put(LanguageLevel.JDK_1_4, "1.5");
    ourPresentableShortMessage.put(LanguageLevel.JDK_1_5, "1.6");
    ourPresentableShortMessage.put(LanguageLevel.JDK_1_6, "1.7");
    ourPresentableShortMessage.put(LanguageLevel.JDK_1_7, "1.8");
    ourPresentableShortMessage.put(LanguageLevel.JDK_1_8, "9");
    ourPresentableShortMessage.put(LanguageLevel.JDK_1_9, "10");
    ourPresentableShortMessage.put(LanguageLevel.JDK_10, "11");
    ourPresentableShortMessage.put(LanguageLevel.JDK_11, "12");
    ourPresentableShortMessage.put(LanguageLevel.JDK_12, "13");
    ourPresentableShortMessage.put(LanguageLevel.JDK_13, "14");
    ourPresentableShortMessage.put(LanguageLevel.JDK_14, "15");
    ourPresentableShortMessage.put(LanguageLevel.JDK_15, "16");
    ourPresentableShortMessage.put(LanguageLevel.JDK_16, "17");
    ourPresentableShortMessage.put(LanguageLevel.JDK_17, "18");
    ourPresentableShortMessage.put(LanguageLevel.JDK_18, "19");

  }

  private static final Set<String> ourGenerifiedClasses = new HashSet<>();

  static {
    ourGenerifiedClasses.add("javax.swing.JComboBox");
    ourGenerifiedClasses.add("javax.swing.ListModel");
    ourGenerifiedClasses.add("javax.swing.JList");
  }

  private static final Set<String> ourDefaultMethods = new HashSet<>();

  static {
    ourDefaultMethods.add("java.util.Iterator#remove()");
  }

  LanguageLevel myEffectiveLanguageLevel;

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public JComponent createOptionsPanel() {
    JPanel panel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 5, true, false));
    panel.add(new JLabel("Forbid API usages:"));

    JRadioButton projectRb = new JRadioButton("Respecting to project language level settings");
    panel.add(projectRb);
    JRadioButton customRb = new JRadioButton("Higher than:");
    panel.add(customRb);
    ButtonGroup gr = new ButtonGroup();
    gr.add(projectRb);
    gr.add(customRb);

    JComboBox<LanguageLevel> llCombo = new ComboBox<LanguageLevel>(LanguageLevel.values()) {
      @Override
      public void setEnabled(boolean b) {
        if (b == customRb.isSelected()) {
          super.setEnabled(b);
        }
      }
    };
    llCombo.setSelectedItem(myEffectiveLanguageLevel != null ? myEffectiveLanguageLevel : LanguageLevel.JDK_1_3);
    llCombo.setRenderer(SimpleListCellRenderer.create("", LanguageLevel::getFullText));
    llCombo.addActionListener(e -> myEffectiveLanguageLevel = (LanguageLevel) llCombo.getSelectedItem());

    JPanel comboPanel = new JPanel(new BorderLayout());
    comboPanel.setBorder(JBUI.Borders.emptyLeft(20));
    comboPanel.add(llCombo, BorderLayout.WEST);
    panel.add(comboPanel);

    ActionListener actionListener = e -> {
      if (projectRb.isSelected()) {
        myEffectiveLanguageLevel = null;
      } else {
        myEffectiveLanguageLevel = (LanguageLevel) llCombo.getSelectedItem();
      }
      UIUtil.setEnabled(comboPanel, !projectRb.isSelected(), true);
    };
    projectRb.addActionListener(actionListener);
    customRb.addActionListener(actionListener);
    projectRb.setSelected(myEffectiveLanguageLevel == null);
    customRb.setSelected(myEffectiveLanguageLevel != null);
    UIUtil.setEnabled(comboPanel, !projectRb.isSelected(), true);
    return panel;
  }

  @Nullable
  private static Set<String> getForbiddenApi(@jakarta.annotation.Nonnull LanguageLevel languageLevel) {
    if (!ourPresentableShortMessage.containsKey(languageLevel)) {
      return null;
    }
    Reference<Set<String>> ref = ourForbiddenAPI.get(languageLevel);
    Set<String> result = SoftReference.dereference(ref);
    if (result == null) {
      result = loadForbiddenApi("api" + getShortName(languageLevel) + ".txt");
      ourForbiddenAPI.put(languageLevel, new SoftReference<>(result));
    }
    return result;
  }

  private static Set<String> loadForbiddenApi(String fileName) {
    URL resource = Java15APIUsageInspection.class.getResource(fileName);
    if (resource == null) {
      Logger.getInstance(Java15APIUsageInspection.class).warn("not found: " + fileName);
      return Collections.emptySet();
    }

    try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.openStream(), StandardCharsets.UTF_8))) {
      return new HashSet<>(FileUtil.loadLines(reader));
    } catch (IOException ex) {
      Logger.getInstance(Java15APIUsageInspection.class).warn("cannot load: " + fileName, ex);
      return Collections.emptySet();
    }
  }

  @Override
  @jakarta.annotation.Nonnull
  public String getGroupDisplayName() {
    return GroupNames.LANGUAGE_LEVEL_SPECIFIC_GROUP_NAME;
  }

  @Override
  @jakarta.annotation.Nonnull
  public String getDisplayName() {
    return InspectionsBundle.message("inspection.1.5.display.name");
  }

  @Override
  @jakarta.annotation.Nonnull
  public String getShortName() {
    return SHORT_NAME;
  }


  @jakarta.annotation.Nonnull
  @Override
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.ERROR;
  }

  @Override
  public void readSettings(@jakarta.annotation.Nonnull Element node) throws InvalidDataException {
    final Element element = node.getChild(EFFECTIVE_LL);
    if (element != null) {
      myEffectiveLanguageLevel = LanguageLevel.valueOf(element.getAttributeValue("value"));
    }
  }

  @Override
  public void writeSettings(@jakarta.annotation.Nonnull Element node) throws WriteExternalException {
    if (myEffectiveLanguageLevel != null) {
      final Element llElement = new Element(EFFECTIVE_LL);
      llElement.setAttribute("value", myEffectiveLanguageLevel.toString());
      node.addContent(llElement);
    }
  }

  @Override
  @Nonnull
  public PsiElementVisitor buildVisitorImpl(@jakarta.annotation.Nonnull ProblemsHolder holder,
                                            boolean isOnTheFly,
                                            LocalInspectionToolSession session,
                                            Object state) {
    return new MyVisitor(holder, isOnTheFly);
  }

  private static boolean isInProject(final PsiElement elt) {
    return elt.getManager().isInProject(elt);
  }

  public static String getShortName(LanguageLevel languageLevel) {
    return ourPresentableShortMessage.get(languageLevel);
  }

  private class MyVisitor extends JavaElementVisitor {
    private final ProblemsHolder myHolder;
    private final boolean myOnTheFly;

    MyVisitor(final ProblemsHolder holder, boolean onTheFly) {
      myHolder = holder;
      myOnTheFly = onTheFly;
    }

    @Override
    public void visitDocComment(PsiDocComment comment) {
      // No references inside doc comment are of interest.
    }

    @Override
    public void visitClass(PsiClass aClass) {
      // Don't go into classes (anonymous, locals).
      if (!aClass.hasModifierProperty(PsiModifier.ABSTRACT) && !(aClass instanceof PsiTypeParameter)) {
        final Module module = ModuleUtilCore.findModuleForPsiElement(aClass);
        final LanguageLevel effectiveLanguageLevel = module != null ? getEffectiveLanguageLevel(module) : null;
        if (effectiveLanguageLevel != null && !effectiveLanguageLevel.isAtLeast(LanguageLevel.JDK_1_8)) {
          final JavaSdkVersion version = JavaVersionService.getInstance().getJavaSdkVersion(aClass);
          if (version != null && version.isAtLeast(JavaSdkVersion.JDK_1_8)) {
            final List<PsiMethod> methods = new ArrayList<>();
            for (HierarchicalMethodSignature methodSignature : aClass.getVisibleSignatures()) {
              final PsiMethod method = methodSignature.getMethod();
              if (ourDefaultMethods.contains(getSignature(method))) {
                methods.add(method);
              }
            }

            if (!methods.isEmpty()) {
              PsiElement element2Highlight = aClass.getNameIdentifier();
              if (element2Highlight == null) {
                element2Highlight = aClass instanceof PsiAnonymousClass ? ((PsiAnonymousClass) aClass).getBaseClassReference() : aClass;
              }
              myHolder.registerProblem(element2Highlight,
                  methods.size() == 1 ? InspectionsBundle.message("inspection.1.8.problem.single.descriptor", methods.get(0).getName(), getJdkName(effectiveLanguageLevel))
                      : InspectionsBundle.message("inspection.1.8.problem.descriptor", methods.size(), getJdkName(effectiveLanguageLevel)),
                  QuickFixFactory.getInstance().createImplementMethodsFix(aClass));
            }
          }
        }
      }
    }

    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
      visitReferenceElement(expression);
    }

    @Override
    public void visitNameValuePair(PsiNameValuePair pair) {
      super.visitNameValuePair(pair);
      PsiReference reference = pair.getReference();
      if (reference != null) {
        PsiElement resolve = reference.resolve();
        if (resolve instanceof PsiCompiledElement && resolve instanceof PsiAnnotationMethod) {
          final Module module = ModuleUtilCore.findModuleForPsiElement(pair);
          if (module != null) {
            final LanguageLevel languageLevel = getEffectiveLanguageLevel(module);
            LanguageLevel sinceLanguageLevel = getLastIncompatibleLanguageLevel((PsiMember) resolve, languageLevel);
            if (sinceLanguageLevel != null) {
              registerError(ObjectUtil.notNull(pair.getNameIdentifier(), pair), sinceLanguageLevel);
            }
          }
        }
      }
    }

    @Override
    public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
      super.visitReferenceElement(reference);
      final PsiElement resolved = reference.resolve();

      if (resolved instanceof PsiCompiledElement && resolved instanceof PsiMember) {
        final Module module = ModuleUtilCore.findModuleForPsiElement(reference.getElement());
        if (module != null) {
          final LanguageLevel languageLevel = getEffectiveLanguageLevel(module);
          LanguageLevel sinceLanguageLevel = getLastIncompatibleLanguageLevel((PsiMember) resolved, languageLevel);
          if (sinceLanguageLevel != null) {
            PsiClass psiClass = null;
            final PsiElement qualifier = reference.getQualifier();
            if (qualifier != null) {
              if (qualifier instanceof PsiExpression) {
                psiClass = PsiUtil.resolveClassInType(((PsiExpression) qualifier).getType());
              }
            } else {
              psiClass = PsiTreeUtil.getParentOfType(reference, PsiClass.class);
            }
            if (psiClass != null) {
              if (isIgnored(psiClass)) {
                return;
              }
              for (PsiClass superClass : psiClass.getSupers()) {
                if (isIgnored(superClass)) {
                  return;
                }
              }
            }
            registerError(reference, sinceLanguageLevel);
          } else if (resolved instanceof PsiClass && isInProject(reference) && !languageLevel.isAtLeast(LanguageLevel.JDK_1_7)) {
            final PsiReferenceParameterList parameterList = reference.getParameterList();
            if (parameterList != null && parameterList.getTypeParameterElements().length > 0) {
              for (String generifiedClass : ourGenerifiedClasses) {
                if (InheritanceUtil.isInheritor((PsiClass) resolved, generifiedClass) &&
                    !isRawInheritance(generifiedClass, (PsiClass) resolved, new HashSet<>())) {
                  String message = InspectionsBundle.message("inspection.1.7.problem.descriptor", getJdkName(languageLevel));
                  myHolder.registerProblem(reference, message);
                  break;
                }
              }
            }
          }
        }
      }
    }

    private boolean isRawInheritance(String generifiedClassQName, PsiClass currentClass, Set<? super PsiClass> visited) {
      for (PsiClassType classType : currentClass.getSuperTypes()) {
        if (classType.isRaw()) {
          return true;
        }
        final PsiClassType.ClassResolveResult resolveResult = classType.resolveGenerics();
        final PsiClass superClass = resolveResult.getElement();
        if (visited.add(superClass) && InheritanceUtil.isInheritor(superClass, generifiedClassQName)) {
          if (isRawInheritance(generifiedClassQName, superClass, visited)) {
            return true;
          }
        }
      }
      return false;
    }

    private boolean isIgnored(PsiClass psiClass) {
      final String qualifiedName = psiClass.getQualifiedName();
      return qualifiedName != null && ourIgnored16ClassesAPI.get().contains(qualifiedName);
    }

    @Override
    public void visitNewExpression(final PsiNewExpression expression) {
      super.visitNewExpression(expression);
      final PsiMethod constructor = expression.resolveConstructor();
      final Module module = ModuleUtilCore.findModuleForPsiElement(expression);
      if (module != null) {
        final LanguageLevel languageLevel = getEffectiveLanguageLevel(module);
        if (constructor instanceof PsiCompiledElement) {
          LanguageLevel sinceLanguageLevel = getLastIncompatibleLanguageLevel(constructor, languageLevel);
          if (sinceLanguageLevel != null) {
            registerError(expression.getClassReference(), sinceLanguageLevel);
          }
        }
      }
    }

    @Override
    public void visitMethod(PsiMethod method) {
      super.visitMethod(method);
      PsiAnnotation annotation = !method.isConstructor() ? AnnotationUtil.findAnnotation(method, CommonClassNames.JAVA_LANG_OVERRIDE) : null;
      if (annotation != null) {
        final Module module = ModuleUtilCore.findModuleForPsiElement(annotation);
        LanguageLevel sinceLanguageLevel = null;
        if (module != null) {
          final LanguageLevel languageLevel = getEffectiveLanguageLevel(module);
          final PsiMethod[] methods = method.findSuperMethods();
          for (PsiMethod superMethod : methods) {
            if (superMethod instanceof PsiCompiledElement) {
              sinceLanguageLevel = getLastIncompatibleLanguageLevel(superMethod, languageLevel);
              if (sinceLanguageLevel == null) {
                return;
              }
            } else {
              return;
            }
          }
          if (methods.length > 0) {
            registerError(annotation.getNameReferenceElement(), sinceLanguageLevel);
          }
        }
      }
    }

    private LanguageLevel getEffectiveLanguageLevel(Module module) {
      if (myEffectiveLanguageLevel != null) {
        return myEffectiveLanguageLevel;
      }
      return EffectiveLanguageLevelUtil.getEffectiveLanguageLevel(module);
    }

    private void registerError(PsiElement reference, LanguageLevel api) {
      if (reference != null && isInProject(reference)) {
        myHolder.registerProblem(reference,
            InspectionsBundle.message("inspection.1.5.problem.descriptor", getShortName(api)),
            myOnTheFly ? new LocalQuickFix[]{(LocalQuickFix) QuickFixFactory.getInstance().createIncreaseLanguageLevelFix(LanguageLevel.values()[api.ordinal() + 1])} : null);
      }
    }
  }

  private static String getJdkName(LanguageLevel languageLevel) {
    final String presentableText = languageLevel.getFullText();
    return presentableText.substring(0, presentableText.indexOf(' '));
  }

  public static LanguageLevel getLastIncompatibleLanguageLevel(@jakarta.annotation.Nonnull PsiMember member, @jakarta.annotation.Nonnull LanguageLevel languageLevel) {
    if (member instanceof PsiAnonymousClass) {
      return null;
    }
    PsiClass containingClass = member.getContainingClass();
    if (containingClass instanceof PsiAnonymousClass) {
      return null;
    }
    if (member instanceof PsiClass && !(member.getParent() instanceof PsiClass || member.getParent() instanceof PsiFile)) {
      return null;
    }

    Set<String> forbiddenApi = getForbiddenApi(languageLevel);
    String signature = getSignature(member);
    if (forbiddenApi != null && signature != null) {
      LanguageLevel lastIncompatibleLanguageLevel = getLastIncompatibleLanguageLevelForSignature(signature, languageLevel, forbiddenApi);
      if (lastIncompatibleLanguageLevel != null) {
        return lastIncompatibleLanguageLevel;
      }
    }
    return containingClass != null ? getLastIncompatibleLanguageLevel(containingClass, languageLevel) : null;

  }

  private static LanguageLevel getLastIncompatibleLanguageLevelForSignature(@jakarta.annotation.Nonnull String signature, @Nonnull LanguageLevel languageLevel, @Nonnull Set<String> forbiddenApi) {
    if (forbiddenApi.contains(signature)) {
      return languageLevel;
    }
    if (languageLevel.compareTo(ourHighestKnownLanguage) == 0) {
      return null;
    }
    LanguageLevel nextLanguageLevel = LanguageLevel.values()[languageLevel.ordinal() + 1];
    Set<String> nextForbiddenApi = getForbiddenApi(nextLanguageLevel);
    return nextForbiddenApi != null ? getLastIncompatibleLanguageLevelForSignature(signature, nextLanguageLevel, nextForbiddenApi) : null;
  }

  /**
   * please leave public for JavaAPIUsagesInspectionTest#testCollectSinceApiUsages
   */
  @jakarta.annotation.Nullable
  public static String getSignature(@jakarta.annotation.Nullable PsiMember member) {
    if (member instanceof PsiClass) {
      return ((PsiClass) member).getQualifiedName();
    }
    if (member instanceof PsiField) {
      String containingClass = getSignature(member.getContainingClass());
      return containingClass == null ? null : containingClass + "#" + member.getName();
    }
    if (member instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod) member;
      String containingClass = getSignature(member.getContainingClass());
      if (containingClass == null) {
        return null;
      }

      StringBuilder buf = new StringBuilder();
      buf.append(containingClass);
      buf.append('#');
      buf.append(method.getName());
      buf.append('(');
      for (PsiType type : method.getSignature(PsiSubstitutor.EMPTY).getParameterTypes()) {
        buf.append(type.getCanonicalText());
        buf.append(";");
      }
      buf.append(')');
      return buf.toString();
    }
    return null;
  }
}
