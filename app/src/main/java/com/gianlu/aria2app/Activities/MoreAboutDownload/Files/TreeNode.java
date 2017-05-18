package com.gianlu.aria2app.Activities.MoreAboutDownload.Files;

import com.gianlu.aria2app.NetIO.JTA2.AFile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

// TODO: Test with more complex hierarchies
public class TreeNode {
    private static String SEPARATOR = "/";
    public final List<TreeNode> files;
    public final List<TreeNode> dirs;
    public final AFile obj;
    public final String incrementalPath;
    public final String name;

    public TreeNode(String incrementalPath, AFile obj) {
        this.incrementalPath = incrementalPath;
        this.obj = obj;
        this.files = null;
        this.dirs = null;
        this.name = obj.getName();
    }

    private TreeNode(String name, String incrementalPath) {
        this.dirs = new ArrayList<>();
        this.files = new ArrayList<>();
        this.name = name;
        this.obj = null;
        this.incrementalPath = incrementalPath;
    }

    public static void guessSeparator(String path) {
        if (path.contains("/")) SEPARATOR = "/";
        else if (path.contains("\\")) SEPARATOR = "\\";
        else SEPARATOR = "/";
    }

    public static TreeNode create(List<AFile> files, String commonRoot) {
        TreeNode rootNode = new TreeNode(SEPARATOR, "");
        for (AFile file : files) rootNode.addElement(file, commonRoot);
        return rootNode;
    }

    public int indexOfDir(TreeNode node) {
        for (int i = 0; i < dirs.size(); i++)
            if (Objects.equals(dirs.get(i), node))
                return i;

        return -1;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TreeNode treeNode = (TreeNode) o;
        return incrementalPath.equals(treeNode.incrementalPath) && name.equals(treeNode.name);
    }

    public void addElement(AFile element, String commonRoot) {
        String[] list = element.path.replace(commonRoot, "").split(SEPARATOR);
        addElement(incrementalPath, list, element);
    }

    public void addElement(String currentPath, String[] list, AFile file) {
        while (list[0] == null || list[0].isEmpty())
            list = Arrays.copyOfRange(list, 1, list.length);

        if (list.length == 1) {
            files.add(new TreeNode(currentPath, file));
        } else {
            TreeNode currentChild = new TreeNode(list[0], currentPath + SEPARATOR + list[0]);

            int index = indexOfDir(currentChild);
            if (index == -1) {
                dirs.add(currentChild);
                currentChild.addElement(currentChild.incrementalPath, Arrays.copyOfRange(list, 1, list.length), file);
            } else {
                TreeNode nextChild = dirs.get(index);
                nextChild.addElement(currentChild.incrementalPath, Arrays.copyOfRange(list, 1, list.length), file);
            }
        }
    }

    public boolean isFile() {
        return files == null || dirs == null;
    }
}
