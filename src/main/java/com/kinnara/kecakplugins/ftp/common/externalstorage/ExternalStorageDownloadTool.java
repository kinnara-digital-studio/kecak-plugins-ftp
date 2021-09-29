package com.kinnara.kecakplugins.ftp.common.externalstorage;

import org.joget.apps.form.lib.FileUpload;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.plugin.base.Plugin;

import java.util.Map;

/**
 * Load file from external storage and save the file into form {@link FileUpload} element
 *
 * @param <T>
 */
public abstract class ExternalStorageDownloadTool<T extends AutoCloseable> extends DefaultApplicationPlugin  {
    private Map<String, Object> properties;

    @Override
    public final Object execute(Map properties) {
        this.properties = properties;

        try(T storageClient = generateClient(this)) {
            execute(storageClient);
        } catch (Exception e) {
            LogUtil.error(getClassName(), e, e.getMessage());
        }

        return null;
    }

    public final Map<String, Object> getProperties() {
        return properties;
    }

    /**
     * Execute Plugin Tool
     *
     * @param storageClient
     * @throws ExternalStorageException
     */
    protected abstract void execute(T storageClient) throws ExternalStorageException;

    protected abstract T generateClient(Plugin plugin) throws ExternalStorageException;
}
