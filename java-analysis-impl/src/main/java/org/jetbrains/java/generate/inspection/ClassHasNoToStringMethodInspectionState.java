package org.jetbrains.java.generate.inspection;

import consulo.configurable.ConfigurableBuilder;
import consulo.configurable.ConfigurableBuilderState;
import consulo.configurable.UnnamedConfigurable;
import consulo.language.editor.inspection.InspectionToolState;
import consulo.localize.LocalizeValue;
import consulo.ui.Label;
import consulo.util.xml.serializer.XmlSerializerUtil;

import jakarta.annotation.Nullable;

/**
 * @author VISTALL
 * @since 20/03/2023
 */
public class ClassHasNoToStringMethodInspectionState implements InspectionToolState<ClassHasNoToStringMethodInspectionState> {
  /**
   * User options for classes to exclude. Must be a regexp pattern
   */
  public String excludeClassNames = "";
  /**
   * User options for excluded exception classes
   */
  public boolean excludeException = true;
  /**
   * User options for excluded deprecated classes
   */
  public boolean excludeDeprecated = true;
  /**
   * User options for excluded enum classes
   */
  public boolean excludeEnum = false;
  /**
   * User options for excluded abstract classes
   */
  public boolean excludeAbstract = false;

  public boolean excludeTestCode = false;

  public boolean excludeInnerClasses = false;

  @Nullable
  @Override
  public UnnamedConfigurable createConfigurable() {
    ConfigurableBuilder<ConfigurableBuilderState> state = ConfigurableBuilder.newBuilder();
    state.component(() -> Label.create(LocalizeValue.localizeTODO("Exclude classes (reg exp):")));
    state.textBox(() -> excludeClassNames, s -> excludeClassNames = s);
    state.checkBox(LocalizeValue.localizeTODO("Ignore exception classes"), () -> excludeException, b -> excludeException = b);
    state.checkBox(LocalizeValue.localizeTODO("Ignore deprecated classes"), () -> excludeDeprecated, b -> excludeDeprecated = b);
    state.checkBox(LocalizeValue.localizeTODO("Ignore enum classes"), () -> excludeEnum, b -> excludeEnum = b);
    state.checkBox(LocalizeValue.localizeTODO("Ignore abstract classes"), () -> excludeAbstract, b -> excludeAbstract = b);
    state.checkBox(LocalizeValue.localizeTODO("Ignore test classes"), () -> excludeTestCode, b -> excludeTestCode = b);
    state.checkBox(LocalizeValue.localizeTODO("Ignore inner classes"), () -> excludeInnerClasses, b -> excludeInnerClasses = b);
    return state.buildUnnamed();
  }

  @Nullable
  @Override
  public ClassHasNoToStringMethodInspectionState getState() {
    return this;
  }

  @Override
  public void loadState(ClassHasNoToStringMethodInspectionState state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
