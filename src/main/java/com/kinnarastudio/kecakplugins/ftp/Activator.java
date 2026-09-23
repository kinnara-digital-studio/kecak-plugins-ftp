package com.kinnarastudio.kecakplugins.ftp;

import java.util.ArrayList;
import java.util.Collection;

import com.kinnarastudio.kecakplugins.ftp.form.FtpFileUploadElement;
import com.kinnarastudio.kecakplugins.ftp.form.SftpFileUploadElement;
import com.kinnarastudio.kecakplugins.ftp.process.DataListFtpUploadTool;
import com.kinnarastudio.kecakplugins.ftp.process.DataListSftpUploadTool;
import com.kinnarastudio.kecakplugins.ftp.process.FtpFileDownloadTool;
import com.kinnarastudio.kecakplugins.ftp.process.SftpSpreadsheetFileDownloadTool;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

public class Activator implements BundleActivator {

    protected Collection<ServiceRegistration> registrationList;

    public void start(BundleContext context) {
        registrationList = new ArrayList<ServiceRegistration>();

        // SFTP
        registrationList.add(context.registerService(SftpFileUploadElement.class.getName(), new SftpFileUploadElement(), null));
        registrationList.add(context.registerService(FtpFileUploadElement.class.getName(), new FtpFileUploadElement(), null));
        registrationList.add(context.registerService(DataListFtpUploadTool.class.getName(), new DataListFtpUploadTool(), null));
        registrationList.add(context.registerService(DataListSftpUploadTool.class.getName(), new DataListSftpUploadTool(), null));
        registrationList.add(context.registerService(SftpSpreadsheetFileDownloadTool.class.getName(), new SftpSpreadsheetFileDownloadTool(), null));

        // FTP
        registrationList.add(context.registerService(FtpFileDownloadTool.class.getName(), new FtpFileDownloadTool(), null));
    }

    public void stop(BundleContext context) {
        for (ServiceRegistration registration : registrationList) {
            registration.unregister();
        }
    }
}