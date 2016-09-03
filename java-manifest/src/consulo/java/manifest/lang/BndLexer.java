package consulo.java.manifest.lang;

import org.osmorc.manifest.lang.ManifestLexer;
import org.osmorc.manifest.lang.ManifestTokenType;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;

/**
 * @author VISTALL
 * @since 1:21/12.05.13
 */
public class BndLexer extends ManifestLexer
{
	@Override
	public IElementType getToken(int position)
	{
		final IElementType token = super.getToken(position);
		if(token != null)
		{
			return null;
		}
		final char c = myBuffer.charAt(position);
		switch(c)
		{
			case '#':
				return ManifestTokenType.SHARP;
		}
		return null;
	}

	@Override
	protected void parseNextToken()
	{
		if(myTokenStart < myEndOffset)
		{
			if(getToken(myTokenStart) == ManifestTokenType.SHARP)
			{
				while(myTokenEnd < myEndOffset && !isNewline(myTokenEnd))
				{
					myTokenEnd++;
				}
				myTokenEnd++; // line end
				myTokenType = ManifestTokenType.LINE_COMMENT;
			}
			else if(isNewline(myTokenStart))
			{
				myTokenType = isLineStart(myTokenStart) ? ManifestTokenType.SECTION_END : ManifestTokenType.NEWLINE;
				myTokenEnd = myTokenStart + 1;
				myCurrentState = INITIAL_STATE;
			}
			else if(myCurrentState == WAITING_FOR_HEADER_ASSIGNMENT_STATE || myCurrentState == WAITING_FOR_HEADER_ASSIGNMENT_AFTER_BAD_CHARACTER_STATE)
			{
				if(isColon(myTokenStart))
				{
					myTokenType = ManifestTokenType.COLON;
					myCurrentState = WAITING_FOR_SPACE_AFTER_HEADER_NAME_STATE;
				}
				else
				{
					myTokenType = TokenType.BAD_CHARACTER;
					myCurrentState = WAITING_FOR_HEADER_ASSIGNMENT_AFTER_BAD_CHARACTER_STATE;
				}
				myTokenEnd = myTokenStart + 1;
			}
			else if(myCurrentState == WAITING_FOR_SPACE_AFTER_HEADER_NAME_STATE)
			{
				if(isSpace(myTokenStart))
				{
					myTokenType = ManifestTokenType.SIGNIFICANT_SPACE;
				}
				else
				{
					myTokenType = TokenType.BAD_CHARACTER;
				}
				myCurrentState = INITIAL_STATE;
				myTokenEnd = myTokenStart + 1;
			}
			else if(isHeaderStart(myTokenStart))
			{
				if(isAlphaNum(myTokenStart))
				{
					myTokenEnd = myTokenStart + 1;
					while(myTokenEnd < myEndOffset && isHeaderChar(myTokenEnd))
					{
						myTokenEnd++;
					}
				}
				myTokenType = ManifestTokenType.HEADER_NAME;
				myCurrentState = WAITING_FOR_HEADER_ASSIGNMENT_STATE;
			}
			else if(isContinuationStart(myTokenStart))
			{
				myTokenType = ManifestTokenType.SIGNIFICANT_SPACE;
				myTokenEnd = myTokenStart + 1;
				myCurrentState = INITIAL_STATE;
			}
			else if(isSpecialCharacter(myTokenStart))
			{
				myTokenType = getToken(myTokenStart);
				myTokenEnd = myTokenStart + getTokenSize(myTokenType);
				myCurrentState = INITIAL_STATE;
			}
			else
			{
				myTokenEnd = myTokenStart;
				while(myTokenEnd < myEndOffset && !isSpecialCharacter(myTokenEnd) && !isNewline(myTokenEnd))
				{
					myTokenEnd++;
				}
				myTokenType = ManifestTokenType.HEADER_VALUE_PART;
			}
		}
		else
		{
			myTokenType = null;
			myTokenEnd = myTokenStart;
		}
	}
}
