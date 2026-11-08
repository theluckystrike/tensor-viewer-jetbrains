package zovo.tensor;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileTypes.FileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Binary file types for .gguf and .safetensors, so the IDE does not ask what these files are.
 * .npy/.npz, .pt, .pth, .ckpt and .bin get no file type here: other plugins or text formats use those names,
 * and the editor provider checks the content instead.
 */
public final class TensorFileType implements FileType {
    public static final TensorFileType GGUF = new TensorFileType("GGUF Model", "GGUF model file (llama.cpp, Ollama)", "gguf");
    public static final TensorFileType SAFETENSORS = new TensorFileType("Safetensors Model", "Safetensors tensor file", "safetensors");

    private final String name;
    private final String description;
    private final String extension;

    private TensorFileType(String name, String description, String extension) { this.name = name; this.description = description; this.extension = extension; }

    @Override public @NotNull String getName() { return name; }
    @Override public @NotNull String getDescription() { return description; }
    @Override public @NotNull String getDefaultExtension() { return extension; }
    @Override public @Nullable Icon getIcon() { return AllIcons.FileTypes.Any_type; }
    @Override public boolean isBinary() { return true; }
}
