package com.intellij.codeInsight.javadoc;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import com.intellij.java.language.JavaLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;

@Singleton
public class JavaDocCodeStyleImpl extends JavaDocCodeStyle
{
	private final Project myProject;

	@Inject
	public JavaDocCodeStyleImpl(Project project)
	{
		myProject = project;
	}

	@Override
	public boolean spaceBeforeComma()
	{
		CommonCodeStyleSettings styleSettings = CodeStyleSettingsManager.getSettings(myProject).getCommonSettings(JavaLanguage.INSTANCE);
		return styleSettings.SPACE_BEFORE_COMMA;
	}

	@Override
	public boolean spaceAfterComma()
	{
		CommonCodeStyleSettings styleSettings = CodeStyleSettingsManager.getSettings(myProject).getCommonSettings(JavaLanguage.INSTANCE);
		return styleSettings.SPACE_AFTER_COMMA;
	}
}
