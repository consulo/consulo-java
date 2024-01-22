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
package com.intellij.java.impl.psi.codeStyle.arrangement;

import com.intellij.java.analysis.impl.ide.highlighter.JavaHighlightingColors;
import com.intellij.java.language.JavaLanguage;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.EditorColors;
import consulo.colorScheme.EditorColorsScheme;
import consulo.colorScheme.TextAttributes;
import consulo.colorScheme.TextAttributesKey;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.ide.impl.idea.util.containers.ContainerUtilRt;
import consulo.language.Language;
import consulo.language.codeStyle.CodeStyleSettings;
import consulo.language.codeStyle.CommonCodeStyleSettings;
import consulo.language.codeStyle.arrangement.*;
import consulo.language.codeStyle.arrangement.group.ArrangementGroupingRule;
import consulo.language.codeStyle.arrangement.match.ArrangementEntryMatcher;
import consulo.language.codeStyle.arrangement.match.StdArrangementEntryMatcher;
import consulo.language.codeStyle.arrangement.match.StdArrangementMatchRule;
import consulo.language.codeStyle.arrangement.model.ArrangementAtomMatchCondition;
import consulo.language.codeStyle.arrangement.model.ArrangementCompositeMatchCondition;
import consulo.language.codeStyle.arrangement.model.ArrangementMatchCondition;
import consulo.language.codeStyle.arrangement.std.*;
import consulo.language.psi.PsiElement;
import consulo.ui.color.ColorValue;
import consulo.util.lang.Pair;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.awt.*;
import java.util.List;
import java.util.*;

import static consulo.language.codeStyle.arrangement.std.StdArrangementTokens.EntryType.*;
import static consulo.language.codeStyle.arrangement.std.StdArrangementTokens.General.*;
import static consulo.language.codeStyle.arrangement.std.StdArrangementTokens.Grouping.*;
import static consulo.language.codeStyle.arrangement.std.StdArrangementTokens.Modifier.*;
import static consulo.language.codeStyle.arrangement.std.StdArrangementTokens.Order.*;

/**
 * @author Denis Zhdanov
 * @since 7/20/12 2:31 PM
 */
