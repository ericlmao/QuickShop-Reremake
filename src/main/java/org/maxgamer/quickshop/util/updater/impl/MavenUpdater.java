/*
 * This file is a part of project QuickShop, the name is JenkinsUpdater.java
 *  Copyright (C) PotatoCraft Studio and contributors
 *
 *  This program is free software: you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License as published by the
 *  Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 *  for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.maxgamer.quickshop.util.updater.impl;

import lombok.Getter;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.InvalidDescriptionException;
import org.bukkit.plugin.PluginDescriptionFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.maxgamer.quickshop.BuildInfo;
import org.maxgamer.quickshop.QuickShop;
import org.maxgamer.quickshop.util.HttpUtil;
import org.maxgamer.quickshop.util.MsgUtil;
import org.maxgamer.quickshop.util.Util;
import org.maxgamer.quickshop.util.updater.QuickUpdater;
import org.maxgamer.quickshop.util.updater.VersionType;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class MavenUpdater implements QuickUpdater {
    private static final String mavenStableBaseUrl = "https://repo.codemc.io/repository/maven-releases/org/maxgamer/QuickShop/";
    private static final String mavenSnapshotBaseUrl = "https://repo.codemc.io/repository/maven-snapshots/org/maxgamer/QuickShop/";
    @Nullable
    private final String updateBaseUrl;
    private final VersionType versionType;
    private final SAXParserFactory factory;
    private String remoteVersion = "UNKNOWN";
    private String remoteBuildNumber;
    private File updatedJar;
    private boolean isLatest = true;
    private long lastCheckingTime;

    public MavenUpdater(BuildInfo pluginBuildInfo) {
        this.updateBaseUrl = pluginBuildInfo.getJobUrl();
        this.versionType = "PotatoCraft-Studio/QuickShop-Reremake".equals(pluginBuildInfo.getJobName()) ? VersionType.STABLE : VersionType.SNAPSHOT;
        this.remoteBuildNumber = pluginBuildInfo.getPomBuildNumber();
        SAXParserFactory factory1;
        try {
            factory1 = SAXParserFactory.newInstance();
            factory1.newSAXParser();
        } catch (Throwable e) {
            factory1 = null;
            QuickShop.getInstance().getLogger().log(Level.WARNING, "Could not init sax parser for xml, update checking is not available!", e);
        }
        factory = factory1;
    }

    @Override
    public @NotNull VersionType getCurrentRunning() {
        return versionType;
    }

    @Override
    public @NotNull String getRemoteServerVersion() {
        return remoteVersion;
    }

    @Override
    public boolean isLatest() {
        if (QuickShop.getInstance().getGameVersion().isEndOfLife()) { // EOL server won't receive future updates
            return true;
        }
        //Not checking updates when environment not available
        if (updateBaseUrl == null || factory == null) {
            return true;
        }
        if (System.currentTimeMillis() - lastCheckingTime < TimeUnit.DAYS.toMillis(1)) {
            return isLatest;
        }
        try {
            if (versionType == VersionType.STABLE) {
                try (Response response = HttpUtil.getClientInstance().newCall(new Request.Builder().url(mavenStableBaseUrl + "maven-metadata.xml").header("User-Agent", "QuickShop-" + QuickShop.getFork() + " " + QuickShop.getVersion()).get().build()).execute();
                     ResponseBody responseBody = response.body();
                     InputStream is = responseBody == null ? null : responseBody.byteStream()) {
                    if (is != null) {
                        javax.xml.parsers.SAXParser parser = factory.newSAXParser();
                        SAXParser.IndexMetaHandler indexMetaHandler = new SAXParser.IndexMetaHandler();
                        parser.parse(is, indexMetaHandler);
                        List<String> versionList = indexMetaHandler.getVersionList();
                        if (versionList.size() >= 1) {
                            remoteVersion = versionList.get(versionList.size() - 1);
                            isLatest = remoteVersion.equals(QuickShop.getVersion());
                        }
                    }
                }
            } else {
                try (Response response = HttpUtil.getClientInstance().newCall(new Request.Builder().url(mavenSnapshotBaseUrl + "maven-metadata.xml").header("User-Agent", "QuickShop-" + QuickShop.getFork() + " " + QuickShop.getVersion()).get().build()).execute();
                     ResponseBody responseBody = response.body();
                     InputStream is = responseBody == null ? null : responseBody.byteStream()) {
                    if (is != null) {
                        javax.xml.parsers.SAXParser parser = factory.newSAXParser();
                        SAXParser.IndexMetaHandler indexMetaHandler = new SAXParser.IndexMetaHandler();
                        parser.parse(is, indexMetaHandler);
                        if (indexMetaHandler.getLatestRelease() != null) {
                            remoteVersion = indexMetaHandler.getLatestRelease();
                            //Exclude wrong version from list
                            if (remoteVersion.equals("6.0.0.0-BETA-SNAPSHOT")) {
                                List<String> versionList = indexMetaHandler.getVersionList();
                                versionList.remove("6.0.0.0-BETA-SNAPSHOT");
                                if (versionList.size() >= 1) {
                                    remoteVersion = versionList.get(versionList.size() - 1);
                                }
                            }
                            isLatest = remoteVersion.equals(QuickShop.getVersion());
                        }
                    }
                }
                if (isLatest) {
                    try (Response response = HttpUtil.getClientInstance().newCall(new Request.Builder().url(mavenSnapshotBaseUrl + remoteVersion + "/maven-metadata.xml").header("User-Agent", "QuickShop-" + QuickShop.getFork() + " " + QuickShop.getVersion()).get().build()).execute();
                         ResponseBody responseBody = response.body();
                         InputStream is = responseBody == null ? null : responseBody.byteStream()) {
                        if (is != null) {
                            javax.xml.parsers.SAXParser parser = factory.newSAXParser();
                            SAXParser.ArtifactMeta artifactMeta = new SAXParser.ArtifactMeta();
                            parser.parse(is, artifactMeta);
                            if (artifactMeta.getBuildNumber() != null) {
                                if (!remoteBuildNumber.equals(artifactMeta.getBuildNumber())) {
                                    remoteBuildNumber = artifactMeta.getBuildNumber();
                                    isLatest = false;
                                }
                            }
                        }
                    }
                }

            }
        } catch (Exception e) {
            MsgUtil.sendDirectMessage(Bukkit.getConsoleSender(), ChatColor.RED + "[QuickShop] Failed to check for an update on build server! It might be an internet issue or the build server host is down. If you want disable the update checker, you can disable in config.yml, but we still high-recommend check for updates on SpigotMC or CodeMC Jenkins often, Error: " + e.getMessage());
        } finally {
            lastCheckingTime = System.currentTimeMillis();
        }
        return isLatest;
    }

    private byte[] downloadUpdate() throws IOException {
        if (updateBaseUrl == null) {
            throw new IOException("Update url is not existed!");
        }
        try (Response response = HttpUtil.getClientInstance().newCall(new Request.Builder().url(updateBaseUrl + "lastSuccessfulBuild/artifact/target/QuickShop.jar").header("User-Agent", "QuickShop-" + QuickShop.getFork() + " " + QuickShop.getVersion()).get().build()).execute();
             ResponseBody responseBody = response.body();
             InputStream is = responseBody == null ? null : responseBody.byteStream();
             ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            byte[] buff = new byte[1024];
            int len;
            long downloaded = 0;
            if (is == null) {
                throw new IOException("Failed downloading: Cannot open connection with remote server.");
            }
            while ((len = is.read(buff)) != -1) {
                os.write(buff, 0, len);
                downloaded += len;
                Util.debugLog("File Downloader: " + downloaded + " bytes.");
            }
            is.close();
            byte[] file = os.toByteArray();
            os.close();
            return file;
        }
    }

    @Override
    public void installUpdate() throws IOException {
        byte[] bytes = downloadUpdate();
        File pluginFolder = new File("plugins");
        if (!pluginFolder.exists()) {
            throw new IOException("Can't find the plugins folder.");
        }
        if (!pluginFolder.isDirectory()) {
            throw new IOException("Plugins not a folder.");
        }
        File[] plugins = pluginFolder.listFiles();
        if (plugins == null) {
            throw new IOException("Can't get the files in plugins folder");
        }
        File newJar = new File(pluginFolder, "QuickShop" + UUID.randomUUID().toString().replace("-", "") + ".jar");

        for (File pluginJar : plugins) {
            try { //Delete all old jar files
                PluginDescriptionFile desc = QuickShop.getInstance().getPluginLoader().getPluginDescription(pluginJar);
                if (!desc.getName().equals(QuickShop.getInstance().getDescription().getName())) {
                    continue;
                }
                Util.debugLog("Deleting: " + pluginJar.getPath());
                if (!pluginJar.delete()) {
                    Util.debugLog("Delete failed, using replacing method");
                    try (OutputStream outputStream = new FileOutputStream(pluginJar, false)) {
                        outputStream.write(bytes);
                        outputStream.flush();
                        updatedJar = pluginJar;
                    }
                } else {
                    try (OutputStream outputStream = new FileOutputStream(newJar, false)) {
                        outputStream.write(bytes);
                        outputStream.flush();
                        updatedJar = newJar;
                    }
                }
            } catch (InvalidDescriptionException ignored) {
            }
        }
    }

    @Override
    public @Nullable File getUpdatedJar() {
        return updatedJar;
    }

    static class SAXParser {
        static class IndexMetaHandler extends DefaultHandler {
            @Getter
            private final List<String> versionList = new ArrayList<>();
            @Getter
            private String latestRelease;
            private String tempStr;
            @Getter
            private String lastUpdated;

            @Override
            public void endElement(String uri, String localName, String qName) {
                switch (qName) {
                    case "release":
                        latestRelease = tempStr;
                        break;
                    case "lastUpdated":
                        lastUpdated = tempStr;
                        break;
                    case "version":
                        if (tempStr != null) {
                            versionList.add(tempStr);
                        }
                        break;
                }
            }

            public void characters(char[] ch, int start, int length) {
                tempStr = new String(ch, start, length);
            }
        }

        static class ArtifactMeta extends DefaultHandler {
            private String tempStr;
            @Getter
            private String lastUpdated;
            @Getter
            private String buildNumber;
            @Getter
            private String timestamp;

            @Override
            public void endElement(String uri, String localName, String qName) {
                switch (qName) {
                    case "timestamp":
                        timestamp = tempStr;
                        break;
                    case "buildNumber":
                        buildNumber = tempStr;
                        break;
                    case "lastUpdated":
                        lastUpdated = tempStr;
                        break;
                }
            }

            public void characters(char[] ch, int start, int length) {
                tempStr = new String(ch, start, length);
            }
        }
    }
}
