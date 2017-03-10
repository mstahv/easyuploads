package org.vaadin.easyuploads.demoandtestapp;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.vaadin.addonhelpers.AbstractTest;
import org.vaadin.easyuploads.FileBuffer;
import org.vaadin.easyuploads.FileFactory;
import org.vaadin.easyuploads.MultiFileUpload;
import org.vaadin.easyuploads.UploadField;
import org.vaadin.easyuploads.UploadField.FieldType;
import org.vaadin.easyuploads.UploadField.StorageMode;

import com.google.common.io.Files;
import com.vaadin.annotations.Theme;
import com.vaadin.data.Binder;
import com.vaadin.data.HasValue.ValueChangeEvent;
import com.vaadin.data.HasValue.ValueChangeListener;
import com.vaadin.data.converter.StringToIntegerConverter;
import com.vaadin.server.Resource;
import com.vaadin.server.StreamResource;
import com.vaadin.shared.ui.ContentMode;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Component;
import com.vaadin.ui.Embedded;
import com.vaadin.ui.FormLayout;
import com.vaadin.ui.Label;
import com.vaadin.ui.Notification;
import com.vaadin.ui.TextField;
import com.vaadin.ui.VerticalLayout;

@Theme("valo")
public class BasicTest extends AbstractTest {
    
