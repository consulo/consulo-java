/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.compiler.impl.javaCompiler.javac;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.jetbrains.annotations.NonNls;
import com.intellij.compiler.OutputParser;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import consulo.java.rt.compiler.JavacResourcesReaderConstans;

public class JavacOutputParser extends OutputParser implements JavacResourcesReaderConstans
{
	private final int myTabSize;
	@NonNls
	private String WARNING_PREFIX = "warning:"; // default value

	public JavacOutputParser(Project project)
	{
		myTabSize = CodeStyleSettingsManager.getSettings(project).getTabSize(JavaFileType.INSTANCE);
		if(ApplicationManager.getApplication().isUnitTestMode())
		{
			// emulate patterns setup if 'embedded' javac is used (javac is started not via JavacRunner)
			addJavacPattern(MSG_PARSING_STARTED + CATEGORY_VALUE_DIVIDER + "[parsing started {0}]");
			addJavacPattern(MSG_PARSING_COMPLETED + CATEGORY_VALUE_DIVIDER + "[parsing completed {0}ms]");
			addJavacPattern(MSG_LOADING + CATEGORY_VALUE_DIVIDER + "[loading {0}]");
			addJavacPattern(MSG_CHECKING + CATEGORY_VALUE_DIVIDER + "[checking {0}]");
			addJavacPattern(MSG_WROTE + CATEGORY_VALUE_DIVIDER + "[wrote {0}]");
		}
	}

	@Override
	public boolean processMessageLine(Callback callback)
	{
		if(super.processMessageLine(callback))
		{
			return true;
		}
		final String line = callback.getCurrentLine();
		if(line == null)
		{
			return false;
		}
		if(MSG_PATTERNS_START.equals(line))
		{
			myParserActions.clear();
			while(true)
			{
				final String patternLine = callback.getNextLine();
				if(MSG_PATTERNS_END.equals(patternLine))
				{
					break;
				}
				addJavacPattern(patternLine);
			}
			return true;
		}

		int colonIndex1 = line.indexOf(':');
		if(colonIndex1 == 1)
		{ // drive letter
			colonIndex1 = line.indexOf(':', colonIndex1 + 1);
		}

		if(colonIndex1 >= 0)
		{ // looks like found something like file path
			@NonNls String part1 = line.substring(0, colonIndex1).trim();
			if(part1.equalsIgnoreCase("error") /*jikes*/ || part1.equalsIgnoreCase("Caused by"))
			{
				addMessage(callback, CompilerMessageCategory.ERROR, line.substring(colonIndex1));
				return true;
			}
			if(part1.equalsIgnoreCase("warning"))
			{
				addMessage(callback, CompilerMessageCategory.WARNING, line.substring(colonIndex1));
				return true;
			}
			if(part1.equals("javac"))
			{
				addMessage(callback, CompilerMessageCategory.ERROR, line);
				return true;
			}

			final int colonIndex2 = line.indexOf(':', colonIndex1 + 1);
			if(colonIndex2 >= 0)
			{
				final String filePath = part1.replace(File.separatorChar, '/');
				if(!new File(filePath).exists())
				{
					// the part one turned out to be something else than a file path
					return true;
				}
				try
				{
					final int lineNum = Integer.parseInt(line.substring(colonIndex1 + 1, colonIndex2).trim());
					String message = line.substring(colonIndex2 + 1).trim();
					CompilerMessageCategory category = CompilerMessageCategory.ERROR;
					if(message.startsWith(WARNING_PREFIX))
					{
						message = message.substring(WARNING_PREFIX.length()).trim();
						category = CompilerMessageCategory.WARNING;
					}

					List<String> messages = new ArrayList<>();
					messages.add(message);
					int colNum;
					String prevLine = null;
					do
					{
						final String nextLine = callback.getNextLine();
						if(nextLine == null)
						{
							return false;
						}
						if(nextLine.trim().equals("^"))
						{
							final CharSequence chars = prevLine == null ? line : prevLine;
							final int offset = Math.max(0, Math.min(chars.length(), nextLine.indexOf('^')));
							colNum = EditorUtil.calcColumnNumber(null, chars, 0, offset, myTabSize);
							String messageEnd = callback.getNextLine();
							while(isMessageEnd(messageEnd))
							{
								messages.add(messageEnd.trim());
								messageEnd = callback.getNextLine();
							}
							if(messageEnd != null)
							{
								callback.pushBack(messageEnd);
							}
							break;
						}
						if(prevLine != null)
						{
							messages.add(prevLine);
						}
						prevLine = nextLine;
					}
					while(true);

					if(colNum >= 0)
					{
						messages = convertMessages(messages);
						final StringBuilder buf = new StringBuilder();
						for(final String m : messages)
						{
							if(buf.length() > 0)
							{
								buf.append("\n");
							}
							buf.append(m);
						}
						addMessage(callback, category, buf.toString(), VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, filePath), lineNum, colNum + 1);
						return true;
					}
				}
				catch(NumberFormatException ignored)
				{
				}
			}
		}

