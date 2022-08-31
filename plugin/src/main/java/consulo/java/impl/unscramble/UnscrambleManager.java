/*
 * Copyright 2013-2017 consulo.io
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

package consulo.java.impl.unscramble;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import jakarta.inject.Singleton;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationActivationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.java.impl.unscramble.UnscrambleListener;
import com.intellij.util.messages.MessageBusConnection;

/**
 * @author VISTALL
 * @since 01-Nov-17
 */
@Singleton
public class UnscrambleManager
{
	private static final String KEY = "java.analyze.exceptions.on.the.fly";

	@Nonnull
	public static UnscrambleManager getInstance()
	{
		return ApplicationManager.getApplication().getComponent(UnscrambleManager.class);
	}

	@Nullable
	private MessageBusConnection myConnection;

	private final UnscrambleListener myListener = new UnscrambleListener();

	public UnscrambleManager()
	{
		updateConnection();
	}

	public boolean isEnabled()
	{
		return PropertiesComponent.getInstance().getBoolean(KEY, false);
	}

	public void setEnabled(boolean enabled)
	{
		PropertiesComponent.getInstance().setValue(KEY, enabled, false);

		updateConnection();
	}

	private void updateConnection()
	{
		final ApplicationEx app = ApplicationManagerEx.getApplicationEx();

		boolean value = PropertiesComponent.getInstance().getBoolean(KEY);
		if(value)
		{
			myConnection = app.getMessageBus().connect();
			myConnection.subscribe(ApplicationActivationListener.TOPIC, myListener);
		}
		else
		{
			if(myConnection != null)
			{
				myConnection.disconnect();
			}
		}
	}
}
