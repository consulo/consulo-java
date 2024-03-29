/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInsight.javadoc;

import com.intellij.java.impl.lang.java.JavaDocumentationProvider;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import consulo.application.ApplicationManager;
import consulo.language.editor.documentation.AbstractExternalFilter;
import consulo.language.editor.documentation.DocumentationManagerProtocol;
import consulo.language.editor.documentation.PlatformDocumentationUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.project.Project;
import consulo.util.io.URLUtil;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;

import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static consulo.language.editor.documentation.DocumentationManagerUtil.createHyperlink;

/**
 * User: db
 * Date: May 2, 2003
 * Time: 8:35:34 PM
 */
public class JavaDocExternalFilter extends AbstractExternalFilter {
  private final Project myProject;

  private static final ParseSettings ourPackageInfoSettings = new ParseSettings(Pattern.compile
      ("package\\s+[^\\s]+\\s+description", Pattern.CASE_INSENSITIVE), Pattern.compile("START OF BOTTOM NAVBAR",
      Pattern.CASE_INSENSITIVE), true, false);

  protected static
  @NonNls
  final Pattern ourHTMLsuffix = Pattern.compile("[.][hH][tT][mM][lL]?");
  protected static
  @NonNls
  final Pattern ourParentFolderprefix = Pattern.compile("^[.][.]/");
  protected static
  @NonNls
  final Pattern ourAnchorsuffix = Pattern.compile("#(.*)$");
  protected static
  @NonNls
  final Pattern ourHTMLFilesuffix = Pattern.compile("/([^/]*[.][hH][tT][mM][lL]?)$");
  private static
  @NonNls
  final Pattern ourHREFselector = Pattern.compile("<A.*?HREF=\"([^>\"]*)\"", Pattern.CASE_INSENSITIVE | Pattern
      .DOTALL);
  private static
  @NonNls
  final Pattern ourMethodHeading = Pattern.compile("<H[34]>(.+?)</H[34]>", Pattern.CASE_INSENSITIVE | Pattern
      .DOTALL);
  @NonNls
  protected static final String H2 = "</H2>";
  @NonNls
  protected static final String HTML_CLOSE = "</HTML>";
  @NonNls
  protected static final String HTML = "<HTML>";

  private final RefConvertor[] myReferenceConvertors = new RefConvertor[]{
      new RefConvertor(ourHREFselector) {
        @Override
        protected String convertReference(String root, String href) {
          if (URLUtil.isAbsoluteURL(href)) {
            return href;
          }

          if (StringUtil.startsWithChar(href, '#')) {
            return root + href;
          }

          String nakedRoot = ourHTMLFilesuffix.matcher(root).replaceAll("/");

          String stripped = ourHTMLsuffix.matcher(href).replaceAll("");
          int len = stripped.length();

          do {
            stripped = ourParentFolderprefix.matcher(stripped).replaceAll("");
          }
          while (len > (len = stripped.length()));

          final String elementRef = stripped.replaceAll("/", ".");
          final String classRef = ourAnchorsuffix.matcher(elementRef).replaceAll("");

          return (JavaPsiFacade.getInstance(myProject).findClass(classRef,
              GlobalSearchScope.allScope(myProject)) != null) ? DocumentationManagerProtocol
              .PSI_ELEMENT_PROTOCOL + elementRef : doAnnihilate(nakedRoot + href);
        }
      }
  };

  public JavaDocExternalFilter(Project project) {
    myProject = project;
  }

  @Override
  protected RefConvertor[] getRefConverters() {
    return myReferenceConvertors;
  }

  @Nullable
  public static String filterInternalDocInfo(String text) {
    if (text == null) {
      return null;
    }
    text = PlatformDocumentationUtil.fixupText(text);
    return text;
  }

  @Override
  @Nullable
  public String getExternalDocInfoForElement(final String docURL, final PsiElement element) throws Exception {
    String externalDoc = super.getExternalDocInfoForElement(docURL, element);
    if (externalDoc != null) {
      if (element instanceof PsiMethod) {
        final String className = ApplicationManager.getApplication().runReadAction(new
                                                                                       Supplier<String>() {
                                                                                         @Override
                                                                                         @Nullable
                                                                                         public String get() {
                                                                                           PsiClass aClass = ((PsiMethod) element).getContainingClass();
                                                                                           return aClass == null ? null : aClass.getQualifiedName();
                                                                                         }
                                                                                       });
        Matcher matcher = ourMethodHeading.matcher(externalDoc);
        final StringBuilder buffer = new StringBuilder();
        createHyperlink(buffer, className, className, false);
        //noinspection HardCodedStringLiteral
        return matcher.replaceFirst("<H3>" + buffer.toString() + "</H3>");
      }
    }
    return externalDoc;
  }

  @Nonnull
  @Override
  protected ParseSettings getParseSettings(@Nonnull String url) {
    return url.endsWith(JavaDocumentationProvider.PACKAGE_SUMMARY_FILE) ? ourPackageInfoSettings : super
        .getParseSettings(url);
  }
}
