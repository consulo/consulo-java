package com.intellij.java.impl.codeInsight.editorActions.smartEnter;

import java.util.Collection;
import java.util.List;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import com.intellij.java.impl.javadoc.JavadocHelper;
import consulo.codeEditor.CaretModel;
import consulo.document.Document;
import consulo.codeEditor.Editor;
import consulo.codeEditor.LogicalPosition;
import consulo.util.lang.Pair;
import consulo.language.psi.PsiFile;

/**
 * Serves as a facade for javadoc smart completion.
 * <p>
 * Thread-safe.
 *
 * @author Denis Zhdanov
 * @since 5/31/11 1:22 PM
 */
public class JavadocFixer
{

	private final JavadocHelper myHelper = new JavadocHelper();

	/**
	 * Checks if caret of the given editor is located inside javadoc and tries to perform smart completion there in case of the positive
	 * answer.
	 *
	 * @param editor  target editor
	 * @param psiFile PSI file for the document exposed via the given editor
	 * @return <code>true</code> if smart completion was performed; <code>false</code> otherwise
	 */
	public boolean process(@Nonnull Editor editor, @Nonnull PsiFile psiFile)
	{
		// Check parameter description completion.
		final CaretModel caretModel = editor.getCaretModel();
		final Pair<JavadocHelper.JavadocParameterInfo, List<JavadocHelper.JavadocParameterInfo>> pair = myHelper.parse(psiFile, editor, caretModel.getOffset());

		if(pair.first == null)
		{
			return false;
		}

		final JavadocHelper.JavadocParameterInfo next = findNext(pair.second, pair.first);
		if(next == null)
		{
			final int line = pair.first.lastLine + 1;
			final Document document = editor.getDocument();
			if(line < document.getLineCount())
			{
				StringBuilder indent = new StringBuilder();
				boolean insertIndent = true;
				final CharSequence text = document.getCharsSequence();
				for(int i = document.getLineStartOffset(line), max = document.getLineEndOffset(line); i < max; i++)
				{
					final char c = text.charAt(i);
					if(c == ' ' || c == '\t')
					{
						indent.append(c);
						continue;
					}
					else if(c == '*')
					{
						indent.append("* ");
						if(i < max - 1 && text.charAt(i + 1) != '/')
						{
							insertIndent = false;
						}
					}
					indent.append("\n");
					break;
				}
				if(insertIndent)
				{
					document.insertString(document.getLineStartOffset(line), indent);
				}
			}
			moveCaretToTheLineEndIfPossible(editor, line);
			return true;
		}

		if(next.parameterDescriptionStartPosition != null)
		{
			myHelper.navigate(next.parameterDescriptionStartPosition, editor, psiFile.getProject());
		}
		else
		{
			final LogicalPosition position = myHelper.calculateDescriptionStartPosition(psiFile, pair.second, next);
			myHelper.navigate(position, editor, psiFile.getProject());
		}
		return true;
	}

	private static void moveCaretToTheLineEndIfPossible(@Nonnull Editor editor, int line)
	{
		final Document document = editor.getDocument();
		final CaretModel caretModel = editor.getCaretModel();
		int offset;
		if(line >= document.getLineCount())
		{
			offset = document.getTextLength();
		}
		else
		{
			offset = document.getLineEndOffset(line);
		}
		caretModel.moveToOffset(offset);
	}

	@Nullable
	private static JavadocHelper.JavadocParameterInfo findNext(@Nonnull Collection<JavadocHelper.JavadocParameterInfo> data, @Nonnull JavadocHelper.JavadocParameterInfo anchor)
	{
		boolean returnNow = false;
		for(JavadocHelper.JavadocParameterInfo info : data)
		{
			if(returnNow)
			{
				return info;
			}
			returnNow = info == anchor;
		}
		return null;
	}
}
