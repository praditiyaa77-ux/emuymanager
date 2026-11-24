package com.filemanagerpro;

import android.os.ParcelFileDescriptor;

interface IFileService {
    void destroy() = 16777114;
    List<String> list(String path) = 1;
    String stat(String path) = 2;
    boolean mkdirs(String path) = 3;
    boolean delete(String path) = 4;
    boolean rename(String from, String to) = 5;
    ParcelFileDescriptor open(String path, int mode) = 6;
}
