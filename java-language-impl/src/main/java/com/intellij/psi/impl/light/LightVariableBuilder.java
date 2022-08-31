package com.intellij.psi.impl.light;

import javax.annotation.Nonnull;
import javax.swing.Icon;

import org.jetbrains.annotations.NonNls;

import javax.annotation.Nullable;
import com.intellij.codeInsight.completion.originInfo.OriginInfoAwareElement;
import com.intellij.lang.Language;
import com.intellij.java.language.JavaLanguage;
import com.intellij.navigation.NavigationItem;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiIdentifier;
import com.intellij.psi.PsiManager;
import com.intellij.java.language.psi.PsiModifierList;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.PsiTypeElement;
import com.intellij.java.language.psi.PsiVariable;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PlatformIcons;

/**
 * @author peter
 */
public class LightVariableBuilder<T extends LightVariableBuilder> extends LightElement implements PsiVariable,
		NavigationItem, OriginInfoAwareElement
{
	private final String myName;
	private final PsiType myType;
	private volatile LightModifierList myModifierList;
	private volatile Icon myBaseIcon = PlatformIcons.VARIABLE_ICON;
	private String myOriginInfo;

	public LightVariableBuilder(@Nonnull String name, @Nonnull String type, @Nonnull PsiElement navigationElement)
	{
		this(name, JavaPsiFacade.getElementFactory(navigationElement.getProject()).createTypeFromText(type,
				navigationElement), navigationElement);
	}

	public LightVariableBuilder(@Nonnull String name, @Nonnull PsiType type, @Nonnull PsiElement navigationElement)
	{
		this(navigationElement.getManager(), name, type, JavaLanguage.INSTANCE);
		setNavigationElement(navigationElement);
	}

	public LightVariableBuilder(PsiManager manager, @Nonnull String name, @Nonnull PsiType type, Language language)
	{
		super(manager, language);
		myName = name;
		myType = type;
		myModifierList = new LightModifierList(manager);
	}

	@Override
	public String toString()
	{
		return "LightVariableBuilder:" + getName();
	}

	@Nonnull
	@Override
	public PsiType getType()
	{
		return myType;
	}

	@Override
	@Nonnull
	public PsiModifierList getModifierList()
	{
		return myModifierList;
	}

	public T setModifiers(String... modifiers)
	{
		myModifierList = new LightModifierList(getManager(), getLanguage(), modifiers);
		return (T) this;
	}

	public T setModifierList(LightModifierList modifierList)
	{
		myModifierList = modifierList;
		return (T)this;
	}

	@Override
	public boolean hasModifierProperty(@NonNls @Nonnull String name)
	{
		return myModifierList.hasModifierProperty(name);
	}

	@Nonnull
	@Override
	public String getName()
	{
		return myName;
	}

	@Override
	public PsiTypeElement getTypeElement()
	{
		return null;
	}

	@Override
	public PsiExpression getInitializer()
	{
		return null;
	}

	@Override
	public boolean hasInitializer()
	{
		return false;
	}

	@Override
	public void normalizeDeclaration() throws IncorrectOperationException
	{
	}

	@Override
	public Object computeConstantValue()
	{
		return null;
	}

	@Override
	public PsiIdentifier getNameIdentifier()
	{
		return null;
	}

	@Override
	public PsiElement setName(@NonNls @Nonnull String name) throws IncorrectOperationException
	{
		throw new UnsupportedOperationException("setName is not implemented yet in com.intellij.psi.impl.light" +
				".LightVariableBuilder");
	}

	@Deprecated
	public T setBaseIcon(Icon baseIcon)
	{
		myBaseIcon = baseIcon;
		return (T) this;
	}

	@javax.annotation.Nullable
	@Override
	public String getOriginInfo()
	{
		return myOriginInfo;
	}

	public void setOriginInfo(@Nullable String originInfo)
	{
		myOriginInfo = originInfo;
	}
}
