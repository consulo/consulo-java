/*
 * Copyright 2001-2007 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.impl.generate.exception;

/**
 * Template resource related exceptions.
 * <p/>
 * Usually error loading or saving template resources.
 */
public class TemplateResourceException extends PluginException
{

	/**
	 * Create template exception (error saving template, loading template etc.)
	 *
	 * @param msg   message description.
	 * @param cause the caused exception.
	 */
	public TemplateResourceException(String msg, Throwable cause)
	{
		super(msg, cause);
	}

}
