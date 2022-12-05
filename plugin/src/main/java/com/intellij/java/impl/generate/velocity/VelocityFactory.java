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
package com.intellij.java.impl.generate.velocity;

import java.util.Properties;

import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import com.intellij.java.impl.codeInsight.generation.VelocityIncludesClassLoader;

/**
 * Velocity factory.
 * <p/>
 * Creating instances of the VelocityEngine.
 */
public class VelocityFactory
{
	private static class Holder
	{
		private static final VelocityEngine engine = newVeloictyEngine();
	}

	/**
	 * Privte constructor.
	 */
	private VelocityFactory()
	{
	}

	/**
	 * Returns a new instance of the VelocityEngine.
	 * <p/>
	 * The engine is initialized and outputs its logging to IDEA logging.
	 *
	 * @return a new velocity engine that is initialized.
	 */
	private static VelocityEngine newVeloictyEngine()
	{
		Properties prop = new Properties();
		prop.setProperty("runtime.log.logsystem.log4j.category", "GenerateToString");
		prop.setProperty(RuntimeConstants.RESOURCE_LOADER, "includes");
		prop.put("includes.resource.loader.instance", new VelocityIncludesClassLoader());
		VelocityEngine velocity = new VelocityEngine(prop);
		velocity.init();
		return velocity;
	}

	/**
	 * Get's a shared instance of the VelocityEngine.
	 * <p/>
	 * The engine is initialized and outputs its logging to IDEA logging.
	 *
	 * @return a shared instance of the engine that is initialized.
	 */
	public static VelocityEngine getVelocityEngine()
	{
		return Holder.engine;
	}
}