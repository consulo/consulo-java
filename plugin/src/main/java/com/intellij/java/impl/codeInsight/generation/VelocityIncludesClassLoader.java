/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInsight.generation;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;

import org.apache.velocity.exception.ResourceNotFoundException;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;

public class VelocityIncludesClassLoader extends ClasspathResourceLoader
{
	@Override
	public Reader getResourceReader(String name, String encoding) throws ResourceNotFoundException
	{
		InputStream stream = VelocityIncludesClassLoader.class.getResourceAsStream("/com/intellij/codeInsight/generation" + name);
		if(stream == null)
		{
			throw new ResourceNotFoundException(name);
		}

		try
		{
			return new InputStreamReader(stream, encoding);
		}
		catch(UnsupportedEncodingException e)
		{
			throw new ResourceNotFoundException(e);
		}
	}
}
