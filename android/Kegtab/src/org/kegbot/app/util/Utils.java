/*
 * Copyright 2012 Mike Wakerly <opensource@hoho.com>.
 *
 * This file is part of the Kegtab package from the Kegbot project. For
 * more information on Kegtab or Kegbot, see <http://kegbot.org/>.
 *
 * Kegtab is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, version 2.
 *
 * Kegtab is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with Kegtab. If not, see <http://www.gnu.org/licenses/>.
 */
package org.kegbot.app.util;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.Signature;
import android.os.Build;

public class Utils {

  private final static char[] HEX_DIGITS = {
    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
  };

  private Utils() {
    throw new IllegalStateException("Non-instantiable class.");
  }

  public static byte[] readFile(String file) throws IOException {
    return readFile(new File(file));
  }

  public static byte[] readFile(File file) throws IOException {
    RandomAccessFile f = new RandomAccessFile(file, "r");

    try {
      long longlength = f.length();
      int length = (int) longlength;
      if (length != longlength) {
        throw new IOException("File size >= 2 GB");
      }

      byte[] data = new byte[length];
      f.readFully(data);
      return data;
    } finally {
      f.close();
    }
  }

  public static String toHexString(byte[] data) {
    return toHexString(data, 0, data.length);
  }

  public static String toHexString(byte[] array, int offset, int length) {
    char[] buf = new char[length * 2];

    int bufIndex = 0;
    for (int i = offset; i < offset + length; i++) {
      byte b = array[i];
      buf[bufIndex++] = HEX_DIGITS[(b >>> 4) & 0x0F];
      buf[bufIndex++] = HEX_DIGITS[b & 0x0F];
    }

    return new String(buf);
  }

  public static String getFingerprintForSignature(Signature sig) {
    final MessageDigest md;
    try {
      md = MessageDigest.getInstance("SHA1");
    } catch (NoSuchAlgorithmException e) {
      return "";
    }
    md.update(sig.toByteArray());
    return toHexString(md.digest());
  }

  public static String getUserAgent(Context context) {
    PackageInfo pinfo;
    try {
      pinfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
    } catch (NameNotFoundException e) {
      pinfo = null;
    }

    final String versionName;
    final int versionCode;
    if (pinfo != null) {
      versionName = pinfo.versionName;
      versionCode = pinfo.versionCode;
    } else {
      versionName = "unknown";
      versionCode = 0;
    }

    return new StringBuilder()
      .append("Kegtab/")
      .append(versionName)
      .append('-')
      .append(versionCode)
      .append(" (Android ")
      .append(Build.VERSION.RELEASE)
      .append("/")
      .append(Build.VERSION.SDK_INT)
      .append("; ")
      .append(Build.MANUFACTURER)
      .append(" ")
      .append(Build.MODEL)
      .append("; ")
      .append(Build.FINGERPRINT)
      .append(")")
      .toString();
  }

}
