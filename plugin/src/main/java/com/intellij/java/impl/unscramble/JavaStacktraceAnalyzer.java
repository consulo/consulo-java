package com.intellij.java.impl.unscramble;

import consulo.annotation.component.ExtensionImpl;
import consulo.execution.unscramble.StacktraceAnalyzer;
import consulo.java.language.module.extension.JavaModuleExtension;
import consulo.localize.LocalizeValue;
import consulo.module.extension.ModuleExtensionHelper;
import consulo.project.Project;
import consulo.util.lang.CharArrayUtil;
import consulo.util.lang.StringUtil;
import org.jetbrains.annotations.NonNls;

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
    stacktrace = normalizeText(stacktrace);
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

  public static String normalizeText(@NonNls String text) {
    StringBuilder builder = new StringBuilder(text.length());

    text = text.replaceAll("(\\S[ \\t\\x0B\\f\\r]+)(at\\s+)", "$1\n$2");
    String[] lines = text.split("\n");

    boolean first = true;
    boolean inAuxInfo = false;
    for (String line : lines) {
      //noinspection HardCodedStringLiteral
      if (!inAuxInfo && (line.startsWith("JNI global references") || line.trim().equals("Heap"))) {
        builder.append("\n");
        inAuxInfo = true;
      }
      if (inAuxInfo) {
        builder.append(trimSuffix(line)).append("\n");
        continue;
      }
      if (!first && mustHaveNewLineBefore(line)) {
        builder.append("\n");
        if (line.startsWith("\"")) {
          builder.append("\n"); // Additional line break for thread names
        }
      }
      first = false;
      int i = builder.lastIndexOf("\n");
      CharSequence lastLine = i == -1 ? builder : builder.subSequence(i + 1, builder.length());
      if (lastLine.toString().matches("\\s*at") && !line.matches("\\s+.*")) {
        builder.append(" "); // separate 'at' from file name
      }
      builder.append(trimSuffix(line));
    }
    return builder.toString();
  }

  private static String trimSuffix(final String line) {
    int len = line.length();

    while ((0 < len) && (line.charAt(len - 1) <= ' ')) {
      len--;
    }
    return (len < line.length()) ? line.substring(0, len) : line;
  }

  private static boolean mustHaveNewLineBefore(String line) {
    final int nonWs = CharArrayUtil.shiftForward(line, 0, " \t");
    if (nonWs < line.length()) {
      line = line.substring(nonWs);
    }

    if (line.startsWith("at")) {
      return true;        // Start of the new stack frame entry
    }
    if (line.startsWith("Caused")) {
      return true;    // Caused by message
    }
    if (line.startsWith("- locked")) {
      return true;  // "Locked a monitor" logging
    }
    if (line.startsWith("- waiting")) {
      return true; // "Waiting for monitor" logging
    }
    if (line.startsWith("- parking to wait")) {
      return true;
    }
    if (line.startsWith("java.lang.Thread.State")) {
      return true;
    }
    if (line.startsWith("\"")) {
      return true;        // Start of the new thread (thread name)
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
