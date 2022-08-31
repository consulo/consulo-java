/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.java.impl.psi.impl.source.codeStyle.javadoc;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.intellij.util.containers.ContainerUtilRt;

/**
 * Method comment
 *
 * @author Dmitry Skavish
 */
public class JDMethodComment extends JDParamListOwnerComment
{
	private String myReturnTag;
	private List<TagDescription> myThrowsList;

	public JDMethodComment(@Nonnull CommentFormatter formatter)
	{
		super(formatter);
	}

	@Override
	protected void generateSpecial(@Nonnull String prefix, @Nonnull StringBuilder sb)
	{
		super.generateSpecial(prefix, sb);

		if(myReturnTag != null)
		{
			if(myFormatter.getSettings().JD_KEEP_EMPTY_RETURN || !myReturnTag.trim().isEmpty())
			{
				JDTag tag = JDTag.RETURN;
				sb.append(myFormatter.getParser().formatJDTagDescription(myReturnTag,
						prefix + tag.getWithEndWhitespace(),
						prefix + javadocContinuationIndent()));

				if(myFormatter.getSettings().JD_ADD_BLANK_AFTER_RETURN)
				{
					sb.append(prefix);
					sb.append('\n');
				}
			}
		}

		if(myThrowsList != null)
		{
			JDTag tag = myFormatter.getSettings().JD_USE_THROWS_NOT_EXCEPTION ? JDTag.THROWS : JDTag.EXCEPTION;
			generateList(prefix, sb, myThrowsList, tag.getWithEndWhitespace(),
					myFormatter.getSettings().JD_ALIGN_EXCEPTION_COMMENTS,
					myFormatter.getSettings().JD_KEEP_EMPTY_EXCEPTION,
					myFormatter.getSettings().JD_PARAM_DESCRIPTION_ON_NEW_LINE
			);
		}
	}

	public void setReturnTag(@Nonnull String returnTag)
	{
		this.myReturnTag = returnTag;
	}

	public void addThrow(@Nonnull String className, @Nullable String description)
	{
		if(myThrowsList == null)
		{
			myThrowsList = ContainerUtilRt.newArrayList();
		}
		myThrowsList.add(new TagDescription(className, description));
	}
}
