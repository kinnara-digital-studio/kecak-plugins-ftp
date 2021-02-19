package com.kinnara.kecakplugins.ftp.common.externalstorage;

import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.DefaultApplicationPlugin;

import java.util.Map;

public abstract class ExternalStorageDownloadTool<T> extends DefaultApplicationPlugin implements IExternalStorage<T> {
    @Override
    public final Object execute(Map properties) {
        try {
            T storageClient = generateClient(this);
            execute(storageClient, properties);
        } catch (ExternalStorageException e) {
            LogUtil.error(getClassName(), e, e.getMessage());
        }

        return null;
    }

    protected abstract void execute(T storageClient, Map<String, Object> properties);
}