    @SuppressWarnings("serial")
    @Override
    public Component getTestComponent() {
        VerticalLayout mainWindow = new VerticalLayout();
        final UploadField uploadField = new UploadField();
        uploadField.setCaption("Default mode: temp files, fieldType:"
                + uploadField.getFieldType());
        
        Button b = new Button("Show value");
        b.addClickListener(new Button.ClickListener() {
            @Override
			public void buttonClick(ClickEvent event) {
                Object value = uploadField.getValue();
                Notification.show("Value:" + value);
            }
        });
        mainWindow.addComponent(uploadField);
        mainWindow.addComponent(b);
        mainWindow.addComponent(hr());
        
        final UploadField uploadField2 = new UploadField();
        uploadField2.setFieldType(FieldType.FILE);
        uploadField2.setCaption("Storagemode: temp files, fieldType:"
                + uploadField2.getFieldType());
        
        b = new Button("Show value");
        b.addClickListener(new Button.ClickListener() {
            @Override
			public void buttonClick(ClickEvent event) {
                Object value = uploadField2.getValue();
                Notification.show("Value:" + value);
            }
        });
        mainWindow.addComponent(uploadField2);
        mainWindow.addComponent(b);
        mainWindow.addComponent(hr());
        
        final UploadField uploadField3 = new UploadField();
        uploadField3.setFieldType(FieldType.FILE);
        final File tempDir = Files.createTempDir();
        uploadField3.setCaption("Storagemode: " + tempDir + " , fieldType:"
                + uploadField3.getFieldType());
        
        uploadField3.setFileFactory(new FileFactory() {
            @Override
			public File createFile(String fileName, String mimeType) {
                File f = new File(tempDir, fileName);
                return f;
            }
        });
        
        final UploadField uploadFieldHtml5Configured = new UploadField();
        uploadFieldHtml5Configured.setFieldType(FieldType.FILE);
        uploadFieldHtml5Configured.setCaption("Storagemode: " + tempDir + " , fieldType:"
                + uploadFieldHtml5Configured.getFieldType() + " just images, max 1000000");
        uploadFieldHtml5Configured.setAcceptFilter("image/*");
        uploadFieldHtml5Configured.setMaxFileSize(1000000);
        
        uploadFieldHtml5Configured.setFileFactory(new FileFactory() {
            @Override
			public File createFile(String fileName, String mimeType) {
                File f = new File(tempDir, fileName);
                return f;
            }
        });
        
        b = new Button("Show value");
        b.addClickListener(new Button.ClickListener() {
            @Override
			public void buttonClick(ClickEvent event) {
                Object value = uploadFieldHtml5Configured.getValue();
                Notification.show("Value:" + value);
            }
        });
        mainWindow.addComponent(uploadFieldHtml5Configured);
        mainWindow.addComponent(b);
        mainWindow.addComponent(hr());
        
        final UploadField uploadField4 = new UploadField();
        uploadField4.setStorageMode(StorageMode.MEMORY);
        uploadField4.setFieldType(FieldType.UTF8_STRING);
        uploadField4
                .setCaption("writethrough=false, Storagemode: memory , fieldType:"
                        + uploadField4.getFieldType());
        
        mainWindow.addComponent(uploadField4);
        uploadField4.addValueChangeListener(new ValueChangeListener() {
            @Override
            public void valueChange(ValueChangeEvent event) {
                Notification.show("ValueChangeEvent fired.");
            }
        });
        b = new Button("Show value");
        b.addClickListener(new Button.ClickListener() {
            @Override
			public void buttonClick(ClickEvent event) {
                Object value = uploadField4.getValue();
                Notification.show("Value:" + value);
            }
        });
        mainWindow.addComponent(b);
        b = new Button("Discard");
        b.addClickListener(new Button.ClickListener() {
            @Override
			public void buttonClick(ClickEvent event) {
                uploadField4.clear();
                Object value = uploadField4.getValue();
                Notification.show("Value:" + value);
            }
        });
        mainWindow.addComponent(b);
        mainWindow.addComponent(hr());
        
        final UploadField uploadField5 = new UploadField();
        uploadField5.setFieldType(FieldType.BYTE_ARRAY);
        uploadField5.setCaption("Storagemode: memory , fieldType:"
                + uploadField5.getFieldType());
        
        uploadField5.addValueChangeListener(new ValueChangeListener() {
            @Override
            public void valueChange(ValueChangeEvent event) {
                
                String lastFileName = uploadField5.getLastFileName();
                String lastMimeType = uploadField5.getLastMimeType();
                
                Notification.show(lastFileName +  " " + lastMimeType);
                
            }
        });
        
        b = new Button("Show value");
        b.addClickListener(new Button.ClickListener() {
            @Override
			public void buttonClick(ClickEvent event) {
                Object value = uploadField5.getValue();
                Notification.show("Value:" + value);
            }
        });
        mainWindow.addComponent(uploadField5);
        mainWindow.addComponent(b);
        mainWindow.addComponent(hr());
        
        final UploadField uploadField6 = new UploadField() {
            @Override
            protected void updateDisplay() {
                final byte[] pngData = (byte[]) getValue();
                String filename = getLastFileName();
                String mimeType = getLastMimeType();
                long filesize = getLastFileSize();
                if (mimeType.equals("image/png")) {
                    Resource resource = new StreamResource(
                            new StreamResource.StreamSource() {
                        @Override
						public InputStream getStream() {
                            return new ByteArrayInputStream(pngData);
                        }
                    }, "") {
                        @Override
                        public String getMIMEType() {
                            return "image/png";
                        }
                    };
                    Embedded embedded = new Embedded("Image:" + filename + "("
                            + filesize + " bytes)", resource);
                    getRootLayout().addComponent(embedded);
                } else {
                    super.updateDisplay();
                }
            }
        };
        uploadField6.setFieldType(FieldType.BYTE_ARRAY);
        uploadField6
                .setCaption("Storagemode: memory , fieldType:"
                        + uploadField6.getFieldType()
                        + ", overridden methods to display possibly loaded PNG in preview.");
        
        b = new Button("Show value");
        b.addClickListener(new Button.ClickListener() {
            @Override
			public void buttonClick(ClickEvent event) {
                Object value = uploadField6.getValue();
                Notification.show("Value:" + value);
            }
        });
        mainWindow.addComponent(uploadField6);
        mainWindow.addComponent(b);
        mainWindow.addComponent(hr());
        
        MultiFileUpload multiFileUpload = new MultiFileUpload() {
            @Override
            protected void handleFile(File file, String fileName,
                    String mimeType, long length) {
                String msg = fileName + " uploaded. Saved to temp file "
                        + file.getAbsolutePath() + " (size " + length
                        + " bytes)";
                Notification.show(msg);
            }
        };
        multiFileUpload.setCaption("MultiFileUpload");
        mainWindow.addComponent(multiFileUpload);
        mainWindow.addComponent(hr());
        
        MultiFileUpload multiFileUploadLimited = new MultiFileUpload() {
            @Override
            protected void handleFile(File file, String fileName,
                    String mimeType, long length) {
                String msg = fileName + " uploaded. Saved to temp file "
                        + file.getAbsolutePath() + " (size " + length
                        + " bytes)";
                Notification.show(msg);
            }
        };
        multiFileUploadLimited.setCaption(
                "MultiFileUpload limited to < 100 000 bytes (~ 97 KB), images, and 5 files");
        multiFileUploadLimited.setMaxFileSize(100000);
        multiFileUploadLimited.setAcceptFilter("image/*");
        multiFileUploadLimited.setMaxFileCount(5);
        mainWindow.addComponent(multiFileUploadLimited);
        mainWindow.addComponent(hr());
        
        MultiFileUpload multiFileUpload2 = new MultiFileUpload() {
            @Override
            protected void handleFile(File file, String fileName,
                    String mimeType, long length) {
                String msg = fileName + " uploaded. Saved to file "
                        + file.getAbsolutePath() + " (size " + length
                        + " bytes)";
                
                Notification.show(msg);
            }
            
            @Override
            protected FileBuffer createReceiver() {
                FileBuffer receiver = super.createReceiver();
                /*
                 * Make receiver not to delete files after they have been
                 * handled by #handleFile().
                 */
                receiver.setDeleteFiles(false);
                return receiver;
            }
        };
        multiFileUpload2.setCaption("MultiFileUpload (with root dir)");
        multiFileUpload2.setRootDirectory(Files.createTempDir().toString());
        mainWindow.addComponent(multiFileUpload2);
        
        mainWindow.addComponent(hr());
        MultiFileUpload multiFileUpload3 = new SlowMultiFileUpload();
        multiFileUpload3.setCaption("MultiFileUpload (simulated slow network)");
        multiFileUpload3.setUploadButtonCaption("Choose File(s)");
        mainWindow.addComponent(multiFileUpload3);
        
        FormLayout maxSizeMultiUploadLayout = new FormLayout();
        maxSizeMultiUploadLayout.setCaption("MultiFileUpload with maxSize");
        mainWindow.addComponent(maxSizeMultiUploadLayout);
        final TextField maxSizeField = new TextField();
        maxSizeField.setCaption("Max size : ");
        maxSizeMultiUploadLayout.addComponent(maxSizeField);
        final MultiFileUpload multiFileUploadWithMaxSize = new MultiFileUpload() {
            @Override
            protected void handleFile(File file, String fileName, String mimeType, long length) {
                String msg = fileName + " uploaded. Saved to temp file " + file.getAbsolutePath() + " (size " + length
                        + " bytes)";
                Notification.show(msg);
            }
        };
        
        final Binder<Integer[]> binder = new Binder<>();
        final Integer[] maxSize = new Integer[1];
        binder.forField(maxSizeField)
        .withConverter(new StringToIntegerConverter("Invalid int"))
        .bind((o) -> maxSize[0], (o, i) -> maxSize[0] = i);
        binder.addValueChangeListener(event -> {
                if (maxSizeField.getValue() != null) {
                    multiFileUploadWithMaxSize.setMaxFileSize((Integer) event.getValue());
                } else {
                    multiFileUploadWithMaxSize.setMaxFileSize(0);
                }
            }
        );
        maxSizeField.setValue("1048576");
        
        maxSizeMultiUploadLayout.addComponent(multiFileUploadWithMaxSize);
        mainWindow.addComponent(hr());
        
        return mainWindow;
    }
    
