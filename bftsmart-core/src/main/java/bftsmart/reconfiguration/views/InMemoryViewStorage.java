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

/**
 * In-memory {@link ViewStorage}: the current view is held in a field, nothing is read
 * from or written to disk.
 *
 * <p>This is what makes it possible to host several independent consensus groups inside
 * a single JVM: each group gets its own {@code InMemoryViewStorage} instance, so they no
 * longer collide on the shared {@code config/currentView} file used by
 * {@link DefaultViewStorage}. It is also handy for fully file-less (programmatic) setups
 * and for tests.</p>
 */
public final class InMemoryViewStorage implements ViewStorage {

    private volatile View currentView;

    public InMemoryViewStorage() {
    }

    public InMemoryViewStorage(View initialView) {
        this.currentView = initialView;
    }

    @Override
    public synchronized boolean storeView(View view) {
        this.currentView = view;
        return true;
    }

    @Override
    public View readView() {
        return currentView;
    }
}
