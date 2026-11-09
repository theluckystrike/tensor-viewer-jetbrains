package zovo.tensor;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileTypes.FileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Binary file types for .gguf and .safetensors, so the IDE does not ask what these files are.
 * .npy/.npz, .pt, .pth, .ckpt and .bin get no extension mapping here: other plugins or text formats use those names.
 * When nothing else claims such a file, TensorFileTypeDetector gives it the "Tensor Data" type by content.
 */
public final class TensorFileType implements FileType {
    public static final TensorFileType GGUF = new TensorFileType("GGUF Model", "GGUF model file (llama.cpp, Ollama)", "gguf");
    public static final TensorFileType SAFETENSORS = new TensorFileType("Safetensors Model", "Safetensors tensor file", "safetensors");
    /** NumPy and PyTorch files that no other plugin claims, found by content (TensorFileTypeDetector). No extension of its own. */
    public static final TensorFileType DATA = new TensorFileType("Tensor Data", "NumPy or PyTorch tensor file", "");

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
