/**
Copyright (c) 2007-2013 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and the authors indicated in the @author tags

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package bftsmart.reconfiguration.views;

import java.util.function.Function;

/**
 * Programmatic registry for the {@link ViewStorage} implementation used to persist the
 * current view.
 *
 * <p>This replaces the previous reflection-based plug-in mechanism (the
 * {@code view.storage.handler} configuration property resolved through
 * {@code Class.forName(...).newInstance()}). Wiring is now fully explicit and
 * reflection-free: by default {@link DefaultViewStorage} is used; an application that
 * needs a custom storage registers a factory once during start-up:</p>
 *
 * <pre>{@code
 * ViewStorageProvider.setFactory(configHome -> new MyViewStorage(configHome));
 * }</pre>
 *
 * <p>The factory receives the configuration home directory (which may be an empty
 * string) so that file-based implementations such as {@link DefaultViewStorage} can
 * locate their files.</p>
 */
public final class ViewStorageProvider {

    private static volatile Function<String, ViewStorage> factory;

    private ViewStorageProvider() {
    }

    /**
     * Registers the factory used to create the {@link ViewStorage}.
     *
     * @param factory maps the configuration home directory to a {@link ViewStorage}
     *                (must not be {@code null})
     */
    public static void setFactory(Function<String, ViewStorage> factory) {
        if (factory == null) {
            throw new IllegalArgumentException("ViewStorage factory must not be null");
        }
        ViewStorageProvider.factory = factory;
    }

    /**
     * Creates a {@link ViewStorage}. Uses the registered factory if any, otherwise
     * falls back to {@link DefaultViewStorage}.
     *
     * @param configHome the configuration home directory
     * @return a new {@link ViewStorage}
     */
    public static ViewStorage newViewStorage(String configHome) {
        Function<String, ViewStorage> f = factory;
        if (f == null) {
            f = DefaultViewStorage::new;
        }
        return f.apply(configHome);
    }
}
