package com.intellij.java.language.impl.psi.impl.light;

import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.*;
import consulo.language.Language;
import consulo.language.impl.psi.LightElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.util.IncorrectOperationException;
import consulo.navigation.NavigationItem;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author peter
 */
public class LightVariableBuilder<T extends LightVariableBuilder> extends LightElement implements PsiVariable,
    NavigationItem, OriginInfoAwareElement {
  private final String myName;
  private final PsiType myType;
  private volatile LightModifierList myModifierList;
  private String myOriginInfo;

  public LightVariableBuilder(@jakarta.annotation.Nonnull String name, @Nonnull String type, @Nonnull PsiElement navigationElement) {
    this(name, JavaPsiFacade.getElementFactory(navigationElement.getProject()).createTypeFromText(type,
        navigationElement), navigationElement);
  }

  public LightVariableBuilder(@jakarta.annotation.Nonnull String name, @Nonnull PsiType type, @Nonnull PsiElement navigationElement) {
    this(navigationElement.getManager(), name, type, JavaLanguage.INSTANCE);
    setNavigationElement(navigationElement);
  }

  public LightVariableBuilder(PsiManager manager, @Nonnull String name, @Nonnull PsiType type, Language language) {
    super(manager, language);
    myName = name;
    myType = type;
    myModifierList = new LightModifierList(manager);
  }

  @Override
  public String toString() {
    return "LightVariableBuilder:" + getName();
  }

  @jakarta.annotation.Nonnull
  @Override
  public PsiType getType() {
    return myType;
  }

  @Override
  @Nonnull
  public PsiModifierList getModifierList() {
    return myModifierList;
  }

  public T setModifiers(String... modifiers) {
    myModifierList = new LightModifierList(getManager(), getLanguage(), modifiers);
    return (T) this;
  }

  public T setModifierList(LightModifierList modifierList) {
    myModifierList = modifierList;
    return (T) this;
  }

  @Override
  public boolean hasModifierProperty(@NonNls @Nonnull String name) {
    return myModifierList.hasModifierProperty(name);
  }

  @jakarta.annotation.Nonnull
  @Override
  public String getName() {
    return myName;
  }

  @Override
  public PsiTypeElement getTypeElement() {
    return null;
  }

  @Override
  public PsiExpression getInitializer() {
    return null;
  }

  @Override
  public boolean hasInitializer() {
    return false;
  }

  @Override
  public void normalizeDeclaration() throws IncorrectOperationException {
  }

  @Override
  public Object computeConstantValue() {
    return null;
  }

  @Override
  public PsiIdentifier getNameIdentifier() {
    return null;
  }

  @Override
  public PsiElement setName(@NonNls @Nonnull String name) throws IncorrectOperationException {
    throw new UnsupportedOperationException("setName is not implemented yet in com.intellij.psi.impl.light" +
        ".LightVariableBuilder");
  }

  @Nullable
  @Override
  public String getOriginInfo() {
    return myOriginInfo;
  }

  public void setOriginInfo(@Nullable String originInfo) {
    myOriginInfo = originInfo;
  }
}
