package com.gianlu.aria2app.MoreAboutDownload.FilesFragment;

import android.widget.LinearLayout;

import com.gianlu.aria2app.NetIO.JTA2.File;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class TreeDirectory {
    public DirectoryViewHolder viewHolder;
    public LinearLayout subView;
    private String incrementalPath;
    private List<TreeDirectory> children;
    private List<TreeFile> files;
    private String name;

    private TreeDirectory(String name, String incrementalPath) {
        children = new ArrayList<>();
        files = new ArrayList<>();
        this.name = name;
        this.incrementalPath = incrementalPath;
    }

    public static TreeDirectory root() {
        return new TreeDirectory("", "");
    }

    public static int indexOf(List<TreeDirectory> nodes, TreeDirectory node) {
        for (int i = 0; i < nodes.size(); i++)
            if (areEquals(nodes.get(i), node)) return i;

        return -1;
    }

    public static boolean areEquals(TreeDirectory first, TreeDirectory second) {
        return Objects.equals(first.incrementalPath, second.incrementalPath) && Objects.equals(first.name, second.name);
    }

    public void addElement(String currentPath, String[] list, File file) {
        while (list[0] == null || list[0].isEmpty())
            list = Arrays.copyOfRange(list, 1, list.length);

        if (list.length == 1) {
            files.add(new TreeFile(file));
        } else {
            TreeDirectory currentChild = new TreeDirectory(list[0], currentPath + "/" + list[0]);

            int index = indexOf(children, currentChild);
            if (index == -1) {
                children.add(currentChild);
                currentChild.addElement(currentChild.incrementalPath, Arrays.copyOfRange(list, 1, list.length), file);
            } else {
                TreeDirectory nextChild = children.get(index);
                nextChild.addElement(currentChild.incrementalPath, Arrays.copyOfRange(list, 1, list.length), file);
            }
        }
    }

    public TreeFile findFile(String path) {
        for (TreeFile file : files)
            if (Objects.equals(file.file.path, path)) return file;

        for (TreeDirectory dir : children)
            return dir.findFile(path);

        return null;
    }

    public String getIncrementalPath() {
        return incrementalPath;
    }

    public List<TreeDirectory> getChildren() {
        return children;
    }

    public List<TreeFile> getFiles() {
        return files;
    }

    public String getName() {
        return name;
    }
}
