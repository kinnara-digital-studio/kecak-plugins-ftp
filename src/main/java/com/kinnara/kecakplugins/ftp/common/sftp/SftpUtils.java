package com.kinnara.kecakplugins.ftp.common.sftp;

import com.jcraft.jsch.*;
import org.joget.apps.app.dao.DatalistDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.DatalistDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.DataList;
import org.joget.apps.datalist.model.DataListCollection;
import org.joget.apps.datalist.model.DataListColumn;
import org.joget.apps.datalist.model.DataListFilter;
import org.joget.apps.datalist.service.DataListService;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.service.FileUtil;
import org.joget.commons.util.FileManager;
import org.joget.commons.util.LogUtil;
import org.springframework.context.ApplicationContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.*;
import java.util.*;
import java.util.function.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public interface SftpUtils {
    String CSV_DELIMITER = ";";

    default ChannelSftp generateSftpChannel(String host, String username, String password, String pathKnownHosts, boolean isStrictHostKeyChecking) throws JSchException {
        JSch jsch = new JSch();
        jsch.setKnownHosts(pathKnownHosts);
        Session jschSession = jsch.getSession(username, host);
        jschSession.setPassword(password);

        if(!isStrictHostKeyChecking) {
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            jschSession.setConfig(config);
        }

        jschSession.connect();
        return (ChannelSftp) jschSession.openChannel("sftp");
    }

    default void storeFile(ChannelSftp channelSftp, File file, String remoteFolder) {
        if(!channelSftp.isConnected()) {
            try {
                channelSftp.connect();
            } catch (JSchException e) {
                LogUtil.error(getClass().getName(), e, e.getMessage());
            }
        }

        // store file in bucket
        try(InputStream fileInputStream = new FileInputStream(file)) {
            // create folder
            String path = Optional.of("/")
                    .map(remoteFolder::split)
                    .map(Arrays::stream)
                    .orElseGet(Stream::empty)
                    .reduce("", (s1, s2) -> {
                        String folder = s1 + "/" + s2;
                        try {
                            channelSftp.mkdir(folder);
                            LogUtil.info(getClass().getName(), "Folder [" + folder + "] is created in remote server");
                        } catch (SftpException ignored) {}
                        return folder;
                    }, String::concat);

            LogUtil.info(getClass().getName(), "Storing file [" + file.getAbsolutePath() + "] in sftp server [" + path + "]");

            channelSftp.put(fileInputStream, path + "/" + file.getName());
        } catch (IOException | SftpException e) {
            LogUtil.error(getClass().getName(), e, e.getMessage());
        }
    }

    default void storeFile(ChannelSftp channelSftp, File file, String remoteFolder, Element element, FormData formData) {
        assert this instanceof Element;
        String path = remoteFolder.replaceAll("^\\./", "") + "/" + FileUtil.getUploadPath(element, formData.getPrimaryKeyValue()).replaceAll("^\\./", "").replaceAll("^.+wflow/", "");
        storeFile(channelSftp, file, path.replaceAll("/+", "/"));
    }

    default InputStream loadFile(ChannelSftp channelSftp, String remoteFolder, String fileName, Element element, FormData formData) throws SftpException {
        String path = remoteFolder.replaceAll("^\\./", "") + "/" + FileUtil.getUploadPath(element, formData.getPrimaryKeyValue()).replaceAll("^\\./", "").replaceAll("^.+wflow/", "");
        return loadFile(channelSftp, path, fileName);
    }

    default InputStream loadFile(ChannelSftp channelSftp, String remoteFolder, String fileName) throws SftpException {
        String filePath = remoteFolder.replaceAll("/+", "/").replaceAll("/$", "") + "/" + fileName;
        return channelSftp.get(filePath);
    }

    /**
     * Generate {@link DataList} by ID
     *
     * @param datalistId String
     * @return DataList
     * @throws SftpException
     */
    @Nonnull
    default DataList getDataList(String datalistId) throws KecakSftpException {
        ApplicationContext appContext = AppUtil.getApplicationContext();
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();

        DataListService dataListService = (DataListService) appContext.getBean("dataListService");
        DatalistDefinitionDao datalistDefinitionDao = (DatalistDefinitionDao) appContext.getBean("datalistDefinitionDao");
        DatalistDefinition datalistDefinition = datalistDefinitionDao.loadById(datalistId, appDef);

        return Optional.ofNullable(datalistDefinition)
                .map(DatalistDefinition::getJson)
                .map(this::processHashVariable)
                .map(dataListService::fromJson)
                .map(peekMap(d -> d.setPageSize(DataList.MAXIMUM_PAGE_SIZE)))
                .orElseThrow(() -> new KecakSftpException("DataList [" + datalistId + "] not found"));
    }

    /**
     * Get collect filters
     *
     * @param dataList Input/Output parameter
     */
    default void getCollectFilters(@Nonnull final DataList dataList, @Nonnull final Map<String, List<String>> filters) {
        Optional.of(dataList)
                .map(DataList::getFilters)
                .map(Arrays::stream)
                .orElseGet(Stream::empty)
                .filter(f -> Optional.of(f)
                        .map(DataListFilter::getName)
                        .map(filters::get)
                        .map(l -> !l.isEmpty())
                        .orElse(false))
                .forEach(f -> f.getType().setProperty("defaultValue", String.join(";", filters.get(f.getName()))));

        dataList.getFilterQueryObjects();
        dataList.setFilters(null);
    }

    /**
     * Get DataList row as JSONObject
     *
     * @param dataList DataList
     * @return
     */

    @Nonnull
    default File getDataListRow(@Nonnull DataList dataList, @Nonnull final Map<String, List<String>> filters, String fileName, int skipLines, String[] headerValues) throws KecakSftpException {
        getCollectFilters(dataList, filters);

        DataListCollection<Map<String, Object>> rows = dataList.getRows();
        if (rows == null) {
            throw new KecakSftpException("Error retrieving row from dataList [" + dataList.getId() + "]");
        }

        String tempDirPath = FileManager.getBaseDirectory();
        File tempDir = new File(tempDirPath + UUID.randomUUID());
        if(!tempDir.exists() && !tempDir.mkdir()) {
            throw new KecakSftpException("Error creating temporary directory [" + tempDirPath + "]");
        }

        File file = new File(tempDir, fileName);
        try(PrintWriter writer = new PrintWriter(file)) {
            IntStream.iterate(0, i -> i + 1).limit(skipLines)
                    .boxed()
                    .map(i -> "")
                    .forEach(writer::println);

            if(headerValues.length > 0) {
                Optional.ofNullable(headerValues)
                        .map(s -> String.join(CSV_DELIMITER, s))
                        .ifPresent(writer::println);
            }

            rows.stream()
                    .map(m -> Optional.of(dataList)
                            .map(DataList::getColumns)
                            .map(Arrays::stream)
                            .orElseGet(Stream::empty)
                            .filter(Objects::nonNull)
                            .map(c -> formatValue(dataList, m, c))
                            .map(String::valueOf)
                            .toArray(String[]::new))
                    .map(this::processLine)
                    .forEach(writer::println);
        } catch (FileNotFoundException e) {
            throw new KecakSftpException(e);
        }

        return file;
    }

    default String processLine(String[] line) {
        return Arrays.stream(line)
                .map(this::processHashVariable)
                .map(this::escapeCharacters)
                .collect(Collectors.joining(CSV_DELIMITER));
    }

    default String escapeCharacters(String data) {
        String escapedData = data.replaceAll("\\R", " ");
        if (data.contains(",") || data.contains("\"") || data.contains("'")) {
            data = data.replace("\"", "\"\"");
            escapedData = "\"" + data + "\"";
        }
        return escapedData;
    }

    /**
     * Format
     *
     * @param dataList DataList
     * @param row      Row
     * @param column   DataListColumn
     * @return
     */
    @Nonnull
    default String formatValue(@Nonnull final DataList dataList, @Nonnull final Map<String, Object> row, @Nonnull DataListColumn column) {
        String value = Optional.of(column)
                .map(DataListColumn::getName)
                .map(row::get)
                .map(String::valueOf)
                .orElse("");

        return Optional.of(column)
                .map(DataListColumn::getFormats)
                .map(Collection::stream)
                .orElseGet(Stream::empty)
                .filter(Objects::nonNull)
                .findFirst()
                .map(f -> f.format(dataList, column, row, value))
                .map(s -> s.replaceAll("<[^>]*>", ""))
                .orElse(value);
    }

    default String processHashVariable(Object content) {
        return AppUtil.processHashVariable(String.valueOf(content), null, null, null);
    }

    /**
     * Predicate not
     *
     * @param p
     * @param <T>
     * @return
     */
    default  <T> Predicate<T> not(Predicate<T> p) {
        return (t) -> !p.test(t);
    }

    /**
     * Can be used in {@link Optional#map(Function)} to "peek" in Optional
     * Example : Optional.map(peekMap(o -> System.out.printl(o))
     *
     * @param consumer Consumer
     * @param <T>
     * @return
     */
    @Nonnull
    default <T> UnaryOperator<T> peekMap(@Nonnull final Consumer<T> consumer) {
        return t -> {
            consumer.accept(t);
            return t;
        };
    }

    /**
     * Throwable supplier
     *
     * @param throwableSupplier
     * @param <R>
     * @param <E>
     * @return
     */
    default  <R, E extends Exception> ThrowableSupplier<R, E> throwableSupplier(ThrowableSupplier<R, E> throwableSupplier) {
        return throwableSupplier;
    }

    /**
     * Throwable function
     *
     * @param throwableFunction
     * @param <T>
     * @param <R>
     * @param <E>
     * @return
     */
    default  <T, R, E extends Exception> ThrowableFunction<T, R, ? extends E> throwableFunction(ThrowableFunction<T, R, ? extends E> throwableFunction) {
        return throwableFunction;
    }

    @FunctionalInterface
    interface ThrowableSupplier<R, E extends Exception> extends Supplier<R> {
        @Nullable
        R getThrowable() throws E;

        @Nullable
        default R get() {
            try {
                return getThrowable();
            } catch (Exception e) {
                LogUtil.error(getClass().getName(), e, e.getMessage());
                return null;
            }
        }

        default ThrowableSupplier<R, E> onException(Function<? super E, R> onException) {
            try {
                return this::getThrowable;
            } catch (Exception e) {
                Objects.requireNonNull(onException);
                return () -> onException.apply((E) e);
            }
        }
    }

    /**
     * Throwable version of {@link Function}.
     * Returns null then exception is raised
     *
     * @param <T>
     * @param <R>
     * @param <E>
     */
    @FunctionalInterface
    interface ThrowableFunction<T, R, E extends Exception> extends Function<T, R> {

        @Override
        default R apply(T t) {
            try {
                return applyThrowable(t);
            } catch (Exception e) {
                LogUtil.error(getClass().getName(), e, e.getMessage());
                return null;
            }
        }

        R applyThrowable(T t) throws E;

        /**
         * @param f
         * @return
         */
        default Function<T, R> onException(Function<? super E, ? extends R> f) {
            return (T a) -> {
                try {
                    return (R) applyThrowable(a);
                } catch (Exception e) {
                    return f.apply((E) e);
                }
            };
        }

        /**
         * @param f
         * @return
         */
        default Function<T, R> onException(BiFunction<? super T, ? super E, ? extends R> f) {
            return (T a) -> {
                try {
                    return (R) applyThrowable(a);
                } catch (Exception e) {
                    return f.apply(a, (E) e);
                }
            };
        }
    }
}
