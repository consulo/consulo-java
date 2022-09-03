package com.intellij.java.impl.unscramble;

import consulo.annotation.component.ExtensionImpl;
import consulo.execution.unscramble.StacktraceAnalyzer;
import consulo.java.language.module.extension.JavaModuleExtension;
import consulo.localize.LocalizeValue;
import consulo.module.extension.ModuleExtensionHelper;
import consulo.project.Project;
import consulo.util.lang.StringUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.regex.Pattern;

/**
 * @author VISTALL
 * @since 01-Sep-22
 */
@ExtensionImpl
public class JavaStacktraceAnalyzer implements StacktraceAnalyzer {
  private static final Pattern STACKTRACE_LINE =
      Pattern.compile("[\t]*at [[_a-zA-Z0-9]+\\.]+[_a-zA-Z$0-9]+\\.[a-zA-Z0-9_]+\\([A-Za-z0-9_]+\\.java:[\\d]+\\)+[ [~]*\\[[a-zA-Z0-9\\.\\:/]\\]]*");

  @Nonnull
  @Override
  public LocalizeValue getName() {
    return LocalizeValue.localizeTODO("JVM");
  }

  @Override
  public boolean isPreferredForProject(@Nonnull Project project) {
    return ModuleExtensionHelper.getInstance(project).hasModuleExtension(JavaModuleExtension.class);
  }

  @Override
  public boolean isStacktrace(@Nonnull String stacktrace) {
    stacktrace = UnscrambleDialog.normalizeText(stacktrace);
    int linesCount = 0;
    for (String line : stacktrace.split("\n")) {
      line = line.trim();
      if (line.length() == 0) continue;
      if (line.endsWith("\r")) {
        line = line.substring(0, line.length() - 1);
      }
      if (STACKTRACE_LINE.matcher(line).matches()) {
        linesCount++;
      } else {
        linesCount = 0;
      }
      if (linesCount > 2) return true;
    }
    return false;
  }

  @Nullable
  @Override
  public String parseAsException(@Nonnull String stacktrace) {
    return StringUtil.nullize(getExceptionName(stacktrace));
  }

  @Nullable
  private static String getExceptionName(String unscrambledTrace) {
    BufferedReader reader = new BufferedReader(new StringReader(unscrambledTrace));
    for (int i = 0; i < 3; i++) {
      try {
        String line = reader.readLine();
        if (line == null) {
          return null;
        }
        line = line.trim();
        String name = getExceptionAbbreviation(line);
        if (name != null) {
          return name;
        }
      } catch (IOException e) {
        return null;
      }
    }
    return null;
  }

  @Nullable
  private static String getExceptionAbbreviation(String line) {
    int lastDelimiter = 0;
    for (int j = 0; j < line.length(); j++) {
      char c = line.charAt(j);
      if (c == '.' || c == '$') {
        lastDelimiter = j;
        continue;
      }
      if (!StringUtil.isJavaIdentifierPart(c)) {
        return null;
      }
    }
    String clazz = line.substring(lastDelimiter);
    String abbreviate = abbreviate(clazz);
    return abbreviate.length() > 1 ? abbreviate : clazz;
  }

  private static String abbreviate(String s) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (Character.isUpperCase(c)) {
        builder.append(c);
      }
    }
    return builder.toString();
  }
}
