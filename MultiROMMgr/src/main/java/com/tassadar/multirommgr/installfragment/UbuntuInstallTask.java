/*
 * This file is part of MultiROM Manager.
 *
 * MultiROM Manager is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MultiROM Manager is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MultiROM Manager. If not, see <http://www.gnu.org/licenses/>.
 */

package com.tassadar.multirommgr.installfragment;

import android.content.SharedPreferences;
import android.util.Log;

import com.tassadar.multirommgr.Device;
import com.tassadar.multirommgr.MgrApp;
import com.tassadar.multirommgr.MultiROM;
import com.tassadar.multirommgr.R;
import com.tassadar.multirommgr.Rom;
import com.tassadar.multirommgr.SettingsActivity;
import com.tassadar.multirommgr.SettingsFragment;
import com.tassadar.multirommgr.Utils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import eu.chainfire.libsuperuser.Shell;

public class UbuntuInstallTask extends InstallAsyncTask  {

    public static final String DOWN_DIR = "UbuntuTouch";

    public UbuntuInstallTask(UbuntuInstallInfo info, MultiROM multirom, Device dev) {
        m_info = info;
        m_multirom = multirom;
        m_device = dev;
    }

    @Override
    protected Void doInBackground(Void... voids) {
        m_listener.onProgressUpdate(0, 0, true, Utils.getString(R.string.preparing_downloads, ""));
        m_listener.onInstallLog(Utils.getString(R.string.preparing_downloads, "<br>"));

        File destDir = new File(Utils.getDownloadDir(), DOWN_DIR);
        destDir.mkdirs();
        String dest = destDir.toString();

        String suDestDir = findSUDestDir(destDir);

        if(suDestDir == null) {
            m_listener.onInstallLog(Utils.getString(R.string.su_failed_find_dir));
            m_listener.onInstallComplete(false);
            return null;
        }

        Log.d("UbuntuInstallTask", "Using download directory: " + dest);
        Log.d("UbuntuInstallTask", "Using SU download directory: " + suDestDir);

        ArrayList<UbuntuFile> files = m_info.buildDownloadList();
        final String base_url = m_device.getUbuntuBaseUrl();

        for(int i = 0; i < files.size(); ++i) {
            UbuntuFile f = files.get(i);

            if(!downloadFile(destDir, base_url + f.path, f))
                return null;

            if(f.signature != null && !downloadFile(destDir, base_url + f.signature, null))
                return null;
        }

        m_listener.onProgressUpdate(0, 0, true, Utils.getString(R.string.installing_utouch));
        m_listener.enableCancel(false);

        String romPath = m_multirom.getNewRomFolder("utouch_" + m_info.channelName);
        if(romPath == null) {
            m_listener.onInstallLog(Utils.getString(R.string.failed_create_rom));
            m_listener.onInstallComplete(false);
            return null;
        }

        Rom rom = new Rom(Utils.getFilenameFromUrl(romPath), Rom.ROM_SECONDARY);
        m_listener.onInstallLog(Utils.getString(R.string.installing_rom, rom.name));

        if(!m_multirom.initUbuntuDir(romPath)) {
            m_listener.onInstallLog(Utils.getString(R.string.failed_rom_init));
            Shell.SU.run("rm -r \"%s\"", romPath);
            m_listener.onInstallComplete(false);
            return null;
        }

        m_multirom.setRomIcon(rom, R.drawable.romic_ubuntu1);

        if(!buildCommandFile(romPath + "/cache/recovery/ubuntu_command")) {
            Shell.SU.run("rm -r \"%s\"", romPath);
            m_listener.onInstallComplete(false);
            return null;
        }

        if(!copyFiles(suDestDir, romPath + "/cache/recovery", files) ||
                !writeBaseUrl(romPath + "/cache/recovery")) {
            Shell.SU.run("rm -r \"%s\"", romPath);
            m_listener.onInstallComplete(false);
            return null;
        }

        m_listener.requestRecovery(true);
        m_listener.onInstallComplete(true);
        return null;
    }

