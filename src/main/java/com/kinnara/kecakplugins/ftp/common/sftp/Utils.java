package com.kinnara.kecakplugins.ftp.common.sftp;

import com.jcraft.jsch.*;
import com.kinnara.kecakplugins.ftp.common.ftp.FtpClient;
import com.kinnarastudio.commons.Declutter;
import com.kinnarastudio.commons.Try;
import org.apache.commons.net.ftp.FTPSClient;
import org.joget.apps.app.dao.DatalistDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.DatalistDefinition;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.DataList;
import org.joget.apps.datalist.model.DataListCollection;
import org.joget.apps.datalist.model.DataListColumn;
import org.joget.apps.datalist.model.DataListFilter;
import org.joget.apps.datalist.service.DataListService;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.service.FileUtil;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.FileManager;
import org.joget.commons.util.LogUtil;
import org.joget.workflow.util.WorkflowUtil;
import org.kecak.apps.form.model.DataJsonControllerHandler;
import org.springframework.context.ApplicationContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Class Extension / Mixin for Sftp
 */
public interface Utils extends Declutter {
    public final static int TIMEOUT = 10000;

    default SftpClient generateSftpChannel(String host, String username, String password, String pathKnownHosts, boolean isStrictHostKeyChecking) throws KecakFtpException {
        try {
            return new SftpClient(host, username, password, pathKnownHosts, isStrictHostKeyChecking);
        } catch (JSchException e) {
            throw new KecakFtpException(e);
        }
    }

    @Override
    default boolean isEmpty(@Nullable Object value) {
        return Declutter.super.isEmpty(value);
    }

    default void storeFile(FtpClient client, File file, String remoteFolder) throws KecakFtpException, IOException {
        // store file in bucket
        try (InputStream fileInputStream = Files.newInputStream(file.toPath())) {
            // create folder
            String path = Optional.of("/")
                    .map(remoteFolder::split)
                    .map(Arrays::stream)
                    .orElseGet(Stream::empty)
                    .filter(Try.toNegate(String::isEmpty))
                    .reduce("", (s1, s2) -> {
                        String folder = s1 + "/" + s2;
//                        try {
//                            int replyCode = client.mkdir(folder);
//                            LogUtil.info(getClass().getName(), "Folder [" + folder + "] is created in remote server reply code [" + replyCode + "]");
//                        } catch (IOException e) {
//                            LogUtil.warn(getClass().getName(), e.getMessage());
//                        }
                        return folder;
                    }, String::concat);

            String targetFullPath = path + "/" + file.getName();
            client.sendFile(fileInputStream, targetFullPath);
        } catch (IOException | com.kinnara.kecakplugins.ftp.common.ftp.KecakFtpException e) {
            throw new KecakFtpException(e);
        }
    }

