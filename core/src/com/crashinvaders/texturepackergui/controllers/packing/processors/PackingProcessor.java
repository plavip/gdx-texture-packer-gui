package com.crashinvaders.texturepackergui.controllers.packing.processors;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.tools.FileProcessor;
import com.badlogic.gdx.tools.texturepacker.TexturePacker;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectSet;
import com.crashinvaders.texturepackergui.services.model.InputFile;
import com.crashinvaders.texturepackergui.services.model.PackModel;
import com.crashinvaders.texturepackergui.utils.packprocessing.PackProcessingNode;
import com.crashinvaders.texturepackergui.utils.packprocessing.PackProcessor;
import com.github.czyzby.kiwi.util.common.Strings;
import org.apache.commons.io.filefilter.SuffixFileFilter;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.regex.Pattern;

public class PackingProcessor implements PackProcessor {
    @Override
    public void processPackage(PackProcessingNode node) throws Exception {
        PackModel pack = node.getPack();
        String settingsOrigExtension = pack.getSettings().atlasExtension;

        System.out.println("Packing is started");

        performPacking(pack);

        pack.getSettings().atlasExtension = settingsOrigExtension;

        System.out.println("Packing is done");
    }
    
    private void performPacking(PackModel packModel) throws Exception {
        Array<ImageEntry> imageEntries = collectImageFiles(packModel);
        if (imageEntries.size == 0) {
            throw new IllegalStateException("No images to pack");
        }

        deleteOldFiles(packModel);
        String filename = getFilename(packModel);

        TexturePacker packer = new TexturePacker(packModel.getSettings());
        for (ImageEntry image : imageEntries) {
            packer.addImage(image.fileHandle.file(), image.name);
        }
        packer.pack(new File(packModel.getOutputDir()), filename);
    }

    private static Array<ImageEntry> collectImageFiles(PackModel packModel) {
        ObjectSet<ImageEntry> images = new ObjectSet<>();
        Array<InputFile> inputFiles = packModel.getInputFiles();

        // Collect input images from directories
        for (InputFile inputFile : inputFiles) {
            if (inputFile.getType() == InputFile.Type.Input && inputFile.isDirectory()) {
                FileHandle fileHandle = inputFile.getFileHandle();
                FileHandle[] children = fileHandle.list((FileFilter) new SuffixFileFilter(new String[]{".png", ".jpg", "jpeg"}));
                for (FileHandle child : children) {
                    String name = child.nameWithoutExtension();
                    if (Strings.isNotEmpty(inputFile.getDirFilePrefix())) {
                        name = inputFile.getDirFilePrefix() + name;
                    }
                    images.add(new ImageEntry(child, name));
                }
            }
        }

        // Collect all singular input images
        for (InputFile inputFile : inputFiles) {
            if (inputFile.getType() == InputFile.Type.Input && !inputFile.isDirectory()) {
                FileHandle fileHandle = inputFile.getFileHandle();
                String name = fileHandle.nameWithoutExtension();

                String regionName = inputFile.getRegionName();
                if (Strings.isNotEmpty(regionName)) {
                    name = regionName;
                }

                ImageEntry imageEntry = new ImageEntry(fileHandle, name);
                if (images.contains(imageEntry)) {
                    // Overwrite existing record (in case it was previously added from a directory)
                    images.remove(imageEntry);
                }
                images.add(imageEntry);
            }
        }

        // Exclude all ignore entries
        for (InputFile inputFile : inputFiles) {
            if (inputFile.getType() != InputFile.Type.Ignore || inputFile.isDirectory()) continue;
            images.remove(new ImageEntry(inputFile.getFileHandle(), null));
        }

        // Return result
        Array<ImageEntry> result = new Array<>();
        for (ImageEntry image : images) {
            result.add(image);
        }
        return result;
    }

    private static void deleteOldFiles(PackModel packModel) throws Exception {
        TexturePacker.Settings settings = packModel.getSettings();
        String atlasExtension = settings.atlasExtension == null ? "" : settings.atlasExtension;
        atlasExtension = Pattern.quote(atlasExtension);

        String filename = getFilename(packModel);

        for (int i = 0, n = settings.scale.length; i < n; i++) {
            FileProcessor deleteProcessor = new FileProcessor() {
                protected void processFile (Entry inputFile) throws Exception {
                    Files.delete(inputFile.inputFile.toPath());
                }
            };
            deleteProcessor.setRecursive(false);

            String scaledPackFileName = settings.getScaledPackFileName(filename, i);
            File packFile = new File(scaledPackFileName);

            String prefix = filename;
            int dotIndex = prefix.lastIndexOf('.');
            if (dotIndex != -1) prefix = prefix.substring(0, dotIndex);
            deleteProcessor.addInputRegex("(?i)" + prefix + "\\d*\\.(png|jpg|jpeg)");
            deleteProcessor.addInputRegex("(?i)" + prefix + atlasExtension);

            File outputRoot = new File(packModel.getOutputDir());
            String dir = packFile.getParent();
            if (dir == null)
                deleteProcessor.process(outputRoot, null);
            else if (new File(outputRoot + "/" + dir).exists()) //
                deleteProcessor.process(outputRoot + "/" + dir, null);
        }
    }

    private static String getFilename(PackModel packModel) {
        String filename = packModel.getCanonicalFilename();
        if (filename.lastIndexOf(".") > -1) {
            String extension = filename.substring(filename.lastIndexOf("."));
            filename = filename.substring(0, filename.lastIndexOf("."));
            packModel.getSettings().atlasExtension = extension;
        } else {
            packModel.getSettings().atlasExtension = "";
        }
        return filename;
    }

    private static BufferedImage createBufferedImage (FileHandle fileHandle) {
        File file = fileHandle.file();
        BufferedImage image;
        try {
            image = ImageIO.read(file);
        } catch (IOException ex) {
            throw new RuntimeException("Error reading image: " + file, ex);
        }
        if (image == null) throw new RuntimeException("Unable to read image: " + file);

        return image;
    }

    private static class ImageEntry {
        private final FileHandle fileHandle;
        private final String name;

        public ImageEntry(FileHandle fileHandle, String name) {
            this.fileHandle = fileHandle;
            this.name = name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ImageEntry that = (ImageEntry) o;

            return fileHandle.equals(that.fileHandle);
        }

        @Override
        public int hashCode() {
            return fileHandle.hashCode();
        }
    }
}
