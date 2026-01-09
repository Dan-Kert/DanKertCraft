package md.dankert.dankertcraft;

import java.util.List;
import java.util.Map;

/**
 * Единый класс для всех моделей данных Minecraft (JSON структуры)
 */
public class VersionData {
    public String id;
    public String mainClass;
    public String assets;
    public String minecraftArguments;
    public Arguments arguments;
    public AssetIndex assetIndex;
    public List<Library> libraries;
    public Downloads downloads;
    public JavaVersion javaVersion;

    public static class Arguments {
        public List<Object> game;
        public List<Object> jvm;
    }

    public static class AssetIndex {
        public String id;
        public String url;
        public String sha1;
    }

    public static class Downloads {
        public DownloadFile client;
        public DownloadFile server;
    }

    public static class DownloadFile {
        public String url;
        public String sha1;
        public long size;
    }

    public static class Library {
        public String name;
        public String url;
        public LibraryDownloads downloads;
        public List<Rule> rules;
    }

    public static class LibraryDownloads {
        public Artifact artifact;
        public Map<String, Artifact> classifiers;
    }

    public static class Artifact {
        public String path;
        public String url;
        public String sha1;
        public long size;
    }

    public static class Rule {
        public String action;
        public OSRule os;
    }

    public static class OSRule {
        public String name;
    }

    public static class JavaVersion {
        public String component;
        public int majorVersion;
    }

    public static class Manifest {
        public Latest latest;
        public List<Version> versions;

        public static class Latest {
            public String release;
            public String snapshot;
        }

        public static class Version {
            public String id;
            public String type;
            public String url;
        }
    }
}