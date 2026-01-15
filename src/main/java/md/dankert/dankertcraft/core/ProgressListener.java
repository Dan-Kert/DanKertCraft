package md.dankert.dankertcraft.core;

public interface ProgressListener {
    void onProgress(String stage, int current, int total, long bytesDownloaded);
}