    default void storeFile(ChannelSftp channelSftp, File file, String remoteFolder) throws KecakFtpException {
        // store file in bucket
        try (InputStream fileInputStream = Files.newInputStream(file.toPath())) {
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
                        } catch (SftpException ignored) {
                        }
                        return folder;
                    }, String::concat);

            String targetFullPath = path + "/" + file.getName();

            LogUtil.info(getClass().getName(), "Storing file [" + file.getAbsolutePath() + "] into sftp server [" + targetFullPath + "]");
            channelSftp.put(fileInputStream, targetFullPath);
        } catch (IOException | SftpException e) {
            throw new KecakFtpException(e);
        }
    }

    default void storeFile(ChannelSftp channelSftp, File file, String remoteFolder, Element element, FormData formData) throws KecakFtpException {
        assert this instanceof Element;
        String path = remoteFolder.replaceAll("^\\./", "") + "/" + FileUtil.getUploadPath(element, formData.getPrimaryKeyValue()).replaceAll("^\\./", "").replaceAll("^.+wflow/", "");
        storeFile(channelSftp, file, path.replaceAll("/+", "/"));
    }

    default InputStream loadFile(ChannelSftp channelSftp, String remoteFolder, String fileName, Element element, FormData formData) throws KecakFtpException {
        String path = remoteFolder.replaceAll("^\\./", "") + "/" + FileUtil.getUploadPath(element, formData.getPrimaryKeyValue()).replaceAll("^\\./", "").replaceAll("^.+wflow/", "");
        return loadFile(channelSftp, path, fileName);
    }

    default InputStream loadFile(ChannelSftp channelSftp, String remoteFolder, String fileName) throws KecakFtpException {
        String filePath = remoteFolder.replaceAll("/+", "/").replaceAll("/$", "") + "/" + fileName;
        return loadFile(channelSftp, filePath);
    }

    /**
     * @param channelSftp
     * @param fullFilePath
     * @return
     * @throws SftpException
     */
    default InputStream loadFile(ChannelSftp channelSftp, String fullFilePath) throws KecakFtpException {
        try {
            LogUtil.info(getClass().getName(), "Loading file from sftp server [" + fullFilePath + "]");
            return channelSftp.get(fullFilePath);
        } catch (SftpException e) {
            throw new KecakFtpException(e.getMessage() + " [" + fullFilePath + "]", e);
        }
    }

    /**
     * Generate {@link DataList} by ID
     *
     * @param datalistId String
     * @return DataList
     * @throws SftpException
     */
    @Nonnull
    default DataList getDataList(String datalistId) throws KecakFtpException {
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
                .orElseThrow(() -> new KecakFtpException("DataList [" + datalistId + "] not found"));
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
    default File getDataListRow(@Nonnull CsvTool pluginTool, @Nonnull DataList dataList, @Nonnull final Map<String, List<String>> filters, String fileName, int skipLines, String[] headerValues) throws KecakFtpException {
        getCollectFilters(dataList, filters);

        DataListCollection<Map<String, Object>> rows = dataList.getRows();
        if (rows == null) {
            throw new KecakFtpException("Error retrieving row from dataList [" + dataList.getId() + "]");
        }

        String tempDirPath = FileManager.getBaseDirectory();
        File tempDir = new File(tempDirPath + UUID.randomUUID());
        if (!tempDir.exists() && !tempDir.mkdir()) {
            throw new KecakFtpException("Error creating temporary directory [" + tempDirPath + "]");
        }

        File file = new File(tempDir, fileName);
        try (PrintWriter writer = new PrintWriter(file)) {
            IntStream.iterate(0, i -> i + 1).limit(skipLines)
                    .boxed()
                    .map(i -> "")
                    .forEach(writer::println);

            if (headerValues != null && headerValues.length > 0) {
                Optional.of(headerValues)
                        .map(s -> String.join(pluginTool.getCsvDelimiter(), s))
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
                    .map(s -> processLine(pluginTool.getCsvDelimiter(), s))
                    .forEach(writer::println);
        } catch (FileNotFoundException e) {
            throw new KecakFtpException(e);
        }

        return file;
    }

    default String processLine(String columnDelimiter, String[] line) {
        return Arrays.stream(line)
                .map(this::processHashVariable)
                .map(this::escapeCharacters)
                .collect(Collectors.joining(columnDelimiter));
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

    @Nonnull
    default void processCsvFile(CsvTool pluginTool, InputStream inputStream, Form form, int skipLines, boolean commaAsThousands, final Map<String, String>[] excelProps, Map<String, String> defaultValues) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream))) {
            br.lines().skip(skipLines)
                    .filter(l -> !l.trim().isEmpty())
                    .forEach(line -> {
                        LogUtil.info(getClass().getName(), "Processing csv file line [" + line + "]");

                        final FormData formData = new FormData();

                        final String[] columns = line.split(pluginTool.getCsvDelimiter());

                        for (Map<String, String> column : excelProps) {
                            int columnNumber = Integer.parseInt(column.get("excelColNum"));
                            if (columnNumber < columns.length) {
                                boolean asNumber = "number".equalsIgnoreCase(column.get("colType"));
                                String cellValue = asNumber ?
                                        (commaAsThousands ?
                                                columns[columnNumber].replaceAll(",", "") :
                                                columns[columnNumber].replaceAll("\\.", "").replaceAll(",", ".")) :
                                        columns[columnNumber];

                                if (!cellValue.isEmpty()) {
                                    Element element = FormUtil.findElement(column.get("field"), form, formData);
                                    String fieldName = FormUtil.getElementParameterName(element);
                                    formData.addRequestParameterValues(fieldName, new String[]{cellValue});
                                }
                            }
                        }

                        // implement default values
                        defaultValues.forEach((k, v) -> {
                            // for every empty field
                            if (!formData.getRequestParams().containsKey(k)) {
                                Element element = FormUtil.findElement(k, form, formData);
                                String fieldName = FormUtil.getElementParameterName(element);
                                formData.addRequestParameterValues(fieldName, new String[]{v});
                            }
                        });

                        FormData resultFormData = submitForm(form, formData, false);
                    });
        } catch (IOException ex) {
            LogUtil.error(getClass().getName(), ex, ex.getMessage());
        }
    }

    default Form getForm(@Nonnull AppDefinition appDefinition, @Nonnull String formDefId, @Nonnull final FormData formData) throws KecakFtpException {
        AppService appService = (AppService) AppUtil.getApplicationContext().getBean("appService");

        if (appService == null) {
            throw new KecakFtpException("Error retrieving appService");
        }

        if (appDefinition.getAppId() == null || appDefinition.getVersion() == null) {
            throw new KecakFtpException("Error retrieving appDefinition");
        }

        final Form form = Optional.ofNullable(appService.viewDataForm(appDefinition.getAppId(), appDefinition.getVersion().toString(), formDefId, null, null, null, formData, null, null))
                .orElseThrow(() -> new KecakFtpException("Form [" + formDefId + "] in app [" + appDefinition.getAppId() + "] version [" + appDefinition.getVersion() + "] not available"));

        // check form permission
        if (!form.isAuthorize(formData)) {
            throw new KecakFtpException("User [" + WorkflowUtil.getCurrentUsername() + "] doesn't have permission to open this form");
        }

        formData.addRequestParameterValues(DataJsonControllerHandler.PARAMETER_DATA_JSON_CONTROLLER, new String[]{DataJsonControllerHandler.PARAMETER_DATA_JSON_CONTROLLER});

        return form;
    }

    default FormData submitForm(@Nonnull Form form, @Nonnull FormData formData, boolean ignoreValidation) {
        AppService appService = (AppService) AppUtil.getApplicationContext().getBean("appService");
        String paramName = FormUtil.getElementParameterName(form);
        formData.addRequestParameterValues(paramName + "_SUBMITTED", new String[]{"true"});

        FormData resultFormData = appService.submitForm(form, formData, ignoreValidation);
        return resultFormData;
    }
}