    @Override
    protected void setup() {
        super.setup();
        content.setSizeUndefined();
    }
    
    class SlowMultiFileUpload extends MultiFileUpload {

        @Override
        protected void handleFile(File file, String fileName, String mimeType,
                long length) {
            String msg = fileName + " uploaded.";
            Notification.show(msg);
        }
        
        @Override
        protected FileBuffer createReceiver() {
            return new FileBuffer() {
                @Override
                public FileFactory getFileFactory() {
                    return SlowMultiFileUpload.this.getFileFactory();
                }
                
                @Override
                public OutputStream receiveUpload(String filename,
                        String MIMEType) {
                    final OutputStream receiveUpload = super.receiveUpload(
                            filename, MIMEType);
                    OutputStream slow = new OutputStream() {
                        private int slept;
                        private int written;
                        
                        @Override
                        public void write(int b) throws IOException {
                            receiveUpload.write(b);
                            written++;
                            if (slept < 60000 && written % 1024 == 0) {
                                int sleep = 5;
                                slept += sleep;
                                try {
                                    Thread.sleep(sleep);
                                } catch (InterruptedException e) {
                                    // TODO Auto-generated catch block
                                    e.printStackTrace();
                                }
                            }
                        }
                    };
                    return slow;
                }

                @Override
                public void setLastMimeType(String mimeType) {
                    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
                }

                @Override
                public void setLastFileName(String fileName) {
                    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
                }
                
            };
        }
        
    }
    
    private Component hr() {
        Label label = new Label("<hr>", ContentMode.HTML);
        return label;
    }
    
}
