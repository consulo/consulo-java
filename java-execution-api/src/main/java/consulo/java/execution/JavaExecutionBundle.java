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

package consulo.java.execution;

import consulo.annotation.DeprecationInfo;
import consulo.annotation.internal.MigratedExtensionsTo;
import consulo.java.execution.localize.JavaExecutionLocalize;
import org.jetbrains.annotations.PropertyKey;
import consulo.component.util.localize.AbstractBundle;

/**
 * @author VISTALL
 * @since 27-Dec-16
 */
@Deprecated
@DeprecationInfo("Use JavaExecutionLocalize")
@MigratedExtensionsTo(JavaExecutionLocalize.class)
public class JavaExecutionBundle extends AbstractBundle
{
	private static final JavaExecutionBundle ourInstance = new JavaExecutionBundle();

	private JavaExecutionBundle()
	{
		super("messages.JavaExecutionBundle");
	}

	public static String message(@PropertyKey(resourceBundle = "messages.JavaExecutionBundle") String key)
	{
		return ourInstance.getMessage(key);
	}

	public static String message(@PropertyKey(resourceBundle = "messages.JavaExecutionBundle") String key, Object... params)
	{
		return ourInstance.getMessage(key, params);
	}
}
