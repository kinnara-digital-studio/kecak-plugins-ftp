package com.kinnara.kecakplugins.ftp.common.externalstorage;

import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.DefaultApplicationPlugin;

import java.util.Map;

/**
 *
 * @param <T> external storage client
 */
public abstract class ExternalStorageUploadTool<T> extends DefaultApplicationPlugin implements IExternalStorage<T> {
    @Override
    public Object execute(Map properties) {
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
