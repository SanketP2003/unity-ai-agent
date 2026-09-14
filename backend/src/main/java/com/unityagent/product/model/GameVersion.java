package com.unityagent.product.model;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Immutable Semantic Version representation (MAJOR.MINOR.PATCH).
 */
public final class GameVersion implements Comparable<GameVersion> {

    private static final Pattern SEMVER_PATTERN = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)(?:-([a-zA-Z0-9.-]+))?$");

    private final int major;
    private final int minor;
    private final int patch;
    private final String prerelease;

    public GameVersion(int major, int minor, int patch) {
        this(major, minor, patch, null);
    }

    public GameVersion(int major, int minor, int patch, String prerelease) {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException("Version numbers cannot be negative: " + major + "." + minor + "." + patch);
        }
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.prerelease = prerelease;
    }

    public static GameVersion parse(String versionStr) {
        if (versionStr == null) {
            throw new IllegalArgumentException("Version string cannot be null");
        }
        Matcher m = SEMVER_PATTERN.matcher(versionStr.trim());
        if (!m.matches()) {
            throw new IllegalArgumentException("Invalid semantic version format (expected MAJOR.MINOR.PATCH): '" + versionStr + "'");
        }
        return new GameVersion(
                Integer.parseInt(m.group(1)),
                Integer.parseInt(m.group(2)),
                Integer.parseInt(m.group(3)),
                m.group(4)
        );
    }

    public int getMajor() {
        return major;
    }

    public int getMinor() {
        return minor;
    }

    public int getPatch() {
        return patch;
    }

    public String getPrerelease() {
        return prerelease;
    }

    public GameVersion bumpMajor() {
        return new GameVersion(major + 1, 0, 0);
    }

    public GameVersion bumpMinor() {
        return new GameVersion(major, minor + 1, 0);
    }

    public GameVersion bumpPatch() {
        return new GameVersion(major, minor, patch + 1);
    }

    @Override
    public int compareTo(GameVersion o) {
        if (o == null) return 1;
        if (this.major != o.major) return Integer.compare(this.major, o.major);
        if (this.minor != o.minor) return Integer.compare(this.minor, o.minor);
        if (this.patch != o.patch) return Integer.compare(this.patch, o.patch);
        if (this.prerelease == null && o.prerelease != null) return 1;
        if (this.prerelease != null && o.prerelease == null) return -1;
        if (this.prerelease != null && o.prerelease != null) return this.prerelease.compareTo(o.prerelease);
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GameVersion that = (GameVersion) o;
        return major == that.major && minor == that.minor && patch == that.patch && Objects.equals(prerelease, that.prerelease);
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, patch, prerelease);
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch + (prerelease != null ? "-" + prerelease : "");
    }
}
