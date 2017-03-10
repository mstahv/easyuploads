package org.vaadin.easyuploads;

import java.io.File;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.vaadin.easyuploads.MultiUpload.FileDetail;
import org.vaadin.easyuploads.UploadField.FieldType;
import org.vaadin.easyuploads.client.AcceptUtil;

import com.vaadin.event.dd.DragAndDropEvent;
import com.vaadin.event.dd.DropHandler;
import com.vaadin.event.dd.acceptcriteria.AcceptAll;
import com.vaadin.event.dd.acceptcriteria.AcceptCriterion;
import com.vaadin.server.StreamVariable;
import com.vaadin.server.StreamVariable.StreamingEndEvent;
import com.vaadin.server.StreamVariable.StreamingErrorEvent;
import com.vaadin.server.StreamVariable.StreamingProgressEvent;
import com.vaadin.server.StreamVariable.StreamingStartEvent;
import com.vaadin.server.WebBrowser;
import com.vaadin.shared.communication.PushMode;
import com.vaadin.shared.ui.ContentMode;
import com.vaadin.ui.Component;
import com.vaadin.ui.CssLayout;
import com.vaadin.ui.DragAndDropWrapper;
import com.vaadin.ui.DragAndDropWrapper.WrapperTransferable;
import com.vaadin.ui.Html5File;
import com.vaadin.ui.Label;
import com.vaadin.ui.Layout;
import com.vaadin.ui.Notification;
import com.vaadin.ui.ProgressBar;
import com.vaadin.ui.PushConfiguration;
import com.vaadin.ui.UI;
import com.vaadin.ui.VerticalLayout;

/**
 * MultiFileUpload makes it easier to upload multiple files. MultiFileUpload
 * releases upload button for new uploads immediately when a file is selected
 * (aka parallel uploads). It also displays progress indicators for pending
 * uploads.
 * <p>
 * MultiFileUpload always streams straight to files to keep memory consumption
 * low. To temporary files by default, but this can be overridden with
 * {@link #setFileFactory(FileFactory)} (eg. straight to target directory on the
 * server).
 * <p>
 * Developer handles uploaded files by implementing the abstract
 * {@link #handleFile(File, String, String, long)} method.
 * <p>
 * TODO Field version (type == Collection<File> or File where isDirectory() ==
 * true).
 * <p>
 * TODO a super progress indicator (total transferred per total, including
 * queued files)
 * <p>
 * TODO Time remaining estimates and current transfer rate
 *
 */
@SuppressWarnings("serial")
public abstract class MultiFileUpload extends CssLayout implements DropHandler {

    private Map<MultiUpload, Html5FileInputSettings> html5FileInputSettings = new LinkedHashMap<MultiUpload, Html5FileInputSettings>();

    private int maxFileSize = -1;
    private int maxFileCount = -1;
    private String acceptString = null;
    private List<String> acceptStrings = new ArrayList<String>();
    private int acceptedUploads = 0;

    private Layout progressBars;
    protected CssLayout uploads = new CssLayout() {

        @Override
        protected String getCss(Component c) {
            if (getComponent(uploads.getComponentCount() - 1) != c) {
                return "overflow: hidden; position: absolute;";
            }
            return "overflow:hidden;";
        }

    };
    private String uploadButtonCaption = "...";
    private String areatext = "<small>DROP<br/>FILES</small>";
    private Integer savedPollInterval = null;

    public MultiFileUpload() {
        setWidth("200px");
    }

    protected void initialize() {
        addComponent(getprogressBarsLayout());
        uploads.setStyleName("v-multifileupload-uploads");
        addComponent(uploads);
        prepareUpload();
        if (supportsFileDrops()) {
            prepareDropZone();
        }
    }

    protected Layout getprogressBarsLayout() {
        if (progressBars == null) {
            progressBars = new VerticalLayout();
        }
        return progressBars;
    }