		if(line.endsWith("java.lang.OutOfMemoryError"))
		{
			addMessage(callback, CompilerMessageCategory.ERROR, CompilerBundle.message("error.javac.out.of.memory"));
			return true;
		}

		addMessage(callback, CompilerMessageCategory.INFORMATION, line);
		return true;
	}

	private static boolean isMessageEnd(String line)
	{
		return line != null && line.length() > 0 && Character.isWhitespace(line.charAt(0));
	}


	private static List<String> convertMessages(List<String> messages)
	{
		if(messages.size() <= 1)
		{
			return messages;
		}
		final String line0 = messages.get(0);
		final String line1 = messages.get(1);
		final int colonIndex = line1.indexOf(':');
		if(colonIndex > 0)
		{
			@NonNls String part1 = line1.substring(0, colonIndex).trim();
			// jikes
			if("symbol".equals(part1))
			{
				String symbol = line1.substring(colonIndex + 1).trim();
				messages.remove(1);
				if(messages.size() >= 2)
				{
					messages.remove(1);
				}
				messages.set(0, line0 + " " + symbol);
			}
		}
		return messages;
	}

	private void addJavacPattern(@NonNls final String line)
	{
		final int dividerIndex = line.indexOf(CATEGORY_VALUE_DIVIDER);
		if(dividerIndex < 0)
		{
			// by reports it may happen for some IBM JDKs (empty string?)
			return;
		}
		final String category = line.substring(0, dividerIndex);
		final String resourceBundleValue = line.substring(dividerIndex + 1);
		if(MSG_PARSING_COMPLETED.equals(category) || MSG_PARSING_STARTED.equals(category) || MSG_WROTE.equals(category))
		{
			myParserActions.add(new FilePathActionJavac(createMatcher(resourceBundleValue)));
		}
		else if(MSG_CHECKING.equals(category))
		{
			myParserActions.add(new JavacParserAction(createMatcher(resourceBundleValue))
			{
				@Override
				protected void doExecute(final String line, String parsedData, final Callback callback)
				{
					callback.setProgressText(CompilerBundle.message("progress.compiling.class", parsedData));
				}
			});
		}
		else if(MSG_LOADING.equals(category))
		{
			myParserActions.add(new JavacParserAction(createMatcher(resourceBundleValue))
			{
				@Override
				protected void doExecute(final String line, @javax.annotation.Nullable String parsedData, final Callback callback)
				{
					callback.setProgressText(CompilerBundle.message("progress.loading.classes"));
				}
			});
		}
		else if(MSG_NOTE.equals(category))
		{
			myParserActions.add(new JavacParserAction(createMatcher(resourceBundleValue))
			{
				@Override
				protected void doExecute(final String line, @Nullable final String filePath, final Callback callback)
				{
					final boolean fileExists = filePath != null && new File(filePath).exists();
					if(fileExists)
					{
						addMessage(callback, CompilerMessageCategory.WARNING, line, VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, filePath), -1, -1);
					}
					else
					{
						addMessage(callback, CompilerMessageCategory.INFORMATION, line);
					}
				}
			});
		}
		else if(MSG_WARNING.equals(category))
		{
			WARNING_PREFIX = resourceBundleValue;
		}
		else if(MSG_STATISTICS.equals(category))
		{
			myParserActions.add(new JavacParserAction(createMatcher(resourceBundleValue))
			{
				@Override
				protected void doExecute(final String line, @javax.annotation.Nullable String parsedData, final Callback callback)
				{
					// empty
				}
			});
		}
		else if(MSG_IGNORED.equals(category))
		{
			myParserActions.add(new JavacParserAction(createMatcher(resourceBundleValue))
			{
				@Override
				protected void doExecute(final String line, @javax.annotation.Nullable String parsedData, final Callback callback)
				{
					// ignored
				}
			});
		}
	}


	/**
	 * made public for Tests, do not use this method directly
	 */
	public static Matcher createMatcher(@NonNls final String resourceBundleValue)
	{
		@NonNls String regexp = resourceBundleValue.replaceAll("([\\[\\]\\(\\)\\.\\*])", "\\\\$1");
		regexp = regexp.replaceAll("\\{\\d+\\}", "(.+)");
		return Pattern.compile(regexp, Pattern.CASE_INSENSITIVE).matcher("");
	}

	@Override
	public boolean isTrimLines()
	{
		return false;
	}
}
