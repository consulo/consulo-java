/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.impl.psi.impl.source.javadoc;

import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.impl.psi.impl.source.javadoc.AuthorDocTagInfo;
import com.intellij.java.language.impl.psi.impl.source.javadoc.SimpleDocTagInfo;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiJavaModule;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.javadoc.JavadocManager;
import com.intellij.java.language.psi.javadoc.JavadocTagInfo;
import consulo.annotation.component.ServiceImpl;
import consulo.language.editor.inspection.SuppressionUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiPackage;
import consulo.project.Project;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * @author mike
 */
@Singleton
@ServiceImpl
public class JavadocManagerImpl implements JavadocManager {
  private final List<JavadocTagInfo> myInfos;
  private final Project myProject;

  @Inject
  public JavadocManagerImpl(Project project) {
    myProject = project;
    myInfos = new ArrayList<>();

    myInfos.add(new AuthorDocTagInfo());
    myInfos.add(new SimpleDocTagInfo("deprecated", LanguageLevel.JDK_1_3, false, PsiElement.class));
    myInfos.add(new SimpleDocTagInfo("serialData", LanguageLevel.JDK_1_3, false, PsiMethod.class));
    myInfos.add(new SimpleDocTagInfo("serialField", LanguageLevel.JDK_1_3, false, PsiField.class));
    myInfos.add(new SimpleDocTagInfo("since", LanguageLevel.JDK_1_3, false, PsiElement.class, PsiPackage.class));
    myInfos.add(new SimpleDocTagInfo("version", LanguageLevel.JDK_1_3, false, PsiClass.class, PsiPackage.class));
    myInfos.add(new SimpleDocTagInfo("apiNote", LanguageLevel.JDK_1_8, false, PsiElement.class));
    myInfos.add(new SimpleDocTagInfo("implNote", LanguageLevel.JDK_1_8, false, PsiElement.class));
    myInfos.add(new SimpleDocTagInfo("implSpec", LanguageLevel.JDK_1_8, false, PsiElement.class));
    myInfos.add(new SimpleDocTagInfo("moduleGraph", LanguageLevel.JDK_1_9, false, PsiJavaModule.class));

    myInfos.add(new SimpleDocTagInfo("docRoot", LanguageLevel.JDK_1_3, true, PsiElement.class));
    myInfos.add(new SimpleDocTagInfo("inheritDoc", LanguageLevel.JDK_1_4, true, PsiElement.class));
    myInfos.add(new SimpleDocTagInfo("literal", LanguageLevel.JDK_1_5, true, PsiElement.class));
    myInfos.add(new SimpleDocTagInfo("code", LanguageLevel.JDK_1_5, true, PsiElement.class));

    //Not a standard tag, but added by IDEA for inspection suppression
    myInfos.add(new SimpleDocTagInfo(SuppressionUtil.SUPPRESS_INSPECTIONS_TAG_NAME, LanguageLevel.JDK_1_3, false, PsiElement.class));

    myInfos.add(new ParamDocTagInfo());
    myInfos.add(new ReturnDocTagInfo());
    myInfos.add(new SerialDocTagInfo());
    myInfos.add(new SeeDocTagInfo("see", false));
    myInfos.add(new SeeDocTagInfo("link", true));
    myInfos.add(new SeeDocTagInfo("linkplain", true));
    myInfos.add(new ExceptionTagInfo("exception"));
    myInfos.add(new ExceptionTagInfo("throws"));
    myInfos.add(new ValueDocTagInfo());
  }

  @Override
  @Nonnull
  public JavadocTagInfo[] getTagInfos(PsiElement context) {
    List<JavadocTagInfo> result = new ArrayList<>();

    for (JavadocTagInfo info : myInfos) {
      if (info.isValidInContext(context)) {
        result.add(info);
      }
    }

    for (JavadocTagInfo info : myProject.getExtensionList(JavadocTagInfo.class)) {
      if (info.isValidInContext(context)) {
        result.add(info);
      }
    }
    return result.toArray(new JavadocTagInfo[result.size()]);
  }

  @Override
  @Nullable
  public JavadocTagInfo getTagInfo(String name) {
    for (JavadocTagInfo info : myInfos) {
      if (info.getName().equals(name)) {
        return info;
      }
    }

    for (JavadocTagInfo info : myProject.getExtensionList(JavadocTagInfo.class)) {
      if (info.getName().equals(name)) {
        return info;
      }
    }

    return null;
  }
}