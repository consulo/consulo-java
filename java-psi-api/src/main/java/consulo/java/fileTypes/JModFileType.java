/*
 * Copyright 2013-2016 must-be.org
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

package consulo.java.fileTypes;

import org.jetbrains.annotations.NotNull;
import consulo.fileTypes.ArchiveFileType;

/**
 * @author VISTALL
 * @since 05-Dec-16.
 */
public class JModFileType extends ArchiveFileType
{
	public static final JModFileType INSTANCE = new JModFileType();

	@NotNull
	@Override
	public String getId()
	{
		return "JMOD_ARCHIVE";
	}

	@NotNull
	@Override
	public String getDefaultExtension()
	{
		return "jmod";
	}

	@NotNull
	@Override
	public String getProtocol()
	{
		return "jar";
	}
}
