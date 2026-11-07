package net.kdt.pojavlaunch.modloaders.modpacks.api;

import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.utils.DownloadUtils;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CallerRunsPolicy;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class ModDownloader {
    private static final ThreadLocal<byte[]> sThreadLocalBuffer = new ThreadLocal<>();
    private final ThreadPoolExecutor mDownloadPool = new ThreadPoolExecutor(3, 3, 100, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(128), new CallerRunsPolicy());
    private final AtomicBoolean mTerminator = new AtomicBoolean(false);
    private final AtomicLong mDownloadSize = new AtomicLong(0);
    private final Object mExceptionSyncPoint = new Object();
    private final File mDestinationDirectory;
    private final boolean mUseFileCount;
    private IOException mFirstIOException;
    private long mTotalSize;

    public ModDownloader(File destinationDirectory) {
        this(destinationDirectory, false);
    }

    public ModDownloader(File destinationDirectory, boolean useFileCount) {
        this.mDestinationDirectory = destinationDirectory;
        this.mUseFileCount = useFileCount;
    }

    public void submitDownload(int fileSize, String relativePath, @Nullable String downloadHash, String... url) {
        File target = resolveDestination(relativePath);
        if (url == null || url.length == 0) throw new IllegalArgumentException("No download URL provided");
        if(mUseFileCount) mTotalSize += 1;
        else mTotalSize += Math.max(0, fileSize);
        mDownloadPool.execute(new DownloadTask(url, target, downloadHash));
    }

    public void submitDownload(FileInfoProvider infoProvider) {
        if(!mUseFileCount) throw new RuntimeException("This method can only be used in a file-counting ModDownloader");
        if (infoProvider == null) throw new IllegalArgumentException("File info provider is missing");
        mTotalSize += 1;
        mDownloadPool.execute(new FileInfoQueryTask(infoProvider));
    }

    public void awaitFinish(Tools.DownloaderFeedback feedback) throws IOException {
        mDownloadPool.shutdown();
        try {
            while (!mDownloadPool.awaitTermination(100, TimeUnit.MILLISECONDS)) {
                if (mTerminator.get()) {
                    mDownloadPool.shutdownNow();
                    break;
                }
                if (feedback != null) {
                    feedback.updateProgress((int) mDownloadSize.get(), (int) mTotalSize);
                }
            }
        } catch (InterruptedException interrupted) {
            mDownloadPool.shutdownNow();
            Thread.currentThread().interrupt();
            InterruptedIOException error = new InterruptedIOException("Mod download was interrupted");
            error.initCause(interrupted);
            throw error;
        }
        if (mTerminator.get()) {
            synchronized (mExceptionSyncPoint) {
                if (mFirstIOException != null) throw mFirstIOException;
            }
            throw new IOException("Mod download stopped after an unexpected error");
        }
    }

    private File resolveDestination(String relativePath) {
        if (relativePath == null || relativePath.trim().isEmpty()) {
            throw new IllegalArgumentException("Invalid modpack file path");
        }
        try {
            File root = mDestinationDirectory.getCanonicalFile();
            File target = new File(root, relativePath.replace('\\', '/')).getCanonicalFile();
            String rootPath = root.getPath() + File.separator;
            if (!target.getPath().startsWith(rootPath)) {
                throw new IllegalArgumentException("Modpack file path escapes the instance directory");
            }
            return target;
        } catch (IOException error) {
            throw new IllegalArgumentException("Unable to validate modpack file path", error);
        }
    }

    private static byte[] getThreadLocalBuffer() {
        byte[] buffer = sThreadLocalBuffer.get();
        if(buffer != null) return buffer;
        buffer = new byte[8192];
        sThreadLocalBuffer.set(buffer);
        return buffer;
    }

    private void downloadFailed(IOException exception) {
        mTerminator.set(true);
        synchronized (mExceptionSyncPoint) {
            if(mFirstIOException == null) {
                mFirstIOException = exception;
                mExceptionSyncPoint.notify();
            }
        }
    }

    class FileInfoQueryTask implements Runnable {
        private final FileInfoProvider mFileInfoProvider;
        public FileInfoQueryTask(FileInfoProvider fileInfoProvider) {
            this.mFileInfoProvider = fileInfoProvider;
        }
        @Override
        public void run() {
            try {
                FileInfo fileInfo = mFileInfoProvider.getFileInfo();
                if(fileInfo == null) return;
                if (fileInfo.url == null || !fileInfo.url.startsWith("https://")) {
                    throw new IOException("Modpack contains an invalid download URL");
                }
                new DownloadTask(new String[]{fileInfo.url},
                        resolveDestination(fileInfo.relativePath), fileInfo.sha1).run();
            }catch (IOException e) {
                downloadFailed(e);
            }
        }
    }

    class DownloadTask implements Runnable, Tools.DownloaderFeedback {
        private final String[] mDownloadUrls;
        private final File mDestination;
        private final String mSha1;
        private int last = 0;

        public DownloadTask(String[] downloadurls,
                            File downloadDestination, String downloadHash) {
            this.mDownloadUrls = downloadurls;
            this.mDestination = downloadDestination;
            this.mSha1 = downloadHash;
        }

        @Override
        public void run() {
            for(String sourceUrl : mDownloadUrls) {
                if (mTerminator.get() || Thread.currentThread().isInterrupted()) return;
                if (sourceUrl == null || !sourceUrl.startsWith("https://")) {
                    downloadFailed(new IOException("Modpack contains an invalid download URL"));
                    return;
                }
                try {
                    DownloadUtils.ensureSha1(mDestination, mSha1, (Callable<Void>) () -> {
                        IOException exception = tryDownload(sourceUrl);
                        if(exception != null) {
                            throw exception;
                        }
                        return null;
                    });

                }catch (IOException e) {
                    downloadFailed(e);
                }
            }
        }

        private IOException tryDownload(String sourceUrl) throws InterruptedException {
            IOException exception = null;
            for (int i = 0; i < 5; i++) {
                try {
                    DownloadUtils.downloadFileMonitored(sourceUrl, mDestination, getThreadLocalBuffer(), this);
                    if(mUseFileCount) mDownloadSize.addAndGet(1);
                    return null;
                } catch (InterruptedIOException e) {
                    throw new InterruptedException();
                } catch (IOException e) {
                    exception = e;
                }
                if(!mUseFileCount) {
                    mDownloadSize.addAndGet(-last);
                    last = 0;
                }
            }
            return exception;
        }

        @Override
        public void updateProgress(int curr, int max) {
            if(mUseFileCount) return;
            mDownloadSize.addAndGet(curr - last);
            last = curr;
        }
    }

    public static class FileInfo {
        public final String url;
        public final String relativePath;
        public final String sha1;

        public FileInfo(String url, String relativePath, @Nullable String sha1) {
            this.url = url;
            this.relativePath = relativePath;
            this.sha1 = sha1;
        }
    }

    public interface FileInfoProvider {
        FileInfo getFileInfo() throws IOException;
    }
}
