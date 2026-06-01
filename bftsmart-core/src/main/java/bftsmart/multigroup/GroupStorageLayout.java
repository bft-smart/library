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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Ratis-style storage directory manager for multi-group deployments.
 *
 * <p>You provide a list of base directories (e.g. different disks or mount-points).
 * Each group always gets its own isolated sub-directory {@code <baseDir>/<groupId>/}
 * so two groups never collide even when only a single base dir is given.</p>
 *
 * <h3>Restart safety</h3>
 * <p>On construction the layout <b>scans</b> all base directories for existing
 * {@code <groupId>/} sub-directories. If a group's directory already exists (restart
 * scenario) it is reused in-place — the group always finds its state exactly where it
 * left it, regardless of the order in which groups are registered. Only truly new groups
 * (no existing directory found) are assigned a base dir in round-robin order to keep
 * disk usage balanced.</p>
 *
 * <p>Example — four groups balanced across two disks (first run):</p>
 * <pre>{@code
 * GroupStorageLayout layout = new GroupStorageLayout("/data/disk1", "/data/disk2");
 * layout.dirFor(0);  // new group → /data/disk1/0/  (round-robin slot 0)
 * layout.dirFor(1);  // new group → /data/disk2/1/  (round-robin slot 1)
 * layout.dirFor(2);  // new group → /data/disk1/2/  (round-robin slot 2)
 * }</pre>
 *
 * <p>On restart (directories already exist, scan finds them):</p>
 * <pre>{@code
 * GroupStorageLayout layout = new GroupStorageLayout("/data/disk1", "/data/disk2");
 * // scan finds /data/disk1/0/, /data/disk2/1/, /data/disk1/2/
 * layout.dirFor(2);  // existing → /data/disk1/2/  (no round-robin consumed)
 * layout.dirFor(0);  // existing → /data/disk1/0/  (no round-robin consumed)
 * layout.dirFor(1);  // existing → /data/disk2/1/  (no round-robin consumed)
 * }</pre>
 */
public final class GroupStorageLayout {

    /** Default base dir used when none is supplied (mirrors DiskStateLog.DEFAULT_DIR). */
    public static final String DEFAULT_BASE = "files" + File.separator;

    private final String[] baseDirs;
    // Pre-populated by the constructor scan; also receives new-group assignments.
    private final ConcurrentHashMap<Integer, String> assigned = new ConcurrentHashMap<>();
    // Round-robin counter used only for groups that have no existing directory.
    private final AtomicInteger rrCounter = new AtomicInteger(0);

    /**
     * Creates a layout that balances groups across the given base directories and
     * immediately scans them for existing group sub-directories.
     *
     * @param baseDirs one or more base directories; trailing separators are normalised
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
        scanExisting();
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
     * Returns the storage directory for {@code groupId}.
     * <ul>
     *   <li>If the directory was found during the startup scan (restart), it is returned
     *       immediately — no round-robin slot is consumed.</li>
     *   <li>If this is a brand-new group, a base directory is chosen in round-robin order
     *       and {@code <baseDir>/<groupId>/} is created on the filesystem.</li>
     * </ul>
     *
     * @return path ending with a file separator, e.g. {@code /data/disk1/3/}
     */
    public String dirFor(int groupId) {
        return assigned.computeIfAbsent(groupId, gid -> {
            // New group: pick the next base dir in round-robin order.
            int slot = rrCounter.getAndIncrement();
            String base = baseDirs[slot % baseDirs.length];
            String dir = base + gid + File.separator;
            File f = new File(dir);
            if (!f.exists()) {
                f.mkdirs();
            }
            return dir;
        });
    }

    /**
     * Returns a snapshot of all group-id → directory mappings discovered by the startup
     * scan. Useful for logging or for a host that wants to know which groups already have
     * persisted state before registering them.
     */
    public Map<Integer, String> discoveredGroups() {
        return new HashMap<>(assigned);
    }

    /** @return the base directories configured for this layout (defensive copy). */
    public String[] baseDirs() {
        return baseDirs.clone();
    }

    /**
     * Scans all base directories for sub-directories whose name is a valid integer
     * (i.e. a groupId). Found entries are pre-loaded into {@code assigned} so that
     * {@link #dirFor} returns the existing path without consuming a round-robin slot.
     * The round-robin counter is advanced past the number of discovered groups so that
     * any new groups are spread across the remaining baseDirs capacity.
     */
    private void scanExisting() {
        int maxSlot = 0;
        for (String base : baseDirs) {
            File baseFile = new File(base);
            if (!baseFile.isDirectory()) continue;
            File[] children = baseFile.listFiles(File::isDirectory);
            if (children == null) continue;
            for (File child : children) {
                try {
                    int gid = Integer.parseInt(child.getName());
                    String dir = child.getPath();
                    if (!dir.endsWith(File.separator) && !dir.endsWith("/")) {
                        dir = dir + File.separator;
                    }
                    assigned.putIfAbsent(gid, dir);
                    maxSlot++;
                } catch (NumberFormatException ignored) {
                    // sub-directory name is not a groupId; skip
                }
            }
        }
        // Advance round-robin so new groups don't overload the first base dir.
        if (maxSlot > 0) {
            rrCounter.set(maxSlot);
        }
    }
}
