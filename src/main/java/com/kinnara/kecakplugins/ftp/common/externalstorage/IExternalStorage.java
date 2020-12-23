package com.kinnara.kecakplugins.ftp.common.externalstorage;

import org.joget.plugin.base.Plugin;

public interface IExternalStorage<T>{
    T generateClient(Plugin plugin) throws ExternalStorageException;

    void connect(T client) throws ExternalStorageException;

    void disconnect(T client) throws ExternalStorageException;
}
