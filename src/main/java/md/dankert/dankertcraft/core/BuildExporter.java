package md.dankert.dankertcraft.core;

/**
 * Deprecated wrapper for BuildExporter — delegates to BuildExporterV2
 */
public class BuildExporter {

    public static java.io.File exportBuildAsZip(String workDir, String instanceName) {
        return BuildExporterV2.exportBuildAsZip(workDir, instanceName);
    }

    public static boolean importBuildFromZip(String workDir, java.io.File zipFile) {
        return BuildExporterV2.importBuildFromZip(workDir, zipFile);
    }

    public static java.io.File exportBuild(String workDir, String instanceName) {
        // Keep compatibility for older .dkbuild format by calling V2 export
        return BuildExporterV2.exportBuildAsZip(workDir, instanceName);
    }

    public static boolean importBuild(String workDir, java.io.File file) {
        return BuildExporterV2.importBuildFromZip(workDir, file);
    }
}
