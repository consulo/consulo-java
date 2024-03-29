/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.java.execution.impl;

import com.intellij.java.execution.CommonJavaRunConfigurationParameters;
import com.intellij.java.execution.ShortenCommandLine;
import com.intellij.java.execution.configurations.JavaRunConfigurationModule;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import consulo.execution.action.Location;
import consulo.execution.configuration.ConfigurationFactory;
import consulo.execution.configuration.ModuleBasedConfiguration;
import consulo.execution.test.TestSearchScope;
import consulo.execution.test.sm.runner.SMRunnerConsolePropertiesProvider;
import consulo.language.editor.refactoring.event.RefactoringListenerProvider;
import consulo.language.psi.PsiElement;
import consulo.util.xml.serializer.InvalidDataException;
import consulo.util.xml.serializer.WriteExternalException;
import org.jdom.Element;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.List;

public abstract class JavaTestConfigurationBase extends ModuleBasedConfiguration<JavaRunConfigurationModule> implements CommonJavaRunConfigurationParameters, ConfigurationWithCommandLineShortener,
    RefactoringListenerProvider, SMRunnerConsolePropertiesProvider {
  private ShortenCommandLine myShortenCommandLine = null;

  public JavaTestConfigurationBase(String name, @Nonnull JavaRunConfigurationModule configurationModule, @Nonnull ConfigurationFactory factory) {
    super(name, configurationModule, factory);
  }

  public JavaTestConfigurationBase(JavaRunConfigurationModule configurationModule, ConfigurationFactory factory) {
    super(configurationModule, factory);
  }

  @Nonnull
  public abstract String getFrameworkPrefix();

  public abstract void bePatternConfiguration(List<PsiClass> classes, PsiMethod method);

  public abstract void beMethodConfiguration(Location<PsiMethod> location);

  public abstract void beClassConfiguration(PsiClass aClass);

  public abstract boolean isConfiguredByElement(PsiElement element);

  public String prepareParameterizedParameter(String paramSetName) {
    return paramSetName;
  }

  public abstract TestSearchScope getTestSearchScope();

  @Nullable
  @Override
  public ShortenCommandLine getShortenCommandLine() {
    return myShortenCommandLine;
  }

  @Override
  public void setShortenCommandLine(ShortenCommandLine shortenCommandLine) {
    myShortenCommandLine = shortenCommandLine;
  }

  @Override
  public void readExternal(@Nonnull Element element) throws InvalidDataException {
    super.readExternal(element);
    setShortenCommandLine(ShortenCommandLine.readShortenClasspathMethod(element));
  }

  @Override
  public void writeExternal(@Nonnull Element element) throws WriteExternalException {
    super.writeExternal(element);
    ShortenCommandLine.writeShortenClasspathMethod(element, myShortenCommandLine);
  }
}