    private void prepareUpload() {
        final FileBuffer receiver = createReceiver();

        final MultiUpload upload = new MultiUpload();
        getHtml5FileInputSettings(upload);
        MultiUploadHandler handler = new MultiUploadHandler() {
            private LinkedList<ProgressBar> indicators;

            @Override
			public void streamingStarted(StreamingStartEvent event) {
                if (maxFileSize > 0 && event.getContentLength() > maxFileSize) {
                    throw new MaxFileSizeExceededException(
                            event.getContentLength(), maxFileSize);
                }
            }

            @Override
			public void streamingFinished(StreamingEndEvent event) {
                if (!indicators.isEmpty()) {
                    getprogressBarsLayout()
                            .removeComponent(indicators.remove(0));
                }
                File file = receiver.getFile();
                handleFile(file, event.getFileName(), event.getMimeType(),
                        event.getBytesReceived());
                receiver.setValue(null);
                if (upload.getPendingFileNames().isEmpty()) {
                    uploads.removeComponent(upload);
                    html5FileInputSettings.remove(upload);
                }
                resetPollIntervalIfNecessary();
            }

            @Override
			public void streamingFailed(StreamingErrorEvent event) {
                Logger.getLogger(getClass().getName()).log(Level.FINE,
                        "Streaming failed", event.getException());

                for (ProgressBar progressIndicator : indicators) {
                    getprogressBarsLayout().removeComponent(progressIndicator);
                    --acceptedUploads;
                }
                resetPollIntervalIfNecessary();
            }

            @Override
			public void onProgress(StreamingProgressEvent event) {
                long readBytes = event.getBytesReceived();
                long contentLength = event.getContentLength();
                float f = (float) readBytes / (float) contentLength;
                indicators.get(0).setValue(f);
            }

            @Override
			public OutputStream getOutputStream() {
                FileDetail next = upload.getPendingFileNames().iterator()
                        .next();
                return receiver.receiveUpload(next.getFileName(),
                        next.getMimeType());
            }

            @Override
			public void filesQueued(Collection<FileDetail> pendingFileNames) {
                if (indicators == null) {
                    indicators = new LinkedList<ProgressBar>();
                }
                acceptedUploads += pendingFileNames.size();
                for (FileDetail f : pendingFileNames) {
                    ensurePushOrPollingIsEnabled();
                    ProgressBar pi = createProgressIndicator();
                    getprogressBarsLayout().addComponent(pi);
                    pi.setCaption(f.getFileName());
                    pi.setVisible(true);
                    indicators.add(pi);
                }
                upload.setHeight("0px");
                prepareUpload();
            }

            @Override
            public boolean isInterrupted() {
                return false;

            }
        };
        upload.setHandler(handler);
        upload.setButtonCaption(getUploadButtonCaption());
        uploads.addComponent(upload);

    }

    protected void ensurePushOrPollingIsEnabled() {
        UI current = UI.getCurrent();
        PushConfiguration pushConfiguration = current.getPushConfiguration();
        PushMode pushMode = pushConfiguration.getPushMode();
        if (pushMode != PushMode.AUTOMATIC) {
            int currentPollInterval = current.getPollInterval();
            if (currentPollInterval == -1 || currentPollInterval > 1000) {
                savedPollInterval = currentPollInterval;
                current.setPollInterval(getPollInterval());
            }
        }
    }

    protected void resetPollIntervalIfNecessary() {
        if (savedPollInterval != null
                && getprogressBarsLayout().getComponentCount() == 0) {
            UI current = UI.getCurrent();
            current.setPollInterval(savedPollInterval);
            savedPollInterval = null;
        }
    }

    protected ProgressBar createProgressIndicator() {
        ProgressBar progressIndicator = new ProgressBar();
        progressIndicator.setWidth("100%");
        progressIndicator.setValue(0f);
        return progressIndicator;
    }

    public String getUploadButtonCaption() {
        return uploadButtonCaption;
    }

    public void setUploadButtonCaption(String uploadButtonCaption) {
        this.uploadButtonCaption = uploadButtonCaption;
        Iterator<Component> componentIterator = uploads.getComponentIterator();
        while (componentIterator.hasNext()) {
            Component next = componentIterator.next();
            if (next instanceof MultiUpload) {
                MultiUpload upload = (MultiUpload) next;
                if (upload.isVisible()) {
                    upload.setButtonCaption(getUploadButtonCaption());
                }
            }
        }
    }

    private FileFactory fileFactory;

    public FileFactory getFileFactory() {
        if (fileFactory == null) {
            fileFactory = new TempFileFactory();
        }
        return fileFactory;
    }

    public void setFileFactory(FileFactory fileFactory) {
        this.fileFactory = fileFactory;
    }

