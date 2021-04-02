package com.kinnara.kecakplugins.ftp.common.externalstorage;

import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.DefaultApplicationPlugin;

import java.util.Map;

/**
 *
 * @param <T> external storage client
 */
public abstract class ExternalStorageUploadTool<T> extends DefaultApplicationPlugin implements IExternalStorage<T> {
    private Map<String, Object> properties;

    @Override
    public final Object execute(Map properties) {
        this.properties = properties;

        try {
            T storageClient = generateClient(this);
            execute(storageClient);
        } catch (ExternalStorageException e) {
            LogUtil.error(getClassName(), e, e.getMessage());
        }

        return null;
    }

    public final Map<String, Object> getProperties() {
        return this.properties;
    }

    protected abstract void execute(T storageClient);
}