@ExtensionImpl
public class JavaRearranger implements Rearranger<JavaElementArrangementEntry>, ArrangementSectionRuleAwareSettings,
    ArrangementStandardSettingsAware, ArrangementColorsAware {

  // Type
  @jakarta.annotation.Nonnull
  private static final Set<ArrangementSettingsToken> SUPPORTED_TYPES = ContainerUtilRt.newLinkedHashSet(FIELD, CONSTRUCTOR, METHOD, CLASS,
      INTERFACE, ENUM);
  // Modifier
  @Nonnull
  private static final Set<ArrangementSettingsToken> SUPPORTED_MODIFIERS = ContainerUtilRt.newLinkedHashSet(PUBLIC, PROTECTED, PACKAGE_PRIVATE,
      PRIVATE, STATIC, FINAL, ABSTRACT, SYNCHRONIZED, TRANSIENT, VOLATILE);
  @jakarta.annotation.Nonnull
  private static final List<ArrangementSettingsToken> SUPPORTED_ORDERS = ContainerUtilRt.newArrayList(KEEP, BY_NAME);
  @Nonnull
  private static final ArrangementSettingsToken NO_TYPE = new ArrangementSettingsToken("NO_TYPE", "NO_TYPE");
  @Nonnull
  private static final Map<ArrangementSettingsToken, Set<ArrangementSettingsToken>> MODIFIERS_BY_TYPE = ContainerUtilRt.newHashMap();
  @Nonnull
  private static final Collection<Set<ArrangementSettingsToken>> MUTEXES = ContainerUtilRt.newArrayList();

  static {
    Set<ArrangementSettingsToken> visibilityModifiers = ContainerUtilRt.newHashSet(PUBLIC, PROTECTED, PACKAGE_PRIVATE, PRIVATE);
    MUTEXES.add(visibilityModifiers);
    MUTEXES.add(SUPPORTED_TYPES);

    Set<ArrangementSettingsToken> commonModifiers = concat(visibilityModifiers, STATIC, FINAL);

    MODIFIERS_BY_TYPE.put(NO_TYPE, commonModifiers);
    MODIFIERS_BY_TYPE.put(ENUM, visibilityModifiers);
    MODIFIERS_BY_TYPE.put(INTERFACE, visibilityModifiers);
    MODIFIERS_BY_TYPE.put(CLASS, concat(commonModifiers, ABSTRACT));
    MODIFIERS_BY_TYPE.put(METHOD, concat(commonModifiers, SYNCHRONIZED, ABSTRACT));
    MODIFIERS_BY_TYPE.put(CONSTRUCTOR, concat(commonModifiers, SYNCHRONIZED));
    MODIFIERS_BY_TYPE.put(FIELD, concat(commonModifiers, TRANSIENT, VOLATILE));
  }

  private static final Map<ArrangementSettingsToken, List<ArrangementSettingsToken>> GROUPING_RULES = ContainerUtilRt.newLinkedHashMap();

  static {
    GROUPING_RULES.put(GETTERS_AND_SETTERS, Collections.<ArrangementSettingsToken>emptyList());
    GROUPING_RULES.put(OVERRIDDEN_METHODS, ContainerUtilRt.newArrayList(BY_NAME, KEEP));
    GROUPING_RULES.put(DEPENDENT_METHODS, ContainerUtilRt.newArrayList(BREADTH_FIRST, DEPTH_FIRST));
  }

  private static final StdArrangementSettings DEFAULT_SETTINGS;

  static {
    List<ArrangementGroupingRule> groupingRules = ContainerUtilRt.newArrayList(new ArrangementGroupingRule(GETTERS_AND_SETTERS));
    List<StdArrangementMatchRule> matchRules = ContainerUtilRt.newArrayList();
    ArrangementSettingsToken[] visibility = {
        PUBLIC,
        PROTECTED,
        PACKAGE_PRIVATE,
        PRIVATE
    };
    for (ArrangementSettingsToken modifier : visibility) {
      and(matchRules, FIELD, STATIC, FINAL, modifier);
    }
    for (ArrangementSettingsToken modifier : visibility) {
      and(matchRules, FIELD, STATIC, modifier);
    }
    for (ArrangementSettingsToken modifier : visibility) {
      and(matchRules, FIELD, FINAL, modifier);
    }
    for (ArrangementSettingsToken modifier : visibility) {
      and(matchRules, FIELD, modifier);
    }
    and(matchRules, FIELD);
    and(matchRules, CONSTRUCTOR);
    and(matchRules, METHOD, STATIC);
    and(matchRules, METHOD);
    and(matchRules, ENUM);
    and(matchRules, INTERFACE);
    and(matchRules, CLASS, STATIC);
    and(matchRules, CLASS);

    DEFAULT_SETTINGS = StdArrangementSettings.createByMatchRules(groupingRules, matchRules);
  }

  private static final DefaultArrangementSettingsSerializer SETTINGS_SERIALIZER = new DefaultArrangementSettingsSerializer(DEFAULT_SETTINGS);

  @Nonnull
  private static Set<ArrangementSettingsToken> concat(@jakarta.annotation.Nonnull Set<ArrangementSettingsToken> base, ArrangementSettingsToken... modifiers) {
    Set<ArrangementSettingsToken> result = ContainerUtilRt.newHashSet(base);
    Collections.addAll(result, modifiers);
    return result;
  }

  private static void setupGettersAndSetters(@jakarta.annotation.Nonnull JavaArrangementParseInfo info) {
    Collection<JavaArrangementPropertyInfo> properties = info.getProperties();
    for (JavaArrangementPropertyInfo propertyInfo : properties) {
      JavaElementArrangementEntry getter = propertyInfo.getGetter();
      JavaElementArrangementEntry setter = propertyInfo.getSetter();
      if (getter != null && setter != null && setter.getDependencies() == null) {
        setter.addDependency(getter);
      }
    }
  }

  private static void setupUtilityMethods(@jakarta.annotation.Nonnull JavaArrangementParseInfo info, @jakarta.annotation.Nonnull ArrangementSettingsToken orderType) {
    if (DEPTH_FIRST.equals(orderType)) {
      for (ArrangementEntryDependencyInfo rootInfo : info.getMethodDependencyRoots()) {
        setupDepthFirstDependency(rootInfo);
      }
    } else if (BREADTH_FIRST.equals(orderType)) {
      for (ArrangementEntryDependencyInfo rootInfo : info.getMethodDependencyRoots()) {
        setupBreadthFirstDependency(rootInfo);
      }
    } else {
      assert false : orderType;
    }
  }

  private static void setupDepthFirstDependency(@jakarta.annotation.Nonnull ArrangementEntryDependencyInfo info) {
    for (ArrangementEntryDependencyInfo dependencyInfo : info.getDependentEntriesInfos()) {
      setupDepthFirstDependency(dependencyInfo);
      JavaElementArrangementEntry dependentEntry = dependencyInfo.getAnchorEntry();
      if (dependentEntry.getDependencies() == null) {
        dependentEntry.addDependency(info.getAnchorEntry());
      }
    }
  }

  private static void setupBreadthFirstDependency(@Nonnull ArrangementEntryDependencyInfo info) {
    Deque<ArrangementEntryDependencyInfo> toProcess = new ArrayDeque<ArrangementEntryDependencyInfo>();
    toProcess.add(info);
    JavaElementArrangementEntry prev = info.getAnchorEntry();
    while (!toProcess.isEmpty()) {
      ArrangementEntryDependencyInfo current = toProcess.removeFirst();
      for (ArrangementEntryDependencyInfo dependencyInfo : current.getDependentEntriesInfos()) {
        JavaElementArrangementEntry dependencyMethod = dependencyInfo.getAnchorEntry();
        if (dependencyMethod.getDependencies() == null) {
          dependencyMethod.addDependency(prev);
          prev = dependencyMethod;
        }
        toProcess.addLast(dependencyInfo);
      }
    }
  }

  private static void setupOverriddenMethods(JavaArrangementParseInfo info) {
    for (JavaArrangementOverriddenMethodsInfo methodsInfo : info.getOverriddenMethods()) {
      JavaElementArrangementEntry previous = null;
      for (JavaElementArrangementEntry entry : methodsInfo.getMethodEntries()) {
        if (previous != null && entry.getDependencies() == null) {
          entry.addDependency(previous);
        }
        previous = entry;
      }
    }
  }

  @Nullable
  @Override
  public Pair<JavaElementArrangementEntry, List<JavaElementArrangementEntry>> parseWithNew(
      @Nonnull PsiElement root,
      @jakarta.annotation.Nullable Document document,
      @jakarta.annotation.Nonnull Collection<TextRange> ranges,
      @Nonnull PsiElement element,
      @jakarta.annotation.Nonnull ArrangementSettings settings) {
    JavaArrangementParseInfo existingEntriesInfo = new JavaArrangementParseInfo();
    root.accept(new JavaArrangementVisitor(existingEntriesInfo, document, ranges, settings));

    JavaArrangementParseInfo newEntryInfo = new JavaArrangementParseInfo();
    element.accept(new JavaArrangementVisitor(newEntryInfo, document, Collections.singleton(element.getTextRange()), settings));
    if (newEntryInfo.getEntries().size() != 1) {
      return null;
    }
    return Pair.create(newEntryInfo.getEntries().get(0), existingEntriesInfo.getEntries());
  }

  @jakarta.annotation.Nonnull
  @Override
  public List<JavaElementArrangementEntry> parse(
      @jakarta.annotation.Nonnull PsiElement root,
      @Nullable Document document,
      @Nonnull Collection<TextRange> ranges,
      @jakarta.annotation.Nonnull ArrangementSettings settings) {
    // Following entries are subject to arrangement: class, interface, field, method.
    JavaArrangementParseInfo parseInfo = new JavaArrangementParseInfo();
    root.accept(new JavaArrangementVisitor(parseInfo, document, ranges, settings));
    for (ArrangementGroupingRule rule : settings.getGroupings()) {
      if (GETTERS_AND_SETTERS.equals(rule.getGroupingType())) {
        setupGettersAndSetters(parseInfo);
      } else if (DEPENDENT_METHODS.equals(rule.getGroupingType())) {
        setupUtilityMethods(parseInfo, rule.getOrderType());
      } else if (OVERRIDDEN_METHODS.equals(rule.getGroupingType())) {
        setupOverriddenMethods(parseInfo);
      }
    }
    setupFieldInitializationDependencies(parseInfo.getFieldDependencyRoots());
    return parseInfo.getEntries();
  }


  public void setupFieldInitializationDependencies(@jakarta.annotation.Nonnull List<ArrangementEntryDependencyInfo> list) {
    for (ArrangementEntryDependencyInfo info : list) {
      JavaElementArrangementEntry anchorField = info.getAnchorEntry();
      for (ArrangementEntryDependencyInfo fieldUsedInInitialization : info.getDependentEntriesInfos()) {
        anchorField.addDependency(fieldUsedInInitialization.getAnchorEntry());
      }
    }
  }


  @Override
  public int getBlankLines(
      @jakarta.annotation.Nonnull CodeStyleSettings settings,
      @Nullable JavaElementArrangementEntry parent,
      @Nullable JavaElementArrangementEntry previous,
      @Nonnull JavaElementArrangementEntry target) {
    if (previous == null) {
      return -1;
    }

    CommonCodeStyleSettings commonSettings = settings.getCommonSettings(JavaLanguage.INSTANCE);
    if (FIELD.equals(target.getType())) {
      if (parent != null && parent.getType() == INTERFACE) {
        return commonSettings.BLANK_LINES_AROUND_FIELD_IN_INTERFACE;
      } else {
        return commonSettings.BLANK_LINES_AROUND_FIELD;
      }
    } else if (METHOD.equals(target.getType())) {
      if (parent != null && parent.getType() == INTERFACE) {
        return commonSettings.BLANK_LINES_AROUND_METHOD_IN_INTERFACE;
      } else {
        return commonSettings.BLANK_LINES_AROUND_METHOD;
      }
    } else if (CLASS.equals(target.getType())) {
      return commonSettings.BLANK_LINES_AROUND_CLASS;
    } else {
      return -1;
    }
  }

  @jakarta.annotation.Nonnull
  @Override
  public ArrangementSettingsSerializer getSerializer() {
    return SETTINGS_SERIALIZER;
  }

  @jakarta.annotation.Nonnull
  @Override
  public StdArrangementSettings getDefaultSettings() {
    return DEFAULT_SETTINGS;
  }

  @Nullable
  @Override
  public List<CompositeArrangementSettingsToken> getSupportedGroupingTokens() {
    return ContainerUtilRt.newArrayList(new CompositeArrangementSettingsToken(GETTERS_AND_SETTERS),
        new CompositeArrangementSettingsToken(OVERRIDDEN_METHODS, BY_NAME, KEEP), new CompositeArrangementSettingsToken(DEPENDENT_METHODS,
            BREADTH_FIRST, DEPTH_FIRST));
  }

  @Nullable
  @Override
  public List<CompositeArrangementSettingsToken> getSupportedMatchingTokens() {
    return ContainerUtilRt.newArrayList(new CompositeArrangementSettingsToken(TYPE, SUPPORTED_TYPES),
        new CompositeArrangementSettingsToken(MODIFIER, SUPPORTED_MODIFIERS), new CompositeArrangementSettingsToken(StdArrangementTokens
            .Regexp.NAME), new CompositeArrangementSettingsToken(ORDER, KEEP, BY_NAME));
  }

  @Override
  public boolean isEnabled(@jakarta.annotation.Nonnull ArrangementSettingsToken token, @jakarta.annotation.Nullable ArrangementMatchCondition current) {
    if (SUPPORTED_TYPES.contains(token) || SUPPORTED_ORDERS.contains(token) || StdArrangementTokens.Regexp.NAME.equals(token)) {
      return true;
    }
    ArrangementSettingsToken type = null;
    if (current != null) {
      type = ArrangementUtil.parseType(current);
    }
    if (type == null) {
      type = NO_TYPE;
    }
    Set<ArrangementSettingsToken> modifiers = MODIFIERS_BY_TYPE.get(type);
    return modifiers != null && modifiers.contains(token);
  }

  @Nonnull
  @Override
  public ArrangementEntryMatcher buildMatcher(@Nonnull ArrangementMatchCondition condition) throws IllegalArgumentException {
    throw new IllegalArgumentException("Can't build a matcher for condition " + condition);
  }

  @jakarta.annotation.Nonnull
  @Override
  public Collection<Set<ArrangementSettingsToken>> getMutexes() {
    return MUTEXES;
  }

  private static void and(@Nonnull List<StdArrangementMatchRule> matchRules, @Nonnull ArrangementSettingsToken... conditions) {
    if (conditions.length == 1) {
      matchRules.add(new StdArrangementMatchRule(new StdArrangementEntryMatcher(new ArrangementAtomMatchCondition(conditions[0],
          conditions[0]))));
      return;
    }

    ArrangementCompositeMatchCondition composite = new ArrangementCompositeMatchCondition();
    for (ArrangementSettingsToken condition : conditions) {
      composite.addOperand(new ArrangementAtomMatchCondition(condition, condition));
    }
    matchRules.add(new StdArrangementMatchRule(new StdArrangementEntryMatcher(composite)));
  }

  @jakarta.annotation.Nullable
  @Override
  public TextAttributes getTextAttributes(@Nonnull EditorColorsScheme scheme, @jakarta.annotation.Nonnull ArrangementSettingsToken token, boolean selected) {
    if (selected) {
      TextAttributes attributes = new TextAttributes();
      attributes.setForegroundColor(scheme.getColor(EditorColors.SELECTION_FOREGROUND_COLOR));
      attributes.setBackgroundColor(scheme.getColor(EditorColors.SELECTION_BACKGROUND_COLOR));
      return attributes;
    } else if (SUPPORTED_TYPES.contains(token)) {
      return getAttributes(scheme, JavaHighlightingColors.KEYWORD);
    } else if (SUPPORTED_MODIFIERS.contains(token)) {
      getAttributes(scheme, JavaHighlightingColors.KEYWORD);
    }
    return null;
  }

  @jakarta.annotation.Nullable
  private static TextAttributes getAttributes(@jakarta.annotation.Nonnull EditorColorsScheme scheme, @jakarta.annotation.Nonnull TextAttributesKey... keys) {
    TextAttributes result = null;
    for (TextAttributesKey key : keys) {
      TextAttributes attributes = scheme.getAttributes(key);
      if (attributes == null) {
        continue;
      }

      if (result == null) {
        result = attributes;
      }

      ColorValue currentForegroundColor = result.getForegroundColor();
      if (currentForegroundColor == null) {
        result.setForegroundColor(attributes.getForegroundColor());
      }

      ColorValue currentBackgroundColor = result.getBackgroundColor();
      if (currentBackgroundColor == null) {
        result.setBackgroundColor(attributes.getBackgroundColor());
      }

      if (result.getForegroundColor() != null && result.getBackgroundColor() != null) {
        return result;
      }
    }

    if (result != null && result.getForegroundColor() == null) {
      return null;
    }

    if (result != null && result.getBackgroundColor() == null) {
      result.setBackgroundColor(scheme.getDefaultBackground());
    }
    return result;
  }

  @jakarta.annotation.Nullable
  @Override
  public Color getBorderColor(@Nonnull EditorColorsScheme scheme, boolean selected) {
    return null;
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }
}