    private boolean downloadFile(File destDir, String url, UbuntuFile file) {
        String filename = Utils.getFilenameFromUrl(url);
        if(filename == null || filename.isEmpty()) {
            m_listener.onInstallLog(Utils.getString(R.string.invalid_url, url));
            m_listener.onInstallComplete(false);
            return false;
        }

        File destFile = new File(destDir, filename);
        long startOffset = 0;
        if(destFile.exists() && file != null) {
            long fileSize = destFile.length();
            if(fileSize < file.size) {
                startOffset = fileSize;
            } else if(file.checksum != null) {
                m_listener.onInstallLog(Utils.getString(R.string.checking_file, Utils.trim(filename, 40)));
                String sha256 = Utils.calculateSHA256(destFile);
                if(file.checksum.equals(sha256)) {
                    m_listener.onInstallLog(Utils.getString(R.string.ok_skippping));
                    return true;
                } else {
                    m_listener.onInstallLog(Utils.getString(R.string.failed_redownload));
                }
            }
        }

        if(!downloadFile(url, destFile, startOffset)) {
            if(!m_canceled)
                m_listener.onInstallComplete(false);
            return false;
        }

        if(file != null && file.checksum != null) {
            m_listener.onInstallLog(Utils.getString(R.string.checking_file, m_downFilename));
            String sha256 = Utils.calculateSHA256(destFile);
            if(file.checksum.equals(sha256))
                m_listener.onInstallLog(Utils.getString(R.string.ok));
            else {
                m_listener.onInstallLog(Utils.getString(R.string.failed));
                m_listener.onInstallComplete(false);
                return false;
            }
        }
        return true;
    }

    private boolean copyFiles(String src, String dest, ArrayList<UbuntuFile> files) {
        for(int i = 0; i < files.size(); ++i) {
            UbuntuFile f = files.get(i);
            String filename = Utils.getFilenameFromUrl(f.path);
            m_listener.onInstallLog(Utils.getString(R.string.copying_file, Utils.trim(filename, 40)));

            if(!copyFile(src + "/" + filename, dest + "/" + filename)) {
                m_listener.onInstallLog(Utils.getString(R.string.failed_file_copy, filename));
                return false;
            }

            filename = Utils.getFilenameFromUrl(f.signature);
            if(!copyFile(src + "/" + filename, dest + "/" + filename)) {
                m_listener.onInstallLog(Utils.getString(R.string.failed_file_copy, filename));
                return false;
            }
            m_listener.onInstallLog(Utils.getString(R.string.ok));
        }

        SharedPreferences pref = MgrApp.getPreferences();
        if(pref.getBoolean(SettingsFragment.UTOUCH_DELETE_FILES, false)) {
            m_listener.onInstallLog(Utils.getString(R.string.deleting_used_files));
            for(int i = 0; i < files.size(); ++i) {
                UbuntuFile f = files.get(i);

                File file = new File(src, Utils.getFilenameFromUrl(f.path));
                file.delete();
                file = new File(src, Utils.getFilenameFromUrl(f.signature));
                file.delete();
            }
        }

        return true;
    }

    private boolean copyFile(final String src, final String dst) {
        List<String> out = Shell.SU.run("cat \"%s\" > \"%s\" && echo success", src, dst);
        return out != null && !out.isEmpty() && out.get(0).equals("success");
    }

    private String findSUDestDir(File destDir) {
        File tmp = new File(destDir, "ut_test_file");
        try {
            tmp.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        File suFile = Utils.findSdcardFileSu(tmp);
        if(suFile == null)
            return null;
        return suFile.getParent();
    }

    private boolean buildCommandFile(String dest) {
        ArrayList<String> cmd = new ArrayList<String>();
        cmd.add("echo \"format data\" > \"" + dest + "\"");
        cmd.add("echo \"format system\" >> \"" + dest + "\"");

        for(int i = 0; i < m_info.keyrings.size(); ++i)  {
            UbuntuFile f = m_info.keyrings.get(i);
            cmd.add(String.format(
                    "echo \"load_keyring %s %s\" >> \"%s\"",
                    Utils.getFilenameFromUrl(f.path), Utils.getFilenameFromUrl(f.signature), dest));
        }

        cmd.add("echo \"mount system\" >> \"" + dest + "\"");

        for(int i = 0; i < m_info.installFiles.size(); ++i)  {
            UbuntuFile f = m_info.installFiles.get(i);
            cmd.add(String.format(
                    "echo \"update %s %s\" >> \"%s\"",
                    Utils.getFilenameFromUrl(f.path), Utils.getFilenameFromUrl(f.signature), dest));
        }

        cmd.add("echo \"unmount system\" >> \"" + dest + "\"");
        cmd.add("echo success");

        List<String> out = Shell.SU.run(cmd);
        return out != null && !out.isEmpty() && out.get(0).equals("success");
    }

    private boolean writeBaseUrl(String destDir) {
        List<String> out = Shell.SU.run("echo \"%s\" > \"%s\" && echo success",
                m_device.getUbuntuBaseUrl(), destDir + "/base_url");
        return out != null && !out.isEmpty() && out.get(0).equals("success");
    }

    private UbuntuInstallInfo m_info;
    private MultiROM m_multirom;
    private Device m_device;
}
