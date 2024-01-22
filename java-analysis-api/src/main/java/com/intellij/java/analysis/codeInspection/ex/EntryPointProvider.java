package com.intellij.java.analysis.codeInspection.ex;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.language.editor.inspection.reference.RefElement;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author VISTALL
 * @since 25/03/2023
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface EntryPointProvider<State extends EntryPointState>
{
	@jakarta.annotation.Nonnull
	String getId();

	@jakarta.annotation.Nonnull
	LocalizeValue getDisplayName();

	@Nonnull
	State createState();

	default boolean isEntryPoint(RefElement refElement, PsiElement psiElement, @jakarta.annotation.Nonnull State state)
	{
		return isEntryPoint(psiElement, state);
	}

	boolean isEntryPoint(PsiElement psiElement, @jakarta.annotation.Nonnull State state);

	@Nullable
	default String[] getIgnoreAnnotations()
	{
		return null;
	}

	default boolean showInSettings()
	{
		return true;
	}

	default void setEnabled(State state, boolean value)
	{
		state.enabled = value;
	}

	default boolean isEnabled(State state)
	{
		return state.enabled;
	}
}