    protected FileBuffer createReceiver() {
        FileBuffer receiver = new FileBuffer(FieldType.FILE) {
            @Override
            public FileFactory getFileFactory() {
                return MultiFileUpload.this.getFileFactory();
            }

            @Override
            public void setLastMimeType(String mimeType) {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            @Override
            public void setLastFileName(String fileName) {
                throw new UnsupportedOperationException("Not supported yet.");
            }
        };
        return receiver;
    }

    protected int getPollInterval() {
        return 500;
    }

    @Override
    public void attach() {
        super.attach();
        if (getComponentCount() == 0) {
            initialize();
        }
    }

    protected DragAndDropWrapper dropZone;

    /**
     * Sets up DragAndDropWrapper to accept multi file drops.
     */
    protected void prepareDropZone() {
        if (dropZone == null) {
            Component label = new Label(getAreaText(), ContentMode.HTML);
            label.setSizeUndefined();
            dropZone = new DragAndDropWrapper(label);
            dropZone.setStyleName("v-multifileupload-dropzone");
            dropZone.setSizeUndefined();
            addComponent(dropZone, 1);
            dropZone.setDropHandler(this);
            addStyleName("no-horizontal-drag-hints");
            addStyleName("no-vertical-drag-hints");
        }
    }

    protected String getAreaText() {
        return areatext;
    }

    public void setAreaText(String areatext) {
        this.areatext = areatext;
    }

    protected boolean supportsFileDrops() {
        WebBrowser browser = getUI().getPage().getWebBrowser();
        if (browser.isChrome()) {
            return true;
        } else if (browser.isFirefox()) {
            return true;
        } else if (browser.isSafari()) {
            return true;
        } else if (browser.isIE() && browser.getBrowserMajorVersion() >= 11) {
            return true;
        }
        return false;
    }

    abstract protected void handleFile(File file, String fileName,
            String mimeType, long length);

    /**
     * A helper method to set DirectoryFileFactory with given pathname as
     * directory.
     *
     * @param directoryWhereToUpload
     *            the path to directory where files should be uploaded
     */
    public void setRootDirectory(String directoryWhereToUpload) {
        setFileFactory(
                new DirectoryFileFactory(new File(directoryWhereToUpload)));
    }

    @Override
	public AcceptCriterion getAcceptCriterion() {
        // TODO accept only files
        // return new And(new TargetDetailIs("verticalLocation","MIDDLE"), new
        // TargetDetailIs("horizontalLoction", "MIDDLE"));
        return AcceptAll.get();
    }

    @Override
	public void drop(DragAndDropEvent event) {
        DragAndDropWrapper.WrapperTransferable transferable = (WrapperTransferable) event
                .getTransferable();
        Html5File[] files = transferable.getFiles();
        int passingCount = 0;
        for (final Html5File html5File : files) {

            if (maxFileSize != -1 && html5File.getFileSize() > maxFileSize) {
                onMaxSizeExceeded(html5File.getFileSize());
                continue;
            }
            if (!AcceptUtil.accepted(html5File.getFileName(),
                    html5File.getType(), acceptStrings)) {
                onFileTypeMismatch();
                continue;
            }
            if (maxFileCount >= 0 && getRemainingFileCount() <= passingCount) {
                onFileCountExceeded();
                continue;
            }
            ++passingCount;

            final ProgressBar pi = createProgressIndicator();
            ensurePushOrPollingIsEnabled();
            pi.setCaption(html5File.getFileName());
            getprogressBarsLayout().addComponent(pi);
            final FileBuffer receiver = createReceiver();
            html5File.setStreamVariable(new StreamVariable() {

                private String name;
                private String mime;

                @Override
				public OutputStream getOutputStream() {
                    return receiver.receiveUpload(name, mime);
                }

                @Override
				public boolean listenProgress() {
                    return true;
                }

                @Override
				public void onProgress(StreamingProgressEvent event) {
                    float p = (float) event.getBytesReceived()
                            / (float) event.getContentLength();
                    pi.setValue(p);
                }

                @Override
				public void streamingStarted(StreamingStartEvent event) {
                    name = event.getFileName();
                    mime = event.getMimeType();

                }

                @Override
				public void streamingFinished(StreamingEndEvent event) {
                    getprogressBarsLayout().removeComponent(pi);
                    handleFile(receiver.getFile(), html5File.getFileName(),
                            html5File.getType(), html5File.getFileSize());
                    receiver.setValue(null);
                    resetPollIntervalIfNecessary();
                }

                @Override
				public void streamingFailed(StreamingErrorEvent event) {
                    getprogressBarsLayout().removeComponent(pi);
                    resetPollIntervalIfNecessary();
                }

                @Override
				public boolean isInterrupted() {
                    return false;
                }
            });
        }
        acceptedUploads += passingCount;

    }

    private Html5FileInputSettings getHtml5FileInputSettings(
            MultiUpload upload) {
        if (!html5FileInputSettings.containsKey(upload)) {
            Html5FileInputSettings settings = createHtml5FileInputSettingsIfNecessary(
                    upload);
            html5FileInputSettings.put(upload, settings);
        }
        return html5FileInputSettings.get(upload);
    }

    private Html5FileInputSettings createHtml5FileInputSettingsIfNecessary(
            MultiUpload upload) {
        Html5FileInputSettings settings = null;
        if (maxFileSize > 0 || acceptString != null || maxFileCount >= 0) {
            settings = new Html5FileInputSettings(upload);
            if (maxFileSize > 0) {
                settings.setMaxFileSize(maxFileSize);
                settings.setMaxFileSizeText(
                        FileUtils.byteCountToDisplaySize(maxFileSize));
            }
            if (acceptString != null) {
                settings.setAcceptFilter(acceptString);
            }
            if (maxFileCount >= 0) {
                settings.setMaxFileCount(getRemainingFileCount());
            }

        }
        return settings;
    }

    public String getAcceptFilter() {
        return acceptString;
    }

    /**
     * @see {@link Html5FileInputSettings#setAcceptFilter(String)}
     * @param acceptString
     */
    public void setAcceptFilter(String acceptString) {
        this.acceptString = acceptString;
        acceptStrings.clear();
        acceptStrings.addAll(AcceptUtil.unpack(acceptString));
        for (Entry<MultiUpload, Html5FileInputSettings> entry : html5FileInputSettings
                .entrySet()) {
            if (entry.getValue() == null) {
                if (acceptString != null) {
                    html5FileInputSettings.put(entry.getKey(),
                            createHtml5FileInputSettingsIfNecessary(
                                    entry.getKey()));
                }
            } else {
                entry.getValue().setAcceptFilter(acceptString);
            }
        }
    }

    public int getMaxFileSize() {
        return maxFileSize;
    }

    /**
     * @see {@link Html5FileInputSettings#setMaxFileSize(Integer)}
     * @param maxFileSize
     */
    public void setMaxFileSize(int maxFileSize) {
        this.maxFileSize = maxFileSize;
        for (Entry<MultiUpload, Html5FileInputSettings> entry : html5FileInputSettings
                .entrySet()) {
            if (entry.getValue() == null) {
                if (maxFileSize > 0) {
                    html5FileInputSettings.put(entry.getKey(),
                            createHtml5FileInputSettingsIfNecessary(
                                    entry.getKey()));
                }
            } else {
                entry.getValue().setMaxFileSize(maxFileSize);
                if (maxFileSize > 0) {
                    entry.getValue().setMaxFileSizeText(
                            FileUtils.byteCountToDisplaySize(maxFileSize));
                } else {
                    entry.getValue().setMaxFileSizeText("not set");
                }
            }
        }
    }
    
    public void setMaxFileCount(int maxFileCount) {
        this.maxFileCount = maxFileCount;
        int uploadCount = html5FileInputSettings.entrySet().size();
        int processing = 0;
        for (Entry<MultiUpload, Html5FileInputSettings> entry : html5FileInputSettings
                .entrySet()) {
            ++processing;
            if (entry.getValue() == null) {
                if (maxFileCount >= 0) {
                    html5FileInputSettings.put(entry.getKey(),
                            createHtml5FileInputSettingsIfNecessary(
                                    entry.getKey()));
                }
            } else {
                if (processing == uploadCount) {
                    // only the latest upload can accept more files
                    entry.getValue().setMaxFileCount(getRemainingFileCount());
                } else {
                    entry.getValue().setMaxFileCount(0);
                }
            }
        }
    }

    public Integer getRemainingFileCount() {
        return maxFileCount - acceptedUploads;
    }

    public void onMaxSizeExceeded(long contentLength) {
        Notification.show(
                "Max size exceeded "
                        + FileUtils.byteCountToDisplaySize(contentLength)
                        + " > " + FileUtils.byteCountToDisplaySize(maxFileSize),
                Notification.Type.ERROR_MESSAGE);
    }

    public void onFileTypeMismatch() {
        Notification.show("File type mismatch, accepted: " + acceptString,
                Notification.Type.ERROR_MESSAGE);
    }

    public void onFileCountExceeded() {
        Notification.show("File count exceeded",
                Notification.Type.ERROR_MESSAGE);
    }
}
