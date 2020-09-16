/*
 * Copyright 2016 Martin Winandy
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.tinylog.configuration;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.regex.Pattern;

import org.tinylog.Level;
import org.tinylog.provider.InternalLogger;
import org.tinylog.runtime.RuntimeProvider;

/**
 * Standard internal property loader for tinylog.
 *
 * <p>
 * By default, the configuration will be loaded from {@code tinylog.properties} in the default package. Another
 * configuration file can be loaded by setting the system property {@code tinylog.configuration}. The configuration file
 * can be a resource in the classpath, a file from file system or an URL
 * </p>
 *
 * <p>
 * Alternately configuration properties can be set via system properties. These properties must be prefixed by
 * "{@code tinylog.}". For example: "{@code level = debug}" becomes "{@code tinylog.level=debug}". If a configuration
 * property exists as system property and in configuration file, the system property will win.
 * </p>
 */
public class PropertyConfigurationLoader implements ConfigurationLoader {
	private static final String[] CONFIGURATION_FILES = new String[] {
		"tinylog-dev.properties",
		"tinylog-test.properties",
		"tinylog.properties"
	};
	
	private static final String PROPERTIES_PREFIX = "tinylog.";
	private static final String CONFIGURATION_PROPERTY = PROPERTIES_PREFIX + "configuration";
	private static final Pattern URL_DETECTION_PATTERN = Pattern.compile("^[a-zA-Z]{2,}:/.*");
	
	/** */
	public PropertyConfigurationLoader() {
		
	}
	
	/**
	 * Loads all configuration properties.
	 *
	 * @return Found properties
	 */
	@Override
	public Properties load() {
		Properties properties = new Properties();

		String file = System.getProperty(CONFIGURATION_PROPERTY);
		InputStream stream = null;
		try {
			if (file != null) {
				if (URL_DETECTION_PATTERN.matcher(file).matches()) {
					stream = new URL(file).openStream();
				} else {
					stream = RuntimeProvider.getClassLoader().getResourceAsStream(file);
					if (stream == null) {
						stream = new FileInputStream(file);
					}
				}
				load(properties, stream);
			} else {
				for (String configurationFile : getConfigurationFiles()) {
					file = configurationFile;
					stream = RuntimeProvider.getClassLoader().getResourceAsStream(file);
					if (stream != null) {
						load(properties, stream);
						break;
					}
				}
			}
		} catch (IOException ex) {
			InternalLogger.log(Level.ERROR, "Failed loading configuration from '" + file + "'");
		} finally {
			if (stream != null) {
				try {
					stream.close();
				} catch (IOException ex) {
					// Do nothing
				}
			}
		}

		for (Object key : new ArrayList<Object>(System.getProperties().keySet())) {
			String name = (String) key;
			if (name.startsWith(PROPERTIES_PREFIX)) {
				properties.put(name.substring(PROPERTIES_PREFIX.length()), System.getProperty(name));
			}
		}

		for (Entry<Object, Object> entry : properties.entrySet()) {
			String value = (String) entry.getValue();
			if (value.indexOf('{') != -1) {
				value = Configuration.resolve(value, EnvironmentVariableResolver.INSTANCE);
				value = Configuration.resolve(value, SystemPropertyResolver.INSTANCE);
				properties.put(entry.getKey(), value);
			}
		}

		return properties;
	}
	
	/**
	 * Puts all properties from a stream to an existing properties object. Already existing properties will be
	 * overridden.
	 *
	 * @param properties
	 *            Read properties will be put to this properties object
	 * @param stream
	 *            Input stream with a properties file
	 * @throws IOException
	 *             Failed reading properties from input stream
	 */
	protected void load(final Properties properties, final InputStream stream) throws IOException {
		properties.load(stream);
	}
	
	/**
	 * Retrieve an array of configuration files. An attempt is made to load the properties from these files in the 
	 * given sequence.
	 * 
	 *  @return The configuration files
	 */
	protected String[] getConfigurationFiles() {
		return CONFIGURATION_FILES;
	}
}
