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
package bftsmart.multigroup;

import java.io.File;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Ratis-style storage directory manager for multi-group deployments.
 *
 * <p>You provide a list of base directories (e.g. different disks or mount-points).
 * As groups are registered, each group is assigned to one base dir in round-robin order,
 * and always gets its own isolated sub-directory {@code <baseDir>/<groupId>/} so two groups
 * never collide even when only a single base dir is given.</p>
 *
 * <p>Example — four groups balanced across two disks:</p>
 * <pre>{@code
 * GroupStorageLayout layout = new GroupStorageLayout("/data/disk1", "/data/disk2");
 * // group 0 → /data/disk1/0/
 * // group 1 → /data/disk2/1/
 * // group 2 → /data/disk1/2/
 * // group 3 → /data/disk2/3/
 * }</pre>
 *
 * <p>Assigning a single base dir is also valid and still gives per-group isolation:</p>
 * <pre>{@code
 * GroupStorageLayout layout = new GroupStorageLayout("data/state");
 * // group 0 → data/state/0/
 * // group 1 → data/state/1/
 * }</pre>
 *
 * <p>The class is thread-safe. Calling {@link #dirFor(int)} twice for the same group
 * returns the same path and does NOT consume a new slot — allocation is idempotent.</p>
 */
public final class GroupStorageLayout {

    /** Default base dir used when none is supplied (mirrors DiskStateLog.DEFAULT_DIR). */
    public static final String DEFAULT_BASE = "files" + File.separator;

    private final String[] baseDirs;
    private final AtomicInteger counter = new AtomicInteger(0);
    private final ConcurrentHashMap<Integer, String> assigned = new ConcurrentHashMap<>();

    /**
     * Creates a layout that balances groups across the given base directories.
     * At least one directory must be provided.
     *
     * @param baseDirs one or more base directories; trailing separators are added automatically
     */
    public GroupStorageLayout(String... baseDirs) {
        if (baseDirs == null || baseDirs.length == 0) {
            throw new IllegalArgumentException("At least one base directory must be provided");
        }
        this.baseDirs = new String[baseDirs.length];
        for (int i = 0; i < baseDirs.length; i++) {
            String d = baseDirs[i];
            if (!d.endsWith(File.separator) && !d.endsWith("/")) {
                d = d + File.separator;
            }
            this.baseDirs[i] = d;
        }
    }

    /** Creates a single-directory layout (per-group sub-directories are still created). */
    public GroupStorageLayout(String baseDir) {
        this(new String[]{baseDir});
    }

    /** Default layout: uses {@value #DEFAULT_BASE} as the single base directory. */
    public static GroupStorageLayout defaultLayout() {
        return new GroupStorageLayout(DEFAULT_BASE);
    }

    /**
     * Returns the storage directory assigned to {@code groupId}, creating the directory
     * on the filesystem if it does not yet exist. Repeated calls for the same group always
     * return the same path (idempotent).
     *
     * @param groupId the consensus group id
     * @return an absolute-or-relative path ending with a file separator, e.g. {@code /data/disk1/3/}
     */
    public String dirFor(int groupId) {
        return assigned.computeIfAbsent(groupId, gid -> {
            int slot = counter.getAndIncrement();
            String base = baseDirs[slot % baseDirs.length];
            String dir = base + gid + File.separator;
            File f = new File(dir);
            if (!f.exists()) {
                f.mkdirs();
            }
            return dir;
        });
    }

    /** @return the base directories configured for this layout (defensive copy). */
    public String[] baseDirs() {
        return baseDirs.clone();
    }
}
