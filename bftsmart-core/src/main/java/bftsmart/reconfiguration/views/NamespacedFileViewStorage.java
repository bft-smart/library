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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import bftsmart.tom.util.io.StateCodecs;

/**
 * Durable, per-group {@link ViewStorage}: like {@link DefaultViewStorage} it persists the
 * current view with the binary codec, but the file name is namespaced
 * ({@code currentView.<namespace>}) so that several consensus groups hosted in the same
 * directory (and the same JVM) each keep their own persisted view without colliding.
 *
 * <p>This is the production-grade counterpart of {@link InMemoryViewStorage} for the
 * multi-group setup: a restarting replica recovers the last view (i.e. the
 * reconfigurations) of each group it belongs to.</p>
 */
public final class NamespacedFileViewStorage implements ViewStorage {

    private final String path;

    /**
     * @param configPath the directory holding the view files (created if absent)
     * @param namespace  a per-group identifier (e.g. the group id); appended to the file name
     */
    public NamespacedFileViewStorage(String configPath, String namespace) {
        String dir = (configPath == null || configPath.isEmpty()) ? "config" : configPath;
        File f = new File(dir);
        if (!f.exists()) {
            f.mkdirs();
        }
        this.path = dir + System.getProperty("file.separator") + "currentView." + namespace;
    }

    @Override
    public synchronized boolean storeView(View view) {
        if (view.equals(readView())) {
            return true;
        }
        try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(new File(path)))) {
            StateCodecs.writeView(view, dos);
            dos.flush();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public synchronized View readView() {
        File f = new File(path);
        if (!f.exists()) {
            return null;
        }
        try (DataInputStream dis = new DataInputStream(new FileInputStream(f))) {
            return StateCodecs.readView(dis);
        } catch (Exception e) {
            return null;
        }
    }
}
